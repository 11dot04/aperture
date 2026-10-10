package com.microtag.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.sin

/*
 * Kept only so other screens (e.g. MicrotagLogActivity) that used the old Material
 * Expressive helpers still compile. Delete anything here once nothing references it.
 */

/** Old theme name; now just the TAG theme. */
@Composable
fun MicrotagTheme(content: @Composable () -> Unit) = TagTheme(content)

class ScallopShape(
    private val lobes: Int = 12,
    private val amplitude: Float = 0.07f
) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val path = Path()
        val cx = size.width / 2f
        val cy = size.height / 2f
        val base = min(size.width, size.height) / 2f * (1f - amplitude)
        val steps = 240
        for (i in 0..steps) {
            val theta = (2.0 * PI * i / steps).toFloat()
            val r = base * (1f + amplitude * cos(lobes * theta))
            val x = cx + r * cos(theta)
            val y = cy + r * sin(theta)
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        path.close()
        return Outline.Generic(path)
    }
}

@Composable
fun SpringButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tonal: Boolean = false,
    shape: Shape = RoundedCornerShape(28.dp),
    content: @Composable () -> Unit
) {
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.93f else 1f,
        animationSpec = spring(dampingRatio = 0.4f, stiffness = Spring.StiffnessMedium),
        label = "PressScale"
    )
    val padding = PaddingValues(horizontal = 22.dp, vertical = 18.dp)
    val animated = modifier.graphicsLayer { scaleX = scale; scaleY = scale }
    if (tonal) {
        FilledTonalButton(onClick = onClick, modifier = animated, shape = shape, interactionSource = source, contentPadding = padding) { content() }
    } else {
        Button(onClick = onClick, modifier = animated, shape = shape, interactionSource = source, contentPadding = padding, colors = ButtonDefaults.buttonColors()) { content() }
    }
}
