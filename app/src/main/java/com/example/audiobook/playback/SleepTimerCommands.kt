package com.example.audiobook.playback

import android.os.Bundle
import androidx.media3.session.CommandButton
import androidx.media3.session.SessionCommand
import androidx.media3.session.SessionCommands

/**
 * الإجراءات المخصصة على MediaSession (زيادة +15/+30/+60، إنقاص −5/−10/−15، إلغاء)
 * التي تعمل من الإشعار/شاشة القفل دون فتح التطبيق، وكلها موصولة مباشرة
 * بـ [SleepTimerController]. خريطة نقية قابلة للاختبار:
 * [extendMinutesFor] / [decreaseMinutesFor] / [isCancelAction] يفسّرون
 * SessionCommand إلى الإجراء الفعلي على المؤقت.
 */
object SleepTimerCommands {
    const val ACTION_EXTEND_15 = "com.example.audiobook.sleep.extend.15"
    const val ACTION_EXTEND_30 = "com.example.audiobook.sleep.extend.30"
    const val ACTION_EXTEND_60 = "com.example.audiobook.sleep.extend.60"
    const val ACTION_DECREASE_5 = "com.example.audiobook.sleep.decrease.5"
    const val ACTION_DECREASE_10 = "com.example.audiobook.sleep.decrease.10"
    const val ACTION_DECREASE_15 = "com.example.audiobook.sleep.decrease.15"
    const val ACTION_CANCEL = "com.example.audiobook.sleep.cancel"

    private val COMMANDS = listOf(
        SessionCommand(ACTION_EXTEND_15, Bundle()),
        SessionCommand(ACTION_EXTEND_30, Bundle()),
        SessionCommand(ACTION_EXTEND_60, Bundle()),
        SessionCommand(ACTION_DECREASE_5, Bundle()),
        SessionCommand(ACTION_DECREASE_10, Bundle()),
        SessionCommand(ACTION_DECREASE_15, Bundle()),
        SessionCommand(ACTION_CANCEL, Bundle())
    )

    fun extendMinutesFor(action: SessionCommand): Int? = when (action.customAction) {
        ACTION_EXTEND_15 -> 15
        ACTION_EXTEND_30 -> 30
        ACTION_EXTEND_60 -> 60
        else -> null
    }

    fun commands(): List<SessionCommand> = COMMANDS

    fun sessionCommands(): SessionCommands =
        SessionCommands.Builder().apply { COMMANDS.forEach { add(it) } }.build()

    fun decreaseMinutesFor(action: SessionCommand): Int? = when (action.customAction) {
        ACTION_DECREASE_5 -> 5
        ACTION_DECREASE_10 -> 10
        ACTION_DECREASE_15 -> 15
        else -> null
    }

    fun isCancelAction(action: SessionCommand): Boolean = action.customAction == ACTION_CANCEL

    fun customButtons(): List<CommandButton> = COMMANDS.map { command ->
        val icon = when (command.customAction) {
            ACTION_EXTEND_15, ACTION_EXTEND_30, ACTION_EXTEND_60 -> CommandButton.ICON_PLUS
            ACTION_DECREASE_5, ACTION_DECREASE_10, ACTION_DECREASE_15 -> CommandButton.ICON_MINUS
            else -> CommandButton.ICON_STOP
        }
        CommandButton.Builder()
            .setSessionCommand(command)
            .setDisplayName(command.customAction.substringAfterLast('.'))
            .setIconResId(CommandButton.getIconResIdForIconConstant(icon))
            .setEnabled(true)
            .build()
    }
}