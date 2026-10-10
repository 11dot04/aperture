package com.microtag.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.roundToInt
import kotlin.math.sin

/** Refresh intervals, in minutes. Order = clockwise around the dial. */
val WeatherSteps = listOf(30, 60, 120, 180, 360)

fun intervalLabel(min: Int): String = if (min < 60) "${min}m" else "${min / 60}h"

/**
 * A knob with detents. Drag or tap anywhere to turn; every detent crossed
 * fires a haptic tick, and lifting your finger fires a confirm.
 * Sweeps 270 degrees, with the dead zone at the bottom.
 */
@Composable
fun TagDial(
    steps: List<String>,
    index: Int,
    onIndex: (Int) -> Unit,
    body: Color,
    face: Color,
    modifier: Modifier = Modifier
) {
    val view = LocalView.current
    val n = steps.size
    val currentIndex by rememberUpdatedState(index)
    val callback by rememberUpdatedState(onIndex)

    fun angleOf(i: Int) = -135f + 270f * i / (n - 1)

    val sweep by animateFloatAsState(
        targetValue = angleOf(index),
        animationSpec = spring(dampingRatio = 0.5f, stiffness = 450f),
        label = "dialSweep"
    )

    fun pick(p: Offset, size: IntSize) {
        val dx = p.x - size.width / 2f
        val dy = size.height / 2f - p.y
        val deg = Math.toDegrees(atan2(dx.toDouble(), dy.toDouble())).toFloat()
        val i = ((deg.coerceIn(-135f, 135f) + 135f) / 270f * (n - 1)).roundToInt().coerceIn(0, n - 1)
        if (i != currentIndex) {
            view.tick(Tick.Detent)
            callback(i)
        }
    }

    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(n) { detectTapGestures { pick(it, size) } }
                .pointerInput(n) {
                    detectDragGestures(
                        onDragStart = { pick(it, size) },
                        onDragEnd = { view.tick(Tick.Confirm) },
                        onDrag = { change, _ -> change.consume(); pick(change.position, size) }
                    )
                }
        ) {
            val c = center
            val r = size.minDimension / 2f
            for (i in 0 until n) {
                val a = Math.toRadians(angleOf(i) - 90.0)
                val selected = i == currentIndex
                val r1 = r * 0.82f
                val r2 = r * if (selected) 1f else 0.93f
                drawLine(
                    color = body,
                    start = Offset(c.x + cos(a).toFloat() * r1, c.y + sin(a).toFloat() * r1),
                    end = Offset(c.x + cos(a).toFloat() * r2, c.y + sin(a).toFloat() * r2),
                    strokeWidth = (if (selected) 4.dp else 2.dp).toPx(),
                    cap = StrokeCap.Round
                )
            }
            drawCircle(body, radius = r * 0.70f, center = c)
            val a = Math.toRadians(sweep - 90.0)
            drawCircle(
                color = face,
                radius = r * 0.08f,
                center = Offset(c.x + cos(a).toFloat() * r * 0.57f, c.y + sin(a).toFloat() * r * 0.57f)
            )
        }
        // Sized from the dial itself, so it can never spill past the knob.
        FitText(
            text = AnnotatedString(steps[index]),
            style = TextStyle(fontFamily = Montserrat, fontWeight = FontWeight.Black, fontSize = 40.sp, color = face),
            modifier = Modifier.fillMaxSize(0.5f),
            contentAlignment = Alignment.Center
        )
    }
}
