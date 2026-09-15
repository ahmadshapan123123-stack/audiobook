package com.example.audiobook.presentation.statistics

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiobook.data.repository.DateRange
import com.example.audiobook.data.room.dao.ListeningHistoryRow
import com.example.audiobook.domain.statistics.BookListeningStat
import com.example.audiobook.domain.statistics.HabitStat
import com.example.audiobook.domain.statistics.InProgressStat
import com.example.audiobook.domain.statistics.ListeningBar
import com.example.audiobook.domain.statistics.PeriodOverview
import com.example.audiobook.domain.statistics.SpeedBucket
import com.example.audiobook.presentation.theme.AtherCoverBlock
import com.example.audiobook.presentation.theme.AppModeChip
import com.example.audiobook.presentation.theme.AppSpacing
import com.example.audiobook.presentation.theme.Cosmic
import com.example.audiobook.presentation.theme.CosmicScreenHeader
import com.example.audiobook.presentation.theme.minTouchTarget
import com.example.audiobook.presentation.theme.rememberHeaderCollapsed
import java.util.Locale
import java.util.UUID

/**
 * [R4-النقطة 3] شاشة Statistics الموسّعة — كل الأرقام محسوبة من جلسات الاستماع
 * الحقيقية في [StatisticsViewModel]: وقت الفترة، الرسم البسيط، الأكثر استماعًا،
 * تقدّمك، الإنجازات، عادات الاستماع، سرعة الاستماع، والسجل.
 */
@Composable
fun StatisticsScreen(
    onBack: () -> Unit,
    showBack: Boolean = true,
    onOpenBook: (UUID) -> Unit = {},
    onShowHistory: () -> Unit = {},
    viewModel: StatisticsViewModel = hiltViewModel()
) {
    val state by viewModel.statistics.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { viewModel.refresh() }
    val scroll = rememberScrollState()
    val collapsed = rememberHeaderCollapsed(scroll)

    Box(modifier = Modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(scroll)
                .padding(horizontal = 24.dp)
                .padding(top = 24.dp, bottom = 200.dp),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.lg)
        ) {
            CosmicScreenHeader(
                title = "إحصائياتك",
                subtitle = "نظرة على وقتك مع الحكايات.",
                collapsed = collapsed,
                onBack = if (showBack) onBack else null,
                backAsTextButton = true
            )
            PeriodChips(selected = state.selectedRange, onSelect = viewModel::selectRange)
            ListeningHero(overview = state.overview)
            ListeningBarsSection(bars = state.bars)
            CompactIndicators(state = state)
            TopBooksSection(books = state.topBooks, onOpenBook = onOpenBook)
            InProgressSection(books = state.inProgress, onOpenBook = onOpenBook)
            AchievementsSection(rows = state.achievements)
            HabitsSection(habits = state.habits, favoriteTime = state.favoriteTime)
            SpeedSection(
                average = state.averageSpeed,
                buckets = state.speedBuckets,
                sampleCount = state.speedSampleCount
            )
            HistorySection(rows = state.history, onShowHistory = onShowHistory, onOpenBook = onOpenBook)
        }
    }
}

@Composable
private fun PeriodChips(selected: DateRange, onSelect: (DateRange) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        val options = listOf(
            DateRange.WEEK to "هذا الأسبوع",
            DateRange.MONTH to "هذا الشهر",
            DateRange.YEAR to "هذا العام",
            DateRange.ALL to "الكل"
        )
        options.forEach { (range, label) ->
            AppModeChip(label = label, selected = selected == range, onClick = { onSelect(range) })
        }
    }
}

@Composable
private fun ListeningHero(overview: PeriodOverview) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        Text("وقت الاستماع", style = MaterialTheme.typography.titleMedium)
        Text(
            formatLongDuration(overview.listenedMs),
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.primary
        )
        if (overview.previousPeriodMs > 0L && overview.deltaMs != 0L) {
            val sign = if (overview.deltaMs > 0L) "+" else "−"
            Text(
                "${sign}${formatLongDuration(kotlin.math.abs(overview.deltaMs))} عن الفترة السابقة",
                style = MaterialTheme.typography.bodyMedium,
                color = if (overview.deltaMs > 0L) Cosmic.Teal else MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun ListeningBarsSection(bars: List<ListeningBar>) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Text("استماعك يومًا بيوم", style = MaterialTheme.typography.titleMedium)
        if (bars.isEmpty()) {
            Text("لا توجد بيانات بعد في هذه الفترة", style = MaterialTheme.typography.bodySmall)
        } else {
            val maxValue = bars.maxOfOrNull { it.valueMs } ?: 0L
            Row(
                modifier = Modifier.fillMaxWidth().height(140.dp),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
                verticalAlignment = Alignment.Bottom
            ) {
                bars.forEach { bar ->
                    val fraction = if (maxValue > 0L) (bar.valueMs.toFloat() / maxValue).coerceIn(0f, 1f) else 0f
                    Column(
                        modifier = Modifier.weight(1f).fillMaxHeight(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Bottom
                    ) {
                        Text(
                            if (bar.valueMs > 0L) formatShortDuration(bar.valueMs) else "",
                            style = MaterialTheme.typography.labelSmall,
                            maxLines = 1
                        )
                        Spacer(Modifier.height(AppSpacing.xxs))
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((if (fraction > 0f) (14 + fraction * 80).dp else 3.dp))
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (fraction > 0f) Cosmic.TealBright.copy(alpha = 0.75f)
                                    else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                                )
                        )
                        Spacer(Modifier.height(AppSpacing.xxs))
                        Text(
                            bar.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactIndicators(state: StatisticsUiState) {
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Text(state.periodLabel, style = MaterialTheme.typography.titleMedium)
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            CompactStat(value = state.overview.sessionsCount, label = "جلسات", modifier = Modifier.weight(1f))
            CompactStat(value = state.overview.booksCount, label = "كتب", modifier = Modifier.weight(1f))
            CompactStat(value = state.overview.chaptersCount, label = "فصلًا", modifier = Modifier.weight(1f))
            CompactStat(value = state.overview.daysCount, label = "أيام", modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun CompactStat(value: Int, label: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(value.toString(), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun TopBooksSection(books: List<BookListeningStat>, onOpenBook: (UUID) -> Unit) {
    if (books.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Text("كتبك", style = MaterialTheme.typography.titleMedium)
        Text(
            "الأكثر استماعًا",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        books.forEach { book -> TopBookRow(book = book, onOpenBook = onOpenBook) }
    }
}

@Composable
private fun TopBookRow(book: BookListeningStat, onOpenBook: (UUID) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().minTouchTarget().clip(RoundedCornerShape(AppSpacing.sm))
            .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
            .clickable { onOpenBook(book.bookId) }
            .padding(AppSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        AtherCoverBlock(title = book.title, coverColor = coverColorOf(book.coverColorTheme), modifier = Modifier.size(52.dp))
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
            Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            val subtitle = listOfNotNull(book.authorName, book.seriesName).joinToString(" · ")
            if (subtitle.isNotBlank()) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
        Text(formatSessionCount(book.sessionCount), style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun InProgressSection(books: List<InProgressStat>, onOpenBook: (UUID) -> Unit) {
    if (books.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Text("تقدّمك", style = MaterialTheme.typography.titleMedium)
        books.forEach { book ->
            val percent = (book.fraction * 100).toInt()
            Column(
                modifier = Modifier.fillMaxWidth().minTouchTarget().clip(RoundedCornerShape(AppSpacing.sm))
                    .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.7f))
                    .clickable { onOpenBook(book.bookId) }
                    .padding(AppSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.md), verticalAlignment = Alignment.CenterVertically) {
                    AtherCoverBlock(title = book.title, coverColor = coverColorOf(book.coverColorTheme), modifier = Modifier.size(52.dp))
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
                        Text(book.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text("بقي ${formatShortDuration(book.remainingMs)}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Text("$percent٪", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Bold)
                }
                LinearProgressIndicator(
                    progress = { book.fraction },
                    modifier = Modifier.fillMaxWidth().height(8.dp),
                    color = Cosmic.TealBright,
                    trackColor = MaterialTheme.colorScheme.surfaceVariant
                )
            }
        }
    }
}

@Composable
private fun AchievementsSection(rows: List<AchievementRow>) {
    if (rows.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Text("إنجازاتك", style = MaterialTheme.typography.titleMedium)
        rows.forEach { row ->
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
                Text(row.label, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                Text(row.value, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

@Composable
private fun HabitsSection(habits: List<HabitStat>, favoriteTime: String?) {
    if (habits.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Text("عادات الاستماع", style = MaterialTheme.typography.titleMedium)
        val maxValue = habits.maxOfOrNull { it.listenedMs } ?: 0L
        habits.forEach { habit ->
            val fraction = if (maxValue > 0L) (habit.listenedMs.toFloat() / maxValue).coerceIn(0f, 1f) else 0f
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(habit.label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.width(64.dp))
                Box(
                    modifier = Modifier.weight(1f).height(10.dp).clip(RoundedCornerShape(5.dp))
                        .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f))
                ) {
                    Box(
                        modifier = Modifier.fillMaxHeight().fillMaxWidth(fraction)
                            .clip(RoundedCornerShape(5.dp))
                            .background(Cosmic.TealBright.copy(alpha = 0.75f))
                    )
                }
                Text(formatShortDuration(habit.listenedMs), style = MaterialTheme.typography.bodySmall)
            }
        }
        favoriteTime?.let {
            Text("وقت استماعك المفضل: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun SpeedSection(average: Float, buckets: List<SpeedBucket>, sampleCount: Int) {
    if (average <= 0f && buckets.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Text("سرعة الاستماع", style = MaterialTheme.typography.titleMedium)
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.md), verticalAlignment = Alignment.CenterVertically) {
            Text(
                if (average > 0f) "${String.format(Locale.US, "%.2f", average)}×" else "—",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            if (buckets.isNotEmpty()) {
                Text(
                    buckets.joinToString(" · ") { "${it.label} ${it.count}" },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
        if (sampleCount in 1..2) {
            Text(
                "بيانات قليلة: كلما استمعت أكثر تظهر سرعتك بدقة أكبر",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun HistorySection(rows: List<ListeningHistoryRow>, onShowHistory: () -> Unit, onOpenBook: (UUID) -> Unit) {
    if (rows.isEmpty()) return
    Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Text("سجل الاستماع", style = MaterialTheme.typography.titleMedium)
        val grouped = GroupedHistory(rows.take(12))
        grouped.forEach { (dayLabel, dayRows) ->
            Column(modifier = Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                Text(dayLabel, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary)
                dayRows.forEach { row ->
                    val bookId = row.bookId
                    Row(
                        modifier =Modifier.fillMaxWidth().then(
                            if (bookId != null) Modifier.clickable { onOpenBook(bookId) }.minTouchTarget() else Modifier
                        ),
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            row.bookTitle ?: row.editionLabel ?: "نسخة محذوفة",
                            style = MaterialTheme.typography.bodyMedium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Text(formatShortDuration(row.durationListenedMs), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
                HorizontalDivider()
            }
        }
        TextButton(onClick = onShowHistory, modifier = Modifier.fillMaxWidth().minTouchTarget()) {
            Text("عرض السجل كاملًا")
        }
    }
}

private fun coverColorOf(colorTheme: String?): Color {
    val parsed = runCatching { android.graphics.Color.parseColor(colorTheme) }.getOrNull()
    return if (parsed != null) Color(parsed.toLong() or 0xFF000000) else Color(0xFF356B68)
}

private fun formatSessionCount(count: Int): String = when (count) {
    1 -> "مرة واحدة"
    2 -> "مرتان"
    else -> "$count مرات"
}

private fun formatShortDuration(ms: Long): String {
    val totalMinutes = ms / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return if (hours > 0L) "${hours}س ${minutes}د" else "${minutes}د"
}

private fun formatLongDuration(ms: Long): String {
    val totalMinutes = ms / 60_000L
    val hours = totalMinutes / 60L
    val minutes = totalMinutes % 60L
    return when {
        hours > 0L && minutes > 0L -> "${hours} ساعة و${minutes} دقيقة"
        hours > 0L -> "${hours} ساعة"
        else -> "${minutes} دقيقة"
    }
}

private fun GroupedHistory(rows: List<ListeningHistoryRow>): List<Pair<String, List<ListeningHistoryRow>>> {
    val today = com.example.audiobook.domain.statistics.StatisticsDates.dayNumber(System.currentTimeMillis())
    val yesterday = today - 1
    val grouped = rows.groupBy { row ->
        com.example.audiobook.domain.statistics.StatisticsDates.dayNumber(row.startedAt)
    }
    return grouped.toSortedMap(compareByDescending { it }).map { (day, dayRows) ->
        val dayLabel = when (day) {
            today -> "اليوم"
            yesterday -> "أمس"
            else -> com.example.audiobook.domain.statistics.arabicDayName(day)
        }
        dayLabel to dayRows
    }
}