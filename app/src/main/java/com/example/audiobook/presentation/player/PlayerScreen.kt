package com.example.audiobook.presentation.player

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioDeviceCallback
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.media.ToneGenerator
import android.os.Build
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.awaitHorizontalTouchSlopOrCancellation
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.drag
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.absoluteOffset
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
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.outlined.AddCircle
import androidx.compose.material.icons.outlined.Bedtime
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.EditNote
import androidx.compose.material.icons.outlined.Headphones
import androidx.compose.material.icons.outlined.LibraryBooks
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Pause
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.SkipNext
import androidx.compose.material.icons.outlined.SkipPrevious
import androidx.compose.material.icons.outlined.VolumeUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.BlurredEdgeTreatment
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt
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
import com.example.audiobook.domain.model.AppThemeMode
import com.example.audiobook.presentation.theme.LocalCosmicHeader
import com.example.audiobook.presentation.theme.SpaceGroteskFamily
import com.example.audiobook.presentation.theme.minTouchTarget

import java.util.UUID
import kotlinx.coroutines.channels.Channel
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
    notificationCenter: com.example.audiobook.notifications.AtherNotificationCenter? = null,
    onFirstPlaybackPermissionRequest: () -> Unit = {},
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
    var noteVisible by remember { mutableStateOf(false) }
    var activeNoteAlert by remember { mutableStateOf<NoteAlert?>(null) }
    var headerHeightPx by remember { mutableIntStateOf(0) }
    var consoleHeightPx by remember { mutableIntStateOf(0) }
    var rootHeightPx by remember { mutableIntStateOf(0) }
    var headerBottomPx by remember { mutableIntStateOf(0) }

    // ---- جهاز الصوت: مراقبة المخرجات وتذكّر الاختيار (التبديل لا يوقف التشغيل) ----
    val context = LocalContext.current
    val audioManager = remember { context.getSystemService(Context.AUDIO_SERVICE) as AudioManager }
    var audioDevices by remember { mutableStateOf<List<AudioDeviceInfo>>(emptyList()) }
    var activeDeviceId by remember { mutableStateOf<Int?>(null) }
    val canListBt = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
        context.checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED
    fun refreshAudioDevices() {
        val found = audioManager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)?.toList().orEmpty()
        audioDevices = found
            .filter { it.isSink && isRelevantOutputType(it.type) }
            .distinctBy { it.id }
            .sortedBy { outputDeviceSortKey(it.type) }
        if (audioDevices.none { it.id == activeDeviceId }) {
            activeDeviceId = audioDevices.firstOrNull()?.id
        }
    }
    DisposableEffect(audioManager, canListBt) {
        refreshAudioDevices()
        val callback = object : AudioDeviceCallback() {
            override fun onAudioDevicesAdded(addedDevices: Array<AudioDeviceInfo>) { refreshAudioDevices() }
            override fun onAudioDevicesRemoved(removedDevices: Array<AudioDeviceInfo>) { refreshAudioDevices() }
        }
        audioManager.registerAudioDeviceCallback(callback, null)
        onDispose { audioManager.unregisterAudioDeviceCallback(callback) }
    }
    val btPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        refreshAudioDevices()
    }

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
    var playbackPermissionRequested by remember { mutableStateOf(false) }
    LaunchedEffect(playback.isPlaying) {
        if (playback.isPlaying && !playbackPermissionRequested) {
            playbackPermissionRequested = true
            onFirstPlaybackPermissionRequest()
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
    val fg = playerForeground(gradient, themeMode)
    val view = LocalView.current
    SideEffect {
        val act = (view.context as? android.app.Activity) ?: return@SideEffect
        val ctrl = androidx.core.view.WindowInsetsControllerCompat(act.window, view)
        val darkIcons = fg.ink.luminance() < 0.5f
        ctrl.isAppearanceLightStatusBars = darkIcons
        ctrl.isAppearanceLightNavigationBars = darkIcons
    }
    val density = LocalDensity.current
    val navBarInsetPx = WindowInsets.navigationBars.getBottom(density)
    val coverAreaHeightPx = (rootHeightPx - headerHeightPx - consoleHeightPx - navBarInsetPx).coerceAtLeast(0)
    val deckMaxHeight = with(density) { coverAreaHeightPx.toDp() * 0.88f }
    // موضع تراكب الملاحظة: أسفل الشريط العلوي وفوق المساحة الفارغة أعلى الغلاف مباشرةً —
    // لا يغطي الغلاف ولا العنوان ولا أدوات التحكّم، ولا يتقاطع مع شريحة الفصل العلوية.
    val noteTopPadding = if (headerBottomPx > 0) {
        with(density) { (headerBottomPx + AppSpacing.xs.roundToPx()).toDp() }
    } else {
        72.dp
    }
    val anyPopupOpen = expandedPanel != null ||
        saveMomentPosMs != null ||
        noteComposerPosMs != null ||
        chapterComposerPosMs != null
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
            notificationCenter?.showSaveMoment(pos)
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
            notificationCenter?.showSaveMoment(pos)
            captureNotice = CaptureNotice(R.string.player_note_saved, pos)
            noteComposerPosMs = null
        }
    }

    val addChapterAt: (Long, String) -> Unit = { pos, name ->
        if (marks != null && editionId != null) scope.launch {
            marks.addChapter(editionId, pos, title = name.ifBlank { "فصل جديد" })
            sleepTimer.onActiveInteraction(ActiveInteraction.CreateChapter)
        } else timeline = PlayerTimelineEditor.addChapter(timeline, pos, title = name.ifBlank { "فصل جديد" })
        notificationCenter?.showSaveMoment(pos)
        captureNotice = CaptureNotice(R.string.player_chapter_saved, pos)
    }

    // منطق موحّد لنقل الفصل: قاعدة البيانات إن توفّرت، وإلا الحالة المحلية (Preview).
    val onChapterMoved: (UUID, Long) -> Unit = { id, position ->
        val stored = storedChapters.firstOrNull { it.id == id }
        if (marks != null && stored != null) scope.launch { marks.updateChapter(stored, position) }
        else timeline = PlayerTimelineEditor.moveChapter(timeline, id, position)
    }

    val saveChapter: (String) -> Unit = { name ->
        chapterComposerPosMs?.let { pos ->
            addChapterAt(pos, name)
            chapterComposerPosMs = null
        }
    }

    // ---- عبور الملاحظات: تُعرض النصوص تباعًا (~٦ ثوانٍ لكل ملاحظة) مع نغمة قصيرة هادئة ----
    val noteTone = remember { NoteCueTone() }
    val noteAlerts = remember { Channel<NoteAlert>(Channel.UNLIMITED) }
    DisposableEffect(Unit) {
        onDispose {
            noteAlerts.close()
            noteTone.release()
        }
    }
    var lastWatchPosMs by remember { mutableStateOf(playback.positionMs) }
    var consumedNoteIds by remember { mutableStateOf(emptySet<UUID>()) }

    // جلسة تشغيل جديدة: إعادة ضبط كاملة لحالة العبور.
    LaunchedEffect(editionId) {
        consumedNoteIds = emptySet()
        lastWatchPosMs = playback.positionMs
    }

    // الكشف عن العبور: مرة واحدة لكل ملاحظة، مع إعادة تسليحها عند الرجوع للخلف فوقها.
    LaunchedEffect(playback.positionMs, renderedTimeline.bookmarks) {
        val notes = renderedTimeline.bookmarks.filter { it.label?.isNotBlank() == true }
        val current = playback.positionMs
        val previous = lastWatchPosMs
        if (current < previous - SEEK_BACK_THRESHOLD_MS) {
            consumedNoteIds = consumedNoteIds.filter { id ->
                notes.any { it.id == id && it.positionMs > current }
            }.toSet()
        }
        if (current > previous) {
            val crossed = notes
                .filter { it.id !in consumedNoteIds && it.positionMs > previous && it.positionMs <= current }
                .sortedBy { it.positionMs }
            if (crossed.isNotEmpty()) {
                consumedNoteIds = consumedNoteIds + crossed.map { it.id }
                crossed.forEach { noteAlerts.trySend(NoteAlert(it.label.orEmpty(), it.positionMs)) }
            }
        }
        lastWatchPosMs = current
    }

    // العرض التسلسلي: ملاحظة واحدة في كل مرة، بلا تكديس، وبلا أي تفاعل مطلوب.
    LaunchedEffect(Unit) {
        for (alert in noteAlerts) {
            activeNoteAlert = alert
            noteVisible = true
            noteTone.play()
            delay(NOTE_OVERLAY_VISIBLE_MS)
            noteVisible = false
            delay(NOTE_OVERLAY_GAP_MS)
        }
    }

    CompositionLocalProvider(LocalPlayerColors provides fg.colors) {
    // تمويه متدرج: يُشغَّل فور فتح أي نافذة منبثقة، مع تلاشي عكسي عند الإغلاق.
    val blurAmount by animateFloatAsState(
        targetValue = if (anyPopupOpen) 1f else 0f,
        animationSpec = tween(durationMillis = 320),
        label = "playerBlur"
    )
    Box(modifier = Modifier.fillMaxSize()) {
        // ---- الطبقة 1: كامل محتوى المشغّل (تُموَّه كطبقة واحدة خلف أي نافذة منبثقة) ----
        Box(
            modifier = Modifier
                .fillMaxSize()
                .blur(
                    radius = 14.dp * blurAmount,
                    edgeTreatment = BlurredEdgeTreatment.Unbounded
                )
                .onGloballyPositioned { rootHeightPx = it.size.height }
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
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = AppSpacing.md, end = AppSpacing.md, top = AppSpacing.xs)
                    .onGloballyPositioned {
                        headerHeightPx = it.size.height
                        headerBottomPx = (it.positionInRoot().y + it.size.height).roundToInt()
                    },
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
                val activeDeviceType = audioDevices.firstOrNull { it.id == activeDeviceId }?.type ?: AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                IconButton(onClick = { expandedPanel = if (expandedPanel == PlayerControlPanel.DEVICES) null else PlayerControlPanel.DEVICES }, modifier = Modifier.minTouchTarget()) {
                    Icon(outputDeviceTypeIcon(activeDeviceType), contentDescription = stringResource(R.string.player_device_icon_desc), tint = fg.ink)
                }
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
            }

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .onGloballyPositioned { consoleHeightPx = it.size.height }
            ) {
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
                onChapterMoved = onChapterMoved,
                onSeekToChapter = { target -> controller.seekTo(target) },
                onPreviewNote = { text, pos ->
                    noteAlerts.trySend(NoteAlert(text, pos))
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
                        color = fg.colors.accent
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
        }
        } // ---- نهاية الطبقة 1 (محتوى المشغّل المموّه) ----

        // ---- الطبقة 2: الحجاب الكامل + فوق المحتوى المموّه ----
        AnimatedVisibility(
            visible = anyPopupOpen,
            enter = fadeIn(tween(200)),
            exit = fadeOut(tween(200))
        ) {
            PopupScrim(
                scrimColor = fg.colors.scrim,
                onDismiss = {
                    expandedPanel = null
                    saveMomentPosMs = null
                    noteComposerPosMs = null
                    chapterComposerPosMs = null
                }
            )
        }

        // ---- لوحات الأدوات (السرعة/النوم/الفصول): فوق الحجاب، فوق منطقة الغلاف ----
        AnimatedVisibility(
            visible = expandedPanel != null && expandedPanel != PlayerControlPanel.MORE && expandedPanel != PlayerControlPanel.DEVICES,
            modifier = Modifier.fillMaxSize(),
            enter = fadeIn() + expandVertically(expandFrom = Alignment.CenterVertically),
            exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.CenterVertically)
        ) {
            Box(modifier = Modifier.fillMaxSize()) {
                UtilitiesDeck(
                    panel = expandedPanel,
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
                onSleepDecrease = { minutes -> sleepTimer.decreaseBy(minutes) },
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
                onChapterMoved = onChapterMoved,
                fg = fg,
                maxDeckHeight = deckMaxHeight,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = AppSpacing.md * 2)
                )
            }
        }

        // ---- ألواح الالتقاط: علامة / ملاحظة / فصل، وقائمة "المزيد" ----
        SaveMomentSheet(
            visible = saveMomentPosMs != null,
            capturedText = saveMomentPosMs?.let { stringResource(R.string.player_mark_captured, formatTime(it)) },
            fg = fg,
            onBookmark = { saveBookmark() },
            onNote = { noteComposerPosMs = saveMomentPosMs; saveMomentPosMs = null },
            onChapter = { chapterComposerPosMs = saveMomentPosMs; saveMomentPosMs = null },
            onDismiss = { saveMomentPosMs = null }
        )
        NoteComposeSheet(
            visible = noteComposerPosMs != null,
            capturedText = noteComposerPosMs?.let { stringResource(R.string.player_mark_captured, formatTime(it)) },
            fg = fg,
            onSave = { text -> saveNote(text) },
            onDismiss = { noteComposerPosMs = null }
        )
        ChapterComposeSheet(
            visible = chapterComposerPosMs != null,
            capturedText = chapterComposerPosMs?.let { stringResource(R.string.player_mark_captured, formatTime(it)) },
            fg = fg,
            onSave = { text -> saveChapter(text) },
            onDismiss = { chapterComposerPosMs = null }
        )
        MoreDeck(
            visible = expandedPanel == PlayerControlPanel.MORE,
            fg = fg,
            onClose = { expandedPanel = null },
            onSpeed = { expandedPanel = PlayerControlPanel.SPEED },
            onSleep = { expandedPanel = PlayerControlPanel.SLEEP },
            onChapters = { expandedPanel = PlayerControlPanel.CHAPTERS },
            onAddChapterHere = { addChapterAt(playback.positionMs, "") }
        )
        DeviceDeck(
            visible = expandedPanel == PlayerControlPanel.DEVICES,
            devices = audioDevices,
            activeDeviceId = activeDeviceId,
            canListBt = canListBt,
            fg = fg,
            onRequestPermission = { btPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT) },
            onSelect = { device ->
                val target = if (outputDeviceIsAutoRouted(device.type)) null else device
                val applied = controller.setPreferredAudioDevice(target)
                activeDeviceId = device.id
                if (!applied) Toast.makeText(context, R.string.player_device_switch_failed, Toast.LENGTH_SHORT).show()
            },
            onClose = { expandedPanel = null }
        )
        NoteOverlay(
            visible = noteVisible,
            alert = activeNoteAlert,
            headerTitle = stringResource(R.string.player_note_overlay_header),
            fg = fg,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = noteTopPadding)
                .padding(horizontal = AppSpacing.lg)
        )
    }
    }
}

private enum class PlayerControlPanel { SPEED, SLEEP, CHAPTERS, MORE, DEVICES }

private data class CaptureNotice(val resId: Int, val posMs: Long)

/** تنبيه ملاحظة عابر: النص الكامل + موضعها الزمني (للترويسة). */
private data class NoteAlert(val text: String, val positionMs: Long)

/** مدة بقاء تراكب الملاحظة ظاهرًا قبل التلاشي التلقائي. */
private const val NOTE_OVERLAY_VISIBLE_MS = 6_000L

/** فاصل قصير بين ملاحظتين متتاليتين حتى لا تتكدّسا. */
private const val NOTE_OVERLAY_GAP_MS = 350L

/** عتبة تمييز الرجوع للخلف (seek/restart) عن التقدّم الطبيعي أثناء التشغيل. */
private const val SEEK_BACK_THRESHOLD_MS = 2_000L

/**
 * أدوار الألوان في المشغّل — مصدر واحد مشتق من تدرج الكتاب.
 *
 * [accent]   → تعبئة/تلوين/تمييز فقط (شريط التقدم، عصا الإشارة، خيار مُحدَّد)
 * [onAccent] → نص/أيقونة فوق [accent] (يُختار تلقائيًا حسب سطوع [accent])
 * [glassBg]  → سطح زجاجي شفاف للوحات والبطاقات
 * [outline]  → حدود/فواصل — أبيض عند 14% (الليل) أو أسود عند 12% (النهار)
 * [scrim]    → طبقة حجب خلف النत्रح — 55% (ليل) أو 45% (نهار)
 *
 * [ink] و [soft] هما النص الأساسي والثانوي على التدرج مباشرة،
 * وهما متكيفان مع الوضع (فاتح/داكن).
 */
internal data class PlayerColors(
    val accent: Color,
    val onAccent: Color,
    val glassBg: Color,
    val outline: Color,
    val scrim: Color,
    val isLight: Boolean,
    val popupInk: Color,
    val popupSoft: Color,
    val popupOutline: Color,
    val popupSurface: Color
)

internal val LocalPlayerColors = staticCompositionLocalOf { PlayerColors(
    accent = Color.Unspecified, onAccent = Color.Unspecified,
    glassBg = Color.Unspecified, outline = Color.Unspecified, scrim = Color.Unspecified,
    isLight = true,
    popupInk = Color.Unspecified, popupSoft = Color.Unspecified,
    popupOutline = Color.Unspecified, popupSurface = Color.Unspecified
) }

/** مُجمّع أدوار المشغّل — حبر التدرج + أدوار النوافذ المنبثقة. */
internal data class PlayerFg(
    val ink: Color,
    val soft: Color,
    val colors: PlayerColors
) {
    @Deprecated("Use colors.accent instead", ReplaceWith("colors.accent"))
    val accent: Color get() = colors.accent
}

internal fun playerForeground(gradient: PlayerGradient, mode: AppThemeMode): PlayerFg {
    val isLightTheme = mode == AppThemeMode.LIGHT
    val isAmoled = mode == AppThemeMode.AMOLED
    // تشويب لون الحجاب: يُغمَّق أشد (18%) ليصبح أسود عميقًا ملوّنًا بلهجة المشغّل، لا أسود خالصًا.
    val scrimAccent = gradient.start.playerAccent(onLightBackground = isLightTheme)
    val lightText = isLightTheme && gradient.start.luminance() <= PlayerGradientResolver.DARK_INK_MIN_LUMINANCE
    val ink = if (isLightTheme && !lightText) Color(0xFF1F2A44) else Color.White
    val accent = scrimAccent
    val onAccent = if (accent.luminance() > 0.45f) Color(0xFF1A1A1A) else Color.White
    val glassBg = ink.copy(alpha = if (isLightTheme && !lightText) 0.06f else 0.10f)
    val outline = ink.copy(alpha = if (isLightTheme && !lightText) 0.12f else 0.14f)
    val scrim = accent.copy(
        red = accent.red * SCRIM_ACCENT_REDUCE,
        green = accent.green * SCRIM_ACCENT_REDUCE,
        blue = accent.blue * SCRIM_ACCENT_REDUCE,
        alpha = when {
            isAmoled -> 0.65f
            isLightTheme -> 0.40f
            else -> 0.50f
        }
    )
    val soft = ink.copy(alpha = if (isLightTheme && !lightText) 0.72f else 0.78f)
    val popupInk = if (isLightTheme && !lightText) Color(0xFF1D1B3B) else Color(0xFFEDF2FF)
    val popupSoft = if (isLightTheme && !lightText) Color(0xFF57537A) else Color(0xFFABB4CE)
    val popupOutline = if (isLightTheme) Color(0x4D000000) else Color(0x4DFFFFFF)
    val popupSurface = if (isLightTheme) Color(0x1E000000) else Color(0x1EFFFFFF)
    return PlayerFg(
        ink = ink,
        soft = soft,
        colors = PlayerColors(
            accent = accent,
            onAccent = onAccent,
            glassBg = glassBg,
            outline = outline,
            scrim = scrim,
            isLight = isLightTheme,
            popupInk = popupInk,
            popupSoft = popupSoft,
            popupOutline = popupOutline,
            popupSurface = popupSurface
        )
    )
}

/** نسبة تخفيض قنوات لون الحجاب لاشتقاق "أسود عميق ملوّن" من لهجة المشغّل. */
private const val SCRIM_ACCENT_REDUCE = 0.18f

/**
 * لهجة المشغّل الوحيدة: تُشتق دائمًا من بداية تدرج الكتاب (السلسلة/المؤلف/الغلاف)
 * — ليست لونًا ثابتًا، بل عائلة لونية من نفس مصدر التدرج، مكيّفة للتباين.
 */
internal fun Color.playerAccent(onLightBackground: Boolean): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.RGBToHSV(
        (red * 255f + 0.5f).toInt(),
        (green * 255f + 0.5f).toInt(),
        (blue * 255f + 0.5f).toInt(),
        hsv
    )
    hsv[1] = (hsv[1] * 1.4f).coerceAtMost(1f)
    return if (onLightBackground) {
        Color.hsv(hsv[0], hsv[1], (hsv[2] * 0.4f).coerceAtLeast(0.16f))
    } else {
        Color.hsv(hsv[0], hsv[1], (hsv[2] * 1.35f).coerceIn(0.52f, 0.94f))
    }
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

/** ختم زمني للملاحظة: س:د:ث عند تجاوز الساعة، وإلا د:ث. */
private fun formatNoteTimestamp(ms: Long): String {
    val totalSeconds = (ms / 1_000L).coerceAtLeast(0L)
    val hours = totalSeconds / 3_600L
    val minutes = (totalSeconds % 3_600L) / 60L
    val seconds = totalSeconds % 60L
    return if (hours > 0L) "%02d:%02d:%02d".format(hours, minutes, seconds)
    else "%02d:%02d".format(minutes, seconds)
}

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
            .border(width = 1.dp, color = fg.colors.outline, shape = shape),
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

/** اسم الفصل الحالي في الرأس — نص فقط بلا شارة/خلفية/حدود: ينفتح لوحة الفصول عند النقر. */
@Composable
private fun CurrentChapterChip(
    chapterLabel: String,
    onClick: () -> Unit,
    fg: PlayerFg,
    modifier: Modifier = Modifier
) {
    val description = stringResource(R.string.player_current_chapter_cd)
    Box(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(RoundedCornerShape(AppSpacing.sm))
            .clickable(onClick = onClick)
            .minTouchTarget()
            .semantics { this.contentDescription = description }
            .padding(vertical = 6.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Text(
            text = chapterLabel,
            style = MaterialTheme.typography.titleSmall,
            color = fg.ink,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

private fun speedLabel(speed: Float): String =
    "${"%.2f".format(speed).trimEnd('0').trimEnd('.')}×"

/**
 * الشريط الزمني الموحّد: "أين أنا؟" (الفصل الحالي) ثم الخيط الذي يحمل
 * علامات الفصول والإشارات ورأس التشغيل + الوقت المنقضي والمتبقّي.
 */
/** ارتفاع شريط مقابض الفصول (المنطقة "أ": سحب الفصل فقط). */
private val chapterStripHeight = 48.dp

/** ارتفاع العلامات البصرية في منطقة البحث (تُرسم بلا التقاط لمس). */
private val markerBarHeight = 46.dp

/** حالة سحب علامة فصل: الفصل + موضعه الحي (بالنطاق الزمني الكامل للنسخة). */
private data class ChapterDragState(val id: UUID, val timeMs: Long)

/** تحويل موضع الأفقي إلى توقيت داخل نافذة العرض المتاحة (خصوصًا للنسخة كاملة). */
private fun mkWindowTime(window: LongRange, fraction: Float): Long =
    window.first + (fraction * (window.last - window.first)).toLong()

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
    onSeekToChapter: (Long) -> Unit,
    onPreviewNote: (String, Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val positionFraction = if (visibleWindow.last <= visibleWindow.first) 0f
    else ((positionMs - visibleWindow.first).toFloat() / (visibleWindow.last - visibleWindow.first)).coerceIn(0f, 1f)
    var dragState by remember { mutableStateOf<ChapterDragState?>(null) }
    var scrubFraction by remember { mutableStateOf<Float?>(null) }
    val latestWindow = rememberUpdatedState(visibleWindow)
    val latestScrub = rememberUpdatedState(onScrub)
    val commitScrub: () -> Unit = {
        val target = scrubFraction
        if (target != null) latestScrub.value(mkWindowTime(latestWindow.value, target))
        scrubFraction = null
    }

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)
    ) {
        // ---- المنطقة "أ": شريط مقابض الفصول — سحب الفصل فقط (لا بحث هنا) ----
        ChapterHandleStrip(
            timelineState = timelineState,
            visibleWindow = visibleWindow,
            editing = editing,
            fg = fg,
            dragState = dragState,
            onDragState = { dragState = it },
            onChapterMoved = onChapterMoved
        )
        // ---- المنطقة "ب": مسار البحث — Slider (بحث سحبًا ونقرًا) + علامات بصرية + نقر العلامات ----
        SeekTrackZone(
            timelineState = timelineState,
            positionFraction = positionFraction,
            scrubFraction = scrubFraction,
            visibleWindow = visibleWindow,
            fg = fg,
            editing = editing,
            onScrubLive = { scrubFraction = it },
            onScrubCommit = commitScrub,
            onSeekToChapter = onSeekToChapter,
            onPreviewNote = onPreviewNote
        )

        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = AppSpacing.xs),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            val displayMs = scrubFraction?.let { mkWindowTime(latestWindow.value, it) } ?: positionMs
            Text(
                formatTime(displayMs),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = SpaceGroteskFamily),
                color = fg.ink
            )
            Text(
                stringResource(R.string.player_remaining, formatTime((durationMs - displayMs).coerceAtLeast(0L))),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = SpaceGroteskFamily),
                color = fg.soft
            )
        }
    }
}

/** منطقة "ب": مسار البحث — شريط تمرير فعّال (بحث سحبًا ونقرًا) + علامات بصرية + أزرار نقر عند كل علامة. */
@Composable
private fun SeekTrackZone(
    timelineState: PlayerTimelineState,
    positionFraction: Float,
    scrubFraction: Float?,
    visibleWindow: LongRange,
    fg: PlayerFg,
    editing: Boolean,
    onScrubLive: (Float) -> Unit,
    onScrubCommit: () -> Unit,
    onSeekToChapter: (Long) -> Unit,
    onPreviewNote: (String, Long) -> Unit
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val latestSeek = rememberUpdatedState(onSeekToChapter)
    val latestPreview = rememberUpdatedState(onPreviewNote)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = markerBarHeight)
            .testTag("seek-track-zone")
    ) {
        if (!editing) {
            Box(modifier = Modifier.fillMaxWidth().minTouchTarget()) {
                Slider(
                    value = scrubFraction ?: positionFraction,
                    onValueChange = onScrubLive,
                    onValueChangeFinished = onScrubCommit,
                    colors = SliderDefaults.colors(
                        thumbColor = fg.colors.accent,
                        activeTrackColor = fg.colors.accent,
                        inactiveTrackColor = fg.colors.outline.copy(alpha = 0.5f),
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent
                    )
                )
            }
        }
        SeekMarksCanvas(
            modifier = Modifier.matchParentSize(),
            timelineState = timelineState,
            visibleWindow = visibleWindow,
            editing = editing,
            fg = fg
        )
        SeekMarkerTapLayer(
            modifier = Modifier.matchParentSize(),
            timelineState = timelineState,
            visibleWindow = visibleWindow,
            isRtl = isRtl,
            onSeekToChapter = latestSeek.value,
            onPreviewNote = latestPreview.value
        )
    }
}

/** طبقة بصرية لاصقة: خطوط الفصول + الإشارات — لا تلتقط أي لمس (لجانب السحب/البحث) . */
@Composable
private fun SeekMarksCanvas(
    modifier: Modifier,
    timelineState: PlayerTimelineState,
    visibleWindow: LongRange,
    editing: Boolean,
    fg: PlayerFg
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier = modifier) {
        val drawableStart = 12.dp.toPx()
        val drawableEnd = size.width - drawableStart
        val span = drawableEnd - drawableStart
        fun markX(fraction: Float): Float = if (isRtl) drawableEnd - fraction * span else drawableStart + fraction * span
        val accent = fg.colors.accent
        val baseAlpha = if (editing) 0.85f else 0.55f
        timelineState.chapters.sortedBy { it.startPositionMs }.forEach { chapter ->
            val f = mkFraction(chapter.startPositionMs, visibleWindow)
            if (f in 0f..1f) {
                val x = markX(f)
                drawLine(
                    color = accent.copy(alpha = baseAlpha),
                    start = Offset(x, 0f),
                    end = Offset(x, size.height),
                    strokeWidth = 2.dp.toPx()
                )
            }
        }
        timelineState.bookmarks.forEach { bookmark ->
            val f = mkFraction(bookmark.positionMs, visibleWindow)
            if (f in 0f..1f) {
                val x = markX(f)
                if (bookmark.label?.isNotBlank() == true) {
                    // ملاحظة: مربّع صغير بلون التدرج (تمييز بصري عن العلامة بدرجة شفافية أعلى).
                    val r = 3.dp.toPx()
                    drawRect(
                        color = fg.colors.accent.copy(alpha = 0.85f),
                        topLeft = Offset(x - r, 12.dp.toPx() - r),
                        size = androidx.compose.ui.geometry.Size(r * 2, r * 2)
                    )
                } else {
                    drawCircle(
                        color = fg.colors.accent.copy(alpha = 0.60f),
                        radius = 3.dp.toPx(),
                        center = Offset(x, 12.dp.toPx())
                    )
                }
            }
        }
    }
}

/**
 * منطقة "ب" — طبقة نقر العلامات: أزرار صغيرة عند خط كل فصل (نقر = قفز إلى الفصل)
 * وعند كل إشارة/ملاحظة (نقر = معاينة النص). لا تُغطي غير موضع العلامة نفسها،
 * فتبقى النقرة على الخيط الحر موجّهة كليًا إلى Slider أدناه للبحث.
 */
@Composable
private fun SeekMarkerTapLayer(
    modifier: Modifier,
    timelineState: PlayerTimelineState,
    visibleWindow: LongRange,
    isRtl: Boolean,
    onSeekToChapter: (Long) -> Unit,
    onPreviewNote: (String, Long) -> Unit
) {
    val latestWindow = rememberUpdatedState(visibleWindow)
    val latestSeek = rememberUpdatedState(onSeekToChapter)
    val latestPreview = rememberUpdatedState(onPreviewNote)
    BoxWithConstraints(modifier = modifier) {
        val spanDp = maxWidth - 24.dp
        fun markerXDp(fraction: Float): Dp =
            if (isRtl) (maxWidth - 12.dp) - spanDp * fraction else 12.dp + spanDp * fraction
        timelineState.chapters.sortedBy { it.startPositionMs }.forEach { chapter ->
            val f = mkFraction(chapter.startPositionMs, latestWindow.value)
            if (f in 0f..1f) {
                val xDp = markerXDp(f)
                Box(
                    modifier = Modifier
                        .offset { IntOffset((xDp - 12.dp).toPx().roundToInt(), 0) }
                        .width(24.dp)
                        .fillMaxHeight()
                        .pointerInput(chapter.id, isRtl) {
                            detectTapGestures(
                                onTap = { latestSeek.value(chapter.startPositionMs) }
                            )
                        }
                ) {}
            }
        }
        timelineState.bookmarks.forEach { bookmark ->
            val f = mkFraction(bookmark.positionMs, latestWindow.value)
            if (f in 0f..1f) {
                val xDp = markerXDp(f)
                Box(
                    modifier = Modifier
                        .offset { IntOffset((xDp - 12.dp).toPx().roundToInt(), 0) }
                        .width(24.dp)
                        .fillMaxHeight()
                        .pointerInput(bookmark.id, isRtl) {
                            detectTapGestures(
                                onTap = { latestPreview.value(bookmark.label ?: formatTime(bookmark.positionMs), bookmark.positionMs) }
                            )
                        }
                ) {}
            }
        }
    }
}

/** منطقة "أ": شريط مقابض الفصول — مقبض مستقل لكل فصل (سحب مباشر دون ضغطة طويلة) + تلميح الوقت أثناء السحب. */
@Composable
private fun ChapterHandleStrip(
    timelineState: PlayerTimelineState,
    visibleWindow: LongRange,
    editing: Boolean,
    fg: PlayerFg,
    dragState: ChapterDragState?,
    onDragState: (ChapterDragState?) -> Unit,
    onChapterMoved: (UUID, Long) -> Unit
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    val latestWindow = rememberUpdatedState(visibleWindow)
    val latestDrag = rememberUpdatedState(dragState)
    val latestMove = rememberUpdatedState(onChapterMoved)
    val latestState = rememberUpdatedState(onDragState)
    BoxWithConstraints(
        modifier = Modifier
            .fillMaxWidth()
            .height(chapterStripHeight)
            .testTag("chapter-handle-strip")
            .pointerInput(isRtl, timelineState) {
                val inset = 12.dp.toPx()
                val stripSpan = size.width.toFloat() - inset * 2f
                val grabRadius = 24.dp.toPx()
                fun epochX(fraction: Float): Float =
                    if (isRtl) inset + stripSpan * (1f - fraction) else inset + stripSpan * fraction
                fun fractionAt(stripX: Float): Float {
                    val f = ((stripX - inset) / stripSpan).coerceIn(0f, 1f)
                    return if (isRtl) 1f - f else f
                }
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false)
                    val w = latestWindow.value
                    val inView = timelineState.chapters
                        .sortedBy { it.startPositionMs }
                        .filter { mkFraction(it.startPositionMs, w) in 0f..1f }
                    var grabId: UUID? = null
                    var bestDist = Float.MAX_VALUE
                    inView.forEach { chapter ->
                        val fx = epochX(mkFraction(chapter.startPositionMs, w))
                        val dist = kotlin.math.abs(down.position.x - fx)
                        if (dist <= grabRadius && dist < bestDist) {
                            bestDist = dist
                            grabId = chapter.id
                        }
                    }
                    val id = grabId ?: return@awaitEachGesture
                    val slop = awaitHorizontalTouchSlopOrCancellation(down.id) { change, _ ->
                        change.consume()
                    }
                    if (slop == null) return@awaitEachGesture
                    var timeMs = mkWindowTime(w, fractionAt(slop.position.x)).coerceIn(w.first, w.last)
                    latestState.value(ChapterDragState(id, timeMs))
                    try {
                        drag(down.id) { change ->
                            change.consume()
                            val ww = latestWindow.value
                            timeMs = mkWindowTime(ww, fractionAt(change.position.x)).coerceIn(ww.first, ww.last)
                            latestState.value((latestDrag.value ?: ChapterDragState(id, timeMs)).copy(timeMs = timeMs))
                        }
                    } finally {
                        latestState.value(null)
                    }
                    latestMove.value(id, timeMs)
                }
            }
    ) {
        val spanDp = maxWidth - 24.dp
        ChapterHandleMarksCanvas(
            modifier = Modifier.matchParentSize(),
            timelineState = timelineState,
            visibleWindow = visibleWindow,
            editing = editing,
            fg = fg,
            dragState = dragState
        )
        dragState?.let { state ->
            val f = mkFraction(state.timeMs, visibleWindow)
            val x = if (isRtl) (maxWidth - 12.dp) - spanDp * f else 12.dp + spanDp * f
            Box(
                modifier = Modifier
                    .absoluteOffset { IntOffset((x - 36.dp).toPx().roundToInt(), 0) }
                    .width(72.dp)
                    .clip(RoundedCornerShape(50))
                    .background(fg.colors.accent)
                    .padding(vertical = 4.dp, horizontal = 6.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    formatTime(state.timeMs),
                    style = MaterialTheme.typography.labelSmall.copy(fontFamily = SpaceGroteskFamily),
                    color = fg.colors.onAccent,
                    textAlign = TextAlign.Center,
                    maxLines = 1
                )
            }
        }
    }
}

/** الطبقة البصرية لشريط المقابض (منطقة أ): المقبض + خيط وصّال نحو علامة الفصل أسفله — بدون التقاط لمس. */
@Composable
private fun ChapterHandleMarksCanvas(
    modifier: Modifier,
    timelineState: PlayerTimelineState,
    visibleWindow: LongRange,
    editing: Boolean,
    fg: PlayerFg,
    dragState: ChapterDragState?
) {
    val isRtl = LocalLayoutDirection.current == LayoutDirection.Rtl
    Canvas(modifier = modifier) {
        val drawableStart = 12.dp.toPx()
        val drawableEnd = size.width - drawableStart
        val span = drawableEnd - drawableStart
        val handleY = 9.dp.toPx()
        val lineBottom = size.height - 5.dp.toPx()
        fun markX(fraction: Float): Float = if (isRtl) drawableEnd - fraction * span else drawableStart + fraction * span
        val accent = fg.colors.accent
        val baseAlpha = if (editing) 0.95f else 0.72f
        timelineState.chapters.sortedBy { it.startPositionMs }.forEach { chapter ->
            val f = mkFraction(chapter.startPositionMs, visibleWindow)
            if (f in 0f..1f) {
                val isDragged = dragState?.id == chapter.id
                val x = if (isDragged) markX(mkFraction(dragState!!.timeMs, visibleWindow)) else markX(f)
                if (isDragged) {
                    drawCircle(accent.copy(alpha = 0.22f), radius = 16.dp.toPx(), center = Offset(x, handleY))
                }
                drawLine(
                    color = accent.copy(alpha = if (isDragged) 1f else baseAlpha),
                    start = Offset(x, handleY),
                    end = Offset(x, lineBottom),
                    strokeWidth = if (isDragged) 3.5.dp.toPx() else 3.dp.toPx()
                )
                drawCircle(
                    color = accent.copy(alpha = if (isDragged) 1f else baseAlpha),
                    radius = if (isDragged) 10.dp.toPx() else 7.dp.toPx(),
                    center = Offset(x, handleY)
                )
                drawCircle(
                    color = fg.colors.onAccent.copy(alpha = 0.92f),
                    radius = 2.5.dp.toPx(),
                    center = Offset(x, handleY)
                )
            }
        }
    }
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
            Icon(Icons.Outlined.SkipPrevious, null, tint = fg.ink,
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
            Icon(Icons.Outlined.SkipNext, null, tint = fg.ink,
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
            .background(fg.colors.glassBg)
            .border(1.dp, fg.colors.outline, CircleShape)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = cd },
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleSmall.copy(fontWeight = FontWeight.SemiBold),
            color = fg.ink,
            maxLines = 1
        )
    }
}

/** زر التشغيل: مربّع بزوايا كبيرة ناعمة — تعبئة لحنية + رمز بلون متباين فوقها، توهّج خفيف. */
@Composable
private fun PlayButton(isPlaying: Boolean, onClick: () -> Unit, fg: PlayerFg, modifier: Modifier = Modifier) {
    val cd = stringResource(if (isPlaying) R.string.player_pause else R.string.player_play)
    val shape = RoundedCornerShape(24.dp)
    Box(
        modifier = modifier
            .size(76.dp)
            .shadow(10.dp, shape, ambientColor = fg.colors.accent.copy(alpha = 0.28f), spotColor = fg.colors.accent.copy(alpha = 0.42f))
            .clip(shape)
            .background(fg.colors.accent, shape)
            .border(1.dp, fg.colors.outline, shape)
            .clickable(onClick = onClick)
            .semantics { this.contentDescription = cd },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            if (isPlaying) Icons.Outlined.Pause else Icons.Outlined.PlayArrow,
            null,
            tint = fg.colors.onAccent,
            modifier = Modifier.size(36.dp)
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
        color = if (selected) fg.colors.accent else fg.soft,
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
    onSleepDecrease: (Int) -> Unit,
    onSleepCancel: () -> Unit,
    onAddChapter: () -> Unit,
    onSeekToChapter: (Long) -> Unit,
    onToggleEditing: () -> Unit,
    onChapterMoved: (UUID, Long) -> Unit,
    fg: PlayerFg,
    maxDeckHeight: Dp,
    modifier: Modifier = Modifier
) {
    if (panel == null) return
    val shape = RoundedCornerShape(24.dp)
    Column(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(max = maxDeckHeight)
            .clip(shape)
            .background(fg.colors.popupSurface, shape)
            .border(1.dp, fg.colors.popupOutline, shape)
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.md),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.sm)
    ) {
        when (panel) {
            PlayerControlPanel.SPEED -> {
                Text(stringResource(R.string.player_speed, speedLabel(selectedSpeed)), style = MaterialTheme.typography.titleSmall, color = fg.colors.popupInk)
                Slider(
                    value = selectedSpeed,
                    onValueChange = onSpeedChange,
                    valueRange = 0.5f..3f,
                    modifier = Modifier.fillMaxWidth(),
                    colors = SliderDefaults.colors(
                        thumbColor = fg.colors.accent,
                        activeTrackColor = fg.colors.accent,
                        inactiveTrackColor = fg.colors.outline.copy(alpha = 0.45f),
                        activeTickColor = Color.Transparent,
                        inactiveTickColor = Color.Transparent
                    )
                )
                Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                    listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f).forEach { preset ->
                        GlassPillButton(label = speedLabel(preset),
                            selected = kotlin.math.abs(selectedSpeed - preset) < 0.01f,
                            onClick = { onSpeedChange(preset) }, fg = fg, modifier = Modifier.weight(1f), compact = true)
                    }
                }
            }
            PlayerControlPanel.SLEEP -> {
                val phase = sleepUi.phase
                val isIdle = phase == SleepTimerPhase.IDLE || phase == SleepTimerPhase.STOPPED
                var choosingDuration by remember { mutableStateOf(false) }
                val showDurationPicker = isIdle || choosingDuration
                Text(
                    when {
                        isIdle -> stringResource(R.string.player_sleep_label)
                        else -> stringResource(R.string.player_sleep_active, formatTime(sleepUi.remainingMs ?: 0L))
                    },
                    style = MaterialTheme.typography.titleMedium,
                    color = fg.colors.popupInk
                )
                val sleepTotal = sleepUi.totalDurationMs ?: 0L
                if (!isIdle && sleepTotal > 0L) {
                    val remaining = sleepUi.remainingMs ?: 0L
                    LinearProgressIndicator(
                        progress = { (remaining.toFloat() / sleepTotal.toFloat()).coerceIn(0f, 1f) },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(6.dp)
                            .clip(RoundedCornerShape(50)),
                        color = fg.colors.accent,
                        trackColor = fg.colors.popupOutline
                    )
                }
                if (showDurationPicker) {
                    var showCustom by remember { mutableStateOf(false) }
                    var customMinutes by remember { mutableStateOf("") }
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                        listOf(15, 30, 45, 60).forEach { minutes ->
                            GlassPillButton(label = "$minutes", selected = false,
                                onClick = {
                                    choosingDuration = false
                                    onSleepStart(minutes)
                                }, fg = fg, modifier = Modifier.weight(1f), compact = true)
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
                                    .background(fg.colors.popupSurface)
                                    .border(1.dp, fg.colors.popupOutline, RoundedCornerShape(50))
                                    .padding(horizontal = AppSpacing.md, vertical = 8.dp)
                            ) {
                                BasicTextField(
                                    value = customMinutes,
                                    onValueChange = { customMinutes = it.filter(Char::isDigit).take(3) },
                                    singleLine = true,
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    textStyle = MaterialTheme.typography.labelLarge.copy(color = fg.colors.popupInk),
                                    decorationBox = { innerTextField ->
                                        if (customMinutes.isEmpty()) Text(
                                            stringResource(R.string.player_sleep_custom_hint),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = fg.colors.popupSoft
                                        )
                                        innerTextField()
                                    }
                                )
                            }
                            GlassPillButton(label = stringResource(R.string.player_sleep_custom_start),
                                selected = false,
                                primary = true,
                                onClick = {
                                    val minutes = customMinutes.toIntOrNull()
                                    if (minutes != null && minutes in 1..240) {
                                        choosingDuration = false
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
                        listOf(5, 10, 15, 30).forEach { minutes ->
                            GlassPillButton(label = stringResource(R.string.player_sleep_increase, minutes),
                                selected = false, primary = true,
                                onClick = { onSleepExtend(minutes) }, fg = fg, modifier = Modifier.weight(1f), compact = true)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                        listOf(5, 10, 15).forEach { minutes ->
                            GlassPillButton(label = stringResource(R.string.player_sleep_decrease, minutes),
                                selected = false,
                                onClick = { onSleepDecrease(minutes) }, fg = fg, modifier = Modifier.weight(1f), compact = true)
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
                        GlassPillButton(label = stringResource(R.string.player_sleep_change_duration),
                            selected = false,
                            onClick = { choosingDuration = true }, fg = fg, modifier = Modifier.weight(1f), compact = true)
                        GlassPillButton(label = stringResource(R.string.player_sleep_cancel_all),
                            selected = false, primary = true,
                            onClick = {
                                choosingDuration = false
                                onSleepCancel()
                            }, fg = fg, modifier = Modifier.weight(1f), compact = true)
                    }
                }
                if (sleepUi.isExtendWindowVisible) {
                    Text(stringResource(R.string.player_sleep_ending), style = MaterialTheme.typography.bodySmall, color = fg.colors.popupSoft)
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
            PlayerControlPanel.DEVICES -> {}
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
            color = fg.colors.popupInk
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
            GlassPillButton(icon = { Icon(Icons.Outlined.BookmarkAdd, null, tint = fg.colors.popupInk, modifier = Modifier.size(18.dp)) },
                label = stringResource(R.string.player_mark_chapter), selected = false, onClick = onAddChapter,
                fg = fg, modifier = Modifier.weight(1f))
            GlassPillButton(icon = { Icon(Icons.Outlined.MoreHoriz, null, tint = fg.colors.popupInk, modifier = Modifier.size(18.dp)) },
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
            .background(if (isCurrent) fg.colors.accent else Color.Transparent)
            .minTouchTarget()
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.sm, vertical = AppSpacing.xs),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isCurrent) {
            Box(modifier = Modifier.size(6.dp).clip(CircleShape).background(fg.colors.onAccent))
        } else {
            Spacer(Modifier.size(6.dp))
        }
        Text(
            text = stringResource(R.string.player_chapter_num, number) + " · " + title,
            style = MaterialTheme.typography.bodyMedium,
            color = if (isCurrent) fg.colors.onAccent else fg.colors.popupInk,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Column(horizontalAlignment = Alignment.End) {
            Text(
                text = startTimeLabel,
                style = MaterialTheme.typography.labelSmall.copy(fontFamily = SpaceGroteskFamily),
                color = if (isCurrent) fg.colors.onAccent.copy(alpha = 0.85f) else fg.colors.popupSoft
            )
            Text(
                text = formatTime(durationMs),
                style = MaterialTheme.typography.labelMedium.copy(fontFamily = SpaceGroteskFamily),
                color = if (isCurrent) fg.colors.onAccent.copy(alpha = 0.85f) else fg.colors.popupSoft
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
            .background(fg.colors.popupSurface)
            .border(1.dp, fg.colors.popupOutline, shape)
            .padding(2.dp)
    ) {
        labels.forEach { (label, level) ->
            val isSelected = level == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) fg.colors.accent else Color.Transparent)
                    .minTouchTarget()
                    .clickable(onClick = { onSelect(level) })
                    .padding(horizontal = AppSpacing.sm, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) fg.colors.onAccent else fg.colors.popupSoft,
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
    compact: Boolean = false,
    primary: Boolean = false
) {
    val shape = RoundedCornerShape(50)
    val isActive = selected || primary
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (isActive) fg.colors.accent else fg.colors.popupSurface)
            .border(1.dp, fg.colors.popupOutline, shape)
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
            color = if (isActive) fg.colors.onAccent else fg.colors.popupSoft,
            textAlign = TextAlign.Center,
            modifier = Modifier.weight(1f)
        )
    }
}

/**
 * الحجاب الموحّد خلف كل النوافذ المنبثقة: طبقة كاملة تغطي الشاشة فوق المحتوى المموّه.
 * لونها مشتق من لهجة المشغّل (أسود عميق ملوّن) — ليس أسود خالصًا. نقرة في أي مكان تُغلق النافذة.
 */
@Composable
private fun PopupScrim(
    scrimColor: Color,
    onDismiss: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(scrimColor)
            .clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null
            ) { onDismiss() }
    )
}

/**
 * لوحة زجاجية سفلية موحّدة (نفس لغة أدوات المشغّل): شفافة على التدرج، حبر/إضاءة متجاوبة،
 * تُستخدم لاختيار الهدف بعد "حفظ اللحظة"، ولتكوين الملاحظة، وقائمة "المزيد".
 * النقر خارج اللوحة يُغلقها عبر الحجاب الموحّد خلفها.
 */
@Composable
private fun BoxScope.GlassSheet(
    visible: Boolean,
    fg: PlayerFg,
    modifier: Modifier = Modifier,
    onDismiss: () -> Unit = {},
    content: @Composable ColumnScope.() -> Unit
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier.fillMaxSize(),
        enter = fadeIn() + expandVertically(expandFrom = Alignment.CenterVertically),
        exit = fadeOut() + shrinkVertically(shrinkTowards = Alignment.CenterVertically)
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            val shape = RoundedCornerShape(24.dp)
            Column(
                modifier = Modifier
                    .align(Alignment.Center)
                    .fillMaxWidth()
                    .padding(horizontal = AppSpacing.md * 2, vertical = AppSpacing.xs)
                    .clip(shape)
                    .background(fg.colors.popupSurface, shape)
                    .border(1.dp, fg.colors.popupOutline, shape)
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
            color = fg.colors.popupInk
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelMedium,
                color = fg.colors.popupSoft
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
            .background(fg.colors.popupSurface)
            .border(1.dp, fg.colors.popupOutline, shape)
            .minTouchTarget()
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(fg.colors.accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) { icon() }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = fg.colors.popupInk)
            if (description.isNotEmpty()) {
                Text(description, style = MaterialTheme.typography.bodySmall, color = fg.colors.popupSoft)
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
    onBookmark: () -> Unit,
    onNote: () -> Unit,
    onChapter: () -> Unit,
    onDismiss: () -> Unit
) {
    GlassSheet(visible = visible, fg = fg, onDismiss = onDismiss) {
        SheetHeadline(title = stringResource(R.string.player_save_moment), subtitle = capturedText, fg = fg)
        SheetOptionRow(
            icon = { Icon(Icons.Outlined.BookmarkAdd, null, modifier = Modifier.size(22.dp), tint = Color.White) },
            title = stringResource(R.string.player_mark_bookmark),
            description = "",
            onClick = onBookmark,
            fg = fg
        )
        SheetOptionRow(
            icon = { Icon(Icons.Outlined.EditNote, null, modifier = Modifier.size(22.dp), tint = Color.White) },
            title = stringResource(R.string.player_mark_note_opt),
            description = stringResource(R.string.player_mark_note_desc),
            onClick = onNote,
            fg = fg
        )
        SheetOptionRow(
            icon = { Icon(Icons.Outlined.AddCircle, null, modifier = Modifier.size(22.dp), tint = Color.White) },
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
    GlassSheet(visible = visible, fg = fg, onDismiss = onDismiss) {
        SheetHeadline(title = stringResource(R.string.player_note_title), subtitle = capturedText, fg = fg)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(fg.colors.popupSurface)
                .border(1.dp, fg.colors.popupOutline, RoundedCornerShape(16.dp))
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
        ) {
            BasicTextField(
                value = text,
                onValueChange = { if (it.length <= 240) text = it },
                modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
                maxLines = 3,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = fg.colors.popupInk),
                decorationBox = { innerTextField ->
                    if (text.isEmpty()) Text(
                        stringResource(R.string.player_note_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = fg.colors.popupSoft
                    )
                    innerTextField()
                }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            GlassPillButton(label = stringResource(R.string.player_note_cancel), selected = false, onClick = { text = ""; onDismiss() }, fg = fg, modifier = Modifier.weight(1f))
            GlassPillButton(label = stringResource(R.string.player_note_save), selected = false, primary = true, onClick = { if (text.isNotBlank()) onSave(text) }, fg = fg, modifier = Modifier.weight(1f))
        }
    }
}

/** تكوين فصل: اسم اختياري + إنشاء — عند موضع الالتقاط نفسه. */
@Composable
private fun BoxScope.ChapterComposeSheet(
    visible: Boolean,
    capturedText: String?,
    fg: PlayerFg,
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var name by remember { mutableStateOf("") }
    LaunchedEffect(visible) {
        if (visible) name = ""
    }
    GlassSheet(visible = visible, fg = fg, onDismiss = onDismiss) {
        SheetHeadline(title = stringResource(R.string.player_mark_chapter), subtitle = capturedText, fg = fg)
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(16.dp))
                .background(fg.colors.popupSurface)
                .border(1.dp, fg.colors.popupOutline, RoundedCornerShape(16.dp))
                .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm)
        ) {
            BasicTextField(
                value = name,
                onValueChange = { if (it.length <= 60) name = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                textStyle = MaterialTheme.typography.bodyLarge.copy(color = fg.colors.popupInk),
                decorationBox = { innerTextField ->
                    if (name.isEmpty()) Text(
                        stringResource(R.string.player_chapter_name_hint),
                        style = MaterialTheme.typography.bodyLarge,
                        color = fg.colors.popupSoft
                    )
                    innerTextField()
                }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(AppSpacing.xs)) {
            GlassPillButton(label = stringResource(R.string.player_note_cancel), selected = false, onClick = onDismiss, fg = fg, modifier = Modifier.weight(1f))
            GlassPillButton(label = stringResource(R.string.player_chapter_save), selected = false, primary = true, onClick = { onSave(name) }, fg = fg, modifier = Modifier.weight(1f))
        }
    }
}

/** قائمة "المزيد": إجراءات سريعة غير مكررة في صف الأدوات. */
@Composable
private fun BoxScope.MoreDeck(
    visible: Boolean,
    fg: PlayerFg,
    onClose: () -> Unit,
    onSpeed: () -> Unit,
    onSleep: () -> Unit,
    onChapters: () -> Unit,
    onAddChapterHere: () -> Unit
) {
    GlassSheet(visible = visible, fg = fg, onDismiss = onClose) {
        SheetHeadline(title = stringResource(R.string.player_more_menu), subtitle = null, fg = fg)
        SheetOptionRow(
            icon = { Icon(Icons.Outlined.AddCircle, null, modifier = Modifier.size(22.dp), tint = Color.White) },
            title = stringResource(R.string.player_more_chapter_now),
            description = stringResource(R.string.player_mark_chapter_desc),
            onClick = { onAddChapterHere(); onClose() },
            fg = fg
        )
        SheetOptionRow(
            icon = { Icon(Icons.Outlined.Speed, null, modifier = Modifier.size(22.dp), tint = Color.White) },
            title = stringResource(R.string.player_speed_short),
            description = "",
            onClick = onSpeed,
            fg = fg
        )
        SheetOptionRow(
            icon = { Icon(Icons.Outlined.Bedtime, null, modifier = Modifier.size(22.dp), tint = Color.White) },
            title = stringResource(R.string.player_sleep_short),
            description = "",
            onClick = onSleep,
            fg = fg
        )
        SheetOptionRow(
            icon = { Icon(Icons.Outlined.LibraryBooks, null, modifier = Modifier.size(22.dp), tint = Color.White) },
            title = stringResource(R.string.player_chapters_short),
            description = "",
            onClick = onChapters,
            fg = fg
        )
    }
}

/**
 * تراكب الملاحظة: سطح زجاجي معتم يظهر فوق منطقة الشريط الزمني مباشرةً (فوق أزرار
 * النقل) — لا يغطي الغلاف ولا العنوان ولا أدوات التحكّم. يحمل ترويسة (أيقونة + عنوان
 * + ختم زمني) وفاصلًا رقيقًا ثم النص الكامل بلا اقتطاع، ويختفي تلقائيًا بلا تفاعل.
 */
@Composable
private fun NoteOverlay(
    visible: Boolean,
    alert: NoteAlert?,
    headerTitle: String,
    fg: PlayerFg,
    modifier: Modifier = Modifier
) {
    AnimatedVisibility(
        visible = visible,
        modifier = modifier,
        enter = fadeIn(tween(220)),
        exit = fadeOut(tween(320))
    ) {
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            val shape = RoundedCornerShape(20.dp)
            Column(
                modifier = Modifier
                    .widthIn(min = 240.dp, max = 460.dp)
                    .clip(shape)
                    .background(fg.colors.scrim)
                    .background(fg.colors.popupSurface)
                    .border(1.dp, fg.colors.popupOutline, shape)
                    .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.xxs)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Outlined.StickyNote2,
                        contentDescription = null,
                        tint = fg.colors.accent,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(Modifier.width(AppSpacing.xs))
                    Text(
                        text = headerTitle,
                        style = MaterialTheme.typography.labelLarge,
                        color = fg.colors.popupSoft,
                        modifier = Modifier.weight(1f)
                    )
                    if (alert != null) {
                        Text(
                            text = formatNoteTimestamp(alert.positionMs),
                            style = MaterialTheme.typography.labelMedium.copy(fontFamily = SpaceGroteskFamily),
                            color = fg.colors.popupSoft
                        )
                    }
                }
                HorizontalDivider(color = fg.colors.popupOutline)
                if (alert != null) {
                    Text(
                        text = alert.text,
                        style = MaterialTheme.typography.bodyLarge,
                        color = fg.colors.popupInk,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }
    }
}

/** نغمة تلميح قصيرة تُشغَّل عند عبور ملاحظة؛ تُنشأ كسولًا وتُحرَّر عند مغادرة الشاشة. */
private class NoteCueTone {
    private var tone: ToneGenerator? = null
    fun play() {
        try {
            if (tone == null) tone = ToneGenerator(AudioManager.STREAM_MUSIC, NOTE_CUE_VOLUME)
            tone?.startTone(ToneGenerator.TONE_PROP_PROMPT, NOTE_CUE_DURATION_MS)
        } catch (_: Throwable) {
            // بعض الأجهزة لا توفّر ToneGenerator؛ تجاهل صامت.
        }
    }
    fun release() {
        try { tone?.release() } catch (_: Throwable) { }
        tone = null
    }
}

/** مستوى منخفض جدًا للنغمة (٠–١٠٠) فوق مسار الوسائط — هادئة، وتخضع لصوت النظام. */
private const val NOTE_CUE_VOLUME = 22

/** مدة النغمة: قصيرة جدًا، بلا تكرار ولا إنذار. */
private const val NOTE_CUE_DURATION_MS = 140

/** مخرجات الصوت ذات الصلة بلوحة "جهاز الصوت" (نسقط قنوات الاتصالات والاستماع الداخلي). */
private fun isRelevantOutputType(type: Int): Boolean = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE, AudioDeviceInfo.TYPE_BUILTIN_SPEAKER,
    AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_HEARING_AID,
    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_HDMI_EARC,
    AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL -> true
    else -> false
}

/** ترتيب العرض: الخارجي (بلوتوث ثم سلكي ثم USB ثم HDMI) ثم سماعة الهاتف ثم مكبر الصوت. */
private fun outputDeviceSortKey(type: Int): Int = when (type) {
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_HEARING_AID -> 0
    AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> 1
    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> 2
    AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_HDMI_EARC -> 3
    AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL -> 4
    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> 5
    else -> 6
}

/** مكبر الصوت يُدار عبر التوجيه التلقائي للنظام (بلا جهاز مفضّل). */
private fun outputDeviceIsAutoRouted(type: Int): Boolean =
    type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER

private fun outputDeviceTypeIcon(type: Int): ImageVector = when (type) {
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_HEARING_AID ->
        Icons.Outlined.Bluetooth
    AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_HDMI_EARC,
    AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL -> Icons.Outlined.Headphones
    else -> Icons.Outlined.VolumeUp
}

private fun outputDeviceTypeLabelRes(type: Int): Int = when (type) {
    AudioDeviceInfo.TYPE_BUILTIN_EARPIECE -> R.string.player_device_earpiece
    AudioDeviceInfo.TYPE_BUILTIN_SPEAKER -> R.string.player_device_speaker
    AudioDeviceInfo.TYPE_WIRED_HEADPHONES -> R.string.player_device_headphones
    AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_LINE_ANALOG, AudioDeviceInfo.TYPE_LINE_DIGITAL -> R.string.player_device_headset
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO, AudioDeviceInfo.TYPE_HEARING_AID -> R.string.player_device_bluetooth
    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET -> R.string.player_device_usb
    AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_HDMI_EARC -> R.string.player_device_hdmi
    else -> R.string.player_device_unknown
}

private fun outputDeviceUsesNamedLabel(type: Int): Boolean = when (type) {
    AudioDeviceInfo.TYPE_BLUETOOTH_A2DP, AudioDeviceInfo.TYPE_BLUETOOTH_SCO,
    AudioDeviceInfo.TYPE_WIRED_HEADSET, AudioDeviceInfo.TYPE_WIRED_HEADPHONES,
    AudioDeviceInfo.TYPE_USB_DEVICE, AudioDeviceInfo.TYPE_USB_HEADSET,
    AudioDeviceInfo.TYPE_HDMI, AudioDeviceInfo.TYPE_HDMI_ARC, AudioDeviceInfo.TYPE_HDMI_EARC -> true
    else -> false
}

private fun outputDeviceLabel(context: Context, device: AudioDeviceInfo): String {
    val name = device.productName?.toString()?.trim().orEmpty()
    return if (outputDeviceUsesNamedLabel(device.type) && name.isNotBlank()) name
    else context.getString(outputDeviceTypeLabelRes(device.type))
}

/** لوحة "جهاز الصوت": تبديل مخرج الصوت بنفس هيكل لوحات Speed/Sleep (حجاب + زجاج + تمركز). */
@Composable
private fun BoxScope.DeviceDeck(
    visible: Boolean,
    devices: List<AudioDeviceInfo>,
    activeDeviceId: Int?,
    canListBt: Boolean,
    fg: PlayerFg,
    onRequestPermission: () -> Unit,
    onSelect: (AudioDeviceInfo) -> Unit,
    onClose: () -> Unit
) {
    GlassSheet(visible = visible, fg = fg, onDismiss = onClose) {
        val context = LocalContext.current
        val active = devices.firstOrNull { it.id == activeDeviceId }
        SheetHeadline(
            title = stringResource(R.string.player_device_output),
            subtitle = if (active != null) stringResource(R.string.player_device_current, outputDeviceLabel(context, active))
            else stringResource(R.string.player_device_available),
            fg = fg
        )
        if (!canListBt) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(fg.colors.popupSurface)
                    .border(1.dp, fg.colors.popupOutline, RoundedCornerShape(16.dp))
                    .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
                horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    stringResource(R.string.player_device_no_permission),
                    style = MaterialTheme.typography.titleSmall,
                    color = fg.colors.popupSoft,
                    modifier = Modifier.weight(1f)
                )
                GlassPillButton(label = stringResource(R.string.player_device_grant), selected = false, onClick = onRequestPermission, fg = fg)
            }
        }
        if (devices.isEmpty()) {
            Text(
                stringResource(R.string.player_device_unknown),
                style = MaterialTheme.typography.bodyMedium,
                color = fg.colors.popupSoft
            )
        }
        devices.forEach { device ->
            DeviceOptionRow(
                icon = { Icon(outputDeviceTypeIcon(device.type), null, modifier = Modifier.size(22.dp), tint = Color.White) },
                title = outputDeviceLabel(context, device),
                selected = device.id == activeDeviceId,
                onClick = { onSelect(device) },
                fg = fg
            )
        }
    }
}

/** صف جهاز صوتي: أيقونة + اسم + علامة "محدد" للجهاز النشط. */
@Composable
private fun DeviceOptionRow(
    icon: @Composable () -> Unit,
    title: String,
    selected: Boolean,
    onClick: () -> Unit,
    fg: PlayerFg
) {
    val shape = RoundedCornerShape(16.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(if (selected) fg.colors.accent.copy(alpha = 0.14f) else fg.colors.popupSurface)
            .border(1.dp, if (selected) fg.colors.accent.copy(alpha = 0.55f) else fg.colors.popupOutline, shape)
            .minTouchTarget()
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.md, vertical = AppSpacing.sm),
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.sm),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier.size(40.dp).clip(CircleShape).background(fg.colors.accent.copy(alpha = 0.18f)),
            contentAlignment = Alignment.Center
        ) { icon() }
        Text(title, style = MaterialTheme.typography.titleSmall, color = fg.colors.popupInk, modifier = Modifier.weight(1f))
        if (selected) {
            Icon(Icons.Outlined.Check, contentDescription = null, modifier = Modifier.size(20.dp), tint = fg.colors.accent)
        }
    }
}