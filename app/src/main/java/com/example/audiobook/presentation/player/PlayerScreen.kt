package com.example.audiobook.presentation.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
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
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiobook.R
import com.example.audiobook.domain.usecases.MarksCoordinator
import com.example.audiobook.playback.PlaybackController
import com.example.audiobook.playback.SleepTimerController
import com.example.audiobook.playback.SleepTimerPhase
import com.example.audiobook.playback.SleepTimerUiState
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
import java.util.UUID
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * مشغّل "أثير" — جلسة استماع هادئة.
 *
 * البنية من جديد: خلفية تدرج هادئ فقط (هوية الكتاب)، رأس رفيع (رجوع + شارة السرعة)،
 * غلاف كبير مع العنوان/المؤلف كتعليق تحته، خيط زمني واحد نظيف، وصندوق تحكّم زجاجي واحد.
 * لوحات الأدوات (سرعة/نوم/فصول) تنبثق فوق منطقة الغلاف — بلا إزاحة للتخطيط أبدًا.
 */
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
    val fg = playerForeground(gradient)
    val hazeState = rememberHazeState()
    val visibleWindow = visibleWindowMs(renderedTimeline, playback.positionMs)
    val title = playerUi.title.ifBlank { stringResource(R.string.player_cover_placeholder) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .haze(hazeState)
    ) {
        // ---- الخلفية: تدرج هادئ واحد (هوية الكتاب) — بلا سدم ولا نجوم ----
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Brush.verticalGradient(listOf(gradient.start, gradient.end)))
        )

        Column(
            modifier = Modifier
                .fillMaxSize()
                .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
        ) {
            // ---- HEADER: رجوع فقط + شارة السرعة الحيّة (دالة دائمة الظهور) ----
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = AppSpacing.md, end = AppSpacing.md, top = AppSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                IconButton(onClick = onBack, modifier = Modifier.minTouchTarget()) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.player_back), tint = fg.ink)
                }
                SpeedBadge(
                    speed = selectedSpeed,
                    onClick = { expandedPanel = if (expandedPanel == PlayerControlPanel.SPEED) null else PlayerControlPanel.SPEED },
                    fg = fg
                )
            }

            // ---- MAIN: غلاف متوسط + عنوان/مؤلف/فصل موزّعون بعرض الشاشة الكامل (حافة إلى حافة) ----
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = AppSpacing.md, end = AppSpacing.md)
            ) {
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PlayerCoverBlock(
                        title = title,
                        gradient = gradient,
                        fg = fg,
                        modifier = Modifier.fillMaxWidth(0.56f).aspectRatio(0.72f)
                    )
                    Spacer(Modifier.height(AppSpacing.lg))
                    PlayerMetadataSpread(
                        title = title,
                        authorName = playerUi.authorName,
                        timeline = renderedTimeline,
                        positionMs = playback.positionMs,
                        fg = fg
                    )
                }

                // لوحات الأدوات: تنبثق فوق أسفل المنطقة — بلا إزاحة للتخطيط (Overlay).
                AnimatedContent(
                    targetState = expandedPanel,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    transitionSpec = {
                        (fadeIn() + expandVertically()) togetherWith (fadeOut() + shrinkVertically())
                    }
                ) { panel ->
                    UtilitiesDeck(
                        panel = panel,
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
                        haze = hazeState,
                        modifier = Modifier.padding(bottom = AppSpacing.xs)
                    )
                }
            }

            playback.missingFileMessage?.let { message ->
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(vertical = AppSpacing.xxs).padding(start = AppSpacing.md, end = AppSpacing.md)
                )
            }

            // ---- TIMELINE: الخيط الزمني النظيف (تبديل مستوى + شريط + وقتان) ----
            PlayerTimelineBar(
                state = renderedTimeline,
                positionMs = playback.positionMs,
                durationMs = playback.durationMs,
                visibleWindow = visibleWindow,
                fg = fg,
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
                },
                modifier = Modifier.padding(start = AppSpacing.md, end = AppSpacing.md)
            )

            // ---- CONSOLE: شريط زجاجي بعرض الشاشة كاملة (تشغيل + أدوات) موزّع حافة إلى حافة ----
            PlayerConsole(
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
                sleepActive = sleepUi.phase == SleepTimerPhase.RUNNING ||
                    sleepUi.phase == SleepTimerPhase.WARNING_WINDOW ||
                    sleepUi.phase == SleepTimerPhase.FADING_OUT,
                onTogglePanel = { panel -> expandedPanel = if (expandedPanel == panel) null else panel },
                haze = hazeState,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = AppSpacing.sm)
            )
        }
    }
}

private enum class PlayerControlPanel { SPEED, SLEEP, CHAPTERS }

/** ألوان النص فوق التدرج: حبر داكن فوق سماء فاتحة، بياض فوق الحبر الليلي. */
private class PlayerFg(val ink: Color, val soft: Color, val accent: Color)

private fun playerForeground(gradient: PlayerGradient): PlayerFg {
    val light = gradient.start.luminance() > 0.55f
    val ink = if (light) Color(0xFF1F2A44) else Color.White
    return PlayerFg(
        ink = ink,
        soft = ink.copy(alpha = if (light) 0.72f else 0.78f),
        accent = if (light) Color(0xFF0B6E63) else Cosmic.TealBright
    )
}

private fun visibleWindowMs(state: PlayerTimelineState, positionMs: Long): LongRange {
    if (state.level != TimelineLevel.ZOOMED || state.durationMs <= 0L) return 0L..state.durationMs.coerceAtLeast(1L)
    val half = 30 * 60_000L
    val start = (positionMs - half).coerceIn(0L, state.durationMs)
    val end = (positionMs + half).coerceIn(start, state.durationMs)
    return start..end
}

/** أساس المعلومات: توزيع بعرض الشاشة الكامل — العنوان والمؤلف والفصل من الحافة إلى الحافة. */
@Composable
private fun PlayerMetadataSpread(
    title: String,
    authorName: String,
    timeline: PlayerTimelineState,
    positionMs: Long,
    fg: PlayerFg
) {
    val numbered = remember(timeline) { PlayerTimelineEditor.numbered(timeline) }
    val chapter = remember(timeline, positionMs) { numbered.lastOrNull { it.chapter.startPositionMs <= positionMs } }
    val chapterTitle = chapter?.chapter?.title?.take(40)?.trim().orEmpty()
    val hasChapter = chapter != null && chapterTitle.isNotEmpty()
    val chapterNum = chapter?.number ?: 0

    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
            color = fg.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = authorName,
            style = MaterialTheme.typography.bodyLarge,
            color = fg.soft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Start,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(AppSpacing.xs))
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp)) {
            Text(
                text = if (hasChapter) stringResource(R.string.player_chapter_num, chapterNum) + " · " + chapterTitle
                else stringResource(R.string.player_cover_placeholder),
                style = MaterialTheme.typography.labelLarge,
                color = fg.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.CenterStart).padding(end = AppSpacing.lg)
            )
            if (hasChapter) {
                Text(
                    text = stringResource(R.string.player_chapter_count, chapterNum, numbered.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = fg.soft,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
        }
    }
}

/** غلاف المشغّل: الحرف الأول فوق تدرج الكتاب — بلا شطاحات ولا توهجات، بسكون بسيط. */
@Composable
private fun PlayerCoverBlock(
    title: String,
    gradient: PlayerGradient,
    fg: PlayerFg,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(AppSpacing.lg)
    Box(
        modifier = modifier
            .clip(shape)
            .background(
                Brush.verticalGradient(listOf(gradient.start, gradient.end)),
                shape
            )
            .border(width = 1.dp, color = fg.ink.copy(alpha = 0.16f), shape = shape),
        contentAlignment = Alignment.Center
    ) {
        val letter = title.trim().firstOrNull()?.toString() ?: "؟"
        Text(
            text = letter,
            color = fg.ink,
            style = MaterialTheme.typography.displayLarge,
            fontWeight = FontWeight.SemiBold
        )
    }
}

/** شارة السرعة الحيّة في الرأس: دالة دائمة الظهور بنقرة واحدة لفتح اللوحة. */
@Composable
private fun SpeedBadge(
    speed: Float,
    onClick: () -> Unit,
    fg: PlayerFg,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(50)
    val description = stringResource(R.string.player_speed, speedLabel(speed))
    Box(
        modifier = modifier
            .clip(shape)
            .background(fg.ink.copy(alpha = 0.08f))
            .border(1.dp, fg.ink.copy(alpha = 0.16f), shape)
            .clickable(onClick = onClick)
            .minTouchTarget()
            .semantics { contentDescription = description }
            .padding(horizontal = AppSpacing.sm, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = speedLabel(speed),
            style = MaterialTheme.typography.labelLarge,
            color = fg.accent
        )
    }
}

private fun speedLabel(speed: Float): String =
    "${"%.2f".format(speed).trimEnd('0').trimEnd('.')}×"

/** عنصر Timeline واحد: شريط عليه علامات الفصول + وقتان، وتبديل مستوى نظيف. */
@Composable
private fun PlayerTimelineBar(
    state: PlayerTimelineState,
    positionMs: Long,
    durationMs: Long,
    visibleWindow: LongRange,
    fg: PlayerFg,
    editing: Boolean,
    onScrub: (Long) -> Unit,
    onLevelChange: (TimelineLevel) -> Unit,
    onChapterMoved: (UUID, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val positionFraction = if (visibleWindow.last <= visibleWindow.first) 0f
    else ((positionMs - visibleWindow.first).toFloat() / (visibleWindow.last - visibleWindow.first)).coerceIn(0f, 1f)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xs)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TimelineModeToggle(
                fg = fg,
                labels = listOf(
                    stringResource(R.string.player_timeline_overview) to TimelineLevel.OVERVIEW,
                    stringResource(R.string.player_timeline_zoom) to TimelineLevel.ZOOMED
                ),
                selected = state.level,
                onSelect = onLevelChange
            )
        }

        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            Box(modifier = Modifier.fillMaxWidth().minTouchTarget()) {
                Slider(
                    value = positionFraction,
                    onValueChange = { fraction ->
                        val target = visibleWindow.first + (fraction * (visibleWindow.last - visibleWindow.first)).toLong()
                        onScrub(target)
                    },
                    onValueChangeFinished = {},
                    colors = SliderDefaults.colors(
                        thumbColor = fg.accent,
                        activeTrackColor = fg.accent,
                        inactiveTrackColor = fg.ink.copy(alpha = 0.18f),
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent
                    )
                )
            }
            if (!editing) {
                TimelineMarksCanvas(state = state, visibleWindow = visibleWindow, editing = false, fg = fg)
            } else {
                TimelineMarksCanvas(state = state, visibleWindow = visibleWindow, editing = true, fg = fg)
                EditingChapterDragLayer(
                    state = state,
                    visibleWindow = visibleWindow,
                    onChapterMoved = onChapterMoved
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(formatTime(positionMs), style = MaterialTheme.typography.bodySmall, color = fg.ink)
            Text(formatTime(durationMs), style = MaterialTheme.typography.bodySmall, color = fg.soft)
        }

        state.markCaptureMs?.let { captured ->
            Text(
                stringResource(R.string.player_mark_captured, formatTime(captured)),
                style = MaterialTheme.typography.bodySmall,
                color = fg.soft,
                modifier = Modifier.padding(horizontal = AppSpacing.xs)
            )
        }
    }
}

/** عنصر تبديل مستوى واحد متسق (نظرة عامة / تكبير) منسجم مع لون السماء. */
@Composable
private fun TimelineModeToggle(
    labels: List<Pair<String, TimelineLevel>>,
    selected: TimelineLevel,
    onSelect: (TimelineLevel) -> Unit,
    fg: PlayerFg
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(fg.ink.copy(alpha = 0.08f))
            .border(width = 1.dp, color = fg.ink.copy(alpha = 0.16f), shape = shape)
            .padding(3.dp)
    ) {
        labels.forEach { (label, level) ->
            val isSelected = level == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) Cosmic.Teal.copy(alpha = 0.30f) else Color.Transparent)
                    .minTouchTarget()
                    .clickable(onClick = { onSelect(level) })
                    .padding(horizontal = AppSpacing.sm, vertical = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) fg.accent else fg.soft,
                    maxLines = 1
                )
            }
        }
    }
}

/** طبقة لاصقة لا تلتقط اللمس إطلاقًا (الـSlider يبقى المسؤول عن السحب). */
@Composable
private fun TimelineMarksCanvas(
    state: PlayerTimelineState,
    visibleWindow: LongRange,
    editing: Boolean,
    fg: PlayerFg
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
                    color = if (editing) Cosmic.StardustAmber.copy(alpha = 0.95f) else fg.accent.copy(alpha = 0.55f),
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

/** وضع التحرير فقط: اسحب أقرب علامة فصل لتحريك بدايتها. */
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

private fun mkFraction(positionMs: Long, window: LongRange): Float {
    if (window.last <= window.first) return 0f
    return ((positionMs - window.first).toFloat() / (window.last - window.first)).coerceIn(0f, 1f)
}

/** صندوق التحكّم الزجاجي الموحّد أسفل الشاشة: تشغيل + أدوات — كل الوظائف المطلوبة ولا غيرها. */
@Composable
private fun PlayerConsole(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onSkipBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSkipForward: () -> Unit,
    onNext: () -> Unit,
    onMark: () -> Unit,
    activePanel: PlayerControlPanel?,
    sleepActive: Boolean,
    onTogglePanel: (PlayerControlPanel) -> Unit,
    haze: HazeState,
    modifier: Modifier = Modifier
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val bandShape = RoundedCornerShape(
        topStart = 22.dp,
        topEnd = 22.dp,
        bottomStart = 0.dp,
        bottomEnd = 0.dp
    )
    Box(
        modifier = modifier
            .clip(bandShape)
            .background(Cosmic.NavBarBlue.copy(alpha = 0.92f), bandShape)
            .border(width = 1.dp, color = Color.White.copy(alpha = 0.12f), shape = bandShape)
            .hazeChild(haze, navBarGlassStyle())
            .padding(vertical = AppSpacing.sm, horizontal = AppSpacing.xs)
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassIconButton(onClick = onPrevious, contentDescription = stringResource(R.string.player_previous)) {
                    Icon(Icons.Outlined.SkipPrevious, null, tint = Cosmic.MoonIce, modifier = Modifier.size(26.dp).graphicsLayer { scaleX = if (isRtl) -1f else 1f })
                }
                TransportPill(text = stringResource(R.string.player_skip_back), onClick = onSkipBack)
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(CircleShape)
                        .background(Brush.linearGradient(listOf(Cosmic.Teal, Cosmic.TealBright)))
                        .border(2.dp, Cosmic.TealBright.copy(alpha = 0.55f), CircleShape)
                        .clickable(onClick = onTogglePlay),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
                        contentDescription = stringResource(if (isPlaying) R.string.player_pause else R.string.player_play),
                        tint = Color.White,
                        modifier = Modifier.size(34.dp)
                    )
                }
                TransportPill(text = stringResource(R.string.player_skip_forward), onClick = onSkipForward)
                GlassIconButton(onClick = onNext, contentDescription = stringResource(R.string.player_next)) {
                    Icon(Icons.Outlined.SkipNext, null, tint = Cosmic.MoonIce, modifier = Modifier.size(26.dp).graphicsLayer { scaleX = if (isRtl) -1f else 1f })
                }
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
                verticalAlignment = Alignment.CenterVertically
            ) {
                GlassPillButton(label = stringResource(R.string.player_mark_label), selected = false,
                    onClick = onMark, modifier = Modifier.weight(1f), compact = true)
                GlassPillButton(label = stringResource(R.string.player_speed_short), selected = activePanel == PlayerControlPanel.SPEED,
                    onClick = { onTogglePanel(PlayerControlPanel.SPEED) }, modifier = Modifier.weight(1f), compact = true)
                GlassPillButton(label = stringResource(R.string.player_sleep_short), selected = activePanel == PlayerControlPanel.SLEEP || sleepActive,
                    onClick = { onTogglePanel(PlayerControlPanel.SLEEP) }, modifier = Modifier.weight(1f), compact = true)
                GlassPillButton(label = stringResource(R.string.player_chapters_short), selected = activePanel == PlayerControlPanel.CHAPTERS,
                    onClick = { onTogglePanel(PlayerControlPanel.CHAPTERS) }, modifier = Modifier.weight(1f), compact = true)
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
            .border(1.dp, Cosmic.MoonIce.copy(alpha = 0.20f), CircleShape)
            .clickable(onClick = onClick)
            .semantics { if (cd != null) this.contentDescription = cd },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun TransportPill(text: String, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .minTouchTarget()
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Cosmic.MoonIce.copy(alpha = 0.18f), RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.md, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = Cosmic.MoonIce)
    }
}

@Composable
private fun GlassPillButton(
    icon: @Composable () -> Unit = {},
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (selected) Cosmic.Teal.copy(alpha = 0.45f) else Color.White.copy(alpha = 0.08f))
            .border(
                1.dp,
                if (selected) Cosmic.TealBright.copy(alpha = 0.55f) else Color.White.copy(alpha = 0.12f),
                shape
            )
            .clickable(onClick = onClick)
            .minTouchTarget()
            .padding(horizontal = if (compact) AppSpacing.xs else AppSpacing.md, vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        icon()
        Text(
            label,
            style = MaterialTheme.typography.labelLarge,
            color = if (selected) Color.White else Cosmic.MoonIce.copy(alpha = 0.92f)
        )
    }
}

/** لوحة الأدوات العائمة: تنبثق فوق أسفل منطقة الغلاف بلا إزاحة للتخطيط. */
@Composable
private fun UtilitiesDeck(
    panel: PlayerControlPanel?,
    sleepUi: SleepTimerUiState,
    selectedSpeed: Float,
    onSpeedChange: (Float) -> Unit,
    onSleepStart: (Int) -> Unit,
    onSleepExtend: (Int) -> Unit,
    onSleepCancel: () -> Unit,
    onAddChapter: () -> Unit,
    onToggleEditing: () -> Unit,
    editing: Boolean,
    haze: HazeState,
    modifier: Modifier = Modifier
) {
    if (panel == null) return
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.md)
            .heightIn(max = 320.dp)
            .clip(shape)
            .background(Cosmic.InkBottom.copy(alpha = 0.92f), shape)
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
            .hazeChild(haze, navBarGlassStyle())
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        when (panel) {
            PlayerControlPanel.SPEED -> {
                Text(stringResource(R.string.player_speed, speedLabel(selectedSpeed)), style = MaterialTheme.typography.titleSmall, color = Color.White)
                Slider(
                    value = selectedSpeed,
                    onValueChange = onSpeedChange,
                    valueRange = 0.5f..3f,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { preset ->
                        GlassPillButton(label = speedLabel(preset),
                            selected = kotlin.math.abs(selectedSpeed - preset) < 0.01f,
                            onClick = { onSpeedChange(preset) }, modifier = Modifier.weight(1f), compact = true)
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
                            GlassPillButton(label = "$minutes", selected = false,
                                onClick = { onSleepStart(minutes) }, modifier = Modifier.weight(1f), compact = true)
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                        GlassPillButton(label = stringResource(R.string.player_sleep_extend, 15),
                            selected = false, onClick = { onSleepExtend(15) }, compact = true)
                        GlassPillButton(label = stringResource(R.string.player_sleep_extend, 30),
                            selected = false, onClick = { onSleepExtend(30) }, compact = true)
                        GlassPillButton(label = stringResource(R.string.player_sleep_cancel),
                            selected = false, onClick = onSleepCancel, compact = true)
                    }
                }
                if (sleepUi.isExtendWindowVisible) {
                    Text(stringResource(R.string.player_sleep_ending), style = MaterialTheme.typography.bodySmall, color = Color.White.copy(alpha = 0.85f))
                }
            }
            PlayerControlPanel.CHAPTERS -> {
                GlassPillButton(icon = { Icon(Icons.Outlined.BookmarkAdd, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                    label = stringResource(R.string.player_mark_chapter), selected = false, onClick = onAddChapter)
                GlassPillButton(icon = { Icon(Icons.Outlined.MoreHoriz, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                    label = stringResource(if (editing) R.string.player_edit_done else R.string.player_edit_chapters),
                    selected = editing, onClick = onToggleEditing)
            }
            null -> {}
        }
    }
}

private fun formatTime(ms: Long): String = "%02d:%02d".format(ms / 60_000, (ms / 1_000) % 60)