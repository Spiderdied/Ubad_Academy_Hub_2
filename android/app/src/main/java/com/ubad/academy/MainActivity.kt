package com.ubad.academy

import android.content.Intent
import android.os.Bundle
import androidx.activity.SystemBarStyle
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.getValue
import androidx.compose.runtime.LaunchedEffect
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.activity.compose.setContent
import com.ubad.academy.ui.UbadApp
import com.ubad.academy.ui.theme.UbadTheme
import com.ubad.academy.focus.FocusTimer
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private val viewModel: MainViewModel by viewModels()
    @Inject lateinit var focusTimer: FocusTimer

    override fun onStart() {
        super.onStart()
        // Finish a focus phase that ended while the app was closed / re-arm after a reboot.
        focusTimer.reconcile()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Keep the native splash until persisted theme/language are known → no theme flash
        // (the web app did the same with an inline <script> before first paint).
        splash.setKeepOnScreenCondition { viewModel.uiState.value == null }

        setContent {
            val state by viewModel.uiState.collectAsStateWithLifecycle()
            val s = state ?: return@setContent
            LaunchedEffect(s.settings.theme) {
                val dark = s.settings.theme.isDark
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    else SystemBarStyle.light(android.graphics.Color.TRANSPARENT, android.graphics.Color.TRANSPARENT),
                )
            }
            UbadTheme(theme = s.settings.theme) {
                UbadApp(settings = s.settings)
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // singleTask: forward deep links to the NavController (handled in UbadApp).
        setIntent(intent)
    }
}
