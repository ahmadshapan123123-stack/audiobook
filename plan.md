مواصفات تطبيق مشغل الكتب الصوتية (Audiobook Player) — النسخة المُحصّنة (v2)
هذا الملف هو المرجع الوحيد الكامل للتطبيق (Source of Truth). أي برومبت تنفيذ لأي مرحلة يجب أن يشير إلى هذا الملف. عند أي تعارض بين هذا الملف وأي برومبت مرحلة، الأولوية دائمًا لهذا الملف، ويجب التوقف وطرح السؤال بدلًا من افتراض حل.
0. تصنيف القرارات — MUST / SHOULD / FUTURE
كل قرار في هذا المستند مصنّف بأحد ثلاث درجات، حتى لا يتعامل أي منفّذ (إنسان أو AI) مع كل التفاصيل بنفس درجة الإلزام:
MUST — قرار نهائي، لا يجوز تغييره أو "تحسينه" أثناء التنفيذ بدون تعديل هذا الملف أولًا.
SHOULD — قرار تصميمي قوي وموصى به بشدة، قابل للتحسين الطفيف أثناء التنفيذ طالما لم يخالف روح القرار.
FUTURE — خارج نطاق هذا الإصدار تمامًا. لا يُنفَّذ أي جزء منه الآن.
قائمة FUTURE (مؤجل بالكامل، لا يُلمس في أي مرحلة)
AI إضافي داخل التطبيق، مزامنة Supabase فعلية، Auth فعلي (تسجيل دخول)، Android Auto، Chromecast، دعم أجهزة متقدم (Tablet مخصص/TV/Web)، Downloads من مصدر خارجي، Transcription، محتوى من ناشر/متجر.
1. نظرة عامة على المشروع
[MUST] التطبيق Offline-first بالكامل: يعمل 100% بدون إنترنت، بدون حساب، بدون خادم. لا API، لا Cloud، لا Login مطلوب لأي فيتشر في هذا الإصدار.
[MUST] مصدر الملفات الصوتية بالكامل من جهاز المستخدم. لا رفع ولا تخزين لأي ملف صوتي على أي خادم.
[MUST] المفهوم الجوهري للتطبيق: Local Audiobook Library + Intelligent Organization + Premium Listening Experience — ليس مجرد مشغل صوت، بل مدير مكتبة ذكي.
[MUST] المعمارية جاهزة لإضافة حساب/مزامنة لاحقًا دون إعادة هيكلة (انظر قسم 12).
2. الـStack التقني
code
Code
Client:   Kotlin + Jetpack Compose + Media3/ExoPlayer + Room + Hilt + WorkManager
Backend (FUTURE، غير مُنفَّذ الآن): Supabase (PostgreSQL + Auth + Realtime)
Network:  لا يوجد اتصال شبكة فعلي في هذا الإصدار
[MUST] Kotlin + Jetpack Compose بالكامل (لا XML Views).
[MUST] Media3/ExoPlayer + MediaSession لكل ما يخص التشغيل والتكامل مع النظام.
[MUST] Room لكل التخزين المحلي.
[MUST] Hilt لإدارة الاعتماديات.
[SHOULD] WorkManager لعمليات الفحص الخلفية.
3. البنية المعمارية (Architecture)
code
Code
Android App
│
├── Presentation (Jetpack Compose)
│   ├── Home
│   ├── Library (All Books / Currently Listening / Finished / Favorites / Collections)
│   ├── BookDetails   ← Management Hub (انظر قسم 11)
│   ├── Player (UI فقط — لا يتحدث مباشرة مع ExoPlayer، انظر أدناه)
│   ├── Bookmarks
│   ├── Statistics
│   ├── History
│   ├── ScanReview
│   ├── Theme (Light / Dark / AMOLED)
│   └── Settings
│
├── Domain (Use Cases)
│   ├── ScanDeviceForAudiobooks
│   ├── DetectEditionCandidates
│   ├── PlayBook / ResumeBook
│   ├── UpdateProgress
│   ├── AddBookmark / AddNote / CreateChapterFromMark
│   ├── ManageSleepTimer
│   ├── ManageCollections / ManageFavorites
│   ├── GetListeningStatistics
│   └── RecoverInterruptedSession
│
├── Data
│   ├── Room (Entities + DAO)
│   ├── Repository (خلف Interfaces — Local-only الآن، قابل للاستبدال بـSynced لاحقًا)
│   ├── LocalFileSystem (قراءة الملفات + الـMetadata + الـFingerprint)
│   └── RemoteDataSource (Interface فارغ — FUTURE placeholder فقط)
│
├── Playback
│   ├── PlaybackController / PlaybackRepository   ← [MUST] طبقة وسيطة إجبارية
│   ├── Media3 / ExoPlayer                         ← لا يُستدعى مباشرة من Compose أبدًا
│   ├── MediaSession
│   ├── PlaybackService (Foreground Service)
│   └── SleepTimerController
│
└── Background
    ├── ScanWorker (WorkManager)
    └── (FUTURE) SyncWorker
[MUST] فصل Player Engine عن Player UI
شاشات الـCompose يُمنع أن تتعامل مباشرة مع ExoPlayer. كل تفاعل (Play/Pause/Seek/Speed...) يمر عبر PlaybackController (Interface) الذي تُنفّذه طبقة الـPlayback وتديره كـSingleton مرتبط بدورة حياة الـService. هذا الفصل ضروري للاستقرار عند تدوير الشاشة، الخروج من التطبيق، أو انقطاع الـProcess.
4. نموذج البيانات (Data Model) — النسخة المُحصّنة
التسلسل الهرمي
code
Code
LibraryRoot (مصدر فحص)
Author
  └── Series (اختياري)
        └── Book
              └── Edition (نسخة/إصدار)
                    └── AudioFile (ملف أو أكثر)
                          └── Chapter
[MUST] كل Entity رئيسي يستخدم id: UUID ثابت محليًا (وليس Auto-increment رقمي)
السبب: أي مزامنة مستقبلية تحتاج معرّفًا محليًا لا يتغير أبدًا بغض النظر عن ترتيب الإدخال أو الجهاز. remoteId: UUID? منفصل تمامًا ويُملأ فقط عند تفعيل الـSync مستقبلًا (FUTURE).
الكيانات بالتفصيل
LibraryRoot (جديد — كان مفهومًا ضمنيًا فقط سابقًا)
id (UUID, PK)
uri (Storage Access Framework URI)
displayName
isPriority (Boolean — المجلد ذو الأولوية العالية)
isEnabled
lastScanAt
scanStatus (enum: IDLE / SCANNING / ERROR)
Author
id (UUID, PK), name, colorTheme (nullable)
Series
id (UUID, PK), authorId (FK), name, colorTheme (nullable — أولوية أعلى من لون المؤلف)
Book
id (UUID, PK), title, authorId (FK), seriesId (FK, nullable), orderInSeries (nullable), genre, coverImagePath, coverSource (enum — انظر قسم 9)، isCoverUserSelected (Boolean)، defaultEditionId (FK, nullable)
remoteId (UUID?, null افتراضيًا), syncStatus (enum: LOCAL_ONLY/SYNCED/PENDING_SYNC، افتراضي LOCAL_ONLY)
Edition
id (UUID, PK), bookId (FK), narratorName (nullable), label, totalDurationMs, fileFormat, libraryRootId (FK), sourceFolderPath, confidenceScore, isUserConfirmed (Boolean)
remoteId, syncStatus (كما أعلاه)
AudioFile (محصّن بحقول الهوية)
id (UUID, PK), editionId (FK)
fileUri, relativePath, fileName
orderIndex, durationMs, fileSizeBytes
lastModified (timestamp من نظام الملفات)
contentFingerprint (نص: مبني من size + lastModified + uri كاستراتيجية سريعة أولى — ليس Hash كامل للملف؛ Hash كامل اختياري ويُحسب فقط عند الحاجة الفعلية لتأكيد الهوية)
mimeType
fileStatus (enum: AVAILABLE / MISSING — انظر قسم 8)
Chapter
id (UUID, PK), editionId (FK), title (nullable), startPositionMs, orderIndex
createdFrom (enum: AUTO_SPLIT / USER_MARK / MANUAL / IMPORTED ← جديد، للفصول المستوردة من M4B المضمّنة)
Bookmark
id (UUID, PK), editionId (FK), positionMs, createdAt, type (BOOKMARK/NOTE), noteText (nullable)
remoteId, syncStatus
ListeningProgress
id (UUID, PK), editionId (FK, 1-to-1), currentPositionMs, lastPlayedAt, status (NOT_STARTED/IN_PROGRESS/FINISHED), playbackSpeed
remoteId, syncStatus
Collection
id (UUID, PK), name, icon (nullable)
remoteId, syncStatus
CollectionBookCrossRef — collectionId (FK), bookId (FK)
FavoriteBook — bookId (FK, PK), addedAt
ListeningSession (محصّن لدعم الاسترجاع بعد Crash)
id (UUID, PK), editionId (FK), startedAt, endedAt (nullable)
durationListenedMs
endReason (enum: MANUAL_PAUSE/SLEEP_TIMER/FINISHED_BOOK/APP_CLOSED/INTERRUPTED)
sessionState (enum: ACTIVE/COMPLETED/INTERRUPTED ← جديد)
[MUST] عند بدء أي جلسة تشغيل، تُنشأ Session بحالة ACTIVE وتُحفظ فورًا (ليست فقط عند الانتهاء). عند بدء التطبيق، أي Session بحالة ACTIVE من جلسة سابقة (لم تُغلق بسبب Crash/Force-kill) تُحوَّل تلقائيًا إلى INTERRUPTED مع endedAt = آخر وقت حفظ موضع معروف.
EditionMatchDecision (محصّن بمراجع صريحة)
id (UUID, PK)
subjectEditionId (FK — الإصدار/المرشح الذي اتُخذ بشأنه القرار)
comparedAgainstEditionId (FK, nullable — الإصدار الآخر في المقارنة إن وُجد)
signalsSnapshot (JSON: القيم الفعلية وقت اتخاذ القرار)
userDecision (SAME_EDITION/DIFFERENT_EDITION/NOT_SAME_BOOK)
createdAt
5. مميزات المكتبة (Library)
[MUST] الأقسام: All Books, Currently Listening, Finished, Favorites, Collections, Recently Added.
[MUST] Grid View / List View قابلين للتبديل.
[MUST] Sort: بالاسم، تاريخ الإضافة، آخر استماع، نسبة الإنجاز. Filter: تصنيف، حالة، سلسلة.
[MUST] بطاقة "Continue Listening": غلاف، عنوان، نسبة إنجاز، وقت متبقٍ، زر يفتح الـPlayer من آخر موضع فعليًا.
[MUST] Search يغطي: العنوان، المؤلف، الراوي، السلسلة، أسماء الفصول.
[MUST] Arabic Search Normalization (جزء أساسي من البحث، وليس تحسينًا مستقبليًا):
قبل المقارنة، يُطبَّق تطبيع نصي على الاستعلام وعلى البيانات المخزنة:
إزالة التشكيل (Diacritics).
توحيد أشكال الألف: أ/إ/آ/ا → ا.
توحيد التاء المربوطة والهاء: ة ↔ ه (معاملتهما كمتكافئتين في المطابقة).
توحيد الياء والألف المقصورة: ي ↔ ى.
إزالة المسافات الزائدة والفروق في الهمزات الوسطى الشائعة.
مثال يجب أن ينجح: البحث عن "ماوراء الطبيعه" يجب أن يطابق "ما وراء الطبيعة".
6. فحص الملفات (Scanning) — النسخة المُحصّنة
[MUST] كل مصدر فحص هو LibraryRoot حقيقي في قاعدة البيانات (وليس مسارًا مدفونًا في Settings). المستخدم يحدد Root واحدًا على الأقل بـ isPriority = true.
[MUST] فحص Priority Root: فوري عند أول تشغيل، وعند أي تغيير مكتشَف، وعند طلب Refresh يدوي.
[SHOULD] فحص باقي الـRoots غير ذات الأولوية: خلفية بأولوية منخفضة عبر WorkManager، قابل للتشغيل اليدوي أيضًا.
[MUST] Metadata Cache: قبل إعادة تحليل أي ملف، يُقارَن size + lastModified + uri الحاليين بما هو مخزّن في AudioFile. إن تطابقا، لا يُعاد تحليل الـMetadata أو حساب أي Fingerprint إضافي. هذا إلزامي للأداء مع مكتبات تحتوي مئات الكتب.
[MUST] سياسة الملفات المفقودة/المحذوفة: عند اكتشاف Scan أن ملفًا لم يعد موجودًا في مساره، لا يُحذف الـEdition أو الـBook المرتبطان به. يُحدَّث AudioFile.fileStatus = MISSING فقط. الكتاب يظل ظاهرًا في المكتبة (بعلامة بصرية تدل على نقص جزء منه). لو عاد الملف لاحقًا (نفس الـURI/المسار)، يُعاد fileStatus = AVAILABLE تلقائيًا دون فقدان أي بيانات (Bookmarks/Progress/Chapters) مرتبطة به.
7. Smart Edition Detection — النسخة المُحصّنة
الإشارات (كما هي، عشر إشارات: اسم الملف، اسم المجلد، اسم مجلد المؤلف، السلسلة، رقم الكتاب بالسلسلة، الـMetadata الداخلية، المدة الإجمالية، عدد/ترتيب الملفات، الراوي، الصيغة/الجودة).
[MUST] مستويات الذكاء (Conservative / Balanced / Aggressive)، مع قيد صارم على الثلاثة كلها:
Conservative: لا دمج تلقائي إطلاقًا؛ كل تجميع يمر عبر تأكيد المستخدم.
Balanced (افتراضي): دمج تلقائي فقط عند ثقة شديدة الارتفاع (كل الإشارات القوية متطابقة معًا: العنوان + المؤلف + مدة متقاربة جدًا).
Aggressive: اقتراحات أوسع تُعرَض للمستخدم في شاشة Review، لكن:
[MUST — قيد لا يُخترق في أي مستوى] اختلاف الراوي (narratorName مختلف بوضوح) أو اختلاف المدة الإجمالية بنسبة جوهرية (مثال معياري: أكثر من 15% فرق) لا يجوز أبدًا أن يؤدي إلى دمج تلقائي صامت، حتى في وضع Aggressive. الحد الأقصى المسموح به في Aggressive هو "اقتراح يظهر للمراجعة"، وليس دمجًا فعليًا بلا علم المستخدم. الخطأ الأسوأ في مكتبة ذكية هو دمج خاطئ لا رجعة سهلة فيه، بينما تأخير الدمج مؤقتًا قابل للإصلاح دائمًا.
[MUST] EditionMatchDecision يحمل مراجع صريحة (subjectEditionId, comparedAgainstEditionId) وليس JSON فقط، بحيث يمكن إعادة تطبيق القرار أو مراجعته لاحقًا بثقة.
[MUST] User Override Wins (قاعدة صريحة إلزامية): أي تصحيح يدوي فعله المستخدم (دمج، فصل، إعادة تسمية، تغيير راوٍ، تعيين غلاف) يصبح نهائيًا وموثوقًا (Authoritative). أي Scan لاحق يُمنع من الكتابة فوق قرار المستخدم أو التراجع عنه تلقائيًا. الاستثناء الوحيد: طلب صريح من المستخدم بعنوان "Reset Metadata / إعادة الاكتشاف" على ذلك الكتاب/الإصدار تحديدًا.
[MUST] M4B ذات الفصول المضمّنة: عند اكتشاف Chapters داخل ملف M4B، تُستورد مباشرة كـChapter بـ createdFrom = IMPORTED. لا تُطبَّق عليها خوارزمية Auto-Split.
[MUST] شاشة Review Matches: ملخص رقمي (عدد الملفات/الكتب/السلاسل/المؤلفين/الحالات المشكوك فيها) + عرض الحالات متوسطة/منخفضة الثقة فقط، مع الخيارات الثلاثة (نفس الإصدار / إصدار مختلف / ليس نفس الكتاب).
[MUST] شاشة إدارة الإصدارات من صفحة الكتاب: دمج، فصل، نقل، تغيير راوٍ، إعادة تسمية، تعيين افتراضي.
8. حالة الملفات المفقودة (Missing Files) — تفصيل تنفيذي
انظر قسم 6. يجب أن تعرض واجهة المستخدم مؤشرًا بصريًا واضحًا (مثال: أيقونة تحذير صغيرة على غلاف الكتاب أو شارة "بعض الملفات غير متاحة") عندما يحتوي أي إصدار على AudioFile واحد أو أكثر بحالة MISSING. [MUST] لا يُمنع المستخدم من فتح الكتاب أو رؤية تقدمه أو Bookmarks الخاصة به حتى لو كان بعض الملفات مفقودًا؛ يُمنع فقط تشغيل الجزء المفقود تحديدًا مع رسالة واضحة.
9. أولوية الغلاف (Cover Precedence)
[MUST] الترتيب الإلزامي عند تحديد غلاف الكتاب:
غلاف اختاره المستخدم يدويًا (isCoverUserSelected = true) — لا يُستبدل أبدًا تلقائيًا أثناء أي Scan لاحق.
غلاف مضمّن داخل الملف الصوتي (Embedded Cover Art).
ملف صورة في نفس المجلد (cover.jpg / cover.png أو ما شابه).
صورة من مجلد الكتاب بشكل عام (أول صورة موجودة).
Placeholder افتراضي بتصميم متسق مع هوية التطبيق البصرية (انظر قسم 13).
10. شاشة الـPlayer — التجربة الكاملة (بدون تغيير جوهري عن التصميم المتفق عليه — هذا الجزء ناضج ولا يُضخَّم أكثر)
الوصول للـPlayer
Library → Book Details → Play/Continue → Player، أو دخول مباشر من: Continue Listening، Mini Player، Notification، Lock Screen، Widget.
الخلفية (Gradient) — [MUST]
الأولوية: لون السلسلة → لون المؤلف → لون مستخرج تلقائيًا من الغلاف (Palette extraction) → لون Theme الافتراضي. [MUST] يجب أن يتوافق الـGradient بصريًا مع الـTheme النشط (Light/Dark/AMOLED) — انظر قسم 13، فلا يُنتج تباينًا سيئًا أو ألوانًا صارخة في أي من الأوضاع الثلاثة.
الجزء العلوي
زر رجوع، اسم الكتاب، اسم المؤلف مختصرًا، زر "More" (يحتوي فقط: معلومات الكتاب، تغيير الإصدار، إدارة الكتاب).
الـTimeline — [MUST]
يمثل الإصدار بالكامل (ليس الملف الفردي). مستويان: Overview (الكتاب كله + Markers للفصول/الـBookmarks المهمة) وZoomed View (30–60 دقيقة عند التفاعل). الفصول Markers بترقيم ديناميكي حقيقي دائمًا.
إنشاء الفصول — طريقتان متكاملتان [MUST]
توزيع تلقائي تكيّفي (Adaptive Auto-Split) — قابل للتعديل الكامل.
Mark أثناء الاستماع — الوقت يُسجَّل فورًا عند الضغط، قبل أي اختيار لاحق (Bookmark/Chapter/Note).
تعديل الفصول
ضغط مطوّل → Drag & Drop مع عرض الوقت بدقة → تحديث فوري للـTimeline والترقيم.
Bookmarks و Notes
منفصلان تمامًا عن مفهوم Chapter. Bookmark = موضع للرجوع إليه. Note = موضع + نص حر، القفز إليها يعيد التموضع فورًا.
أزرار التحكم الأساسية
code
Code
Previous Chapter | -15s | ▶ Play/Pause | +15s | Next Chapter
مع: Speed (سرعات ثابتة + Custom، محفوظة لكل كتاب)، Sleep Timer، More.
مبدأ التصميم — [MUST]
الظاهر دائمًا فقط: Play/Pause, ±15s, Previous/Next Chapter, Timeline, الفصل الحالي, Mark, Speed, Sleep Timer. كل ما عدا ذلك عند الطلب فقط. لا تُضِف أي عنصر تحكم إضافي غير مذكور هنا.
11. Book Details — [MUST] مركز إدارة الكتاب (Management Hub)
Book Details ليست مجرد صفحة عرض (غلاف + عنوان + زر تشغيل). هي المركز الذي تُدار منه كل جوانب الكتاب:
Metadata (عنوان، مؤلف، سلسلة، راوٍ) — قابلة للتعديل اليدوي.
الغلاف (تغيير يدوي، مع تفعيل isCoverUserSelected).
إدارة الإصدارات (Editions) بكل إجراءاتها (قسم 7).
قائمة الفصول الكاملة.
Bookmarks و Notes الخاصة بهذا الكتاب.
التقدم الحالي (Progress).
إحصائيات هذا الكتاب تحديدًا (وقت الاستماع الكلي له، تاريخ الإكمال إن وُجد).
تعيين الإصدار الافتراضي (defaultEditionId).
12. Sleep Timer الذكي — النسخة المُحصّنة والكاملة
الأساس
خيارات: 15/30/45/60 دقيقة + مدة مخصصة، عداد تنازلي حي داخل الـPlayer.
مرحلة التحذير (آخر 3 دقائق) — [MUST]
عند الوصول لـ03:00: تنبيه صوتي هادئ جدًا (Duck وليس Interrupt للصوت الأساسي) يتكرر كل 30 ثانية: 3:00, 2:30, 2:00, 1:30, 1:00, 0:30. زر التمديد (+15/+30 دقيقة) يظهر فقط في هذه النافذة الزمنية ولا يظهر قبلها إطلاقًا.
التمديد من شاشة القفل — [MUST]
عناصر تحكم فعلية (+15/+30/+60 دقيقة) على الإشعار/شاشة القفل تعمل دون فتح القفل أو التطبيق.
[MUST] تعريف "Active Interaction" الرسمي — قائمة نهائية، لا تُوسَّع أو تُقلَّص دون تعديل هذا الملف
يُعتبر تفاعلًا فعليًا (يُفعّل Auto-Extend):
Seek / سحب الـTimeline
Play / Pause صريح من المستخدم
±15s
Previous/Next Chapter
إضافة Bookmark
إضافة Note
إنشاء Chapter
تغيير السرعة
تمديد الـSleep Timer يدويًا
لا يُعتبر تفاعلًا (لا يُفعّل Auto-Extend):
مجرد فتح الشاشة / إضاءتها
بقاء الشاشة مضاءة بدون إجراء
تحريك الجهاز فيزيائيًا
وصول إشعار من تطبيق آخر
مجرد كون التطبيق في المقدمة (Foreground) بدون أي إجراء ملموس
[MUST] قاعدة التمديد التلقائي الدقيقة
عند حدوث تفاعل فعلي (من القائمة أعلاه) أثناء نافذة التحذير (آخر 3 دقائق): يُضاف 15 دقيقة بالضبط إلى الوقت المتبقي (وليس Reset لكامل المدة الأصلية، وليس 30 دقيقة). يظهر Toast خفيف: "تم تمديد مؤقت النوم تلقائيًا".
آخر ثوانٍ — [MUST]
عند آخر 2–3 ثوانٍ بدون أي تمديد (يدوي أو تلقائي): Audio Fade Out تدريجي حقيقي، ثم توقف كامل عند الصفر، مع حفظ الموضع فورًا.
بعد التوقف
تسجيل ListeningSession مع endReason = SLEEP_TIMER، sessionState = COMPLETED، وكل الحقول ذات الصلة.
13. الهوية البصرية والتصميم — [MUST] قسم إلزامي جديد
هذا التطبيق تجربة استماع يومية طويلة (ساعات متواصلة، غالبًا ليلًا)، لذلك التصميم البصري ليس تفصيلًا تجميليًا بل جزء أساسي من جودة المنتج.
[MUST] ثلاثة أوضاع مظهر كاملة، وليست تلوينًا سطحيًا
Light Mode: خلفيات فاتحة مريحة (ليست أبيض ناصع صارخ)، تباين نص كافٍ لقراءة مريحة، ألوان دافئة غير متعبة للعين.
Dark Mode: خلفيات داكنة رمادية-مزرقة وليست أسود مطلق، مع نصوص بتباين مدروس (تجنّب الأبيض النقي الحارق على الخلفية الداكنة).
AMOLED Black: أسود نقي حقيقي (#000000) لتوفير البطارية على شاشات AMOLED وراحة أكبر في الظلام الكامل أثناء النوم، مع الحفاظ على نفس التباين المدروس للنصوص.
[MUST] كل الشاشات (بما فيها الـPlayer بخلفيته Gradient الديناميكية) يجب أن تُختبر فعليًا في الأوضاع الثلاثة والتأكد من عدم وجود أي تعارض بصري (نص غير مقروء، تباين ضعيف، ألوان صارخة).
[MUST] التبديل بين الأوضاع فوري (بدون إعادة تشغيل التطبيق) ومحفوظ كإعداد دائم.
[MUST] نظام تصميم متكامل (Design System) — ليس Material Design افتراضيًا بلا تخصيص
Typography: اختيار خط (أو زوج خطوط) واضح ومريح للقراءة الطويلة، مع دعم كامل وصحيح للعربية (اتجاه RTL، أحجام مقروءة، تباعد أسطر مريح خصوصًا في قوائم الفصول والملاحظات الطويلة).
نظام ألوان مخصص: لون أساسي (Primary) وألوان ثانوية متسقة عبر التطبيق، وليست ألوان Material الافتراضية بدون تخصيص. الألوان المستخرجة ديناميكيًا من أغلفة الكتب (قسم 10) يجب أن تنسجم مع النظام العام ولا تبدو دخيلة.
المسافات والتخطيط (Spacing & Layout): استخدام نظام مسافات متسق (مقياس ثابت 4/8dp مثلًا) عبر كل الشاشات، تجنّب الازدحام البصري خصوصًا في المكتبة (شبكة أغلفة الكتب) وقائمة الفصول.
الحركة (Motion): انتقالات ورسوم متحركة خفيفة وسلسة (فتح الـPlayer، التبديل بين Overview/Zoomed Timeline، تمدد Bookmark) — تعزّز الإحساس بالجودة دون أن تُبطئ الاستخدام أو تُصبح مزعجة عند التكرار.
[MUST] الشعور العام المستهدف: تطبيق يبدو مصممًا خصيصًا لهذا الغرض (لا يبدو كقالب Compose افتراضي)، مريح للعين في الاستخدام الممتد، وواثق بصريًا (Cover كبير وواضح، Typography هادئة، ألوان متجانسة) — وليس "مبهرًا" بمعنى صاخب أو مثقل بالتأثيرات.
[SHOULD] عند تنفيذ أي شاشة جديدة، يُراجَع دليل التصميم الداخلي للمشروع (إن وُجد) قبل استخدام مكوّنات Compose الافتراضية بلا تخصيص.
14. Statistics و History
[MUST] ListeningSession يُسجَّل مع كل جلسة حقيقية (بداية/نهاية/مدة/سبب/حالة).
[MUST] تعريف "Chapter Completed": يُعتبر الفصل مكتملًا عندما يتجاوز موضع التشغيل (playhead) نسبة 90% أو أكثر من مدة ذلك الفصل، أو يتجاوز نهايته فعليًا (أيهما أسبق). هذا الحد (90%) هو القيمة المعتمدة؛ أي تغيير له يتطلب تحديث هذا الملف أولًا.
[MUST] شاشة Statistics: وقت الاستماع (اليوم/الأسبوع/الشهر)، عدد الكتب المكتملة، عدد الفصول المكتملة (حسب التعريف أعلاه)، Listening Streak (تعريفه: عدد الأيام المتتالية التي سُجِّلت فيها جلسة استماع واحدة على الأقل بغض النظر عن مدتها)، متوسط السرعة المستخدمة.
[MUST] شاشة History: قائمة زمنية بآخر الجلسات (اسم الكتاب، التاريخ، المدة).
15. Background Playback والتكامل مع النظام
[MUST] دعم كامل عبر Media3 MediaSession لـ: Lock Screen controls، Notification controls (Previous/Play-Pause/Next/Seek)، سماعات وسيارات Bluetooth، Headphone controls.
16. صيغ الملفات المدعومة
[MUST] MP3, M4A, AAC, Opus, FLAC، مع أهمية خاصة لـ M4B (فصول ومعلومات مضمّنة — انظر قسم 7).
17. إمكانية الوصول (Accessibility)
[MUST] TalkBack ودعم Content Descriptions لكل عنصر تفاعلي، دعم تكبير النص دون كسر أي تخطيط، تباين عالٍ اختياري، مساحات لمس لا تقل عن 48dp.
18. الجاهزية المستقبلية لتسجيل الدخول والمزامنة (FUTURE — تحضير معماري فقط، بدون تنفيذ فعلي)
[MUST] كل Entity رئيسي يستخدم id: UUID ثابت محليًا منذ البداية (قسم 4) — أساس ضروري لأي Sync مستقبلي.
[MUST] الكيانات المذكورة في قسم 4 تحمل remoteId: UUID? وsyncStatus منذ الآن، دون أي استخدام فعلي.
[MUST] الملفات الصوتية نفسها Local-first دائمًا وليست كيانًا سحابيًا: أي تصميم Sync مستقبلي يُزامن البيانات الوصفية فقط (Book/Edition/Chapter/Bookmark/Progress/Collection/Favorite) — لا يُزامن أبدًا filePath/fileUri لأنه غير صالح عبر الأجهزة المختلفة.
[MUST] طبقة الـRepository خلف Interfaces، بتنفيذ محلي فقط الآن (LocalOnlyXRepositoryImpl)، قابل للاستبدال لاحقًا بـSyncedXRepositoryImpl دون تغيير أي كود في الـDomain أو الـUI.
[MUST] RemoteDataSource مجرد Interface فارغ الآن (Placeholder) — بدون أي تنفيذ فعلي أو اتصال شبكة.
[MUST] لا شاشة تسجيل دخول أو حساب في هذا الإصدار إطلاقًا.
نهاية المستند. أي إضافة أو تعديل مستقبلي على الفيتشرز أو القرارات التقنية يجب أن يُحدَّث هنا أولًا قبل كتابة أي برومبت تنفيذ جديد.
برومبتات مراحل بناء تطبيق Audiobook Player (v2 — بعد المراجعة التقنية)
أرفق ملف AUDIOBOOK_APP_SPEC.md (v2) كاملاً مع كل برومبت مرحلة. كل قرار في هذا الملف مصنّف MUST / SHOULD / FUTURE — التزم بالتصنيف حرفيًا. لا تنفّذ أي شيء من قائمة FUTURE في أي مرحلة مهما بدا سهلاً أو مفيدًا.
قاعدة عامة لكل مرحلة: توقف بعد كل نقطة رئيسية لمراجعة صريحة (Checkpoint) — Build ناجح + اختبار فعلي (تشغيل حقيقي، وليس افتراضًا نظريًا) قبل الانتقال للنقطة التالية. لا تعتبر أي نقطة "منتهية" لمجرد أن الكود مكتوب.
Phase 0 — Foundation & Architecture Skeleton
code
Code
اقرأ AUDIOBOOK_APP_SPEC.md (v2) كاملاً، خصوصًا قسم "تصنيف القرارات" وقسم "البنية المعمارية". أنت مهندس أندرويد سنيور تبني تطبيق Audiobook Player.

المطلوب في هذه المرحلة فقط:

1. أنشئ مشروع Android بـ Kotlin + Jetpack Compose (أحدث إصدار مستقر وقت التنفيذ). حدد minSdk بما يتوافق فعليًا مع أحدث Jetpack Media3 — تحقق من التوافق الحقيقي، لا تفترض رقمًا قديمًا.
2. أضف فقط: Hilt، Room (إعداد أساسي بدون Entities بعد)، Media3/ExoPlayer (إعداد أساسي بدون منطق تشغيل)، WorkManager، Navigation Compose.
3. أنشئ هيكلة المجلدات بالضبط كما في قسم "البنية المعمارية": Presentation / Domain / Data / Playback / Background، بكل المجلدات الفرعية المذكورة.
4. **[أهم نقطة في هذه المرحلة]** أنشئ الـInterface الخاص بـ `PlaybackController` في طبقة Playback الآن كـعقد فارغ (بدون تنفيذ فعلي بعد) — فقط لتثبيت مبدأ الفصل بين Player Engine وPlayer UI منذ البداية المعمارية، كما هو موضح صراحة في قسم "فصل Player Engine عن Player UI" في الـSpec.
5. أنشئ Application class مهيأ لـ Hilt.
6. أنشئ MainActivity بأبسط شكل (شاشة ترحيب فقط) تؤكد أن المشروع يبني ويعمل فعليًا.

بعد كل نقطة، توقف واعرض: ملخص التنفيذ، نتيجة Build فعلية، وفي النقطة الأخيرة تأكيد تشغيل حقيقي على جهاز/محاكي. لا تُنفّذ أي شيء من فيتشرز مراحل لاحقة.
Phase 1 — Database & Domain (النموذج المُحصّن الكامل)
code
Code
بناءً على قسم "نموذج البيانات (Data Model) — النسخة المُحصّنة" وقسم "الجاهزية المستقبلية" في AUDIOBOOK_APP_SPEC.md v2، نفّذ:

1. استخدم `UUID` كمفتاح أساسي (id) لكل Entity رئيسي — هذا قرار MUST غير قابل للتفاوض، وليس Auto-increment رقميًا.
2. أنشئ كل Entity بالضبط كما هو مفصّل في قسم 4، بما يشمل الحقول المُضافة حديثًا:
   - `LibraryRoot` (كيان كامل جديد، وليس قيمة في Settings).
   - `AudioFile` مع: fileUri, relativePath, fileName, lastModified, contentFingerprint, mimeType, fileStatus (AVAILABLE/MISSING).
   - `Chapter.createdFrom` يتضمن القيمة الرابعة `IMPORTED`.
   - `ListeningSession` مع `sessionState` (ACTIVE/COMPLETED/INTERRUPTED) بالإضافة إلى `endReason` مع القيمة الجديدة `INTERRUPTED`.
   - `EditionMatchDecision` مع `subjectEditionId` و`comparedAgainstEditionId` كمراجع فعلية (Foreign Keys)، وليس فقط JSON.
   - `Book`/`Edition`/`Bookmark`/`ListeningProgress`/`Collection` مع `remoteId: UUID?` و`syncStatus` كما في قسم 18.
   - `Book` مع `coverSource` و`isCoverUserSelected`.
3. اكتب DAO كامل لكل Entity (Insert/Update/Delete/GetById/GetByParent مرتبة بـ orderIndex حيث ينطبق).
4. **منطق استرجاع الجلسات المقطوعة (Session Recovery):** عند بدء تشغيل التطبيق، ابحث عن أي `ListeningSession` بحالة `ACTIVE` من تشغيل سابق (لم تُغلق بسبب Crash/Force-kill)، وحوّلها فعليًا إلى `INTERRUPTED` مع `endedAt` = آخر وقت حفظ موضع معروف. اكتب هذا كـUse Case منفصل (`RecoverInterruptedSession`) يُستدعى عند إقلاع التطبيق.
5. أنشئ AppDatabase تجمع كل DAOs.
6. أنشئ طبقة Repository خلف Interfaces (BookRepository, EditionRepository, ChapterRepository, BookmarkRepository, ProgressRepository, CollectionRepository, StatisticsRepository, LibraryRootRepository) بتنفيذ Local-only فقط الآن، وInterface فارغ لـ `RemoteDataSource` كـPlaceholder فقط (بدون أي تنفيذ فعلي — قسم 18 من الـSpec MUST).
7. اكتب اختبارات وحدة لكل DAO: الإدراج/الاسترجاع، صحة الترتيب، صحة العلاقات، وتحديدًا اختبار سيناريو Session Recovery (نقطة 4).

بعد كل نقطة، توقف واعرض الكود + نتيجة تشغيل الاختبارات + تأكيد Build ناجح. لا تكتب أي منطق فحص ملفات فعلي بعد — هذا في Phase 2.
Phase 2 — Scanner & Local File System
code
Code
بناءً على قسم "فحص الملفات (Scanning) — النسخة المُحصّنة" في AUDIOBOOK_APP_SPEC.md v2، نفّذ:

1. صلاحيات الوصول لملفات الجهاز (أحدث سياسات أندرويد وقت التنفيذ).
2. شاشة إدارة LibraryRoots: إضافة مجلد جديد عبر Storage Access Framework، تحديد أي مجلد هو Priority (isPriority = true)، تفعيل/تعطيل أي Root.
3. ScanWorker بمنطقين منفصلين بوضوح: (أ) فحص فوري لكل Root بـ isPriority=true عند أول تشغيل/تغيير/Refresh يدوي، (ب) فحص خلفية بأولوية منخفضة لباقي الـRoots عبر WorkManager.
4. **Metadata Cache إلزامي:** قبل إعادة تحليل أي ملف، قارن `size + lastModified + uri` بما هو مخزَّن في AudioFile. عند التطابق، لا تُعِد قراءة الـMetadata إطلاقًا. اختبر هذا فعليًا: شغّل Scan مرتين متتاليتين على نفس المجلد بدون تغيير، وأثبت أن الفحص الثاني لا يعيد قراءة الملفات غير المتغيرة (قِس الزمن أو أضف Logging يوضح ذلك).
5. **سياسة الملفات المفقودة:** عند اكتشاف اختفاء ملف كان مسجلاً مسبقًا، حدّث `fileStatus = MISSING` فقط — لا تحذف أي Edition/Book/Chapter/Bookmark مرتبط. اختبر فعليًا: احذف ملفًا تجريبيًا بعد فحص أولي، أعد الفحص، وتأكد أن الكتاب ما زال ظاهرًا مع علامة نقص. ثم أعد الملف وتأكد من عودة `fileStatus = AVAILABLE` تلقائيًا دون فقدان أي Bookmark أو Progress كانا مرتبطين به.
6. استخراج Metadata من كل صيغة مدعومة (MP3, M4A, AAC, Opus, FLAC)، مع قراءة Chapters المضمّنة من M4B تحديدًا.

بعد كل نقطة، توقف واختبر فعليًا على ملفات حقيقية أو تجريبية واقعية، واعرض النتائج الفعلية (وليس افتراضًا) قبل الانتقال للنقطة التالية.
Phase 3 — Book/Edition Intelligence (Smart Edition Detection)
code
Code
بناءً على قسم "Smart Edition Detection — النسخة المُحصّنة" وقسم "أولوية الغلاف" في AUDIOBOOK_APP_SPEC.md v2، نفّذ:

1. خوارزمية Confidence Score بالإشارات العشر المذكورة، مع ثلاثة مستويات (Conservative/Balanced/Aggressive) يختارها المستخدم من الإعدادات.
2. **القيد الصارم غير القابل للاختراق في أي مستوى:** اختلاف الراوي بوضوح، أو اختلاف المدة الإجمالية بأكثر من 15%، يمنع أي دمج تلقائي صامت — حتى في Aggressive الحد الأقصى هو "اقتراح يُعرض للمراجعة"، وليس دمجًا فعليًا. اكتب اختبارًا صريحًا يثبت هذا القيد: أنشئ حالتين وهميتين براويين مختلفين بوضوح، وتأكد أن الخوارزمية لا تدمجهما تلقائيًا في أي من المستويات الثلاثة.
3. حفظ كل قرار (تلقائي أو يدوي) في `EditionMatchDecision` بمراجع صريحة (subjectEditionId, comparedAgainstEditionId) واستخدام القرارات السابقة فعليًا (وليس شكليًا) في تعديل أوزان المطابقة لهذا المستخدم تحديدًا.
4. **User Override Wins:** نفّذ قاعدة صريحة تمنع أي Scan لاحق من الكتابة فوق أي تصحيح يدوي سابق للمستخدم (دمج/فصل/تسمية/راوٍ/غلاف)، إلا عند طلب صريح "Reset Metadata / إعادة الاكتشاف" على ذلك العنصر تحديدًا. اختبر هذا فعليًا: صحّح تجميعًا يدويًا، أعد تشغيل Scan كامل، وتأكد أن التصحيح لم يُلغَ.
5. استيراد فصول M4B المضمّنة كـ`Chapter` بـ `createdFrom = IMPORTED` (بدون تطبيق Auto-Split عليها).
6. **أولوية الغلاف** بالترتيب الخمسي المذكور بالضبط في الـSpec، مع عدم استبدال أي غلاف اختاره المستخدم يدويًا أثناء أي Scan لاحق.
7. شاشة "Review Matches": الملخص الرقمي + عرض الحالات متوسطة/منخفضة الثقة فقط + الأزرار الثلاثة.
8. شاشة إدارة الإصدارات من صفحة الكتاب (دمج، فصل، نقل، تغيير راوٍ، إعادة تسمية، تعيين افتراضي).

بعد كل نقطة، توقف واختبر على بيانات تجريبية واقعية (حالات تطابق قوي وحالات مشكوك فيها) وأثبت أن النتائج منطقية فعليًا، خصوصًا نقطتي القيد الصارم وUser Override Wins.
Phase 4 — Design System & Theming (Light / Dark / AMOLED)
code
Code
بناءً على قسم "الهوية البصرية والتصميم" في AUDIOBOOK_APP_SPEC.md v2، وقبل بناء أي شاشة مكتبة أو Player، أسس نظام التصميم الكامل للتطبيق:

1. صمّم نظام ألوان مخصص كامل لثلاثة أوضاع: Light (خلفيات فاتحة مريحة غير صارخة)، Dark (رمادي-مزرق داكن وليس أسود مطلق، تباين نص مدروس بدون أبيض حارق)، AMOLED Black (أسود نقي #000000 حقيقي مع نفس جودة التباين). لا تستخدم ألوان Material الافتراضية بدون تخصيص فعلي.
2. اختر/أعد نظام Typography يدعم العربية بشكل صحيح تمامًا (RTL كامل، تباعد أسطر مريح للقراءة الطويلة في قوائم الفصول والملاحظات)، مع تدرّج أحجام واضح للعناوين/النصوص الثانوية.
3. أسس نظام مسافات وتخطيط متسق (Spacing scale ثابت، مثلاً مضاعفات 4dp) يُستخدم في كل شاشة لاحقة — لا تترك كل شاشة تخترع مسافاتها الخاصة.
4. أسس مجموعة مكونات Compose أساسية قابلة لإعادة الاستخدام (أزرار، بطاقات كتب، Chips، Sliders) بنفس الهوية البصرية، بدلاً من استخدام مكونات Material الافتراضية مباشرة بلا تخصيص.
5. نفّذ آلية تبديل فوري بين الأوضاع الثلاثة (بدون إعادة تشغيل)، محفوظة كإعداد دائم في التخزين المحلي.
6. اختبر شاشة تجريبية واحدة (مثلاً شاشة ترحيب أو شاشة إعدادات بسيطة) في الأوضاع الثلاثة، وتأكد فعليًا (بلقطات شاشة أو وصف دقيق لما تراه) من عدم وجود أي مشكلة تباين أو ألوان صارخة في أي وضع قبل اعتماد النظام لبقية المراحل.

هذه المرحلة أساس بصري لكل ما يأتي بعدها — لا تنتقل لبناء شاشات فعلية (Library/Player) قبل إثبات أن نظام التصميم متماسك وجاهز في الأوضاع الثلاثة.
Phase 5 — Library UI
code
Code
بناءً على قسم "مميزات المكتبة" في AUDIOBOOK_APP_SPEC.md v2، وباستخدام نظام التصميم المؤسَّس في Phase 4، نفّذ:

1. شاشة المكتبة بكل الأقسام: All Books, Currently Listening, Finished, Favorites, Collections, Recently Added — Grid/List قابل للتبديل، مصمَّمة بهوية بصرية واضحة (أغلفة كبيرة وواضحة، مسافات متسقة، ليست شبكة افتراضية مزدحمة).
2. Search مع **Arabic Normalization إلزامي** كما هو مفصّل في الـSpec (إزالة تشكيل، توحيد الألف بأشكالها، توحيد ة/ه، توحيد ي/ى). اختبر فعليًا: ابحث عن "ماوراء الطبيعه" وتأكد أنه يطابق "ما وراء الطبيعة" في بيانات تجريبية.
3. Sort وFilter كما هو مذكور.
4. بطاقة "Continue Listening" بتصميم بصري جذاب ومريح (غلاف بارز، شريط تقدم واضح، نص وقت متبقٍ مقروء).
5. إدارة Collections وFavorites بالكامل.
6. اختبر الثلاثة أوضاع (Light/Dark/AMOLED) على هذه الشاشة تحديدًا وتأكد من التناسق البصري.

بعد كل نقطة، توقف واختبر ببيانات تجريبية حقيقية (عدة كتب عربية بأسماء متشابهة لاختبار البحث) قبل الانتقال للنقطة التالية.
Phase 6 — Book Details (Management Hub)
code
Code
بناءً على قسم "Book Details — مركز إدارة الكتاب" في AUDIOBOOK_APP_SPEC.md v2، نفّذ شاشة Book Details كمركز إدارة كامل وليس شاشة عرض بسيطة:

1. عرض/تعديل Metadata (عنوان، مؤلف، سلسلة، راوٍ).
2. تغيير الغلاف يدويًا مع تفعيل isCoverUserSelected = true فور الاختيار (اختبر أن هذا يمنع استبداله في أي Scan لاحق، بالتنسيق مع منطق Phase 3).
3. إدارة الإصدارات (كل الإجراءات المذكورة في قسم 7 من الـSpec).
4. قائمة الفصول الكاملة، Bookmarks وNotes الخاصة بهذا الكتاب.
5. عرض التقدم الحالي وإحصائيات هذا الكتاب تحديدًا (وقت استماع كلي، تاريخ إكمال إن وُجد).
6. تعيين الإصدار الافتراضي.

بعد كل نقطة، توقف واختبر فعليًا كل إجراء إدارة (خاصة تغيير الغلاف اليدوي وتأثيره على منع الاستبدال التلقائي) قبل الانتقال للنقطة التالية.
Phase 7 — Playback Engine (Media3 / ExoPlayer + PlaybackController)
code
Code
بناءً على قسم "فصل Player Engine عن Player UI" وقسم "شاشة الـPlayer" (الجزء التقني) في AUDIOBOOK_APP_SPEC.md v2، نفّذ:

1. نفّذ `PlaybackController` (الـInterface المؤسَّس في Phase 0) بشكل كامل الآن، كطبقة وسيطة إلزامية بين أي UI ومحرك ExoPlayer — يُمنع أي كود Compose من استدعاء ExoPlayer مباشرة في أي مرحلة لاحقة.
2. PlaybackService كـForeground Service يدير ExoPlayer وMediaSession عبر PlaybackController.
3. تشغيل الإصدار (Edition) كوحدة متصلة: التنقل بين ملفات AudioFile المتعددة سلس تمامًا وشفاف، والموضع المعروض دائمًا نسبة لإجمالي مدة الإصدار وليس الملف الفردي.
4. Auto Resume من آخر currentPositionMs محفوظ، وحفظ الموضع دوريًا أثناء التشغيل وفورًا عند Pause/إغلاق.
5. أزرار التحكم الأساسية (Play/Pause, ±15s, Previous/Next Chapter) بمنطق يعتمد على جدول Chapter.startPositionMs.
6. Playback Speed محفوظة لكل Edition على حدة في ListeningProgress.playbackSpeed.
7. تكامل MediaSession الكامل: Lock Screen، Notification، أزرار سماعات/سيارات Bluetooth.
8. **معالجة الملفات المفقودة أثناء التشغيل:** إذا وصل التشغيل لجزء يخص AudioFile بحالة MISSING، أوقف التشغيل عند تلك النقطة تحديدًا مع رسالة واضحة للمستخدم، بدلاً من محاولة تشغيل ملف غير موجود أو الانهيار.

بعد كل نقطة، اختبر فعليًا على جهاز/محاكي حقيقي بكتاب متعدد الملفات، وتأكد أن الانتقال بين الملفات سلس تمامًا دون توقف مسموع أو قفزة زمن، وأن كل تفاعل UI يمر فعليًا عبر PlaybackController وليس مباشرة مع ExoPlayer.
Phase 8 — Player UI (التصميم الكامل والتفاعلي)
code
Code
بناءً على قسم "شاشة الـPlayer" الكامل في AUDIOBOOK_APP_SPEC.md v2، وباستخدام PlaybackController من Phase 7 ونظام التصميم من Phase 4، نفّذ:

1. Gradient ديناميكي بالأولوية المحددة (سلسلة → مؤلف → غلاف → افتراضي)، مُختبر فعليًا في الأوضاع الثلاثة (Light/Dark/AMOLED) للتأكد من عدم وجود تعارض بصري.
2. Timeline بمستويين (Overview وZoomed View) مع Markers للفصول والـBookmarks، وترقيم ديناميكي حقيقي (اختبر: أضف فصلًا بين فصلين وتأكد من إعادة الترقيم التلقائي الكامل).
3. زر Mark السريع: يسجل الوقت فورًا عند الضغط قبل أي اختيار لاحق.
4. وضع تحرير الفصول (Drag & Drop) مع عرض الوقت بدقة أثناء السحب.
5. التوزيع التلقائي التكيّفي للفصول عند غيابها.
6. أزرار التحكم الأساسية وشريط Speed/Sleep Timer/More، بالضبط كما هو موصوف — بدون أي زر إضافي.
7. جودة بصرية عالية: غلاف بارز وواضح، حركة انتقال سلسة عند فتح الـPlayer وعند التبديل بين Overview/Zoomed، بدون أي تأثير يُبطئ الاستخدام.

بعد كل نقطة، توقف واختبر فعليًا (تسجيل شاشة أو وصف دقيق للسلوك المُختبر فعليًا)، خصوصًا الترقيم الديناميكي والـDrag & Drop والتناسق البصري عبر الأوضاع الثلاثة.
Phase 9 — Chapters + Marks + Bookmarks + Notes (تكامل كامل)
code
Code
هذه المرحلة تُثبّت وتُختبر التكامل الكامل بين Chapters وBookmarks وNotes عبر Book Details (Phase 6) والـPlayer (Phase 8) معًا، بناءً على الأقسام ذات الصلة في AUDIOBOOK_APP_SPEC.md v2:

1. تأكد أن إنشاء Bookmark/Note/Chapter من زر Mark في الـPlayer ينعكس فورًا في شاشة Bookmarks المستقلة وفي Book Details.
2. تأكد أن تعديل/نقل/حذف فصل من أي مكان (Player أو Book Details) يُحدّث كل الواجهات الأخرى فورًا (مصدر بيانات واحد، لا تكرار حالة محلية غير متزامنة).
3. اختبر القفز المباشر من Bookmark/Note إلى نفس اللحظة الزمنية فعليًا عبر PlaybackController.
4. اختبر سيناريو متكامل كامل: افتح كتابًا، أنشئ Bookmark، حوّل Mark آخر إلى Chapter جديد، عدّل موضعه بالسحب، تأكد من الترقيم الديناميكي، انتقل لشاشة Bookmarks وتأكد من ظهور كل شيء صحيحًا، ثم أغلق التطبيق وأعد فتحه وتأكد من بقاء كل شيء محفوظًا بدقة.

هذه مرحلة اختبار تكامل بامتياز — لا تضيف فيتشرز جديدة، فقط أثبت أن كل الأجزاء المبنية في المراحل السابقة تعمل معًا بلا تعارض.
Phase 10 — Smart Sleep Timer (التنفيذ الكامل الحرفي)
code
Code
هذه من أدق مراحل المشروع، بناءً على قسم "Sleep Timer الذكي — النسخة المُحصّنة والكاملة" في AUDIOBOOK_APP_SPEC.md v2. نفّذ **بحرفية الوصف دون أي تبسيط**:

1. SleepTimerController مستقل ضمن طبقة Playback، متكامل مع PlaybackController وليس مع ExoPlayer مباشرة.
2. خيارات التفعيل (15/30/45/60 + مخصص) مع عداد تنازلي حي.
3. مرحلة التحذير عند 3 دقائق بالضبط: تنبيه صوتي هادئ (Duck وليس Interrupt) يتكرر كل 30 ثانية عند 3:00/2:30/2:00/1:30/1:00/0:30.
4. زر التمديد يظهر فقط بدءًا من دخول نافذة الـ3 دقائق الأخيرة، ويختفي تمامًا قبلها — اختبر هذا صراحة.
5. تمديد من شاشة القفل (+15/+30/+60 دقيقة) يعمل دون فتح القفل أو التطبيق — اختبر فعليًا بقفل الجهاز.
6. **طبّق تعريف "Active Interaction" الرسمي حرفيًا** كما في الـSpec (القائمتان: ما يُعتبر تفاعلًا وما لا يُعتبر) — لا تضِف ولا تحذف أي بند من القائمتين.
7. **قاعدة التمديد التلقائي الدقيقة: +15 دقيقة بالضبط** عند أي تفاعل فعلي أثناء نافذة التحذير (وليس Reset لكامل المدة، وليس +30). أظهر Toast "تم تمديد مؤقت النوم تلقائيًا".
8. آخر 2–3 ثوانٍ بدون تمديد: Audio Fade Out تدريجي حقيقي ثم توقف كامل مع حفظ الموضع فورًا.
9. عند التوقف: سجّل ListeningSession بـ endReason=SLEEP_TIMER وsessionState=COMPLETED.

بعد كل نقطة، اختبر يدويًا فعليًا على جهاز حقيقي (خصوصًا نقاط 3، 4، 5، 6، 7) واذكر بدقة ما لاحظته أثناء الاختبار الفعلي، وليس افتراضًا نظريًا، قبل الانتقال للنقطة التالية.
Phase 11 — History & Statistics
code
Code
بناءً على قسم "Statistics و History" في AUDIOBOOK_APP_SPEC.md v2، نفّذ:

1. تسجيل ListeningSession فعليًا مع كل أسباب الانتهاء (MANUAL_PAUSE, SLEEP_TIMER, FINISHED_BOOK, APP_CLOSED, INTERRUPTED) بما يشمل التكامل مع منطق Session Recovery من Phase 1.
2. **طبّق تعريف "Chapter Completed" حرفيًا: playhead ≥ 90% من مدة الفصل أو تجاوز نهايته فعليًا** — لا تستخدم أي عتبة أخرى.
3. شاشة Statistics: وقت الاستماع (يوم/أسبوع/شهر)، كتب مكتملة، فصول مكتملة (حسب التعريف أعلاه)، Listening Streak (أيام متتالية بجلسة واحدة على الأقل)، متوسط السرعة.
4. شاشة History: قائمة زمنية بآخر الجلسات.

بعد كل نقطة، اختبر فعليًا بالاستماع لفترات قصيرة متعددة (محاكاة تواريخ مختلفة إن لزم) وتأكد أن الأرقام صحيحة حسابيًا، خصوصًا حساب Streak وتعريف اكتمال الفصل، قبل الانتقال للنقطة التالية.
Phase 12 — Accessibility, Full Audit & Performance
code
Code
بناءً على قسم "إمكانية الوصول" وقسم "الهوية البصرية والتصميم" في AUDIOBOOK_APP_SPEC.md v2، وكمراجعة شاملة نهائية قبل الاعتبار أن الإصدار الأول جاهز:

1. TalkBack: أضف Content Description حقيقي لكل عنصر تفاعلي في كل شاشة، واختبر فعليًا بتشغيل TalkBack والتنقل الكامل.
2. اختبر تكبير حجم الخط للحد الأقصى في إعدادات النظام على كل شاشة رئيسية وتأكد من عدم كسر أي تخطيط.
3. تأكد من مساحات لمس لا تقل عن 48dp لكل عنصر قابل للنقر عبر التطبيق كله.
4. **مراجعة بصرية شاملة نهائية:** افحص كل شاشة رئيسية (المكتبة، Book Details، الـPlayer، Bookmarks، Statistics، Settings) في الأوضاع الثلاثة (Light/Dark/AMOLED) وتأكد من التناسق الكامل مع نظام التصميم المؤسَّس في Phase 4 — لا شاشة منسية بتصميم افتراضي غير متسق.
5. **مراجعة أداء:** تأكد أن فحص الملفات الخلفي (Phase 2) لا يؤثر على سلاسة التشغيل أو استجابة الواجهة أثناء الاستخدام الفعلي، خصوصًا مع مكتبة كبيرة (اختبر بعدد كبير من الملفات التجريبية إن أمكن).
6. **مراجعة القيود الحرجة (Regression Check):** أعد اختبار صريح لكل القيود التالية للتأكد أنها لم تُكسر تراكميًا خلال المراحل السابقة: User Override Wins (Phase 3)، القيد الصارم لمنع الدمج التلقائي عند اختلاف الراوي/المدة (Phase 3)، سياسة الملفات المفقودة (Phase 2)، قاعدة التمديد التلقائي +15 دقيقة بالضبط (Phase 10)، تعريف Chapter Completed 90% (Phase 11).

هذه المرحلة الأخيرة يجب أن تُنهى بتقرير واضح: ما الذي تم اختباره فعليًا، وما النتيجة، لكل نقطة من النقاط الست أعلاه — وليس فقط تأكيدًا عامًا بأن "كل شيء يعمل".
ملاحظة ختامية
عند أي تعارض بين هذا الملف وAUDIOBOOK_APP_SPEC.md v2، الأولوية دائمًا للـSpec. لا يُسمح بإضافة أي فيتشر من قائمة FUTURE (AI إضافي، Auth فعلي، Supabase فعلي، Android Auto، Chromecast، دعم أجهزة متقدم) في أي مرحلة من هذه المراحل الثلاث عشرة.
Data model ممتاز ومطابق تمامًا للـSpec (Phase 1 مكتملة). خلينا نفحص باقي الطبقات.ثيم Phase 4 ممتاز فعلًا — نظام ألوان مخصص حقيقي للثلاث أوضاع. خلينا نفحص باقي الشاشات (Book Details، الترابط بين Bookmarks/Chapters، والـAccessibility).راجعت المشروع بالكامل ملف بملف. الصورة واضحة ودي حالة كل مرحلة فعليًا:
✅ مكتمل بجودة عالية
Phase 0 — Foundation: البنية المعمارية كاملة ومطابقة (Presentation/Domain/Data/Playback/Background)، وHilt/Room/Media3/WorkManager مُعدّة صح.
Phase 1 — Data Model: كل الكيانات الـ13 موجودة بالضبط كما في الـSpec v2، بما فيها كل التحصينات (UUID ثابت، LibraryRoot، حقول هوية الملفات، remoteId/syncStatus، EditionMatchDecisionEntity بمراجع صريحة، ListeningSession.sessionState). ممتاز.
Phase 2 — Scanner: ScanRoot.kt منفّذ باحتراف حقيقي — Metadata Cache شغّال فعليًا (مقارنة size/lastModified قبل إعادة القراءة)، سياسة الملفات المفقودة (MISSING/AVAILABLE) صحيحة، استيراد فصول M4B كـIMPORTED. هذا الجزء أفضل جزء في المشروع حاليًا.
Phase 4 — Theming: نظام ألوان مخصص حقيقي للثلاث أوضاع (مش Material افتراضي)، AMOLED أسود نقي فعلي، DesignSystemShowcase موجود.
🟡 منفّذ جزئيًا فقط
Phase 8 — Player UI: الـGradient الديناميكي منفّذ صح بالكامل (4 مستويات + فحص تباين). لكن الـTimeline فيها Zoom منطقي بس (zoomCenterMs) بدون تأكيد فعلي إن الـUI بترسم الـMarkers بدقة في الوضعين.
Phase 6 — Book Details: الشاشة موجودة بمنطق إدارة إصدارات صحيح (دمج/فصل/نقل/تسمية/راوٍ)، لكنها مربوطة ببيانات وهمية Hardcoded (sampleEdition, كتاب عشوائي بـauthorId عشوائي) — مش بتجيب فعليًا Book/Author/Series الحقيقيين من قاعدة البيانات بالـeditionId المُمرَّر.
Phase 5 — Library UI: كل منطق البحث/الفرز/الفلترة/التطبيع العربي مكتوب صح، لكن نفس المشكلة: sampleBooks قائمة Hardcoded بالكامل، مش متصلة بـBookRepository الحقيقي.
🔴 غير مبدوء أو ناقص جوهريًا
Phase 3 — Smart Edition Detection: دي أكبر فجوة في المشروع. ensureEdition() في الـScanner بيعمل إصدار واحد بس لكل مجلد بثقة ثابتة (confidenceScore = 1f) — مفيش خوارزمية Confidence Score حقيقية بالإشارات العشر، مفيش مستويات Conservative/Balanced/Aggressive، مفيش شاشة Review Matches، ومفيش استخدام فعلي لـEditionMatchDecision رغم إنها موجودة في قاعدة البيانات فاضية.
Phase 10 — Smart Sleep Timer: المنفّذ حاليًا setSleepTimer() هو عداد تنازلي بسيط يوقف التشغيل عند الصفر، بدون أي من التفاصيل المتفق عليها: لا تحذير آخر 3 دقائق، لا تنبيه صوتي متكرر، لا زر تمديد مشروط بالنافذة الزمنية، لا تحكم من شاشة القفل، لا Active Interaction detection، لا +15 دقيقة تلقائي، لا Fade Out.
Phase 11 — Statistics & History: لا توجد أي شاشة. StatisticsRepository فيه دالة واحدة فقط (completedSessions()) بدون أي حساب فعلي لـStreak أو Chapter Completion (90%) أو متوسط السرعة.
Phase 12 — Accessibility: صفر استخدام لـcontentDescription في كل المشروع (تأكدت بالبحث).
ربط الشاشات بالبيانات الحقيقية (Wiring): هذه فجوة عرضية تمس كل الشاشات — MainActivity بيستخدم EnsureDemoEdition وبيانات وهمية بدل ViewModels حقيقية تجيب من الـRepositories.
خطة تنفيذ المتبقي — بالترتيب
#	المرحلة	لماذا بهذا الترتيب
1	ربط الشاشات الحالية بالبيانات الحقيقية (ViewModels حقيقية لـLibrary/BookDetails تستبدل الـmock)	أساس لازم قبل أي حاجة تانية، وإلا أي فيتشر جديد هيتبني على بيانات وهمية
2	Smart Edition Detection الكامل (Phase 3 من برومبتاتنا)	أهم فجوة منطقية — التطبيق حاليًا "يفترض" كل مجلد إصدار منفصل بثقة كاملة، وده يخالف جوهر الفكرة
3	Smart Sleep Timer الكامل (Phase 10)	أعقد فيتشر في المواصفات ومفيش منه غير الهيكل
4	Statistics & History (Phase 11)	يحتاج بيانات ListeningSession حقيقية من الخطوة 1 والتشغيل الفعلي
5	Accessibility + مراجعة نهائية شاملة (Phase 12)	آخر خطوة كالمتفق عليه