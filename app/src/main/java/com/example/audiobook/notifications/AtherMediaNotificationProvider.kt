package com.example.audiobook.notifications

import android.content.Context
import android.graphics.Bitmap
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.example.audiobook.R
import com.example.audiobook.data.preferences.AppSettings
import com.example.audiobook.playback.PlaybackSessionCommands
import com.google.common.collect.ImmutableList

/**
 * إشعار التشغيل عبر Media3 فقط (ممنوع إشعار يدوي للوسائط). وضعان:
 *
 * 1. كامل ([NotificationChannels.PLAYBACK_FULL]): غلاف + عنوان الكتاب + الفصل الحالي
 *    + المؤلف، مع أزرار الفصل السابق، تشغيل/إيقاف، الفصل التالي، ±15 ثانية،
 *    وأسلوب Media3 ([MediaStyleNotificationHelper.MediaStyle]) لشاشة القفل والعرض المصغّر.
 *
 * 2. مصغّر ([NotificationChannels.PLAYBACK_MINIMAL]): "يتم التشغيل" بلا أزرار ولا غلاف.
 *    يُستعمل عندما تكون [AppSettings.notificationsEnabled] = false — فلا يُلغى إشعار
 *    الخدمة الأمامية أبدًا (يُظهر النظام إشعار FGS حتى لو حُذف المستخدم).
 *
 * المعرّفات تعتمد على Media3 [MediaNotification.notificationId] نفسه.
 */
class AtherMediaNotificationProvider(
    private val context: Context,
    private val appSettings: AppSettings
) : MediaNotification.Provider {

    private var session: MediaSession? = null
    private var actionFactory: MediaNotification.ActionFactory? = null
    private var callback: MediaNotification.Provider.Callback? = null

    @Volatile var contentTitle: String = ""
    @Volatile var contentChapter: String = ""
    @Volatile var contentAuthor: String = ""
    @Volatile var artwork: Bitmap? = null
    @Volatile var playing: Boolean = false

    /** تحديث ثيم الإشعار (عند تبديل الفصل) يُعيد بناءه فورًا دون إيقاف الخدمة. */
    fun refresh() {
        val s = session ?: return
        val f = actionFactory ?: return
        val c = callback ?: return
        c.onNotificationChanged(createNotification(s, ImmutableList.of(), f, c))
    }

    override fun getNotificationChannelInfo(): MediaNotification.Provider.NotificationChannelInfo {
        val channel = if (useMinimal()) NotificationChannels.PLAYBACK_MINIMAL else NotificationChannels.PLAYBACK_FULL
        return MediaNotification.Provider.NotificationChannelInfo(channel, context.getString(R.string.channel_playback_full_name))
    }

    override fun createNotification(
        session: MediaSession,
        commandButtons: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory,
        callback: MediaNotification.Provider.Callback
    ): MediaNotification {
        this.session = session
        this.actionFactory = actionFactory
        this.callback = callback
        playing = session.player.isPlaying
        return MediaNotification(
            NotificationChannels.ID_PLAYBACK,
            if (useMinimal()) buildMinimal(session, actionFactory) else buildFull(session, commandButtons, actionFactory)
        )
    }

    override fun handleCustomCommand(session: MediaSession, action: String, args: android.os.Bundle): Boolean = false

    private fun useMinimal(): Boolean =
        !appSettings.notificationsEnabled.value || appSettings.mediaNotificationMinimal.value

    private fun buildFull(
        session: MediaSession,
        commandButtons: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory
    ): android.app.Notification {
        val isPlaying = playing
        val builder = NotificationCompat.Builder(context, NotificationChannels.PLAYBACK_FULL)
            .setSmallIcon(R.drawable.ic_stat_ather)
            .setContentTitle(contentTitle.ifBlank { context.getString(R.string.channel_playback_full_name) })
            .setContentText(contentChapter.ifBlank { contentAuthor })
            .setSubText(contentAuthor)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setOngoing(session.player.playWhenReady)
            .setContentIntent(session.getSessionActivity())
            .setDeleteIntent(actionFactory.createNotificationDismissalIntent(session))
        artwork?.let { builder.setLargeIcon(it) }

        val playPauseIcon = if (isPlaying) CommandButton.ICON_PAUSE else CommandButton.ICON_PLAY
        val playPauseLabel = if (isPlaying) context.getString(R.string.notif_action_pause) else context.getString(R.string.notif_action_play)
        val playPauseAction = actionFactory.createMediaAction(
            session,
            IconCompat.createWithResource(context, CommandButton.getIconResIdForIconConstant(playPauseIcon)),
            playPauseLabel,
            Player.COMMAND_PLAY_PAUSE
        )
        val staticButtons = PlaybackSessionCommands.notificationButtons()
        val fullActions = listOf(
            actionFor(staticButtons[0], actionFactory),
            playPauseAction,
            actionFor(staticButtons[2], actionFactory),
            actionFor(staticButtons[3], actionFactory),
            actionFor(staticButtons[1], actionFactory)
        )
        fullActions.forEach { builder.addAction(it) }
        builder.setStyle(
            MediaStyleNotificationHelper.MediaStyle(session)
                .setShowActionsInCompactView(0, 1, 2)
        )
        return builder.build()
    }

    private fun buildMinimal(session: MediaSession, actionFactory: MediaNotification.ActionFactory): android.app.Notification =
        NotificationCompat.Builder(context, NotificationChannels.PLAYBACK_MINIMAL)
            .setSmallIcon(R.drawable.ic_stat_ather)
            .setContentTitle(context.getString(R.string.notif_playback_minimal_title))
            .setContentText(context.getString(R.string.notif_playback_minimal_text))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setOngoing(true)
            .setContentIntent(session.getSessionActivity())
            .setDeleteIntent(actionFactory.createNotificationDismissalIntent(session))
            .build()

    private fun actionFor(button: CommandButton, actionFactory: MediaNotification.ActionFactory) =
        actionFactory.createCustomActionFromCustomCommandButton(requireNotNull(session), button)
}