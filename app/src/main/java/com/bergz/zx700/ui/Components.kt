package com.bergz.zx700.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.cos
import kotlin.math.sin

/** Tiny screen-printed label above or below a control. */
@Composable
fun PanelLabel(text: String, color: Color = Neon.Dim, modifier: Modifier = Modifier) {
    Text(
        text, color = color, fontSize = 8.sp, fontWeight = FontWeight.ExtraBold,
        letterSpacing = 1.2.sp, maxLines = 1, overflow = TextOverflow.Clip,
        textAlign = TextAlign.Center, modifier = modifier,
    )
}

/** A neon-outlined panel section with a pink title. Tapping the title is optional. */
@Composable
fun Section(
    title: String,
    modifier: Modifier = Modifier,
    onTitleClick: (() -> Unit)? = null,
    content: @Composable RowScope.() -> Unit,
) {
    val shape = RoundedCornerShape(10.dp)
    Column(
        modifier
            .shadow(8.dp, shape, ambientColor = Neon.Purple, spotColor = Neon.Purple)
            .background(Neon.Panel, shape)
            .border(1.dp, Neon.Purple, shape)
            .padding(horizontal = 6.dp, vertical = 4.dp),
    ) {
        val titleMod = if (onTitleClick != null) Modifier.clickable { onTitleClick() } else Modifier
        Text(
            title, color = Neon.Pink, fontSize = 9.sp, fontWeight = FontWeight.ExtraBold,
            letterSpacing = 2.sp, maxLines = 1, modifier = titleMod.padding(bottom = 2.dp),
        )
        Box(Modifier.fillMaxWidth().height(1.dp).background(Neon.Line))
        Row(
            Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
            content = content,
        )
    }
}

/**
 * Realistic hardware knob with a thin neon value ring. Drag up/down to turn.
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
            label, color = Neon.Label, fontSize = 8.sp, fontWeight = FontWeight.SemiBold,
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
    drawArc(Color(0xFF24183A), 135f, 270f, false, arcTopLeft, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
    drawArc(ring.copy(alpha = 0.3f), 135f, 270f * value, false, arcTopLeft, arcSize, style = Stroke(sw * 3f, cap = StrokeCap.Round))
    drawArc(ring, 135f, 270f * value, false, arcTopLeft, arcSize, style = Stroke(sw, cap = StrokeCap.Round))
    // printed tick marks
    for (k in 0..6) {
        val a = Math.toRadians(-135.0 + 45.0 * k)
        val s = sin(a).toFloat(); val co = cos(a).toFloat()
        drawLine(
            Color(0xFF8C7BAA),
            Offset(c.x + r * 0.76f * s, c.y - r * 0.76f * co),
            Offset(c.x + r * 0.84f * s, c.y - r * 0.84f * co),
            strokeWidth = r * 0.05f,
        )
    }
    // body: drop shadow, skirt, cap, highlight
    drawCircle(Color(0xFF040306), r * 0.68f, c + Offset(0f, r * 0.09f))
    drawCircle(Color(0xFF18181C), r * 0.68f, c)
    drawCircle(Color(0xFF25262B), r * 0.53f, c)
    drawCircle(Color(0xFF3D3E45), r * 0.53f, c, style = Stroke(r * 0.03f))
    drawArc(
        Color.White.copy(alpha = 0.13f), 200f, 140f, false,
        Offset(c.x - r * 0.45f, c.y - r * 0.45f), Size(r * 0.9f, r * 0.9f),
        style = Stroke(r * 0.07f, cap = StrokeCap.Round),
    )
    drawCircle(Color(0xFF2F3036), r * 0.34f, c)
    // pointer
    val a = Math.toRadians(-135.0 + 270.0 * value)
    drawLine(
        Color(0xFFF4F4F6), c,
        Offset(c.x + r * 0.6f * sin(a).toFloat(), c.y - r * 0.6f * cos(a).toFloat()),
        strokeWidth = r * 0.11f, cap = StrokeCap.Round,
    )
}

/** Big data wheel: drag in any direction; each notch reports +1 / -1. */
@Composable
fun DataWheel(modifier: Modifier = Modifier, wheelSize: Dp = 66.dp, onNotch: (Int) -> Unit) {
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
            val r = size.minDimension / 2f
            drawCircle(Neon.Purple.copy(alpha = 0.25f), r, center)
            drawCircle(Color(0xFF140E1E), r * 0.92f, center)
            drawCircle(Neon.Purple, r * 0.92f, center, style = Stroke(r * 0.05f))
            for (k in 0 until 24) {
                val a = Math.toRadians((k * 15f + angle).toDouble())
                drawLine(
                    Neon.Line,
                    Offset(center.x + r * 0.72f * sin(a).toFloat(), center.y - r * 0.72f * cos(a).toFloat()),
                    Offset(center.x + r * 0.86f * sin(a).toFloat(), center.y - r * 0.86f * cos(a).toFloat()),
                    strokeWidth = r * 0.04f,
                )
            }
            val a = Math.toRadians(angle.toDouble() + 40.0)
            val dimple = Offset(center.x + r * 0.5f * sin(a).toFloat(), center.y - r * 0.5f * cos(a).toFloat())
            drawCircle(Neon.Body, r * 0.14f, dimple)
            drawCircle(Neon.Blue, r * 0.14f, dimple, style = Stroke(r * 0.04f))
        }
        PanelLabel("DATA", modifier = Modifier.padding(top = 2.dp))
    }
}

/** Rubber hardware button with a label above it. */
@Composable
fun HwButton(
    label: String,
    modifier: Modifier = Modifier,
    active: Boolean = false,
    activeColor: Color = Neon.Pink,
    height: Dp = 40.dp,
    round: Boolean = false,
    caption: String? = null,
    onClick: () -> Unit,
) {
    val shape = if (round) RoundedCornerShape(50) else RoundedCornerShape(8.dp)
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        PanelLabel(label, color = if (active) activeColor else Neon.Dim)
        Spacer(Modifier.height(2.dp))
        val base = Modifier
            .fillMaxWidth()
            .height(height)
            .let { if (active) it.shadow(10.dp, shape, ambientColor = activeColor, spotColor = activeColor) else it }
            .background(if (active) activeColor.copy(alpha = 0.22f) else Neon.Panel, shape)
            .border(if (active) 2.dp else 1.dp, if (active) activeColor else Neon.Line, shape)
            .clickable(remember { MutableInteractionSource() }, indication = null) { onClick() }
        Box(base, contentAlignment = Alignment.Center) {
            if (caption != null) {
                Text(caption, color = if (active) Color.White else Neon.Label, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

/** One step: LED on top, rubber key, step number. */
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
        val led = if (isPlayhead) Neon.Blue else ledColor
        Box(
            Modifier
                .size(9.dp)
                .let { if (led != null) it.shadow(6.dp, CircleShape, ambientColor = led, spotColor = led) else it }
                .background(led ?: Neon.LedOff, CircleShape),
        )
        Spacer(Modifier.height(4.dp))
        val shape = RoundedCornerShape(6.dp)
        val edge = when {
            isPlayhead -> Neon.Blue
            isSelected -> Neon.Pink
            else -> Neon.KeyEdge
        }
        Box(
            Modifier
                .fillMaxWidth()
                .weight(1f)
                .offset(y = if (isPlayhead) 2.dp else 0.dp)
                .background(if (isBeat) Neon.KeyBeat else Neon.Key, shape)
                .border(if (isSelected || isPlayhead) 2.dp else 1.dp, edge, shape)
                .clickable(remember { MutableInteractionSource() }, indication = null) { onClick() },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Text(noteText, color = Neon.Muted, fontSize = 8.sp, modifier = Modifier.padding(bottom = 3.dp))
        }
        Text(
            "$number", color = if (isBeat) Neon.Pink else Neon.Muted, fontSize = 9.sp,
            fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 2.dp),
        )
    }
}

/** Fills the remaining column height; used to let weights work inside ColumnScope helpers. */
@Composable
fun ColumnScope.Grow() = Spacer(Modifier.weight(1f))
