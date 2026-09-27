package com.example.audiobook.presentation.onboarding

import androidx.compose.foundation.clickable
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
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        val unassignedLabel = stringResource(R.string.preview_unassigned)
        val filesLabel = stringResource(R.string.preview_book_files)

        tree.unassignedBooks.forEach { book ->
            BookRow(
                book = book,
                filesLabel = filesLabel,
                indent = 0,
                onClick = onBookClick?.let { { onBookClick(book) } }
            )
        }

        tree.authors.forEach { author ->
            var authorExpanded by remember { mutableStateOf(initiallyExpanded) }
            AuthorRow(
                author = author,
                filesLabel = filesLabel,
                expanded = authorExpanded,
                onClick = { authorExpanded = !authorExpanded },
                onRowAction = onAuthorClick?.let { { onAuthorClick(author) } }
            )
            if (authorExpanded) {
                author.series.forEach { series ->
                    var seriesExpanded by remember { mutableStateOf(initiallyExpanded) }
                    SeriesRow(
                        series = series,
                        filesLabel = filesLabel,
                        expanded = seriesExpanded,
                        indent = 1,
                        onClick = { seriesExpanded = !seriesExpanded },
                        onRowAction = onSeriesClick?.let { { onSeriesClick(author, series) } }
                    )
                    if (seriesExpanded) {
                        series.books.forEach { book ->
                            BookRow(
                                book = book,
                                filesLabel = filesLabel,
                                indent = 2,
                                onClick = onBookClick?.let { { onBookClick(book) } }
                            )
                        }
                    }
                }
                author.books.forEach { book ->
                    BookRow(
                        book = book,
                        filesLabel = filesLabel,
                        indent = 1,
                        onClick = onBookClick?.let { { onBookClick(book) } }
                    )
                }
            }
        }
    }
}

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
            text = author.name,
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
            text = series.name,
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
            text = book.title,
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