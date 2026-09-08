package com.example.audiobook.playback

import android.content.Context
import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.session.MediaSession
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.entity.AudioFileEntity
import com.example.audiobook.data.room.entity.FileStatus
import com.example.audiobook.data.room.entity.ListeningProgressEntity
import com.example.audiobook.data.room.entity.ProgressStatus
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
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ExoPlaybackController @Inject constructor(
    @ApplicationContext context: Context,
    private val database: AppDatabase
) : PlaybackController {
    private val player = ExoPlayer.Builder(context.applicationContext).build()
    val mediaSession: MediaSession = MediaSession.Builder(context.applicationContext, player).build()
    private val scope = CoroutineScope(Dispatchers.Main.immediate + Job())
    private val mutableState = MutableStateFlow(PlaybackState())
    override val state: StateFlow<PlaybackState> = mutableState.asStateFlow()
    private var editionId: UUID? = null
    private var files: List<AudioFileEntity> = emptyList()
    private var playableFiles: List<AudioFileEntity> = emptyList()
    private var timeline: EditionTimeline = EditionTimeline(emptyList())
    private var lastOriginalIndex = -1
    private var saveJob: Job? = null
    private var sleepJob: Job? = null
    private var sleepRemainingMs: Long? = null

    init {
        player.addListener(object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                updateState()
                if (isPlaying) startPeriodicSave()
                else saveProgress()
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
        })
        scope.launch {
            while (isActive) {
                delay(500)
                if (player.isPlaying) updateState()
            }
        }
    }

    override suspend fun openEdition(editionId: UUID) {
        this.editionId = editionId
        files = database.audioFileDao().getByParent(editionId)
        playableFiles = files.filter { it.fileStatus == FileStatus.AVAILABLE }
        timeline = EditionTimeline(files.map { TimelineItem(it.fileUri, it.durationMs, it.fileStatus == FileStatus.AVAILABLE) })
        val progress = database.progressDao().getByParent(editionId)
        val missingFirst = files.firstOrNull()?.fileStatus == FileStatus.MISSING
        if (playableFiles.isEmpty()) {
            mutableState.value = PlaybackState(editionId = editionId, durationMs = timeline.durationMs, speed = progress?.playbackSpeed ?: 1f, missingFileMessage = "لا توجد ملفات صوتية متاحة لهذا الإصدار")
            return
        }
        player.setMediaItems(playableFiles.map { MediaItem.Builder().setUri(Uri.parse(it.fileUri)).setMediaId(it.id.toString()).build() })
        player.setPlaybackSpeed(progress?.playbackSpeed ?: 1f)
        seekToGlobal(progress?.currentPositionMs ?: 0L)
        lastOriginalIndex = files.indexOfFirst { it.id == playableFiles.firstOrNull()?.id }
        mutableState.value = PlaybackState(
            editionId = editionId,
            isPlaying = false,
            positionMs = progress?.currentPositionMs ?: 0L,
            durationMs = timeline.durationMs,
            speed = progress?.playbackSpeed ?: 1f,
            missingFileMessage = if (missingFirst) "الجزء الأول مفقود؛ بدأ التشغيل من أول ملف متاح" else null
        )
    }

    override fun play() { player.play() }
    override fun pause() { player.pause(); saveProgress() }
    override fun seekTo(positionMs: Long) { seekToGlobal(positionMs); saveProgress() }
    override fun skipForward15Seconds() { seekTo(currentGlobalPosition() + 15_000L) }
    override fun skipBack15Seconds() { seekTo(currentGlobalPosition() - 15_000L) }

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

    override fun setSleepTimer(minutes: Int) {
        sleepJob?.cancel()
        if (minutes <= 0) {
            cancelSleepTimer()
            return
        }
        sleepRemainingMs = minutes * 60_000L
        sleepJob = scope.launch {
            while (isActive && (sleepRemainingMs ?: 0L) > 0L) {
                delay(1_000)
                sleepRemainingMs = ((sleepRemainingMs ?: 0L) - 1_000L).coerceAtLeast(0L)
                mutableState.value = mutableState.value.copy(sleepRemainingMs = sleepRemainingMs)
            }
            if ((sleepRemainingMs ?: 0L) == 0L) {
                pause()
                sleepRemainingMs = null
                mutableState.value = mutableState.value.copy(sleepRemainingMs = null)
            }
        }
        mutableState.value = mutableState.value.copy(sleepRemainingMs = sleepRemainingMs)
    }

    override fun cancelSleepTimer() {
        sleepJob?.cancel()
        sleepRemainingMs = null
        mutableState.value = mutableState.value.copy(sleepRemainingMs = null)
    }

    override fun release() {
        saveProgress()
        saveJob?.cancel()
        sleepJob?.cancel()
        mediaSession.release()
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
        val offset = files.take(originalIndex).sumOf { it.durationMs }
        player.seekTo(playableIndex, (bounded - offset).coerceAtLeast(0L))
        lastOriginalIndex = originalIndex
        updateState()
    }

    private fun currentGlobalPosition(): Long {
        val current = playableFiles.getOrNull(player.currentMediaItemIndex) ?: return mutableState.value.positionMs
        val originalIndex = files.indexOfFirst { it.id == current.id }
        return timeline.positionFor(originalIndex, player.currentPosition)
    }

    private fun updateState() {
        mutableState.value = mutableState.value.copy(isPlaying = player.isPlaying, positionMs = currentGlobalPosition(), durationMs = timeline.durationMs, editionId = editionId, speed = player.playbackParameters.speed)
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
        scope.launch(Dispatchers.IO) {
            val existing = database.progressDao().getByParent(id)
            val progress = ListeningProgressEntity(
                id = existing?.id ?: UUID.randomUUID(), editionId = id, currentPositionMs = position,
                lastPlayedAt = System.currentTimeMillis(), status = if (position >= timeline.durationMs && timeline.durationMs > 0) ProgressStatus.FINISHED else if (position > 0) ProgressStatus.IN_PROGRESS else ProgressStatus.NOT_STARTED,
                playbackSpeed = player.playbackParameters.speed, remoteId = existing?.remoteId, syncStatus = existing?.syncStatus ?: com.example.audiobook.data.room.entity.SyncStatus.LOCAL_ONLY
            )
            if (existing == null) database.progressDao().insert(progress) else database.progressDao().update(progress)
        }
    }
}