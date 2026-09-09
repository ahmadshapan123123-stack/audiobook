package com.example.audiobook.presentation.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.MoreVert
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.example.audiobook.playback.PlaybackController
import com.example.audiobook.playback.SleepTimerController
import com.example.audiobook.playback.SleepTimerPhase
import android.widget.Toast
import androidx.compose.ui.platform.LocalContext
import com.example.audiobook.presentation.theme.AppSpacing
import com.example.audiobook.presentation.theme.AppThemeMode
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.distinctUntilChanged
import com.example.audiobook.domain.usecases.MarksCoordinator
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiobook.data.room.entity.BookmarkType
import java.util.UUID

@Composable
fun PlayerScreen(
    controller: PlaybackController,
    themeMode: AppThemeMode,
    marks: MarksCoordinator? = null,
    sleepTimer: SleepTimerController,
    onBack: () -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val playerUi by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val editionId: UUID? = playerUi.edition?.id ?: controller.state.value.editionId
    val playback by controller.state.collectAsState()
val sleepUi by sleepTimer.uiState.collectAsState()
    var sleepMinutes by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) {
        sleepTimer.messages.distinctUntilChanged().collect { message ->
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }
    LaunchedEffect(sleepUi.phase) {
        if (sleepUi.phase == SleepTimerPhase.IDLE || sleepUi.phase == SleepTimerPhase.STOPPED) sleepMinutes = 0
    }
    val scope = rememberCoroutineScope()
    val storedChapters by (if (editionId != null && marks != null) marks.chapters(editionId) else flowOf(emptyList())).collectAsState(initial = emptyList())
    val storedBookmarks by (if (editionId != null && marks != null) marks.bookmarks(editionId) else flowOf(emptyList())).collectAsState(initial = emptyList())
    var timeline by remember {
        mutableStateOf(
            PlayerTimelineState(
                durationMs = playback.durationMs,
                chapters = listOf(PlayerChapter(title = "البداية", startPositionMs = 0L)),
                bookmarks = emptyList()
            )
        )
    }
    var editing by remember { mutableStateOf(false) }
    var showMore by remember { mutableStateOf(false) }
    var selectedSpeed by remember { mutableStateOf(playback.speed) }
    var showMarkChoices by remember { mutableStateOf(false) }

    LaunchedEffect(editionId) { editionId?.let { controller.openEdition(it) } }
    LaunchedEffect(playback.durationMs) {
        if (playback.durationMs > 0L && timeline.durationMs != playback.durationMs) {
            timeline = timeline.copy(durationMs = playback.durationMs)
        }
    }
    val renderedTimeline = timeline.copy(
        chapters = if (marks == null) timeline.chapters else storedChapters.map { PlayerChapter(it.id, it.title ?: "فصل", it.startPositionMs) },
        bookmarks = if (marks == null) timeline.bookmarks else storedBookmarks.map { PlayerBookmark(it.id, it.positionMs, it.noteText) }
    )

    val gradient = PlayerGradientResolver.resolve(
        mode = themeMode,
        seriesColor = playerUi.seriesColor,
        authorColor = playerUi.authorColor,
        coverColor = playerUi.coverColor
    )

    Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(gradient.start, gradient.end)))) {
        Column(modifier = Modifier.fillMaxSize().padding(AppSpacing.lg), verticalArrangement = Arrangement.spacedBy(AppSpacing.md)) {
            Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Outlined.ArrowBack, "رجوع") }
                Column(modifier = Modifier.weight(1f)) {
                    Text(playerUi.title.ifBlank { "كتاب" }, style = MaterialTheme.typography.titleLarge)
                    Text(playerUi.authorName, style = MaterialTheme.typography.bodySmall)
                }
                Box {
                    IconButton(onClick = { showMore = true }) { Icon(Icons.Outlined.MoreVert, "المزيد") }
                    DropdownMenu(expanded = showMore, onDismissRequest = { showMore = false }) {
                        DropdownMenuItem(text = { Text("معلومات الكتاب") }, onClick = { showMore = false })
                        DropdownMenuItem(text = { Text("تغيير الإصدار") }, onClick = { showMore = false })
                        DropdownMenuItem(text = { Text("إدارة الكتاب") }, onClick = { showMore = false })
                    }
                }
            }

            Box(modifier = Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(AppSpacing.xs)).background(gradient.start), contentAlignment = Alignment.Center) {
                Text(playerUi.title.ifBlank { "غلاف الكتاب" }, color = Color.White, style = MaterialTheme.typography.headlineSmall)
            }
            Text("${formatTime(playback.positionMs)} / ${formatTime(playback.durationMs)}", style = MaterialTheme.typography.bodyMedium)
            LinearProgressIndicator(
                progress = { if (playback.durationMs == 0L) 0f else playback.positionMs.toFloat() / playback.durationMs },
                modifier = Modifier.fillMaxWidth()
            )

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                FilterChip(selected = renderedTimeline.level == TimelineLevel.OVERVIEW, onClick = { timeline = PlayerTimelineEditor.overview(timeline) }, label = { Text("نظرة عامة") })
                FilterChip(selected = renderedTimeline.level == TimelineLevel.ZOOMED, onClick = { timeline = PlayerTimelineEditor.zoom(timeline, playback.positionMs) }, label = { Text("تكبير ٣٠–٦٠ دقيقة") })
                TextButton(onClick = { editing = !editing }) { Text(if (editing) "إنهاء التحرير" else "تحرير الفصول") }
            }

            AnimatedContent(targetState = timeline.level, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "timeline-level") { level ->
                TimelineView(renderedTimeline, playback.positionMs, level, editing) { id, position ->
                    val stored = storedChapters.firstOrNull { it.id == id }
                    if (marks != null && stored != null) scope.launch { marks.updateChapter(stored, position) }
                    else timeline = PlayerTimelineEditor.moveChapter(timeline, id, position)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text("الفصل ${currentChapterNumber(renderedTimeline, playback.positionMs)}", style = MaterialTheme.typography.titleMedium)
                Button(onClick = {
                    timeline = PlayerTimelineEditor.markNow(timeline, playback.positionMs)
                    showMarkChoices = true
                }) { Text("Mark") }
            }
            if (showMarkChoices && timeline.markCaptureMs != null) {
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    TextButton(onClick = {
                        val captured = timeline.markCaptureMs!!
                        if (marks != null && editionId != null) scope.launch { marks.addBookmark(editionId, captured) }
                        timeline = PlayerTimelineEditor.consumeMark(timeline, "Bookmark", false); showMarkChoices = false
                    }) { Text("Bookmark") }
                    TextButton(onClick = {
                        val captured = timeline.markCaptureMs!!
                        if (marks != null && editionId != null) scope.launch { marks.addChapter(editionId, captured) }
                        timeline = PlayerTimelineEditor.consumeMark(timeline, "فصل جديد", true); showMarkChoices = false
                    }) { Text("Chapter") }
                    Text("تم التقاط ${formatTime(timeline.markCaptureMs!!)} فورًا", style = MaterialTheme.typography.bodySmall)
                }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { scope.launch { controller.previousChapter() } }) { Text("السابق") }
                TextButton(onClick = controller::skipBack15Seconds) { Text("-15s") }
                Button(onClick = { if (playback.isPlaying) controller.pause() else controller.play() }) { Text(if (playback.isPlaying) "إيقاف" else "تشغيل") }
                TextButton(onClick = controller::skipForward15Seconds) { Text("+15s") }
                TextButton(onClick = { scope.launch { controller.nextChapter() } }) { Text("التالي") }
            }

            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm), verticalAlignment = Alignment.CenterVertically) {
                Text("Speed ${"%.2f".format(selectedSpeed)}x")
                Slider(value = selectedSpeed, onValueChange = { selectedSpeed = it; controller.setSpeed(it) }, valueRange = .5f..3f, modifier = Modifier.weight(1f))
                TextButton(onClick = {
                    sleepMinutes = when {
                        sleepMinutes == 0 -> 15
                        sleepMinutes == 60 -> 0
                        else -> sleepMinutes + 15
                    }
                    if (sleepMinutes == 0) sleepTimer.cancel() else sleepTimer.start(sleepMinutes)
                }) {
                    val label = when (sleepUi.phase) {
                        SleepTimerPhase.IDLE, SleepTimerPhase.STOPPED -> "Sleep"
                        else -> formatTime(sleepUi.remainingMs ?: 0L)
                    }
                    Text(label)
                }
            }
            if (sleepUi.isExtendWindowVisible) {
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                    Text("مؤقت النوم سينتهي قريبًا", style = MaterialTheme.typography.bodySmall)
                    TextButton(onClick = { sleepTimer.extendBy(15) }) { Text("+15m") }
                    TextButton(onClick = { sleepTimer.extendBy(30) }) { Text("+30m") }
                }
            }
            playback.missingFileMessage?.let { Text(it, color = MaterialTheme.colorScheme.error) }
        }
    }
}

@Composable
private fun TimelineView(state: PlayerTimelineState, positionMs: Long, level: TimelineLevel, editing: Boolean, onChapterMoved: (UUID, Long) -> Unit) {
    val visibleStart = if (level == TimelineLevel.ZOOMED) (state.zoomCenterMs - 30 * 60_000L).coerceAtLeast(0L) else 0L
    val visibleEnd = if (level == TimelineLevel.ZOOMED) (state.zoomCenterMs + 30 * 60_000L).coerceAtMost(state.durationMs) else state.durationMs
    Column(modifier = Modifier.fillMaxWidth().height(88.dp).background(MaterialTheme.colorScheme.surface.copy(alpha = .75f), RoundedCornerShape(AppSpacing.xs)).padding(AppSpacing.sm)) {
        Text(if (level == TimelineLevel.OVERVIEW) "Timeline · الإصدار بالكامل" else "Timeline · ${formatTime(visibleStart)} – ${formatTime(visibleEnd)}", style = MaterialTheme.typography.bodySmall)
        Box(modifier = Modifier.fillMaxWidth().weight(1f).background(MaterialTheme.colorScheme.surfaceVariant, RoundedCornerShape(4.dp))) {
            state.bookmarks.forEach { bookmark -> Marker(x = markerFraction(bookmark.positionMs, visibleStart, visibleEnd), color = MaterialTheme.colorScheme.secondary, label = "•") }
            PlayerTimelineEditor.numbered(state).forEach { numbered ->
                ChapterMarkerView(numbered, markerFraction(numbered.chapter.startPositionMs, visibleStart, visibleEnd), editing, visibleStart, visibleEnd, onChapterMoved)
            }
            Text("●", modifier = Modifier.align(Alignment.CenterStart).padding(start = (markerFraction(positionMs, visibleStart, visibleEnd) * 100).dp), color = MaterialTheme.colorScheme.primary)
        }
    }
}

@Composable
private fun ChapterMarkerView(numbered: NumberedChapter, fraction: Float, editing: Boolean, visibleStart: Long, visibleEnd: Long, onMoved: (UUID, Long) -> Unit) {
    Text(
        "${numbered.number}",
        modifier = Modifier
            .padding(start = (fraction * 100).dp)
            .pointerInput(editing) {
                if (editing) detectDragGestures { change, dragAmount ->
                    change.consume()
                    val next = (visibleStart + ((fraction + dragAmount.x / 300f).coerceIn(0f, 1f) * (visibleEnd - visibleStart))).toLong()
                    onMoved(numbered.chapter.id, next)
                }
            },
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge
    )
}

@Composable
private fun Marker(x: Float, color: Color, label: String) { Text(label, modifier = Modifier.padding(start = (x * 100).dp), color = color) }

private fun markerFraction(position: Long, start: Long, end: Long): Float = if (end <= start) 0f else ((position - start).toFloat() / (end - start)).coerceIn(0f, 1f)
private fun currentChapterNumber(state: PlayerTimelineState, position: Long): Int = PlayerTimelineEditor.numbered(state).lastOrNull { it.chapter.startPositionMs <= position }?.number ?: 1
private fun formatTime(ms: Long): String = "%02d:%02d".format(ms / 60_000, (ms / 1_000) % 60)