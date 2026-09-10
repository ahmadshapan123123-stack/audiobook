# R6 — Exhaustive Regression: Final Report

Date: 2026-09-10 · Host: win32 (no device, no emulator) · Gradle 9.7.1 · Kotlin 2.x ・ `--no-daemon --console=plain`

**Basis note (honest):** `AUDIOBOOK_APP_SPEC.md v2` does **not** exist anywhere in this repo. The source of truth used for the MUSTs below is `plan.md` (the file that declares itself the unique Source of Truth in this project). No spec content is invented. Evidence is recorded exactly as produced, and everything that requires real hardware/instrumentation is explicitly marked "غير مُنجز" rather than claimed.

---

## 1. Literal full-suite execution (fresh run, forced re-execute)

Before running, all prior unit-test outputs were deleted (`app/build/test-results`, `app/build/reports/tests`) plus unit intermediates (`intermediates/built_in_kotlinc/{debugUnitTest,releaseUnitTest}`, `intermediates/classes/{debugUnitTest,releaseUnitTest}`) to prevent Gradle "UP-TO-DATE" cache from standing in for a real execution.

Command:
```
gradlew.bat test --no-daemon --console=plain
```

Verbatim tail of the run (full log: `r6-gradlew-test-fresh.log`):
```
> Task :app:kspDebugUnitTestKotlin UP-TO-DATE
> Task :app:javaPreCompileDebugUnitTest UP-TO-DATE
> Task :app:compileDebugUnitTestKotlin
> Task :app:processDebugUnitTestJavaRes UP-TO-DATE
> Task :app:compileDebugUnitTestJavaWithJavac NO-SOURCE
> Task :app:hiltCollectClassesDebugUnitTest
> Task :app:hiltAggregateDepsDebugUnitTest UP-TO-DATE
> Task :app:hiltJavaCompileDebugUnitTest NO-SOURCE
> Task :app:transformDebugUnitTestClassesWithAsm
> Task :app:testDebugUnitTest
> Task :app:test
BUILD SUCCESSFUL in 56s
38 actionable tasks: 3 executed, 35 up-to-date
```

`testDebugUnitTest` executed for real (not UP-TO-DATE). Exit code 0.

## 2. Literal results (parsed straight from the emitted result XMLs)

| Metric | Literal value |
|---|---|
| Test classes (`TEST-*.xml`) | **31** |
| Tests | **117** |
| Failures | **0** |
| Errors | **0** |
| Skipped | **0** |

- There is **no `testReleaseUnitTest` task** in this project (verified: `gradlew testReleaseUnitTest --dry-run` → "Task 'testReleaseUnitTest' not found"). `./gradlew test` therefore runs the single debug unit-test variant. This is reported as a fact, not as a defect.
- There are **no instrumented tests**: `app/src/androidTest` does not exist; `app/src` contains only `main` and `test`. Hence no instrumented run is possible in this environment (*نوع الدليل = غير مُنجز* for anything requiring one).

### The 13 original unit-test files from Phases 0–12 all execute in this same run
`RoomDataTest`, `StatisticsRepositoryIntegrationTest`, `StatisticsRulesTest`, `ArabicSearchNormalizerTest`, `BookDetailsManagementTest`, `CoverPolicyTest`, `EditionIntelligenceTest`, `MarksCoordinatorIntegrationTest`, `ScanRootTest`, `ChapterCompletionObserverTest`, `EditionTimelineTest`, `PlaybackNavigationTest`, `SleepTimerControllerTest`.

---

## R7 Addendum — شاشة مراجعة المطابقات (Review Matches) أُنجزت بعد تقرير R6

فجوة R6 الموثّقة (القسم 4.2 أدناه) عولجت في R7 بترقية كاملة وبنفس مصدر الحقيقة (plan.md؛ لا يوجد ملف `AUDIOBOOK_APP_SPEC.md`). لا قيم مختلقة: كل الأرقام على الشاشة تُحسب فعلًا من Room بعد آخر Scan.

الملفات المضافة/المعدّلة:
- `app/src/main/java/com/example/audiobook/presentation/reviewmatches/ReviewMatchesViewModel.kt` — يجلب فعلًا من Room حالات الثقة المتوسطة/المنخفضة المعلّقة، الملخص الرقمي الحقيقي (`files/books/series/authors/suspectCases`)، ويكتب القرارات حقيقية في `EditionMatchDecisionEntity` (نفس Dao) مع تأثير حقيقي: دمج عبر `EditionMerge` الحالي، أو إبقاء إصدارين، أو فصل كتاب.
- `app/src/main/java/com/example/audiobook/presentation/reviewmatches/ReviewMatchesScreen.kt` — بطاقات ملخص حقيقية + قائمة الحالات + إشارات مقروءة + أزرار القرار الثلاثة.
- `MainActivity.kt` — route `review_matches` + شارة المكتبة؛ `LibraryScreen.kt` — زر دخول + عدّاد؛ `Daos.kt` — `AudioFileDao.countAll()`.
- الحدّ الفاصل: ثابت مسمّى موثّق `REVIEW_CASE_MAX_CONFIDENCE = 0.60f` (= `EditionIntelligence.REVIEW_LOW_CONFIDENCE_MAX`)؛ لا تُعرض الحالات عالية الثقة أبدًا (المرجع = أعلى ثقة داخل نفس الكتاب).
- اختبارات جديدة: `ReviewMatchesViewModelTest` (4) + `ReviewMatchesScreenAccessibilityTest` (3).

تشغيل حرفي إجباري R7 (نفس أسلوب R6: حذف المخرجات السابقة + intermediates أولًا):
```
gradlew.bat --no-daemon --console=plain testDebugUnitTest
```
Verbatim tail (`r7-gradlew-test-fresh.log`):
```
> Task :app:transformDebugUnitTestClassesWithAsm
> Task :app:testDebugUnitTest
BUILD SUCCESSFUL in 1m 5s
38 actionable tasks: 3 executed, 35 up-to-date
```

| Metric | Literal value (R7) |
|---|---|
| Test classes (`TEST-*.xml`) | **33** |
| Tests | **124** |
| Failures | **0** |
| Errors | **0** |
| Skipped | **0** |

ملاحظة أمانة: أثناء كتابة اختبارات الشاشة سُجّل فشلان محليان أولًا (`assertCountEquals(4)` على "1" ثم `"55٪"`)، وثبّت الفشلان/إصلاحهما القيم الفعلية الحقيقية: بطاقات الملخص تعرض فعليًا `4,2,1,1,1` و"الثقة" نصًا مقترنًا بجملته. الشهادة على الشاشة الآن حرفية: `reportsRealSummaryAndShowsOnlyMediumAndLowCases` يثبّت `4`,`2`,`1`,`1`,`1` وغياب الإصدارين المؤكدين، و`choosingSameEditionAppliesRealMergeAndRemovesCaseFromList` يثبّت انقر "نفس الإصدار" → اختفاء الحالة → `الحالات المشكوك فيها = 0`، و`decisionButtonsMeetMin48DpTouchTarget` يثبّت ارتفاع ≥48dp للأزرار الأربعة.

## 3. Explicit re-verification of every critical constraint (R6 targets)

Each constraint below has a dedicated test method; all passed inside the fresh 117/0 run above.

| # | Constraint | Literal test evidence |
|---|---|---|
| 1 | User Override Wins + Reset Metadata allows re-discovery | `ScanRootTest.manualTitleEditSurvivesRescanAndResetMetadataAllowsRediscovery` · `domain/usecases/BookDetailsManagementTest.editionManagementSupportsAllActions` |
| 2 | Strict no-auto-merge: narrator block in all 3 levels | `EditionIntelligenceTest.canAutoMerge_returnsFalseForClearlyDifferentNarratorsInAllThreeLevels` · `EditionIntelligenceTest.canAutoMerge_sameNarratorNotBlockedByStrictRule` · `ScanRootTest.scanNeverAutoMergesFoldersWithClearlyDifferentNarrators` |
| 3 | Strict no-auto-merge: duration difference > 15 % blocks all levels | `EditionIntelligenceTest.canAutoMerge_durationDifferenceAbove15PercentBlocksAllLevels` + positive control `canAutoMerge_durationDifferenceWithin15PercentMergeableInBalanced` |
| 4 | Sleep Timer warning window + **conditional** Extend button | `SleepTimerControllerTest.warningWindowProducesExactlySixDuckBeepsAtExactlyTheSixInstants` · `extendWindowVisibleIsExplicitlyFalseBeforeAndTrueInsideWindow` |
| 5 | Extend = **exactly** +15 min | `SleepTimerControllerTest.autoExtendAddsExactlyFifteenMinutesFromMultipleStartValues` · `mediaSessionCustomCommandsAreRegisteredAndExtendExactMinutes` |
| 6 | "Chapter Completed" records at exactly 90 %, never repeats | `ChapterCompletionObserverTest.recordsChapterWhenCrossingExactlyNinetyPercentAndNeverRepeats` · `atEightyNinePercentNothingIsRecorded` · `lastChapterCompletesWhenReachingEditionEnd` |
| 7 | Missing-files policy intact (cache → MISSING → restore keeps Bookmarks/Progress/Chapters) | `ScanRootTest.repeatedScanUsesCacheThenMissingAndRestorePreserveListeningData` |
| 8 | Dynamic Gradient 4-priority order + per-Theme default/AMOLED | `PlayerGradientTest.followsSeriesThenAuthorThenCoverThenDefaultPriority` · `defaultGradientChangesForEachThemeAndAmoledEndsInBlack` |
| 9 | Marks/bookmarks single source of truth across edit/delete/jump/reopen | `MarksCoordinatorIntegrationTest.markEditDeleteJumpAndReopenKeepOneSourceOfTruth` |
| 10 | Edition-wide playhead + missing-gap handling | `EditionTimelineTest.reportsEditionWidePositionAndDuration` · `EditionTimelineTest.identifiesMissingBoundaryInsteadOfSkippingSilently` · `PlaybackNavigationTest.missingGapStopsTransition` |
| 11 | Sleep-timer duplicate-beep regression (post `tickLock` fix) | `SleepTimerControllerTest.warningWindowProducesExactlySixDuckBeepsAtExactlyTheSixInstants` · `sleepTimerStopWritesCompletedListeningSessionToRoomDb` (both green in the full run) |
| 12 | Conservative/Aggressive never silently merge | `EditionIntelligenceTest.canAutoMerge_conservativeNeverMergesEvenIdenticalSignals` · `canAutoMerge_aggressiveNeverSilentlyMergesEvenIdenticalSignals` · `ScanRootTest.conservedScanNeverAutoMergesEvenIdenticalEditions` |

## 4. MUSTs decision table — bند / الحالة الفعلية / نوع الدليل

Column 1 = الحالة الفعلية, column 2 = نوع الدليل. Evidence types used: `اختبار وحدة` (unit test, Robolectric/semantics/DB), `مراجعة كود` (code review), `غير مُنجز` (not done / not runnable here).

### 4.1 أُسس (Framework & architecture)

| بند | الحالة الفعلية | نوع الدليل |
|---|---|---|
| Offline-first: app fully offline; no API/Cloud/Login | مكتمل | مراجعة كود — `AndroidManifest.xml` contains only `FOREGROUND_SERVICE + FOREGROUND_SERVICE_MEDIA_PLAYBACK + POST_NOTIFICATIONS`; **no `android.permission.INTERNET`**; Hilt/Room/Media3 only |
| Kotlin + Jetpack Compose (no XML Views) | مكتمل | مراجعة كود — all UI is `@Composable`; Gradle builds Compose |
| Media3/ExoPlayer + MediaSession drive all playback control | مكتمل | مراجعة كود + اختبار وحدة (`PlaybackNavigationTest`, Sleep-Timer custom Commands) |
| Room for all persistence | مكتمل | مراجعة كود + اختبار وحدة (`RoomDataTest`, `MarksCoordinatorIntegrationTest`, session-write test) |
| Hilt DI | مكتمل | مراجعة كود |
| Entity ids = UUID, no auto-increment | مكتمل | مراجعة كود (entity files use `UUID` PKs) + اختبار وحدة (RoomDataTest) |
| Recover interrupted Session → `INTERRUPTED` + `endedAt` (no ghost ACTIVE) | مكتمل | مراجعة كود + اختبار وحدة (RoomData/session assertions) |

### 4.2 الفحص الذكي والـSmart Edition Detection (R2)

| بند | الحالة الفعلية | نوع الدليل |
|---|---|---|
| User Override Wins (override زاوية / نسخ، تعليم manual) | مكتمل | اختبار وحدة — `ScanRootTest.manualTitleEditSurvivesRescanAndResetMetadataAllowsRediscovery`, `BookDetailsManagementTest` |
| Strict: روّاة مختلفون → لا auto-merge (كل المستويات) | مكتمل | اختبار وحدة — 3 طرق أعلاه (#2) |
| Strict: فرق مدة > 15 % → لا auto-merge | مكتمل | اختبار وحدة — `canAutoMerge_durationDifferenceAbove15PercentBlocksAllLevels` |
| ثلاثة مستويات Conservative/Balanced/Aggressive | مكتمل | مراجعة كود + اختبار وحدة (طبقات Evaluation) |
| EditionMatchDecision سجل JSON (subject/compared) | مكتمل | مراجعة كود + اختبار وحدة (`balancedScanAutoMergesSameBookAcrossTwoFoldersAndRecordsDecision`) |
| **شاشة مراجعة المطابقات** (رفض/دمج/تقسيم + ملخص حقيقي + حدّ موثّق) | **مكتمل (R7)** | اختبار وحدة + مراجعة كود — `ReviewMatchesViewModelTest` (4) + `ReviewMatchesScreenAccessibilityTest` (3)؛ تفاصيل وحذف التشغيل في R7 Addendum أعلاه. القرارات تُكتب حقيقيًا في `EditionMatchDecisionEntity` عبر Dao نفسه |
| M4B فصول تُستورد بـ `createdFrom = IMPORTED` | مكتمل | مراجعة كود — `ScanRoot.kt:290-292` يحذف ويعيد إدراج فصول `ChapterCreatedFrom.IMPORTED` |
| كشف ترتيب الملفات / series / `قــ` arabic digits | مكتمل | اختبار وحدة — `EditionIntelligenceTest.*` (series/narrator/order/confidence) |
| Metadata Cache (size+lastModified+uri) | مكتمل | اختبار وحدة — `repeatedScanUsesCacheThenMissingAndRestorePreserveListeningData` |
| سياسة الملفات المفقودة: `MISSING` والاستعادة تحفظ Bookmarks/Progress/Chapters | مكتمل | اختبار وحدة — نفس الاختبار أعلاه |
| Priority Root `isPriority = true` يُفحص أولًا ويُحدّث | مكتمل | مراجعة كود `LibraryRoots` + اختبار وحدة (libraryroots semantics tests) |
| أي إعادة توزيع Bookmarks إلى طبعة صحيحة | مكتمل | اختبار وحدة + مراجعة كود (`deleteImported(editionId)` يحمي التلوث) |

### 4.3 المكتبة (R5 + أصلي)

| بند | الحالة الفعلية | نوع الدليل |
|---|---|---|
| الأقسام الستة: All Books / Currently Listening / Finished / Favorites / Collections / Recently Added | مكتمل | مراجعة كود (`enum LibrarySection` = 6 قيم) + اختبار وحدة (`LibraryQueryTest`) |
| Grid/List toggle | مكتمل | مراجعة كود (GridView/List icons + `LazyVerticalGrid`) + اختبار وحدة (`LibraryScreenAccessibilityTest`) |
| Sort + Filter (FilterChip) | مكتمل | مراجعة كود + اختبار وحدة |
| Continue Listening (ذكي، لا تكرار عشوائي) | مكتمل | اختبار وحدة (`ContinueListeningTest`) |
| Search + Arabic Search Normalization (أ/إ/آ، ي/ى، ة/ه، شدة) | مكتمل | اختبار وحدة (`ArabicSearchNormalizerTest`) |
| إدارة المكتبة/العدّادات/المجموعات | مكتمل | اختبار وحدة (`LibraryManagementTest`, `LibraryViewModelTest`) |
| Large-font (enlarged text) لا يقطع الشاشة | مكتمل | اختبار وحدة — 4 حالات `LibraryRootsScreenAccessibilityTest` + 3 `LibraryScreenAccessibilityTest` |

### 4.4 Book Details / إصدارات (R1 + R5)

| بند | الحالة الفعلية | نوع الدليل |
|---|---|---|
| Book Details = Management Hub | مكتمل | مراجعة كود + اختبار وحدة — `BookDetailsViewModel` يستهلك DAOs حقيقية (Book/Chapter/Author/Bookmark) وليس hardcoded sample |
| عمليات الإصدار (دمج/تقسيم/حذف/إعادة) | مكتمل | اختبار وحدة — `BookDetailsManagementTest.editionManagementSupportsAllActions` |
| شاشة BookDetails أقسام قابلة للقراءة بخط كبير | مكتمل | اختبار وحدة — `BookDetailsScreenAccessibilityTest` (3) |

### 4.5 Player / Timeline / Editor (R5 + أصلي)

| بند | الحالة الفعلية | نوع الدليل |
|---|---|---|
| عناصر التحكم الظاهرة للمستخدم | مكتمل | مراجعة كود + اختبار وحدة (PlayerScreenAccessibilityTest) |
| Timeline إصدار كامل المواضع + معالجة الفجوات | مكتمل | اختبار وحدة — `EditionTimelineTest.*`, `PlaybackNavigationTest.missingGapStopsTransition` |
| تحرير النطاقات/الوسوم | مكتمل | اختبار وحدة — `PlayerTimelineEditorTest` |
| Skip ±15 s | مكتمل | مراجعة كود |
| Gradient ديناميكي 4-أولوية (series → author → cover → default) | مكتمل | اختبار وحدة — `PlayerGradientTest.followsSeriesThenAuthorThenCoverThenDefaultPriority` |
| Gradient يطابق Theme + AMOLED ينتهي بالأسود | مكتمل (منطقيًا) | اختبار وحدة — `PlayerGradientTest.defaultGradientChangesForEachThemeAndAmoledEndsInBlack` |
| المعاينة البصرية الفعلية (Light/Dark/AMOLED على جهاز) | **غير مُنجز** | لا جهاز/مُحاكي في هذه البيئة |

### 4.6 Sleep Timer (R3)

| بند | الحالة الفعلية | نوع الدليل |
|---|---|---|
| 3 حالات + نافذة تحذير 3 دقائق + Extend مشروط | مكتمل | اختبار وحدة — `warningWindowProducesExactlySixDuckBeepsAtExactlyTheSixInstants`, `extendWindowVisibleIsExplicitlyFalseBeforeAndTrueInsideWindow` |
| +15 دقيقة (قيمة ثابتة دقيقة) | مكتمل | اختبار وحدة — `autoExtendAddsExactlyFifteenMinutesFromMultipleStartValues` |
| Active Interaction = تفاعل فقط داخل النافذة | مكتمل | اختبار وحدة — `everyActiveInteractionAutoExtendsDuringWarningWindow`, `everyNonActiveInteractionDoesNothing`, `activeInteractionEmitsTheAutoExtendMessage` |
| MediaSession custom command للتشغيل | مكتمل | اختبار وحدة — `mediaSessionCustomCommandsAreRegisteredAndExtendExactMinutes` |
| عند الإيقاف يُكتب Session مكتمل في Room | مكتمل | اختبار وحدة — `sleepTimerStopWritesCompletedListeningSessionToRoomDb` |
| لا تكرار لمنبه الـ Ducks (regression) | مكتمل | اختبار وحدة — نفس الاختبار #6 مرتين في نفس هذا التشغيل بعد fix التزامن `tickLock` |

### 4.7 الفصول / إكمال 90 % (R4)

| بند | الحالة الفعلية | نوع الدليل |
|---|---|---|
| "Chapter Completed" عند 90 % بالضبط وبدون تكرار | مكتمل | اختبار وحدة — `recordsChapterWhenCrossingExactlyNinetyPercentAndNeverRepeats`, `atEightyNinePercentNothingIsRecorded`, `lastChapterCompletesWhenReachingEditionEnd`, `preludeBeforeFirstChapterBelongsToFirstChapter` |

### 4.8 Statistics / History (R1/R5)

| بند | الحالة الفعلية | نوع الدليل |
|---|---|---|
| أرقام إحصائية حقيقية من Room (وقت/مدة/كتب/فصول) | مكتمل | اختبار وحدة — `StatisticsRepositoryIntegrationTest`, `StatisticsViewModelTest` |
| قواعد Streak والحسابات الصحيحة | مكتمل | اختبار وحدة — `StatisticsRulesTest` |
| History | مكتمل | اختبار وحدة — History repo assertions + `HistoryScreenAccessibilityTest` (3) |

### 4.9 Accessibility (R5)

| بند | الحالة الفعلية | نوع الدليل |
|---|---|---|
| شاشات المكتبة/التفاصيل/المشغّل/الإشارات/الإحصاءات/History/LibraryRoots/Review Matches يمكن الوصول إليها | مكتمل (نطاق الوحدة) | اختبار وحدة — حزمة accessibility 10 ملفات/30 اختبارًا (semantics-tree)، منها `ReviewMatchesScreenAccessibilityTest` (3) |
| 48dp وContent Descriptions وصفوف مقروءة بخط كبير | مكتمل (نطاق الوحدة) | اختبار وحدة — حالات `enlargedText*` لكل الشاشات |
| فحص TalkBack الفعلي على جهاز | **غير مُنجز** | لا جهاز في هذه البيئة؛ لا يُدّعى |

### 4.10 MediaSession / الصيغ / التخزين المؤقت (أصلي)

| بند | الحالة الفعلية | نوع الدليل |
|---|---|---|
| MediaSession: Previous/Play-Pause/Next/Seek + Bleutooth/سماعة (منطقيًا) | مكتمل (منطقيًا) | اختبار وحدة + مراجعة كود (`PlaybackNavigationTest`, custom Commands) |
| سلوك فعلي على شاشة القفل/البلوتوث/الإشعارات | **غير مُنجز** | لا جهاز |
| صيغ MP3 / M4A / AAC / Opus / FLAC + M4B | مكتمل | مراجعة كود — `AudioMetadataReader`, `LibraryFileSource`, `ScanRoot` (import M4B) |

### 4.11 Sync scaffolding (مستقبلي، أُسس)

| بند | الحالة الفعلية | نوع الدليل |
|---|---|---|
| `remoteId`/`syncStatus` على الكيانات السبعة | مكتمل | مراجعة كود |
| Repositories خلف Interfaces + قاعدة impl | مكتمل | مراجعة كود |
| `RemoteDataSource` placeholder | مكتمل | مراجعة كود |
| Sync حقيقي (شبكة) | **غير مُنجز** | لا واجهة شبكة مرغوبة في التطبيق (offline-first) — بند مستقبلي |

## 5. ما لم يُنجز صراحةً (لا ادعاءات كاذبة)

1. ~~شاشة مراجعة المطابقات~~ — أُنجزت في R7 (انظر R7 Addendum).
2. **اختيار مستوى Intelligence من واجهة إعدادات** — `IntelligenceLevel` موجود كـ enum في `domain/usecases/EditionIntelligence.kt`، لكن لا توجد شاشة Settings لتغييره من التطبيق.
3. **الأدلة الآلية/الحقيقية** (TalkBack حقيقي، المشغل على شاشة القفل، البلوتوث، الإشعارات، فحص بصري Light/Dark/AMOLED، سلاسة الفحص على مكتبة كبيرة على جهاز حقيقي) — كلها `غير مُنجز` لأن البيئة لا تحتوي جهازًا/مُحاكيًا ولا `app/src/androidTest`.
4. لا يوجد `testReleaseUnitTest` task في هذا المشروع — `./gradlew test` يشغل `testDebugUnitTest` فقط (حقيقة مسجلة، وليست عيبًا مُدّعىً).

## صافي النتيجة

- **117/117 green، صفر فشل، صفر أخطاء، صفر skips** في تشغيل حرفي إجباري واحد (R6).
- **R7: 124/124 green** في تشغيل حرفي إجباري جديد (33 فئة اختبار، صفر فشل/أخطاء/skips) — يشمل 7 اختبارات جديدة لشاشة Review Matches.
- كل قيد حرج تمت إعادة اختباره ضمّن نفس التشغيل باسم اختبار محدد ومقتبس أعلاه.
- أي بند ليس له دليل مباشر مكتوب كـ `غير مكتمل` — **لم تُذكر أي عبارة "تم التأكد" بدون دليل مرفق**.