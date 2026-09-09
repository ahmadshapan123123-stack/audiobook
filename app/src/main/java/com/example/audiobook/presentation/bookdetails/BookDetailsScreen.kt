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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiobook.data.room.entity.BookmarkType
import com.example.audiobook.data.room.entity.EditionEntity
import com.example.audiobook.domain.usecases.CoverCandidate
import com.example.audiobook.domain.usecases.CoverCandidateSource
import com.example.audiobook.presentation.theme.AppSpacing

@Composable
fun BookDetailsScreen(
    onBack: () -> Unit = {},
    onPlay: (editionId: java.util.UUID) -> Unit,
    onBookmarks: (editionId: java.util.UUID) -> Unit,
    viewModel: BookDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var title by remember { mutableStateOf("") }
    var author by remember { mutableStateOf("") }
    var activeTab by remember { mutableStateOf("overview") }

    val book = uiState.book

    if (book == null) {
        Column(modifier = Modifier.fillMaxSize().padding(AppSpacing.lg)) {
            TextButton(onClick = onBack) { Text("رجوع") }
            Text("الكتاب غير متوفر", style = MaterialTheme.typography.titleLarge)
        }
        return
    }

    if (title.isBlank()) title = book.title
    if (author.isBlank()) author = uiState.authorName

    Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(AppSpacing.lg), verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            TextButton(onClick = onBack) { Text("رجوع") }
            Text("مركز إدارة الكتاب", style = MaterialTheme.typography.titleLarge)
        }
        Text(book.title, style = MaterialTheme.typography.displaySmall)
        Text("${book.coverSource} · ${if (book.isCoverUserSelected) "غلاف يدوي محمي" else "غلاف تلقائي"}", color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { uiState.defaultEditionId?.let { onPlay(it) } }) { Text("تشغيل الإصدار الافتراضي") }

        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            FilterChip(activeTab == "overview", { activeTab = "overview" }, label = { Text("البيانات") })
            FilterChip(activeTab == "editions", { activeTab = "editions" }, label = { Text("الإصدارات") })
            FilterChip(activeTab == "content", { activeTab = "content" }, label = { Text("المحتوى") })
        }

        when (activeTab) {
            "overview" -> {
                SectionTitle("Metadata")
                OutlinedTextField(title, { title = it }, label = { Text("العنوان") }, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(author, { author = it }, label = { Text("المؤلف") }, modifier = Modifier.fillMaxWidth())
                Text("السلسلة: ${uiState.seriesName ?: "لا توجد"}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Button(onClick = {
                    if (title != book.title) viewModel.updateTitle(book, title)
                    if (author != uiState.authorName) viewModel.updateAuthor(book, author)
                }) { Text("حفظ البيانات") }
                SectionTitle("الغلاف")
                Text("${book.coverImagePath ?: "لا يوجد"} · المصدر: ${book.coverSource}")
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    Button(onClick = { viewModel.setUserCover(book, "user-selected-cover.jpg") }) { Text("اختيار غلاف يدوي") }
                    TextButton(onClick = {
                        viewModel.rediscoverCover(
                            book.copy(isCoverUserSelected = false),
                            listOf(CoverCandidate("folder-cover.jpg", CoverCandidateSource.FOLDER_COVER))
                        )
                    }) { Text("إعادة الاكتشاف") }
                }
                Text(if (book.isCoverUserSelected) "هذا الغلاف لن يُستبدل أثناء Scan لاحق." else "الغلاف يتبع أولوية الاكتشاف التلقائي.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                SectionTitle("التقدم")
                val progressFraction = if ((uiState.progress?.let { it.currentPositionMs } ?: 0L) <= 0L || (uiState.editions.firstOrNull()?.totalDurationMs ?: 0L) <= 0L) 0f
                    else (uiState.progress!!.currentPositionMs.toFloat() / uiState.editions.first().totalDurationMs).coerceIn(0f, 1f)
                Text("${(progressFraction * 100).toInt()}٪ مكتمل", style = MaterialTheme.typography.titleLarge)
            }
            "editions" -> EditionManagement(uiState.editions, uiState.defaultEditionId, viewModel)
            "content" -> {
                SectionTitle("الفصول (${uiState.chapters.size})")
                uiState.chapters.forEachIndexed { index, chapter ->
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("${index + 1}. ${chapter.title ?: "فصل"} · ${chapter.startPositionMs / 1000}s")
                    }
                }
                Divider()
                SectionTitle("Bookmarks و Notes (${uiState.bookmarks.size})")
                uiState.bookmarks.forEach { Text("${it.positionMs / 1000}s · ${it.noteText ?: if (it.type == BookmarkType.NOTE) "Note" else "Bookmark"}") }
                uiState.defaultEditionId?.let { editionId ->
                    Button(onClick = { onBookmarks(editionId) }, modifier = Modifier.padding(top = AppSpacing.sm)) { Text("فتح شاشة Bookmarks") }
                }
                Text("Bookmark وNote محفوظان كموضع مستقلين عن Chapter.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@Composable
private fun EditionManagement(editions: List<EditionEntity>, defaultId: java.util.UUID?, viewModel: BookDetailsViewModel) {
    SectionTitle("إدارة الإصدارات")
    editions.forEach { edition ->
        var label by remember(edition.id, edition.label) { mutableStateOf(edition.label) }
        var narrator by remember(edition.id, edition.narratorName) { mutableStateOf(edition.narratorName.orEmpty()) }
        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
            Column(modifier = Modifier.padding(AppSpacing.md), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                Text(edition.label, style = MaterialTheme.typography.titleMedium)
                Text(edition.narratorName ?: "راوٍ غير محدد", color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    TextButton(onClick = { viewModel.renameEdition(edition, label) }) { Text("إعادة تسمية") }
                    TextButton(onClick = { viewModel.changeNarrator(edition, narrator) }) { Text("تغيير الراوي") }
                    TextButton(onClick = { viewModel.setDefaultEdition(edition.id) }) { Text(if (defaultId == edition.id) "افتراضي ✓" else "تعيين افتراضي") }
                }
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    TextButton(onClick = { viewModel.deleteEdition(edition) }) { Text("فصل") }
                    TextButton(onClick = { viewModel.splitEdition(edition) }) { Text("إنشاء نسخة") }
                }
            }
        }
    }
    Text("القرارات اليدوية محفوظة كقرارات authoritative ولا يكتب عليها Scan لاحقًا.", color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable private fun SectionTitle(text: String) { Text(text, style = MaterialTheme.typography.titleLarge) }
