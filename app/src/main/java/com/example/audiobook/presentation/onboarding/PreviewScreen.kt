package com.example.audiobook.presentation.onboarding

import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ExpandLess
import androidx.compose.material.icons.outlined.ExpandMore
import androidx.compose.material.icons.outlined.Folder
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.audiobook.R
import com.example.audiobook.domain.usecases.StrictFolderClassifier.PreviewAuthor
import com.example.audiobook.domain.usecases.StrictFolderClassifier.PreviewBook
import com.example.audiobook.domain.usecases.StrictFolderClassifier.PreviewSeries
import com.example.audiobook.domain.usecases.StrictFolderClassifier.PreviewTree
import com.example.audiobook.presentation.common.cleanDisplayTitle

/**
 * صفّ واحد في شجرة المعاينة بعد التسطيح — يعرضها [LazyColumn] كقائمة، بدل
 * [Column] بتكرار متداخل يبني كل الصفوف دفعةً واحدة.
 */
private sealed interface PreviewNode {
    val key: String

    data class AuthorNode(val author: PreviewAuthor, val expanded: Boolean) : PreviewNode {
        override val key: String get() = "a:${author.name}"
    }

    data class SeriesNode(
        val author: PreviewAuthor,
        val series: PreviewSeries,
        val expanded: Boolean
    ) : PreviewNode {
        override val key: String get() = "s:${author.name}/${series.name}"
    }

    data class BookNode(val book: PreviewBook, val indent: Int) : PreviewNode {
        override val key: String get() = "b:$indent:${book.folderPath}/${book.title}"
    }

    /** عنوان قسم الكتب غير المنسوبة إلى مؤلف/سلسلة. */
    data class UnassignedHeaderNode(val label: String) : PreviewNode {
        override val key: String get() = "u:$label"
    }
}

/**
 * تسطيح الشجرة إلى قائمة صفوف حسب حالة الطيّ. مكتبة حقيقية فيها سلسلة
 * بمئة كتاب مئة صف، فبناء Compose لكل صف دفعةً واحدة (كما كان في [Column])
 * كان يجمّد الشاشة قبل أن تُرسم.
 */
private fun buildPreviewNodes(
    tree: PreviewTree,
    expandedAuthors: Set<String>,
    expandedSeries: Set<String>,
    unassignedLabel: String
): List<PreviewNode> {
    val nodes = ArrayList<PreviewNode>(tree.totalFiles.coerceAtMost(4096))
    if (tree.unassignedBooks.isNotEmpty()) {
        nodes += PreviewNode.UnassignedHeaderNode(unassignedLabel)
        tree.unassignedBooks.forEach { book -> nodes += PreviewNode.BookNode(book, indent = 0) }
    }
    tree.authors.forEach { author ->
        val authorOpen = author.name in expandedAuthors
        nodes += PreviewNode.AuthorNode(author, authorOpen)
        if (!authorOpen) return@forEach
        author.series.forEach { series ->
            val seriesKey = "${author.name}/${series.name}"
            val seriesOpen = seriesKey in expandedSeries
            nodes += PreviewNode.SeriesNode(author, series, seriesOpen)
            if (!seriesOpen) return@forEach
            series.books.forEach { book -> nodes += PreviewNode.BookNode(book, indent = 2) }
        }
        author.books.forEach { book -> nodes += PreviewNode.BookNode(book, indent = 1) }
    }
    return nodes
}

/**
 * عرض شجرة المعاينة (المرحلة 5): مؤلف ← (سلسلة) ← كتاب، مع طي/فتح لكل عقدة.
 * قراءة دائمًا؛ إذا مُرِّرت دوال فعل تُفعَّل نقرة العقدة (شاشة التعديل)،
 * وإلا فهي شاشة اعتماد (تأكيد) بلا أي تعديل.
 */
@Composable
fun PreviewTreeList(
    tree: PreviewTree,
    modifier: Modifier = Modifier,
    initiallyExpanded: Boolean = true,
    onAuthorClick: ((PreviewAuthor) -> Unit)? = null,
    onSeriesClick: ((PreviewAuthor, PreviewSeries) -> Unit)? = null,
    onBookClick: ((PreviewBook) -> Unit)? = null
) {
    val unassignedLabel = stringResource(R.string.preview_unassigned)
    val filesLabel = stringResource(R.string.preview_book_files)

    // حالة الطيّ في مجموعتين بدل state لكل عقدة: مع آلاف الكتب كان
    // remember لكل صف يثقل التكوين، والنقر يعيد بناء القائمة كلها.
    var expandedAuthors by remember(tree) {
        mutableStateOf(if (initiallyExpanded) tree.authors.map { it.name }.toSet() else emptySet())
    }
    var expandedSeries by remember(tree) {
        mutableStateOf(
            if (initiallyExpanded) {
                tree.authors.flatMap { author -> author.series.map { "${author.name}/${it.name}" } }.toSet()
            } else {
                emptySet()
            }
        )
    }
    val nodes = remember(tree, expandedAuthors, expandedSeries, unassignedLabel) {
        buildPreviewNodes(tree, expandedAuthors, expandedSeries, unassignedLabel)
    }

    LazyColumn(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(nodes, key = { it.key }) { node ->
            when (node) {
                is PreviewNode.UnassignedHeaderNode -> SectionHeader(node.label)

                is PreviewNode.AuthorNode -> AuthorRow(
                    author = node.author,
                    filesLabel = filesLabel,
                    expanded = node.expanded,
                    onClick = { expandedAuthors = expandedAuthors.toggle(node.author.name) },
                    onRowAction = onAuthorClick?.let { { onAuthorClick(node.author) } }
                )

                is PreviewNode.SeriesNode -> SeriesRow(
                    series = node.series,
                    filesLabel = filesLabel,
                    expanded = node.expanded,
                    indent = 1,
                    onClick = {
                        expandedSeries = expandedSeries.toggle("${node.author.name}/${node.series.name}")
                    },
                    onRowAction = onSeriesClick?.let { { onSeriesClick(node.author, node.series) } }
                )

                is PreviewNode.BookNode -> BookRow(
                    book = node.book,
                    filesLabel = filesLabel,
                    indent = node.indent,
                    onClick = onBookClick?.let { { onBookClick(node.book) } }
                )
            }
        }
    }
}

private fun Set<String>.toggle(value: String): Set<String> =
    if (value in this) this - value else this + value

@Composable
private fun AuthorRow(
    author: PreviewAuthor,
    filesLabel: String,
    expanded: Boolean,
    onClick: () -> Unit,
    onRowAction: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = onRowAction != null, onClickLabel = null, onClick = onRowAction ?: onClick)
            .padding(vertical = 6.dp, horizontal = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(18.dp)
        )
        Icon(
            imageVector = Icons.Outlined.Folder,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(20.dp).padding(start = 4.dp)
        )
        Text(
            text = cleanDisplayTitle(author.name),
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.SemiBold,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f).padding(start = 8.dp)
        )
        Text(
            text = filesLabel.format(author.fileCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SeriesRow(
    series: PreviewSeries,
    filesLabel: String,
    expanded: Boolean,
    indent: Int,
    onClick: () -> Unit,
    onRowAction: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (16 + indent * 16).dp, end = 8.dp)
            .clickable(enabled = onRowAction != null, onClickLabel = null, onClick = onRowAction ?: onClick)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (expanded) Icons.Outlined.ExpandLess else Icons.Outlined.ExpandMore,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.size(16.dp)
        )
        Icon(
            imageVector = Icons.Outlined.MenuBook,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.tertiary,
            modifier = Modifier.size(16.dp)
        )
        Text(
            text = cleanDisplayTitle(series.name),
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium,
            color = MaterialTheme.colorScheme.onBackground,
            modifier = Modifier.weight(1f).padding(start = 8.dp)
        )
        Text(
            text = filesLabel.format(series.fileCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@Composable
private fun SectionHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.SemiBold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 4.dp)
    )
}

@Composable
private fun BookRow(
    book: PreviewBook,
    filesLabel: String,
    indent: Int,
    onClick: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = (32 + indent * 16).dp, end = 8.dp)
            .clickable(onClick = onClick ?: {})
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = "•",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = cleanDisplayTitle(book.title),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.weight(1f).padding(start = 8.dp)
        )
        Text(
            text = filesLabel.format(book.fileCount),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
