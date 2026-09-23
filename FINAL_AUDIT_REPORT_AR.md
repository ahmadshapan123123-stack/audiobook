# تقرير التدقيق النهائي — أثير (Ather)

> **التاريخ:** 2026-09-20
> **النطاق:** جولة إصلاح جراحية على بنود تدقيق الست نقاط — لا إعادة تصميم لواجهة المشغّل أو التدرّجات أو هوية الإشعارات أو منظومة الفصول.
> **بيئة التشغيل:** Windows + `./gradlew.bat` — لم تُعد قنوات REST، كل الشهادات من تشغيل فعلي محلي.

---

## أ. الملخص التنفيذي

كل بنود تدقيق الست نُفِّذت ولها إثبات تشغيل فعلي:

| البند | الحكم | الدليل |
|---|---|---|
| 1. اختبار مؤقّت النوم المتقلقل | مُصلح | `SleepTimerControllerTest` 30/30 تشغيلًا متتاليًا |
| 2. `ProgressResolver` الميت | حُذف | `git` يظهر حذف الملف؛ `rg` بلا مراجع |
| 3. منطق/واجهة "تراجع" ليست مربوطة | مُربوط بالكامل | 4 اختبارات تراجع تركّب المؤلف وسلسلة ومجموعة وتستعيد بأعمدة كاملة |
| 4. فحص إعدادات التذكيرات في الإشعارات | مُصلح | 3 اختبارات (ReminderSettingsGatingTest) |
| 5. كود/وثائق ميتة | أُزيل/حُدّث | حذف 5 أعضاء + مستندات بمحرّك 194/11 |
| 6. باسلاين اللينت (11 خطأ) | مراجَع بدون تغيير | `lint: Lint found no new issues` |

**النتيجة الكلية:** تشغيل نظيف بعد `clean` → `assembleDebug` + `assembleRelease` + `testDebugUnitTest` (43 suites / **194 اختبارًا / 0 فشل**) + `lint` (صفر جديد) + APK الإصدار R8 ‏(4.9MB، بلا أي فئات تجريبية).

---

## ب. نقاط التدقيق الست والقرارات

1. **مؤقّت النوم**: الاستبدال المعلّق كان `Thread.sleep(5)` في `awaitSessionCount` بانتظار مصفوفة `listening_sessions` — تحوّل إلى انتظار Flow عبر استعلام جديد `observeByParent` في `ListeningSessionDao` مع `withTimeout(5_000L)` و`first { size == expected }`. **القرار:** إزالة التلامس الزمني نهائيًا.
2. **`ProgressResolver`**: جيد القرار الاختيار 1 (حذف). المراجع: صفر استدعاءات؛ المنطق الصحيح موجود أصلًا في `LibraryViewModel.kt:211-220` (الافتراضي ثم IN_PROGRESS ثم الأول). **القرار: حذف**.
3. **التراجع**: كان `deleteAuthor/deleteSeries/deleteCollection/mergeAuthors` تحذف مباشرة بلا لقطة ولا شريط "تراجع". **القرار:** لقطة كاملة قبل الإتلاف، وسناكبار 5 ثوانٍ داخل شاشة التفاصيل نفسها (لأن ViewModel النوتالتالي يُطهر عند الخروج فلا يصح تأجيل التراجع بعد المغادرة). اكتُشف وأُصلح خطأ حقيقي: `undoMergeAuthors` كان يحدّث الكتب إلى `authorId` المصدر قبل إعادة إدراج المؤلف → انتهاك FK.
4. **التذكيرات**: `showDailyReminder`/`showResumeReminder` كانا يمرران `true` لـ `canPost` فيتخطيان مفتاح النوع؛ **القرار:** تمرير `appSettings.dailyReminderEnabled.value` / `resumeReminderEnabled.value`. تحقّق أن العاملين (`DailyReminderWorker`/`ResumeReminderWorker`) يفحصان الإعداد أصلًا → دفاع على طبقتين.
5. **الكود الميت**: حذف `snapshotRoot`, `deleteRootCascade`, `RootSnapshot`, `deleteRoot` (أيضًا ميت)، `updateBookGenre`، وعضو `libraryRootDao` غير المستخدم. **بقِيَ** `PlayerTimeline.adaptiveAutoSplit` بملاحظة `TODO` (قرار إبقاء موثّق).
6. **باسلاين اللينت**: الـ 11 خطأ في `app/lint-baseline.xml` كلها حالات مضمونة: `MissingPermission`×5 خلف `canPost()`، `NewApi`×4 خلف `Build.VERSION.SDK_INT < O`) return`، إضافة إلى `OldTargetApi` و`AndroidGradlePluginVersion` (بنية/إصدارات لا تُرفع في هذه الجولة). إعادة توليد الباسلاين ستعيد نفس الإدخالات. **القرار:** إبقاء بلا تغيير.

---

## ج. ما تغيّر في الكود

| الملف | التغيير |
|---|---|
| `data/room/dao/Daos.kt` | `+ListeningSessionDao.observeByParent` (Flow) و`+ChapterCompletionDao.getByEdition` |
| `domain/usecases/LibraryManagement.kt` | `AuthorSnapshot` يعتمد `bookSnapshots: List<BookSnapshot>` كاملة؛ `restoreAuthor` يعيدها عبر `restoreBooks`؛ `EditionSnapshot` يحمل `chapterCompletions`؛ `restoreBooks` يعيدها؛ إصلاح ترتيب `undoMergeAuthors`؛ حذف الأعضاء الخمسة الميتة والعضو `libraryRootDao` |
| `domain/usecases/ProgressResolver.kt` | **حذف** |
| `notifications/AtherNotificationCenter.kt` | مفتاحا النوع في `showDailyReminder`/`showResumeReminder` |
| `presentation/entitydetails/{Author,Series,Collection}DetailsViewModel.kt` | `pendingUndo` المختوم، `messages: StateFlow<OpMessage?>` + `consumeMessage()`، `undo()`، إسقاط معامل `onDone` من الحذف/الدمج |
| `presentation/entitydetails/EntityUndo.kt` | **جديد**: `EntityUndoEffect` + `ENTITY_UNDO_WINDOW_MS = 5_000L` |
| `presentation/entitydetails/*DetailsScreen.kt` | `Box` + `SnackbarHost` + `EntityUndoEffect` (عند المهلة → `onBack`) |
| `res/values/strings.xml` | 5 نصوص: `author_deleted_undo`، `author_merged_undo`، `series_deleted_undo`، `series_merged_undo`، `collection_deleted_undo` |
| `test/.../SleepTimerControllerTest.kt` | انتظار Flow بدل `Thread.sleep(5)` |
| `test/.../EntityDetailsUndoTest.kt` | **جديد**: 4 اختبارات (حذف مؤلف/دمج مؤلفين/حذف سلسلة/حذف مجموعة) بأعمدة كاملة (نسخة، ملف صوتي، فصل، اكتمال فصل، علامة، تقدم، مجموعة، مفضّل) |
| `test/.../ReminderSettingsGatingTest.kt` | **جديد**: 3 اختبارات (الاحترام لكل مفتاح + المفتاح الرئيسي) |
| `test/.../LibraryManagementTestFactory.kt` | إسقاط `libraryRootDao` من المنشئ |

---

## د. الشهادات الآلية (الاختبارات)

`./gradlew.bat :app:testDebugUnitTest` (بعد `clean`):

- **43 suite** ، **194 اختبارًا** ، 0 فشل ، 0 خطأ ، 0 مُتخطّى (تُجمع من XMLs).
- الجديد: `EntityDetailsUndoTest` (4) + `ReminderSettingsGatingTest` (3).

مشكلة اختبار وُجدت وحُلّت أثناءها: فشلان من التراجع كانا **سباق زمن حقيقي** بين منفّذات Room وخيط الموقّت الافتراضي (الانبعاث الأول لسطر الأب يسبق إعادة إدراج الصفوف الفرعية). الحل: الانتظار على `viewModel.messages` (يُصفَّر فقط **بعد اكتمال** نُسخ الاستعادة) بدل التوقّف عند انبعاث مبكر.

---

## هـ. اللينت والباسلاين

- `:app:lint` → **"Lint found no new issues"**؛ الباسلاين يقسّم **11 errors / 167–168 warnings / 2 hints**.
- ملاحظة: تناقص warning بواحد بين تشغيلين عائد لتغيّر قراريات تبعية عابرة (GradleDependency/Toml)؛ لا يُؤثّر في الحكم (0 أخطاء جديدة).
- تحليل الـ 11 خطأ أعلاه في البند ب.6.

---

## و. البناء

| الناتج | الحجم | الشهادة |
|---|---|---|
| `app-debug.apk` | 27,405,377 بايت | `assembleDebug` بعد `clean` |
| `app-release.apk` | 4,939,923 بايت (~4.9MB) | `assembleRelease` بعد `clean` (R8 + `shrinkResources`) |

`aapt dump badging`: package `com.example.audiobook`، minSdk **23**، targetSdk **36**، label **أثير**، launcher `MainActivity`.
`dexdump`: **0** ظهور لعبارة `Seeder`/`DatabaseSeeder`/`DemoAudioProvider` → الفئات التجريبية مقتطعة من الإصدار.

---

## ز. اختبار الـ 30 تشغيلًا

`SleepTimerControllerTest` ‏30 تشغيلًا متتاليًا بعد `clean` → **PASS_COUNT=30/30 FAIL=0**.

---

## ح. قرارات التصميم التي لم تتغير

- واجهة المشغّل/الألوان/الشكل الزجاجي، هوية القنوات والإشعارات، منظومة الفصول والتوافق: **لم تُمَس** — الجولة جراحية على بنود التدقيق فقط.
- إبقاء `PlayerTimeline.adaptiveAutoSplit` بملاحظة `TODO` (غير مستخدمة حاليًا، مقصودة).
- `targetSdk 36` لا تُرفع إلى 37 (قرار بنية/متجر خارج هذه الجولة).

---

## ط. المخاطر المتبقية / NOT TESTED

- لا يوجد **androidTest** (لم تُضف بُنية `androidTest`)؛ كل الشهادات من Unit/Robolectric.
- الـ `WrongConstant` في `PlaybackService.kt:151` إيجابية كاذبة معروفة في Media3 (`RESULT_ERROR_BAD_VALUE` قيمة صالحة) → أُبقيت في الباسلاين.
- التغيّر المؤقت لمعاملات التحقق (scheduleBackgroundScans إلخ.) خارج نطاق هذا التقرير.

---

## ي. قائمة الملفات المتأثرة

**مُعدَّل (main):**
`Daos.kt` · `LibraryManagement.kt` · `AtherNotificationCenter.kt` · `AuthorDetailsViewModel.kt` · `SeriesDetailsViewModel.kt` · `CollectionDetailsViewModel.kt` · `AuthorDetailsScreen.kt` · `SeriesDetailsScreen.kt` · `CollectionDetailsScreen.kt` · `strings.xml`

**جديد (main):**
`EntityUndo.kt`

**مُحذف (main):**
`ProgressResolver.kt`

**اختبارات:**
`SleepTimerControllerTest.kt` (مُعدَّل) · `EntityDetailsUndoTest.kt` + `ReminderSettingsGatingTest.kt` (جديدان) · `LibraryManagementTestFactory.kt` (مُعدَّل)

**وثائق:**
`FINAL_ANALYTICAL_REPORT.md` · `R6_FINAL_REPORT.md` · `PRODUCT.md` (مُحدَّثة إلى 194/11 مع ملاحظة تحديث)

---

## ك. كيف تتحقق بنفسك

```bash
./gradlew.bat :app:testDebugUnitTest          # 43 suites / 194 tests
./gradlew.bat :app:lint                        # لا أخطاء جديدة
./gradlew.bat :app:assembleDebug               # APK تصحيح
./gradlew.bat :app:assembleRelease             # APK إصدار R8
```

تشغيل واحد من FlakyTest:
```bash
for ($i=1; $i -le 30; $i++) { ./gradlew.bat :app:testDebugUnitTest --tests "SleepTimerControllerTest" }
```

---

## ل. بيان الصدق

- لا يوجد **تشغيل على جهاز مادي** في هذه الجولة؛ سلوك الشاشات (السناكبار، التوقيت، التفاعل البصري) مثبت آليًا فقط.
- لا تغيير في REST/API، ولا إعادة تصميم. كل الأرقام الواردة أعلاها قابلة لإعادة الإنتاج بأوامر القسم «ك» من حالة `clean`.
- إذا رُمّمت الباسلاين أعيد الصفر من الأخطاء الحقيقية؛ الـ 11 إدخالًا المتبقية مضمونة كإيجابيات كاذبة/بنية (تفصيلها في البند ب.6).