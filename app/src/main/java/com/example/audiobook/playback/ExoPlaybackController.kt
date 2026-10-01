@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.audiobook.playback

import android.content.Context
import android.content.ComponentName
import android.content.Intent
import android.net.Uri
import androidx.core.content.ContextCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.session.MediaController
import androidx.media3.session.SessionToken
import com.google.common.util.concurrent.MoreExecutors
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.example.audiobook.data.preferences.AppSettings
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.entity.AudioFileEntity
import com.example.audiobook.data.room.entity.ChapterEntity
import com.example.audiobook.data.room.entity.FileStatus
import com.example.audiobook.data.room.entity.ListeningProgressEntity
import com.example.audiobook.data.room.entity.ListeningSessionEntity
import com.example.audiobook.data.room.entity.ProgressStatus
import com.example.audiobook.data.room.entity.SessionEndReason
import com.example.audiobook.data.room.entity.SessionState
import com.example.audiobook.notifications.BookCompletionNotifier
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.cancel
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.withContext
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

/** المسافة الزمنية بين عينات فحص اكتمال الفصول أثناء التشغيل. */
private const val CHAPTER_COMPLETION_CHECK_INTERVAL_MS = 2_000L
/** هامش التسامح مع فرق ترميز MP3 بين الموضع الفعلي ومدة الحاوية عند كشف اكتمال الكتاب. */
private const val COMPLETION_TOLERANCE_MS = 2_000L
/** حد التراجع الصريح (60 ثانية) الذي يُعيد الكتاب إلى حالة "قيد الاستماع" بعد إنهائه. */
private const val UNFINISH_REWIND_THRESHOLD_MS = 60_000L
/**
 * FIX 6.2: shortest recordable session (2s) — buffering blips
 * (flickering play/stop) must not pollute the history with zero rows.
 */
internal const val MIN_LISTENING_SESSION_MS = 2_000L

@Singleton
class ExoPlaybackController @Inject constructor(
    @ApplicationContext context: Context,
    private val database: AppDatabase,
    private val appSettings: AppSettings,
    private val bookCompletionNotifier: BookCompletionNotifier
) : PlaybackController {
    private val appContext: Context = context.applicationContext
    /** كائن ExoPlayer الفعلي؛ يُكشَف فقط لبناء MediaSession في PlaybackService. */
    internal val player: ExoPlayer = ExoPlayer.Builder(context.applicationContext).build()
    private val scope = CoroutineScope(Dispatchers.Main.immediate + Job())
    private val mutableState = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()
    private var editionId: UUID? = null
    private var files: List<AudioFileEntity> = emptyList()
    private var playableFiles: List<AudioFileEntity> = emptyList()
    private var timeline: EditionTimeline = EditionTimeline(emptyList())
    private var lastOriginalIndex = -1
    private var saveJob: Job? = null
    private var mediaServiceStarted = false
    private var mediaController: MediaController? = null
    /** اختزال فحص اكتمال الفصول: يُؤخذ عينة كل ثانيتين فقط بدل كل تحديث موضع (500ms). */
    private var lastChapterCompletionCheckAtMs = 0L
    /**
     * FIX 6.2 — session tracking: sessions were written only on sleep-timer
     * expiry, so Statistics stayed zero for normal listening. Now a session
     * starts on real playback start and ends (MANUAL_PAUSE) on every stop,
     * with a 2s floor to ignore buffering blips.
     */
    private var sessionStartMs: Long? = null
    private var sessionStartPositionMs: Long = 0L
    private var sessionEditionId: UUID? = null
    /** آخر موضع فُحص لعبور حد الفصل (إيقاف المتابعة التلقائية) — سالب = غير مهيأ. */
    private var lastChapterCheckPositionMs: Long = -1L
    /**
     * نطاق كتابة الجلسات: مستقل عن scope الرئيسي الذي يُلغى في release() —
     * وإلا ضاعت جلسة الإغلاق نفسها.
     */
    private val sessionIoScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    /**
     * علامة نقطة اكتمال الكتاب: ما دامت مُنصوبة لا يُعاد الكتاب صامتًا إلى
     * IN_PROGRESS بعد FINISHED، إلا إذا تراجع المستخدم تراجعًا صريحًا كبيرًا.
     */
    private var finishedMarkMs: Long? = null

    /** يبدأ خدمة التشغيل كخدمة في المقدمة عند أول تشغيل فعلي — Media3 ينشر إشعار التشغيل. */
    private fun ensureMediaServiceStarted() {
        if (mediaServiceStarted) return
        mediaServiceStarted = true
        val intent = Intent(appContext, PlaybackService::class.java)
        ContextCompat.startForegroundService(appContext, intent)
        connectMediaController()
    }

    /** يربط MediaController بخدمة الجلسة حتى تُبنى MediaSession وتُنشر خدمة المقدمة والإشعار. */
    private fun connectMediaController() {
        val token = SessionToken(appContext, ComponentName(appContext, PlaybackService::class.java))
        val controllerFuture = MediaController.Builder(appContext, token).buildAsync()
        controllerFuture.addListener(
            {
                try {
                    mediaController = controllerFuture.get()
                } catch (e: Exception) {
                    mediaServiceStarted = false
                }
            },
            MoreExecutors.directExecutor()
        )
    }

    private val chapterCompletionObserver = ChapterCompletionObserver(
        clock = { System.currentTimeMillis() },
        writer = { database.chapterCompletionDao().insert(it) }
    )

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateState()
                if (isPlaying) {
                    ensureMediaServiceStarted()
                    startPeriodicSave()
                    beginListeningSession()
                } else {
                    saveProgress()
                    endListeningSession(SessionEndReason.MANUAL_PAUSE)
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val next = playableFiles.getOrNull(player.currentMediaItemIndex)
                val nextOriginalIndex = files.indexOfFirst { it.id == next?.id }
                if (lastOriginalIndex >= 0 && nextOriginalIndex >= 0 && timeline.shouldStopBeforeTransition(lastOriginalIndex, nextOriginalIndex)) {
                    player.pause()
                    mutableState.value = mutableState.value.copy(isPlaying = false, missingFileMessage = "توقف التشغيل: الجزء التالي مفقود")
                }
                if (nextOriginalIndex >= 0) lastOriginalIndex = nextOriginalIndex
                updateState()
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                updateState()
                if (playbackState == Player.STATE_ENDED) {
                    stopAtMissingBoundaryIfNeeded()
                    saveProgress()
                }
            }

            override fun onPlayerError(error: androidx.media3.common.PlaybackException) {
                // يُطبَّق سلوك التوقف عند الملفات المفقودة عبر onMediaItemTransition و stopAtMissingBoundaryIfNeeded
            }

            override fun onTimelineChanged(timeline: androidx.media3.common.Timeline, reason: Int) {
            }
        })
        scope.launch {
            while (isActive) {
                delay(500)
                if (player.isPlaying) updateState()
            }
        }
    }

    override suspend fun openEdition(editionId: UUID) {
        // FIX 2: نفس النسخة تعمل فعلًا (نقرة إشعار أثناء التشغيل) — لا تعيد
        // الضبط ولا تقفز لموضع القاعدة؛ أبقِ موضع ExoPlayer الحي. الحالة
        // المعلنة محدّثة أصلًا، فلا شيء يُفعل هنا إطلاقًا.
        if (editionId == this.editionId && player.playbackState != Player.STATE_IDLE) {
            // PART 0: نفس النسخة محمّلة — لا إعادة ضبط، لكن التشغيل التلقائي
            // يُحترم (play() بلا أثر إن كان يعمل أصلًا ولا يمسّ الموضع).
            if (appSettings.autoPlayOnOpen.value) play()
            return
        }
        this.editionId = editionId
        lastChapterCheckPositionMs = -1L
        PlaybackStateHolder.update(editionId)
        files = database.audioFileDao().getByParent(editionId)
        playableFiles = files.filter { it.fileStatus == FileStatus.AVAILABLE }
        timeline = EditionTimeline(files.map { TimelineItem(it.fileUri, it.durationMs, it.fileStatus == FileStatus.AVAILABLE) })
        val progress = database.progressDao().getByParent(editionId)
        if (progress?.status == ProgressStatus.FINISHED) finishedMarkMs = timeline.durationMs
        // أولوية السرعة: سرعة الكتاب المحفوظة، وإلا السرعة الافتراضية من الإعدادات.
        val resolvedSpeed = progress?.playbackSpeed ?: appSettings.defaultSpeed.value
        val missingFirst = files.firstOrNull()?.fileStatus == FileStatus.MISSING
        if (playableFiles.isEmpty()) {
            mutableState.value = PlaybackState(editionId = editionId, durationMs = timeline.durationMs, speed = resolvedSpeed, missingFileMessage = "لا توجد ملفات صوتية متاحة لهذا الإصدار")
            return
        }
        // FIX 2.3: عنوان الكتاب في عناصر الوسائط منذ الفتح — شاشة القفل
        // (أندرويد 13+) تقرأ بيانات الجلسة مباشرة فكانت فارغة حتى الـrebuild
        // المتأخر. قراءة واحدة متزامنة (الدالة suspend أصلًا) ثم يُغنيها
        // الـrebuild لاحقًا بالفصل/المؤلف/الغلاف.
        val openTitle = runCatching {
            database.editionDao().getById(editionId)?.let { ed ->
                database.bookDao().getById(ed.bookId)?.title?.takeIf { it.isNotBlank() }
                    ?: ed.label.takeIf { it.isNotBlank() }
            }
        }.getOrNull()?.takeIf { !it.isNullOrBlank() }
        val openMetadata = openTitle?.let {
            MediaMetadata.Builder().setTitle(it).setArtist(it).setDisplayTitle(it).build()
        }
        player.setMediaItems(playableFiles.map {
            val builder = MediaItem.Builder().setUri(Uri.parse(it.fileUri)).setMediaId(it.id.toString())
            if (openMetadata != null) builder.setMediaMetadata(openMetadata)
            builder.build()
        })
        player.prepare()
        player.setPlaybackSpeed(resolvedSpeed)
        player.volume = 1f
        seekToGlobal(progress?.currentPositionMs ?: 0L)
        lastOriginalIndex = files.indexOfFirst { it.id == playableFiles.firstOrNull()?.id }
        mutableState.value = PlaybackState(
            editionId = editionId,
            isPlaying = false,
            positionMs = progress?.currentPositionMs ?: 0L,
            durationMs = timeline.durationMs,
            speed = resolvedSpeed,
            missingFileMessage = if (missingFirst) "الجزء الأول مفقود؛ بدأ التشغيل من أول ملف متاح" else null
        )
        // FIX 5: التشغيل التلقائي عند فتح المشغّل (افتراضي ON) — يُطبَّق على
        // كل مسارات الفتح (المكتبة/الإشعار/الاستئناف) لأنها كلها تمرّ هنا.
        // OFF = يبقى متوقفًا كما كان.
        if (appSettings.autoPlayOnOpen.value) play()
    }

    override fun play() {
        player.play()
    }

    /**
     * تزويد قائمة التشغيل ببيانات شاشة القفل/مركز الوسائط بعد اكتمال تحميل سياق
     * النسخة في [PlaybackService]: عنوان الفصل + اسم الكتاب (الفنّان) + السلسلة/المؤلف
     * (الألبوم) + الغلاف. تُعاد القائمة نفسها بنفس الموضع حتى لا تنقطع الجلسة.
     * أندرويد 13+ يقرأ هذه البيانات مباشرة من MediaSession (NotificationSeat).
     */
    suspend fun rebuildQueueWithMetadata(bookTitle: String, albumTitle: String?, artworkBytes: ByteArray?) {
        if (playableFiles.isEmpty()) return
        if (player.isReleased) return
        val currentEdition = editionId ?: return
        val chapters = database.chapterDao().getByParent(currentEdition)
        val items = playableFiles.map { file ->
            val fileIndex = files.indexOfFirst { it.id == file.id }.coerceAtLeast(0)
            val fileStart = files.take(fileIndex).sumOf { it.durationMs }
            val chapter = chapters.lastOrNull {
                it.startPositionMs >= fileStart && it.startPositionMs < fileStart + file.durationMs
            }
            val metadataBuilder = MediaMetadata.Builder()
                .setTitle(chapter?.title?.takeIf { it.isNotBlank() } ?: "الفصل ${fileIndex + 1}")
                .setArtist(bookTitle)
                .setAlbumTitle(albumTitle?.takeIf { it.isNotBlank() } ?: "")
                .setSubtitle(chapter?.title?.takeIf { it.isNotBlank() } ?: "")
                .setDisplayTitle(bookTitle ?: "")
                .setSubtitle(chapter?.title?.takeIf { it.isNotBlank() } ?: "")
                .setDisplayTitle(bookTitle ?: "")
            if (artworkBytes != null) metadataBuilder.setArtworkData(artworkBytes)
            MediaItem.Builder()
                .setUri(Uri.parse(file.fileUri))
                .setMediaId(file.id.toString())
                .setMediaMetadata(metadataBuilder.build())
                .build()
        }
        val index = player.currentMediaItemIndex.coerceIn(0, items.lastIndex)
        val positionMs = player.currentPosition
        runCatching {
            // PART 0 (سبب التشغيل التلقائي المكسور): كان `if (!isPlaying) pause()`
            // يقرأ حالة التدفق اللحظية — أثناء التخزين المؤقت بعد فتح جديد
            // (play() طُلب للتو وplayWhenReady=true) تكون isPlaying=false
            // فيُقتل التشغيل التلقائي. نحفظ نية التشغيل ونستعيدها حرفيًا:
            // متوقف يبقى متوقفًا، وطالب التشغيل يواصل.
            val wasPlayRequested = player.playWhenReady
            player.setMediaItems(items, index, positionMs)
            player.prepare()
            player.playWhenReady = wasPlayRequested
        }
    }

    /** FIX 6.2: بداية جلسة — تُستدعى عند أول isPlaying=true فعلي. */
    private fun beginListeningSession() {
        if (sessionStartMs != null) return
        sessionStartMs = System.currentTimeMillis()
        sessionStartPositionMs = currentGlobalPosition()
        sessionEditionId = editionId
    }

    /**
     * FIX 6.2: نهاية جلسة — صف COMPLETED واحد لكل مقطع استماع حقيقي.
     * الكتابة على IO (قاعدة الإنتاج تمنع الخيط الرئيسي).
     */
    private fun endListeningSession(reason: SessionEndReason) {
        val startMs = sessionStartMs ?: return
        val edition = sessionEditionId
        val listenedMs = (currentGlobalPosition() - sessionStartPositionMs).coerceAtLeast(0L)
        sessionStartMs = null
        sessionEditionId = null
        if (edition == null || listenedMs < MIN_LISTENING_SESSION_MS) return
        val endedAt = System.currentTimeMillis()
        sessionIoScope.launch {
            runCatching {
                database.listeningSessionDao().insert(
                    ListeningSessionEntity(
                        id = UUID.randomUUID(),
                        editionId = edition,
                        startedAt = startMs,
                        endedAt = endedAt,
                        durationListenedMs = listenedMs,
                        endReason = reason,
                        sessionState = SessionState.COMPLETED
                    )
                )
            }
        }
    }

    override fun pause() {
        player.pause()
        saveProgress()
    }
    override fun seekTo(positionMs: Long) {
        val mark = finishedMarkMs
        if (mark != null && positionMs <= mark - UNFINISH_REWIND_THRESHOLD_MS) finishedMarkMs = null
        // قفزة يدوية تُعيد ضبط كاشف العبور — لا إيقاف بعد قفزة صريحة.
        lastChapterCheckPositionMs = positionMs
        seekToGlobal(positionMs)
        saveProgress()
    }
    override fun skipForward15Seconds() {
        seekTo(currentGlobalPosition() + appSettings.skipForwardSeconds.value * 1_000L)
    }
    override fun skipBack15Seconds() {
        seekTo(currentGlobalPosition() - appSettings.skipBackwardSeconds.value * 1_000L)
    }

    override suspend fun previousChapter() {
        val id = editionId ?: return
        val chapters = database.chapterDao().getByParent(id)
        val current = currentGlobalPosition()
        val target = chapters.lastOrNull { it.startPositionMs < current - 2_000L }?.startPositionMs ?: 0L
        seekTo(target)
    }

    override suspend fun nextChapter() {
        val id = editionId ?: return
        val target = database.chapterDao().getByParent(id).firstOrNull { it.startPositionMs > currentGlobalPosition() + 1_000L }?.startPositionMs ?: timeline.durationMs
        seekTo(target)
    }

    override fun setSpeed(speed: Float) {
        val bounded = speed.coerceIn(.5f, 3f)
        player.setPlaybackSpeed(bounded)
        mutableState.value = mutableState.value.copy(speed = bounded)
        saveProgress()
    }

    override fun getVolume(): Float = player.volume

    override fun setVolume(volume: Float) {
        player.volume = volume.coerceIn(0f, 1f)
    }

    override fun setPreferredAudioDevice(device: android.media.AudioDeviceInfo?): Boolean {
        if (player.isReleased) return false
        return try {
            player.setPreferredAudioDevice(device)
            true
        } catch (t: Exception) {
            false
        }
    }

    override fun release() {
        // FIX 6.2: تصفية الجلسة المفتوحة عند موت الخدمة (سبب: إغلاق التطبيق) —
        // بنطاق مستقل لأن scope يُلغى أدناه مباشرة.
        endListeningSession(SessionEndReason.APP_CLOSED)
        saveProgress()
        saveJob?.cancel()
        player.release()
        scope.cancel()
    }

    private fun seekToGlobal(positionMs: Long) {
        val bounded = positionMs.coerceIn(0L, timeline.durationMs)
        val originalIndex = timeline.fileAt(bounded) ?: files.indexOfLast { it.fileStatus == FileStatus.AVAILABLE }
        val target = files.getOrNull(originalIndex) ?: return
        val playableIndex = playableFiles.indexOfFirst { it.id == target.id }
        if (playableIndex < 0) {
            mutableState.value = mutableState.value.copy(missingFileMessage = "الجزء المطلوب مفقود ولا يمكن تشغيله")
            player.pause()
            return
        }
        if (mutableState.value.missingFileMessage != null) {
            mutableState.value = mutableState.value.copy(missingFileMessage = null)
        }
        val offset = files.take(originalIndex).sumOf { it.durationMs }
        player.seekTo(playableIndex, (bounded - offset).coerceAtLeast(0L))
        lastOriginalIndex = originalIndex
        updateState()
    }

    /**
     * FIX 2: الموضع الحي من ExoPlayer مباشرة (لا قيمة مخزّنة قد تتقادم) —
     * أي واجهة تقرأ الموضع تحصل على الفعلي لحظة القراءة.
     */
    val currentPositionMs: Long
        get() = currentGlobalPosition()

    private fun currentGlobalPosition(): Long {
        val current = playableFiles.getOrNull(player.currentMediaItemIndex) ?: return mutableState.value.positionMs
        val originalIndex = files.indexOfFirst { it.id == current.id }
        return timeline.positionFor(originalIndex, player.currentPosition)
    }

    private fun updateState() {
        val position = currentGlobalPosition()
        mutableState.value = mutableState.value.copy(isPlaying = player.isPlaying, positionMs = position, durationMs = timeline.durationMs, editionId = editionId, speed = player.playbackParameters.speed)
        observeChapterCompletion(position)
    }

    /**
     * [R4-النقطة 2] مراقب اكتمال الفصل: عند كل تحديث موضع يفحص الفصل الحالي
     * (من قاعدة البيانات حتى تظهر الفصول المضافة أثناء التشغيل فورًا) ويسجل
     * الاكتمال بمجرد تجاوز 90% — مرة واحدة لكل فصل (P.K + IGNORE).
     */
    private fun observeChapterCompletion(positionMs: Long) {
        val id = editionId ?: return
        if (timeline.durationMs <= 0L) return
        val now = System.currentTimeMillis()
        if (now - lastChapterCompletionCheckAtMs < CHAPTER_COMPLETION_CHECK_INTERVAL_MS) return
        lastChapterCompletionCheckAtMs = now
        scope.launch(Dispatchers.IO) {
            val chapters = database.chapterDao().getByParent(id)
            chapterCompletionObserver.onPositionUpdate(id, positionMs, chapters, timeline.durationMs)
            pauseAtChapterEndIfDisabled(id, positionMs, chapters)
        }
    }

    /**
     * إيقاف عند نهاية الفصل عندما تكون المتابعة التلقائية OFF: يُطلق مرة
     * واحدة عند عبور الحد (الموضع السابق قبل النهاية والحالي بعدها)، فلا
     * يعيد الإيقاف عند ضغط التشغيل مجددًا من نفس الموضع. الفصل الأخير
     * يُترك لمنطق الإنهاء الطبيعي.
     */
    private fun pauseAtChapterEndIfDisabled(editionId: UUID, positionMs: Long, chapters: List<ChapterEntity>) {
        if (appSettings.autoNextChapter.value) {
            lastChapterCheckPositionMs = positionMs
            return
        }
        if (chapters.size < 2) {
            lastChapterCheckPositionMs = positionMs
            return
        }
        val previous = lastChapterCheckPositionMs
        lastChapterCheckPositionMs = positionMs
        if (previous < 0L) return
        val boundary = chapters.drop(1).map { it.startPositionMs }.firstOrNull { it > previous } ?: return
        if (positionMs >= boundary && previous < boundary) {
            player.pause()
        }
    }

    private fun stopAtMissingBoundaryIfNeeded() {
        val current = playableFiles.getOrNull(player.currentMediaItemIndex) ?: return
        val originalIndex = files.indexOfFirst { it.id == current.id }
        if (player.playbackState == Player.STATE_ENDED && timeline.hasMissingAfter(originalIndex)) {
            player.pause()
            mutableState.value = mutableState.value.copy(isPlaying = false, missingFileMessage = "توقف التشغيل: الملف التالي مفقود")
        }
    }

    private fun startPeriodicSave() {
        if (saveJob?.isActive == true) return
        saveJob = scope.launch {
            while (isActive && player.isPlaying) {
                delay(5_000)
                saveProgress()
            }
        }
    }

    private fun saveProgress() {
        val id = editionId ?: return
        val position = currentGlobalPosition()
        val speed = player.playbackParameters.speed
        val durationMs = timeline.durationMs
        // قراءات ExoPlayer تتم على الخيط الرئيسي (قواعد media3) قبل الانتقال إلى IO.
        val ended = player.playbackState == Player.STATE_ENDED
        val nearEnd = durationMs > 0 && position >= durationMs - COMPLETION_TOLERANCE_MS
        val isFinishedNow = durationMs > 0 && (ended || nearEnd)
        val hasFinishedMark = finishedMarkMs != null
        if (isFinishedNow) finishedMarkMs = durationMs
        scope.launch(Dispatchers.IO) {
            val existing = database.progressDao().getByParent(id)
            val wasFinished = existing?.status == ProgressStatus.FINISHED
            // اكتمال الكتاب: الإشارة الأساسية هي وصول ExoPlayer إلى STATE_ENDED،
            // والتسامح احتياطيًا بسبب فرق ترميز MP3 (توقف الموضع قبل نهاية الحاوية).
            val status = when {
                isFinishedNow -> ProgressStatus.FINISHED
                // منع الرجوع الصامت إلى IN_PROGRESS بعد FINISHED: يحافظ الكتاب على
                // حالة الإنتهاء عند إعادة التشغيل ما لم يتراجع المستخدم تراجعًا كبيرًا.
                hasFinishedMark && wasFinished -> ProgressStatus.FINISHED
                position > 0 -> ProgressStatus.IN_PROGRESS
                else -> ProgressStatus.NOT_STARTED
            }
            val progress = ListeningProgressEntity(
                id = existing?.id ?: UUID.randomUUID(), editionId = id, currentPositionMs = position,
                lastPlayedAt = System.currentTimeMillis(), status = status,
                playbackSpeed = speed, remoteId = existing?.remoteId, syncStatus = existing?.syncStatus ?: com.example.audiobook.data.room.entity.SyncStatus.LOCAL_ONLY
            )
            if (existing == null) database.progressDao().insert(progress) else database.progressDao().update(progress)
            // إشعار واحد لكل اكتمال فعلي (انتقال الجامعة إلى FINISHED لأول مرة)،
            // وBookCompletionNotifier نفسه يحرس بـ24 ساعة كحد إضافي.
            if (isFinishedNow && !wasFinished) bookCompletionNotifier.onPlaybackEnded(id)
        }
    }
}