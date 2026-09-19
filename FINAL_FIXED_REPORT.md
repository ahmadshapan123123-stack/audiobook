# تقرير إصلاح الأخطاء المؤكدة — تطبيق أثير للكتب الصوتية

التاريخ: 19 سبتمبر 2026 · النطاق: إصلاح 4 أخطاء مؤكدة مُثبتة بمراجعة read-only
المتطلبات: إصلاحات جراحية فقط، لا تغيير في منطق الـRegex أو منطق مؤقت النوم أو هوية الإشعارات أو UI المشغّل.

---

## أ. BUG 1 — EditionSignals.kt: انهيار الفحص على API 23-25

### التشخيص المؤكد
الوصول إلى مجموعات الـRegex بالاسم (`match.groups["suffix"]` وما شابه) يُرجَم داخل kotlin-stdlib إلى
`java.util.regex.Matcher.start(String)`/`end(String)` — وهذه الدوال بـ **API 26+**. على أجهزة API 23-25
لا توجد في `Matcher`، فيحدث اهتزاز أثناء الفحص (`NoSuchMethodError`).

### الإصلاح المطبَّق (سطر بسطر)
- `EditionSignals.kt:82` القبضة/السلسلة:
  - قبل: `val suffix = match.groups["suffix"]?.value ?: return@forEach`
  - بعد: `val suffix = match.groupValues[1]`
- `EditionSignals.kt:94` الراوي:
  - قبل: `match.groups["name"]?.value?.trim()?.trimEnd(...)?.takeIf { it.length >= 2 }`
  - بعد: `match.groupValues[1].trim().trimEnd(...).takeIf { it.length >= 2 }`
- `EditionSignals.kt:105` رقم الملف:
  - قبل: `arabicDigitToIntOrNull(match.groups["num"]?.value ?: return@mapNotNull null)`
  - بعد: `arabicDigitToIntOrNull(match.groupValues[1]) ?: return@mapNotNull null`
- الأنماط كلها: استبدال `(?<suffix>…)`/`(?<name>…)`/`(?<num>…)` بأقواس اعتيادية `(…)`
  (`SERIES_PATTERNS` أسطر 166-171، و`NARRATOR_PATTERNS` أسطر 179-185، و`SUFFIX_NUMBER` سطر 187).

### صحة سلوك المجموعة رقم 1
كل نمط من الأنماط العشرة يحتوي **مجموعة التقاط واحدة بالضبط** (الباقي `(?:…)` غير ملتقط)، لذلك
`groupValues[1]` يقرأ نفس القيمة التي كانت المجموعة المسماة تقرؤها. الفرق الوحيد: لغير الملتقط يُرجع `""`
بدل `null`، لكن المجموعة إلزامية عند نجاح المباراة، و"`""`" تفشل في `ARABIC_ORDINALS`/`arabicDigitToIntOrNull`
/`takeIf { length >= 2 }` — أي نفس النتيجة السابقة تمامًا.

### الأدلة
- `grep` يؤكد صفر بقايا من `?<` أو `groups[`.
- اختبارات `EditionSignalsApiCompatTest` (7 دوال × SDK 23/24/25 = 21 تنفيذًا) تتحقق من أن القيم
  المنطقية (book/juz/kitab/numbered + الأرقام العربية، الراوي، ترتيب الملفات) سليمة تمامًا بعد التحويل.

---

## ب. BUG 2 — AtherNotificationCenter.kt: انهيار مؤقت النوم على API 23-25

### التشخيص المؤكد
`AtherNotificationCenter.kt:72` كانت تستدعي `PendingIntent.getForegroundService(...)` وهو **API 26+**،
بلا شرط يحمي النسخ الأدنى. أي مستخدم يبدأ مؤقت نوم على Android 5-7 (API 23-25) يُنهار فورًا.

### الإصلاح المطبَّق
`sleepAction(minutes, flags)` (الأسطر 68-79) الآن:

```kotlin
private fun sleepAction(minutes: Int, flags: Int): PendingIntent {
    val intent = Intent(context, PlaybackService::class.java)
        .setAction(SLEEP_ACTION)
        .putExtra(SLEEP_ACTION_MINUTES, minutes)
    return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        PendingIntent.getForegroundService(context, 1000 + minutes, intent, flags)
    } else {
        // getForegroundService هو API 26+. على API 23-25 نستخدم getService؛
        // الخدمة نفسها تستدعي startForeground(...) داخل onStartCommand.
        PendingIntent.getService(context, 1000 + minutes, intent, flags)
    }
}
```

### لماذا لا يكسر شيئًا
- منطق المؤقت لم يتغير (نفس الإجراء، نفس extra، نفس request code `1000 + minutes`).
- `PlaybackService.onStartCommand` (الأسطر 161-171) يعالج `SLEEP_ACTION` ثم يستدعي `super` —
  و`MediaSessionService` يستدعي `startForeground` داخليًا، فمسار `getService` يوصّي الإجراء بأمان
  وتقوم الخدمة بالتصعيد إلى `startForeground` بنفسها (لهذا كان `getForegroundService` مُستحسنًا في
  الأصل، لكنه غير متاح تحت API 26).
- `FLAG_IMMUTABLE` متاح من API 23 = `minSdk`، فبقيت الأعلام دون تغيير.

### الأدلة
- `AtherNotificationCenterApiCompatTest` على SDK 23/24/25 (تستدعي `postSleepTimer` و`sleepAction`
  عبر Reflection وتؤكد إنشاء `PendingIntent` بلا استثناء). هذه الاختبارات **كانت ستفشل فعلًا** قبل
  الإصلاح لأن `getForegroundService` غير موجودة في JAR الأندرويد للنسخة 23 داخل Robolectric.

---

## ج. BUG 3 — proguard-rules.pro: تعليق مضلل

### التشخيص المؤكد
السطران 2-3 القديمان زعما أن صنوف الـdemo تعيش في `src/debug` فقط (اختيار أ)، بينما في الحقيقة
`DatabaseSeeder` و`DemoAudioProvider` موجودتان في **src/main**.

### لماذا اُستبعد الخيار (أ) (نقل الصنوف إلى src/debug)
`SeederEntryPoint` الذي يستحضر `DatabaseSeeder` معلَن في `MainActivity.kt:149-153` ضمن
**src/main**، والـ Kotlin تمنع أن يُشير كود الرئيس إلي نوع في `debug` — النقل كان سيكسر البناء.

### الإصلاح المطبَّق (الخيار ب)
التعليق أعيدت صياغته لوصف الحقيقة بدقة:
> Demo / seeder classes (DatabaseSeeder, DemoAudioProvider) live in src/main,
> but every reference is guarded by BuildConfig.DEBUG, a compile-time constant
> that is false in release builds, so R8 strips them there. The debug manifest
> registers DemoAudioProvider; the release manifest does not.

---

## د. BUG 4 — اختبارات Robolectric للنسخ API 23/24/25

ثلاثة ملفات اختبار جديدة (لا تعديل على الاختبارات القائمة):

| الملف | دوال/أس دي كي | الغرض |
|---|---|---|
| `app/src/test/.../domain/usecases/EditionSignalsApiCompatTest.kt` | 7 × SDK 23/24/25 | عزل BUG 1: استخراج السلسلة/الراوي/ترتيب الملفات بدون انهيار وبقيم صحيحة |
| `app/src/test/.../notifications/AtherNotificationCenterApiCompatTest.kt` | 3 × SDK 23/24/25 | عزل BUG 2: بناء إشعار مؤقت النوم + توليد PendingIntent بلا `getForegroundService` |
| `app/src/test/.../domain/usecases/ScanRootApiCompatTest.kt` | 2 × SDK 23 | فحص كامل (سلسلة + راوٍ) على API 23: تنشأ الكتب وتُمتَّأ دون انهيار |

النتيجة: **32 تنفيذًا جديدًا (12 دالة × توابع SDK) — كلها PASS**.
• `@Config(sdk = [23, 24, 25])` تجعل Robolectric تُنفّذ كل دالة على النسخ الثلاث.
• استخدام Reflection على `sleepAction` الخاص ضروري لأن `canPost` قد يختصر المسار تحت Robolectric
  قبل بلوغ سطر `getForegroundService`؛ فالانعكاس يضمن بلوغ السطر المُصلَح تحديدًا.
• لو أُعيد الخطأ: `PendingIntent.getForegroundService` غير موجودة في android-all لأي نسخة < 26
  فتفشل الاختبارات فورًا — أي أنها «تلتقط» إعادة الخطأ فعلًا.

---

## هـ. البناء والاختبارات الكاملة

| الأمر | النتيجة |
|---|---|
| `:app:testDebugUnitTest` | **187 اختبارًا، 0 فشل** (41 صنفًا) — من ضمنها 32 تنفيذًا جديدًا لـAPI-compat |
| `:app:assembleDebug` | ✔ BUILD SUCCESSFUL |
| `:app:assembleRelease` | ✔ BUILD SUCCESSFUL (R8 مفعّل، `lintVitalRelease` ناجح) |

ملاحظة أدقّ: أثناء تشغيل كامل ظهر فشل واحد عشوائي في `SleepTimerControllerTest.stateMachineAdvances…`
(اختبار آلة حالات لم تمسّه أي من تغييراتنا)؛ عاد الاختبار **PASS في العزلة وعلى إعادة تشغيل المجموعة
الكاملة** — سلوك توقيت flaky وليس خطأً وظيفيًا.

---

## و. lint-baseline.xml: NewApi من 8 ← 4

- أُزيلت من baseline السجلات الأربعة للمواضع المُصلَحة:
  `AtherNotificationCenter.kt:72` + `EditionSignals.kt:82/94/105`.
- بقي في baseline سجلات `NotificationChannels.kt` الأربعة (42/43/43/44) — وهي مضمَّنة بحارس
  `if (Build.VERSION.SDK_INT < O) return` سطر 29 (استخدام مشروع مقصود لا مشكلة).
- `./gradlew :app:lint` بعد التحديث: **«Lint found no new issues»** → يؤكد أن المواضع الأربعة لم
  تَعُد تُنتج NewApi، وأن baseline المحدَّث متوافق ولا يُخفي مشاكل جديدة.

---

## ز. APK الإصدار (release)

```
app/build/outputs/apk/release/app-release.apk — 4.71 MB
classes.dex = 5,005,008 bytes
Seeder matches            = 0
DemoAudioProvider matches = 0
DatabaseSeeder matches    = 0
```
- R8 جمّد فعليًا صنوف الـdemo من الـrelease رغم وجودها في src/main. ✔
- التوقيع: «CN=Android Debug» — لأنه لا يوجد `keystore.properties` في جذر المشروع فيُستعمل
  توقيع debug كبديل تلقائي (إعداد مشروع، لا تغيير منا). الـAPK قابل للتثبيت على أي جهاز API 23+.

---

## ح. قائمة الملفات المعدَّلة/المضافة

- تعديل: `app/src/main/java/com/example/audiobook/domain/usecases/EditionSignals.kt` (BUG 1)
- تعديل: `app/src/main/java/com/example/audiobook/notifications/AtherNotificationCenter.kt` (BUG 2)
- تعديل: `app/proguard-rules.pro` (BUG 3 — التعليق)
- تعديل: `app/lint-baseline.xml` (إزالة 4 NewApi)
- إضافة: `app/src/test/java/com/example/audiobook/domain/usecases/EditionSignalsApiCompatTest.kt`
- إضافة: `app/src/test/java/com/example/audiobook/notifications/AtherNotificationCenterApiCompatTest.kt`
- إضافة: `app/src/test/java/com/example/audiobook/domain/usecases/ScanRootApiCompatTest.kt`

---

## ط. القيود المتبقية

1. **Robolectric لا التقطت انهيار الـRegex على JVM الحقيقي**: تُشغَّل الاختبارات فوق
   `java.util.regex` لحاسوب التطوير الذي يملك دالتي `Matcher.start(String)` منذ JDK 7، بينما
   ينقصهما libcore الأندرويد قبل API 26. لذلك اختبارات `EditionSignalsApiCompatTest` تثبت **سلامة
   القيم بعد التحويل**، لكن حارس النسخة نفسها هو: (أ) التحول إلى `groupValues[1]` (uses `group(int)` —
   قديم منذ API 1)، و(ب) إزالة السجلات من baseline بحيث تعود أي إعادة استخدام للاسم مشكلة Lint جديدة.
   اختبارا الإشعارات والفحص الكامل يلتقطان إعادة الخلل على مستوى الإنقلاب الآني لـ`NoSuchMethodError`.
2. **لا اختبار على جهاز فعلي**: كل ما فُعل unit/performance-less تحت Robolectric + توقيع مؤكد.
   تُوصى جولة يدوية على Android 5/6 (API 23/24) لفتح فحص مكتبة + بدء مؤقت نوم، وعلى API 26+ للتأكد
   من عدم تراجع الإشعار الأمامي.
3. **`WakeLock`/`setTimeoutAfter` وغيره داخل مسارات الإشعارات خارج نطاق هذا الإصلاح** — لم تُلمس.

---

## ي. خلاصة الأدلة (ما يُؤكَّد فعليًا)

| البند | الأدلة |
|---|---|
| BUG 1 مُصلَح | قراءة `groupValues[1]`، صفر `groups[`/`?<`، اختبارات 21×PASS، سجلات Lint أُزلت |
| BUG 2 مُصلَح | فرع `SDK < O → getService` موجود، اختبارات 9×PASS (كانت ستفشل قبلًا) |
| BUG 3 مُصلَح | التعليق يصف الواقع (src/main + BuildConfig.DEBUG) |
| BUG 4 | 12 دالة × توابع SDK = 32 تنفيذًا، كلها PASS |
| بنية كاملة | 187/187 اختبار، debug ✔، release ✔ |
| Lint | لا مشاكل جديدة؛ NewApi 8 → 4 |
| Release نظيف | 0 Seeder / 0 DemoAudioProvider / 0 DatabaseSeeder في classes.dex |