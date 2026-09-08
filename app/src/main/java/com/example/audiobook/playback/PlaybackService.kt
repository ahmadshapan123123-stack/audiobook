package com.example.audiobook.playback

import android.content.Intent
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class PlaybackService : MediaSessionService() {
    @Inject lateinit var playbackController: PlaybackController

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession =
        (playbackController as ExoPlaybackController).mediaSession

    override fun onTaskRemoved(rootIntent: Intent?) {
        playbackController.pause()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        playbackController.release()
        super.onDestroy()
    }
}