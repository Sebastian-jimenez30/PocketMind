package com.pocketmind

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import com.pocketmind.presentation.navigation.PocketMindApp
import com.pocketmind.ui.theme.PocketMindTheme
import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.auth.handleDeeplinks
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    @Inject lateinit var supabase: SupabaseClient

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        supabase.handleDeeplinks(intent)
        enableEdgeToEdge()
        setContent {
            PocketMindTheme {
                PocketMindApp()
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        supabase.handleDeeplinks(intent)
    }
}
