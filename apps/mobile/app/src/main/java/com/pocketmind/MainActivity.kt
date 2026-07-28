package com.pocketmind

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import com.pocketmind.data.sync.SyncCoordinator
import dagger.hilt.android.AndroidEntryPoint
import com.pocketmind.presentation.navigation.PocketMindApp
import com.pocketmind.ui.theme.PocketMindTheme
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.handleDeeplinks
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var supabase: SupabaseClient
    @Inject lateinit var syncCoordinator: SyncCoordinator

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supabase.handleDeeplinks(intent)
        enableEdgeToEdge()
        setContent {
            PocketMindTheme {
                val view = LocalView.current
                val darkTheme = PocketMindTheme.isDarkTheme
                SideEffect {
                    WindowCompat.getInsetsController(window, view)
                        .isAppearanceLightStatusBars = darkTheme
                }
                PocketMindApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        supabase.handleDeeplinks(intent)
    }

    override fun onStart() {
        super.onStart()
        syncCoordinator.requestForegroundSync()
    }
}
