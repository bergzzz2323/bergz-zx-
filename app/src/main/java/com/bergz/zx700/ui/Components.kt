package com.bergz.zx700.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.TileMode
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

// ============================================================ materials

/** Black brushed steel: near-black base, fine horizontal grain, soft diagonal sheen. */
fun Modifier.brushedSteel(): Modifier = drawWithCache {
    val base = Brush.verticalGradient(listOf(Theme.SteelTop, Theme.SteelMid, Theme.SteelBottom))
    val sheen = Brush.linearGradient(
        0.22f to Color.Transparent, 0.38f to Color.White.copy(alpha = 0.055f), 0.52f to Color.Transparent,
        0.68f to Color.Transparent, 0.80f to Color.White.copy(alpha = 0.035f), 0.92f to Color.Transparent,
        start = Offset(0f, 0f), end = Offset(size.width, size.height * 1.6f),
    )
    val step = 3.dp.toPx() / 2f
    val light = Path()
    val dark = Path()
    var y = 0f
    var i = 0
    while (y < size.height) {
        val p = if (i % 2 == 0) light else dark
        p.moveTo(0f, y); p.lineTo(size.width, y)
        y += step; i++
    }
    onDrawBehind {
        drawRect(base)
        drawPath(light, Color.White.copy(alpha = 0.035f), style = Stroke(1f))
        drawPath(dark, Color.Black.copy(alpha = 0.22f), style = Stroke(1f))
        drawRect(sheen)
    }
}

/** Walnut end-cheek, like the side panels on vintage synths. */
@Composable
fun WalnutCheek(modifier: Modifier = Modifier) {
    Canvas(modifier.width(18.dp).fillMaxHeight()) {
        val grain = Brush.linearGradient(
            listOf(Color(0xFF5B3820), Color(0xFF6E4729), Color(0xFF4C2F1A), Color(0xFF714A2B), Color(0xFF5B3820)),
            start = Offset(0f, 0f), end = Offset(19.dp.toPx(), 2.5.dp.toPx()), tileMode = TileMode.Repeated,
        )
        drawRect(grain)
        drawRect(
            Brush.horizontalGradient(
                0f to Color.White.copy(alpha = 0.16f), 0.15f to Color.Transparent,
                0.8f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.45f),
            ),
        )
    }
}

/** A glass LED lens in a small metal bezel. [color] null = unlit. */
@Composable
fun LedLens(color: Color?, unlit: Color = Theme.AmberOff, diameter: Dp = 9.dp, modifier: Modifier = Modifier) {
    Canvas(modifier.size(diameter + 6.dp)) {
        val r = diameter.toPx() / 2f
        val c = center
        if (color != null) {
            drawCircle(color.copy(alpha = 0.16f), r * 2.6f, c)
            drawCircle(color.copy(alpha = 0.28f), r * 1.8f, c)
        }
        drawCircle(Color.White.copy(alpha = 0.09f), r + 3.dp.toPx(), c)
        drawCircle(Theme.Bezel, r + 2.dp.toPx(), c)
        drawCircle(Color.Black, r + 0.5.dp.toPx(), c)
        val lens = if (color != null) {
            Brush.radialGradient(
                0f to Color.White, 0.38f to color, 0.72f to color, 1f to Color.Black.copy(alpha = 0.4f),
                center = Offset(c.x - r * 0.3f, c.y - r * 0.4f), radius = r * 1.4f,
            )
        } else {
            Brush.radialGradient(
                0f to Color.White.copy(alpha = 0.32f), 0.42f to unlit, 1f to Color(0xFF050505),
                center = Offset(c.x - r * 0.3f, c.y - r * 0.4f), radius = r * 1.4f,
            )
        }
        drawCircle(lens, r, c)
    }
}

// ============================================================ text

/** Screen-printed label above or below a control. */
@Composable
fun PanelLabel(text: String, color: Color = Theme.Dim, modifier: Modifier = Modifier, size: TextUnit = 9.sp) {
    Text(
        text, color = color, fontSize = size, fontWeight = FontWeight.Bold, fontFamily = PanelFonts.Condensed,
        letterSpacing = 1.5.sp, maxLines = 1, overflow = TextOverflow.Clip,
        textAlign = TextAlign.Center, modifier = modifier,
    )
}

/** Amber section title, screen-printed onto the steel. */
@Composable
fun SectionTitle(text: String, modifier: Modifier = Modifier) {
    Text(
        text, color = Theme.Amber, fontSize = 11.5.sp, fontWeight = FontWeight.Bold, fontFamily = PanelFonts.Condensed,
        letterSpacing = 1.8.sp, maxLines = 1, modifier = modifier,
    )
}

// ============================================================ sections

/** A section plate on the steel, with an amber title. Tap / double-tap on the title are optional. */
@Composable
fun Section(
    title: String,
    modifier: Modifier = Modifier,
    onTitleClick: (() -> Unit)? = null,
    onTitleDoubleClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    SectionPlate(modifier) {
        val tapCb by rememberUpdatedState(onTitleClick)
        val doubleCb by rememberUpdatedState(onTitleDoubleClick)
        val titleMod = when {
            onTitleDoubleClick != null -> Modifier.pointerInput(Unit) {
                detectTapGestures(onTap = { tapCb?.invoke() }, onDoubleTap = { doubleCb?.invoke() })
            }
            onTitleClick != null -> Modifier.clickable { onTitleClick() }
            else -> Modifier
        }
        SectionTitle(title, titleMod.padding(bottom = 2.dp))
        Box(Modifier.fillMaxWidth().height(1.dp).background(Theme.Line))
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/** The plate itself: a faint lift off the steel, a thin outline and a lit top edge. */
@Composable
fun SectionPlate(modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(4.dp)
    Column(
        modifier
            .background(Theme.Panel, shape)
            .border(1.dp, Theme.Line, shape)
            .drawBehind {
                drawLine(
                    Color.White.copy(alpha = 0.06f), Offset(4.dp.toPx(), 1.5f), Offset(size.width - 4.dp.toPx(), 1.5f), 1f,
                )
            }
            .padding(horizontal = 7.dp, vertical = 4.dp),
        content = content,
    )
}

// ============================================================ knob

/**
 * Black hardware knob with a steel edge and a glowing value ring. Drag up/down to turn.
 * onTouch(true/false) brackets the gesture so motion recording knows a finger is on it.
 */
@Composable
fun Knob(
    label: String,
    value: Float,
    ring: Color,
    modifier: Modifier = Modifier,
    knobSize: Dp = 46.dp,
    onTouch: (Boolean) -> Unit,
    onChange: (Float) -> Unit,
) {
    val latestValue by rememberUpdatedState(value)
    val touchCb by rememberUpdatedState(onTouch)
    val changeCb by rememberUpdatedState(onChange)
    var dragValue by remember { mutableFloatStateOf(value) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .size(knobSize)
                .pointerInput(Unit) {
                    val travel = knobSize.toPx() * 4f  // drag 4 knob-heights for full range
                    detectVerticalDragGestures(
                        onDragStart = { dragValue = latestValue; touchCb(true) },
                        onDragEnd = { touchCb(false) },
                        onDragCancel = { touchCb(false) },
                        onVerticalDrag = { change, dy ->
                            change.consume()
                            dragValue = (dragValue - dy / travel).coerceIn(0f, 1f)
                            changeCb(dragValue)
                        },
                    )
                },
        ) { drawKnob(value, ring) }
        Text(
            label, color = Theme.Label, fontSize = 10.5.sp, fontWeight = FontWeight.Bold, fontFamily = PanelFonts.Condensed,
            letterSpacing = 0.8.sp, maxLines = 1, modifier = Modifier.padding(top = 1.dp),
        )
    }
}

private fun DrawScope.drawKnob(value: Float, ring: Color) {
    val c = center
    val r = size.minDimension / 2f
    val arcR = r * 0.94f
    val arcTopLeft = Offset(c.x - arcR, c.y - arcR)
    val arcSize = Size(arcR * 2, arcR * 2)
    val sw = r * 0.07f
    // value ring: track, glow, line
    drawArc(Color.White.copy(alpha = 0.10f), 135f, 270f, false, arcTopLeft, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
    drawArc(ring.copy(alpha = 0.28f), 135f, 270f * value, false, arcTopLeft, arcSize, style = Stroke(sw * 3.2f, cap = StrokeCap.Round))
    drawArc(ring, 135f, 270f * value, false, arcTopLeft, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
    // printed tick marks
    for (k in 0..6) {
        val a = Math.toRadians(-135.0 + 45.0 * k)
        val s = sin(a).toFloat(); val co = cos(a).toFloat()
        drawLine(
            Theme.Tick,
            Offset(c.x + r * 0.76f * s, c.y - r * 0.76f * co),
            Offset(c.x + r * 0.84f * s, c.y - r * 0.84f * co),
            strokeWidth = r * 0.05f,
        )
    }
    // body: drop shadow, skirt with steel edge, cap, highlight, centre
    drawCircle(Color.Black.copy(alpha = 0.6f), r * 0.68f, c + Offset(0f, r * 0.09f))
    drawCircle(Theme.KnobSkirt, r * 0.68f, c)
    drawCircle(Theme.KnobEdge, r * 0.68f, c, style = Stroke(r * 0.035f))
    drawCircle(Theme.KnobCap, r * 0.53f, c)
    drawCircle(Theme.KnobRim, r * 0.53f, c, style = Stroke(r * 0.035f))
    drawArc(
        Color.White.copy(alpha = 0.22f), 200f, 140f, false,
        Offset(c.x - r * 0.45f, c.y - r * 0.45f), Size(r * 0.9f, r * 0.9f),
        style = Stroke(r * 0.07f, cap = StrokeCap.Round),
    )
    drawCircle(Theme.KnobInner, r * 0.34f, c)
    // pointer
    val a = Math.toRadians(-135.0 + 270.0 * value)
    drawLine(
        Color.White, c,
        Offset(c.x + r * 0.6f * sin(a).toFloat(), c.y - r * 0.6f * cos(a).toFloat()),
        strokeWidth = r * 0.11f, cap = StrokeCap.Round,
    )
}

// ============================================================ data wheel

/** Big data wheel: drag in any direction; each notch reports +1 / -1. */
@Composable
fun DataWheel(modifier: Modifier = Modifier, wheelSize: Dp = 78.dp, onNotch: (Int) -> Unit) {
    val notchCb by rememberUpdatedState(onNotch)
    var acc by remember { mutableFloatStateOf(0f) }
    var angle by remember { mutableFloatStateOf(0f) }
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        Canvas(
            Modifier
                .size(wheelSize)
                .pointerInput(Unit) {
                    val notch = 14.dp.toPx()
                    detectDragGestures { change, drag ->
                        change.consume()
                        acc += drag.x - drag.y   // right or up = increase
                        while (acc > notch) { acc -= notch; angle += 15f; notchCb(1) }
                        while (acc < -notch) { acc += notch; angle -= 15f; notchCb(-1) }
                    }
                },
        ) {
            val r = size.minDimension / 2f * 0.92f
            val c = center
            drawCircle(Color.Black.copy(alpha = 0.6f), r, c + Offset(0f, r * 0.06f))
            drawCircle(
                Brush.radialGradient(listOf(Color(0xFF34383D), Color(0xFF2A2D31), Color(0xFF1C1E21)), center = Offset(c.x, c.y - r * 0.4f), radius = r * 1.6f),
                r, c,
            )
            drawCircle(Theme.KnobRim, r, c, style = Stroke(r * 0.06f))
            for (k in 0 until 8) {
                val a = Math.toRadians((k * 45f + angle).toDouble())
                drawLine(
                    Theme.KnobRim,
                    Offset(c.x + r * 0.66f * sin(a).toFloat(), c.y - r * 0.66f * cos(a).toFloat()),
                    Offset(c.x + r * 0.84f * sin(a).toFloat(), c.y - r * 0.84f * cos(a).toFloat()),
                    strokeWidth = r * 0.06f,
                )
            }
            val a = Math.toRadians(angle.toDouble() + 40.0)
            val dimple = Offset(c.x + r * 0.42f * sin(a).toFloat(), c.y - r * 0.42f * cos(a).toFloat())
            drawCircle(Color(0xFF101214), r * 0.15f, dimple)
            drawCircle(Theme.Red, r * 0.15f, dimple, style = Stroke(r * 0.05f))
        }
        PanelLabel("DATA", modifier = Modifier.padding(top = 2.dp))
    }
}

// ============================================================ buttons

/** Graphite rubber button with a label above it. Lit buttons glow red. */
@Composable
fun HwButton(
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    activeColor: Color = Theme.Red,
    height: Dp = 40.dp,
    round: Boolean = false,
    caption: String? = null,
    onClick: () -> Unit,
) {
    val radius = if (round) height / 2 else 5.dp
    val shape = RoundedCornerShape(radius)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        if (label.isNotEmpty()) {
            PanelLabel(label, color = if (active) Theme.RedText else Theme.Dim)
            Spacer(Modifier.height(2.dp))
        }
        val body = if (active) {
            Brush.verticalGradient(listOf(lighten(activeColor, 0.25f), darken(activeColor, 0.15f)))
        } else {
            Brush.verticalGradient(listOf(Color(0xFF3A3E43), Color(0xFF2A2D31)))
        }
        Box(
            Modifier
                .fillMaxWidth()
                .height(height)
                .let { if (active) it.shadow(10.dp, shape, ambientColor = activeColor, spotColor = activeColor) else it }
                .drawBehind {
                    // rubber key edge under the button
                    drawRoundRect(
                        if (active) darken(activeColor, 0.55f) else Theme.Edge,
                        topLeft = Offset(0f, 2.dp.toPx()), size = size, cornerRadius = CornerRadius(radius.toPx()),
                    )
                }
                .background(body, shape)
                .border(1.dp, if (active) darken(activeColor, 0.55f) else Theme.Edge, shape)
                .drawBehind {
                    drawLine(
                        Color.White.copy(alpha = if (active) 0.3f else 0.08f),
                        Offset(radius.toPx() * 0.6f, 1.5f), Offset(size.width - radius.toPx() * 0.6f, 1.5f), 1f,
                    )
                }
                .clickable(remember { MutableInteractionSource() }, indication = null) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            if (caption != null) {
                Text(
                    caption, color = if (active) Color.White else Theme.Label,
                    fontSize = 11.sp, fontWeight = FontWeight.Bold, fontFamily = PanelFonts.Condensed, letterSpacing = 1.sp,
                )
            }
        }
    }
}

enum class TransportKind { REC, STOP, PLAY }

/** Domed rubber transport button sitting in a steel collar; PLAY is lit from inside. */
@Composable
fun TransportButton(kind: TransportKind, lit: Boolean, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val label = kind.name
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        PanelLabel(label, color = if (kind == TransportKind.PLAY && lit) Theme.RedText else Theme.Dim)
        Spacer(Modifier.height(3.dp))
        Box(
            Modifier
                .size(48.dp)
                .clickable(remember { MutableInteractionSource() }, indication = null) { onClick() },
            contentAlignment = Alignment.Center,
        ) {
            Canvas(Modifier.fillMaxSize()) {
                val r = size.minDimension / 2f - 4.dp.toPx()
                val c = center
                val glowOn = (kind == TransportKind.PLAY && lit) || (kind == TransportKind.REC && lit)
                if (glowOn) {
                    drawCircle(Theme.Red.copy(alpha = 0.18f), r + 9.dp.toPx(), c)
                    drawCircle(Theme.Red.copy(alpha = 0.22f), r + 6.dp.toPx(), c)
                }
                drawCircle(Color.Black.copy(alpha = 0.6f), r + 4.dp.toPx(), c + Offset(0f, 2.dp.toPx()))
                drawCircle(Color.White.copy(alpha = 0.08f), r + 4.dp.toPx(), c)
                drawCircle(Color(0xFF141619), r + 3.dp.toPx(), c)
                drawCircle(Color.Black, r + 0.6.dp.toPx(), c)
                val dome = if (kind == TransportKind.PLAY && lit) {
                    Brush.radialGradient(
                        0f to Color(0xFFFF8A7A), 0.42f to Color(0xFFE8352E), 1f to Color(0xFF9A1712),
                        center = Offset(c.x, c.y - r * 0.4f), radius = r * 1.5f,
                    )
                } else {
                    Brush.radialGradient(
                        0f to Color(0xFF4E5258), 0.55f to Color(0xFF2E3135), 1f to Color(0xFF1A1C1F),
                        center = Offset(c.x, c.y - r * 0.45f), radius = r * 1.5f,
                    )
                }
                drawCircle(dome, r, c)
                drawArc(
                    Color.White.copy(alpha = if (kind == TransportKind.PLAY && lit) 0.35f else 0.22f), 200f, 140f, false,
                    Offset(c.x - r * 0.82f, c.y - r * 0.82f), Size(r * 1.64f, r * 1.64f),
                    style = Stroke(1.5.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            val (glyph, color) = when (kind) {
                TransportKind.REC -> "●" to (if (lit) Color(0xFFFF6B6B) else Theme.Red)
                TransportKind.STOP -> "■" to Color(0xFFD8DCE0)
                TransportKind.PLAY -> "▶" to (if (lit) Color(0xFFFFF4E6) else Color(0xFFD8DCE0))
            }
            Text(
                glyph, color = color, fontSize = 15.sp,
                style = TextStyle(
                    shadow = if (kind == TransportKind.STOP || (kind == TransportKind.PLAY && !lit)) Shadow(Color.Black, Offset(0f, 2f), 0f)
                    else Shadow(color, blurRadius = 12f),
                ),
            )
        }
    }
}

// ============================================================ step key

/** One step: glass LED lens, sculpted rubber key, step number. */
@Composable
fun StepKey(
    number: Int,
    ledColor: Color?,        // null = LED off
    isPlayhead: Boolean,
    isSelected: Boolean,
    isBeat: Boolean,
    noteText: String,
    modifier: Modifier = Modifier,
    onClick: () -> Unit,
) {
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        LedLens(if (isPlayhead) Theme.Red else ledColor)
        Spacer(Modifier.height(1.dp))
        val shape = RoundedCornerShape(4.dp)
        val top = if (isBeat) {
            Brush.verticalGradient(0f to Color(0xFF5A5E64), 0.46f to Color(0xFF3D4146), 1f to Color(0xFF2B2E32))
        } else {
            Brush.verticalGradient(0f to Color(0xFF474B50), 0.46f to Color(0xFF313438), 1f to Color(0xFF222528))
        }
        val press = if (isPlayhead) 2.dp else 0.dp
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(bottom = 3.dp)
                .offset(y = press)
                .let { if (isPlayhead) it.shadow(8.dp, shape, ambientColor = Theme.Red, spotColor = Theme.Red) else it }
                .drawBehind {
                    if (!isPlayhead) {
                        // soft drop shadow and the solid key base under the cap
                        drawRoundRect(Color.Black.copy(alpha = 0.55f), Offset(0f, 4.dp.toPx()), size, CornerRadius(4.dp.toPx()))
                        drawRoundRect(Theme.KeyEdge, Offset(0f, 3.dp.toPx()), size, CornerRadius(4.dp.toPx()))
                    } else {
                        drawRoundRect(Theme.KeyEdge, Offset(0f, 1.dp.toPx()), size, CornerRadius(4.dp.toPx()))
                    }
                }
                .background(top, shape)
                .border(
                    if (isSelected) 2.dp else 1.dp,
                    if (isSelected) Theme.Amber else Theme.KeyEdge, shape,
                )
                .drawBehind {
                    val w = size.width; val h = size.height
                    if (isPlayhead) {
                        // pressed in: shadowed top edge and a red glow from below
                        drawRect(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.6f), Color.Transparent), endY = 6.dp.toPx()))
                        drawRect(Brush.radialGradient(listOf(Theme.Red.copy(alpha = 0.35f), Color.Transparent), radius = w))
                    } else {
                        // lit top edge, shaded sides and bottom
                        drawLine(Color.White.copy(alpha = 0.22f), Offset(3.dp.toPx(), 1.5f), Offset(w - 3.dp.toPx(), 1.5f), 1.2f)
                        drawRect(Brush.horizontalGradient(0f to Color.White.copy(alpha = 0.05f), 0.15f to Color.Transparent, 0.85f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.3f)))
                        drawRect(Brush.verticalGradient(0.75f to Color.Transparent, 1f to Color.Black.copy(alpha = 0.45f), startY = 0f, endY = h))
                    }
                }
                .clickable(remember { MutableInteractionSource() }, indication = null) { onClick() },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Text(
                noteText, color = if (isPlayhead) Color(0xFFFFD9D4) else Theme.KeyText,
                fontSize = 9.5.sp, fontWeight = FontWeight.SemiBold, fontFamily = PanelFonts.Condensed,
                modifier = Modifier.padding(bottom = 3.dp),
            )
        }
        Text(
            "$number", color = if (isBeat) Theme.Amber else Theme.Dim, fontSize = 10.sp,
            fontWeight = FontWeight.Bold, fontFamily = PanelFonts.Condensed,
        )
    }
}

// ============================================================ crossfader

/**
 * DJ-mixer style crossfader: a black ribbed cap with a red / white / red centre
 * line, riding in a recessed slot. The slot glows from amber (scene A) to red
 * (scene B), and the lit part and the cap's glow blend to the colour at the cap.
 */
@Composable
fun Crossfader(
    value: Float,
    modifier: Modifier = Modifier,
    onStart: () -> Unit,
    onChange: (Float) -> Unit,
    onEnd: () -> Unit,
) {
    val latest by rememberUpdatedState(value)
    val startCb by rememberUpdatedState(onStart)
    val changeCb by rememberUpdatedState(onChange)
    val endCb by rememberUpdatedState(onEnd)
    var drag by remember { mutableFloatStateOf(value) }
    Box(modifier.height(40.dp)) {
        val capW = 18.dp
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    val travel = size.width - capW.toPx()
                    detectHorizontalDragGestures(
                        onDragStart = { drag = latest; startCb() },
                        onDragEnd = { endCb() },
                        onDragCancel = { endCb() },
                        onHorizontalDrag = { change, dx ->
                            change.consume()
                            drag = (drag + dx / travel).coerceIn(0f, 1f)
                            changeCb(drag)
                        },
                    )
                },
        ) {
            val w = size.width
            val cy = size.height / 2f
            val cw = capW.toPx()
            val ch = 32.dp.toPx()
            val slotH = 6.dp.toPx()
            val travel = w - cw
            val capX = cw / 2f + travel * value
            val mix = Theme.morphColor(value)
            val slotTop = cy - slotH / 2f
            // recessed slot with a faint amber-to-red wash
            drawRoundRect(Color(0xFF050607), Offset(0f, slotTop), Size(w, slotH), CornerRadius(slotH / 2))
            drawRoundRect(
                Brush.horizontalGradient(listOf(Theme.Amber.copy(alpha = 0.4f), Theme.Red.copy(alpha = 0.3f))),
                Offset(0f, slotTop), Size(w, slotH), CornerRadius(slotH / 2),
            )
            drawLine(Color.Black.copy(alpha = 0.9f), Offset(slotH / 2, slotTop + 1f), Offset(w - slotH / 2, slotTop + 1f), 2f)
            drawLine(Color.White.copy(alpha = 0.08f), Offset(slotH / 2, slotTop + slotH + 1f), Offset(w - slotH / 2, slotTop + slotH + 1f), 1f)
            // lit part from A to the cap, blending to the colour at the cap
            if (capX > slotH) {
                drawRoundRect(
                    Brush.horizontalGradient(listOf(Theme.Amber, mix), startX = 0f, endX = capX),
                    Offset(0f, slotTop), Size(capX, slotH), CornerRadius(slotH / 2),
                )
            }
            // cap glow
            for (k in 3 downTo 1) {
                val g = k * 3.dp.toPx()
                drawRoundRect(
                    mix.copy(alpha = 0.12f), Offset(capX - cw / 2 - g, cy - ch / 2 - g), Size(cw + 2 * g, ch + 2 * g),
                    CornerRadius(3.dp.toPx() + g),
                )
            }
            // cap shadow, body, ridges, centre line, rim
            val capTL = Offset(capX - cw / 2, cy - ch / 2)
            val capSize = Size(cw, ch)
            val rr = CornerRadius(3.dp.toPx())
            drawRoundRect(Color.Black.copy(alpha = 0.75f), capTL + Offset(0f, 3.dp.toPx()), capSize, rr)
            drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF3C4045), Color(0xFF222528), Color(0xFF141618)), startY = capTL.y, endY = capTL.y + ch), capTL, capSize, rr)
            var y = capTL.y + 2.dp.toPx()
            while (y < capTL.y + ch - 2.dp.toPx()) {
                drawLine(Color.White.copy(alpha = 0.09f), Offset(capTL.x + 1.5f, y), Offset(capTL.x + cw - 1.5f, y), 1f)
                y += 3.dp.toPx() / 1.5f
            }
            val lineH = ch - 4.dp.toPx()
            val lineTop = capTL.y + 2.dp.toPx()
            drawRect(Theme.Red, Offset(capX - 2.5.dp.toPx(), lineTop), Size(5.dp.toPx(), lineH))
            drawRect(Color.White, Offset(capX - 1.dp.toPx(), lineTop), Size(2.dp.toPx(), lineH))
            drawRoundRect(Color.Black, capTL, capSize, rr, style = Stroke(1.dp.toPx()))
            drawLine(Color.White.copy(alpha = 0.22f), capTL + Offset(2.dp.toPx(), 1.5f), capTL + Offset(cw - 2.dp.toPx(), 1.5f), 1f)
        }
    }
}

// ============================================================ helpers

fun lighten(c: Color, t: Float) = Color(c.red + (1 - c.red) * t, c.green + (1 - c.green) * t, c.blue + (1 - c.blue) * t, c.alpha)
fun darken(c: Color, t: Float) = Color(c.red * (1 - t), c.green * (1 - t), c.blue * (1 - t), c.alpha)

/** Fills the remaining column height; used to let weights work inside ColumnScope helpers. */
@Composable
fun ColumnScope.Grow() = Spacer(Modifier.weight(1f))
