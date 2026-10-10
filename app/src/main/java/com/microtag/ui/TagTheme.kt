package com.microtag.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.BiasAlignment
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.Layout
import androidx.compose.ui.layout.ParentDataModifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Constraints
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.microtag.R
import kotlin.math.max

// ==========================================================
// PALETTE
// One accent, pure black, one cool grey for structure.
// Lime fill = on. Outline only = off. White fill = always on, not switchable.
// ==========================================================

object TagColors {
    val Lime = Color(0xFFCCFF00)
    val Black = Color(0xFF000000)
    val White = Color(0xFFF6F6F6)
    val Outline = Color(0xFFC3C9D4)
    val Steel = Color(0xFFC5CCD8)
    val Dim = Color(0xFF6E737C)
}

// ==========================================================
// TYPE: Montserrat. Weight does the talking: the first word is Bold,
// the rest of the label drops to Light. Drop the TTFs in res/font.
// ==========================================================

val Montserrat = FontFamily(
    Font(R.font.montserrat_light, FontWeight.Light),
    Font(R.font.montserrat_medium, FontWeight.Medium),
    Font(R.font.montserrat_bold, FontWeight.Bold),
    Font(R.font.montserrat_black, FontWeight.Black)
)

@Composable
fun TagLabel(
    bold: String,
    light: String? = null,
    color: Color,
    modifier: Modifier = Modifier,
    size: TextUnit = 17.sp,
    align: TextAlign = TextAlign.Start,
    inline: Boolean = false
) {
    Text(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
            if (light != null) {
                append(if (inline) " " else "\n")
                withStyle(SpanStyle(fontWeight = FontWeight.Light)) { append(light) }
            }
        },
        modifier = modifier,
        style = TextStyle(
            fontFamily = Montserrat,
            fontSize = size,
            lineHeight = size * 1.12f,
            letterSpacing = (-0.2).sp,
            color = color,
            textAlign = align
        )
    )
}

@Composable
fun TagTheme(content: @Composable () -> Unit) {
    // Dark only, no dynamic colour: the lime is the identity.
    val scheme = darkColorScheme(
        primary = TagColors.Lime,
        onPrimary = TagColors.Black,
        background = TagColors.Black,
        onBackground = TagColors.White,
        surface = TagColors.Black,
        onSurface = TagColors.White,
        surfaceContainerHigh = TagColors.Black,
        outline = TagColors.Outline
    )
    MaterialTheme(colorScheme = scheme, content = content)
}

// ==========================================================
// SHAPES
// ==========================================================

object TagShapes {
    val Rect = RoundedCornerShape(28.dp)
    val Big = RoundedCornerShape(36.dp)
    val Pill = RoundedCornerShape(percent = 50)
    val Circle = CircleShape
    /** Left cap round, right edge square. Pairs with CapRight to interlock. */
    val CapLeft = RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50, topEndPercent = 0, bottomEndPercent = 0)
    val CapRight = RoundedCornerShape(topStartPercent = 0, bottomStartPercent = 0, topEndPercent = 50, bottomEndPercent = 50)
    /** Onboarding pills that nearly touch: round outside, tight inside. */
    val JoinLeft = RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50, topEnd = 6.dp, bottomEnd = 6.dp)
    val JoinRight = RoundedCornerShape(topStart = 6.dp, bottomStart = 6.dp, topEndPercent = 50, bottomEndPercent = 50)
}

// ==========================================================
// HAPTICS
// ==========================================================

enum class Tick { Press, On, Off, Detent, Confirm }

fun View.tick(kind: Tick) {
    performHapticFeedback(
        when (kind) {
            Tick.Press -> HapticFeedbackConstants.CONTEXT_CLICK
            Tick.On -> HapticFeedbackConstants.TOGGLE_ON
            Tick.Off -> HapticFeedbackConstants.TOGGLE_OFF
            Tick.Detent -> HapticFeedbackConstants.SEGMENT_TICK
            Tick.Confirm -> HapticFeedbackConstants.CONFIRM
        }
    )
}

// ==========================================================
// THE INVISIBLE GRID
// A square cell, a fixed gutter, and tiles that span whole cells.
// 1x1 and 2x2 are exact squares, 2x1 is exactly two cells wide.
// A tile's position is (col, row); it never floats.
// ==========================================================

data class CellData(
    val col: Int,
    val row: Int,
    val w: Int = 1,
    val h: Int = 1,
    val outset: Dp = 0.dp
) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = this@CellData
}

fun Modifier.cell(col: Int, row: Int, w: Int = 1, h: Int = 1, outset: Dp = 0.dp): Modifier =
    this.then(CellData(col, row, w, h, outset))

@Composable
fun TagGrid(
    modifier: Modifier = Modifier,
    columns: Int = 4,
    gap: Dp = 18.dp,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val g = gap.roundToPx()
        val cell = (constraints.maxWidth - g * (columns - 1)) / columns
        var rows = 0
        val placed = measurables.map { m ->
            val d = m.parentData as? CellData ?: CellData(0, 0)
            rows = max(rows, d.row + d.h)
            val o = d.outset.roundToPx()
            val w = d.w * cell + (d.w - 1) * g + 2 * o
            val h = d.h * cell + (d.h - 1) * g + 2 * o
            Triple(m.measure(Constraints.fixed(w, h)), d, o)
        }
        val height = if (rows == 0) 0 else rows * cell + (rows - 1) * g
        layout(constraints.maxWidth, height) {
            placed.forEach { (p, d, o) ->
                p.place(d.col * (cell + g) - o, d.row * (cell + g) - o)
            }
        }
    }
}

/** A border that wraps a block of tiles and sits in the gutter, so the tiles stay on the grid. */
@Composable
fun TagFrame(label: String, color: Color, modifier: Modifier = Modifier, labelBias: Float = 0f) {
    Box(modifier.border(2.dp, color, RoundedCornerShape(34.dp))) {
        Text(
            text = label,
            modifier = Modifier
                .align(BiasAlignment(labelBias, -1f))
                .offset(y = (-8).dp)
                .background(TagColors.Black)
                .padding(horizontal = 8.dp),
            style = TextStyle(
                fontFamily = Montserrat,
                fontWeight = FontWeight.Light,
                fontSize = 12.sp,
                lineHeight = 14.sp,
                color = color
            )
        )
    }
}

// ==========================================================
// TILE
// ==========================================================

enum class TileState { Off, On, Static }

@Composable
fun TagTile(
    modifier: Modifier = Modifier,
    shape: Shape = TagShapes.Rect,
    state: TileState,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.(fg: Color) -> Unit
) {
    val view = LocalView.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "press"
    )
    val bg by animateColorAsState(
        when (state) {
            TileState.On -> TagColors.Lime
            TileState.Off -> TagColors.Black
            TileState.Static -> TagColors.White
        }, label = "bg"
    )
    val fg by animateColorAsState(
        when (state) {
            TileState.Off -> TagColors.White
            else -> TagColors.Black
        }, label = "fg"
    )
    val edge by animateColorAsState(if (state == TileState.Off) TagColors.Outline else bg, label = "edge")

    Box(
        modifier = modifier
            .graphicsLayer { scaleX = scale; scaleY = scale }
            .clip(shape)
            .background(bg)
            .border(2.dp, edge, shape)
            .then(
                if (onClick != null) Modifier.clickable(interactionSource = source, indication = null) {
                    view.tick(if (state == TileState.Off) Tick.On else Tick.Off)
                    onClick()
                } else Modifier
            )
    ) { content(fg) }
}

@Composable
fun TagIcon(res: Int, tint: Color, modifier: Modifier = Modifier, size: Dp = 26.dp) {
    Icon(painterResource(res), contentDescription = null, tint = tint, modifier = modifier.size(size))
}

/** Marks a tile as "has options". Turns to point down when open. */
@Composable
fun ChevronButton(open: Boolean, tint: Color, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val view = LocalView.current
    val rot by animateFloatAsState(
        if (open) 90f else 0f,
        spring(dampingRatio = 0.6f, stiffness = Spring.StiffnessMediumLow),
        label = "chevron"
    )
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .clickable { view.tick(Tick.Press); onClick() },
        contentAlignment = Alignment.Center
    ) {
        TagIcon(R.drawable.ic_chevron_right, tint, Modifier.rotate(rot), size = 22.dp)
    }
}

@Composable
fun TagSwitch(
    checked: Boolean,
    onChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    trackOn: Color = TagColors.Lime,
    thumbOn: Color = TagColors.Black,
    trackOff: Color = TagColors.White,
    thumbOff: Color = TagColors.Black
) {
    val view = LocalView.current
    val x by animateDpAsState(
        if (checked) 22.dp else 0.dp,
        spring(dampingRatio = 0.55f, stiffness = Spring.StiffnessMedium),
        label = "thumb"
    )
    val track by animateColorAsState(if (checked) trackOn else trackOff, label = "track")
    val thumb by animateColorAsState(if (checked) thumbOn else thumbOff, label = "thumbColor")
    Box(
        modifier = modifier
            .size(width = 52.dp, height = 30.dp)
            .clip(CircleShape)
            .background(track)
            .clickable {
                view.tick(if (checked) Tick.Off else Tick.On)
                onChange(!checked)
            }
            .padding(4.dp)
    ) {
        Box(
            Modifier
                .offset(x = x)
                .size(22.dp)
                .clip(CircleShape)
                .background(thumb)
        )
    }
}
