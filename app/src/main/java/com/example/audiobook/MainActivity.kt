package com.example.audiobook

import android.os.Bundle
import android.os.Build
import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.example.audiobook.background.scanworker.ScanScheduler
import com.example.audiobook.domain.usecases.RecoverInterruptedSession
import com.example.audiobook.presentation.library.LibraryScreen
import com.example.audiobook.presentation.bookdetails.BookDetailsScreen
import com.example.audiobook.presentation.player.PlayerScreen
import com.example.audiobook.presentation.bookmarks.BookmarksScreen
import com.example.audiobook.presentation.theme.AudiobookTheme
import com.example.audiobook.presentation.theme.ThemePreference
import com.example.audiobook.presentation.libraryroots.LibraryRootsScreen
import com.example.audiobook.playback.PlaybackController
import com.example.audiobook.playback.SleepTimerController
import com.example.audiobook.domain.usecases.EnsureDemoEdition
import com.example.audiobook.domain.usecases.MarksCoordinator
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject
import kotlinx.coroutines.launch

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var recoverInterruptedSession: RecoverInterruptedSession
    @Inject lateinit var scanScheduler: ScanScheduler
    @Inject lateinit var playbackController: PlaybackController
    @Inject lateinit var sleepTimerController: SleepTimerController
    @Inject lateinit var marksCoordinator: MarksCoordinator
    @Inject lateinit var ensureDemoEdition: EnsureDemoEdition
    private val libraryRootsViewModel: com.example.audiobook.presentation.libraryroots.LibraryRootsViewModel by viewModels()
    private lateinit var themePreference: ThemePreference
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch { recoverInterruptedSession() }
        lifecycleScope.launch { scanScheduler.scheduleStartupScans() }
        if (BuildConfig.DEBUG) {
            lifecycleScope.launch { ensureDemoEdition() }
        }
        themePreference = ThemePreference(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            val navController = rememberNavController()
            AudiobookTheme(themePreference.mode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    NavHost(
                        navController = navController,
                        startDestination = "library"
                    ) {
                        composable("library") {
                            LibraryScreen(
                                onBookSelected = { bookId -> navController.navigate("book_details/$bookId") },
                                onManageRoots = { navController.navigate("library_roots") },
                                onStatistics = { navController.navigate("statistics") },
                                onHistory = { navController.navigate("history") }
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
                                onBookmarks = { editionId ->
                                    navController.navigate("bookmarks/$editionId")
                                }
                            )
                        }
                        composable(
                            route = "player/{editionId}",
                            arguments = listOf(navArgument("editionId") { type = NavType.StringType })
                        ) {
                            PlayerScreen(
                                controller = playbackController,
                                themeMode = themePreference.mode,
                                marks = marksCoordinator,
                                sleepTimer = sleepTimerController,
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
                            LibraryRootsScreen(libraryRootsViewModel)
                        }
                        composable("statistics") {
                            com.example.audiobook.presentation.statistics.StatisticsScreen(onBack = { navController.popBackStack() })
                        }
                        composable("history") {
                            com.example.audiobook.presentation.statistics.HistoryScreen(onBack = { navController.popBackStack() })
                        }
                    }
                }
            }
        }
    }
}
