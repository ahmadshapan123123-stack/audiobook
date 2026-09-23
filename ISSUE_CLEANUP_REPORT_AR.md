# تقرير إصلاحات المكتبة — البيانات التجريبية والكتب المفقودة

**التاريخ:** 2026-09-21
**الفرع/الإصدار:** قاعدة البيانات v5 (ترقية إضافية 4→5)
**الجهاز:** محاكي emulator-5554 (AVD pixel7_api36) + وحدة فحص `:app:testDebugUnitTest`

نطاق العمل: إصلاح جراحي لأربع مسائل محدّدة دون أي تغيير في تجربة التشغيل/المشغّل/نظام الإشعارات/الفصول/نظام الألوان، ودون أي ترحيل هدّام للبيانات. كل فقرة أدناه مدعومة بدليل من قاعدة البيانات أو سجلات النظام أو لقطات الواجهة المحفوظة في `evidence/`.

---

## أ) إزالة بيانات التجربة تُنظّف المؤلفين/السلاسل/المجموعات اليتيمة

القاعدة السابقة `clearDemoData()` كانت تحذف الكتب التجريبية فقط، فتظل المؤلفون/السلاسل/المجموعات التجريبية عالقة. أُعيدت كتابة الدالة لتتبع الترتيب التالي (يضمن عدم مساس أي بيانات حقيقية):

1. `getDemoBooks()` ثم `deleteBookCascade` (حذف كتب التجربة كأحد الطبقات المرتبطة).
2. `getDemoRoots()`: حذف جذور التجربة لكن **مع حماية** — يُحذف الجذر فقط إن لم تكن له أي إصدارات (editions)، فلا يتيتّم أي كتاب حقيقي (انظر «ب»).
3. حذف اليتامى حسب قاعدة النسبية داخل نطاق التجربة:
   - `AuthorDao.getDemoOrphans()`: `isDemo = 1 AND id NOT IN (SELECT authorId FROM books)`.
   - `SeriesDao.getDemoOrphans()`: `isDemo = 1 AND id NOT IN (SELECT seriesId FROM books WHERE seriesId IS NOT NULL)`.
   - `CollectionDao.getDemoOrphans()`: `isDemo = 1 AND id NOT IN (SELECT collectionId FROM collection_book_cross_ref)`.
4. إعادة تعيين علامة التجربة لأي كيان تجريبي بات فيه محتوى حقيقي:
   - `clearDemoFlagForAuthorsWithBooks()` → `isDemo = 0` للمؤلفين الذين لهم كتب فعلًا.
   - `clearDemoFlagForSeriesWithBooks()` و `clearDemoFlagForCollectionsWithMembers()` بالمثل.

**المراجع:** `LibraryManagement.kt:508` (clearDemoData)، `Daos.kt:41-42,53-54,156-157`، `AppDatabase.kt:59-80` (ترقية 4→5 التي تضيف العمود `isDemo` وتعبّئه للجذور القائمة قبل الترقية).

**دليل على الجهاز:** أعداد ما قبل التنظيف (من `evidence/issue4-precounts.txt`) كانت: كتب 5، مؤلفون 5، سلاسل 2، جذور 3، مجموعات 2. بعد الضغط على «إزالة بيانات التجربة» ثم «حذف» (دليل `issue4-removedialog.xml`) أصبحت (من `evidence/issue4-postcleanup.txt`):

| الكيان | قبل | بعد | النتيجة |
|---|---|---|---|
| الكتب | 5 | 5 | محفوظة كلها |
| المؤلفون | 5 | 2 | حُذف اليتامى (نجيب محفوظ، يوسف زيدان، غادة السمان) |
| المؤلفون | — | 2 | «أحمد خالد توفيق» (له كتب حقيقية) + «نبيل فاروق» **بقيَا** وفُكّت علامة التجربة عنهما (`isDemo=0`) |
| السلاسل | 2 | 0 | ما وراء الطبيعة، الثلاثية حُذفتا |
| المجموعات | 2 | 0 | للاستماع الليلي، قائمة الانتظار حُذفتا |

---

## ب) إزالة جذور التجربة بعد التنظيف (لا بعث بعد إعادة الفحص)

- أصبحت الجذور التجريبية موسومة بـ `isDemo = 1` في الملقّح (`DatabaseSeeder.kt:45,50`) وفي ترقية 4→5 (`AppDatabase.kt:75`) عبر UUIDs حتمية من الوسوم `root.demo` / `root.mobile`.
- `clearDemoData()` تحذفها، لكن فقط إن لم تكن مرجعًا لأي إصدار (حماية من تعطيل مكتبة حقيقية).
- **دليل:** `library_roots` بعد التنظيف = جذر واحد فقط (`Audiobooks`) من أصل 3 (نص `evidence/issue4-postcleanup.txt`). إغلاقٌ وإعادة تشغيل التطبيق بعد التنظيف أجرى `scan-complete ... created=0 restored=0 missing=0` دون إعادة إنشاء أي من جذري التجربة، والدالة `has_seeded_demo` في `shared_prefs/app_settings.xml` عادت إلى `false` تلقائيًا. لا يوجد `fallbackToDestructiveMigration` في المشروع.

---

## ج) إخفاء الكتب «المفقودة كليًا» من بطاقات المكتبة

**القاعدة:** يُخفى الكتاب إذا وُجد له **مقابل صوتي واحد على الأقل عبر إصداراته** وَكان **لا شيء** منها `AVAILABLE`. الكتب التي لا صفوف صوتية لها تبقى ظاهرة (لحماية سلوك الاختبارات الوحداتية القائمة).

- التنفيذ: `LibraryViewModel.kt:140` (isFullyMissing) و `mapNotNull` في `:145-147` — حذفٌ منطقي فقط، لا مساس بالمحتوى أو بجداول الصوت.

**دليل على الجهاز (لقطات `evidence/issue3-library-4books.png`، `issue4-library-post-rename.xml`، `issue4-library-partial-scrolled.xml`):**

| الحالة | الكتاب | الملفات | في الواجهة؟ |
|---|---|---|---|
| مفقود كليًا | فانتازيا (القديمة) | 2×MISSING | مخفي ✅ |
| مفقود كليًا | سافاري-MANUAL (بعد إعادة التسمية) | 1×MISSING | مخفي ✅ |
| مفقود جزئيًا | ما وراء الطبيعة | 1 AVAILABLE + 1 MISSING | ظاهر ✅ |
| سليم | سافاري الجديد / فانتازيا 2 / ملف المستقبل | AVAILABLE | ظاهرة ✅ |

عنوان المكتبة يعرض «4 كتابًا» بينما قاعدة البيانات تحتوي 6 كتب (الحذف منطقي بالكامل — أي 6 صفوف في `books`، 4 بطاقات في الواجهة).

---

## د) توثيق أن إعادة تسمية المجلد تُنشئ كتابًا جديدًا

أُضيف تعليق توثيقي في `ScanRoot.kt:263` (createEdition) يشرح أن هوية الكتاب هي `libraryRootId + sourceFolderPath`، لذلك أي إعادة تسمية لمجلد = كتاب/إصدار جديد، بينما تبقى صفوف الكتاب القديم وتُعلَّم `MISSING` عند الفحص (الفحص يعلّم ولا يحذف أبدًا).

**دليل سلوك على الجهاز:** بعدها `mv /sdcard/Audiobooks/أحمد خالد توفيق/سافاري → سافاري الجديد` ثم «فحص المكتبة الآن»: `scan-complete ... created=1 missing=1` (من logcat)؛ أصحبت القاعدة: `سافاري الجديد|01.mp3|AVAILABLE` و `سافاري-MANUAL|01.mp3|MISSING` (المخفي في الواجهة).

---

## هـ) الاختبارات التراجعية (وضع صارم على المحاكي)

| # | الاختبار | النتيجة | الدليل |
|---|---|---|---|
| 1 | فتح سلس للتطبيق بعد ترقية v5 | PASS | `PRAGMA user_version = 5` + لا FATAL/ANR في logcat |
| 2 | الترقية تحافظ على بيانات المستخدم | PASS | 5 كتب + تقدم «سافاري-MANUAL 20053 FINISHED» + تجاوز الاسم محفوظ (v4→v5) |
| 3 | لا ترحيل هدّام (`fallbackToDestructiveMigration`) | PASS | فحص سكوني |
| 4 | إخفاء كتاب مفقود كليًا | PASS | فانتازيا و سافاري-MANUAL مخفيان (لقطات section ج) |
| 5 | بقاء الكتاب المخفي في قاعدة البيانات (حذف منطقي) | PASS | 6 صفوف كتب/4 بطاقات |
| 6 | إبقاء الكتاب المفقود جزئيًا ظاهرًا | PASS | ما وراء الطبيعة مع 1 MISSING ظاهرًا |
| 7 | تنظيف الكيانات اليتيمة للتجربة | PASS | counts قبل/بعد (section أ) |
| 8 | حذف جذور التجربة بعد التنظيف | PASS | الجذور 3→1 |
| 9 | الحفاظ على المؤلف «أحمد خالد توفيق» مع كتبه وفكّ علامته | PASS | `isDemo=0` بعد التنظيف |
| 10 | حذف السلاسل اليتيمة | PASS | السلاسل 2→0 |
| 11 | حذف المجموعات اليتيمة | PASS | المجموعات 2→0 |
| 12 | إعادة تسمية المجلد تنشئ كتابًا جديدًا وتُبقي القديم | PASS | سافاري الجديد/سافاري-MANUAL |
| 13 | استقرار الفحص بعد المسح الثاني (`created=0`) | PASS | `scan-complete ... created=0 cacheHits=5` |
| 14 | الحفاظ على تجاوز عنوان المستخدم | PASS | `isTitleUserConfirmed=1` |
| 15 | الحفاظ على تقدم الاستماع | PASS | 20053/FINISHED |
| 16 | عداد المكتبة يعكس البطاقات المعروضة | PASS | «4 كتابًا» |
| 17 | شاشة الإحصائيات سليمة | PASS | `issue4-stats.xml` |
| 18 | شاشة المحفوظات سليمة | PASS | `issue4-history.xml` + `issue4-history.png` |
| 19 | الاختبارات الوحداتية | PASS | 43 وحدة/194 اختبارًا، 0 إخفاق (من `test-results`) |
| 20 | الفحص الثابت (lint) دون مشكلات جديدة | PASS | `Lint found no new issues` |

---

## و) الملفات المعدّلة

- `app/src/main/java/com/example/audiobook/data/room/AppDatabase.kt` — الإصدار 5 + `MIGRATION_4_5` (4 ALTER TABLE + تعبئة `isDemo` للجذور القائمة) + قوائم UUID الحتمية للوسوم التجريبية.
- `app/src/main/java/com/example/audiobook/data/room/entity/Entities.kt` — عمود `isDemo` في الجذر/المؤلف/السلسلة/المجموعة.
- `app/src/main/java/com/example/audiobook/data/room/dao/Daos.kt` — استعلامات التنظيف المذكورة في section أ.
- `app/src/main/java/com/example/audiobook/domain/usecases/DatabaseSeeder.kt` — وسم `isDemo = true` على كل كيانات التجربة.
- `app/src/main/java/com/example/audiobook/domain/usecases/LibraryManagement.kt` — `clearDemoData()` الجديدة + حقن `libraryRootDao` و`clearDemoFlag…`.
- `app/src/main/java/com/example/audiobook/presentation/library/LibraryViewModel.kt` — فلتر `isFullyMissing`/`mapNotNull`.
- `app/src/main/java/com/example/audiobook/domain/usecases/ScanRoot.kt` — تعليق التوثيق (section د).
- `app/src/test/java/com/example/audiobook/domain/usecases/LibraryManagementTestFactory.kt` — تمرير `libraryRootDao` للمصنع.

---

## ز) القيود والملاحظات المتبقية

1. **المصدر المستخدم في إظهار/page الرئيسية «ماذا بعد؟»**: يعرض أحيانًا كتبًا مفقودة كليًا (مصدر بيانات مختلف غير خاضع لفلتر المكتبة) — خارج نطاق هذا الإصلاح ولم يُمسّ.
2. **إدخال نص عربي عبر adb (`input text`) غير مدعوم** — اختبار البحث العربي عبر الواجهة أُجري جزئيًا (نتيجته لم تُسجَّل كاملة عبر automatio اداتنا)، ويُستحسن إعادته يدويًا أو عبر اختبار وهمي (fakes) على مستوى ViewModel.
3. **فتح مشغِّل كتاب مفقود كليًا** غير ممكن عبر الواجهة لأنه مخفي فعلًا — التأكيد المتبقي لسلوك المشغّل على المفقود كليًا يُترك لاختبار يدوي إن لزم.
4. **إعادة تشغيل التطبيق بعد التنظيف لا تعيد البذر التجريبي** لأن البذر مشروط بعدم وجود كتب (نمط DEBUG قائم دون تغيير) — مؤكد بـ `has_seeded_demo=false` و absence بيانات تجريبية بعد إعادة الإطلاق.
5. حالة المحاكي في نهاية الجلسة: 6 كتب، جذر حقيقي واحد، لا كيانات تجريبية، «ما وراء الطبيعة» مفقودة جزئيًا (ملف 02.mp3 محذوف من الجهاز كأثر اختباري متعمد).