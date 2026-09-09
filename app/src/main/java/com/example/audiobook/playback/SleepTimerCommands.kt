package com.example.audiobook.playback

import android.os.Bundle
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands

/**
 * الإجراءات المخصصة على MediaSession (+15/+30/+60 دقيقة) التي تعمل من
 * الإشعار/شاشة القفل دون فتح التطبيق. خريطة نقية قابلة للاختبار:
 * [extendMinutesFor] يحوّل SessionCommand إلى دقائق التمديد الصحيحة.
 */
object SleepTimerCommands {
    const val ACTION_EXTEND_15 = "com.example.audiobook.sleep.extend.15"
    const val ACTION_EXTEND_30 = "com.example.audiobook.sleep.extend.30"
    const val ACTION_EXTEND_60 = "com.example.audiobook.sleep.extend.60"

    private val COMMANDS = listOf(
        SessionCommand(ACTION_EXTEND_15, Bundle()),
        SessionCommand(ACTION_EXTEND_30, Bundle()),
        SessionCommand(ACTION_EXTEND_60, Bundle())
    )

    fun extendMinutesFor(action: SessionCommand): Int? = when (action.customAction) {
        ACTION_EXTEND_15 -> 15
        ACTION_EXTEND_30 -> 30
        ACTION_EXTEND_60 -> 60
        else -> null
    }

    fun sessionCommands(): SessionCommands =
        SessionCommands.Builder().apply { COMMANDS.forEach { add(it) } }.build()

    fun customButtons(): List<CommandButton> = COMMANDS.map { command ->
        CommandButton.Builder()
            .setSessionCommand(command)
            .setDisplayName(command.customAction.substringAfterLast('.'))
            .setEnabled(true)
            .build()
    }
}