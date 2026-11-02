package com.automatelinux.myEmail.ui

import androidx.compose.ui.graphics.Color
import kotlinx.datetime.Clock
import kotlinx.datetime.Instant
import kotlinx.datetime.TimeZone
import kotlinx.datetime.toLocalDateTime

private fun two(n: Int) = n.toString().padStart(2, '0')

/** Today → 14:05 · this year → 3/11 · older → 3/11/25 (day first, as at home). */
fun shortTime(ms: Long): String {
    val tz = TimeZone.currentSystemDefault()
    val t = Instant.fromEpochMilliseconds(ms).toLocalDateTime(tz)
    val now = Clock.System.now().toLocalDateTime(tz)
    return when {
        t.date == now.date -> "${two(t.hour)}:${two(t.minute)}"
        t.year == now.year -> "${t.dayOfMonth}/${t.monthNumber}"
        else -> "${t.dayOfMonth}/${t.monthNumber}/${two(t.year % 100)}"
    }
}

/** Full time for the thread view: 3/11/2026 14:05 */
fun longTime(ms: Long): String {
    val t = Instant.fromEpochMilliseconds(ms).toLocalDateTime(TimeZone.currentSystemDefault())
    return "${t.dayOfMonth}/${t.monthNumber}/${t.year} ${two(t.hour)}:${two(t.minute)}"
}

private val avatarColors = listOf(
    Color(0xFF3B6EA8), Color(0xFF2E7D6B), Color(0xFF8A5A2B), Color(0xFF7B4B94),
    Color(0xFFA0453F), Color(0xFF4F6B2E), Color(0xFF365F7A), Color(0xFF8C4A6B),
)

/** Same sender, same colour, every time — you learn to recognise people by it. */
fun avatarColor(key: String): Color = avatarColors[(key.lowercase().hashCode() and 0x7fffffff) % avatarColors.size]

fun initial(name: String): String =
    name.trim().trimStart('"', '\'').firstOrNull { it.isLetterOrDigit() }?.uppercase() ?: "?"
