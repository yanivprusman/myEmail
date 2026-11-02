package com.automatelinux.myEmail

import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight

/** Heebo — one face for Hebrew and Latin, so a mixed inbox reads as one voice. */
val Heebo = FontFamily(
    Font(R.font.heebo_regular, FontWeight.Normal),
    Font(R.font.heebo_medium, FontWeight.Medium),
    Font(R.font.heebo_bold, FontWeight.SemiBold),
    Font(R.font.heebo_bold, FontWeight.Bold),
)

/** Which view the user is on, for feedback-lib to tag a report with. */
object ScreenTracker {
    @Volatile var currentScreen: String = "Inbox"
}
