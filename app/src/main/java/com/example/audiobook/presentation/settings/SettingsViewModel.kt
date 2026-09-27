package com.example.audiobook.presentation.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.audiobook.background.reclassify.ReclassifyResultNotifier
import com.example.audiobook.background.reclassify.ReclassifyScheduler
import com.example.audiobook.data.preferences.AppSettings
import com.example.audiobook.domain.usecases.ClearDemoDataResult
import com.example.audiobook.domain.usecases.ClassificationPreviewPerRoot
import com.example.audiobook.domain.usecases.IntelligenceLevel
import com.example.audiobook.domain.usecases.LibraryClassificationPreview
import com.example.audiobook.domain.usecases.LibraryManagement
import com.example.audiobook.domain.usecases.ReclassifyLibrary
import com.example.audiobook.domain.usecases.ReclassifyPreview
import com.example.audiobook.domain.usecases.ScanLibraryNow
import com.example.audiobook.domain.usecases.RebuildLibraryStructure
import com.example.audiobook.domain.usecases.RebuildStructureResult
import com.example.audiobook.domain.usecases.ScanNowResult
import com.example.audiobook.domain.model.AppThemeMode
import com.example.audiobook.domain.model.LogoColorMode
import com.example.audiobook.background.reminders.ReminderScheduler
import com.example.audiobook.data.room.dao.BookDao
import com.example.audiobook.data.room.dao.LibraryRootDao
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val appSettings: AppSettings,
    private val reminderScheduler: ReminderScheduler,
    private val libraryManagement: LibraryManagement,
    private val libraryRootDao: LibraryRootDao,
    private val bookDao: BookDao,
    private val scanLibraryNow: ScanLibraryNow,
    private val rebuildLibraryStructure: RebuildLibraryStructure,
    private val reclassifyLibrary: ReclassifyLibrary,
    private val libraryClassificationPreview: LibraryClassificationPreview,
    private val reclassifyScheduler: ReclassifyScheduler
) : ViewModel() {

    val themeMode: StateFlow<AppThemeMode> = appSettings.themeMode
    val logoColor: StateFlow<LogoColorMode> = appSettings.logoColor
    val intelligenceLevel: StateFlow<IntelligenceLevel> = appSettings.intelligenceLevel
    val autoSeriesClassification: StateFlow<Boolean> = appSettings.autoSeriesClassification
    val defaultSpeed: StateFlow<Float> = appSettings.defaultSpeed
    val autoResume: StateFlow<Boolean> = appSettings.autoResume
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

    private val _hasLibraryRoots = MutableStateFlow(false)
    val hasLibraryRoots: StateFlow<Boolean> = _hasLibraryRoots.asStateFlow()

    /**
     * «لا توجد مجلدات» (غير true = لا شيء يُعرض). يملؤه ViewModel بعد استشارة القاعدة
     * مباشرةً (`countAll()` suspend) بدل الوثوق بقيمة `hasLibraryRoots` القديمة في
     * Composable — القراءة القديمة كانت تبقى كما لو لم تُستدعَ `refreshRootsCount()` أصلًا.
     */
    private val _noRootsPrompt = MutableStateFlow(false)
    val noRootsPrompt: StateFlow<Boolean> = _noRootsPrompt.asStateFlow()

    private val _isRemovingDemoData = MutableStateFlow(false)
    val isRemovingDemoData: StateFlow<Boolean> = _isRemovingDemoData.asStateFlow()

    /** حصيلة آخر تنظيف لبيانات التجربة (تُستهلك مرة واحدة لعرض Snackbar). */
    private val _demoCleanupResult = MutableStateFlow<ClearDemoDataResult?>(null)
    val demoCleanupResult: StateFlow<ClearDemoDataResult?> = _demoCleanupResult.asStateFlow()

    private val _isScanning = MutableStateFlow(false)
    val isScanning: StateFlow<Boolean> = _isScanning.asStateFlow()

    /** حصيلة آخر فحص فوري (تُستهلك مرة واحدة لعرض Snackbar). */
    private val _scanResult = MutableStateFlow<ScanNowResult?>(null)
    val scanResult: StateFlow<ScanNowResult?> = _scanResult.asStateFlow()

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

    private val _isPreviewingClassification = MutableStateFlow(false)
    val isPreviewingClassification: StateFlow<Boolean> = _isPreviewingClassification.asStateFlow()

    /** معاينة تصنيف المكتبة (غير null = شجرة المؤلف ← السلسلة ← الكتاب تنتظر العرض). */
    private val _classificationPreview = MutableStateFlow<List<ClassificationPreviewPerRoot>?>(null)
    val classificationPreview: StateFlow<List<ClassificationPreviewPerRoot>?> = _classificationPreview.asStateFlow()

    init {
        viewModelScope.launch {
            _hasLibraryRoots.value = libraryRootDao.countAll() > 0
        }
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

    fun refreshRootsCount() {
        viewModelScope.launch {
            _hasLibraryRoots.value = libraryRootDao.countAll() > 0
        }
    }

    fun removeDemoData() {
        viewModelScope.launch {
            _isRemovingDemoData.value = true
            val result = libraryManagement.clearDemoData()
            appSettings.setHasSeededDemoData(false)
            _demoCleanupResult.value = result
            _isRemovingDemoData.value = false
        }
    }

    fun consumeDemoCleanupResult() {
        _demoCleanupResult.value = null
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
            runCatching { scanLibraryNow() }
                .onSuccess { result ->
                    _scanResult.value = result
                    refreshRootsCount()
                }
                .onFailure { _scanFailed.value = true }
            _isScanning.value = false
        }
    }

    fun consumeScanResult() {
        _scanResult.value = null
    }

    fun consumeScanFailed() {
        _scanFailed.value = false
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
            runCatching { rebuildLibraryStructure() }
                .onSuccess { result ->
                    _rebuildResult.value = result
                    refreshRootsCount()
                }
                .onFailure { error ->
                    _rebuildError.value = error.message ?: error::class.java.simpleName
                    _rebuildFailed.value = true
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

    /** استشارة القاعدة مباشرةً + تحديث [_hasLibraryRoots] ليبقى العدّاد متسقًا. */
    private suspend fun hasRootsNow(): Boolean {
        val count = libraryRootDao.countAll()
        _hasLibraryRoots.value = count > 0
        return count > 0
    }

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
    fun setAutoResume(enabled: Boolean) = appSettings.setAutoResume(enabled)
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
}