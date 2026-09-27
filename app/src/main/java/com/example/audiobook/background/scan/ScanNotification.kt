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
        cancellable: Boolean
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
        indeterminate: Boolean
    ): Notification = build(
        context = context,
        title = context.getString(R.string.notif_scan_title_scanning),
        detail = if (total > 0) {
            context.getString(
                R.string.notif_scan_progress,
                folderLabel.ifBlank { context.getString(R.string.notif_scan_no_folder) },
                current,
                total
            )
        } else {
            folderLabel.ifBlank { context.getString(R.string.notif_scan_progress_indeterminate) }
        },
        progress = if (total > 0) ((current.toLong() * 100) / total).toInt() else 0,
        indeterminate = indeterminate || total <= 0,
        cancellable = true
    )

    /**
     * تحديث الإشعار القائم. `notify` بنفس الـid يستبدل الإشعار، فالنوع
     * الأمامي يبقى معلنًا (لا pulsing ولا إعادة تصريح).
     */
    fun update(
        context: Context,
        folderLabel: String,
        current: Int,
        total: Int,
        indeterminate: Boolean
    ) {
        try {
            NotificationManagerCompat.from(context).notify(
                NotificationChannels.ID_SCAN,
                scanning(context, folderLabel, current, total, indeterminate)
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

    private const val TAG = "ScanNotification"
    private const val REQUEST_TASK = 9101
    private const val REQUEST_CANCEL = 9102
}
