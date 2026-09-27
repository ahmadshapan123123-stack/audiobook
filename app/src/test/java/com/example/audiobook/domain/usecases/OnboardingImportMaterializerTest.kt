package com.example.audiobook.domain.usecases

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.audiobook.data.room.AppDatabase
import com.example.audiobook.data.room.entity.DiscoveryStatus
import com.example.audiobook.data.room.entity.LibraryRootEntity
import com.example.audiobook.data.room.entity.ScanStatus
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import java.util.UUID

/**
 * GAP 2 — تغطية الشريحة التي نُقلت من `OnboardingViewModel` إلى
 * [OnboardingImportMaterializer] و[PendingOnboardingImport].
 *
 * وجود هذا الملف مهم: الكود انتقل من ViewModel (بلا أي اختبار) إلى طبقة
 * المجال، ومن المنطقي ألّا يُترك بلا اختبار لمجرّد نقله. نتحقق من ثلاثة
 * أشياء: صحّة الكتابة على دفعات، واحترام حدود الإلغاء، وعقد «الطلب المعلّق».
 */
@RunWith(RobolectricTestRunner::class)
class OnboardingImportMaterializerTest {

    private lateinit var context: Context
    private lateinit var database: AppDatabase
    private lateinit var materializer: OnboardingImportMaterializer
    private lateinit var root: LibraryRootEntity

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        database = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        materializer = OnboardingImportMaterializer(database)
        root = LibraryRootEntity(
            id = UUID.randomUUID(),
            uri = "content://library",
            displayName = "Library",
            isPriority = true,
            isEnabled = true,
            lastScanAt = null,
            scanStatus = ScanStatus.IDLE
        )
        runBlocking { database.libraryRootDao().insert(root) }
    }

    @After
    fun tearDown() = database.close()

    private fun spec(
        author: String?,
        series: String?,
        title: String,
        folder: String = "a/b/$title"
    ) = OnboardingImportMaterializer.BookSpec(
        authorName = author,
        seriesName = series,
        title = title,
        folderPath = folder,
        totalDurationMs = 60_000L
    )

    @Test
    fun writesBookAndEditionForEverySpec() = runBlocking {
        val specs = listOf(
            spec("Naguib Mahfouz", null, "Book1"),
            spec("Naguib Mahfouz", "Cairo", "Book2"),
            spec(null, null, "Standalone")
        )

        val created = materializer.materialize(
            root = root,
            bookSpecs = specs,
            renamedPaths = emptySet(),
            skippedPaths = emptyList()
        )

        assertEquals("عدد الكتب المموّهة", 3, created)
        assertEquals(3, database.bookDao().getAll().size)
        val editions = database.editionDao().getByRoot(root.id)
        assertEquals("إصدار لكل كتاب", 3, editions.size)
        assertTrue("الجذر مربوط بكل الإصدارات", editions.all { it.libraryRootId == root.id })
    }

    @Test
    fun sharesAuthorAndSeriesRowsAcrossBooks() = runBlocking {
        val specs = listOf(
            spec("Same Author", "Same Series", "A1"),
            spec("Same Author", "Same Series", "A2"),
            spec("Same Author", "Same Series", "A3")
        )

        materializer.materialize(root, specs, emptySet(), emptyList())

        // إن لم يُتخطَّ الكاش لتكون هناك ثلاثة صفوف مؤلف/سلسلة لـ3 كتب،
        // وهو خطأ صامت في تطبيع القاعدة.
        val authors = database.authorDao().getByName("Same Author")
        assertNotNull("صف مؤلف واحد مشترك", authors)
        val books = database.bookDao().getAll().filter { it.seriesId != null }
        assertEquals("كل الكتب على السلسلة نفسها", 1, books.map { it.seriesId }.distinct().size)
    }

    @Test
    fun renamedFolderMarksTitleAsUserConfirmed() = runBlocking {
        val specs = listOf(spec("A", null, "Original", folder = "a/b/Original"))

        materializer.materialize(
            root = root,
            bookSpecs = specs,
            renamedPaths = setOf("a/b/Original"),
            skippedPaths = emptyList()
        )

        val book = database.bookDao().getAll().single()
        assertTrue(
            "المجلد المُعاد تسميته يجب أن يُعلَّم كتأكيد مستخدم",
            book.isTitleUserConfirmed
        )
    }

    @Test
    fun notRenamedFolderLeavesTitleEditable() = runBlocking {
        materializer.materialize(
            root = root,
            bookSpecs = listOf(spec("A", null, "Auto", folder = "a/b/Auto")),
            renamedPaths = emptySet(),
            skippedPaths = emptyList()
        )

        assertTrue(
            "بلا إعادة تسمية يبقى العنوان قابلًا لتجاوز التصنيف",
            !database.bookDao().getAll().single().isTitleUserConfirmed
        )
    }

    @Test
    fun skippedFoldersBecomeIgnoredDiscoveries() = runBlocking {
        materializer.materialize(
            root = root,
            bookSpecs = listOf(spec("A", null, "Kept")),
            renamedPaths = emptySet(),
            skippedPaths = listOf("a/b/Skipped1", "a/b/Skipped2")
        )

        // `isIgnored` هو الاستعلام الذي يستعمله الفحص نفسه لتجاهل المجلد،
        // فنتحقق به لا بعدّاد صفّ لا يعبّر عن السلوك المقصود.
        assertTrue(
            "المجلد الأول سُجّل IGNORED",
            database.pendingDiscoveryDao().isIgnored(root.id, "a/b/Skipped1")
        )
        assertTrue(
            "المجلد الثاني سُجّل IGNORED",
            database.pendingDiscoveryDao().isIgnored(root.id, "a/b/Skipped2")
        )
        assertTrue(
            "المجلد المحتفظ به ليس متخطّى",
            !database.pendingDiscoveryDao().isIgnored(root.id, "a/b/Kept")
        )
    }

    @Test
    fun publishesProgressForEveryBatch() = runBlocking {
        // 120 كتابًا = ثلاث دفعات (50/50/20) — نتأكد أن التقدّم يُنشر فعلًا
        // حتى يبقى إشعار الخدمة حيًّا بدل أن يتجمّد على «جارٍ التجهيز».
        val specs = (1..120).map { spec("A${it % 5}", null, "T$it") }
        val seen = mutableListOf<Pair<Int, Int>>()

        val created = materializer.materialize(
            root = root,
            bookSpecs = specs,
            renamedPaths = emptySet(),
            skippedPaths = emptyList()
        ) { done, total -> seen += done to total }

        assertEquals(120, created)
        assertEquals("نشر لكل دفعة", 3, seen.size)
        assertEquals("الإجمالي هو عدد المواصفات", 120, seen.first().second)
        assertTrue("الدفعة الأخيرة تكتمل", seen.last().first >= 100)
    }

    @Test
    fun cancellationStopsBeforeWritingEveryBatch() = runBlocking {
        val specs = (1..150).map { spec("A${it % 5}", null, "T$it") }
        ScanProgressBus.requestCancel()
        try {
            val created = materializer.materialize(
                root = root,
                bookSpecs = specs,
                renamedPaths = emptySet(),
                skippedPaths = emptyList()
            )

            // الإلغاء يُفحص بعد الدفعة الأولى، فلا يكتمل العدد.
            assertTrue(
                "الإلغاء يجب أن يوقف التوجيه مبكرًا (كتب=$created من ${specs.size})",
                created < specs.size
            )
        } finally {
            ScanProgressBus.resetCancel()
        }
    }

    @Test
    fun pendingPayloadIsConsumedExactlyOnce() {
        val pending = PendingOnboardingImport()
        val rootId = UUID.randomUUID()
        val payload = PendingOnboardingImport.Payload(
            rootId = rootId,
            books = listOf(spec("A", null, "One")),
            renamedPaths = setOf("x"),
            skippedPaths = listOf("y")
        )

        assertNull("لا دفعة قبل enqueue", pending.consume())
        pending.enqueue(payload)

        val first = pending.consume()
        assertNotNull("الدفعة تُلتقط مرة", first)
        assertEquals("الجذر يمرّ سليمًا", rootId, first!!.rootId)
        assertEquals(1, first.books.size)
        assertEquals("المسارات المحفوظة", setOf("x"), first.renamedPaths)
        assertEquals(listOf("y"), first.skippedPaths)

        assertNull(
            "الدفعة تُستهلك مرة واحدة: إعادة الالتقاط تعني استيرادًا مكرّرًا",
            pending.consume()
        )
    }

    @Test
    fun clearDiscardsPendingPayload() {
        val pending = PendingOnboardingImport()
        pending.enqueue(
            PendingOnboardingImport.Payload(
                rootId = UUID.randomUUID(),
                books = listOf(spec("A", null, "One")),
                renamedPaths = emptySet(),
                skippedPaths = emptyList()
            )
        )

        pending.clear()

        assertNull(
            "الإلغاء قبل الإطلاق يجب أن يمحو الدفعة وإلا استوردتها خدمة بلا جذر",
            pending.consume()
        )    }
}
