package com.example.audiobook.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import android.content.Context
import android.util.Log
import androidx.lifecycle.viewModelScope
import com.example.audiobook.background.reclassify.ReclassifyResultNotifier
import com.example.audiobook.background.reclassify.ReclassifyScheduler
import com.example.audiobook.background.scan.ScanServiceLauncher
import com.example.audiobook.background.scan.ScanJob
import com.example.audiobook.background.scan.ScanOutcome
import com.example.audiobook.background.scan.ScanRequest
import com.example.audiobook.background.scan.ScanServiceNotifier
import com.example.audiobook.data.preferences.AppSettings
import com.example.audiobook.domain.usecases.ClearDemoDataResult
import com.example.audiobook.domain.usecases.ClassificationPreviewPerRoot
import com.example.audiobook.domain.usecases.IntelligenceLevel
import com.example.audiobook.domain.usecases.LibraryClassificationPreview
import com.example.audiobook.domain.usecases.LibraryManagement
import com.example.audiobook.domain.usecases.ReclassifyLibrary
import com.example.audiobook.domain.usecases.ReclassifyPreview
import com.example.audiobook.domain.usecases.ScanLibraryNow
import com.example.audiobook.domain.usecases.ScanResume
import com.example.audiobook.domain.usecases.RebuildLibraryStructure
import com.example.audiobook.domain.usecases.RebuildStructureResult
import com.example.audiobook.domain.usecases.ScanNowResult
import com.example.audiobook.domain.model.AppThemeMode
import com.example.audiobook.domain.model.LogoColorMode
import com.example.audiobook.domain.model.ScanMode
import com.example.audiobook.background.reminders.ReminderScheduler
import com.example.audiobook.data.room.dao.BookDao
import com.example.audiobook.data.room.dao.LibraryRootDao
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appSettings: AppSettings,
    private val reminderScheduler: ReminderScheduler,
    private val libraryManagement: LibraryManagement,
    private val libraryRootDao: LibraryRootDao,
    private val bookDao: BookDao,
    private val scanServiceLauncher: ScanServiceLauncher,
    private val reclassifyLibrary: ReclassifyLibrary,
    private val libraryClassificationPreview: LibraryClassificationPreview,
    private val reclassifyScheduler: ReclassifyScheduler,
    @ApplicationContext private val appContext: Context
) : ViewModel() {

    val themeMode: StateFlow<AppThemeMode> = appSettings.themeMode
    val logoColor: StateFlow<LogoColorMode> = appSettings.logoColor
    val intelligenceLevel: StateFlow<IntelligenceLevel> = appSettings.intelligenceLevel
    val autoSeriesClassification: StateFlow<Boolean> = appSettings.autoSeriesClassification

    /** PART 12: «وضع الفحص» — عادي/اقتصادي/سريع. */
    val scanMode: StateFlow<ScanMode> = appSettings.scanMode

    fun setScanMode(mode: ScanMode) = appSettings.setScanMode(mode)

    val defaultSpeed: StateFlow<Float> = appSettings.defaultSpeed
    val autoResume: StateFlow<Boolean> = appSettings.autoResume
    val pauseOnAudioDisconnect: StateFlow<Boolean> = appSettings.pauseOnAudioDisconnect
    val autoNextChapter: StateFlow<Boolean> = appSettings.autoNextChapter
    val keepScreenOn: StateFlow<Boolean> = appSettings.keepScreenOn
    /** FIX 5: التشغيل التلقائي عند فتح المشغّل. */
    val autoPlayOnOpen: StateFlow<Boolean> = appSettings.autoPlayOnOpen
    /** PART 3: فاصلا التقديم/التأخير (ثوانٍ) — القيم: 10/15/20/30/45/60. */
    val skipForwardSeconds: StateFlow<Int> = appSettings.skipForwardSeconds
    val skipBackwardSeconds: StateFlow<Int> = appSettings.skipBackwardSeconds
    val defaultSleepMinutes: StateFlow<Int> = appSettings.defaultSleepMinutes
    val autoExtendSleep: StateFlow<Boolean> = appSettings.autoExtendSleep
    val notificationsEnabled: StateFlow<Boolean> = appSettings.notificationsEnabled
    val mediaNotificationMinimal: StateFlow<Boolean> = appSettings.mediaNotificationMinimal
    val sleepTimerNotificationsEnabled: StateFlow<Boolean> = appSettings.sleepTimerNotificationsEnabled
    val saveMomentNotificationsEnabled: StateFlow<Boolean> = appSettings.saveMomentNotificationsEnabled
    val bookCompletionNotificationsEnabled: StateFlow<Boolean> = appSettings.bookCompletionNotificationsEnabled
    val dailyReminderEnabled: StateFlow<Boolean> = appSettings.dailyReminderEnabled
    val dailyReminderHour: StateFlow<Int> = appSettings.dailyReminderHour
    val dailyReminderMinute: StateFlow<Int> = appSettings.dailyReminderMinute
    val resumeReminderEnabled: StateFlow<Boolean> = appSettings.resumeReminderEnabled
    val hasSeededDemoData: StateFlow<Boolean> = appSettings.hasSeededDemoData

    /**
     * «لا توجد مجلدات» (غير true = لا شيء يُعرض). يُحسم باستشارة القاعدة
     * مباشرةً (`countAll()` suspend) عند الحاجة — لا حالة مخزّنة تتقادم.
     */
    private val _noRootsPrompt = MutableStateFlow(false)
    val noRootsPrompt: StateFlow<Boolean> = _noRootsPrompt.asStateFlow()

    /** حصيلة آخر تنظيف لبيانات التجربة (تُستهلك مرة واحدة لعرض Snackbar). */
    private val _demoCleanupResult = MutableStateFlow<ClearDemoDataResult?>(null)
    val demoCleanupResult: StateFlow<ClearDemoDataResult?> = _demoCleanupResult.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    /** حصيلة آخر فحص فوري (تُستهلك مرة واحدة لعرض Snackbar). */
    private val _scanResult = MutableStateFlow<ScanNowResult?>(null)
    val scanResult: StateFlow<ScanNowResult?> = _scanResult.asStateFlow()

    // ── STAGE 4/5: حالات الاستئناف والدمج — قبل `init` قصدًا: `init`
    // يستدعي `refreshResumeInfo()`، وأي حالة تُعلَن بعده تكون null أثناء
    // تنفيذ الكوروتين الفوري (Main.immediate + مصدر mocked لا يعلّق).
    private val _resumeInfo = MutableStateFlow<ScanResume.ResumeInfo?>(null)
    val resumeInfo: StateFlow<ScanResume.ResumeInfo?> = _resumeInfo.asStateFlow()

    private val _mergeCandidates = MutableStateFlow<List<LibraryManagement.MergeCandidate>>(emptyList())
    val mergeCandidates: StateFlow<List<LibraryManagement.MergeCandidate>> = _mergeCandidates.asStateFlow()

    private val _mergePreview = MutableStateFlow<LibraryManagement.MergePreview?>(null)
    val mergePreview: StateFlow<LibraryManagement.MergePreview?> = _mergePreview.asStateFlow()

    private val _mergeResult = MutableStateFlow<Int?>(null)
    /** عدد الكتب بعد دمج ناجح (يُستهلك لعرض snackbar). */
    val mergeResult: StateFlow<Int?> = _mergeResult.asStateFlow()

    private val _mergeFailed = MutableStateFlow(false)
    val mergeFailed: StateFlow<Boolean> = _mergeFailed.asStateFlow()

    private val _isMerging = MutableStateFlow(false)
    val isMerging: StateFlow<Boolean> = _isMerging.asStateFlow()

    private val _isRebuilding = MutableStateFlow(false)
    val isRebuilding: StateFlow<Boolean> = _isRebuilding.asStateFlow()

    /** حصيلة آخر إعادة بناء بنية (تُستهلك مرة واحدة لعرض Snackbar). */
    private val _rebuildResult = MutableStateFlow<RebuildStructureResult?>(null)
    val rebuildResult: StateFlow<RebuildStructureResult?> = _rebuildResult.asStateFlow()

    private val _rebuildFailed = MutableStateFlow(false)
    val rebuildFailed: StateFlow<Boolean> = _rebuildFailed.asStateFlow()

    /**
     * عدد الكتب الحالي لحظة فتح حوار «إعادة البناء» — يحتاجه المستخدم ليعرف
     * حجم ما سيُعاد بناؤه قبل أن يؤكّد. يُقرأ من القاعدة عند فتح الحوار
     * (لا قيمة مخزّنة قد تتقادم)؛ `null` = لم يُقرأ بعد.
     */
    private val _rebuildBookCount = MutableStateFlow<Int?>(null)
    val rebuildBookCount: StateFlow<Int?> = _rebuildBookCount.asStateFlow()

    /** سبب فشل آخر إعادة بناء (يُعرض في Snackbar بدل نص عام). */
    private val _rebuildError = MutableStateFlow<String?>(null)
    val rebuildError: StateFlow<String?> = _rebuildError.asStateFlow()

    private val _isReclassifying = MutableStateFlow(false)
    val isReclassifying: StateFlow<Boolean> = _isReclassifying.asStateFlow()

    /** معاينة إعادة التصنيف (غير null = يوجد تغييرات تنتظر تأكيد المستخدم). */
    private val _reclassifyPreview = MutableStateFlow<ReclassifyPreview?>(null)
    val reclassifyPreview: StateFlow<ReclassifyPreview?> = _reclassifyPreview.asStateFlow()

    /** شجرة «البنية القادمة» (مؤلف ← سلسلة ← كتاب) المعروضة داخل حوار إعادة التصنيف قبل التأكيد. */
    private val _reclassifyTree = MutableStateFlow<List<ClassificationPreviewPerRoot>?>(null)
    val reclassifyTree: StateFlow<List<ClassificationPreviewPerRoot>?> = _reclassifyTree.asStateFlow()

    /** حصيلة آخر إعادة تصنيف (تُستهلك مرة واحدة لعرض Snackbar). */
    private val _reclassifyApplied = MutableStateFlow<ReclassifyPreview?>(null)
    val reclassifyApplied: StateFlow<ReclassifyPreview?> = _reclassifyApplied.asStateFlow()

    private val _scanFailed = MutableStateFlow(false)
    val scanFailed: StateFlow<Boolean> = _scanFailed.asStateFlow()

    /**
     * PART 11: رُفض الفحص لأن فحصًا آخر يعمل (حارس الفحص الوحيد). حالة منفصلة
     * عن [scanFailed] لأن الرسالة مختلفة — ليست خطأً ولا «فشل».
     */
    private val _scanAlreadyRunning = MutableStateFlow(false)
    val scanAlreadyRunning: StateFlow<Boolean> = _scanAlreadyRunning.asStateFlow()

    private val _isPreviewingClassification = MutableStateFlow(false)
    val isPreviewingClassification: StateFlow<Boolean> = _isPreviewingClassification.asStateFlow()

    /** معاينة تصنيف المكتبة (غير null = شجرة المؤلف ← السلسلة ← الكتاب تنتظر العرض). */
    private val _classificationPreview = MutableStateFlow<List<ClassificationPreviewPerRoot>?>(null)
    val classificationPreview: StateFlow<List<ClassificationPreviewPerRoot>?> = _classificationPreview.asStateFlow()

    init {
        // STAGE 4: عرض «استئناف الفحص الأخير» إن بقي checkpoint صالح.
        refreshResumeInfo()
        // يلتقط نتيجة Worker إعادة التصنيف الخلفي ويعرضها (Snackbar + إنهاء الحالة).
        viewModelScope.launch {
            ReclassifyResultNotifier.result.collect { result ->
                if (_isReclassifying.value && result != null) {
                    ReclassifyResultNotifier.reset()
                    _reclassifyApplied.value = result
                    _isReclassifying.value = false
                }
            }
        }
    }

    fun removeDemoData() {
        viewModelScope.launch {
            val result = libraryManagement.clearDemoData()
            appSettings.setHasSeededDemoData(false)
            _demoCleanupResult.value = result
        }
    }

    fun consumeDemoCleanupResult() {
        _demoCleanupResult.value = null
    }

    // ── STAGE 4: استئناف الفحص الأخير ──

    /** يحدّث عرض الاستئناف: checkpoint صالح (< 24h) وجذر مفعّل. */
    fun refreshResumeInfo() {
        viewModelScope.launch {
            _resumeInfo.value = libraryManagement.getScanResumeInfo()
        }
    }

    /** زر الاستئناف: فحص كامل عادي — `resumeIndexFor` يكمل من الـcheckpoint. */
    fun resumeLastScan() {
        _resumeInfo.value = null
        scanNow()
    }

    // ── STAGE 5: دمج الجذور ──

    fun refreshMergeCandidates() {
        viewModelScope.launch {
            _mergeCandidates.value = libraryManagement.detectMergeCandidates()
        }
    }

    fun previewMerge(rootIds: List<java.util.UUID>) {
        viewModelScope.launch {
            _mergePreview.value = libraryManagement.previewMerge(rootIds)
        }
    }

    fun consumeMergePreview() {
        _mergePreview.value = null
    }

    fun consumeMergeResult() {
        _mergeResult.value = null
    }

    fun consumeMergeFailed() {
        _mergeFailed.value = false
    }

    /**
     * تطبيق الدمج ثم فحص الأب الجديد. `parentUri` من منتقي المجلدات
     * (المجلد الحاوي لمجلدات المؤلفين).
     */
    fun applyMerge(parentUri: String, parentName: String, rootIds: List<java.util.UUID>) {
        if (_isMerging.value) return
        viewModelScope.launch {
            _isMerging.value = true
            try {
                val preview = libraryManagement.previewMerge(rootIds)
                libraryManagement.applyMerge(parentUri, parentName, preview.candidates.map { it.rootId })
                _mergePreview.value = null
                _mergeResult.value = preview.totalBooks
                refreshMergeCandidates()
                refreshResumeInfo()
                // فحص الأب الجديد لملء الملفات/المدد تحت الهوية البنيوية نفسها.
                scanNow()
            } catch (error: Exception) {
                android.util.Log.w(TAG, "merge-roots failed: ${error.message}")
                _mergeFailed.value = true
            } finally {
                _isMerging.value = false
            }
        }
    }

    /**
     * الفحص الفوري لكل المجلدات الممكّنة (وليس جدولة خلفية).
     * قرار «هل يوجد جذر؟» يُتخذ هنا على قيمة مقروءة من القاعدة الآن، لا من Composable.
     */
    fun scanNow() {
        if (_isScanning.value) return
        viewModelScope.launch {
            if (!hasRootsNow()) {
                _noRootsPrompt.value = true
                return@launch
            }
            _isScanning.value = true
            // PART 1: الفحص يمرّ عبر ScanForegroundService — نطاق foreground
            // لا مقيّد بظهور الشاشة، فلا يقتله lmkd على مكتبة كبيرة.
            // PART 1 (تنظيف): مراقب التقدّم المحلي حُذف — لا شاشة تقرؤه.
            // PART 1: لا مسار احتياطي مباشر. الفحص في هذه الشاشة إما عبر
            // الخدمة الأمامية أو لا فحص — تشغيل `scanLibraryNow()` من
            // ViewModel كان يسقط كل ضمانة بقاء الخدمة عند فشل `start()`،
            // وهو بالضبط ما يفشل على مكتبة كبيرة.
            // الناقل يُصفَّر **قبل** `launch` لا بعده: الخدمة قد تنشر حصيلتها
            // وتعود قبل أن يُرجع `launch` قيمة `true`، فتصفييرٌ بعده يمحو
            // حصيلة هذا الطلب ويُنتظر التالية إلى الأبد.
            ScanServiceNotifier.reset()
            if (!scanServiceLauncher.launch(ScanRequest(ScanJob.FULL_SCAN))) {
                Log.w(TAG, "scan-service-unavailable; scan not started")
                _scanFailed.value = true
            } else {
                when (val outcome = awaitScanOutcome()) {
                    is ScanOutcome.Completed -> {
                        _scanResult.value = outcome.outcome
                    }
                    is ScanOutcome.Failed -> _scanFailed.value = true
                    // ليس خطأ: فحص آخر يعمل. نُظهر الرسالة المخصّصة لا رسالة الفشل.
                    ScanOutcome.Rejected -> _scanAlreadyRunning.value = true
                    ScanOutcome.Cancelled -> Unit
                }
            }
            _isScanning.value = false
            // STAGE 4: بعد أي فحص تتغير نقاط التوقف — حدّث عرض الاستئناف.
            refreshResumeInfo()
        }
    }

    fun consumeScanResult() {
        _scanResult.value = null
    }

    /**
     * ينتظر حصيلة هذا الفحص من الناقل. الناقل يُصفَّر قبل `launch` في
     * `scanNow`/`rebuildStructure`، فأول قيمة غير null تخصّ هذا الطلب.
     */
    private suspend fun awaitScanOutcome(): ScanOutcome =
        ScanServiceNotifier.result.first { it != null } ?: ScanOutcome.Failed("no-result")

    fun consumeScanFailed() {
        _scanFailed.value = false
    }

    fun consumeScanAlreadyRunning() {
        _scanAlreadyRunning.value = false
    }

    /**
     * الخطوة 1 (الحوار): يقرأ عدد الكتب الحالي ويعرضه في نصّ التأكيد قبل
     * أن يلمس المستخدم زر التأكيد. لا يكتب شيئًا.
     */
    fun requestRebuild() {
        if (_isRebuilding.value) return
        viewModelScope.launch {
            if (!hasRootsNow()) {
                _noRootsPrompt.value = true
                return@launch
            }
            _rebuildBookCount.value = runCatching { bookDao.countAll() }.getOrDefault(0)
        }
    }

    /** إلغاء قبل البدء فقط؛ أثناء [_isRebuilding] يبقى الحوار مثبّتًا لإظهار التقدّم. */
    fun cancelRebuild() {
        if (_isRebuilding.value) return
        _rebuildBookCount.value = null
    }

    /**
     * الخطوة 2: إعادة بناء بنية المكتبة: تحذف القشور الفارغة التي تركها تغيّر نموذج التصنيف
     * (كتب مجمّعة سابقًا فقدت ملفاتها إلى كتب لكل ملف) ثم تعيد الفحص لبناء
     * البنية الجديدة. لا تمسّ الملفات على القرص ولا بيانات المستخدم.
     */
    fun rebuildStructure() {
        if (_isRebuilding.value) return
        viewModelScope.launch {
            if (!hasRootsNow()) {
                _rebuildBookCount.value = null
                _noRootsPrompt.value = true
                return@launch
            }
            _isRebuilding.value = true
            _rebuildError.value = null
            // [_rebuildBookCount] يُبقى كما هو: الحوار يظل ظاهرًا أثناء التنفيذ
            // لعرض مؤشّر التقدّم، وهو ما يحجب ما خلفه عن اللمس أصلًا.
            // PART 1: عبر الخدمة الأمامية — نظافة القشور + الفحص كلاهما مغطّى
            // بستوى foreground، فلا يُقتل الفحص على مكتبة كبيرة.
            ScanServiceNotifier.reset()
            if (!scanServiceLauncher.launch(ScanRequest(ScanJob.REBUILD))) {
                // لا rebuild مباشر: المسار القديم كان ينظّف القشور ثم يفحص
                // داخل ViewModel، أي بلا ضمانة بقاء عند خروج التطبيق للخلفية.
                Log.w(TAG, "scan-service-unavailable; rebuild not started")
                _rebuildError.value = "تعذّر بدء خدمة الفحص"
                _rebuildFailed.value = true
            } else {
                val outcome = ScanServiceNotifier.result.first { it != null }
                    ?: ScanOutcome.Failed("no-result")
                ScanServiceNotifier.reset()
                when (outcome) {
                    is ScanOutcome.Completed -> {
                        // إعادة البناء تُغلَّف بنفس عقد RebuildStructureResult ليبقى
                        // مسار العرض (Snackbar) واحدًا في الحالتين. الخدمة تنفّذ
                        // إعادة البناء كاملةً فتُرجع نفس العقد.
                        _rebuildResult.value = outcome.rebuildResult
                            ?: RebuildStructureResult(shellsRemoved = 0, orphanBooksRemoved = 0, scan = outcome.outcome)
                    }
                    is ScanOutcome.Failed -> {
                        _rebuildError.value = outcome.reason
                        _rebuildFailed.value = true
                    }
                    ScanOutcome.Rejected -> _scanAlreadyRunning.value = true
                    ScanOutcome.Cancelled -> Unit
                }
            }
            _isRebuilding.value = false
            // انتهى التنفيذ: يُغلق الحوار ليظهر الـSnackbar.
            _rebuildBookCount.value = null
        }
    }

    fun consumeRebuildResult() {
        _rebuildResult.value = null
    }

    fun consumeRebuildFailed() {
        _rebuildFailed.value = false
    }

    fun consumeRebuildError() {
        _rebuildError.value = null
    }

    fun consumeNoRootsPrompt() {
        _noRootsPrompt.value = false
    }

    /** يبني شجرة التصنيف المعاينة للمجلدات الممكّنة (بلا كتابة أي شيء). */
    fun requestClassificationPreview() {
        if (_isPreviewingClassification.value) return
        viewModelScope.launch {
            if (!hasRootsNow()) {
                _noRootsPrompt.value = true
                return@launch
            }
            _isPreviewingClassification.value = true
            _classificationPreview.value = runCatching { libraryClassificationPreview.invoke() }.getOrNull()
            _isPreviewingClassification.value = false
        }
    }

    /** استشارة القاعدة مباشرةً — لا حالة مخزّنة تتقادم. */
    private suspend fun hasRootsNow(): Boolean = libraryRootDao.countAll() > 0

    fun consumeClassificationPreview() {
        _classificationPreview.value = null
    }

    /**
     * الخطوة 1: حساب ما الذي ستغيّره إعادة التصنيف + شجرة البنية القادمة،
     * وعرضهما للتأكيد (بلا كتابة). الشجرة تأتي من [LibraryClassificationPreview].
     */
    fun requestReclassify() {
        if (_isReclassifying.value) return
        viewModelScope.launch {
            _isReclassifying.value = true
            _reclassifyPreview.value = runCatching { reclassifyLibrary(dryRun = true) }.getOrNull()
            _reclassifyTree.value = runCatching { libraryClassificationPreview.invoke() }.getOrNull()
            _isReclassifying.value = false
        }
    }

    fun cancelReclassify() {
        _reclassifyPreview.value = null
        _reclassifyTree.value = null
    }

    /**
     * الخطوة 2: تأكيد فرض إعادة التصنيف — تُنفَّذ كمهمة خلفية عبر
     * [ReclassifyScheduler] (WorkManager) وتعود نتيجتها عبر ReclassifyResultNotifier.
     */
    fun confirmReclassify() {
        if (_isReclassifying.value) return
        _isReclassifying.value = true
        _reclassifyPreview.value = null
        _reclassifyTree.value = null
        ReclassifyResultNotifier.reset()
        reclassifyScheduler.enqueue()
        // شبكة أمان: إذا فشل Worker ولم يُمرِّر نتيجة، نُنهي حالة «جارٍ» عاجلًا.
        viewModelScope.launch {
            delay(60_000)
            _isReclassifying.value = false
        }
    }

    fun consumeReclassifyApplied() {
        _reclassifyApplied.value = null
    }

    fun selectThemeMode(mode: AppThemeMode) = appSettings.setThemeMode(mode)
    fun selectLogoColor(mode: LogoColorMode) = appSettings.setLogoColor(mode)
    fun selectIntelligenceLevel(level: IntelligenceLevel) = appSettings.setIntelligenceLevel(level)
    fun setAutoSeriesClassification(enabled: Boolean) = appSettings.setAutoSeriesClassification(enabled)
    fun setDefaultSpeed(speed: Float) = appSettings.setDefaultSpeed(speed)
    fun setSkipForwardSeconds(seconds: Int) = appSettings.setSkipForwardSeconds(seconds)
    fun setSkipBackwardSeconds(seconds: Int) = appSettings.setSkipBackwardSeconds(seconds)
    fun setAutoResume(enabled: Boolean) = appSettings.setAutoResume(enabled)
    fun setPauseOnAudioDisconnect(enabled: Boolean) = appSettings.setPauseOnAudioDisconnect(enabled)
    fun setAutoNextChapter(enabled: Boolean) = appSettings.setAutoNextChapter(enabled)
    fun setKeepScreenOn(enabled: Boolean) = appSettings.setKeepScreenOn(enabled)
    fun setAutoPlayOnOpen(enabled: Boolean) = appSettings.setAutoPlayOnOpen(enabled)
    fun setDefaultSleepMinutes(minutes: Int) = appSettings.setDefaultSleepMinutes(minutes)
    fun setAutoExtendSleep(enabled: Boolean) = appSettings.setAutoExtendSleep(enabled)
    fun setNotificationsEnabled(enabled: Boolean) {
        appSettings.setNotificationsEnabled(enabled)
        reminderScheduler.syncWithSettings()
    }

    fun setMediaNotificationMinimal(minimal: Boolean) = appSettings.setMediaNotificationMinimal(minimal)
    fun setSleepTimerNotificationsEnabled(enabled: Boolean) = appSettings.setSleepTimerNotificationsEnabled(enabled)
    fun setSaveMomentNotificationsEnabled(enabled: Boolean) = appSettings.setSaveMomentNotificationsEnabled(enabled)
    fun setBookCompletionNotificationsEnabled(enabled: Boolean) = appSettings.setBookCompletionNotificationsEnabled(enabled)
    fun setDailyReminderEnabled(enabled: Boolean) {
        appSettings.setDailyReminderEnabled(enabled)
        reminderScheduler.syncWithSettings()
    }

    fun setDailyReminderTime(hour: Int, minute: Int) {
        appSettings.setDailyReminderHour(hour)
        appSettings.setDailyReminderMinute(minute)
        reminderScheduler.syncWithSettings()
    }

    fun setResumeReminderEnabled(enabled: Boolean) {
        appSettings.setResumeReminderEnabled(enabled)
        reminderScheduler.syncWithSettings()
    }

    private companion object {
        const val TAG = "SettingsVM"
    }
}