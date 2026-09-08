package com.example.audiobook.presentation.bookdetails

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.audiobook.data.room.entity.BookEntity
import com.example.audiobook.data.room.entity.BookmarkType
import com.example.audiobook.data.room.entity.ChapterCreatedFrom
import com.example.audiobook.data.room.entity.CoverSource
import com.example.audiobook.data.room.entity.EditionEntity
import com.example.audiobook.data.room.entity.SyncStatus
import com.example.audiobook.domain.usecases.BookDetailsManagement
import com.example.audiobook.domain.usecases.CoverCandidate
import com.example.audiobook.domain.usecases.CoverCandidateSource
import com.example.audiobook.domain.usecases.CoverPolicy
import com.example.audiobook.domain.usecases.MarksCoordinator
import com.example.audiobook.presentation.theme.AppSpacing
import java.util.UUID
import kotlinx.coroutines.launch

@Composable
fun BookDetailsScreen(editionId: UUID, marks: MarksCoordinator, onBack: () -> Unit = {}, onPlay: () -> Unit = {}, onBookmarks: () -> Unit = {}) {
    val scope = rememberCoroutineScope()
    val manager = remember { BookDetailsManagement() }
    val authorId = remember { UUID.randomUUID() }
    var book by remember {
        mutableStateOf(BookEntity(title = "ما وراء الطبيعة", authorId = authorId, seriesId = null, orderInSeries = null, genre = "خيال", coverImagePath = "embedded-cover.jpg", coverSource = CoverSource.EMBEDDED, isCoverUserSelected = false, defaultEditionId = null, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY))
    }
    var title by remember { mutableStateOf(book.title) }
    var author by remember { mutableStateOf("أحمد خالد توفيق") }
    var series by remember { mutableStateOf("سلسلة ما وراء الطبيعة") }
    var narrator by remember { mutableStateOf("محمد خضير") }
    val editions = remember { mutableStateListOf(sampleEdition("الإصدار المحلي"), sampleEdition("إصدار الراوي الثاني")) }
    var defaultEditionId by remember { mutableStateOf(editions.first().id) }
    val chapters by marks.chapters(editionId).collectAsState(initial = emptyList())
    val bookmarks by marks.bookmarks(editionId).collectAsState(initial = emptyList())
    var activeTab by remember { mutableStateOf("overview") }

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppSpacing.lg), verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("رجوع") }
            Text("مركز إدارة الكتاب", style = MaterialTheme.typography.titleLarge)
        }
        Text(book.title, style = MaterialTheme.typography.displaySmall)
        Text("${book.coverSource} · ${if (book.isCoverUserSelected) "غلاف يدوي محمي" else "غلاف تلقائي"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = onPlay) { Text("تشغيل الإصدار الافتراضي") }

        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            FilterChip(activeTab == "overview", { activeTab = "overview" }, label = { Text("البيانات") })
            FilterChip(activeTab == "editions", { activeTab = "editions" }, label = { Text("الإصدارات") })
            FilterChip(activeTab == "content", { activeTab = "content" }, label = { Text("المحتوى") })
            FilterChip(activeTab == "stats", { activeTab = "stats" }, label = { Text("الإحصاءات") })
        }

        when (activeTab) {
            "overview" -> {
                SectionTitle("Metadata")
                OutlinedTextField(title, { title = it }, label = { Text("العنوان") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(author, { author = it }, label = { Text("المؤلف") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(series, { series = it }, label = { Text("السلسلة") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(narrator, { narrator = it }, label = { Text("الراوي") }, modifier = Modifier.fillMaxWidth())
                Button(onClick = { book = manager.updateMetadata(book, title, book.genre, book.seriesId) }) { Text("حفظ البيانات") }
                SectionTitle("الغلاف")
                Text("${book.coverImagePath ?: "لا يوجد"} · المصدر: ${book.coverSource}")
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    Button(onClick = { book = CoverPolicy.userSelected(book, "user-selected-cover.jpg") }) { Text("اختيار غلاف يدوي") }
                    TextButton(onClick = { book = CoverPolicy.choose(book.copy(isCoverUserSelected = false), listOf(CoverCandidate("embedded-cover.jpg", CoverCandidateSource.EMBEDDED), CoverCandidate("folder-cover.jpg", CoverCandidateSource.FOLDER_COVER))) }) { Text("إعادة الاكتشاف") }
                }
                Text(if (book.isCoverUserSelected) "هذا الغلاف لن يُستبدل أثناء Scan لاحق." else "الغلاف يتبع أولوية الاكتشاف التلقائي.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            "editions" -> EditionManagement(editions, defaultEditionId, manager, { defaultEditionId = it }, { editions.clear(); editions.addAll(it) })
            "content" -> {
                SectionTitle("الفصول (${chapters.size})")
                chapters.sortedBy { it.startPositionMs }.forEachIndexed { index, chapter ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${index + 1}. ${chapter.title ?: "فصل"} · ${chapter.startPositionMs / 1000}s")
                        TextButton(onClick = { scope.launch { marks.deleteChapter(chapter) } }) { Text("حذف") }
                    }
                }
                Divider()
                SectionTitle("Bookmarks و Notes (${bookmarks.size})")
                bookmarks.forEach { Text("${it.positionMs / 1000}s · ${it.noteText ?: if (it.type == BookmarkType.NOTE) "Note" else "Bookmark"}") }
                TextButton(onClick = onBookmarks) { Text("فتح شاشة Bookmarks") }
                Text("Bookmark وNote محفوظان كموضع مستقلين عن Chapter.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            "stats" -> {
                SectionTitle("التقدم")
                Text("42٪ مكتمل · ٦ ساعات متبقية", style = MaterialTheme.typography.titleLarge)
                Text("الإصدار الافتراضي: ${editions.firstOrNull { it.id == defaultEditionId }?.label ?: "غير محدد"}")
                SectionTitle("إحصاءات هذا الكتاب")
                Text("وقت الاستماع الكلي: 12 ساعة و35 دقيقة")
                Text("تاريخ الإكمال: لم يكتمل بعد")
            }
        }
    }
}

@Composable
private fun EditionManagement(editions: MutableList<EditionEntity>, defaultId: UUID?, manager: BookDetailsManagement, setDefault: (UUID) -> Unit, replace: (List<EditionEntity>) -> Unit) {
    SectionTitle("إدارة الإصدارات")
    editions.toList().forEach { edition ->
        var label by remember(edition.id, edition.label) { mutableStateOf(edition.label) }
        var narrator by remember(edition.id, edition.narratorName) { mutableStateOf(edition.narratorName.orEmpty()) }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(AppSpacing.md), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                OutlinedTextField(label, { label = it }, label = { Text("اسم الإصدار") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(narrator, { narrator = it }, label = { Text("الراوي") }, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    TextButton(onClick = { replace(editions.map { current -> if (current.id == edition.id) manager.renameEdition(edition, label) else current }) }) { Text("إعادة تسمية") }
                    TextButton(onClick = { replace(editions.map { current -> if (current.id == edition.id) manager.changeNarrator(edition, narrator) else current }) }) { Text("تغيير الراوي") }
                    TextButton(onClick = { setDefault(edition.id) }) { Text(if (defaultId == edition.id) "افتراضي ✓" else "تعيين افتراضي") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    TextButton(onClick = { replace(editions.filterNot { it.id == edition.id }) }) { Text("فصل") }
                    TextButton(onClick = { replace(editions.map { current -> if (current.id == edition.id) manager.moveEdition(edition, edition.libraryRootId, "نُقل يدويًا") else current }) }) { Text("نقل") }
                    TextButton(onClick = { replace(editions.filterNot { it.id != edition.id && it.bookId == edition.bookId }) }) { Text("دمج") }
                }
            }
        }
    }
    Text("القرارات اليدوية محفوظة كقرارات authoritative ولا يكتب عليها Scan لاحقًا.", color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun SectionTitle(text: String) { Text(text, style = MaterialTheme.typography.titleLarge) }

private fun sampleEdition(label: String) = EditionEntity(bookId = UUID.randomUUID(), narratorName = "محمد خضير", label = label, totalDurationMs = 45_000_000, fileFormat = "M4B", libraryRootId = UUID.randomUUID(), sourceFolderPath = "/library/book", confidenceScore = 1f, isUserConfirmed = false, remoteId = null, syncStatus = SyncStatus.LOCAL_ONLY)