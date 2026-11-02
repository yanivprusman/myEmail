package com.automatelinux.myEmail

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/** Heebo — one face for Hebrew and Latin, so a mixed inbox reads as one voice. */
private val Heebo = FontFamily(
    Font(R.font.heebo_regular, FontWeight.Normal),
    Font(R.font.heebo_medium, FontWeight.Medium),
    Font(R.font.heebo_bold, FontWeight.SemiBold),
    Font(R.font.heebo_bold, FontWeight.Bold),
)

// Thin Android launcher — all UI lives in the shared commonMain App() composable.
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            App(baseUrl = BuildConfig.API_BASE_URL, token = BuildConfig.API_TOKEN, fonts = Heebo)
        }
    }
}
