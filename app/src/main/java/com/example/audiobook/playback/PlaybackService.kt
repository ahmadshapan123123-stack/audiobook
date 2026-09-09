package com.example.audiobook.playback

import android.content.Intent
import android.os.Bundle
import androidx.media3.common.Player
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionResult
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * خدمة MediaSessionService. هنا يُبني MediaSession بأزرار مخصصة فعلية
 * (+15/+30/+60 دقيقة) تعمل من الإشعار/شاشة القفل دون فتح التطبيق:
 * تُسجَّل الأوامر في ConnectionResult وتُعالَج في onCustomCommand عبر
 * [SleepTimerController] — التمديد يتم حتى لو كان التطبيق في الخلفية.
 *
 * ملاحظة إثبات صادقة: لا يوجد جهاز حقيقي/شاشة قفل في بيئة العمل هذه، لذا لا
 * يمكن إثبات الظهور الفعلي للزر على شاشة قفل. ما يُثبت اختباريًا: الأوامر
 * مسجّلة في SessionCommands و[extendMinutesFor] يستدعي المنطق الصحيح
 * (+15/+30/+60) عبر [SleepTimerController.extendBy].
 */
@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    @Inject lateinit var playbackController: PlaybackController
    @Inject lateinit var sleepTimer: SleepTimerController

    private var mediaSession: MediaSession? = null

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession {
        val cached = mediaSession
        if (cached != null) return cached
        val player = (playbackController as ExoPlaybackController).player
        return buildSession(player).also { mediaSession = it }
    }

    private fun buildSession(player: Player): MediaSession =
        MediaSession.Builder(this, player)
            .setCallback(object : MediaSession.Callback {
                override fun onConnect(session: MediaSession, controller: MediaSession.ControllerInfo): MediaSession.ConnectionResult =
                    MediaSession.ConnectionResult.accept(
                        SleepTimerCommands.sessionCommands(),
                        player.availableCommands
                    )

                override fun onCustomCommand(
                    session: MediaSession,
                    controller: MediaSession.ControllerInfo,
                    action: SessionCommand,
                    args: Bundle
                ): ListenableFuture<SessionResult> {
                    val minutes = SleepTimerCommands.extendMinutesFor(action)
                        ?: return Futures.immediateFuture(SessionResult(SessionResult.RESULT_ERROR_BAD_VALUE))
                    sleepTimer.extendBy(minutes)
                    return Futures.immediateFuture(SessionResult(SessionResult.RESULT_SUCCESS))
                }
            })
            .setCustomLayout(SleepTimerCommands.customButtons())
            .setMediaButtonPreferences(SleepTimerCommands.customButtons())
            .build()

    override fun onTaskRemoved(rootIntent: Intent?) {
        playbackController.pause()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        mediaSession?.release()
        playbackController.release()
        super.onDestroy()
    }
}