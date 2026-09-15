package com.example.audiobook.presentation.player

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
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
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiobook.R
import com.example.audiobook.domain.usecases.MarksCoordinator
import com.example.audiobook.playback.PlaybackController
import com.example.audiobook.playback.SleepTimerController
import com.example.audiobook.playback.SleepTimerPhase
import com.example.audiobook.playback.SleepTimerUiState
import com.example.audiobook.presentation.theme.AppSpacing
import com.example.audiobook.presentation.theme.AppThemeMode
import com.example.audiobook.presentation.theme.Cosmic
import com.example.audiobook.presentation.theme.LocalCosmicHeader
import com.example.audiobook.presentation.theme.SpaceGroteskFamily
import com.example.audiobook.presentation.theme.minTouchTarget
import com.example.audiobook.presentation.theme.navBarGlassStyle
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.rememberHazeState
import java.util.UUID
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch

/**
 * مشغّل "أثير" — جلسة استماع هادئة.
 *
 * التسلسل البصري من أعلى إلى أسفل: الهوية (الغلاف + العنوان)، ثم "أين أنا؟"
 * (فصل الشكل الزمني)، ثم الشريط الزمني الموحّد، ثم التحكّم الأساسي، ثم أدوات
 * الاستماع الخفيفة. بلا تبويبات، بلا جدار أزرار، بلا صناديق متكدسة — سطح واحد متصل.
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
    var lastMarkMs by remember { mutableStateOf<Long?>(null) }
    var showMarkHint by remember { mutableStateOf(false) }

    LaunchedEffect(lastMarkMs) {
        if (lastMarkMs != null) {
            showMarkHint = true
            delay(3_000)
            showMarkHint = false
            lastMarkMs = null
        }
    }

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

    val onAddBookmark = {
        if (editionId != null && marks != null) {
            scope.launch { marks.addBookmark(editionId, playback.positionMs) }
            lastMarkMs = playback.positionMs
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .haze(hazeState)
    ) {
        // ---- الخلفية: تدرج هادئ واحد (هوية الكتاب) — سطح المشغّل كله ----
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
            // ---- الرأس: رجوع + شارة السرعة الحيّة ----
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

            // ---- الوسط: الغلاف + الهوية — حجم متكيف حسب المساحة المتاحة ----
            BoxWithConstraints(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(start = AppSpacing.md, end = AppSpacing.md)
            ) {
                val identityBlockHeight = 136.dp
                val coverHeight = (maxHeight - identityBlockHeight).coerceIn(112.dp, 300.dp)
                val coverWidth = coverHeight * 0.72f

                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    PlayerCoverBlock(
                        title = title,
                        gradient = gradient,
                        fg = fg,
                        modifier = Modifier.width(coverWidth).height(coverHeight)
                    )
                    Spacer(Modifier.height(AppSpacing.md))
                    PlayerTitleBlock(
                        title = title,
                        authorName = playerUi.authorName,
                        fg = fg
                    )
                }

                // لوحات الأدوات: تنبثق فوق منطقة الغلاف — بلا إزاحة للتخطيط (Overlay).
                AnimatedContent(
                    targetState = expandedPanel,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    transitionSpec = {
                        (fadeIn() + expandVertically()) togetherWith (fadeOut() + shrinkVertically())
                    }
                ) { panel ->
                    UtilitiesDeck(
                        panel = panel,
                        timelineState = renderedTimeline,
                        positionMs = playback.positionMs,
                        durationMs = playback.durationMs,
                        sleepUi = sleepUi,
                        selectedSpeed = selectedSpeed,
                        editing = editing,
                        onLevelChange = { level ->
                            timeline = if (level == TimelineLevel.ZOOMED) PlayerTimelineEditor.zoom(timeline, playback.positionMs)
                            else PlayerTimelineEditor.overview(timeline)
                        },
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
                        onSeekToChapter = { target ->
                            controller.seekTo(target)
                            expandedPanel = null
                        },
                        onToggleEditing = {
                            editing = !editing
                            if (editing && timeline.level != TimelineLevel.ZOOMED) {
                                timeline = PlayerTimelineEditor.zoom(timeline, playback.positionMs)
                            }
                        },
                        onChapterMoved = { id, position ->
                            val stored = storedChapters.firstOrNull { it.id == id }
                            if (marks != null && stored != null) scope.launch { marks.updateChapter(stored, position) }
                            else timeline = PlayerTimelineEditor.moveChapter(timeline, id, position)
                        },
                        maxDeckHeight = maxHeight * 0.88f,
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

            // ---- الشريط الزمني الموحّد: "أين أنا؟" + الخيط ----
            PlayerTimelineSection(
                timelineState = renderedTimeline,
                positionMs = playback.positionMs,
                durationMs = playback.durationMs,
                visibleWindow = visibleWindow,
                fg = fg,
                editing = editing,
                onScrub = { target -> controller.seekTo(target) },
                onChapterMoved = { id, position ->
                    val stored = storedChapters.firstOrNull { it.id == id }
                    if (marks != null && stored != null) scope.launch { marks.updateChapter(stored, position) }
                    else timeline = PlayerTimelineEditor.moveChapter(timeline, id, position)
                },
                modifier = Modifier.padding(start = AppSpacing.md, end = AppSpacing.md)
            )

            // ---- التحكّم الأساسي: التشغيل هو البطل ----
            PlayerTransportRow(
                isPlaying = playback.isPlaying,
                onPrevious = { scope.launch { controller.previousChapter() } },
                onSkipBack = controller::skipBack15Seconds,
                onTogglePlay = { if (playback.isPlaying) controller.pause() else controller.play() },
                onSkipForward = controller::skipForward15Seconds,
                onNext = { scope.launch { controller.nextChapter() } },
                fg = fg,
                modifier = Modifier.padding(start = AppSpacing.md, end = AppSpacing.md)
            )

            // ---- إشعار الالتقاط (فوق فجوة الأدوات) ----
            Column(
                modifier = Modifier.fillMaxWidth().height(AppSpacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AnimatedVisibility(visible = showMarkHint) {
                    Text(
                        lastMarkMs?.let { stringResource(R.string.player_mark_captured, formatTime(it)) }.orEmpty(),
                        style = MaterialTheme.typography.labelMedium,
                        color = fg.accent
                    )
                }
            }

            // ---- أدوات الاستماع: خفيفة، لا تنافس التحكّم ----
            PlayerToolsRow(
                markLabel = stringResource(R.string.player_save_moment),
                sleepLabel = sleepStatusLabel(sleepUi),
                sleepActive = sleepUi.phase == SleepTimerPhase.RUNNING ||
                    sleepUi.phase == SleepTimerPhase.WARNING_WINDOW ||
                    sleepUi.phase == SleepTimerPhase.FADING_OUT,
                activePanel = expandedPanel,
                onMark = onAddBookmark,
                onTogglePanel = { panel -> expandedPanel = if (expandedPanel == panel) null else panel },
                fg = fg,
                modifier = Modifier.padding(start = AppSpacing.md, end = AppSpacing.md, bottom = AppSpacing.xs)
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

@Composable
private fun sleepStatusLabel(sleepUi: SleepTimerUiState): String {
    val active = sleepUi.phase == SleepTimerPhase.RUNNING ||
        sleepUi.phase == SleepTimerPhase.WARNING_WINDOW ||
        sleepUi.phase == SleepTimerPhase.FADING_OUT
    val remaining = sleepUi.remainingMs ?: 0L
    return if (active && remaining > 0L) {
        stringResource(R.string.player_sleep_with_remaining, formatTime(remaining))
    } else {
        stringResource(R.string.player_sleep_short)
    }
}

private fun formatTime(ms: Long): String = "%02d:%02d".format(ms / 60_000, (ms / 1_000) % 60)

/** غلاف المشغّل: الحرف الأول فوق تدرج الكتاب. */
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

/** هوية الكتاب: العنوان + المؤلف — وسط متزن تحت الغلاف. */
@Composable
private fun PlayerTitleBlock(
    title: String,
    authorName: String,
    fg: PlayerFg
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.displaySmall,
            fontWeight = FontWeight.SemiBold,
            color = fg.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
        Text(
            text = authorName,
            style = MaterialTheme.typography.bodyLarge,
            color = fg.soft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/** شارة السرعة الحيّة في الرأس. */
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
            .semantics { this.contentDescription = description }
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

/**
 * الشريط الزمني الموحّد: "أين أنا؟" (الفصل الحالي) ثم الخيط الذي يحمل
 * علامات الفصول والإشارات ورأس التشغيل + الوقت المنقضي والمتبقّي.
 */
@Composable
private fun PlayerTimelineSection(
    timelineState: PlayerTimelineState,
    positionMs: Long,
    durationMs: Long,
    visibleWindow: LongRange,
    fg: PlayerFg,
    editing: Boolean,
    onScrub: (Long) -> Unit,
    onChapterMoved: (UUID, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val numbered = remember(timelineState) { PlayerTimelineEditor.numbered(timelineState) }
    val chapter = remember(timelineState, positionMs) { numbered.lastOrNull { it.chapter.startPositionMs <= positionMs } }
    val chapterTitle = chapter?.chapter?.title?.take(40)?.trim().orEmpty()
    val hasChapter = chapter != null && chapterTitle.isNotEmpty()

    val positionFraction = if (visibleWindow.last <= visibleWindow.first) 0f
    else ((positionMs - visibleWindow.first).toFloat() / (visibleWindow.last - visibleWindow.first)).coerceIn(0f, 1f)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)
    ) {
        // سياق الفصل الحالي — مربوط مباشرة بالشريط تحته.
        Box(modifier = Modifier.fillMaxWidth().heightIn(min = 32.dp)) {
            Text(
                text = if (hasChapter) stringResource(R.string.player_chapter_num, chapter?.number ?: 0) + " · " + chapterTitle
                else stringResource(R.string.player_cover_placeholder),
                style = MaterialTheme.typography.labelLarge,
                color = fg.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.align(Alignment.CenterStart).padding(end = AppSpacing.lg)
            )
            if (hasChapter) {
                Text(
                    text = stringResource(R.string.player_chapter_count, chapter?.number ?: 0, numbered.size),
                    style = MaterialTheme.typography.labelMedium,
                    color = fg.soft,
                    modifier = Modifier.align(Alignment.CenterEnd)
                )
            }
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
                TimelineMarksCanvas(timelineState = timelineState, visibleWindow = visibleWindow, editing = false, fg = fg)
            } else {
                TimelineMarksCanvas(timelineState = timelineState, visibleWindow = visibleWindow, editing = true, fg = fg)
                EditingChapterDragLayer(
                    timelineState = timelineState,
                    visibleWindow = visibleWindow,
                    onChapterMoved = onChapterMoved
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                formatTime(positionMs),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = SpaceGroteskFamily),
                color = fg.ink
            )
            Text(
                stringResource(R.string.player_remaining, formatTime((durationMs - positionMs).coerceAtLeast(0L))),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = SpaceGroteskFamily),
                color = fg.soft
            )
        }
    }
}

/** طبقة لاصقة: علامات الفصول + الإشارات فوق الشريط — بدون التقاط لمس. */
@Composable
private fun TimelineMarksCanvas(
    timelineState: PlayerTimelineState,
    visibleWindow: LongRange,
    editing: Boolean,
    fg: PlayerFg
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier = Modifier.fillMaxWidth().height(34.dp)) {
        val trackY = size.height * 0.45f
        val drawableStart = 12.dp.toPx()
        val drawableEnd = size.width - drawableStart
        val span = drawableEnd - drawableStart
        fun markX(fraction: Float): Float = if (isRtl) drawableEnd - fraction * span else drawableStart + fraction * span
        timelineState.chapters.sortedBy { it.startPositionMs }.forEach { chapter ->
            val f = mkFraction(chapter.startPositionMs, visibleWindow)
            if (f in 0f..1f) {
                val x = markX(f)
                drawLine(
                    color = if (editing) Cosmic.StardustAmber.copy(alpha = 0.95f) else fg.accent.copy(alpha = 0.55f),
                    start = Offset(x, trackY - 7.dp.toPx()),
                    end = Offset(x, trackY + 7.dp.toPx()),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
        timelineState.bookmarks.forEach { bookmark ->
            val f = mkFraction(bookmark.positionMs, visibleWindow)
            if (f in 0f..1f) {
                val x = markX(f)
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
    timelineState: PlayerTimelineState,
    visibleWindow: LongRange,
    onChapterMoved: (UUID, Long) -> Unit
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(34.dp)
            .pointerInput(timelineState, isRtl) {
                fun fractionAt(x: Float): Float = if (isRtl) (1f - x / size.width).coerceIn(0f, 1f) else (x / size.width).coerceIn(0f, 1f)
                detectDragGestures(
                    onDragStart = { start ->
                        val t = visibleWindow.first + (fractionAt(start.x) * (visibleWindow.last - visibleWindow.first)).toLong()
                        val chapter = timelineState.chapters.sortedBy { it.startPositionMs }
                            .minByOrNull { kotlin.math.abs(it.startPositionMs - t) }
                        if (chapter != null) onChapterMoved(chapter.id, t.coerceIn(visibleWindow.first, visibleWindow.last))
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val t = visibleWindow.first + (fractionAt(change.position.x) * (visibleWindow.last - visibleWindow.first)).toLong()
                        val chapter = timelineState.chapters.sortedBy { it.startPositionMs }
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

/** التحكّم الأساسي: التشغيل مسيطر، والمحيط هادئ بوضوح. */
@Composable
private fun PlayerTransportRow(
    isPlaying: Boolean,
    onPrevious: () -> Unit,
    onSkipBack: () -> Unit,
    onTogglePlay: () -> Unit,
    onSkipForward: () -> Unit,
    onNext: () -> Unit,
    fg: PlayerFg,
    modifier: Modifier = Modifier
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        TransportIcon(
            contentDescription = stringResource(R.string.player_previous),
            onClick = onPrevious,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Outlined.SkipPrevious, null, tint = fg.soft,
                modifier = Modifier.size(26.dp).graphicsLayer { scaleX = if (isRtl) -1f else 1f })
        }
        SkipPill(text = stringResource(R.string.player_skip_back), onClick = onSkipBack, fg = fg, modifier = Modifier.weight(1.25f))
        PlayButton(isPlaying = isPlaying, onClick = onTogglePlay, modifier = Modifier.size(72.dp).padding(horizontal = AppSpacing.xs))
        SkipPill(text = stringResource(R.string.player_skip_forward), onClick = onSkipForward, fg = fg, modifier = Modifier.weight(1.25f))
        TransportIcon(
            contentDescription = stringResource(R.string.player_next),
            onClick = onNext,
            modifier = Modifier.weight(1f)
        ) {
            Icon(Icons.Outlined.SkipNext, null, tint = fg.soft,
                modifier = Modifier.size(26.dp).graphicsLayer { scaleX = if (isRtl) -1f else 1f })
        }
    }
}

@Composable
private fun TransportIcon(
    contentDescription: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .minTouchTarget()
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = contentDescription },
        contentAlignment = Alignment.Center
    ) {
        content()
    }
}

@Composable
private fun SkipPill(text: String, onClick: () -> Unit, fg: PlayerFg, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .minTouchTarget()
            .clip(RoundedCornerShape(50))
            .clickable(onClick = onClick)
            .padding(vertical = AppSpacing.xs),
        contentAlignment = Alignment.Center
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = fg.soft, maxLines = 1)
    }
}

/** زر التشغيل: العنصر الأكبر والأكثف — تدرّج التيل للهوية. */
@Composable
private fun PlayButton(isPlaying: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val cd = stringResource(if (isPlaying) R.string.player_pause else R.string.player_play)
    Box(
        modifier = modifier
            .clip(CircleShape)
            .background(Brush.linearGradient(listOf(Cosmic.Teal, Cosmic.TealBright)))
            .border(2.dp, Cosmic.TealBright.copy(alpha = 0.55f), CircleShape)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = cd },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
            null,
            tint = Color.White,
            modifier = Modifier.size(34.dp)
        )
    }
}

/** أدوات الاستماع: نص هادئ بأهداف لمس 48dp — لا عبوات، لا بطاقات. */
@Composable
private fun PlayerToolsRow(
    markLabel: String,
    sleepLabel: String,
    sleepActive: Boolean,
    activePanel: PlayerControlPanel?,
    onMark: () -> Unit,
    onTogglePanel: (PlayerControlPanel) -> Unit,
    fg: PlayerFg,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ToolText(
            text = markLabel,
            onClick = onMark,
            selected = false,
            fg = fg,
            modifier = Modifier.weight(1f)
        )
        ToolText(
            text = stringResource(R.string.player_speed_short),
            onClick = { onTogglePanel(PlayerControlPanel.SPEED) },
            selected = activePanel == PlayerControlPanel.SPEED,
            fg = fg,
            modifier = Modifier.weight(1f)
        )
        ToolText(
            text = sleepLabel,
            onClick = { onTogglePanel(PlayerControlPanel.SLEEP) },
            selected = activePanel == PlayerControlPanel.SLEEP || sleepActive,
            fg = fg,
            modifier = Modifier.weight(1f)
        )
        ToolText(
            text = stringResource(R.string.player_chapters_short),
            onClick = { onTogglePanel(PlayerControlPanel.CHAPTERS) },
            selected = activePanel == PlayerControlPanel.CHAPTERS,
            fg = fg,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ToolText(
    text: String,
    onClick: () -> Unit,
    selected: Boolean,
    fg: PlayerFg,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier
            .clip(RoundedCornerShape(50))
            .minTouchTarget()
            .clickable(onClick = onClick)
            .padding(vertical = AppSpacing.xs),
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) fg.accent else fg.soft,
        textAlign = TextAlign.Center,
        maxLines = 1,
        overflow = TextOverflow.Ellipsis
    )
}

/** لوحة الأدوات العائمة: تنبثق فوق أسفل منطقة الغلاف بلا إزاحة للتخطيط. */
@Composable
private fun UtilitiesDeck(
    panel: PlayerControlPanel?,
    timelineState: PlayerTimelineState,
    positionMs: Long,
    durationMs: Long,
    sleepUi: SleepTimerUiState,
    selectedSpeed: Float,
    editing: Boolean,
    onLevelChange: (TimelineLevel) -> Unit,
    onSpeedChange: (Float) -> Unit,
    onSleepStart: (Int) -> Unit,
    onSleepExtend: (Int) -> Unit,
    onSleepCancel: () -> Unit,
    onAddChapter: () -> Unit,
    onSeekToChapter: (Long) -> Unit,
    onToggleEditing: () -> Unit,
    onChapterMoved: (UUID, Long) -> Unit,
    maxDeckHeight: Dp,
    haze: HazeState,
    modifier: Modifier = Modifier
) {
    if (panel == null) return
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = AppSpacing.md)
            .heightIn(max = maxDeckHeight)
            .clip(shape)
            .background(Cosmic.InkBottom.copy(alpha = 0.94f), shape)
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
            PlayerControlPanel.CHAPTERS -> ChapterManagePanel(
                timelineState = timelineState,
                positionMs = positionMs,
                durationMs = durationMs,
                editing = editing,
                onLevelChange = onLevelChange,
                onAddChapter = onAddChapter,
                onSeekToChapter = onSeekToChapter,
                onToggleEditing = onToggleEditing,
                onChapterMoved = onChapterMoved
            )
        }
    }
}

/** لوحة الفصول: قائمة قفز + مستوى العرض الزمني + إدارة. */
@Composable
private fun ChapterManagePanel(
    timelineState: PlayerTimelineState,
    positionMs: Long,
    durationMs: Long,
    editing: Boolean,
    onLevelChange: (TimelineLevel) -> Unit,
    onAddChapter: () -> Unit,
    onSeekToChapter: (Long) -> Unit,
    onToggleEditing: () -> Unit,
    onChapterMoved: (UUID, Long) -> Unit
) {
    val numbered = remember(timelineState) { PlayerTimelineEditor.numbered(timelineState) }
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Text(
            stringResource(R.string.player_chapters_header, numbered.size),
            style = MaterialTheme.typography.titleSmall,
            color = Color.White
        )

        // مستوى العرض الزمني (يعمل مع تحرير الفصول بالسحب).
        DeckSegmented(
            labels = listOf(
                stringResource(R.string.player_timeline_overview) to TimelineLevel.OVERVIEW,
                stringResource(R.string.player_timeline_zoom) to TimelineLevel.ZOOMED
            ),
            selected = timelineState.level,
            onSelect = onLevelChange
        )

        // قائمة الفصول — الانتقال بالنقر.
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)) {
            numbered.forEachIndexed { index, nc ->
                val isCurrent = nc.chapter.startPositionMs <= positionMs &&
                    (numbered.getOrNull(index + 1)?.chapter?.startPositionMs ?: Long.MAX_VALUE) > positionMs
                val chapterEnd = numbered.getOrNull(index + 1)?.chapter?.startPositionMs ?: durationMs
                ChapterDeckRow(
                    number = nc.number,
                    title = nc.chapter.title ?: "فصل",
                    durationMs = (chapterEnd - nc.chapter.startPositionMs).coerceAtLeast(0L),
                    isCurrent = isCurrent,
                    onClick = { onSeekToChapter(nc.chapter.startPositionMs) }
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            GlassPillButton(icon = { Icon(Icons.Outlined.BookmarkAdd, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                label = stringResource(R.string.player_mark_chapter), selected = false, onClick = onAddChapter,
                modifier = Modifier.weight(1f))
            GlassPillButton(icon = { Icon(Icons.Outlined.MoreHoriz, null, tint = Color.White, modifier = Modifier.size(18.dp)) },
                label = stringResource(if (editing) R.string.player_edit_done else R.string.player_edit_chapters),
                selected = editing, onClick = onToggleEditing,
                modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ChapterDeckRow(
    number: Int,
    title: String,
    durationMs: Long,
    isCurrent: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppSpacing.sm))
            .background(if (isCurrent) Cosmic.Teal.copy(alpha = 0.18f) else Color.Transparent)
            .minTouchTarget()
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isCurrent) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(Cosmic.TealBright))
        } else {
            Spacer(Modifier.size(6.dp))
        }
        Text(
            text = stringResource(R.string.player_chapter_num, number) + " · " + title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isCurrent) Cosmic.TealBright else Color.White.copy(alpha = 0.92f),
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = formatTime(durationMs),
            style = MaterialTheme.typography.labelMedium.copy(fontFamily = SpaceGroteskFamily),
            color = Color.White.copy(alpha = 0.6f)
        )
    }
}

/** تبديل مستوى العرض الزمني داخل اللوحة (نظرة عامة / تكبير). */
@Composable
private fun DeckSegmented(
    labels: List<Pair<String, TimelineLevel>>,
    selected: TimelineLevel,
    onSelect: (TimelineLevel) -> Unit
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = Modifier
            .clip(shape)
            .background(Color.White.copy(alpha = 0.08f))
            .border(1.dp, Color.White.copy(alpha = 0.14f), shape)
            .padding(2.dp)
    ) {
        labels.forEach { (label, level) ->
            val isSelected = level == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) Cosmic.Teal.copy(alpha = 0.30f) else Color.Transparent)
                    .minTouchTarget()
                    .clickable(onClick = { onSelect(level) })
                    .padding(horizontal = AppSpacing.sm, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) Cosmic.TealBright else Color.White.copy(alpha = 0.8f),
                    maxLines = 1
                )
            }
        }
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
            color = if (selected) Color.White else Color.White.copy(alpha = 0.9f)
        )
    }
}