@file:androidx.annotation.OptIn(androidx.media3.common.util.UnstableApi::class)

package com.example.audiobook.notifications

import android.content.Context
import android.graphics.Bitmap
import android.widget.RemoteViews
import androidx.core.app.NotificationCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.media3.common.Player
import androidx.media3.session.CommandButton
import androidx.media3.session.MediaNotification
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaStyleNotificationHelper
import com.example.audiobook.R
import com.example.audiobook.data.preferences.AppSettings
import com.example.audiobook.domain.model.AppThemeMode
import com.example.audiobook.playback.PlaybackSessionCommands
import com.example.audiobook.presentation.theme.AtherAccent
import com.example.audiobook.presentation.theme.AtherCosmic
import com.google.common.collect.ImmutableList

/**
 * إشعار التشغيل عبر Media3 فقط (ممنوع إشعار يدوي للوسائط). وضعان:
 *
 * 1. كامل ([NotificationChannels.PLAYBACK_FULL]): هوية أثير الكاملة — تخطيطان
 *    كونيان مخصصان (مطوي/موسّع) بخلفية "أثير" الكونية، شريط أكسنت علوي، غلاف
 *    الكتاب، العنوان/الفصل/المؤلف، شريط تقدّم وأزرار (±15، فصول، تشغيل) ملوّنة
 *    باللهجة الديناميكية نفسها التي يستخدمها المشغّل ([AtherAccent]).
 *    على شاشة القفل تُضمّن بيانات MediaSession (العنوان/الفصل/الغلاف/اللون).
 *
 * 2. مصغّر ([NotificationChannels.PLAYBACK_MINIMAL]): "يتم التشغيل" بلا أزرار ولا غلاف.
 *    يُستعمل عندما تكون [AppSettings.notificationsEnabled] = false — فلا يُلغى إشعار
 *    الخدمة الأمامية أبدًا (يُظهر النظام إشعار FGS حتى لو حُذف المستخدم).
 *
 * القيود الوثائقية (شاشة القفل): على أندرويد 5–12 يُعرض الإشعار المطوي فقط (أو
 * تخطيط MediaStyle المكوّن) فوق شاشة القفل، والأزرار القابلة للتفاعل تظهر وفق
 * سياسة OEM؛ ومن أندرويد 13 تدير شاشة القفل/مركز الوسائط MediaSession مباشرة
 * (عبر NotificationSeat) فتحسب العناوين/الغلاف من بيانات MediaMetadata —
 * وهي تُغذّى في PlaybackService.
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
    @Volatile var accentArgb: Int = AtherAccent.ambientAccentArgb(appSettings.currentThemeMode())

    /** تحديث ثيم الإشعار (عند تبديل الفصل/الوضع/اللون) يُعيد بناءه فورًا دون إيقاف الخدمة. */
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

    // ── تخطيط "أثير" الكوني وفق الوضع (فاتح/داكن/AMOLED) ──

    private data class NotifPalette(
        val bg: Int,
        val surface: Int,
        val ink: Int,
        val soft: Int,
        val accent: Int
    )

    private fun palette(): NotifPalette {
        val mode = appSettings.currentThemeMode()
        val (bg, surface) = when (mode) {
            AppThemeMode.LIGHT -> AtherCosmic.DawnTop to AtherCosmic.DawnBottom
            AppThemeMode.AMOLED -> (0xFF05060F).toInt() to (0xFF07070C).toInt()
            AppThemeMode.DARK -> AtherCosmic.InkBottom to (0xFF131A38).toInt()
        }
        val ink = AtherCosmic.MoonIce
        val soft = if (mode == AppThemeMode.LIGHT) (0xFF57537A).toInt() else (0xFFA9B2CC).toInt()
        return NotifPalette(
            bg = bg,
            surface = surface,
            ink = if (mode == AppThemeMode.LIGHT) (0xFF1D1B3B).toInt() else ink,
            soft = soft,
            accent = accentArgb
        )
    }

    private fun buildFull(
        session: MediaSession,
        commandButtons: ImmutableList<CommandButton>,
        actionFactory: MediaNotification.ActionFactory
    ): android.app.Notification {
        val isPlaying = playing
        val p = palette()
        val player = session.player
        val builder = NotificationCompat.Builder(context, NotificationChannels.PLAYBACK_FULL)
            .setSmallIcon(R.drawable.ic_stat_ather)
            .setContentTitle(contentTitle.ifBlank { context.getString(R.string.channel_playback_full_name) })
            .setContentText(contentChapter.ifBlank { contentAuthor })
            .setSubText(contentAuthor)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setOngoing(session.player.playWhenReady)
            .setOnlyAlertOnce(true)
            .setColor(p.accent)
            .setColorized(true)
            .setContentIntent(session.getSessionActivity())
            .setDeleteIntent(actionFactory.createNotificationDismissalIntent(session))
        artwork?.let { builder.setLargeIcon(it) }
        if (player.duration > 0L) {
            val pct = ((player.currentPosition.toFloat() / player.duration) * 100f).toInt().coerceIn(0, 100)
            builder.setProgress(100, pct, false)
        }

        val playPauseIcon = if (isPlaying) CommandButton.ICON_PAUSE else CommandButton.ICON_PLAY
        val playPauseLabel = if (isPlaying) context.getString(R.string.notif_action_pause) else context.getString(R.string.notif_action_play)
        val playPauseAction = actionFactory.createMediaAction(
            session,
            IconCompat.createWithResource(context, CommandButton.getIconResIdForIconConstant(playPauseIcon)),
            playPauseLabel,
            Player.COMMAND_PLAY_PAUSE
        )
        val staticButtons = PlaybackSessionCommands.notificationButtons(
            forwardSeconds = appSettings.skipForwardSeconds.value,
            backwardSeconds = appSettings.skipBackwardSeconds.value
        )
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

        builder.setCustomContentView(stripViews(session, fullActions, p, isPlaying))
        builder.setCustomBigContentView(panelViews(session, fullActions, p, isPlaying))
        return builder.build()
    }

    private fun stripViews(
        session: MediaSession,
        actions: List<NotificationCompat.Action>,
        p: NotifPalette,
        isPlaying: Boolean
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.notification_ather_strip)
        views.setInt(R.id.ather_strip_root, "setBackgroundColor", p.bg)
        views.setInt(R.id.ather_strip_bar, "setBackgroundColor", p.accent)
        views.setInt(R.id.ather_strip_cover, "setBackgroundColor", p.surface)
        views.setTextViewText(R.id.ather_strip_title, contentTitle.ifBlank { context.getString(R.string.channel_playback_full_name) })
        views.setTextViewText(R.id.ather_strip_chapter, contentChapter.ifBlank { contentAuthor })
        views.setTextColor(R.id.ather_strip_title, p.ink)
        views.setTextColor(R.id.ather_strip_chapter, p.soft)
        bindCover(views, R.id.ather_strip_cover)
        bindProgress(views, R.id.ather_strip_progress, p)
        bindPlayButton(views, R.id.ather_strip_play, actions[1], isPlaying, p)
        views.setOnClickPendingIntent(R.id.ather_strip_root, session.getSessionActivity())
        return views
    }

    private fun panelViews(
        session: MediaSession,
        actions: List<NotificationCompat.Action>,
        p: NotifPalette,
        isPlaying: Boolean
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.notification_ather_panel)
        views.setInt(R.id.ather_panel_root, "setBackgroundColor", p.bg)
        views.setInt(R.id.ather_panel_bar, "setBackgroundColor", p.accent)
        views.setInt(R.id.ather_panel_cover, "setBackgroundColor", p.surface)
        views.setTextViewText(R.id.ather_panel_app, context.getString(R.string.app_name))
        views.setTextViewText(R.id.ather_panel_title, contentTitle.ifBlank { context.getString(R.string.channel_playback_full_name) })
        views.setTextViewText(R.id.ather_panel_chapter, contentChapter.ifBlank { contentAuthor })
        views.setTextViewText(R.id.ather_panel_author, contentAuthor)
        views.setTextColor(R.id.ather_panel_app, p.accent)
        views.setTextColor(R.id.ather_panel_title, p.ink)
        views.setTextColor(R.id.ather_panel_chapter, p.soft)
        views.setTextColor(R.id.ather_panel_author, p.soft)
        bindCover(views, R.id.ather_panel_cover)
        bindProgress(views, R.id.ather_panel_progress, p)
        bindPlayButton(views, R.id.ather_panel_play, actions[1], isPlaying, p)
        bindIconButton(views, R.id.ather_panel_prev, actions[0], p)
        bindIconButton(views, R.id.ather_panel_next, actions[2], p)
        bindIconButton(views, R.id.ather_panel_back15, actions[3], p)
        bindIconButton(views, R.id.ather_panel_fwd15, actions[4], p)
        views.setOnClickPendingIntent(R.id.ather_panel_root, session.getSessionActivity())
        return views
    }

    private fun bindCover(views: RemoteViews, coverId: Int) {
        val art = artwork
        if (art != null) views.setImageViewBitmap(coverId, art)
    }

    private fun bindProgress(views: RemoteViews, progressId: Int, p: NotifPalette) {
        val player = session?.player
        val total = player?.duration ?: 0L
        val current = player?.currentPosition ?: 0L
        val pct = if (total > 0L) ((current.toFloat() / total) * 100f).toInt().coerceIn(0, 100) else 0
        views.setProgressBar(progressId, 100, pct, false)
    }

    private fun bindPlayButton(views: RemoteViews, buttonId: Int, action: NotificationCompat.Action, isPlaying: Boolean, p: NotifPalette) {
        bindAction(views, buttonId, action, p)
        val iconConst = if (isPlaying) CommandButton.ICON_PAUSE else CommandButton.ICON_PLAY
        views.setImageViewResource(buttonId, CommandButton.getIconResIdForIconConstant(iconConst))
    }

    private fun bindIconButton(views: RemoteViews, buttonId: Int, action: NotificationCompat.Action, p: NotifPalette) {
        bindAction(views, buttonId, action, p)
    }

    private fun bindAction(views: RemoteViews, buttonId: Int, action: NotificationCompat.Action, p: NotifPalette) {
        views.setInt(buttonId, "setColorFilter", p.accent)
        views.setContentDescription(buttonId, action.title)
        action.actionIntent?.let { views.setOnClickPendingIntent(buttonId, it) }
    }

    private fun buildMinimal(session: MediaSession, actionFactory: MediaNotification.ActionFactory): android.app.Notification =
        NotificationCompat.Builder(context, NotificationChannels.PLAYBACK_MINIMAL)
            .setSmallIcon(R.drawable.ic_stat_ather)
            .setContentTitle(context.getString(R.string.notif_playback_minimal_title))
            .setContentText(context.getString(R.string.notif_playback_minimal_text))
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setCategory(NotificationCompat.CATEGORY_TRANSPORT)
            .setOngoing(true)
            .setColor(AtherAccent.ambientAccentArgb(appSettings.currentThemeMode()))
            .setContentIntent(session.getSessionActivity())
            .setDeleteIntent(actionFactory.createNotificationDismissalIntent(session))
            .build()

    private fun actionFor(button: CommandButton, actionFactory: MediaNotification.ActionFactory) =
        actionFactory.createCustomActionFromCustomCommandButton(requireNotNull(session), button)
}