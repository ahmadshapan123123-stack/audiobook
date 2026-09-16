package com.example.audiobook.presentation.player

import android.media.AudioManager
import android.media.ToneGenerator
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
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.BoxWithConstraintsScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
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
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.audiobook.R
import com.example.audiobook.domain.usecases.MarksCoordinator
import com.example.audiobook.playback.ActiveInteraction
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
    var saveMomentPosMs by remember { mutableStateOf<Long?>(null) }
    var noteComposerPosMs by remember { mutableStateOf<Long?>(null) }
    var chapterComposerPosMs by remember { mutableStateOf<Long?>(null) }
    var captureNotice by remember { mutableStateOf<CaptureNotice?>(null) }
    var showNoteOverlay by remember { mutableStateOf(false) }
    var activeNoteText by remember { mutableStateOf("") }

    LaunchedEffect(captureNotice) {
        if (captureNotice != null) {
            delay(3_000)
            captureNotice = null
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
    val numberedChapters = remember(renderedTimeline) { PlayerTimelineEditor.numbered(renderedTimeline) }
    val currentChapter = remember(numberedChapters, playback.positionMs) {
        numberedChapters.lastOrNull { it.chapter.startPositionMs <= playback.positionMs }
    }
    val currentChapterLabel = if (currentChapter != null && currentChapter.chapter.title.isNotBlank()) {
        stringResource(R.string.player_top_chapter_format, currentChapter.number, currentChapter.chapter.title.take(26))
    } else title

    // ---- أهمية اللحظة: التُقطت فورًا، ثم يُختار الهدف (علامة/ملاحظة/فصل) ----
    val captureMoment = {
        saveMomentPosMs = playback.positionMs
    }

    val saveBookmark = {
        saveMomentPosMs?.let { pos ->
            if (marks != null && editionId != null) scope.launch {
                marks.addBookmark(editionId, pos)
                sleepTimer.onActiveInteraction(ActiveInteraction.AddBookmark)
            } else timeline = timeline.copy(bookmarks = timeline.bookmarks + PlayerBookmark(positionMs = pos))
            captureNotice = CaptureNotice(R.string.player_mark_captured, pos)
            saveMomentPosMs = null
        }
    }

    val saveNote: (String) -> Unit = { text ->
        noteComposerPosMs?.let { pos ->
            val trimmed = text.trim()
            if (marks != null && editionId != null) scope.launch {
                marks.addBookmark(editionId, pos, note = trimmed.ifBlank { "ملاحظة" })
                sleepTimer.onActiveInteraction(ActiveInteraction.AddNote)
            } else timeline = timeline.copy(bookmarks = timeline.bookmarks + PlayerBookmark(positionMs = pos, label = trimmed.ifBlank { null }))
            captureNotice = CaptureNotice(R.string.player_note_saved, pos)
            noteComposerPosMs = null
        }
    }

    val addChapterAt: (Long, String) -> Unit = { pos, name ->
        if (marks != null && editionId != null) scope.launch {
            marks.addChapter(editionId, pos, title = name.ifBlank { "فصل جديد" })
            sleepTimer.onActiveInteraction(ActiveInteraction.CreateChapter)
        } else timeline = PlayerTimelineEditor.addChapter(timeline, pos, title = name.ifBlank { "فصل جديد" })
        captureNotice = CaptureNotice(R.string.player_chapter_saved, pos)
    }

    val saveChapter: (String) -> Unit = { name ->
        chapterComposerPosMs?.let { pos ->
            addChapterAt(pos, name)
            chapterComposerPosMs = null
        }
    }

    // ---- مراقبة عبور الملاحظات: تُعرض ٦ ثوانٍ مع نغمة تنبيه عند تخطّي موضعها ----
    val noteTone = remember { NoteCueTone() }
    DisposableEffect(Unit) { onDispose { noteTone.release() } }
    var lastWatchPosMs by remember { mutableStateOf(playback.positionMs) }
    var triggeredNoteIds by remember { mutableStateOf(emptySet<UUID>()) }
    LaunchedEffect(playback.positionMs, renderedTimeline.bookmarks) {
        val notes = renderedTimeline.bookmarks.filter { it.label?.isNotBlank() == true }
        val current = playback.positionMs
        val previous = lastWatchPosMs
        if (current < previous - 2_000L) {
            val before = current + 500L
            triggeredNoteIds = buildSet {
                for (id in triggeredNoteIds) {
                    if (notes.none { it.id == id && it.positionMs > before }) add(id)
                }
            }
        }
        if (current != previous) {
            val crossed = notes.firstOrNull { it.positionMs in previous..current && it.id !in triggeredNoteIds }
            if (crossed != null) {
                triggeredNoteIds = triggeredNoteIds + crossed.id
                activeNoteText = crossed.label.orEmpty()
                showNoteOverlay = true
                noteTone.play()
                launch {
                    delay(6_000)
                    showNoteOverlay = false
                }
            }
        }
        lastWatchPosMs = current
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
            // ---- الرأس: رجوع + الفصل الحالي (يُفتح لوحة الفصول) + المزيد ----
            Row(
                modifier = Modifier.fillMaxWidth().padding(start = AppSpacing.md, end = AppSpacing.md, top = AppSpacing.xs),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.xxs)
            ) {
                IconButton(onClick = onBack, modifier = Modifier.minTouchTarget()) {
                    Icon(Icons.AutoMirrored.Outlined.ArrowBack, contentDescription = stringResource(R.string.player_back), tint = fg.ink)
                }
                CurrentChapterChip(
                    chapterLabel = currentChapterLabel,
                    onClick = { expandedPanel = if (expandedPanel == PlayerControlPanel.CHAPTERS) null else PlayerControlPanel.CHAPTERS },
                    fg = fg,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = { expandedPanel = if (expandedPanel == PlayerControlPanel.MORE) null else PlayerControlPanel.MORE }, modifier = Modifier.minTouchTarget()) {
                    Icon(Icons.Outlined.MoreHoriz, contentDescription = stringResource(R.string.player_more_menu), tint = fg.ink)
                }
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
                val deckMaxHeight = maxHeight * 0.88f

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
                        seriesName = playerUi.seriesName,
                        fg = fg
                    )
                }

                // ---- لوحات الأدوات (السرعة/النوم/الفصول): تنبثق فوق منطقة الغلاف — نقرة خارجها تُغلقها ----
                AnimatedContent(
                    targetState = expandedPanel,
                    modifier = Modifier.align(Alignment.BottomCenter),
                    transitionSpec = {
                        (fadeIn() + expandVertically()) togetherWith (fadeOut() + shrinkVertically())
                    }
                ) { panel ->
                    if (panel != null && panel != PlayerControlPanel.MORE) {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Box(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(Color(0xFF0A0F1E).copy(alpha = 0.18f))
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null
                                    ) { expandedPanel = null }
                            )
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
                                    sleepTimer.onActiveInteraction(ActiveInteraction.ChangeSpeed)
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
                                fg = fg,
                                maxDeckHeight = deckMaxHeight,
                                haze = hazeState,
                                modifier = Modifier
                                    .align(Alignment.BottomCenter)
                                    .padding(horizontal = AppSpacing.md)
                                    .padding(bottom = AppSpacing.xs)
                            )
                        }
                    }
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
                onScrub = { target ->
                    sleepTimer.onActiveInteraction(ActiveInteraction.Seek)
                    controller.seekTo(target)
                },
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
                onPrevious = {
                    sleepTimer.onActiveInteraction(ActiveInteraction.PreviousChapter)
                    scope.launch { controller.previousChapter() }
                },
                onSkipBack = {
                    sleepTimer.onActiveInteraction(ActiveInteraction.SkipBackward15)
                    controller.skipBack15Seconds()
                },
                onTogglePlay = {
                    sleepTimer.onActiveInteraction(ActiveInteraction.PlayPause)
                    if (playback.isPlaying) controller.pause() else controller.play()
                },
                onSkipForward = {
                    sleepTimer.onActiveInteraction(ActiveInteraction.SkipForward15)
                    controller.skipForward15Seconds()
                },
                onNext = {
                    sleepTimer.onActiveInteraction(ActiveInteraction.NextChapter)
                    scope.launch { controller.nextChapter() }
                },
                fg = fg,
                modifier = Modifier.padding(start = AppSpacing.md, end = AppSpacing.md)
            )

            // ---- إشعار الالتقاط (فوق فجوة الأدوات) ----
            Column(
                modifier = Modifier.fillMaxWidth().height(AppSpacing.sm),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                AnimatedVisibility(visible = captureNotice != null) {
                    val hint = captureNotice?.let { stringResource(it.resId, formatTime(it.posMs)) } ?: ""
                    Text(
                        hint,
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
                onMark = { sleepTimer.onActiveInteraction(ActiveInteraction.AddBookmark); captureMoment() },
                onTogglePanel = { panel -> expandedPanel = if (expandedPanel == panel) null else panel },
                fg = fg,
                modifier = Modifier.padding(start = AppSpacing.md, end = AppSpacing.md, bottom = AppSpacing.xs)
            )
        }

        // ---- ألواح الالتقاط: علامة / ملاحظة / فصل، وقائمة "المزيد" ----
        SaveMomentSheet(
            visible = saveMomentPosMs != null,
            capturedText = saveMomentPosMs?.let { stringResource(R.string.player_mark_captured, formatTime(it)) },
            fg = fg,
            haze = hazeState,
            onBookmark = { saveBookmark() },
            onNote = { noteComposerPosMs = saveMomentPosMs; saveMomentPosMs = null },
            onChapter = { chapterComposerPosMs = saveMomentPosMs; saveMomentPosMs = null },
            onDismiss = { saveMomentPosMs = null }
        )
        NoteComposeSheet(
            visible = noteComposerPosMs != null,
            capturedText = noteComposerPosMs?.let { stringResource(R.string.player_mark_captured, formatTime(it)) },
            fg = fg,
            haze = hazeState,
            onSave = { text -> saveNote(text) },
            onDismiss = { noteComposerPosMs = null }
        )
        ChapterComposeSheet(
            visible = chapterComposerPosMs != null,
            capturedText = chapterComposerPosMs?.let { stringResource(R.string.player_mark_captured, formatTime(it)) },
            fg = fg,
            haze = hazeState,
            onSave = { text -> saveChapter(text) },
            onDismiss = { chapterComposerPosMs = null }
        )
        MoreDeck(
            visible = expandedPanel == PlayerControlPanel.MORE,
            fg = fg,
            haze = hazeState,
            onClose = { expandedPanel = null },
            onSpeed = { expandedPanel = PlayerControlPanel.SPEED },
            onSleep = { expandedPanel = PlayerControlPanel.SLEEP },
            onChapters = { expandedPanel = PlayerControlPanel.CHAPTERS },
            onAddChapterHere = { addChapterAt(playback.positionMs, "") }
        )
        NoteOverlay(
            visible = showNoteOverlay,
            text = activeNoteText,
            fg = fg,
            haze = hazeState,
            onDismiss = { showNoteOverlay = false }
        )
    }
}

private enum class PlayerControlPanel { SPEED, SLEEP, CHAPTERS, MORE }

private data class CaptureNotice(val resId: Int, val posMs: Long)

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

/** هوية الكتاب: العنوان + المؤلف (والسلسلة إن وُجدت) — وسط متزن تحت الغلاف. */
@Composable
private fun PlayerTitleBlock(
    title: String,
    authorName: String,
    seriesName: String,
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
        val subtitle = when {
            authorName.isNotEmpty() && seriesName.isNotEmpty() ->
                stringResource(R.string.player_author_series, authorName, seriesName)
            else -> authorName
        }
        if (subtitle.isNotEmpty()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodyLarge,
                color = fg.soft,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
                modifier = Modifier.fillMaxWidth()
            )
        }
    }
}

/** شارة الفصل الحالي في الرأس: تنفتح لوحة الفصول عند النقر. */
@Composable
private fun CurrentChapterChip(
    chapterLabel: String,
    onClick: () -> Unit,
    fg: PlayerFg,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(50)
    val description = stringResource(R.string.player_current_chapter_cd)
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(shape)
            .background(fg.ink.copy(alpha = 0.08f))
            .border(1.dp, fg.ink.copy(alpha = 0.16f), shape)
            .clickable(onClick = onClick)
            .minTouchTarget()
            .semantics { this.contentDescription = description }
            .padding(horizontal = AppSpacing.sm, vertical = 6.dp),
        contentAlignment = Alignment.Center
    ) {
        Row(
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = chapterLabel,
                style = MaterialTheme.typography.labelLarge,
                color = fg.accent,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Spacer(Modifier.width(2.dp))
            Icon(Icons.Outlined.KeyboardArrowDown, null, tint = fg.soft, modifier = Modifier.size(16.dp))
        }
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
    val positionFraction = if (visibleWindow.last <= visibleWindow.first) 0f
    else ((positionMs - visibleWindow.first).toFloat() / (visibleWindow.last - visibleWindow.first)).coerceIn(0f, 1f)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)
    ) {
        BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
            if (editing) {
                TimelineMarksCanvas(timelineState = timelineState, visibleWindow = visibleWindow, editing = true, fg = fg)
                EditingChapterDragLayer(
                    timelineState = timelineState,
                    visibleWindow = visibleWindow,
                    onChapterMoved = onChapterMoved
                )
            } else {
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
                TimelineMarksCanvas(timelineState = timelineState, visibleWindow = visibleWindow, editing = false, fg = fg)
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
                if (bookmark.label?.isNotBlank() == true) {
                    // ملاحظة: مربّع صغير بلون التيل (تمييز بصري عن العلامة).
                    val r = 3.dp.toPx()
                    drawRect(
                        color = Cosmic.TealBright.copy(alpha = 0.95f),
                        topLeft = Offset(x - r, trackY - r),
                        size = androidx.compose.ui.geometry.Size(r * 2, r * 2)
                    )
                } else {
                    drawCircle(
                        color = Cosmic.StardustMagenta.copy(alpha = 0.9f),
                        radius = 3.dp.toPx(),
                        center = Offset(x, trackY)
                    )
                }
            }
        }
    }
}

/** وضع التحرير فقط: اسحب أقرب علامة فصل لتحريك بدايتها — لا يُعاد إنشاء المؤشر أثناء السحب. */
@Composable
private fun EditingChapterDragLayer(
    timelineState: PlayerTimelineState,
    visibleWindow: LongRange,
    onChapterMoved: (UUID, Long) -> Unit
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val latestTimeline = rememberUpdatedState(timelineState)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .pointerInput(isRtl) {
                fun fractionAt(x: Float): Float = if (isRtl) (1f - x / size.width).coerceIn(0f, 1f) else (x / size.width).coerceIn(0f, 1f)
                fun nearestTime(t: Long): PlayerChapter? =
                    latestTimeline.value.chapters
                        .sortedBy { it.startPositionMs }
                        .minByOrNull { kotlin.math.abs(it.startPositionMs - t) }
                var draggedId: UUID? = null
                detectDragGestures(
                    onDragStart = { start ->
                        val t = visibleWindow.first + (fractionAt(start.x) * (visibleWindow.last - visibleWindow.first)).toLong()
                        val chapter = nearestTime(t)
                        if (chapter != null) {
                            draggedId = chapter.id
                            onChapterMoved(chapter.id, t.coerceIn(visibleWindow.first, visibleWindow.last))
                        }
                    },
                    onDrag = { change, _ ->
                        change.consume()
                        val id = draggedId ?: return@detectDragGestures
                        val t = visibleWindow.first + (fractionAt(change.position.x) * (visibleWindow.last - visibleWindow.first)).toLong()
                        onChapterMoved(id, t.coerceIn(visibleWindow.first, visibleWindow.last))
                    },
                    onDragEnd = { draggedId = null },
                    onDragCancel = { draggedId = null }
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
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceEvenly
    ) {
        TransportIcon(
            contentDescription = stringResource(R.string.player_previous),
            onClick = onPrevious
        ) {
            Icon(Icons.Outlined.SkipPrevious, null, tint = fg.soft,
                modifier = Modifier.size(26.dp).graphicsLayer { scaleX = if (isRtl) -1f else 1f })
        }
        SkipButton(
            cd = stringResource(R.string.player_skip_back),
            label = stringResource(R.string.player_skip_back).removeSuffix(" ثا"),
            onClick = onSkipBack,
            fg = fg
        )
        PlayButton(isPlaying = isPlaying, onClick = onTogglePlay, fg = fg)
        SkipButton(
            cd = stringResource(R.string.player_skip_forward),
            label = stringResource(R.string.player_skip_forward).removeSuffix(" ثا"),
            onClick = onSkipForward,
            fg = fg
        )
        TransportIcon(
            contentDescription = stringResource(R.string.player_next),
            onClick = onNext
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

/** زر 15 ثانية: دائرة زجاجية مدمجة تحمل الوجه الموجّه للتنقل السريع. */
@Composable
private fun SkipButton(
    cd: String,
    label: String,
    onClick: () -> Unit,
    fg: PlayerFg,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .size(58.dp)
            .minTouchTarget()
            .clip(CircleShape)
            .background(fg.ink.copy(alpha = 0.08f))
            .border(1.dp, fg.ink.copy(alpha = 0.18f), CircleShape)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = cd },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = fg.soft,
            maxLines = 1
        )
    }
}

/** زر التشغيل: العنصر الأكبر والأكثف — دائرة زجاجية فوق التدرج. */
@Composable
private fun PlayButton(isPlaying: Boolean, onClick: () -> Unit, fg: PlayerFg, modifier: Modifier = Modifier) {
    val cd = stringResource(if (isPlaying) R.string.player_pause else R.string.player_play)
    Box(
        modifier = modifier
            .size(76.dp)
            .clip(CircleShape)
            .background(fg.ink.copy(alpha = 0.14f))
            .border(1.dp, fg.ink.copy(alpha = 0.30f), CircleShape)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = cd },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
            null,
            tint = fg.ink,
            modifier = Modifier.size(38.dp)
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

/** لوحة الأدوات العائمة: تنبثق فوق أسفل الشاشة — زجاج شفاف بلغة ألوان المشغّل. */
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
    fg: PlayerFg,
    maxDeckHeight: Dp,
    haze: HazeState,
    modifier: Modifier = Modifier
) {
    if (panel == null) return
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxDeckHeight)
            .clip(shape)
            .background(fg.ink.copy(alpha = 0.10f), shape)
            .border(1.dp, fg.ink.copy(alpha = 0.20f), shape)
            .hazeChild(haze, navBarGlassStyle())
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        when (panel) {
            PlayerControlPanel.SPEED -> {
                Text(stringResource(R.string.player_speed, speedLabel(selectedSpeed)), style = MaterialTheme.typography.titleSmall, color = fg.ink)
                Slider(
                    value = selectedSpeed,
                    onValueChange = onSpeedChange,
                    valueRange = 0.5f..3f,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = fg.accent,
                        activeTrackColor = fg.accent,
                        inactiveTrackColor = fg.ink.copy(alpha = 0.18f),
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { preset ->
                        GlassPillButton(label = speedLabel(preset),
                            selected = kotlin.math.abs(selectedSpeed - preset) < 0.01f,
                            onClick = { onSpeedChange(preset) }, fg = fg, modifier = Modifier.weight(1f), compact = true)
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
                    color = fg.ink
                )
                if (phase == SleepTimerPhase.IDLE || phase == SleepTimerPhase.STOPPED) {
                    var showCustom by remember { mutableStateOf(false) }
                    var customMinutes by remember { mutableStateOf("") }
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                        listOf(15, 30, 45, 60).forEach { minutes ->
                            GlassPillButton(label = "$minutes", selected = false,
                                onClick = { onSleepStart(minutes) }, fg = fg, modifier = Modifier.weight(1f), compact = true)
                        }
                        GlassPillButton(label = stringResource(R.string.player_sleep_custom),
                            selected = showCustom, onClick = { showCustom = !showCustom }, fg = fg, modifier = Modifier.weight(1f), compact = true)
                    }
                    if (showCustom) {
                        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs), verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .clip(RoundedCornerShape(50))
                                    .background(fg.ink.copy(alpha = 0.08f))
                                    .border(1.dp, fg.ink.copy(alpha = 0.14f), RoundedCornerShape(50))
                                    .padding(horizontal = AppSpacing.md, vertical = 8.dp)
                            ) {
                                BasicTextField(
                                    value = customMinutes,
                                    onValueChange = { customMinutes = it.filter(Char::isDigit).take(3) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    textStyle = MaterialTheme.typography.labelLarge.copy(color = fg.ink),
                                    decorationBox = { innerTextField ->
                                        if (customMinutes.isEmpty()) Text(
                                            stringResource(R.string.player_sleep_custom_hint),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = fg.soft
                                        )
                                        innerTextField()
                                    }
                                )
                            }
                            GlassPillButton(label = stringResource(R.string.player_sleep_custom_start),
                                selected = false,
                                onClick = {
                                    val minutes = customMinutes.toIntOrNull()
                                    if (minutes != null && minutes in 1..240) {
                                        onSleepStart(minutes)
                                        showCustom = false
                                        customMinutes = ""
                                    }
                                },
                                fg = fg,
                                compact = true)
                        }
                    }
                } else {
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                        GlassPillButton(label = stringResource(R.string.player_sleep_extend, 15),
                            selected = false, onClick = { onSleepExtend(15) }, fg = fg, compact = true)
                        GlassPillButton(label = stringResource(R.string.player_sleep_extend, 30),
                            selected = false, onClick = { onSleepExtend(30) }, fg = fg, compact = true)
                        GlassPillButton(label = stringResource(R.string.player_sleep_cancel),
                            selected = false, onClick = onSleepCancel, fg = fg, compact = true)
                    }
                }
                if (sleepUi.isExtendWindowVisible) {
                    Text(stringResource(R.string.player_sleep_ending), style = MaterialTheme.typography.bodySmall, color = fg.soft)
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
                onChapterMoved = onChapterMoved,
                fg = fg
            )
            PlayerControlPanel.MORE -> {}
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
    onChapterMoved: (UUID, Long) -> Unit,
    fg: PlayerFg
) {
    val numbered = remember(timelineState) { PlayerTimelineEditor.numbered(timelineState) }
    Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)) {
        Text(
            stringResource(R.string.player_chapters_header, numbered.size),
            style = MaterialTheme.typography.titleSmall,
            color = fg.ink
        )

        // مستوى العرض الزمني (يعمل مع تحرير الفصول بالسحب).
        DeckSegmented(
            labels = listOf(
                stringResource(R.string.player_timeline_overview) to TimelineLevel.OVERVIEW,
                stringResource(R.string.player_timeline_zoom) to TimelineLevel.ZOOMED
            ),
            selected = timelineState.level,
            onSelect = onLevelChange,
            fg = fg
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
                    startTimeLabel = stringResource(R.string.player_chapter_start_at, formatTime(nc.chapter.startPositionMs)),
                    isCurrent = isCurrent,
                    onClick = { onSeekToChapter(nc.chapter.startPositionMs) },
                    fg = fg
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            GlassPillButton(icon = { Icon(Icons.Outlined.BookmarkAdd, null, tint = fg.ink, modifier = Modifier.size(18.dp)) },
                label = stringResource(R.string.player_mark_chapter), selected = false, onClick = onAddChapter,
                fg = fg, modifier = Modifier.weight(1f))
            GlassPillButton(icon = { Icon(Icons.Outlined.MoreHoriz, null, tint = fg.ink, modifier = Modifier.size(18.dp)) },
                label = stringResource(if (editing) R.string.player_edit_done else R.string.player_edit_chapters),
                selected = editing, onClick = onToggleEditing,
                fg = fg, modifier = Modifier.weight(1f))
        }
    }
}

@Composable
private fun ChapterDeckRow(
    number: Int,
    title: String,
    durationMs: Long,
    startTimeLabel: String,
    isCurrent: Boolean,
    onClick: () -> Unit,
    fg: PlayerFg
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AppSpacing.sm))
            .background(if (isCurrent) fg.accent.copy(alpha = 0.15f) else Color.Transparent)
            .minTouchTarget()
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isCurrent) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(fg.accent))
        } else {
            Spacer(Modifier.size(6.dp))
        }
        Text(
            text = stringResource(R.string.player_chapter_num, number) + " · " + title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isCurrent) fg.accent else fg.soft,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = startTimeLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = SpaceGroteskFamily),
                color = fg.soft
            )
            Text(
                text = formatTime(durationMs),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = SpaceGroteskFamily),
                color = fg.soft
            )
        }
    }
}

/** تبديل مستوى العرض الزمني داخل اللوحة (نظرة عامة / تكبير). */
@Composable
private fun DeckSegmented(
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
            .border(1.dp, fg.ink.copy(alpha = 0.16f), shape)
            .padding(2.dp)
    ) {
        labels.forEach { (label, level) ->
            val isSelected = level == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) fg.accent.copy(alpha = 0.28f) else Color.Transparent)
                    .minTouchTarget()
                    .clickable(onClick = { onSelect(level) })
                    .padding(horizontal = AppSpacing.sm, vertical = 4.dp),
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

@Composable
private fun GlassPillButton(
    icon: @Composable () -> Unit = {},
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    fg: PlayerFg,
    modifier: Modifier = Modifier,
    compact: Boolean = false
) {
    val shape = RoundedCornerShape(50)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (selected) fg.accent.copy(alpha = 0.30f) else fg.ink.copy(alpha = 0.08f))
            .border(
                1.dp,
                if (selected) fg.accent.copy(alpha = 0.55f) else fg.ink.copy(alpha = 0.16f),
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
            color = if (selected) fg.ink else fg.soft
        )
    }
}

/**
 * لوحة زجاجية سفلية موحّدة (نفس لغة أدوات المشغّل): شفافة على التدرج، حبر/إضاءة متجاوبة،
 * تُستخدم لاختيار الهدف بعد "حفظ اللحظة"، ولتكوين الملاحظة، وقائمة "المزيد".
 * النقر خارج اللوحة يُغلقها عبر طبقة التفاف شفافة خلفها.
 */
@Composable
private fun BoxScope.GlassSheet(
    visible: Boolean,
    fg: PlayerFg,
    haze: HazeState,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxSize(),
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color(0xFF0A0F1E).copy(alpha = 0.18f))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { onDismiss() }
            )
            val shape = RoundedCornerShape(24.dp)
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.md, vertical = AppSpacing.xs)
                    .clip(shape)
                    .background(fg.ink.copy(alpha = 0.10f), shape)
                    .border(1.dp, fg.ink.copy(alpha = 0.20f), shape)
                    .hazeChild(haze, navBarGlassStyle())
                    .imePadding()
                    .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                    .padding(horizontal = AppSpacing.lg, vertical = AppSpacing.md),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
            ) {
                content()
            }
        }
    }
}

@Composable
private fun ColumnScope.SheetHeadline(title: String, subtitle: String?, fg: PlayerFg) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = fg.ink
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = fg.soft
            )
        }
    }
}

@Composable
private fun SheetOptionRow(
    icon: @Composable () -> Unit,
    title: String,
    description: String,
    onClick: () -> Unit,
    fg: PlayerFg
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(fg.ink.copy(alpha = 0.06f))
            .border(1.dp, fg.ink.copy(alpha = 0.12f), shape)
            .minTouchTarget()
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(fg.accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) { icon() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = fg.ink)
            if (description.isNotEmpty()) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = fg.soft)
            }
        }
    }
}

/** بعد "حفظ اللحظة": اختيار الهدف — علامة مرجعية / ملاحظة / فصل. */
@Composable
private fun BoxScope.SaveMomentSheet(
    visible: Boolean,
    capturedText: String?,
    fg: PlayerFg,
    haze: HazeState,
    onBookmark: () -> Unit,
    onNote: () -> Unit,
    onChapter: () -> Unit,
    onDismiss: () -> Unit
) {
    GlassSheet(visible = visible, fg = fg, haze = haze, onDismiss = onDismiss) {
        SheetHeadline(title = stringResource(R.string.player_save_moment), subtitle = capturedText, fg = fg)
        SheetOptionRow(
            icon = { Icon(Icons.Outlined.BookmarkAdd, null, modifier = Modifier.size(22.dp), tint = fg.accent) },
            title = stringResource(R.string.player_mark_bookmark),
            description = "",
            onClick = onBookmark,
            fg = fg
        )
        SheetOptionRow(
            icon = { Icon(Icons.Outlined.EditNote, null, modifier = Modifier.size(22.dp), tint = fg.accent) },
            title = stringResource(R.string.player_mark_note_opt),
            description = stringResource(R.string.player_mark_note_desc),
            onClick = onNote,
            fg = fg
        )
        SheetOptionRow(
            icon = { Icon(Icons.Outlined.AddCircle, null, modifier = Modifier.size(22.dp), tint = fg.accent) },
            title = stringResource(R.string.player_mark_chapter_opt),
            description = stringResource(R.string.player_mark_chapter_desc),
            onClick = onChapter,
            fg = fg
        )
    }
}

/** تكوين ملاحظة: نص قصير + حفظ — تُحفظ عند نفس موضع الالتقاط. */
@Composable
private fun BoxScope.NoteComposeSheet(
    visible: Boolean,
    capturedText: String?,
    fg: PlayerFg,
    haze: HazeState,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember { mutableStateOf("") }
    val focusRequester = remember { FocusRequester() }
    LaunchedEffect(visible) {
        if (visible) {
            text = ""
            focusRequester.requestFocus()
        }
    }
    GlassSheet(visible = visible, fg = fg, haze = haze, onDismiss = onDismiss) {
        SheetHeadline(title = stringResource(R.string.player_note_title), subtitle = capturedText, fg = fg)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(fg.ink.copy(alpha = 0.06f))
                .border(1.dp, fg.ink.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
        ) {
            BasicTextField(
                value = text,
                onValueChange = { if (it.length <= 240) text = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                maxLines = 3,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = fg.ink),
                decorationBox = { innerTextField ->
                    if (text.isEmpty()) Text(
                        stringResource(R.string.player_note_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = fg.soft
                    )
                    innerTextField()
                }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            GlassPillButton(label = stringResource(R.string.player_note_cancel), selected = false, onClick = { text = ""; onDismiss() }, fg = fg, modifier = Modifier.weight(1f))
            GlassPillButton(label = stringResource(R.string.player_note_save), selected = false, onClick = { if (text.isNotBlank()) onSave(text) }, fg = fg, modifier = Modifier.weight(1f))
        }
    }
}

/** تكوين فصل: اسم اختياري + إنشاء — عند موضع الالتقاط نفسه. */
@Composable
private fun BoxScope.ChapterComposeSheet(
    visible: Boolean,
    capturedText: String?,
    fg: PlayerFg,
    haze: HazeState,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    LaunchedEffect(visible) {
        if (visible) name = ""
    }
    GlassSheet(visible = visible, fg = fg, haze = haze, onDismiss = onDismiss) {
        SheetHeadline(title = stringResource(R.string.player_mark_chapter), subtitle = capturedText, fg = fg)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(fg.ink.copy(alpha = 0.06f))
                .border(1.dp, fg.ink.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
        ) {
            BasicTextField(
                value = name,
                onValueChange = { if (it.length <= 60) name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = fg.ink),
                decorationBox = { innerTextField ->
                    if (name.isEmpty()) Text(
                        stringResource(R.string.player_chapter_name_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = fg.soft
                    )
                    innerTextField()
                }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            GlassPillButton(label = stringResource(R.string.player_note_cancel), selected = false, onClick = onDismiss, fg = fg, modifier = Modifier.weight(1f))
            GlassPillButton(label = stringResource(R.string.player_chapter_save), selected = false, onClick = { onSave(name) }, fg = fg, modifier = Modifier.weight(1f))
        }
    }
}

/** قائمة "المزيد": إجراءات سريعة غير مكررة في صف الأدوات. */
@Composable
private fun BoxScope.MoreDeck(
    visible: Boolean,
    fg: PlayerFg,
    haze: HazeState,
    onClose: () -> Unit,
    onSpeed: () -> Unit,
    onSleep: () -> Unit,
    onChapters: () -> Unit,
    onAddChapterHere: () -> Unit
) {
    GlassSheet(visible = visible, fg = fg, haze = haze, onDismiss = onClose) {
        SheetHeadline(title = stringResource(R.string.player_more_menu), subtitle = null, fg = fg)
        SheetOptionRow(
            icon = { Icon(Icons.Outlined.AddCircle, null, modifier = Modifier.size(22.dp), tint = fg.accent) },
            title = stringResource(R.string.player_more_chapter_now),
            description = stringResource(R.string.player_mark_chapter_desc),
            onClick = { onAddChapterHere(); onClose() },
            fg = fg
        )
        SheetOptionRow(
            icon = { Icon(Icons.Outlined.Speed, null, modifier = Modifier.size(22.dp), tint = fg.accent) },
            title = stringResource(R.string.player_speed_short),
            description = "",
            onClick = onSpeed,
            fg = fg
        )
        SheetOptionRow(
            icon = { Icon(Icons.Outlined.Bedtime, null, modifier = Modifier.size(22.dp), tint = fg.accent) },
            title = stringResource(R.string.player_sleep_short),
            description = "",
            onClick = onSleep,
            fg = fg
        )
        SheetOptionRow(
            icon = { Icon(Icons.Outlined.LibraryBooks, null, modifier = Modifier.size(22.dp), tint = fg.accent) },
            title = stringResource(R.string.player_chapters_short),
            description = "",
            onClick = onChapters,
            fg = fg
        )
    }
}

/** تراكب الملاحظة العابرة: يُظهر النص ٦ ثوانٍ عند تخطّي موضع الملاحظة — زجاج متجاوب مع المشغّل. */
@Composable
private fun BoxScope.NoteOverlay(
    visible: Boolean,
    text: String,
    fg: PlayerFg,
    haze: HazeState,
    onDismiss: () -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 128.dp).padding(horizontal = AppSpacing.lg),
        enter = fadeIn() + expandVertically(expandFrom = Alignment.Top),
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.Top)
    ) {
        val shape = RoundedCornerShape(18.dp)
        Row(
            modifier = Modifier
                .clip(shape)
                .background(fg.ink.copy(alpha = 0.10f), shape)
                .border(1.dp, fg.ink.copy(alpha = 0.20f), shape)
                .hazeChild(haze, navBarGlassStyle())
                .padding(start = AppSpacing.md, top = AppSpacing.sm, bottom = AppSpacing.sm),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = stringResource(R.string.player_note_overlay_cue),
                style = MaterialTheme.typography.labelMedium,
                color = fg.accent,
                modifier = Modifier.padding(end = AppSpacing.sm)
            )
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                color = fg.ink,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.minTouchTarget()) {
                Icon(Icons.Outlined.Close, contentDescription = stringResource(R.string.player_note_overlay_close), tint = fg.ink)
            }
        }
    }
}

/** نغمة قصيرة تُشغَّل عند عبور ملاحظة؛ تُنشأ كسولًا وتُحرَّر عند مغادرة الشاشة. */
private class NoteCueTone {
    private var tone: ToneGenerator? = null
    fun play() {
        try {
            if (tone == null) tone = ToneGenerator(AudioManager.STREAM_MUSIC, 70)
            tone?.startTone(ToneGenerator.TONE_PROP_BEEP2, 350)
        } catch (_: Throwable) {
            // بعض الأجهزة لا توفّر ToneGenerator؛ تجاهل صامت.
        }
    }
    fun release() {
        try { tone?.release() } catch (_: Throwable) { }
        tone = null
    }
}