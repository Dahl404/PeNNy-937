package com.dahl404.penny937.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.unit.dp
import com.dahl404.penny937.ui.theme.LocalPenny
import kotlin.math.min

/** Penny, the app's pen mascot. A little ink, a little personality. */
@Composable
fun PennyMascot(
    modifier: Modifier = Modifier,
    accent: Color = LocalPenny.current.accent
) {
    val palette = LocalPenny.current
    val transition = rememberInfiniteTransition(label = "penny")
    val bob by transition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(2600, easing = CubicBezierEasing(0.45f, 0f, 0.55f, 1f)),
            repeatMode = RepeatMode.Reverse
        ),
        label = "bob"
    )
    val blink by transition.animateFloat(
        initialValue = 1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = keyframes {
                durationMillis = 5400
                1f at 0
                1f at 4700
                0.06f at 4820
                1f at 4940
            }
        ),
        label = "blink"
    )

    val body = palette.textPrimary
    val face = if (palette.isDark) Color(0xFF17171A) else Color(0xFFF8F3E7)
    val cheek = Color(0xFFFF8A80).copy(alpha = 0.45f)

    Canvas(modifier = modifier) {
        val designW = 200f
        val designH = 224f
        val s = min(size.width / designW, size.height / designH)
        val left = (size.width - designW * s) / 2f
        val top = (size.height - designH * s) / 2f
        translate(left, top) {
            scale(s, s, pivot = Offset.Zero) {
                // Ground shadow.
                drawOval(
                    color = Color.Black.copy(alpha = if (palette.isDark) 0.35f else 0.08f),
                    topLeft = Offset(58f, 200f + (bob - 0.5f) * 2f),
                    size = Size(84f, 13f)
                )
                translate(0f, (bob - 0.5f) * 6f) {
                    rotate(-8f, pivot = Offset(100f, 118f)) {
                        // Clip.
                        drawRoundRect(
                            color = accent,
                            topLeft = Offset(122f, 36f),
                            size = Size(12f, 44f),
                            cornerRadius = CornerRadius(6f, 6f)
                        )
                        // Nib.
                        val nib = Path().apply {
                            moveTo(72f, 148f)
                            lineTo(128f, 148f)
                            lineTo(108f, 190f)
                            quadraticBezierTo(100f, 201f, 92f, 190f)
                            close()
                        }
                        drawPath(nib, body)
                        drawLine(
                            color = face.copy(alpha = 0.9f),
                            start = Offset(100f, 182f),
                            end = Offset(100f, 194f),
                            strokeWidth = 3f
                        )
                        // Body.
                        val bodyPath = Path().apply {
                            moveTo(72f, 148f)
                            lineTo(72f, 56f)
                            cubicTo(72f, 35f, 86f, 25f, 100f, 25f)
                            cubicTo(114f, 25f, 128f, 35f, 128f, 56f)
                            lineTo(128f, 148f)
                            close()
                        }
                        drawPath(bodyPath, body)
                        // Collar.
                        drawRect(
                            color = accent,
                            topLeft = Offset(72f, 132f),
                            size = Size(56f, 15f)
                        )
                        // Face plate.
                        drawRoundRect(
                            color = face,
                            topLeft = Offset(82f, 50f),
                            size = Size(36f, 80f),
                            cornerRadius = CornerRadius(18f, 18f)
                        )
                        // Cheeks.
                        drawCircle(cheek, radius = 4f, center = Offset(87f, 106f))
                        drawCircle(cheek, radius = 4f, center = Offset(113f, 106f))
                        // Eyes.
                        if (blink > 0.25f) {
                            val eyeR = 3.4f * blink
                            drawCircle(body, radius = eyeR, center = Offset(93f, 92f))
                            drawCircle(body, radius = eyeR, center = Offset(107f, 92f))
                            drawCircle(face, radius = 1.05f, center = Offset(92f, 90.8f))
                            drawCircle(face, radius = 1.05f, center = Offset(106f, 90.8f))
                        } else {
                            drawLine(
                                color = body,
                                start = Offset(90.5f, 92f),
                                end = Offset(95.5f, 92f),
                                strokeWidth = 2.2f
                            )
                            drawLine(
                                color = body,
                                start = Offset(104.5f, 92f),
                                end = Offset(109.5f, 92f),
                                strokeWidth = 2.2f
                            )
                        }
                        // Smile.
                        val smile = Path().apply {
                            moveTo(92.5f, 104f)
                            quadraticBezierTo(100f, 111.5f, 107.5f, 104f)
                        }
                        drawPath(smile, body, style = Stroke(width = 2.4f))
                        // Body highlight.
                        drawLine(
                            color = face.copy(alpha = 0.16f),
                            start = Offset(78.5f, 60f),
                            end = Offset(78.5f, 88f),
                            strokeWidth = 3f
                        )
                    }
                }
            }
        }
    }
}
