package com.automatelinux.myEmail.ui

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarData
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.automatelinux.myEmail.ui.theme.Ink

/** The amber seal — the app's one mark for "a person is waiting on you". */
@Composable
fun SealDot(size: Dp = 9.dp, modifier: Modifier = Modifier) {
    Box(modifier.size(size).background(Ink.SealBrush, CircleShape))
}

@Composable
fun Avatar(name: String, key: String, size: Dp = 42.dp) {
    val c = avatarColor(key)
    Box(
        Modifier.size(size).background(Brush.linearGradient(listOf(c.copy(alpha = 0.78f), c)), CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        Text(initial(name), color = Color.White, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
    }
}

/** Snackbar in the header's ink, with the action in amber. */
@Composable
fun InkSnackbar(data: SnackbarData) {
    Row(
        Modifier.padding(horizontal = 16.dp, vertical = 10.dp).fillMaxWidth()
            .shadow(12.dp, RoundedCornerShape(16.dp)).background(Ink.Night, RoundedCornerShape(16.dp))
            .padding(start = 18.dp, end = 6.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(data.visuals.message, color = Color.White, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f).padding(vertical = 8.dp))
        data.visuals.actionLabel?.let { label ->
            Text(
                label, color = Ink.SealGlow, style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(10.dp)).clickable { data.performAction() }.testTag("snackbar-action")
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }
    }
}

/** The empty inbox: the icon's envelope, drawn in line, sealed. */
@Composable
fun EmptyEnvelope(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 64.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(Modifier.size(width = 132.dp, height = 96.dp)) {
            val w = size.width; val h = size.height
            val line = Stroke(width = 2.2.dp.toPx(), cap = StrokeCap.Round)
            drawRoundRect(Ink.Card, Offset(0f, 10f), Size(w, h - 14f), CornerRadius(14f))
            drawRoundRect(Ink.Faint.copy(alpha = 0.55f), Offset(0f, 10f), Size(w, h - 14f), CornerRadius(14f), style = line)
            val flap = Path().apply { moveTo(6f, 16f); lineTo(w / 2, h * 0.62f); lineTo(w - 6f, 16f) }
            drawPath(flap, Ink.Faint.copy(alpha = 0.55f), style = line)
            drawCircle(Ink.SealBrush, radius = 13.dp.toPx(), center = Offset(w / 2, h * 0.62f))
            drawCircle(Color.White.copy(alpha = 0.55f), radius = 8.5.dp.toPx(), center = Offset(w / 2, h * 0.62f), style = Stroke(1.2.dp.toPx()))
        }
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.titleLarge, color = Ink.Text, textAlign = TextAlign.Center)
        Spacer(Modifier.height(6.dp))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = Ink.Soft, textAlign = TextAlign.Center)
    }
}

/** Loading rows that breathe, in the shape of the rows that are coming. */
@Composable
fun SkeletonRows(count: Int = 7) {
    val t = rememberInfiniteTransition(label = "skeleton")
    val a by t.animateFloat(0.45f, 0.9f, infiniteRepeatable(tween(900, easing = LinearEasing), RepeatMode.Reverse), label = "a")
    val block = Ink.Hairline.copy(alpha = a)
    Column {
        repeat(count) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 16.dp)) {
                Box(Modifier.size(42.dp).background(block, CircleShape))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Box(Modifier.fillMaxWidth(0.42f).height(12.dp).background(block, RoundedCornerShape(6.dp)))
                    Spacer(Modifier.height(9.dp))
                    Box(Modifier.fillMaxWidth(0.8f).height(11.dp).background(block, RoundedCornerShape(6.dp)))
                    Spacer(Modifier.height(7.dp))
                    Box(Modifier.fillMaxWidth(0.62f).height(11.dp).background(block, RoundedCornerShape(6.dp)))
                }
            }
        }
    }
}
