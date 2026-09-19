package com.example.audiobook

import android.os.Bundle
import android.os.Build
import android.Manifest
import android.content.Intent
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Leaderboard
import androidx.compose.material.icons.outlined.MenuBook
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.audiobook.background.reminders.ReminderScheduler
import com.example.audiobook.background.scanworker.ScanScheduler
import com.example.audiobook.data.preferences.AppSettings
import com.example.audiobook.data.room.dao.StatisticsDao
import com.example.audiobook.domain.usecases.RecoverInterruptedSession
import com.example.audiobook.notifications.AtherNotificationCenter
import com.example.audiobook.presentation.home.HomeScreen
import com.example.audiobook.presentation.home.ListeningHubScreen
import com.example.audiobook.presentation.library.LibraryScreen
import com.example.audiobook.presentation.bookdetails.BookDetailsScreen
import com.example.audiobook.presentation.entitydetails.SeriesDetailsScreen
import com.example.audiobook.presentation.entitydetails.AuthorDetailsScreen
import com.example.audiobook.presentation.entitydetails.CollectionDetailsScreen
import com.example.audiobook.presentation.player.PlayerScreen
import com.example.audiobook.presentation.player.MiniPlayer
import com.example.audiobook.presentation.bookmarks.BookmarksScreen
import com.example.audiobook.presentation.saved.SavedScreen
import com.example.audiobook.presentation.theme.AppSpacing
import com.example.audiobook.domain.model.AppThemeMode
import com.example.audiobook.presentation.theme.AudiobookTheme
import com.example.audiobook.presentation.theme.CosmicBackground
import com.example.audiobook.presentation.theme.CosmicHeaderState
import com.example.audiobook.presentation.theme.CosmicTopBar
import com.example.audiobook.presentation.theme.LocalAppAccent
import com.example.audiobook.presentation.theme.LocalBottomBarInset
import com.example.audiobook.presentation.theme.LocalCosmicHeader
import com.example.audiobook.presentation.theme.cosmicGlassStyle
import com.example.audiobook.presentation.theme.minTouchTarget
import com.example.audiobook.presentation.theme.navBarGlassStyle
import com.example.audiobook.presentation.theme.navLogoGlassStyle
import com.example.audiobook.presentation.reviewmatches.ReviewMatchesScreen
import com.example.audiobook.presentation.reviewmatches.ReviewMatchesViewModel
import com.example.audiobook.presentation.splash.AtherSplash
import com.example.audiobook.presentation.onboarding.OnboardingScreen
import com.example.audiobook.presentation.settings.SettingsScreen
import com.example.audiobook.presentation.libraryroots.LibraryRootsScreen
import com.example.audiobook.presentation.libraryroots.LibraryRootsViewModel
import com.example.audiobook.playback.PlaybackController
import com.example.audiobook.playback.SleepTimerController
import com.example.audiobook.domain.usecases.DatabaseSeeder
import com.example.audiobook.domain.usecases.MarksCoordinator
import dagger.hilt.android.AndroidEntryPoint
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.haze
import dev.chrisbanes.haze.hazeChild
import dev.chrisbanes.haze.rememberHazeState
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var recoverInterruptedSession: RecoverInterruptedSession
    @Inject lateinit var scanScheduler: ScanScheduler
    @Inject lateinit var reminderScheduler: ReminderScheduler
    @Inject lateinit var playbackController: PlaybackController
    @Inject lateinit var sleepTimerController: SleepTimerController
    @Inject lateinit var marksCoordinator: MarksCoordinator
    @Inject lateinit var databaseSeeder: DatabaseSeeder
    @Inject lateinit var appSettings: AppSettings
    @Inject lateinit var statisticsDao: StatisticsDao
    @Inject lateinit var notificationCenter: AtherNotificationCenter
    private val libraryRootsViewModel: LibraryRootsViewModel by viewModels()
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val pendingNotificationRoute = kotlinx.coroutines.flow.MutableStateFlow<String?>(null)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        intent.getStringExtra(AtherNotificationCenter.EXTRA_ROUTE)
            ?.let { pendingNotificationRoute.value = it }
        setIntent(intent)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        pendingNotificationRoute.value = intent.getStringExtra(AtherNotificationCenter.EXTRA_ROUTE)
        enableEdgeToEdge()
        lifecycleScope.launch { recoverInterruptedSession() }
        lifecycleScope.launch { scanScheduler.scheduleStartupScans() }
        reminderScheduler.syncWithSettings()
        if (BuildConfig.DEBUG) {
            lifecycleScope.launch { databaseSeeder() }
        }
        setContent {
            val navController = rememberNavController()
            var showSplash by remember { mutableStateOf(true) }
            val hasOnboarded by appSettings.hasCompletedOnboarding.collectAsStateWithLifecycle()
            val mode by appSettings.themeMode.collectAsStateWithLifecycle()
            AudiobookTheme(mode) {
                CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                    Surface(modifier = Modifier.fillMaxSize()) {
                        if (showSplash) {
                            AtherSplash(onFinished = { showSplash = false })
                        } else if (!hasOnboarded) {
                            OnboardingScreen(
                                onFinish = { appSettings.setHasCompletedOnboarding(true) },
                                onAddFolder = { navController.navigate("library_roots") }
                            )
                        } else {
                            AudiobookApp(
                                navController = navController,
                                mode = mode
                            )
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun AudiobookApp(navController: NavHostController, mode: AppThemeMode) {
        val header = remember { CosmicHeaderState() }
        val hazeState = rememberHazeState()
        val backStackEntry by navController.currentBackStackEntryAsState()
        val currentRoute = backStackEntry?.destination?.route
        val currentRouteBase = routeBase(currentRoute)
        val isPlayerRoute = currentRouteBase?.startsWith("player") == true
        val showBottomBar = !isPlayerRoute

        val playbackState by playbackController.state.collectAsStateWithLifecycle()
        val showMiniPlayer = playbackState.editionId != null && !isPlayerRoute

        // الإزاحة السفلية للواجهة (المشغّل المصغّر + الفجوة + الشريط + هامش النظام).
        // مصدر واحد للحقيقة يُمرَّر عبر LocalBottomBarInset؛ يتحدّث تلقائيًا عند
        // ظهور/اختفاء المشغّل المصغّر. القياس الفعلي عبر onSizeChanged يُحسّن القيمة،
        // لكن على أول إطار تركيب لا يكون القياس قد اكتمل بعد — لذا نبدأ بتقدير معماري
        // غير صفري (شريط 72dp + هامشه السفلي sm + هامش النظام + المشغّل المصغّر ~64dp
        // + فجوة md) كحدّ أدنى؛ فينتج المحتوى إزاحة صحيحة من اللحظة الأولى حتى للشاشات
        // المفتوحة فورًا (كتفاصيل الكتاب) قبل أن يصدر القياس.
        val density = LocalDensity.current
        val navBarBottomDp = with(density) { WindowInsets.navigationBars.getBottom(density).toDp() }
        val chromeEstimate = if (showBottomBar || showMiniPlayer) {
            navBarBottomDp + 72.dp + AppSpacing.sm + if (showMiniPlayer) (AppSpacing.md + 64.dp) else 0.dp
        } else 0.dp
        var bottomChromeInset by remember(chromeEstimate) { mutableStateOf(chromeEstimate) }
        val bottomBarInset = if (showBottomBar || showMiniPlayer) bottomChromeInset else 0.dp

        // Auto-Resume: إن كان مفعّلًا، افتح آخر كتاب قيد الاستماع عند موضعه المحفوظ.
        LaunchedEffect(Unit) {
            if (!appSettings.autoResume.value) return@LaunchedEffect
            val lastInProgress = statisticsDao.getInProgressRows().firstOrNull() ?: return@LaunchedEffect
            navController.navigate("player/${lastInProgress.editionId}") { launchSingleTop = true }
        }

        // فتح مسار مُرسَل من إشعار (المكتبة / المشغّل).
        val notificationRoute by pendingNotificationRoute.collectAsStateWithLifecycle()
        LaunchedEffect(notificationRoute) {
            notificationRoute?.let { route ->
                navController.navigate(route) { launchSingleTop = true }
                pendingNotificationRoute.value = null
            }
        }

        SideEffect {
            val controller = WindowInsetsControllerCompat(window, window.decorView)
            controller.isAppearanceLightStatusBars = mode == AppThemeMode.LIGHT
            controller.isAppearanceLightNavigationBars = mode == AppThemeMode.LIGHT
        }

        CompositionLocalProvider(
            LocalCosmicHeader provides header,
            LocalBottomBarInset provides bottomBarInset
        ) {
            Box(modifier = Modifier.fillMaxSize().haze(hazeState)) {
                CosmicBackground(mode = mode, modifier = Modifier.fillMaxSize())

                Box(
                    modifier = Modifier.fillMaxSize()
                        .windowInsetsPadding(WindowInsets.statusBars.only(WindowInsetsSides.Top))
                ) {
                    appNavHost(navController, mode)
                }

                // لا شريط علوي مثبّت في "الرئيسية" ولا في "الإعدادات": عنوان الإعدادات
                // يتحرّك مع المحتوى ويختفي بالتمرير (العنصر المثبّت الوحيد هو شريط التنقل السفلي).
                if (header.title.isNotBlank() && header.collapsed && currentRouteBase != "home" && currentRouteBase != "settings") {
                    CosmicTopBar(
                        title = header.title,
                        subtitle = header.subtitle,
                        onBack = header.onBack,
                        modifier = Modifier
                            .align(Alignment.TopCenter)
                            .clip(RoundedCornerShape(bottomStart = 24.dp, bottomEnd = 24.dp))
                            .hazeChild(hazeState, cosmicGlassStyle())
                    )
                }

                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .windowInsetsPadding(WindowInsets.navigationBars.only(WindowInsetsSides.Bottom))
                        .onSizeChanged { size ->
                            if (showBottomBar || showMiniPlayer) {
                                bottomChromeInset = with(density) { size.height.toDp() }
                            }
                        },
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.md, Alignment.Bottom)
                ) {
                    if (showMiniPlayer) {
                        MiniPlayer(
                            editionId = playbackState.editionId!!,
                            controller = playbackController,
                            haze = hazeState,
                            mode = mode,
                            onClick = { navigateToPlayer(navController, playbackState.editionId!!) }
                        )
                    }

                    if (showBottomBar) {
                        AppBottomBar(
                            mode = mode,
                            currentRoute = currentRoute,
                            onNavigate = { route -> navigateToTab(navController, route) },
                            onHome = { navigateToTab(navController, "home") },
                            haze = hazeState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AppSpacing.md)
                                .padding(bottom = AppSpacing.sm)
                                .shadow(
                                    elevation = 18.dp,
                                    shape = RoundedCornerShape(28.dp),
                                    ambientColor = Color.Black.copy(alpha = 0.45f),
                                    spotColor = Color.Black.copy(alpha = 0.45f)
                                )
                                .clip(RoundedCornerShape(28.dp))
                                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.86f), RoundedCornerShape(28.dp))
                                .border(
                                    width = 1.dp,
                                    color = MaterialTheme.colorScheme.onSurface.copy(alpha = if (mode == AppThemeMode.LIGHT) 0.16f else 0.12f),
                                    shape = RoundedCornerShape(28.dp)
                                )
                                .hazeChild(hazeState, navBarGlassStyle(mode))
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun appNavHost(navController: NavHostController, mode: AppThemeMode) {
        NavHost(
            navController = navController,
            startDestination = "home"
        ) {
            composable("home") {
                HomeScreen(
                    onOpenPlayer = { editionId -> navController.navigate("player/$editionId") },
                    onOpenLibrarySection = { section -> navController.navigate("library?section=$section") },
                    onBookSelected = { bookId -> navController.navigate("book_details/$bookId") },
                    onOpenHistory = { navController.navigate("history") },
                    onOpenSeries = { seriesId -> navController.navigate("series_details/$seriesId") },
                    onOpenAuthor = { authorId -> navController.navigate("author_details/$authorId") },
                    onOpenCollection = { collectionId -> navController.navigate("collection_details/$collectionId") },
                    onOpenListenNow = { navController.navigate("listen_now") }
                )
            }
            composable("listen_now") {
                ListeningHubScreen(
                    onOpenPlayer = { editionId -> navController.navigate("player/$editionId") },
                    onPlayWithSleepTimer = { editionId ->
                        sleepTimerController.start(appSettings.defaultSleepMinutes.value)
                        navController.navigate("player/$editionId")
                    },
                    onBookSelected = { bookId -> navController.navigate("book_details/$bookId") },
                    onOpenSeries = { seriesId -> navController.navigate("series_details/$seriesId") },
                    onOpenLibrarySection = { section -> navController.navigate("library?section=$section") },
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "library?section={section}",
                arguments = listOf(navArgument("section") { type = NavType.StringType; defaultValue = "ALL_BOOKS" })
            ) { entry ->
                val section = entry.arguments?.getString("section") ?: "ALL_BOOKS"
                val reviewViewModel: ReviewMatchesViewModel = hiltViewModel()
                val reviewState by reviewViewModel.uiState.collectAsStateWithLifecycle()
                LibraryScreen(
                    initialSection = section,
                    onBookSelected = { bookId -> navController.navigate("book_details/$bookId") },
                    onOpenPlayer = { editionId -> navController.navigate("player/$editionId") },
                    onManageRoots = { navController.navigate("library_roots") },
                    onStatistics = { navController.navigate("statistics") },
                    onHistory = { navController.navigate("history") },
                    onReviewMatches = { navController.navigate("review_matches") },
                    reviewBadgeCount = reviewState.summary.suspectCases,
                    onSettings = { navController.navigate("settings") }
                )
            }
            composable(
                route = "book_details/{bookId}",
                arguments = listOf(navArgument("bookId") { type = NavType.StringType })
            ) {
                BookDetailsScreen(
                    onBack = { navController.popBackStack() },
                    onPlay = { editionId ->
                        navController.navigate("player/$editionId")
                    },
                    onPlayAt = { editionId, startMs ->
                        navController.navigate("player/$editionId?startMs=$startMs")
                    },
                    onBookmarks = { editionId ->
                        navController.navigate("bookmarks/$editionId")
                    }
                )
            }
            composable(
                route = "series_details/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) {
                SeriesDetailsScreen(
                    onBack = { navController.popBackStack() },
                    onBookSelected = { bookId -> navController.navigate("book_details/$bookId") },
                    onAuthorSelected = { authorId -> navController.navigate("author_details/$authorId") }
                )
            }
            composable(
                route = "author_details/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) {
                AuthorDetailsScreen(
                    onBack = { navController.popBackStack() },
                    onBookSelected = { bookId -> navController.navigate("book_details/$bookId") },
                    onSeriesSelected = { seriesId -> navController.navigate("series_details/$seriesId") }
                )
            }
            composable(
                route = "collection_details/{id}",
                arguments = listOf(navArgument("id") { type = NavType.StringType })
            ) {
                CollectionDetailsScreen(
                    onBack = { navController.popBackStack() },
                    onBookSelected = { bookId -> navController.navigate("book_details/$bookId") }
                )
            }
            composable(
                route = "player/{editionId}?startMs={startMs}",
                arguments = listOf(
                    navArgument("editionId") { type = NavType.StringType },
                    navArgument("startMs") { type = NavType.LongType; defaultValue = -1L }
                )
            ) { entry ->
                val startMs = entry.arguments?.getLong("startMs") ?: -1L
                PlayerScreen(
                    controller = playbackController,
                    themeMode = mode,
                    marks = marksCoordinator,
                    sleepTimer = sleepTimerController,
                    notificationCenter = notificationCenter,
                    onFirstPlaybackPermissionRequest = {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    },
                    initialPositionMs = startMs,
                    onBack = { navController.popBackStack() }
                )
            }
            composable(
                route = "bookmarks/{editionId}",
                arguments = listOf(navArgument("editionId") { type = NavType.StringType })
            ) {
                BookmarksScreen(
                    controller = playbackController,
                    onBack = { navController.popBackStack() }
                )
            }
            composable("library_roots") {
                LibraryRootsScreen(libraryRootsViewModel) { navController.popBackStack() }
            }
            composable("statistics") {
                com.example.audiobook.presentation.statistics.StatisticsScreen(
                    onBack = { navController.popBackStack() },
                    showBack = false,
                    onOpenBook = { bookId -> navController.navigate("book_details/$bookId") },
                    onShowHistory = { navController.navigate("history") }
                )
            }
            composable("history") {
                com.example.audiobook.presentation.statistics.HistoryScreen(
                    onBack = { navController.popBackStack() },
                    onOpenBook = { bookId -> navController.navigate("book_details/$bookId") }
                )
            }
            composable("review_matches") {
                ReviewMatchesScreen(onBack = { navController.popBackStack() })
            }
            composable("settings") {
                SettingsScreen(
                    onBack = { navController.popBackStack() },
                    showBack = false,
                    onOpenLibraryRoots = { navController.navigate("library_roots") },
                    onScanNow = { lifecycleScope.launch { scanScheduler.scheduleBackgroundScans() } }
                )
            }
            composable("saved") {
                SavedScreen(
                    onBack = { navController.popBackStack() },
                    onOpenPlayer = { editionId, startMs ->
                        navController.navigate("player/$editionId?startMs=$startMs")
                    }
                )
            }
        }
    }
}

/** مسارات تُظهر شريط التنقل السفلي؛ "home" عبر اللوجو، وبقية التبويبات حوله. */


private enum class TopLevelDestination(val route: String, val labelRes: Int, val icon: ImageVector) {
    LIBRARY("library", R.string.nav_library, Icons.Outlined.MenuBook),
    STATISTICS("statistics", R.string.nav_statistics, Icons.Outlined.Leaderboard),
    SAVED("saved", R.string.nav_saved, Icons.Outlined.Bookmarks),
    SETTINGS("settings", R.string.nav_settings, Icons.Outlined.Settings)
}

private fun routeBase(route: String?): String? = route?.substringBefore('?')

/**
 * شريط تنقل زجاجي: اللوجو الدائري في الوسط (نقرة = الرئيسية)،
 * والمكتبة والإحصائيات على يمينه، والإعدادات على يساره — بتوزيع متساوٍ.
 */
@Composable
private fun AppBottomBar(
    mode: AppThemeMode,
    currentRoute: String?,
    onNavigate: (String) -> Unit,
    onHome: () -> Unit,
    haze: HazeState,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.height(72.dp).padding(horizontal = AppSpacing.xs),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavBarTab(
                destination = TopLevelDestination.LIBRARY,
                selected = routeBase(currentRoute) == TopLevelDestination.LIBRARY.route,
                onClick = { onNavigate(TopLevelDestination.LIBRARY.route) },
                modifier = Modifier.weight(1f)
            )
            NavBarTab(
                destination = TopLevelDestination.STATISTICS,
                selected = routeBase(currentRoute) == TopLevelDestination.STATISTICS.route,
                onClick = { onNavigate(TopLevelDestination.STATISTICS.route) },
                modifier = Modifier.weight(1f)
            )
        }

        NavHomeLogo(
            selected = routeBase(currentRoute) == "home",
            onClick = onHome,
            haze = haze
        )

        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.xxs),
            verticalAlignment = Alignment.CenterVertically
        ) {
            NavBarTab(
                destination = TopLevelDestination.SAVED,
                selected = routeBase(currentRoute) == TopLevelDestination.SAVED.route,
                onClick = { onNavigate(TopLevelDestination.SAVED.route) },
                modifier = Modifier.weight(1f)
            )
            NavBarTab(
                destination = TopLevelDestination.SETTINGS,
                selected = routeBase(currentRoute) == TopLevelDestination.SETTINGS.route,
                onClick = { onNavigate(TopLevelDestination.SETTINGS.route) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun NavBarTab(destination: TopLevelDestination, selected: Boolean, onClick: () -> Unit, modifier: Modifier = Modifier) {
    val colorScheme = MaterialTheme.colorScheme
    val appAccent = LocalAppAccent.current
    val shape = RoundedCornerShape(20.dp)
    val contentColor = if (selected) appAccent.accent else colorScheme.onSurfaceVariant
    Column(
        modifier = modifier
            .clip(shape)
            .background(if (selected) appAccent.accent.copy(alpha = 0.16f) else Color.Transparent)
            .minTouchTarget()
            .clickable(onClick = onClick)
            .padding(horizontal = AppSpacing.xxs, vertical = AppSpacing.xxs),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(destination.icon, contentDescription = stringResource(destination.labelRes), tint = contentColor, modifier = Modifier.size(20.dp))
        Spacer(Modifier.height(2.dp))
        Text(
            text = stringResource(destination.labelRes),
            style = MaterialTheme.typography.labelSmall,
            color = contentColor,
            maxLines = 1,
            modifier = Modifier.fillMaxWidth(),
            textAlign = TextAlign.Center
        )
    }
}

/** اللوجو الدائري الزجاجي: يستبدل تبويب "الرئيسية" — نقرة عليه تفتح الصفحة الرئيسية. */
@Composable
private fun NavHomeLogo(selected: Boolean, onClick: () -> Unit, haze: HazeState) {
    val appAccent = LocalAppAccent.current
    Box(
        modifier = Modifier
            .size(48.dp)
            .drawBehind {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            appAccent.accent.copy(alpha = 0.38f),
                            appAccent.accent.copy(alpha = 0.14f),
                            Color.Transparent
                        ),
                        center = Offset(size.width / 2f, size.height / 2f),
                        radius = size.width * 1.4f
                    )
                )
            }
            .clip(CircleShape)
            .hazeChild(haze, navLogoGlassStyle())
            .background(appAccent.accent.copy(alpha = if (selected) 0.92f else 0.62f))
            .border(
                width = if (selected) 2.dp else 1.dp,
                color = if (selected) appAccent.accent else appAccent.accent.copy(alpha = 0.55f),
                shape = CircleShape
            )
            .minTouchTarget()
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Image(
            painter = painterResource(id = R.drawable.app_logo),
            contentDescription = "أثير",
            modifier = Modifier.size(32.dp)
        )
    }
}

private fun navigateToTab(navController: NavHostController, route: String) {
    navController.navigate(route) {
        popUpTo(navController.graph.findStartDestination().id) { inclusive = false }
        launchSingleTop = true
    }
}

/** فتح الـPlayer الكامل من المشغّل المصغّر عند نفس موضع التشغيل الحالي. */
private fun navigateToPlayer(navController: NavHostController, editionId: java.util.UUID) {
    navController.navigate("player/$editionId") { launchSingleTop = true }
}