@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.audiobook.playback

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.AudioManager
import android.os.Bundle
import android.util.LruCache
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.example.audiobook.MainActivity
import com.example.audiobook.R
import com.example.audiobook.background.reminders.ReminderScheduler
import com.example.audiobook.data.preferences.AppSettings
import com.example.audiobook.data.room.dao.AuthorDao
import com.example.audiobook.data.room.dao.BookDao
import com.example.audiobook.data.room.dao.ChapterDao
import com.example.audiobook.data.room.dao.EditionDao
import com.example.audiobook.data.room.dao.SeriesDao
import com.example.audiobook.data.room.entity.ChapterEntity
import com.example.audiobook.domain.model.AppThemeMode
import com.example.audiobook.notifications.AtherMediaNotificationProvider
import com.example.audiobook.notifications.AtherNotificationCenter
import com.example.audiobook.playback.SleepTimerPhase
import com.example.audiobook.presentation.theme.AtherAccent
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.util.UUID
import javax.inject.Inject

/**
 * خدمة MediaSessionService. تُبنى هنا MediaSession الكاملة عبر Media3 فقط:
 *
 * — [AtherMediaNotificationProvider]: إشعار تشغيل كامل (غلاف/عنوان/فصل/مؤلف + أزرار
 *   الفصل السابق، تشغيل/إيقاف، الفصل التالي، ±15 ثانية) أو مصغّر ("يتم التشغيل")
 *   عندما تُعطّل الإشعارات — فلا يُلغى إشعار الخدمة الأمامية أبدًا.
 *
 * — إجراءات مخصصة تعمل من الإشعار/شاشة القفل: تمتيل مؤقت النوم، التنقل بين الفصول، ±15.
 *
 * — إشعار مؤقت النوم المستقل (عدّاد حي + +15/+30/إلغاء) عبر [AtherNotificationCenter].
 *
 * ملاحظة إثبات صادقة: لا يوجد جهاز حقيقي/شاشة قفل في بيئة العمل هذه؛ ما يُثبت
 * اختباريًا: الأوامر مسجّلة في SessionCommands و provider يبني الإشعار الصحيح.
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    @Inject lateinit var playbackController: PlaybackController
    @Inject lateinit var sleepTimer: SleepTimerController
    @Inject lateinit var appSettings: AppSettings
    @Inject lateinit var notificationCenter: AtherNotificationCenter
    @Inject lateinit var chapterDao: ChapterDao
    @Inject lateinit var editionDao: EditionDao
    @Inject lateinit var bookDao: BookDao
    @Inject lateinit var authorDao: AuthorDao
    @Inject lateinit var seriesDao: SeriesDao
    @Inject lateinit var reminderScheduler: ReminderScheduler

    private var mediaSession: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val provider by lazy {
        AtherMediaNotificationProvider(applicationContext, appSettings)
    }

    private var chapters: List<ChapterEntity> = emptyList()
    private var openedEdition: UUID? = null
    private var currentChapterStart = -1L

    // لهجة المشغّل الحيّة + بيانات شاشة القفل (MediaMetadata) — تُحدَّث عند تبديل النسخة/الفصل.
    private var seriesColorArgb: Int? = null
    private var authorColorArgb: Int? = null
    private var latestAlbumName: String? = null
    private var artworkBytes: ByteArray? = null
    /** FIX 1 (B7): مخبأ الأغلفة المولّدة (bookId → صورة 128px). */
    private val fallbackArtworkCache = LruCache<java.util.UUID, Bitmap>(20)

    /**
     * FIX 8.1 — مستقبل فصل السماعة: بلوتوث/سلكية → إيقاف مؤقت فوري
     * (وحفظ الموضع عبر pause نفسها). يُسجَّل في onCreate ويُحرَّر في onDestroy.
     *
     * FIX 8.2 — الاعتماد على ACTION_AUDIO_BECOMING_NOISY وحده عمدًا: النظام
     * يبثّه عند فصل A2DP والسلكي معًا، فلا حاجة لمستقبل بلوتوث منفصل
     * (كان سيتطلب إذن BLUETOOTH_CONNECT على API 31+ لمكسب صفري).
     */
    private val noisyHandler by lazy {
        AudioNoisyHandler(
            isEnabled = { appSettings.pauseOnAudioDisconnect.value },
            onPause = { playbackController.pause() }
        )
    }
    private val becomingNoisyReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == AudioManager.ACTION_AUDIO_BECOMING_NOISY) {
                noisyHandler.onNoisy()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(provider)
        registerReceiver(becomingNoisyReceiver, IntentFilter(AudioManager.ACTION_AUDIO_BECOMING_NOISY))
        observeNotificationMode()
    }

    /** إعادة بناء إشعار التشغيل فورًا عند تغيير مفتاح الإشعارات أو الوضع الكامل/المصغّر. */
    private fun observeNotificationMode() {
        scope.launch { appSettings.notificationsEnabled.collect { provider.refresh() } }
        scope.launch { appSettings.mediaNotificationMinimal.collect { provider.refresh() } }
        // فواصل التخطي تغيّر تسميات الأزرار — إعادة بناء عند تغييرها.
        scope.launch { appSettings.skipForwardSeconds.collect { provider.refresh() } }
        scope.launch { appSettings.skipBackwardSeconds.collect { provider.refresh() } }
        scope.launch {
            appSettings.themeMode.collect { mode ->
                provider.accentArgb = AtherAccent.accentFor(mode, seriesColorArgb, authorColorArgb, null)
                provider.refresh()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        val cached = mediaSession
        if (cached != null) return cached
        val player = (playbackController as ExoPlaybackController).player
        return buildSession(player).also {
            mediaSession = it
            observePlayback()
            observeSleepTimer()
        }
    }

    private fun buildSession(player: Player): MediaSession =
        MediaSession.Builder(this, player)
            .setSessionActivity(sessionActivityIntent())
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
                    MediaSession.ConnectionResult.accept(
                        combinedCommands(),
                        player.availableCommands
                    )

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    action: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    when (action.customAction) {
                        PlaybackSessionCommands.ACTION_PREVIOUS_CHAPTER -> scope.launch { playbackController.previousChapter() }
                        PlaybackSessionCommands.ACTION_NEXT_CHAPTER -> scope.launch { playbackController.nextChapter() }
                        PlaybackSessionCommands.ACTION_SKIP_FORWARD_15 -> playbackController.skipForward15Seconds()
                        PlaybackSessionCommands.ACTION_SKIP_BACK_15 -> playbackController.skipBack15Seconds()
                        else -> {
                            val minutes = SleepTimerCommands.extendMinutesFor(action)
                            if (minutes != null) {
                                sleepTimer.extendBy(minutes)
                                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                            }
                            val decrease = SleepTimerCommands.decreaseMinutesFor(action)
                            if (decrease != null) {
                                sleepTimer.decreaseBy(decrease)
                                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                            }
                            if (SleepTimerCommands.isCancelAction(action)) {
                                sleepTimer.cancel()
                                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                            }
                            if (SleepTimerCommands.isSleepOpenAction(action)) {
                                // PHASE 2: فتح المشغل على لوحة النوم عبر مسار
                                // التنقل نفسه الذي تستخدمه نقرات الإشعار.
                                openedEdition?.let { id ->
                                    val intent = Intent(this@PlaybackService, MainActivity::class.java).apply {
                                        putExtra(
                                            AtherNotificationCenter.EXTRA_ROUTE,
                                            "player/$id?panel=SLEEP"
                                        )
                                        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
                                    }
                                    runCatching { startActivity(intent) }
                                }
                                return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                            }
                            return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
                        }
                    }
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            })
            .setCustomLayout(customLockScreenLayout())
            .setMediaButtonPreferences(customLockScreenLayout())
            .build()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val action = intent?.action
        if (action == AtherNotificationCenter.SLEEP_ACTION) {
            val minutes = intent?.getIntExtra(AtherNotificationCenter.SLEEP_ACTION_MINUTES, 0) ?: 0
            when {
                minutes > 0 -> sleepTimer.extendBy(minutes)
                minutes < 0 -> sleepTimer.cancel()
            }
        }
        return super.onStartCommand(intent, flags, startId)
    }

    /** مراقبة حالة التشغيل: تحديث ثيم إشعار التشغيل (عند تبديل الفصل/النسخة/التشغيل) فقط — لا تكرار. */
    private fun observePlayback() {
        var wasPlaying = false
        var lastPlaying = false
        // FIX 1 (Phase 7): شريط تقدم الإشعار المخصص يُبنى من الموضع لحظة
        // البناء فقط — فكان يتجمد داخل الفصل الواحد ("الإشعار لا يتغير").
        // الحالة تتدفق كل 500ms أثناء التشغيل، فنُعيد البناء مرة كل ثانية.
        var lastProgressSec = -1L
        scope.launch {
            playbackController.state.collect { state ->
                if (wasPlaying && !state.isPlaying) reminderScheduler.rearmResumeReminder()
                wasPlaying = state.isPlaying
                val playingToggled = state.isPlaying != lastPlaying
                lastPlaying = state.isPlaying
                val id = state.editionId
                if (id != null && id != openedEdition) {
                    openedEdition = id
                    loadEditionContext(id)
                }
                provider.playing = state.isPlaying
                if (playingToggled) {
                    provider.refresh()
                    // إعادة ضبط عتبة الثانية عند التبديل حتى يتحدث الشريط فورًا.
                    lastProgressSec = if (state.isPlaying) state.positionMs / 1000L else -1L
                }
                val positionSec = state.positionMs / 1000L
                if (state.isPlaying && positionSec != lastProgressSec) {
                    lastProgressSec = positionSec
                    provider.refresh()
                }
                if (id != null && chapters.isNotEmpty()) {
                    val current = chapters.lastOrNull { it.startPositionMs <= state.positionMs }
                    val start = current?.startPositionMs ?: 0L
                    if (start != currentChapterStart) {
                        currentChapterStart = start
                        // FIX-N1: عنوان فصل يطابق الكتاب = فراغ (الشريط يعرض
                        // المؤلف بديلًا) حتى لا يتكرر العنوان سطرين.
                        val label = chapterLabel(current, chapters.indexOf(current))
                        provider.contentChapter =
                            if (label.isNotBlank() && label == provider.contentTitle) "" else label
                        provider.refresh()
                    }
                }
            }
        }
    }

    /** تحميل بيانات النسخة (العنوان/المؤلف/الغلاف/السلسلة) وتتبع فصولها للعرض الحي. */
    private suspend fun loadEditionContext(editionId: UUID) {
        chapters = runCatching { chapterDao.getByParent(editionId) }.getOrDefault(emptyList())
        currentChapterStart = -1L
        val edition = runCatching { editionDao.getById(editionId) }.getOrNull()
        val book = edition?.let { runCatching { bookDao.getById(it.bookId) }.getOrNull() }
        val author = book?.authorId?.let { id -> runCatching { authorDao.getById(id) }.getOrNull() }
        val series = book?.seriesId?.let { runCatching { seriesDao.getById(it) }.getOrNull() }
        provider.contentTitle = book?.title ?: ""
        provider.contentAuthor = author?.name ?: ""
        latestAlbumName = series?.name
        seriesColorArgb = parseColorArgb(series?.colorTheme)
        authorColorArgb = parseColorArgb(author?.colorTheme)
        provider.accentArgb = AtherAccent.accentFor(appSettings.currentThemeMode(), seriesColorArgb, authorColorArgb, null)
        val coverPath = book?.coverImagePath
        val bitmap = coverPath?.let { path ->
            withContext(Dispatchers.IO) { runCatching { BitmapFactory.decodeFile(path) }.getOrNull() }
        }
        // FIX 1 (B7): بلا غلاف → مولّد (حرف + لهجة) مخبأ لكل كتاب، لا أيقونة
        // التطبيق. التوليد على IO؛ المخبأ LruCache آمن خيطيًا (20 كتابًا).
        val largeIcon = bitmap ?: book?.let { b ->
            withContext(Dispatchers.IO) {
                fallbackArtworkCache.get(b.id)
                    ?: com.example.audiobook.notifications.NotificationLargeIcons.letterArtwork(
                        b.title,
                        provider.accentArgb
                    ).also { fallbackArtworkCache.put(b.id, it) }
            }
        }
        provider.artwork = largeIcon
        withContext(Dispatchers.IO) {
            artworkBytes = largeIcon?.let { b ->
                runCatching {
                    ByteArrayOutputStream().use { out ->
                        if (b.compress(Bitmap.CompressFormat.PNG, 100, out)) out.toByteArray() else null
                    }
                }.getOrNull()
            }
        }
        (playbackController as ExoPlaybackController).rebuildQueueWithMetadata(
            bookTitle = provider.contentTitle,
            albumTitle = latestAlbumName ?: provider.contentAuthor,
            artworkBytes = artworkBytes,
            // FIX-N1: المؤلف للسطر الثاني في القالب النظامي.
            authorName = provider.contentAuthor.ifBlank { null }
        )
        // FIX 2.1+2.2: التحديث بعد حقن البيانات — الإشعار كان يُنشر باكرًا
        // بحقول فارغة ولا يُحدَّث بعد وصول البيانات (الدوري يعمل أثناء
        // التشغيل فقط). مرة واحدة لكل نسخة: بلا إزعاج، ويعمل حتى متوقفًا.
        provider.refresh()
        scope.launch {
            chapterDao.observeByParent(editionId).collect { updated ->
                chapters = updated
                provider.refresh()
            }
        }
    }

    private fun parseColorArgb(hex: String?): Int? {
        if (hex.isNullOrBlank()) return null
        return runCatching { android.graphics.Color.parseColor(hex) }.getOrNull()
    }

    private fun chapterLabel(chapter: ChapterEntity?, index: Int): String =
        chapter?.title?.takeIf { it.isNotBlank() } ?: "الفصل ${index + 1}"

    /** مراقبة مؤقت النوم: عدّاد حي في إشعار مستقل، يُزال عند التوقف/الإلغاء، + تحديث حيّ لشاشة القفل. */
    private fun observeSleepTimer() {
        scope.launch {
            sleepTimer.uiState.collect { state ->
                refreshLockScreenLayout()
                // PHASE 2: عدّاد النوم في اللوحة الموسّعة — يُحدَّث مع كل نبضة.
                val active = state.phase == SleepTimerPhase.RUNNING ||
                    state.phase == SleepTimerPhase.WARNING_WINDOW ||
                    state.phase == SleepTimerPhase.FADING_OUT
                val remaining = state.remainingMs
                provider.sleepCountdownText =
                    if (active && remaining != null && remaining > 0L) {
                        getString(R.string.notif_sleep_remaining, formatSleepClock(remaining))
                    } else null
                // FIX-N2: عدّاد القفل في subText النظامي — يُحدَّث مع كل نبضة
                // (النبضة كل ثانية أثناء النوم) ويُمسح عند التوقف.
                provider.subTextOverride =
                    if (active && remaining != null && remaining > 0L) {
                        getString(R.string.notif_sleep_lock_countdown, formatSleepClock(remaining))
                    } else null
                provider.refresh()
                when (state.phase) {
                    SleepTimerPhase.RUNNING,
                    SleepTimerPhase.WARNING_WINDOW,
                    SleepTimerPhase.FADING_OUT -> state.remainingMs?.let { notificationCenter.postSleepTimer(it) }
                    SleepTimerPhase.IDLE,
                    SleepTimerPhase.STOPPED -> notificationCenter.cancelSleepTimer()
                }
            }
        }
    }

    /** MINI-FIX: ساعة ذكية لعدّاد النوم في الإشعار/القفل — أقل من ساعة "MM:SS"، وساعة فأكثر "H:MM:SS". */
    private fun formatSleepClock(ms: Long): String {
        val totalSeconds = (ms / 1_000L).coerceAtLeast(0L)
        val hours = totalSeconds / 3_600L
        val minutes = (totalSeconds % 3_600L) / 60L
        val seconds = totalSeconds % 60L
        return if (hours > 0L) "%d:%02d:%02d".format(hours, minutes, seconds)
        else "%02d:%02d".format(minutes, seconds)
    }

    /**
     * الأزرار الديناميكية لشاشة القفل/الإشعار: أثناء فعالية مؤقت النوم أو نافذة التحذير/الخبو
     * تُعطى أزرار المؤقّت الأولوية (تمديد +15/+30/+60 + إلغاء) ليصل المستخدم إليها فورًا،
     * وإلا: النقل الأربعة + زر فتح لوحة النوم (هلال) خامسًا — والتشغيل/الإيقاف يضيفه النظام.
     * يُعاد تطبيقها حيًّا عند كل تغيّر طور.
     */
    private fun customLockScreenLayout(): List<CommandButton> {
        val phase = sleepTimer.uiState.value.phase
        val timerPriority = phase == SleepTimerPhase.RUNNING ||
            phase == SleepTimerPhase.WARNING_WINDOW ||
            phase == SleepTimerPhase.FADING_OUT
        return if (timerPriority) SleepTimerCommands.customButtons()
        else PlaybackSessionCommands.notificationButtons(
            forwardSeconds = appSettings.skipForwardSeconds.value,
            backwardSeconds = appSettings.skipBackwardSeconds.value
        ) + SleepTimerCommands.sleepOpenButton(R.drawable.ic_sleep)
    }

    /** إعادة تطبيق الأزرار الحيّة على الجلسة الدّوّارة دون إيقاف التشغيل. */
    private fun refreshLockScreenLayout() {
        val session = mediaSession ?: return
        try {
            session.setCustomLayout(customLockScreenLayout())
            session.setMediaButtonPreferences(customLockScreenLayout())
        } catch (_: IllegalStateException) {
            // الجلسة قيد البناء/التحرير — تتجاهل المكالمة، وسيُطبَّق الـ layout لاحقًا عند baseline.
        }
    }

    private fun combinedCommands() =
        androidx.media3.session.SessionCommands.Builder()
            .apply {
                SleepTimerCommands.commands().forEach { add(it) }
                add(androidx.media3.session.SessionCommand(SleepTimerCommands.ACTION_SLEEP_OPEN, Bundle()))
                PlaybackSessionCommands.commands().forEach { add(it) }
            }
            .build()

    private fun sessionActivityIntent(): PendingIntent {
        val intent = Intent(this, MainActivity::class.java)
        return PendingIntent.getActivity(
            this, 900,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        playbackController.pause()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(becomingNoisyReceiver) }
        notificationCenter.cancelSleepTimer()
        scope.cancel()
        mediaSession?.release()
        playbackController.release()
        super.onDestroy()
    }
}