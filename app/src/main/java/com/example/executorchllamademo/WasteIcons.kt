package com.example.executorchllamademo

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import kotlin.math.cos
import kotlin.math.sin

/** Which icon belongs to which waste category. */
fun categoryIcon(category: String): String = when (category) {
    "Biodegradable" -> "leaf"
    "Recyclable" -> "recycle"
    "E-Waste" -> "chip"
    "Hazardous" -> "hazard"
    else -> "info"
}

/**
 * A small line icon drawn with code, so the app needs no icon library.
 * kind: home, camera, chat, gallery, leaf, recycle, chip, hazard, bulb, check, cross,
 *       globe, back, send, stop, info
 */
@Composable
fun Icon24(kind: String, tint: Color, dp: Dp = 24.dp) {
    Canvas(modifier = Modifier.size(dp)) {
        val k = this.size.width / 24f
        scale(scaleX = k, scaleY = k, pivot = Offset.Zero) {
            drawIcon(kind, tint)
        }
    }
}

private fun DrawScope.drawIcon(kind: String, c: Color) {
    val stroke = Stroke(width = 1.9f, cap = StrokeCap.Round, join = StrokeJoin.Round)

    when (kind) {
        "home" -> {
            val p = Path().apply {
                moveTo(3f, 11f); lineTo(12f, 3.5f); lineTo(21f, 11f)
                moveTo(5.5f, 9.5f); lineTo(5.5f, 20f); lineTo(18.5f, 20f); lineTo(18.5f, 9.5f)
                moveTo(10f, 20f); lineTo(10f, 14f); lineTo(14f, 14f); lineTo(14f, 20f)
            }
            drawPath(p, c, style = stroke)
        }

        "camera" -> {
            drawRoundRect(c, Offset(2.5f, 7f), Size(19f, 13f), CornerRadius(3f), style = stroke)
            val top = Path().apply {
                moveTo(8f, 7f); lineTo(9.5f, 4.5f); lineTo(14.5f, 4.5f); lineTo(16f, 7f)
            }
            drawPath(top, c, style = stroke)
            drawCircle(c, 3.4f, Offset(12f, 13.5f), style = stroke)
        }

        "chat" -> {
            drawRoundRect(c, Offset(3f, 4f), Size(18f, 12f), CornerRadius(3.5f), style = stroke)
            val tail = Path().apply {
                moveTo(8f, 16f); lineTo(8f, 20.5f); lineTo(12.5f, 16f)
            }
            drawPath(tail, c, style = stroke)
            drawLine(c, Offset(8f, 8.5f), Offset(16f, 8.5f), 1.9f, StrokeCap.Round)
            drawLine(c, Offset(8f, 11.8f), Offset(13f, 11.8f), 1.9f, StrokeCap.Round)
        }

        "gallery" -> {
            drawRoundRect(c, Offset(3f, 4f), Size(18f, 16f), CornerRadius(3f), style = stroke)
            drawCircle(c, 1.6f, Offset(9f, 9.5f))
            val hills = Path().apply {
                moveTo(3.5f, 17f); lineTo(9f, 12.5f); lineTo(13f, 16f); lineTo(15.5f, 13.5f); lineTo(20.5f, 18f)
            }
            drawPath(hills, c, style = stroke)
        }

        "leaf" -> {
            val leaf = Path().apply {
                moveTo(5.5f, 19f)
                cubicTo(5f, 10f, 10f, 5f, 20f, 4f)
                cubicTo(20.5f, 13f, 16f, 19.5f, 5.5f, 19f)
                close()
            }
            drawPath(leaf, c, style = stroke)
            drawLine(c, Offset(4.5f, 20.5f), Offset(13f, 12f), 1.9f, StrokeCap.Round)
        }

        "recycle" -> {
            val cx = 12f
            val cy = 12f
            val r = 7.5f
            val starts = listOf(25f, 145f, 265f)
            val sweep = 85f
            for (start in starts) {
                drawArc(
                    c, start, sweep, false,
                    Offset(cx - r, cy - r), Size(2 * r, 2 * r), style = stroke
                )
                // arrow head at the end of the arc
                val end = Math.toRadians((start + sweep).toDouble())
                val px = cx + r * cos(end).toFloat()
                val py = cy + r * sin(end).toFloat()
                val tx = -sin(end).toFloat()
                val ty = cos(end).toFloat()
                val nx = cos(end).toFloat()
                val ny = sin(end).toFloat()
                val back = 3.2f
                val side = 2.4f
                drawLine(c, Offset(px, py),
                    Offset(px - tx * back + nx * side, py - ty * back + ny * side), 1.9f, StrokeCap.Round)
                drawLine(c, Offset(px, py),
                    Offset(px - tx * back - nx * side, py - ty * back - ny * side), 1.9f, StrokeCap.Round)
            }
        }

        "chip" -> {
            drawRoundRect(c, Offset(7f, 7f), Size(10f, 10f), CornerRadius(1.8f), style = stroke)
            for (t in listOf(10f, 12f, 14f)) {
                drawLine(c, Offset(t, 3.5f), Offset(t, 7f), 1.9f, StrokeCap.Round)
                drawLine(c, Offset(t, 17f), Offset(t, 20.5f), 1.9f, StrokeCap.Round)
                drawLine(c, Offset(3.5f, t), Offset(7f, t), 1.9f, StrokeCap.Round)
                drawLine(c, Offset(17f, t), Offset(20.5f, t), 1.9f, StrokeCap.Round)
            }
        }

        "hazard" -> {
            val tri = Path().apply {
                moveTo(12f, 3.5f); lineTo(21.5f, 20f); lineTo(2.5f, 20f); close()
            }
            drawPath(tri, c, style = stroke)
            drawLine(c, Offset(12f, 9.5f), Offset(12f, 14f), 1.9f, StrokeCap.Round)
            drawCircle(c, 1.05f, Offset(12f, 17f))
        }

        "bulb" -> {
            drawCircle(c, 5.5f, Offset(12f, 10f), style = stroke)
            drawLine(c, Offset(9.5f, 18f), Offset(14.5f, 18f), 1.9f, StrokeCap.Round)
            drawLine(c, Offset(10.5f, 21f), Offset(13.5f, 21f), 1.9f, StrokeCap.Round)
        }

        "check" -> {
            val p = Path().apply {
                moveTo(5f, 12.5f); lineTo(10f, 17.5f); lineTo(19f, 7f)
            }
            drawPath(p, c, style = stroke)
        }

        "cross" -> {
            drawLine(c, Offset(6.5f, 6.5f), Offset(17.5f, 17.5f), 1.9f, StrokeCap.Round)
            drawLine(c, Offset(17.5f, 6.5f), Offset(6.5f, 17.5f), 1.9f, StrokeCap.Round)
        }

        "globe" -> {
            drawCircle(c, 8.5f, Offset(12f, 12f), style = stroke)
            drawLine(c, Offset(3.5f, 12f), Offset(20.5f, 12f), 1.9f, StrokeCap.Round)
            drawOval(c, Offset(8f, 3.5f), Size(8f, 17f), style = stroke)
        }

        "back" -> {
            val p = Path().apply {
                moveTo(15f, 5f); lineTo(8f, 12f); lineTo(15f, 19f)
            }
            drawPath(p, c, style = stroke)
        }

        "send" -> {
            drawLine(c, Offset(12f, 19f), Offset(12f, 5.5f), 2.2f, StrokeCap.Round)
            val p = Path().apply {
                moveTo(6f, 11f); lineTo(12f, 5.5f); lineTo(18f, 11f)
            }
            drawPath(p, c, style = Stroke(width = 2.2f, cap = StrokeCap.Round, join = StrokeJoin.Round))
        }

        "flash" -> {
            val bolt = Path().apply {
                moveTo(13f, 2.5f); lineTo(5f, 13.5f); lineTo(11f, 13.5f)
                lineTo(10f, 21.5f); lineTo(19f, 10f); lineTo(13f, 10f); close()
            }
            drawPath(bolt, c, style = stroke)
        }

        "stop" -> {
            drawRoundRect(c, Offset(6f, 6f), Size(12f, 12f), CornerRadius(2.5f))
        }

        else -> {
            // info
            drawCircle(c, 9f, Offset(12f, 12f), style = stroke)
            drawLine(c, Offset(12f, 11f), Offset(12f, 16.5f), 1.9f, StrokeCap.Round)
            drawCircle(c, 1.05f, Offset(12f, 7.8f))
        }
    }
}