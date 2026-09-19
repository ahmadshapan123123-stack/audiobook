# تقرير توحيد الإعدادات والمراجعة النهائية (A–K)

**المشروع:** أثير (Audiobook) — `com.example.audiobook`
**الفرع:** `main` — التغييرات **غير مُودَعة في Git** (uncommitted).
**التاريخ:** 2026-09-17
**نطاق هذا الـPass:** توحيد كل إعدادات التطبيق داخل مصدر واحد (`AppSettings`)، وإعادة بناء شاشة الإعدادات بأقسام ثابتة، وإزالة النصوص المكرّرة/المكتوبة يدويًا في الإعدادات، ثم مراجعة شاملة + اختبار انحدار. **بدون** تحويل إلى DataStore، وبدون تغيير مفاتيح التخزين أو القيم الافتراضية، وبدون إعادة تصميم الـPlayer/الـgradient/الـtheme.

---

## A. جرد الإعدادات (SETTINGS INVENTORY — قبل)

| # | الإعداد | المخزن (SharedPreferences) | المفتاح | الافتراضي | القارئ/الكاتب | المشكلة |
|---|---------|----------------------------|---------|-----------|----------------|---------|
| 1 | `defaultSpeed` | `AppSettings` → `app_settings` | `default_speed` | `1.0f` | ExoPlaybackController | مبثوث في `AppSettings` |
| 2 | `autoResume` | `app_settings` | `auto_resume` | `true` | MainActivity | — |
| 3 | `defaultSleepMinutes` | `app_settings` | `default_sleep_minutes` | `30` | MainActivity / SleepTimerController | — |
| 4 | `autoExtendSleep` | `app_settings` | `auto_extend_sleep` | `true` | SleepTimerController | — |
| 5 | `notificationsEnabled` | `app_settings` | `notifications_enabled` | `true` | PlaybackService | — |
| 6 | `intelligenceLevel` | **`ScanSettings`** → `scan` (صف منفصل) | `intelligence_level` | `BALANCED` | ScanRoot, SettingsViewModel, ScanModule | مصدر منفصل خارج `AppSettings` |
| 7 | `themeMode` | **`ThemePreference`** → `appearance` (Compose state) | `theme_mode` | `DARK` | MainActivity, SettingsScreen, Showcase screens | مصدر منفصل + يقود الثيم مباشرة |

**ملاحظة:** لا يوجد DataStore في المشروع إطلاقًا (مجرد ذكر في الوثائق). الشاشة كانت تعرض الأقسام بترتيب مختلف (المظهر أولًا)، والنص `settings_notifications` كان مستخدمًا **مرتين** لنفس الشاشة والقسم (SettingsScreen.kt:210 و:213)، والوصف "الصور المخزنة مؤقتًا" كان مضلِّلًا (لا مكتبة صور)، والنصوص الثابتة (عناوين الخيارات/الشرح) مكتوبة داخل الـCompose.

---

## B. جرد الإعدادات (SETTINGS INVENTORY — بعد)

**مصدر واحد:** `data/preferences/AppSettings.kt` أصبح الواجهة (facade) الوحيدة لكل الإعدادات، `@Singleton @Inject`.

| # | الإعداد | الملف/المفتاح (كما هو — بلا كسر) | الافتراضي (كما هو) | الواجهة المكشوفة |
|---|---------|----------------------------------|--------------------|-------------------|
| 1 | `defaultSpeed` | `app_settings` / `default_speed` | `1.0f` | `StateFlow<Float>` + `setDefaultSpeed` |
| 2 | `autoResume` | `app_settings` / `auto_resume` | `true` | `StateFlow<Boolean>` + `setAutoResume` |
| 3 | `defaultSleepMinutes` | `app_settings` / `default_sleep_minutes` | `30` | `StateFlow<Int>` + `setDefaultSleepMinutes` |
| 4 | `autoExtendSleep` | `app_settings` / `auto_extend_sleep` | `true` | `StateFlow<Boolean>` + `setAutoExtendSleep` |
| 5 | `notificationsEnabled` | `app_settings` / `notifications_enabled` | `true` | `StateFlow<Boolean>` + `setNotificationsEnabled` |
| 6 | `intelligenceLevel` | `scan` / `intelligence_level` | `BALANCED` | `StateFlow<IntelligenceLevel>` + `currentIntelligenceLevel()` + `setIntelligenceLevel` |
| 7 | `themeMode` | `appearance` / `theme_mode` | `DARK` | `StateFlow<AppThemeMode>` + `currentThemeMode()` + `setThemeMode` |

- **حُذف:** `data/preferences/ScanSettings.kt` بالكامل، و`ThemePreference` من `presentation/theme/AppTheme.kt`.
- **حُذف:** `ScanModule.provideScanSettings()` (لم يعد مطلوبًا؛ `AppSettings` يُحقن تلقائيًا عبر `@Inject`).
- **حُذف:** مرجع `ThemePreference` من MainActivity وSettingsScreen وشاشتي العرض التجريبي.
- **لم يُغيَّر أي مفتاح ولا اسم ملف** → القيم المحفوظة لدى المستخدمين تُقرأ كما هي (إثبات emulator في القسم G).

**المتحقق عبر grep بعد التغيير:**
- `getSharedPreferences` يوجد **فقط** داخل `AppSettings.kt` (3 مرات: `app_settings`/`scan`/`appearance`).
- عدد مراجع `ScanSettings|ThemePreference` في `src/main` و`src/test` = **0** (عدا تعليق موثّق تم تحديثه).

---

## C. واجهة الإعدادات (SETTINGS UI)

الترتيب الثابت المطبَّق في `SettingsScreen.kt` (كل قسم = عنوان + وصف مختصر + القيمة الحالية مرئية):

1. **التشغيل** — `settings_playback` + `settings_playback_desc` → سرعة افتراضية (شبكة خيارات، القيمة المختارة مُبرَزة) + `استئناف تلقائي` (Switch).
2. **مؤقت النوم** — `settings_sleep_timer` + `_desc` → المدة الافتراضية (شبكة) + `تمديد تلقائي` (Switch).
3. **المظهر** — `settings_appearance` + `_desc` → ثلاثة خيارات Radio (فاتح/داكن/أموليد) مع "القيمة الحالية" = الخيار المُبرَز، ويُطبَّق **دون إعادة تشغيل**.
4. **المكتبة والفحص** — `settings_library` (قيمته الآن "المكتبة والفحص") + `settings_library_desc` → مجلدات المكتبة + فحص الآن، ثم `settings_intelligence` + وصفه + 3 Radio cards (محافظ/متوازن/ذكي) + ملاحظة القيد الصارم.
5. **الإشعارات** — `settings_notifications` + `settings_notifications_desc` (وصف القسم) → صف `settings_notifications_title`="تفعيل الإشعارات" + `settings_notifications_row_desc`.
6. **البيانات والتخزين** — `settings_storage` + `settings_storage_desc` → **حجم قاعدة البيانات** (قراءة فقط: `audiobook.db` + WAL/SHM) + زر المسح.
7. **عن أثير** — `settings_about` + `settings_about_section_desc` → الإصدار + الوصف.

**التنسيق:** يبقى على نظام الثيم الديناميكي القائم (`MaterialTheme.colorScheme` + `CosmicScreenHeader` + البطاقات)، بدون أي تعديل على لوحة الألوان. عند تغيير المظهر تنعكس القيمة فورًا لأن MainActivity يجمَع `appSettings.themeMode` كـ`StateFlow`.

---

## D. عناصر واجهة ميتة/غير فعّالة

- **زر "مسح ذاكرة التخزين المؤقت":**
  - الواقع: `clearCacheInternal()` تحذف `context.cacheDir` فقط. لا يوجد download ولا مكتبة تحميل صور (Coil/Glide) في المشروع، لذا لا توجد "صور مخزنة" أصلًا — الوصف القديم كان مضلِّلًا.
  - **القرار:** الإبقاء على الزر (وظيفة حقيقية = تفريغ الملفات المؤقتة) مع **تصحيح التسمية والوصف**: العنوان "مسح الملفات المؤقتة"، والوصف "حذف الملفات المؤقتة فقط دون المساس بالكتب أو الإعدادات أو قاعدة البيانات." هذا يطابق الفعل الفعلي ولا يوحي بحذف غير موجود.
  - **أُضيف** صف **حجم قاعدة البيانات** (قراءة فقط) لإضفاء قيمة حقيقية على قسم التخزين.

---

## E. النصوص المكتوبة يدويًا (HARDCODED STRINGS)

**شاشة الإعدادات — أُصلحت بالكامل:**
- `"الإعدادات"` → `settings_title`؛ `"تخصيص أثير"` → `settings_subtitle`.
- عناوين/أوصاف الخيارات الثلاثة لمستوى الذكاء → `settings_intelligence_{conservative|balanced|aggressive}_{title|desc}`.
- ملاحظة القيد الصارم → `settings_intelligence_strict_note`.
- وصف كل قسم → `settings_*_desc` / `settings_about_section_desc` / `settings_library_desc`.
- `speedLabel` → مورد `settings_speed_value` = `%1$s×`.
- **تقسيم النص المكرّر:** `settings_notifications` صار عنوان القسم فقط، وأُضيف `settings_notifications_title` + `settings_notifications_row_desc` لصف الـSwitch.

**نصوص مكتوبة يدويًا خارج نطاق الإعدادات (تم رصدها فقط — لم تُعدَّل):**
`StatisticsScreen` (متعددة)، `ReviewMatchesScreen` (متعددة)، `BookmarksScreen` ("انتقال"/"حذف")، `HistoryScreen` ("لا توجد جلسات بعد")، و`DesignSystemShowcase`/`CosmicSmokeScreen` (شاشات عرض تجريبية داخل نظام الثيم). تُوصى بمعالجتها في Pass لاحق لأنها تمسّ ملفات وشاشات غير مرتبطة بتوحيد الإعدادات وخارج نطاق هذه المهمة (خشية كسر اختبارات الوصولية). **لم تُغيَّر** `values-en` لأنها غير موجودة ولن تُنشأ.

---

## F. نتائج المراجعة الشاملة عبر التطبيق (CROSS-APP REVIEW)

**A) مصدر واحد لمفتاح الإعداد/الواجهة/القارئ:** ✅
- `getSharedPreferences` محصور في `AppSettings.kt`.
- لا توجد مراجع مباشرة لـ`ScanSettings`/`ThemePreference` خارج `AppSettings`.
- القرّاء: `ExoPlaybackController`, `SleepTimerController`, `PlaybackService`, `MainActivity`, `ScanRoot`, `SettingsViewModel` — كلهم عبر `AppSettings`.

**B) الشاشات تستخدم الثيم الديناميكي:** ✅
- كل الشاشات تستعمل `MaterialTheme.colorScheme` و`mode` القادم من `AudiobookTheme`. لم يُعدَّل نظام الألوان؛ تغيير المظهر يُدفع من `AppSettings.themeMode` (StateFlow) → `MainActivity` → `AudiobookTheme`. أثبت تبديل AMOLED الحي + صموده بعد إعادة التشغيل على المحاكي.

**C) النصوص/الأيقونات:** ⚠️ جزئي
- شاشة الإعدادات: كل النصوص `stringResource`، وأيقونة صف التنقّل بلا `contentDescription` لأنها زخرفية بجانب نص وصفي (مقبول).
- باقي الشاشات: لا تزال تحوي نصوصًا عربية ثابتة (القسم E). **لم تُعدَّل** (خارج النطاق).

**D) Room:** ✅
- `AppDatabase` الإصدار `3` ثابت، `MIGRATION_1_2` و`MIGRATION_2_3` موجودتان، `DATABASE_MIGRATIONS` مُمرَّرة في `DatabaseModule` و`DemoAudioProvider`. **لا يوجد** `fallbackToDestructiveMigration` في كود الإنتاج (يظهر فقط `allowMainThreadQueries` في الاختبارات).

**E) قرّاء الإعدادات يستخدمون `AppSettings` فقط:** ✅ (نفس أدلة A).

---

## G. نتائج اختبار الانحدار

| الفحص | الأمر | النتيجة |
|------|-------|---------|
| ترجمة الإنتاج | `:app:compileDebugKotlin` | ✅ نجاح |
| اختبارات الوحدات | `:app:testDebugUnitTest` | ✅ **145 اختبار / 0 فشل / 0 خطأ / 0 متخطّى** (37 suite) |
| حزمة APK | `:app:assembleDebug` | ✅ نجاح |

**تشغيل فعلي على المحاكي (emulator-5554، Pixel 7، API 36):**
1. **autoResume (انحدار الـPass السابق):** الإقلاع فتح الـPlayer مباشرة على آخر كتاب قيد الاستماع (الفصل الثامن) → السلوك سليم بعد التوحيد.
2. **ترتيب الأقسام:** التنقّل للإعدادات أظهر الترتيب: التشغيل ← مؤقت النوم ← المظهر ← المكتبة والفحص ← الإشعارات ← البيانات والتخزين ← عن أثير، مع أوصاف الأقسام.
3. **تقسيم نص الإشعارات:** ظهر "الإشعارات" (قسم) و"تفعيل الإشعارات" (صف) كنصّين منفصلين.
4. **التخزين:** ظهر "حجم قاعدة البيانات 722 KB" + الوصف الصادق للمسح.
5. **الكتابة عبر الواجهة الموحّدة:** الضغط على "محافظ" كتب `scan/intelligence_level = CONSERVATIVE`، والضغط على "أموليد" كتب `appearance/theme_mode = AMOLED` — **بنفس أسماء الملفات والمفاتيح القديمة**.
6. **الثبات بعد إعادة التشغيل:** `force-stop` ثم تشغيل → القيمة `AMOLED` بقيت مقروءة، **بلا `FATAL EXCEPTION`**.
7. **تنظيف:** أُزيلت ملفات prefs التجريبية لإرجاع المحاكي للافتراضي.

**اختبارات وُجدت/حُدّثت:** `SettingsViewModelTest` (+اختبار ثبات المظهر عبر `AppSettings` جديد)، و`SettingsChoiceDrivesScanIntegrationTest`، و`ScanRootTest`، و`SettingsScreenAccessibilityTest` (إضافة `performScrollTo` قبل النقر لأن خيار الذكاء انتقل أسفل الشاشة بعد إعادة الترتيب)، و`DesignSystemShowcaseAccessibilityTest` (بدّل توقيع الشاشة من `ThemePreference` إلى `mode` + `onModeChange`).

---

## H. الملفات المعدّلة (هذا الـPass)

**إنتاج:**
- `data/preferences/AppSettings.kt` (facade موحّد: +`themeMode` +`intelligenceLevel` + setters + قراءات متزامنة).
- `data/preferences/ScanSettings.kt` (**محذوف**).
- `data/di/ScanModule.kt` (حذف مزوّد `ScanSettings`).
- `presentation/theme/AppTheme.kt` (حذف صف `ThemePreference`).
- `domain/usecases/ScanRoot.kt` (`AppSettings` بدل `ScanSettings`).
- `presentation/settings/SettingsViewModel.kt` (`AppSettings` فقط + `themeMode`).
- `presentation/settings/SettingsScreen.kt` (ترتيب الأقسام، الأوصاف، `stringResource`، حجم الـDB، وصف المسح).
- `MainActivity.kt` (حذف `ThemePreference`؛ جمع `appSettings.themeMode`؛ تمرير الوضع للـPlayer).
- `presentation/theme/DesignSystemShowcase.kt` و`CosmicSmokeScreen.kt` (`mode` + `onModeChange`).
- `res/values/strings.xml` (نصوص جديدة + تصحيح/تقسيم + إزالة وصف مضلِّل).

**اختبارات:** `SettingsViewModelTest.kt`, `SettingsChoiceDrivesScanIntegrationTest.kt`, `ScanRootTest.kt`, `SettingsScreenAccessibilityTest.kt`, `DesignSystemShowcaseAccessibilityTest.kt`.

> ملاحظة: `git status` يُظهر أيضًا ملفات الـPass السابق (ExoPlaybackController, PlaybackService, SleepTimerController, MarksCoordinator, PlayerScreen, PlayerTimeline + اختباراتها) لأن كلا الـPassين غير مُودَعين بعد.

---

## I. الملفات المفحوصة دون تعديل

- `data/room/AppDatabase.kt`, `data/di/DatabaseModule.kt`, `data/demo/DemoAudioProvider.kt` (للتحقق من الهجرات).
- `presentation/theme/CosmicShell.kt`, `CosmicBackground.kt`, `MiniPlayer.kt`, `PlayerVisuals.kt`, `player/PlayerScreen.kt` (مراجعة استخدام الوضع/الألوان فقط).
- `presentation/statistics/StatisticsScreen.kt`, `HistoryScreen.kt`, `reviewmatches/ReviewMatchesScreen.kt`, `bookmarks/BookmarksScreen.kt` (رصد النصوص الثابتة فقط).
- `DESIGN.md`, `AUDIOBOOK_REMEDIATION_PLAN.md`, `R6_FINAL_REPORT.md` (مراجع توثيقية — لم تُحدَّث).

---

## J. القيود المتبقية

1. **اعتماد طبقي:** `AppSettings` (في `data`) يستورد `presentation.theme.AppThemeMode` لتخزين الوضع كنوع مُنمَّط. مقبول عمليًا داخل module واحد، لكنه يخالف اتجاه الطبقات `presentation → data`. الحل النظيف لاحقًا: نقل `AppThemeMode` إلى طبقة محايدة.
2. **نصوص عربية ثابتة** في Statistics/ReviewMatches/Bookmarks/History وشاشات العرض التجريبي (القسم E/F-C) — لم تُلمس.
3. **حجم قاعدة البيانات:** قُرئ من مسار الملف مباشرة (`audiobook.db` + WAL/SHM)؛ لا يُحدَّث حيًّا أثناء فتح الشاشة (يُعاد حسابه عند فتح الشاشة).
4. **التحقق البصري:** لا يمكن للمساعد قراءة لقطات الشاشة؛ الاعتماد كان على `uiautomator dump` + قراءة ملفات prefs + logcat. يبقى التأكيد البصري النهائي على المستخدم.
5. **غير مُودَع:** كل التغييرات في working tree.

---

## K. الخطة المقترحة للـPass القادم

1. تحديث `DESIGN.md` ليذكر أن مصدر كل الإعدادات هو `AppSettings` (وإزالة ذكر `ThemePreference`).
2. تحويل النصوص العربية الثابتة المتبقية إلى `stringResource` (Statistics ثم ReviewMatches ثم Bookmarks/History) بحذر مع تحديث اختبارات الوصولية المقابلة.
3. نقل `AppThemeMode` إلى طبقة محايدة (مثل `domain/model` أو `data/preferences`) لإزالة اعتماد `data → presentation`.
4. جعل "حجم قاعدة البيانات" يُحدَّث عبر `Flow` أو عند العودة للشاشة بدل قراءة لحظية.
5. توحيد الأنماط البصرية لبطاقات الإعدادات مع بقية النظام الزجاجي (Haze) إن رُغِب — مع الحفاظ على الثيم الحالي.
