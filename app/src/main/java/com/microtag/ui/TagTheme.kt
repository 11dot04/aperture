package com.microtag.ui

import android.view.HapticFeedbackConstants
import android.view.View
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.CornerSize
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
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.rememberTextMeasurer
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
import androidx.compose.ui.unit.isSpecified
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import kotlin.math.roundToInt
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInRoot
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.delay
import kotlin.math.hypot
import androidx.compose.ui.geometry.isSpecified
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

/**
 * Single-purpose text that never wraps mid-word. It keeps every line whole and
 * shrinks the type until the widest line fits the space it is given.
 * Measured up front, so there is no visible resize flicker.
 */
@Composable
fun FitText(
    text: AnnotatedString,
    style: TextStyle,
    modifier: Modifier = Modifier,
    minSize: TextUnit = 9.sp,
    contentAlignment: Alignment = Alignment.TopStart
) {
    val measurer = rememberTextMeasurer()
    BoxWithConstraints(modifier, contentAlignment = contentAlignment) {
        val maxW = if (constraints.hasBoundedWidth) constraints.maxWidth else Int.MAX_VALUE
        val fitted = remember(text, style, maxW) {
            val ratio = if (style.lineHeight.isSpecified && style.fontSize.isSpecified)
                style.lineHeight.value / style.fontSize.value else 0f
            fun at(size: Float) = style.copy(
                fontSize = size.sp,
                lineHeight = if (ratio > 0f) (size * ratio).sp else style.lineHeight
            )
            var size = style.fontSize.value
            while (size > minSize.value) {
                val width = measurer.measure(text = text, style = at(size), softWrap = false).size.width
                if (width <= maxW) break
                size *= 0.96f
            }
            at(size.coerceAtLeast(minSize.value))
        }
        Text(text, style = fitted, softWrap = false, maxLines = text.text.count { it == '\n' } + 1)
    }
}

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
    FitText(
        text = buildAnnotatedString {
            withStyle(SpanStyle(fontWeight = FontWeight.Bold)) { append(bold) }
            if (light != null) {
                append(if (inline) " " else "\n")
                withStyle(SpanStyle(fontWeight = FontWeight.Light)) { append(light) }
            }
        },
        style = TextStyle(
            fontFamily = Montserrat,
            fontSize = size,
            lineHeight = size * 1.12f,
            letterSpacing = (-0.2).sp,
            color = color,
            textAlign = align
        ),
        modifier = modifier,
        contentAlignment = when (align) {
            TextAlign.End -> Alignment.CenterEnd
            TextAlign.Center -> Alignment.Center
            else -> Alignment.CenterStart
        }
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
    val Rect = RoundedCornerShape(22.dp)
    val Big = RoundedCornerShape(26.dp)
    val Pill = RoundedCornerShape(percent = 50)
    val Circle = CircleShape
    /** Left cap round, right edge square. Pairs with CapRight to interlock. */
    val CapLeft = RoundedCornerShape(topStartPercent = 50, bottomStartPercent = 50, topEndPercent = 0, bottomEndPercent = 0)
    val CapRight = RoundedCornerShape(topStartPercent = 0, bottomStartPercent = 0, topEndPercent = 50, bottomEndPercent = 50)
    /** Onboarding pills that nearly touch: round outside, tight inside. */
    val JoinLeft = RoundedCornerShape(topStart = CornerSize(50), bottomStart = CornerSize(50), topEnd = CornerSize(6.dp), bottomEnd = CornerSize(6.dp))
    val JoinRight = RoundedCornerShape(topStart = CornerSize(6.dp), bottomStart = CornerSize(6.dp), topEnd = CornerSize(50), bottomEnd = CornerSize(50))
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

/** The one gutter used everywhere, so tiles that bridge it (like the music pair) line up. */
val TagGap = 18.dp

data class CellData(
    val col: Float,
    val row: Float,
    val w: Float = 1f,
    val h: Float = 1f,
    val outset: Dp = 0.dp
) : ParentDataModifier {
    override fun Density.modifyParentData(parentData: Any?): Any = this@CellData
}

/** Fractional cells are allowed so tiles can glide between grid positions. */
fun Modifier.cell(col: Float, row: Float, w: Float = 1f, h: Float = 1f, outset: Dp = 0.dp): Modifier =
    this.then(CellData(col, row, w, h, outset))

fun Modifier.cell(col: Int, row: Int, w: Int = 1, h: Int = 1, outset: Dp = 0.dp): Modifier =
    cell(col.toFloat(), row.toFloat(), w.toFloat(), h.toFloat(), outset)

@Composable
fun TagGrid(
    modifier: Modifier = Modifier,
    columns: Int = 4,
    gap: Dp = TagGap,
    content: @Composable () -> Unit
) {
    Layout(content = content, modifier = modifier) { measurables, constraints ->
        val g = gap.roundToPx().toFloat()
        val cell = (constraints.maxWidth - g * (columns - 1)) / columns
        var rows = 0f
        val placed = measurables.map { m ->
            val d = m.parentData as? CellData ?: CellData(0f, 0f)
            rows = max(rows, d.row + d.h)
            val o = d.outset.roundToPx()
            val w = (d.w * cell + (d.w - 1f) * g).roundToInt() + 2 * o
            val h = (d.h * cell + (d.h - 1f) * g).roundToInt() + 2 * o
            Triple(m.measure(Constraints.fixed(w.coerceAtLeast(0), h.coerceAtLeast(0))), d, o)
        }
        val height = if (rows <= 0f) 0 else (rows * cell + (rows - 1f) * g).roundToInt()
        layout(constraints.maxWidth, height) {
            placed.forEach { (p, d, o) ->
                p.place((d.col * (cell + g)).roundToInt() - o, (d.row * (cell + g)).roundToInt() - o)
            }
        }
    }
}

/**
 * A border that wraps a block of tiles and sits in the gutter, so the tiles stay on the grid.
 * The line is drawn behind its children so the label's black plate cuts the line cleanly.
 */
@Composable
fun TagFrame(label: String, color: Color, modifier: Modifier = Modifier, labelBias: Float = 0f) {
    Box(
        modifier.drawBehind {
            val w = 2.dp.toPx()
            drawRoundRect(
                color = color,
                topLeft = Offset(w / 2, w / 2),
                size = Size(size.width - w, size.height - w),
                cornerRadius = CornerRadius(28.dp.toPx()),  // tile radius 22 + 6 outset = concentric
                style = Stroke(w)
            )
        }
    ) {
        Text(
            text = label,
            modifier = Modifier
                .align(BiasAlignment(labelBias, -1f))
                .offset(y = (-9).dp)
                .background(TagColors.Black)
                .padding(horizontal = 10.dp),
            style = TextStyle(
                fontFamily = Montserrat,
                fontWeight = FontWeight.Medium,
                fontSize = 13.sp,
                lineHeight = 16.sp,
                color = color
            )
        )
    }
}

// ==========================================================
// TILE
// ==========================================================

enum class TileState { Off, On, Static }

private fun fillOf(state: TileState): Color = when (state) {
    TileState.On -> TagColors.Lime
    TileState.Off -> TagColors.Black
    TileState.Static -> TagColors.White
}

/**
 * Three bits of motion live here:
 *  - a springy squish while pressed
 *  - the new state's colour floods out from the exact point you touched
 *  - tiles spring in on first show, cascading down and across the screen
 */
@Composable
fun TagTile(
    modifier: Modifier = Modifier,
    shape: Shape = TagShapes.Rect,
    state: TileState,
    onClick: (() -> Unit)? = null,
    content: @Composable BoxScope.(fg: Color) -> Unit
) {
    val view = LocalView.current
    val density = LocalDensity.current
    val source = remember { MutableInteractionSource() }
    val pressed by source.collectIsPressedAsState()
    val press by animateFloatAsState(
        targetValue = if (pressed) 0.94f else 1f,
        animationSpec = spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "press"
    )
    val fg by animateColorAsState(
        if (state == TileState.Off) TagColors.White else TagColors.Black,
        tween(160), label = "fg"
    )
    val edge by animateColorAsState(
        if (state == TileState.Off) TagColors.Outline else fillOf(state),
        tween(160), label = "edge"
    )

    // Colour flood: `base` is what's painted underneath, `target` floods over it from `origin`.
    var base by remember { mutableStateOf(state) }
    var target by remember { mutableStateOf(state) }
    val reveal = remember { Animatable(1f) }
    var origin by remember { mutableStateOf(Offset.Unspecified) }
    LaunchedEffect(state) {
        if (state != target) {
            base = target
            target = state
            reveal.snapTo(0f)
            reveal.animateTo(1f, spring(dampingRatio = 0.8f, stiffness = 220f))
            base = state
        }
    }

    // Entrance: delay grows with distance from the top-left, capped so nothing feels slow.
    val enter = remember { Animatable(0f) }
    var enterDelay by remember { mutableLongStateOf(-1L) }
    LaunchedEffect(enterDelay) {
        if (enterDelay >= 0) {
            delay(enterDelay)
            enter.animateTo(1f, spring(dampingRatio = 0.68f, stiffness = Spring.StiffnessLow))
        }
    }

    Box(
        modifier = modifier
            .onGloballyPositioned { c ->
                if (enterDelay < 0) {
                    val pos = c.positionInRoot()
                    enterDelay = ((pos.y + pos.x * 0.35f) / density.density * 0.3f).toLong().coerceIn(0L, 420L)
                }
            }
            .graphicsLayer {
                val e = enter.value
                val s = press * (0.88f + 0.12f * e)
                scaleX = s
                scaleY = s
                alpha = e.coerceIn(0f, 1f)
                translationY = (1f - e) * 36f
            }
            .clip(shape)
            .drawBehind {
                val o = if (origin.isSpecified) origin else center
                drawRect(fillOf(base))
                if (reveal.value < 1f) {
                    val far = hypot(maxOf(o.x, size.width - o.x), maxOf(o.y, size.height - o.y))
                    drawCircle(fillOf(target), radius = far * reveal.value.coerceAtLeast(0f), center = o)
                }
            }
            .border(2.dp, edge, shape)
            .pointerInput(Unit) {
                // Watch only: remember where the finger landed, never consume.
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    origin = down.position
                }
            }
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
            .size(36.dp)
            .clip(CircleShape)
            .clickable { view.tick(Tick.Press); onClick() },
        contentAlignment = Alignment.Center
    ) {
        TagIcon(R.drawable.ic_chevron_right, tint, Modifier.rotate(rot), size = 20.dp)
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
