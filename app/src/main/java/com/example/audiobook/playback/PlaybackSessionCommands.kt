package com.example.audiobook.playback

import android.os.Bundle
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands

/**
 * إجراءات التشغيل المخصصة على MediaSession: الفصل السابق/التالي و ±15 ثانية.
 * تعمل من إشعار التشغيل و شاشة القفل دون فتح التطبيق، وتُعالَج في
 * [PlaybackService.onCustomCommand] عبر [PlaybackController] مباشرة.
 */
object PlaybackSessionCommands {
    const val ACTION_PREVIOUS_CHAPTER = "com.example.audiobook.playback.previous_chapter"
    const val ACTION_NEXT_CHAPTER = "com.example.audiobook.playback.next_chapter"
    const val ACTION_SKIP_FORWARD_15 = "com.example.audiobook.playback.skip_forward_15"
    const val ACTION_SKIP_BACK_15 = "com.example.audiobook.playback.skip_back_15"

    private val COMMANDS = listOf(
        SessionCommand(ACTION_PREVIOUS_CHAPTER, Bundle()),
        SessionCommand(ACTION_NEXT_CHAPTER, Bundle()),
        SessionCommand(ACTION_SKIP_FORWARD_15, Bundle()),
        SessionCommand(ACTION_SKIP_BACK_15, Bundle())
    )

    fun isPlaybackAction(action: SessionCommand): Boolean =
        COMMANDS.any { it.customAction == action.customAction }

    fun sessionCommands(): SessionCommands =
        SessionCommands.Builder().apply { COMMANDS.forEach { add(it) } }.build()

    fun commands(): List<SessionCommand> = COMMANDS

    /** أزرار تظهر في إشعار التشغيل الكامل (الفصل السابق، +15، التالي، −15). */
    fun notificationButtons(): List<CommandButton> = listOf(
        CommandButton.Builder()
            .setSessionCommand(COMMANDS[0])
            .setDisplayName("الفصل السابق")
            .setIconResId(CommandButton.getIconResIdForIconConstant(CommandButton.ICON_PREVIOUS))
            .setEnabled(true)
            .build(),
        CommandButton.Builder()
            .setSessionCommand(COMMANDS[2])
            .setDisplayName("+15")
            .setIconResId(CommandButton.getIconResIdForIconConstant(CommandButton.ICON_SKIP_FORWARD_15))
            .setEnabled(true)
            .build(),
        CommandButton.Builder()
            .setSessionCommand(COMMANDS[1])
            .setDisplayName("الفصل التالي")
            .setIconResId(CommandButton.getIconResIdForIconConstant(CommandButton.ICON_NEXT))
            .setEnabled(true)
            .build(),
        CommandButton.Builder()
            .setSessionCommand(COMMANDS[3])
            .setDisplayName("-15")
            .setIconResId(CommandButton.getIconResIdForIconConstant(CommandButton.ICON_SKIP_BACK_15))
            .setEnabled(true)
            .build()
    )
}