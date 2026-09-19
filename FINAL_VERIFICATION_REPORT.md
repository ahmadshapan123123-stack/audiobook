# تقرير التحقق النهائي — المراجعة الكاملة لبنود FINAL_ANALYTICAL_REPORT.md

**الوضع:** قراءة فقط (read-only). لم يُعدّل أي ملف مصدري، ولم تُشغَّل أي build.
**الأدلة:** كود المصدر مع `file:line` + أثريّات build على القرص (`app/build/**`) + XML اختبارات + `lint-baseline.xml` + تحليل APK الإصدار مباشرةً.
**المتغيرات:** `minSdk=23, compileSdk=36, targetSdk=36` (`app/build.gradle.kts`). إجمالي الاختبارات المعاد احتسابه: **38 suite / 155 test / 0 fail / 0 error / 0 skipped**.

---

## A. الجدول الموجز

| # | بند الادعاء في التقرير | الحكم | خلاصة الدليل |
|---|------------------------|-------|--------------|
| 1 | الـ15 خطأ في lint موزعة MissingPermission(5) + NewApi(8) + ExportedService(1) + WrongConstant(1) وأنها كلّها "false-positive-class" | **VERIFIED (الأماكن/العدّادات) + REFUTED (كلها كاذبة)** | العدّهات مطابقة تمامًا، لكن 4 مواقع NewApi غير محرّسة فعلاً = مخاطر حقيقية، واستنتاج "الـ15" من العدّات قاصر على الـ4 أصناف (لا تُخزَّن severity في baseline) |
| 2 | R8 يشذّب فئات demo في الإصدار | **VERIFIED** | بحث بايتي في `classes.dex` بالإصدار: لا توجد أي رموز demo؛ تعليق `proguard-rules.pro` غير دقيق لكن النتيجة سليمة |
| 3 | المصنف يميّز AUTHOR/SERIES/BOOK/MIXED وكل مجلد كتاب مستقل لا يُدمج | **VERIFIED** (مع دقة أسلوبية) | المنطق في `FolderClassifier` صحيح ويُثبَت بالاختبار؛ لكن "فانتازيا" في مثالك = BOOK لا SERIES لوجود ملفات مباشرة |
| 4 | التعامل مع الملفات المكررة (unique index + تصفية إعادة الفحص) | **VERIFIED** | `Entities.kt:106` فهرس فريد على fileUri + مساري ديدوب في `ScanRoot` + اختبارا rescan |
| 5 | زر "فحص المكتبة الآن" في الإعدادات | **VERIFIED** | `SettingsScreen.kt:284-299` → `scanNow()` → `ScanLibraryNow.kt` |
| 6 | شاشة Onboarding تُعرض مرة واحدة فقط | **VERIFIED** | `MainActivity.kt:198,206-210` + مفتاح `has_completed_onboarding` في AppSettings |
| 7 | عزل بيانات demo + شارة isDemo | **VERIFIED** | `DatabaseSeeder:185`, `Entities.kt:75`, `Daos.kt:64-65`, `LibraryManagement:534-540`, شارة Debug-only `LibraryScreen:651-666` |
| 8 | Bulk select وعمليات متعددة | **VERIFIED** | `LibraryScreen.kt:147-150/193-212/383-414/457-487` → BookManagerViewModel |
| 9 | إيماءات السحب (انهيار Player + Mini dismiss + سحب أُفقي) | **VERIFIED** (بتمييز) | سحب عمودي فقط للطي/الإغلاق (`PlayerScreen:449-479`, `MiniPlayer:154-179`)؛ الأُفقي = سحب مقبض فصل في الجدول الزمني لا تبديل أغنية |
| 10 | هوية إشعار "أثير" (قناتان + تخطيطا RemoteViews + لوحة ألوان) | **VERIFIED** | `AtherMediaNotificationProvider.kt` كامل + `notification_ather_{strip,panel}.xml` + `ic_stat_ather.xml` |
| 11 | الشعار المتكيِّف + Splash | **VERIFIED** | `ic_launcher.xml`/`ic_launcher_round.xml` (adaptive+monochrome) + `ic_launcher_foreground.xml` + `AtherSplash.kt` |
| 12 | أقسام المؤلفين/السلاسل في Home | **VERIFIED** | `HomeScreen.kt:156-191` + بناء الأقسام في `HomeViewModel:166-222` |
| 13 | 155 اختبارًا + توثيق الثغرات | **VERIFIED** | أُعيد احتسابها من XML: 38/155/0/0/0 |
| 14 | APK الإصدار نظيف (بلا demo/مزوّدات) | **VERIFIED** | تحليل مباشر للـAPK الموجود على القرص + manifest المُغلَّف |
| 15 | lint نظيف بعد baseline والتعداد الكامل | **VERIFIED (الخلوّ) + REFUTED (التعداد الكامل)** | "No errors or warnings" صحيح، لكن تعداد التقرير ناقص صنف InlinedApi(3) وادّعاء "كل الـ15 errors كاذبة" مبالغ فيه (راجع بند C) |

**مدهش مهم:** بلغ مجموع أصناف baseline المحسوبة = **185** وعينًا، وعدّادات كل صنف تطابق التقرير تمامًا (83 UnusedResources, 30 UseKtx, 16 GradleDependency, 13 UseToml, 8 NewApi, 5 NewerVersionAvailable, 5 MissingPermission, 5 IconLauncherShape, 3 InlinedApi, 2×4 أصناف صغيرة, و9 أصناف بـ1). إذن بيانات التقرير الرقمية صحيحة ما عدا إغفال InlinedApi في القائمة النصية.

---

## B. التفصيل بالأدلة

### البند 1 — أصناف الـ15 خطأ في lint

مواقع baseline المؤكَّدة (من `app/lint-baseline.xml`):

- **MissingPermission (5):** `AtherNotificationCenter.kt:65, 88, 114, 131, 144` — كلها `manager.notify(...)`
- **NewApi (8):** `AtherNotificationCenter.kt:72`؛ `EditionSignals.kt:82, 94, 105`؛ `NotificationChannels.kt:42, 43, 43, 44`
- **ExportedService (1):** `src/main/AndroidManifest.xml:20` (PlaybackService)
- **WrongConstant (1):** `PlaybackService.kt:151`

ميزان الحماية الفعلي لكل صنف (أدلة جديدة تحسم الحكم):

| الموقع | الصنف | مضمون | حراسة فعلية؟ | الحكم |
|--------|-------|-------|--------------|-------|
| AtherNotificationCenter:65/88/114/131/144 | MissingPermission | notify | نعم — كلها خلف `if (!canPost(...)) return` (الأسطر 50, 77, 98, 119, 135) و`canPost` (:147-154) تفحص الإذن على T+ و`areNotificationsEnabled()` | **كاذب زائف (محروس)** |
| NotificationChannels:42/43/43/44 | NewApi | NotificationChannel (API 26) | نعم — `createAll` تعيد مبكرًا `if (SDK_INT < O) return` (:29) | **كاذب زائف (محروس)** |
| AtherNotificationCenter:72 | NewApi | `PendingIntent.getForegroundService` (API 26) | **لا** — تُستدعى ضمن `sleepAction()` عند بناء إشعار مؤقّت النوم، والحارس `canPost` لا يفحص API | **حقيقي — خطر NoSuchMethodError على Android 6–7.1** |
| EditionSignals:82/94/105 | NewApi | وصول مجموعات regex المسماة `groups["..."]` → `Matcher.start(String)` (API 26) | **لا** — دوال نقية تُستدعى أثناء الفحص بلا أي فحص `Build.VERSION` | **حقيقي — 3 مخاطر NoSuchMethodError أثناء الفحص على Android 6–7.1** |
| AndroidManifest:20-27 | ExportedService | PlaybackService exported إلزامي لـMediaSessionService | مقصود — Media3 يتطلب exported=true ليستقبل النظام الترجيحات/الربط | **كاذب زائف (متطلب إطار العمل)** |
| PlaybackService:151 | WrongConstant | `SessionResult(RESULT_ERROR_BAD_VALUE)` | مكافئ سلوكيًا | **مكافئ — الفرق أسلوبي** (ثُبّت بفحص javap: الثابتان -3، ومُنشئ int يغلّف السالب كـSessionError) |

> **حول "الـ15 خطأ":** `lint-results-debug.txt` يقول حرفيًا: *"No errors or warnings (and 15 errors, 168 warnings, 2 hints filtered by baseline)"*. المجموع الذاتي للأصناف الأربعة 5+8+1+1 = 15. لكن `lint-baseline.xml` (format 6) **لا يخزّن severity** لكل عنصر، وملفّات النتائج تحتوي فقط تلخيصًا + تلميح LintBaseline واحدًا. إذن توزيع "الـ15" على هذه الأصناف الأربعة **استنتاج متّسق مع العدّهات، لا إثبات من القرص** (راجع القسم E).

### البند 2 — تشذيب فئات demo في الإصدار

- فحص بايت-با-بايت لـ`classes.dex` في `app-release.apk` الموجود على القرص (4,937,726 ب): المصطلحات `Seeder`, `DemoAudioProvider`, `EntryPoint`, `DatabaseSeeder`, `com/example/audiobook/demo` **كلها غائبة** (النمط الوحيد الموجود `isDemo` = حقل في الجداول).
- `proguard-rules.pro:2-3` يزعم أن فئات demo "تعيش حصريًا في debug source set" — **غير دقيق**: `DatabaseSeeder.kt:31` و`DemoAudioProvider.kt:38` في `src/main/java`. رغم ذلك التشذيب ينجح لأن المرجع الوحيد لهما عبر `BuildConfig.DEBUG` (ثابتة الكذب) وmanifest debug.
- manifest الإصدار المُغلَّف لا يحوي مزوّد demo — مزوّد `androidx.startup.InitializationProvider` فقط.

### البند 3 — تصنيف المجلدات

- `FolderClassifier.kt:32` — `FolderKind { AUTHOR, SERIES, BOOK, MIXED_BOOK, EMPTY }`؛ منطق `classifyNode` يعتمد على: وجود ملفات مباشرة، وجود مجلدات صوت حاوية، وعمق الحاوية.
- **في مثال الأدلة لديك** (`أحمد خالد توفيق/فانتازيا/01.mp3 …mp3` حيث الملفات داخل كورا فانتازيا مباشرةً):
  - `فانتازيا` تحوي ملفات مباشرة ولا تحوي مجلدات صوت حاوية ⇒ **BOOK** (ليست SERIES) — العنوان = "فانتازيا"، المؤلف = أحمد خالد توفيق.
  - `أحمد خالد توفيق` عمق 1 بلا ملفات مباشرة وبها مجلد صوت حاوية ⇒ **AUTHOR**.
- سيتصرّف SERIES فقط لو حوى `فانتازيا` مجلدات كتب نازلة (مثال head التقرير الداخلي `:8-31`) — أي "فانتازيا = SERIES" في سؤالك تعبير مبني على الشجرة التوضيحية لا على مثال الملفات المسطّح.
- **عدم الدمج مُثبَت اختباريًا:** `ScanRootTest` R1 يبني 3 مجلدات تحت مؤلف واحد ويأكد: 3 كتب مستقلة، 0 دمج تلقائي، وكل كتاب من مجلده بـ author موحّد. و`EditionIntelligence.mergeDecision:118-128` يمنع الدمج عند اختلاف سلسلة/مؤلف/راوٍ، والإصدارات تتعرّف عبر `getByRootAndFolder` (هوية مجلد) لا `getByAuthorAndTitle` — فلا يوجد مسار دمج بمؤلف-parent.

### البند 4 — عدم تكرار الملفات

- `Entities.kt:106` — `Index(value=["fileUri"], unique=true)` على AudioFileEntity.
- مسارا تصفية في `ScanRoot.kt`: (:99-106) ديدوب ضمن نفس المجلد (اسم+حجم) يعتدّ كـ`filesDeduped++`؛ (:146-152) قبل إدراج ملف جديد يفحص `getByEditionNameSize` ويمنع الإدراج المكرر.
- الاختبارات: `ScanRootTest.kt:161-175` `rescanSameRootTwiceNeverDuplicatesFiles` (نفس العدد بعد مرتين) و`:180-199` إعادة تسمية مجلد → قديم يُعلَّم MISSING والجديد يُنشأ بلا نسخة مكررة من الملف الفيزيائي نفسه.

### البند 5 — زر الفحص الفوري

`SettingsScreen.kt:284-299` (SettingsActionRow "فحص المكتبة الآن") → يفحص `hasLibraryRoots` ثم `viewModel.scanNow()` أو يفتح حوار بلا جذور → `SettingsViewModel.scanNow():100-112` → `ScanLibraryNow.kt` يفحص جميع الجذور الممكّنة (خلفية+أولوية) ويرجع `ScanNowResult`.

### البند 6 — Onboarding لمرة واحدة

- `MainActivity.kt:198` `hasOnboarded` من `appSettings.hasCompletedOnboarding`؛ `:206-210` الشرط `else if (!hasOnboarded) OnboardingScreen(...)`؛ عند الإنهاء: `setHasCompletedOnboarding(true)`.
- المفتاح `KEY_HAS_COMPLETED_ONBOARDING = "has_completed_onboarding"` (AppSettings:237) افتراضي false، لافتة قبوله/كتابته (:161-164, :82-83).
- واجهة `OnboardingScreen.kt:38-160` بثلاث خطوات (:42-58).

### البند 7 — عزل بيانات demo

- `DatabaseSeeder.kt:185` — الكتب التجريبية تُكتب بـ `isDemo = true`.
- `Entities.kt:75` — عمود `isDemo` في BookEntity (افتراضي false).
- `Daos.kt:64-65` — `getDemoBooks()` / `deleteDemoBooks()`.
- `LibraryManagement.clearDemoData():534-540` — حذف cascading لكل كتاب demo ثم `deleteDemoBooks()`.
- `MainActivity.kt:193` — في الإصدار تُستدعى `clearDemoData()` لإزالة أي بقايا حين الترقية من build تصحيح؛ وفي debug تتم البذرة عبر SeederEntryPoint (:187-191).
- الشارة: `LibraryScreen.kt:608/638` تُعرض `DemoBadge()` فقط عند `book.book.isDemo` و`(:651-666)` تبني نفسه بـ `if (!BuildConfig.DEBUG) return` — أي شارة debug-only على كتب موسومة هويةً.

### البند 8 — التحديد الجماعي

`LibraryScreen.kt`: حالة `selectionMode/selectedIds` (:147-150)؛ دخول من `onCardLongPress` (:163-169) أو القائمة (:247-248)؛ شريط علوية مع عداد وتبديل تحديد الكل (:193-212)؛ تحويل عناصر القائمة/الشبكة (:383-391)؛ شريط سفلي بالعمليات: حذف/نقل لمؤلف/نقل لسلسلة/إضافة لمجموعة/تعليم مفضّل (:393-414)؛ حوار تأكيد وحقنها في `BookManagerViewModel` (bulkDelete/bulkMoveToAuthor/bulkMoveToSeries/bulkAddToCollection/bulkSetFavorite) عند (:457-487, :431/441/451).

### البند 9 — إيماءات السحب

- **عمودي:** `PlayerScreen.kt:449-479` — سحب لأسفل يطوي المشغّل الكامل (collapse); `MiniPlayer.kt:154-179` — سحب لأسفل يطرد المشغّل المصغّر ويوقف التشغيل.
- **أُفقي:** `PlayerScreen.kt:1354-1371` — **سحب مقبض فصل على الجدول الزمني (seek فصل)** وليس "سحبًا للانتقال بين المقاطع/الأغاني". لا يوجد "سحب لحذف" في أي مكان (الحذف يتم عبر bulk فقط).
- التقرير الأصلي وصف هذا بدقة (J.2 + MiniPlayer dismiss)؛ فقط لا تقرأ "delete/play ⇒ swipe".

### البند 10 — هوية الإشعار

`AtherMediaNotificationProvider.kt`:
- قناتان: `PLAYBACK_FULL`/`PLAYBACK_MINIMAL`؛ التبديل عبر `useMinimal():89-90`؛ والتقرير المطابق (:66-69).
- الوضع الكامل (:120-172): `ic_stat_ather` كـsmallIcon (:129)، غلاف، عنوان/فصل/مؤلف، progress، 5 Actions (prev/play-next/±15) (:156-163)، تخطيطا RemoteViews مُخصّصان (:169-170, 174-222) على `R.layout.notification_ather_strip` و`notification_ather_panel` (الملفان موجودان على القرص)، ولوحة ألوان كونية حسب الوضع (فاتح/داكن/AMOLED) (:102-118) مع accent ديناميكي (:137).
- الوضع المصغّر (:253-264): "يتم التشغيل" بلا أزرار للقناة الأدنى.
- بيانات lock screen من MediaSession/MediaStyle (:26-40 doc, :164-167 MediaStyle).

### البند 11 — الشعار المتكيِّف + Splash

- `mipmap-anydpi-v26/ic_launcher.xml`: adaptive-icon (background `@color/ic_launcher_background` + foreground `@mipmap/ic_launcher_foreground` + `monochrome`).
- `mipmap-anydpi-v26/ic_launcher_round.xml` المكافئ؛ `drawable/ic_launcher_foreground.xml` (الشعار)؛ `drawable/ic_stat_ather.xml` (أيقونة الإشعار).
- `AtherSplash.kt` موجود (Splash بلمعان ≈1600ms حسب التقرير F16)؛ الاسم في MainActivity:204-205 مع noe `logoColorMode`.

### البند 12 — أقسام Home

- `HomeScreen.kt:156-175` قسم السلاسل (`state.series` + LazyRow من HomeSeriesCard) خلف `HomeSectionPanel`.
- `HomeScreen.kt:177-191` قسم المؤلفين (HomeAuthorCard).
- البناء في `HomeViewModel.kt:166-222` (seriesSection يُبنى من `seriesDao.observeAll()` مع members، وauthorsSection، وأقسام Collections/Favorites/Recently/Continue)، و`ListeningHubViewModel` مثيل مماثل (:143-168).

### البند 13 — الاختبارات

أُعيد احتساب كل ملفات `app/build/test-results/testDebugUnitTest/*.xml` برمجيًا: **38 suite, 155 tests, 0 failures, 0 errors, 0 skipped**. أرقام التقرير في Section G مطابقة. القائمة في التقرير (22 suite ذُكرت)↔الأرقام متطابقة. الثغرات المذكورة في Section J (on-device غير مُجرَّب، Hilt wiring غير مُاختبَر androidTest، إلخ) معقولة ومتوافقة مع ما نراه من كود.

### البند 14 — نظافة APK الإصدار

التحقيق المباشر للـAPK الموجود (`app/build/outputs/apk/release/app-release.apk`, 4,937,726 ب):
- `classes.dex` (5,004,916 ب): لا رموز demo (انظر البند 2).
- Manifest المُغلَّف لا يحوي مزوّد demo؛ مزوّد واحد androidx.startup؛ FGS type=mediaPlayback على PlaybackService؛ MainActivity exported.
- لا يوجد `keystore.properties` → توقيع build.gradle يتراجع لـ debug-signing للإصدار (ملاحظة أُثبتت سابقًا، لا تؤثر على المضمون).

### البند 15 — نظافة lint الأساسية

- `lint-results-debug.txt`: *"No errors or warnings (and … filtered by baseline)"* — خلوّ قبل suppression مُثبَت.
- **إغفال في تعداد التقرير:** قائمة `FINAL_ANALYTICAL_REPORT.md:251` تذكر 21 صنفًا بمجموع 182، بينما baseline الفعلية 185 وتتضمن **InlinedApi(3)** غير المذكور. بقية العدّادات مطابقة حرفيًا (راجع الجدول الموجز).
- **مبالغة في الحكم:** جملة التقرير "الـ15 errors كلها false-positive-class (… NewApi محروس بـ minSdk)" **غير صحيحة** — Upgrade إلى البند C.

---

## C. أخطاء حقيقية وجدتها المراجعة

1. **خطر NoSuchMethodError (4 مواقع NewApi غير محمية) على Android 6–7.1 (API 23-25) مع minSdk=23:**
   - `EditionSignals.kt:82, 94, 105` — `match.groups["suffix"/"name"/"num"]` يُترجم لمكالمات `Matcher.start(String)` (API 26) بلا أي حارس `Build.VERSION`. تُنفَّذ أثناء فحص المكتبة على كل الأجهزة. أي ملف يطابق أنماط السلسلة/الراوي/الترقيم سيثير الانكسار هناك.
   - `AtherNotificationCenter.kt:72` — `PendingIntent.getForegroundService` (API 26) يُستدعى عند بناء إشعار مؤقّت النوم، والحارس (`canPost`) لا يفحص مستوى API.
   - سببا ظهورهما: الفحوصات تعمل على JVM (API اصطناعي مرتفع) فلم تكشف الاختبارات أمرًا، وعلى أجهزة حديثة لن يظهر شيء — لذلك "خطأ خفي".

2. **تعليق مضلِّل في ملفات الإنتاج:** `app/proguard-rules.pro:2-3` يزعم أن فئات demo "تعيش حصريًا في debug source set" بينما هي في `src/main`. لا يكسّر التشذيب (لا تزال تنزرف لأن المرجع الوحيد عبر DEBUG الثابتة الكذب وmanifest debug)، لكنه وثيقة غير صحيحة خطر تفسيرها مستقبلًا.

3. **سقطة تعداد في التقرير:** قائمة أنواع lint في `FINAL_ANALYTICAL_REPORT.md:251` تحذف `InlinedApi(3)` فتأت بمجموع 182 عوض 185.

---

## D. الإيجابيات الكاذبة المثبتة (false positives)

- **MissingPermission (5):** كل إعلى `manager.notify` محفوظٌ بفحص فعلي للإذن عبر `canPost()` (AtherNotificationCenter:50/77/98/119/135 + :147-154).
- **NewApi في NotificationChannels (4):** كلها مسورة بفحص `SDK_INT < O → return` مبكرًا (:29).
- **ExportedService:** `exported=true` متطلب رسمي لـMedia3 `MediaSessionService` (بلا intent-filter يسمح للنظام بطلبات الترجيحات).
- **WrongConstant:** `SessionResult(RESULT_ERROR_BAD_VALUE)` ≈ `SessionError.ERROR_BAD_VALUE` سلوكيًا (تحقق javap: كلاهما -3، ومنشئ int يغلّف السالب كـSessionError) — اختلاف أسلوبي/امتثال لقاعدة IntDef لا فرق سلوكي.

---

## E. تعذّر الجزم منها على مستوى القرص (CANNOT DETERMINE)

1. **هوية "الـ15 خطأ" بالضبط:** baseline لا تخزّن severity لكل عنصر، وملفّات النتائج لا تُعدّد العناصر الخمسة عشر — لذلك تعيينها {MissingPermission 5, NewApi 8, ExportedService 1, WrongConstant 1} **استنتاج من العدّهات** (المجموع يساوي 15 تمامًا) ومن سلوكيات lint الافتراضية، ولا يمكن إثباته قطعيًا من الأثريات وحدها. التوزيع *متّسق* مع مجموع 15، لكن "هذه الأصناف تحديدًا هي الخمسة عشر" خاضع لتفسير الافتراضيات.
2. **سلوكيات مسار runtime على جهاز حقيقي:** توصيل الصوت بمواد حقيقية، شاشة القفل بفنيّة OEM، توقيت Splash، انهيار mini-player بإيماءة، تجربة الحذف الجماعي، إحساس تمرير Home — دون جهاز فعلي لا يمكن إثباتها (التقرير أعلن هذا ضمنيًا في Section J).
3. **مسار بذرة demo/تنظيفها داخل التطبيق على boot:** الوصلات في `MainActivity:187-194` تعتمد ثوابت وقت-الترجمة؛ وحدة androidTest لا تغطي لقطة Hilt على الأجهزة.
4. **هل ترد Up-Down فوري خلال إعادة تسمية مجلدات كبرى؟** الدوال المفتوحة مؤكد منطقيًا (R5) لكن لا اختبار آلي لسيناريو "حجم معلوماتي" فهرس فريدٌ قد يرفض كتابتين متزامنتين (غير مُجرَّب).

---

## F. ترتيب الإجراءات الموصى به

1. **إصلاح الـ4 NewApi غير المحمية أولاً** (أعلى خطر فعلي):
   - `EditionSignals.kt:82/94/105`: مسار API<26 بديلًا لقراءة المجموعات المسماة (فهرس رقمي/Ants منهجي) أو رفع minSdk بشكل مقصود وموثّق.
   - `AtherNotificationCenter.kt:72`: استخدام `getActivity` أو `getService` عند `SDK_INT < O`.
2. **تصحيح تعليق proguard** (`proguard-rules.pro:2-3`) و/أو نقل فئات demo فعليًا لمجلد `src/debug`.
3. **استكمال تعداد lint** في التقرير بإضافة `InlinedApi(3)` (المجموع يجب أن يبقى 185).
4. **اختبار آلي لسناريو API<26** (Robolectric حتى API 23-25) لمجموعات regex وكيفية بناء PendingIntent لمؤقّت النوم.
5. تحسين أسلوبي: تفضيل `SessionError.ERROR_BAD_VALUE` في `PlaybackService.kt:151` تماشيًا مع IntDef (اختياري، لا سلوك يختلف)، وإضافات androidTest للبذرة/التنظيف متى توفر جهاز.

---

## G. الملفات المفحوصة

- `app/lint-baseline.xml` + `app/build/reports/lint-results-debug.{txt,html,sarif}`
- `app/src/main/AndroidManifest.xml` (29 سطرًا) + `app/src/debug/AndroidManifest.xml` + manifest مُغلَّف إصدار من `processReleaseManifestForPackage`
- `AtherNotificationCenter.kt`، `NotificationChannels.kt`، `AtherMediaNotificationProvider.kt`
- `domain/usecases/FolderClassifier.kt`، `ScanRoot.kt`، `EditionIntelligence.kt`، `EditionSignals.kt`، `ScanLibraryNow.kt`، `LibraryManagement.kt`، `DatabaseSeeder.kt`
- `data/room/entity/Entities.kt`، `data/room/dao/Daos.kt`، `data/preferences/AppSettings.kt`
- `presentation/player/PlayerScreen.kt` (2557 سطرًا)، `MiniPlayer.kt`، `playback/PlaybackService.kt`
- `presentation/settings/SettingsScreen.kt`، `SettingsViewModel.kt`
- `presentation/library/LibraryScreen.kt`، `presentation/onboarding/OnboardingScreen.kt`
- `presentation/home/HomeScreen.kt`، `HomeViewModel.kt`، `ListeningHubViewModel.kt`، `HomeMapper.kt`
- `MainActivity.kt`، `app/proguard-rules.pro`، `app/build.gradle.kts`
- `res/mipmap-anydpi-v26/ic_launcher*.xml`، `res/drawable/ic_launcher_foreground.xml`، `ic_stat_ather.xml`، `res/layout/notification_ather_{strip,panel}.xml`
- `app/src/test/**/ScanRootTest.kt` (شمل R1/R4/R5) + جميع ملفات `app/build/test-results/testDebugUnitTest/*.xml` (38)
- `app/build/outputs/apk/release/app-release.apk` + مستخرج `classes.dex` (فحص بايتي مباشر)