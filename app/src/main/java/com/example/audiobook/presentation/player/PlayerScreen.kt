package com.example.audiobook.presentation.player

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiobook.R
import com.example.audiobook.playback.PlaybackController
import com.example.audiobook.playback.SleepTimerController
import com.example.audiobook.playback.SleepTimerPhase
import com.example.audiobook.presentation.theme.AppProgressSlider
import com.example.audiobook.presentation.theme.AppSpacing
import com.example.audiobook.presentation.theme.AppThemeMode
import com.example.audiobook.presentation.theme.Cosmic
import com.example.audiobook.presentation.theme.LocalCosmicHeader
import com.example.audiobook.presentation.theme.minTouchTarget
import com.example.audiobook.presentation.theme.navBarGlassStyle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.rememberHazeState
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import com.example.audiobook.domain.usecases.MarksCoordinator
import java.util.UUID

@Composable
fun PlayerScreen(
    controller: PlaybackController,
    themeMode: AppThemeMode,
    marks: MarksCoordinator? = null,
    sleepTimer: SleepTimerController,
    initialPositionMs: Long = -1L,
    onBack: () -> Unit = {},
    viewModel: PlayerViewModel = hiltViewModel()
) {
    val playerUi by viewModel.uiState.collectAsStateWithLifecycle()
    val playback by controller.state.collectAsState()
    val editionId: UUID? = playerUi.edition?.id ?: playback.editionId
    val sleepUi by sleepTimer.uiState.collectAsState()
    val scope = rememberCoroutineScope()
    val storedChapters by (if (editionId != null && marks != null) marks.chapters(editionId) else flowOf(emptyList())).collectAsState(initial = emptyList())
    val storedBookmarks by (if (editionId != null && marks != null) marks.bookmarks(editionId) else flowOf(emptyList())).collectAsState(initial = emptyList())
    val cosmicHeader = LocalCosmicHeader.current
    SideEffect { cosmicHeader.reset() }
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
    var selectedSpeed by remember { mutableStateOf(playback.speed) }
    var expandedPanel by remember { mutableStateOf<PlayerControlPanel?>(null) }

    LaunchedEffect(editionId) {
        editionId?.let { controller.openEdition(it) }
        if (initialPositionMs >= 0L) controller.seekTo(initialPositionMs)
    }
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
    val hazeState = rememberHazeState()
    val visibleWindow = visibleWindowMs(renderedTimeline, playback.positionMs)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .haze(hazeState)
    ) {
        // ---- خلفية "غبار النجوم": التدرج الديناميكي محفوظ + سدم نجمية ----
        Box(modifier = Modifier.fillMaxSize()) {
            Box(modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(gradient.start, gradient.end))))
            PlayerStardustBackdrop(modifier = Modifier.fillMaxSize(), mode = themeMode)
        }

        // ---- عمود واحد ثابت (بلا تمرير): TOP → MAIN → TIMELINE → تحكّم متصل ----
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = AppSpacing.md)
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
        ) {
            // TOP: رجوع + عنوان/مؤلف بخط مضغوط
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AppSpacing.xs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack, modifier = Modifier.minTouchTarget()) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.player_back), tint = Color.White)
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(playerUi.title.ifBlank { stringResource(R.string.player_cover_placeholder) }, style = MaterialTheme.typography.titleMedium, color = Color.White, maxLines = 1)
                    Text(playerUi.authorName, style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.75f), maxLines = 1)
                }
            }

            // MAIN CONTENT: غلاف مضغوط + الفصل الحالي — منطقة مرنة تتمدد وتنكمش بلا فراغ ثابت
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Spacer(Modifier.weight(1f))
                PlayerCoverBlock(
                    title = playerUi.title.ifBlank { stringResource(R.string.player_cover_placeholder) },
                    gradient = gradient,
                    modifier = Modifier.fillMaxWidth().height(176.dp)
                )
                Text(
                    text = currentChapterLabel(renderedTimeline, playback.positionMs),
                    style = MaterialTheme.typography.labelLarge,
                    color = Cosmic.TealBright.copy(alpha = 0.95f),
                    maxLines = 1,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.lg)
                )
                Spacer(Modifier.weight(1f))
            }

            playback.missingFileMessage?.let { message ->
                Text(message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }

            // TIMELINE: الخيط الزمني الموحّد وعلاماته (فصول/إشارات) فوق أدوات التشغيل دائمًا
            PlayerTimelineBar(
                state = renderedTimeline,
                positionMs = playback.positionMs,
                durationMs = playback.durationMs,
                visibleWindow = visibleWindow,
                editing = editing,
                onScrub = { target -> controller.seekTo(target) },
                onLevelChange = { level ->
                    timeline = if (level == TimelineLevel.ZOOMED) PlayerTimelineEditor.zoom(timeline, playback.positionMs)
                    else PlayerTimelineEditor.overview(timeline)
                },
                onChapterMoved = { id, position ->
                    val stored = storedChapters.firstOrNull { it.id == id }
                    if (marks != null && stored != null) scope.launch { marks.updateChapter(stored, position) }
                    else timeline = PlayerTimelineEditor.moveChapter(timeline, id, position)
                }
            )

            // PRIMARY + SECONDARY: منطقة تحكّم واحدة متصلة أسفل الشاشة
            AnimatedVisibility(
                visible = expandedPanel != null,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                ControlPanel(
                    panel = expandedPanel,
                    sleepUi = sleepUi,
                    selectedSpeed = selectedSpeed,
                    onSpeedChange = { value ->
                        selectedSpeed = value
                        controller.setSpeed(value)
                    },
                    onSleepStart = { minutes -> sleepTimer.start(minutes) },
                    onSleepExtend = { minutes -> sleepTimer.extendBy(minutes) },
                    onSleepCancel = { sleepTimer.cancel() },
                    onAddChapter = {
                        if (editionId != null && marks != null) scope.launch { marks.addChapter(editionId, playback.positionMs) }
                    },
                    onToggleEditing = { editing = !editing },
                    editing = editing,
                    modifier = Modifier.padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs)
                )
            }

            PlayerControlsBar(
                isPlaying = playback.isPlaying,
                onPrevious = { scope.launch { controller.previousChapter() } },
                onSkipBack = controller::skipBack15Seconds,
                onTogglePlay = { if (playback.isPlaying) controller.pause() else controller.play() },
                onSkipForward = controller::skipForward15Seconds,
                onNext = { scope.launch { controller.nextChapter() } },
                onMark = {
                    if (editionId != null && marks != null) scope.launch { marks.addBookmark(editionId, playback.positionMs) }
                },
                activePanel = expandedPanel,
                onTogglePanel = { panel -> expandedPanel = if (expandedPanel == panel) null else panel },
                haze = hazeState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.md)
                    .padding(bottom = AppSpacing.xs)
            )
        }
    }
}

private enum class PlayerControlPanel { SPEED, SLEEP, CHAPTERS }

private fun visibleWindowMs(state: PlayerTimelineState, positionMs: Long): LongRange {
    if (state.level != TimelineLevel.ZOOMED || state.durationMs <= 0L) return 0L..state.durationMs.coerceAtLeast(1L)
    val half = 30 * 60_000L
    val start = (positionMs - half).coerceIn(0L, state.durationMs)
    val end = (positionMs + half).coerceIn(start, state.durationMs)
    return start..end
}

@Composable
private fun currentChapterLabel(state: PlayerTimelineState, positionMs: Long): String {
    val numbered = PlayerTimelineEditor.numbered(state)
    val chapter = numbered.lastOrNull { it.chapter.startPositionMs <= positionMs }
    val title = chapter?.chapter?.title?.take(40)?.trim().orEmpty()
    if (chapter == null || title.isEmpty()) return stringResource(R.string.player_cover_placeholder)
    val num = stringResource(R.string.player_chapter_num, chapter.number)
    return "$num · $title"
}

/** غلاف الـPlayer المضغوط: حرف أول فقط فوق التدرج السديمي — العنوان في الـheader، لا ازدواج. */
@Composable
private fun PlayerCoverBlock(title: String, gradient: PlayerGradient, modifier: Modifier = Modifier) {
    val shape = RoundedCornerShape(AppSpacing.lg)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        gradient.start,
                        Cosmic.StardustViolet.copy(alpha = if (gradient.start.luminance() > 0.55f) 0.55f else 0.85f),
                        gradient.end
                    )
                ),
                shape
            )
            .border(width = 1.dp, color = Color.White.copy(alpha = 0.14f), shape = shape),
        contentAlignment = Alignment.Center
    ) {
        Box(
            modifier = Modifier.fillMaxSize().background(
                Brush.verticalGradient(
                    colors = listOf(
                        Color.White.copy(alpha = 0.10f),
                        Color.Transparent,
                        Color.Black.copy(alpha = 0.10f)
                    )
                )
            )
        )
        val letter = title.trim().firstOrNull()?.toString() ?: "؟"
        Text(
            text = letter,
            color = Color.White,
            style = MaterialTheme.typography.displayMedium,
            fontWeight = FontWeight.Bold
        )
    }
}

/** عنصر Timeline واحد: الشريط نفسه عليه علامات الفصول + Tooltip يتبع اللمس، وتبديل Overview/Zoomed واحد. */
@Composable
private fun PlayerTimelineBar(
    state: PlayerTimelineState,
    positionMs: Long,
    durationMs: Long,
    visibleWindow: LongRange,
    editing: Boolean,
    onScrub: (Long) -> Unit,
    onLevelChange: (TimelineLevel) -> Unit,
    onChapterMoved: (UUID, Long) -> Unit
) {
    val positionFraction = if (visibleWindow.last <= visibleWindow.first) 0f
    else ((positionMs - visibleWindow.first).toFloat() / (visibleWindow.last - visibleWindow.first)).coerceIn(0f, 1f)
    var scrubTooltip by remember { mutableStateOf<String?>(null) }

    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
        // ---- التبديل الزجاجي الموحّد: عنصر واحد يبدّل نظرة عامة/تكبير على نفس الشريط ----
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.xxs),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            TimelineModeToggle(
                labels = listOf(
                    stringResource(R.string.player_timeline_overview) to TimelineLevel.OVERVIEW,
                    stringResource(R.string.player_timeline_zoom) to TimelineLevel.ZOOMED
                ),
                selected = state.level,
                onSelect = onLevelChange
            )
            Spacer(Modifier.weight(1f))
            Text(
                stringResource(R.string.player_chapter_num, currentChapterNumber(state, positionMs)),
                style = MaterialTheme.typography.labelMedium,
                color = Color.White.copy(alpha = 0.85f)
            )
        }

        // ---- الوقت على نفس العنصر: لا صندوق منفصل تاني يعرض الموضع/الفصل ----
        Row(modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.xs), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(formatTime(positionMs), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.88f))
            Text(formatTime(durationMs), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.55f))
        }

        // ---- الشريط الفعلي (Scrub حقيقي لـPlaybackController) + العلامات فوقه ----
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            val barWidthPx = maxWidth
            AppProgressSlider(
                value = positionFraction,
                onValueChange = { fraction ->
                    val target = visibleWindow.first + (fraction * (visibleWindow.last - visibleWindow.first)).toLong()
                    scrubTooltip = tooltipForPosition(state, target)
                    onScrub(target)
                },
                onValueChangeFinished = { scrubTooltip = null }
            )
            if (!editing) {
                TimelineMarksCanvas(
                    state = state,
                    visibleWindow = visibleWindow,
                    editing = false
                )
                if (scrubTooltip != null) {
                    TooltipChip(
                        text = scrubTooltip,
                        modifier = Modifier.align(Alignment.TopCenter).padding(top = 2.dp)
                    )
                }
            } else {
                TimelineMarksCanvas(state = state, visibleWindow = visibleWindow, editing = true)
                EditingChapterDragLayer(
                    state = state,
                    visibleWindow = visibleWindow,
                    onChapterMoved = onChapterMoved
                )
            }
        }

        state.markCaptureMs?.let { captured ->
            Text(
                stringResource(R.string.player_mark_captured, formatTime(captured)),
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.85f),
                modifier = Modifier.padding(horizontal = AppSpacing.xs)
            )
        }
    }
}

private fun tooltipForPosition(state: PlayerTimelineState, positionMs: Long): String {
    val chapter = state.chapters.sortedBy { it.startPositionMs }.lastOrNull { it.startPositionMs <= positionMs }
    val label = chapter?.title?.take(24) ?: "فصل"
    return "$label — ${formatTime(positionMs)}"
}

/** طبقة لاصقة لا تلتقط اللمس إطلاقًا (الـSlider يبقى هو المسؤول عن السحب). */
@Composable
private fun TimelineMarksCanvas(
    state: PlayerTimelineState,
    visibleWindow: LongRange,
    editing: Boolean
) {
    Canvas(modifier = Modifier.fillMaxWidth().height(34.dp)) {
        val trackY = size.height * 0.45f
        val drawableStart = 12.dp.toPx()
        val drawableEnd = size.width - drawableStart
        val span = drawableEnd - drawableStart
        state.chapters.sortedBy { it.startPositionMs }.forEach { chapter ->
            val f = mkFraction(chapter.startPositionMs, visibleWindow)
            if (f in 0f..1f) {
                val x = drawableStart + f * span
                drawLine(
                    color = if (editing) Cosmic.StardustAmber.copy(alpha = 0.95f) else Cosmic.MoonIce.copy(alpha = 0.80f),
                    start = Offset(x, trackY - 7.dp.toPx()),
                    end = Offset(x, trackY + 7.dp.toPx()),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
        state.bookmarks.forEach { bookmark ->
            val f = mkFraction(bookmark.positionMs, visibleWindow)
            if (f in 0f..1f) {
                val x = drawableStart + f * span
                drawCircle(
                    color = Cosmic.StardustMagenta.copy(alpha = 0.9f),
                    radius = 3.dp.toPx(),
                    center = Offset(x, trackY)
                )
            }
        }
    }
}

/** وضع التحرير فقط: اسحب أقرب علامة فصل لتحريك بدايتها — ويبقى السحب على الشريط سليمًا في الوضع العادي. */
@Composable
private fun BoxWithConstraintsScope.EditingChapterDragLayer(
    state: PlayerTimelineState,
    visibleWindow: LongRange,
    onChapterMoved: (UUID, Long) -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .pointerInput(state) {
                detectDragGestures(
                    onDragStart = { start ->
                        val f = (start.x / size.width).coerceIn(0f, 1f)
                        val t = visibleWindow.first + (f * (visibleWindow.last - visibleWindow.first)).toLong()
                        val chapter = state.chapters.sortedBy { it.startPositionMs }
                            .minByOrNull { kotlin.math.abs(it.startPositionMs - t) }
                        if (chapter != null) onChapterMoved(chapter.id, t.coerceIn(visibleWindow.first, visibleWindow.last))
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val f = (change.position.x / size.width).coerceIn(0f, 1f)
                        val t = visibleWindow.first + (f * (visibleWindow.last - visibleWindow.first)).toLong()
                        val chapter = state.chapters.sortedBy { it.startPositionMs }
                            .minByOrNull { kotlin.math.abs(it.startPositionMs - t) }
                        if (chapter != null) onChapterMoved(chapter.id, t.coerceIn(visibleWindow.first, visibleWindow.last))
                    }
                )
            }
    )
}

@Composable
private fun TooltipChip(text: String?, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(Cosmic.InkBottom.copy(alpha = 0.92f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp)
    ) {
        Text(text ?: "", style = MaterialTheme.typography.bodySmall, color = Color.White)
    }
}

/** عنصر تبديل زجاجي واحد متسق (زراران في شريط واحد، لا ستايلين مختلفين). */
@Composable
private fun TimelineModeToggle(
    labels: List<Pair<String, TimelineLevel>>,
    selected: TimelineLevel,
    onSelect: (TimelineLevel) -> Unit
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(width = 1.dp, color = Color.White.copy(alpha = 0.12f), shape = shape)
            .padding(3.dp)
    ) {
        labels.forEach { (label, level) ->
            val isSelected = level == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) Cosmic.Teal.copy(alpha = 0.55f) else Color.Transparent)
                    .minTouchTarget()
                    .clickable(onClick = { onSelect(level) })
                    .padding(horizontal = AppSpacing.sm, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.75f),
                    maxLines = 1
                )
            }
        }
    }
}

private fun mkFraction(positionMs: Long, window: LongRange): Float {
    if (window.last <= window.first) return 0f
    return ((positionMs - window.first).toFloat() / (window.last - window.first)).coerceIn(0f, 1f)
}

/** شريط التحكم السفلي الزجاجي العFloating — عناصر قسم 10 حرفيًا ولا أكثر. */
@Composable
private fun PlayerControlsBar(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onSkipBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSkipForward: () -> Unit,
    onNext: () -> Unit,
    onMark: () -> Unit,
    activePanel: PlayerControlPanel?,
    onTogglePanel: (PlayerControlPanel) -> Unit,
    haze: HazeState,
    modifier: Modifier = Modifier
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(28.dp))
            .background(Cosmic.NavBarBlue.copy(alpha = 0.88f), RoundedCornerShape(28.dp))
            .border(width = 1.dp, color = Color.White.copy(alpha = 0.12f), shape = RoundedCornerShape(28.dp))
            .hazeChild(haze, navBarGlassStyle())
            .padding(vertical = AppSpacing.sm, horizontal = AppSpacing.xs)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
            // Previous | -15 | Play/Pause | +15 | Next
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(onClick = onPrevious, contentDescription = stringResource(R.string.player_previous)) {
                    Icon(Icons.Outlined.SkipPrevious, null, tint = Color.White, modifier = Modifier.size(26.dp).graphicsLayer { scaleX = if (isRtl) -1f else 1f })
                }
                GlassIconButton(onClick = onSkipBack, contentDescription = stringResource(R.string.player_skip_back)) {
                    Text(stringResource(R.string.player_skip_back), style = MaterialTheme.typography.labelLarge, color = Color.White)
                }
                Box(
                    modifier = Modifier
                        .size(62.dp)
                        .drawBehind {
                            drawCircle(brush = Brush.radialGradient(listOf(Cosmic.TealBright.copy(alpha = 0.5f), Color.Transparent), radius = size.width))
                        }
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Cosmic.Teal, Cosmic.StardustViolet)))
                        .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                        .clickable(onClick = onTogglePlay),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = stringResource(if (isPlaying) R.string.player_pause else R.string.player_play),
                        tint = Color.White,
                        modifier = Modifier.size(32.dp)
                    )
                }
                GlassIconButton(onClick = onSkipForward, contentDescription = stringResource(R.string.player_skip_forward)) {
                    Text(stringResource(R.string.player_skip_forward), style = MaterialTheme.typography.labelLarge, color = Color.White)
                }
                GlassIconButton(onClick = onNext, contentDescription = stringResource(R.string.player_next)) {
                    Icon(Icons.Outlined.SkipNext, null, tint = Color.White, modifier = Modifier.size(26.dp).graphicsLayer { scaleX = if (isRtl) -1f else 1f })
                }
            }
            // Mark | Speed | Sleep Timer | Chapters
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassPillButton(icon = { Icon(Icons.Outlined.BookmarkAdd, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                    label = stringResource(R.string.player_mark_label), selected = false,
                    onClick = onMark, modifier = Modifier.weight(1f))
                GlassPillButton(icon = { Icon(Icons.Outlined.Speed, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                    label = stringResource(R.string.player_speed_short), selected = activePanel == PlayerControlPanel.SPEED,
                    onClick = { onTogglePanel(PlayerControlPanel.SPEED) }, modifier = Modifier.weight(1f))
                GlassPillButton(icon = { Icon(Icons.Outlined.Bedtime, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                    label = stringResource(R.string.player_sleep_short), selected = activePanel == PlayerControlPanel.SLEEP,
                    onClick = { onTogglePanel(PlayerControlPanel.SLEEP) }, modifier = Modifier.weight(1f))
                GlassPillButton(icon = { Icon(Icons.Outlined.MoreHoriz, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                    label = stringResource(R.string.player_chapters_short), selected = activePanel == PlayerControlPanel.CHAPTERS,
                    onClick = { onTogglePanel(PlayerControlPanel.CHAPTERS) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun GlassIconButton(onClick: () -> Unit, contentDescription: String?, content: @Composable () -> Unit) {
    val cd = contentDescription
    Box(
        modifier = Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color.White.copy(alpha = 0.08f))
            .clickable(onClick = onClick)
            .semantics { if (cd != null) this.contentDescription = cd },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun GlassPillButton(
    icon: @Composable () -> Unit,
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (selected) Cosmic.Teal.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.08f))
            .border(1.dp, if (selected) Color.White.copy(alpha = 0.30f) else Color.White.copy(alpha = 0.12f), shape)
            .clickable(onClick = onClick)
            .minTouchTarget()
            .padding(horizontal = AppSpacing.md, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Text(label, style = MaterialTheme.typography.labelLarge, color = Color.White)
    }
}

/** لوحة تحكم قابلة للتمدد فوق شريط التحكم: السرعة / مؤقت النوم / المزيد. */
@Composable
private fun ControlPanel(
    panel: PlayerControlPanel?,
    sleepUi: com.example.audiobook.playback.SleepTimerUiState,
    selectedSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    onSleepStart: (Int) -> Unit,
    onSleepExtend: (Int) -> Unit,
    onSleepCancel: () -> Unit,
    onAddChapter: () -> Unit,
    onToggleEditing: () -> Unit,
    editing: Boolean,
    modifier: Modifier = Modifier
) {
    if (panel == null) return
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(shape)
            .background(Cosmic.InkBottom.copy(alpha = 0.88f), shape)
            .border(1.dp, Color.White.copy(alpha = 0.12f), shape)
            .padding(AppSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        when (panel) {
            PlayerControlPanel.SPEED -> {
                Text(stringResource(R.string.player_speed, ("%.2f".format(selectedSpeed)) + "×"), style = MaterialTheme.typography.titleSmall, color = Color.White)
                Slider(
                    value = selectedSpeed,
                    onValueChange = onSpeedChange,
                    valueRange = 0.5f..3f,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { preset ->
                        GlassPillButton(icon = {}, label = "%.2f".format(preset).replace(".00", "").toString() + "×",
                            selected = kotlin.math.abs(selectedSpeed - preset) < 0.01f,
                            onClick = { onSpeedChange(preset) }, modifier = Modifier.weight(1f))
                    }
                }
            }
            PlayerControlPanel.SLEEP -> {
                val phase = sleepUi.phase
                Text(
                    when (phase) {
                        SleepTimerPhase.IDLE, SleepTimerPhase.STOPPED -> stringResource(R.string.player_sleep_label)
                        else -> stringResource(R.string.player_sleep_active, formatTime(sleepUi.remainingMs ?: 0L))
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White
                )
                if (phase == SleepTimerPhase.IDLE || phase == SleepTimerPhase.STOPPED) {
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                        listOf(15, 30, 45, 60).forEach { minutes ->
                            GlassPillButton(icon = {}, label = "$minutes", selected = false,
                                onClick = { onSleepStart(minutes) }, modifier = Modifier.weight(1f))
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                        GlassPillButton(icon = {}, label = stringResource(R.string.player_sleep_extend, 15),
                            selected = false, onClick = { onSleepExtend(15) })
                        GlassPillButton(icon = {}, label = stringResource(R.string.player_sleep_extend, 30),
                            selected = false, onClick = { onSleepExtend(30) })
                        GlassPillButton(icon = {}, label = stringResource(R.string.player_sleep_cancel),
                            selected = false, onClick = onSleepCancel)
                    }
                }
                if (sleepUi.isExtendWindowVisible) {
                    Text(stringResource(R.string.player_sleep_ending), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                }
            }
            PlayerControlPanel.CHAPTERS -> {
                GlassPillButton(icon = { Icon(Icons.Outlined.BookmarkAdd, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                    label = stringResource(R.string.player_mark_chapter), selected = false, onClick = onAddChapter)
                GlassPillButton(icon = { Icon(if (editing) Icons.Outlined.BookmarkAdd else Icons.Outlined.MoreHoriz, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                    label = stringResource(if (editing) R.string.player_edit_done else R.string.player_edit_chapters),
                    selected = editing, onClick = onToggleEditing)
            }
            null -> {}
        }
    }
}

/** خلفية "غبار النجوم": سدم بنفسجي/ماجنتا/كهرماني + نجوم ساكنة فوق التدرج الديناميكي. */
@Composable
private fun PlayerStardustBackdrop(modifier: Modifier = Modifier, mode: AppThemeMode) {
    val baseAlpha = if (mode == AppThemeMode.LIGHT) 0.35f else 0.70f
    Canvas(modifier = modifier) {
        val blobs = listOf(
            Triple(size.width * 0.18f, size.height * 0.14f, Cosmic.StardustViolet),
            Triple(size.width * 0.84f, size.height * 0.30f, Cosmic.StardustMagenta),
            Triple(size.width * 0.50f, size.height * 0.90f, Cosmic.StardustAmber)
        )
        blobs.forEach { (cx, cy, color) ->
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(color.copy(alpha = if (mode == AppThemeMode.LIGHT) 0.10f else 0.14f), Color.Transparent),
                    center = Offset(cx, cy),
                    radius = size.minDimension * 0.50f
                ),
                radius = size.minDimension * 0.50f,
                center = Offset(cx, cy)
            )
        }
        val rng = kotlin.random.Random(42L)
        val count = when (mode) { AppThemeMode.LIGHT -> 40; AppThemeMode.DARK -> 80; AppThemeMode.AMOLED -> 100 }
        repeat(count) {
            val x = rng.nextFloat() * size.width
            val y = rng.nextFloat() * size.height
            val r = (0.5f + rng.nextFloat() * 1.6f).dp.toPx()
            val a = baseAlpha * (0.4f + rng.nextFloat() * 0.6f)
            drawCircle(color = Cosmic.MoonIce.copy(alpha = a), radius = r, center = Offset(x, y))
        }
    }
}

private fun formatTime(ms: Long): String = "%02d:%02d".format(ms / 60_000, (ms / 1_000) % 60)
private fun currentChapterNumber(state: PlayerTimelineState, position: Long): Int =
    PlayerTimelineEditor.numbered(state).lastOrNull { it.chapter.startPositionMs <= position }?.number ?: 1