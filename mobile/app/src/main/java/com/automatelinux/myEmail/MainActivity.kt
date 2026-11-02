package com.automatelinux.myEmail

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.automatelinux.myEmail.ui.feedback.FeedbackHost
import dagger.hilt.android.AndroidEntryPoint

// Thin Android launcher — all UI lives in the shared commonMain App() composable.
// @AndroidEntryPoint: feedback-lib's overlay (dev flavor) resolves Hilt view models here.
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            FeedbackHost {
                App(
                    baseUrl = BuildConfig.API_BASE_URL,
                    token = BuildConfig.API_TOKEN,
                    fonts = Heebo,
                    onScreen = { ScreenTracker.currentScreen = it },
                )
            }
        }
    }
}
