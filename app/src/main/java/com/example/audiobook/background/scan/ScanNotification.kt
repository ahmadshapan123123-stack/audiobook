package com.example.audiobook.background.scan

import android.app.Notification
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.example.audiobook.MainActivity
import com.example.audiobook.R
import com.example.audiobook.notifications.NotificationChannels

/**
 * إشعار الفحص الأمامي: العنوان والمجلد الحالي وشريط «N / M ملف» وزر إلغاء.
 *
 * منفصل عن [com.example.audiobook.background.scanworker.ScanWorker] لأن
 * WorkManager لا يصلح كحماية: `ScanForegroundService` هو المالك الفعلي للعمل،
 * و`ScanWorker` صار يوفّر الحماية فقط عبر `startForeground` في `onStartCommand`.
 */
internal object ScanNotification {

    fun build(
        context: Context,
        title: String,
        detail: String,
        progress: Int,
        indeterminate: Boolean,
        cancellable: Boolean,
        /**
         * STAGE 3A — لحظة بدء الفحص (uptime ms): تُعرض «منذ 2:34» تلقائيًا
         * عبر chronometer النظام، وتتحدث كل ثانية بلا أي عمل منا.
         */
        startedAtMs: Long? = null,
        /** STAGE 2 — نص موسّع (مجلد + ملف + ETA) يظهر عند فرد الإشعار. */
        bigText: String? = null
    ): Notification {
        val builder = NotificationCompat.Builder(context, NotificationChannels.SCAN)
            .setSmallIcon(R.drawable.ic_scan)
            .setContentTitle(title)
            .setContentText(detail)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setProgress(100, progress.coerceIn(0, 100), indeterminate)
            .setContentIntent(taskIntent(context))
        if (startedAtMs != null) {
            builder.setWhen(startedAtMs).setUsesChronometer(true)
        }
        if (bigText != null) {
            builder.setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
        }
        // FIX 3 (B7): largeIcon للفحص — عدسة بيضاء على خلفية كونية، تُحسب
        // مرة واحدة وتُعاد. smallIcon تبقى أحادية كما تفرض المنصة.
        builder.setLargeIcon(scanLargeIcon(context))

        if (cancellable) {
            builder.addAction(
                R.drawable.ic_scan,
                context.getString(R.string.action_cancel_scan),
                cancelIntent(context)
            )
        }
        return builder.build()
    }

    /** إشعار «جارٍ فحص المكتبة» فوق شريط التقدّم — PART 1D. */
    fun scanning(
        context: Context,
        folderLabel: String,
        current: Int,
        total: Int,
        indeterminate: Boolean,
        currentFile: String = "",
        startedAtMs: Long? = null,
        etaText: String? = null
    ): Notification {
        val folder = folderLabel.ifBlank { context.getString(R.string.notif_scan_no_folder) }
        val detail = if (total > 0) {
            context.getString(R.string.notif_scan_progress, folder, current, total)
        } else {
            folderLabel.ifBlank { context.getString(R.string.notif_scan_progress_indeterminate) }
        }
        // STAGE 2+3: السطر الموسّع — المجلد، ثم الملف الجاري، ثم الوقت المتبقي.
        val bigLines = buildList {
            add(folder)
            if (currentFile.isNotBlank()) add(currentFile)
            if (total > 0) add(context.getString(R.string.notif_scan_progress, folder, current, total))
            if (etaText != null) add(etaText)
        }
        return build(
            context = context,
            title = context.getString(R.string.notif_scan_title_scanning),
            detail = detail,
            progress = if (total > 0) ((current.toLong() * 100) / total).toInt() else 0,
            indeterminate = indeterminate || total <= 0,
            cancellable = true,
            startedAtMs = startedAtMs,
            bigText = bigLines.joinToString("\n")
        )
    }

    /**
     * تحديث الإشعار القائم. `notify` بنفس الـid يستبدل الإشعار، فالنوع
     * الأمامي يبقى معلنًا (لا pulsing ولا إعادة تصريح).
     */
    fun update(
        context: Context,
        folderLabel: String,
        current: Int,
        total: Int,
        indeterminate: Boolean,
        currentFile: String = "",
        startedAtMs: Long? = null,
        etaText: String? = null
    ) {
        try {
            NotificationManagerCompat.from(context).notify(
                NotificationChannels.ID_SCAN,
                scanning(context, folderLabel, current, total, indeterminate, currentFile, startedAtMs, etaText)
            )
        } catch (error: SecurityException) {
            // إذن الإشعارات غير ممنوح: الإشعار غير مرئي أصلًا، والفحص يعمل.
            Log.w(TAG, "Unable to update scan notification", error)
        }
    }

    fun cancel(context: Context) {
        try {
            NotificationManagerCompat.from(context).cancel(NotificationChannels.ID_SCAN)
        } catch (error: SecurityException) {
            // إذن الإشعارات غير ممنوح: لا إشعار لإلغائه. غير قاتل.
            Log.w(TAG, "Unable to cancel scan notification", error)
        }
    }

    /**
     * STAGE 3C/4 — إشعار «فحص متوقف» بزر استئناف.
     *
     * إشعار عادي (ليس foreground — لا خدمة تعمل)، بنفس الـid فيحل محل أي
     * أثر سابق. الضغط على «استئناف الفحص» يوقظ الخدمة التي تطلق FULL_SCAN،
     * و`resumeIndexFor` يكمل تلقائيًا من مجلد الـcheckpoint.
     */
    fun postResumeAvailable(context: Context, rootLabel: String) {
        try {
            val notification = NotificationCompat.Builder(context, NotificationChannels.SCAN)
                .setSmallIcon(R.drawable.ic_scan)
                .setContentTitle(context.getString(R.string.notif_resume_scan_title))
                .setContentText(context.getString(R.string.notif_resume_scan_text, rootLabel))
                .setOngoing(false)
                .setAutoCancel(true)
                .setOnlyAlertOnce(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentIntent(taskIntent(context))
                .addAction(
                    R.drawable.ic_scan,
                    context.getString(R.string.action_resume_scan),
                    resumeIntent(context)
                )
                .build()
            NotificationManagerCompat.from(context).notify(NotificationChannels.ID_SCAN, notification)
        } catch (error: SecurityException) {
            Log.w(TAG, "Unable to post resume notification", error)
        }
    }

    private fun resumeIntent(context: Context): PendingIntent {
        val intent = Intent(context, ScanForegroundService::class.java).apply {
            action = ScanForegroundService.ACTION_RESUME
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getService(context, REQUEST_RESUME, intent, flags)
    }

    private fun taskIntent(context: Context): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getActivity(context, REQUEST_TASK, intent, flags)
    }

    /**
     * زر الإلغاء: [ACTION_CANCEL] عائد إلى الخدمة نفسها، فتسجّل خدمة الإلغاء
     * على [com.example.audiobook.domain.usecases.ScanProgressBus] وتوقف العمل
     * تعاونيًا. الإلغاء لا يقتل العملية — الفحص ينتهي عند أقرب نقطة آمنة
     * ويبقى `checkpoint` للاستئناف.
     */
    private fun cancelIntent(context: Context): PendingIntent {
        val intent = Intent(context, ScanForegroundService::class.java).apply {
            action = ScanForegroundService.ACTION_CANCEL
        }
        val flags = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        } else {
            PendingIntent.FLAG_UPDATE_CURRENT
        }
        return PendingIntent.getService(context, REQUEST_CANCEL, intent, flags)
    }

    const val FOREGROUND_TYPE = ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC

    @Volatile
    private var cachedScanIcon: android.graphics.Bitmap? = null

    private fun scanLargeIcon(context: Context): android.graphics.Bitmap {
        cachedScanIcon?.let { return it }
        return com.example.audiobook.notifications.NotificationLargeIcons.vectorArtwork(
            context,
            com.example.audiobook.R.drawable.ic_scan,
            0xFF131A38.toInt()
        ).also { cachedScanIcon = it }
    }

    private const val TAG = "ScanNotification"
    private const val REQUEST_TASK = 9101
    private const val REQUEST_CANCEL = 9102
    private const val REQUEST_RESUME = 9103
}
