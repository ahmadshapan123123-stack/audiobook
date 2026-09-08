package com.example.audiobook

import android.os.Bundle
import android.os.Build
import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import dagger.hilt.android.AndroidEntryPoint
import androidx.lifecycle.lifecycleScope
import com.example.audiobook.domain.usecases.RecoverInterruptedSession
import javax.inject.Inject
import kotlinx.coroutines.launch
import androidx.activity.viewModels
import androidx.activity.result.contract.ActivityResultContracts
import com.example.audiobook.background.scanworker.ScanScheduler
import com.example.audiobook.presentation.theme.AudiobookTheme
import com.example.audiobook.presentation.theme.DesignSystemShowcase
import com.example.audiobook.presentation.theme.ThemePreference
import com.example.audiobook.presentation.library.LibraryScreen
import com.example.audiobook.presentation.bookdetails.BookDetailsScreen
import com.example.audiobook.presentation.player.PlayerScreen
import com.example.audiobook.presentation.bookmarks.BookmarksScreen
import com.example.audiobook.playback.PlaybackController
import com.example.audiobook.domain.usecases.EnsureDemoEdition
import com.example.audiobook.domain.usecases.MarksCoordinator
import java.util.UUID
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var recoverInterruptedSession: RecoverInterruptedSession
    @Inject lateinit var scanScheduler: ScanScheduler
    @Inject lateinit var playbackController: PlaybackController
    @Inject lateinit var marksCoordinator: MarksCoordinator
    @Inject lateinit var ensureDemoEdition: EnsureDemoEdition
    private val libraryRootsViewModel: com.example.audiobook.presentation.libraryroots.LibraryRootsViewModel by viewModels()
    private lateinit var themePreference: ThemePreference
    private val notificationPermission = registerForActivityResult(ActivityResultContracts.RequestPermission()) {}
    private val demoEditionId = EnsureDemoEdition.stable("demo-edition")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        lifecycleScope.launch { recoverInterruptedSession() }
        lifecycleScope.launch { scanScheduler.scheduleStartupScans() }
        lifecycleScope.launch { ensureDemoEdition() }
        themePreference = ThemePreference(this)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        setContent {
            var showBookDetails by remember { mutableStateOf(false) }
            var showPlayer by remember { mutableStateOf(false) }
            var showBookmarks by remember { mutableStateOf(false) }
            AudiobookTheme(themePreference.mode) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    if (showBookmarks) BookmarksScreen(demoEditionId, marksCoordinator, playbackController) { showBookmarks = false }
                    else if (showPlayer) PlayerScreen(playbackController, demoEditionId, marksCoordinator, themePreference.mode) { showPlayer = false }
                    else if (showBookDetails) BookDetailsScreen(demoEditionId, marksCoordinator, onBack = { showBookDetails = false }, onPlay = { showPlayer = true }, onBookmarks = { showBookmarks = true })
                    else LibraryScreen { showBookDetails = true }
                }
            }
        }
    }
}