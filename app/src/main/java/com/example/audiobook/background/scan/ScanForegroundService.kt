package com.example.audiobook.background.scan

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.net.Uri
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.domain.usecases.PreviewResultBus
import com.example.audiobook.domain.usecases.RebuildLibraryStructure
import com.example.audiobook.domain.usecases.RebuildStructureResult
import com.example.audiobook.domain.usecases.ScanAlreadyRunningException
import com.example.audiobook.domain.usecases.ScanLibraryNow
import com.example.audiobook.domain.usecases.ScanNowResult
import com.example.audiobook.domain.usecases.ScanPhase
import com.example.audiobook.domain.usecases.ScanProgress
import com.example.audiobook.domain.usecases.ScanProgressBus
import com.example.audiobook.domain.usecases.ScanReport
import com.example.audiobook.domain.usecases.ScanRoot
import com.example.audiobook.domain.usecases.OnboardingImportMaterializer
import com.example.audiobook.domain.usecases.PendingOnboardingImport
import com.example.audiobook.notifications.NotificationChannels
import com.example.audiobook.presentation.pendingdiscoveries.DiscoveryNotifier
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/**
 * PART 1 — المالك الأمامي لكل عمليات الفحص.
 *
 * قبل هذا كانت «فحص المكتبة الآن» و`rebuildStructure()` و`confirmAndImport()`
 * تشغّل الفحص مباشرة داخل `viewModelScope` (نطاق مقيّد بظهور الشاشة). على مكتبة
 * كبيرة كانت العملية تستهلك عشرات الميغابايت، فيقتلها lmkd/OEM killer حين
 * يغادر المستخدم الشاشة أو يهبط الميموري. لا يوجد نطاق Foreground ⇒ لا حماية.
 *
 * `startForeground` فورًا في [onStartCommand] (خلال 5 ثوانٍ أو يرمي النظام
 * ForegroundServiceDidNotStartInTimeException) ثم يعمل الفحص في
 * [serviceScope] غير المقيّد. الإشعار تقدّم + «N / M ملف» + زر إلغاء
 * (PART 1D).
 *
 * الإلغاء تعاوني: يمرّ عبر [ScanProgressBus.requestCancel] فينتهي الفحص عند
 * أقرب نقطة آمنة ويبقى `checkpoint` للاستئناف — لا `stopSelf` قسري.
 */
@AndroidEntryPoint
class ScanForegroundService : Service() {

    @Inject
    lateinit var scanLibraryNow: ScanLibraryNow

    @Inject
    lateinit var scanRoot: ScanRoot

    @Inject
    lateinit var rebuildLibraryStructure: RebuildLibraryStructure

    /** GAP 1: الخدمة تحتاج القاعدة لقراءة أولوية الجذر بعد فحص جذر واحد. */
    @Inject
    lateinit var database: AppDatabase

    /** GAP 2: كاتب شجرة الاستيراد المعلّقة، ومستهلكها في نطاق الخدمة. */
    @Inject
    lateinit var pendingOnboardingImport: PendingOnboardingImport

    @Inject
    lateinit var onboardingImportMaterializer: OnboardingImportMaterializer

    /**
     * نطاق الخدمة: [SupervisorJob] وحده، غير ابن لـ scope الـViewModel.
     * [cancel] في [onDestroy] فقط.
     */
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null

    /** STAGE 3A — wall-clock بدء المهمة الجارية، لchronometer الإشعار. */
    @Volatile
    private var scanStartedAtMs: Long = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        ensureChannel()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_CANCEL -> {
                // زر الإشعار: طلب إيقاف تعاوني (لا قتل للخدمة).
                ScanProgressBus.requestCancel()
                Log.i(TAG, "cancel-requested via notification action")
                return START_NOT_STICKY
            }
        }

        // STAGE 4 — زر «استئناف الفحص» من إشعار الفحص المتوقف: فحص كامل
        // عادي، و`resumeIndexFor` داخل ScanRoot يكمل تلقائيًا من مجلد
        // الـcheckpoint الباقي بدل البدء من الصفر.
        val isResume = intent?.action == ACTION_RESUME
        val jobName = if (isResume) {
            ScanJob.FULL_SCAN
        } else {
            intent?.getStringExtra(EXTRA_JOB)
                ?.let { name -> runCatching { ScanJob.valueOf(name) }.getOrNull() }
        }
        if (jobName == null) {
            val reason = "unusable job extra: ${intent?.getStringExtra(EXTRA_JOB)}"
            Log.w(TAG, reason)
            serviceScope.launch { failAndStop(reason) }
            return START_NOT_STICKY
        }
        val request = ScanRequest(job = jobName, rootId = intent?.getStringExtra(EXTRA_ROOT_ID))
        if (isResume) Log.i(TAG, "resume-requested via notification action")
        if (request.job == ScanJob.SINGLE_ROOT && request.rootId.isNullOrBlank()) {
            val reason = "SINGLE_ROOT without rootId"
            Log.w(TAG, reason)
            serviceScope.launch { failAndStop(reason) }
            return START_NOT_STICKY
        }

        // الحماية أولاً: startForeground قبل أي عمل ثقيل.
        if (job?.isActive == true) {
            Log.w(TAG, "start-while-running job=${request.job}; ignoring duplicate start")
            // FIX 1: المعاينة تنتظر نتيجتها على PreviewResultBus — رفض صامت
            // كان سيعلّق شاشتها إلى الأبد. الفحص له قناته (Rejected) فلا يلزم.
            if (request.job == ScanJob.PREVIEW) PreviewResultBus.publishFailed("scan-busy")
            return START_NOT_STICKY
        }
        // STAGE 3A — ساعة المهمة تُضبط هنا (لا داخل الكوروتين) فيحمل حتى
        // الإشعار الأولي نفس الأساس الزمني.
        scanStartedAtMs = System.currentTimeMillis()
        if (!promoteToForeground(request)) return START_NOT_STICKY

        job = serviceScope.launch {
            // مراقب التقدّم في نطاق فرعي: ينتهي فور انتهاء الفحص بدل أن
            // يبقي collectLatest معلّقًا على coroutineScopeService إلى الأبد.
            val progressJob = launch { observeProgressForNotification() }
            try {
                runRequest(request)
            } finally {
                progressJob.cancel()
            }
        }
        return START_NOT_STICKY
    }

    /**
     * @return false إذا رفض النظام إعلِن الحالة الأمامية (سياسة صارمة) — عندها
     * لا نعمل بلا حماية لأن هذا هو سبب وجود الخدمة.
     */
    private fun promoteToForeground(request: ScanRequest): Boolean {
        val notification = ScanNotification.scanning(
            context = this,
            folderLabel = "",
            current = 0,
            total = 0,
            indeterminate = true,
            startedAtMs = scanStartedAtMs.takeIf { it > 0 }
        )
        return try {
            ServiceCompat.startForeground(
                this,
                NotificationChannels.ID_SCAN,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                }
            )
            true
        } catch (error: Exception) {
            // ForegroundServiceStartNotAllowedException على API 31+ حين يكون
            // التطبيق في الخلفية، أو ForegroundServiceDidNotStartInTimeException.
            Log.e(TAG, "startForeground rejected for ${request.job}", error)
            false
        }
    }

    /**
     * يعكس تقدّم [ScanProgressBus] على الإشعار: المجلد + الملف الجاري + N/M.
     * STAGE 3B — بعد 100 ملف يُحسب ETA من المعدل ويُعرض «متبقٍ ~M:SS».
     */
    private suspend fun observeProgressForNotification() {
        ScanProgressBus.state.collectLatest { progress ->
            if (progress == null || !ScanProgressBus.active.value) return@collectLatest
            // PART 1D: الإشعار يحمل اسم المجلد الحالي + «N / M ملف» + زر الإلغاء.
            val label = progress.currentFolder
                .substringAfterLast('/')
                .ifBlank { progress.currentFolder }
            ScanNotification.update(
                context = this,
                folderLabel = label,
                current = progress.processed,
                total = progress.total,
                indeterminate = progress.total <= 0,
                currentFile = progress.currentFile,
                startedAtMs = scanStartedAtMs.takeIf { it > 0 },
                etaText = computeEta(progress.processed, progress.total)
            )
        }
    }

    /**
     * STAGE 3B — تقدير الوقت المتبقي من معدل المعالجة منذ بدء المهمة.
     * @return «متبقٍ ~M:SS» أو null إن تعذّر التقدير (أقل من 100 ملف أو بلا معدل).
     */
    private fun computeEta(processed: Int, total: Int): String? {
        if (processed <= 100 || total <= processed) return null
        val elapsedMs = System.currentTimeMillis() - scanStartedAtMs
        if (elapsedMs <= 0 || scanStartedAtMs <= 0) return null
        val ratePerMs = processed.toDouble() / elapsedMs
        if (ratePerMs <= 0) return null
        val remainingMs = ((total - processed) / ratePerMs).toLong()
        return getString(com.example.audiobook.R.string.notif_scan_eta, formatDuration(remainingMs))
    }

    private fun formatDuration(ms: Long): String {
        val totalSeconds = (ms / 1000).coerceAtLeast(0)
        return "%d:%02d".format(totalSeconds / 60, totalSeconds % 60)
    }

    private suspend fun runRequest(request: ScanRequest) {
        // FIX 1 — المعاينة قراءة فقط: لا حصيلة فحص ولا إشعار ختامي ولا
        // ScanServiceNotifier (قناتها PreviewResultBus حصرًا)، فتخرج مبكرًا
        // قبل try/when الفحص حتى لا تمسّ أي مسار استيراد/فحص.
        if (request.job == ScanJob.PREVIEW) {
            runPreview(request)
            return
        }
        val outcome = try {
            when (request.job) {
                ScanJob.FULL_SCAN -> ScanOutcome.Completed(scanLibraryNow())
                ScanJob.PREVIEW -> ScanOutcome.Failed("preview-handled-earlier")
                ScanJob.REBUILD -> {
                    // إعادة البناء: تنظيف القشور ثم فحص كامل، بنفس تغطية الحماية.
                    val result: RebuildStructureResult = rebuildLibraryStructure()
                    ScanOutcome.Completed(outcome = result.scan, rebuildResult = result)
                }
                ScanJob.SINGLE_ROOT -> {
                    // onStartCommand يتحقق من rootId مسبقًا، فهنا non-null.
                    val rootId = UUID.fromString(request.rootId!!)
                    val report: ScanReport = scanRoot(rootId)
                    // GAP 1: كان هذا الإشعار مسؤولية ScanWorker — أي يُشعل
                    // البوب-أب بعد الفحص. وبما أن الفحص صار داخل الخدمة، نُبقي
                    // الأثر معه وإلا اختفى إشعار «اكتشافات جديدة» مع تفريغ
                    // الخدمة. نقرأ الأولوية من الجذر لأن الطلب لا يحملها.
                    val root = database.libraryRootDao().getById(rootId)
                    if (root != null && root.isPriority &&
                        database.pendingDiscoveryDao().countPendingByRoot(rootId) > 0
                    ) {
                        DiscoveryNotifier.notify(rootId)
                    }
                    ScanOutcome.Completed(
                        ScanNowResult(
                            rootsScanned = 1,
                            filesSeen = report.filesSeen + report.filesDeduped,
                            booksFound = report.editionsCreated
                        )
                    )
                }
                ScanJob.ONBOARDING_IMPORT -> {
                    // GAP 2: أثقل جزء من الاستيراد صار هنا — داخل نطاق أمامي.
                    val pending = pendingOnboardingImport.consume()
                    if (pending == null) {
                        ScanOutcome.Failed("onboarding-payload-missing")
                    } else {
                        val root = database.libraryRootDao().getById(pending.rootId)
                        if (root == null) {
                            ScanOutcome.Failed("onboarding-root-missing")
                        } else {
                            ScanProgressBus.resetCancel()
                            publishImportProgress(root, pending.books.size)
                            val created = onboardingImportMaterializer.materialize(
                                root = root,
                                bookSpecs = pending.books,
                                renamedPaths = pending.renamedPaths,
                                skippedPaths = pending.skippedPaths
                            ) { done, total ->
                                publishImportProgress(root, total, done)
                            }
                            // ثم الفحص الفعلي لملء الملفات/المدد وربطها بما وُهّد.
                            val report = scanRoot(pending.rootId)
                            ScanOutcome.Completed(
                                ScanNowResult(
                                    rootsScanned = 1,
                                    filesSeen = report.filesSeen + report.filesDeduped,
                                    booksFound = created
                                )
                            )
                        }
                    }
                }
            }
        } catch (rejected: ScanAlreadyRunningException) {
            Log.w(TAG, "rejected: another scan already running")
            ScanOutcome.Rejected
        } catch (error: Throwable) {
            Log.e(TAG, "scan failed job=${request.job}", error)
            ScanOutcome.Failed(error.message ?: error::class.java.simpleName)
        }

        ScanServiceNotifier.notify(outcome)
        finishScan(outcome)
    }

    /**
     * FIX 1 — تنفيذ المعاينة: جوس وتصنيف قراءةً فقط عبر
     * [com.example.audiobook.domain.usecases.ScanRoot.preview]، التقدّم يُعاد
     * نشره على الناقل (للشاشة) والإشعار (للخلفية) مباشرةً — لا عبر مراقب
     * `active` لأنه مخصّص للفحص. النتيجة (شجرة/فشل/إلغاء) على
     * PreviewResultBus. بلا ScanServiceNotifier وبلا إشعار «انتهى الفحص».
     */
    private suspend fun runPreview(request: ScanRequest) {
        val uriString = request.rootUri
        if (uriString.isNullOrBlank()) {
            PreviewResultBus.publishFailed("preview-missing-uri")
            finishPreview()
            return
        }
        try {
            val tree = scanRoot.preview(
                rootUri = Uri.parse(uriString),
                isCancelled = ScanProgressBus::isCancelRequested
            ) { progress ->
                ScanProgressBus.publish(progress)
                val label = progress.currentFolder
                    .substringAfterLast('/')
                    .ifBlank { progress.currentFolder }
                ScanNotification.update(
                    context = this,
                    folderLabel = label,
                    current = progress.processed,
                    total = progress.total,
                    indeterminate = progress.total <= 0,
                    currentFile = progress.currentFile,
                    startedAtMs = scanStartedAtMs.takeIf { it > 0 },
                    etaText = computeEta(progress.processed, progress.total)
                )
            }
            PreviewResultBus.publishReady(tree)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            Log.i(TAG, "preview-cancelled")
            PreviewResultBus.publishCancelled()
            throw cancelled
        } catch (error: Throwable) {
            Log.e(TAG, "preview failed", error)
            PreviewResultBus.publishFailed(error.message ?: error::class.java.simpleName)
        } finally {
            finishPreview()
        }
    }

    /** ختام المعاينة: إسقاط الإشعار وإيقاف الخدمة — بلا رسالة ختامية. */
    private fun finishPreview() {
        ScanNotification.cancel(this)
        stopSelf()
    }

    /**
     * GAP 2: مرحلة التوجيه أثناء الاستيراد.
     *
     * لا نستخدم `ScanProgressBus.begin()` هنا عن قصد: `begin()` تلتقط حارس
     * الفحص الوحيد، ثم `scanRoot` التي تُستدعى بعد `materialize` تطلبه هي
     * الأخرى فتُرفض. فالتوجيه يمرّ بـ`publish` (لتصل شاشة الاستهلال) +
     * تحديث مباشر للإشعار (لأن مُراقب الخدمة يشترط `active = true`).
     */
    private fun publishImportProgress(
        root: com.example.audiobook.data.room.entity.LibraryRootEntity,
        total: Int,
        done: Int = 0
    ) {
        val label = root.displayName
        ScanProgressBus.publish(ScanProgress(ScanPhase.IMPORTING, done, total, label))
        ScanNotification.update(
            context = this,
            folderLabel = label,
            current = done,
            total = total,
            indeterminate = total <= 0
        )
    }

    private suspend fun failAndStop(reason: String): Int {
        ScanServiceNotifier.notify(ScanOutcome.Failed(reason))
        finishScan(ScanOutcome.Failed(reason))
        return START_NOT_STICKY
    }

    private fun finishScan(outcome: ScanOutcome) {
        val text = when (outcome) {
            is ScanOutcome.Completed -> getString(
                com.example.audiobook.R.string.notif_scan_finished,
                outcome.outcome.booksFound
            )
            ScanOutcome.Cancelled -> getString(com.example.audiobook.R.string.notif_scan_cancelled)
            is ScanOutcome.Failed -> getString(com.example.audiobook.R.string.notif_scan_failed)
            ScanOutcome.Rejected -> getString(com.example.audiobook.R.string.notif_scan_already_running)
        }
        runCatching {
            androidx.core.app.NotificationManagerCompat.from(this).notify(
                NotificationChannels.ID_SCAN,
                ScanNotification.build(
                    context = this,
                    title = getString(com.example.audiobook.R.string.notif_scan_title),
                    detail = text,
                    progress = 100,
                    indeterminate = false,
                    cancellable = false
                ).also { it.flags = it.flags or android.app.Notification.FLAG_AUTO_CANCEL }
            )
        }
        // الإشعار الختامي قصير العمر: نمنحه ثوانٍ ثم نُغلق الخدمة.
        serviceScope.launch {
            kotlinx.coroutines.delay(FINISH_NOTIFICATION_MS)
            ScanNotification.cancel(this@ScanForegroundService)
            stopSelf()
        }
    }

    override fun onDestroy() {
        job?.cancel()
        serviceScope.cancel()
        ScanNotification.cancel(this)
        super.onDestroy()
    }

    private fun ensureChannel() {
        NotificationChannels.createScanChannel(this)
    }

    companion object {
        private const val TAG = "ScanFgService"

        /** intent قارئه `ScanNotification` لبناء زر الإلغاء. */
        const val ACTION_CANCEL = "com.example.audiobook.action.CANCEL_SCAN"

        /** STAGE 4 — زر «استئناف الفحص» من إشعار الفحص المتوقف. */
        const val ACTION_RESUME = "com.example.audiobook.action.RESUME_SCAN"
        private const val EXTRA_JOB = "scan_job"
        private const val EXTRA_ROOT_ID = "scan_root_id"
        private const val FINISH_NOTIFICATION_MS = 2_500L

        fun start(context: Context, request: ScanRequest): Boolean {
            val intent = Intent(context, ScanForegroundService::class.java).apply {
                putExtra(EXTRA_JOB, request.job.name)
                request.rootId?.let { putExtra(EXTRA_ROOT_ID, it) }
            }
            return try {
                // startForegroundService يبدأ العمل ثم — داخل الخدمة — startForeground.
                ContextCompat.startForegroundService(context, intent)
                true
            } catch (error: Exception) {
                // IllegalStateException/ForegroundServiceStartNotAllowedException على
                // API 31+ خارج المقدمة. الاستدعاء يرجع false فيسقط المستدعي إلى
                // المسار المباشر.
                Log.w(TAG, "startForegroundService rejected: ${error.message}")
                false
            }
        }
    }
}
