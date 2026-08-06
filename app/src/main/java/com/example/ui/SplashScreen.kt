package com.example.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.*
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGold
import com.example.ui.theme.DarkBg
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

/**
 * 60 FPS Native Jetpack Compose Canvas Animated Splash Screen.
 * Features Hexagon Forming Particles assembling from chaotic space into a glowing 3D cyber structure.
 */
@Composable
fun SplashScreen(
    onTimeout: () -> Unit
) {
    HexagonParticleSplashScreen(onSplashFinished = onTimeout)
}

@Composable
fun HexagonParticleSplashScreen(
    onSplashFinished: () -> Unit
) {
    var isVisible by remember { mutableStateOf(true) }
    val coroutineScope = rememberCoroutineScope()

    // Animation progress timer (0.0 to 1.0 over 2.5 seconds)
    val animProgress = remember { Animatable(0f) }
    val rotationAngle = remember { Animatable(0f) }

    LaunchedEffect(Unit) {
        // Run spin & assembly animation
        coroutineScope.launch {
            rotationAngle.animateTo(
                targetValue = 360f,
                animationSpec = tween(durationMillis = 3000, easing = LinearEasing)
            )
        }
        animProgress.animateTo(
            targetValue = 1f,
            animationSpec = tween(durationMillis = 2400, easing = FastOutSlowInEasing)
        )
        delay(300)
        isVisible = false
        delay(400)
        onSplashFinished()
    }

    // Generate random particle seed points
    val particles = remember {
        List(72) {
            ParticleData(
                startX = Random.nextFloat() * 2f - 1f,
                startY = Random.nextFloat() * 2f - 1f,
                size = Random.nextFloat() * 4f + 2f,
                color = when (Random.nextInt(4)) {
                    0 -> CyberGold
                    1 -> CyberCyan
                    2 -> Color(0xFF00FFCC)
                    else -> Color(0xFFB388FF)
                },
                targetHexCorner = Random.nextInt(6),
                cornerWeight = Random.nextFloat()
            )
        }
    }

    AnimatedVisibility(
        visible = isVisible,
        enter = fadeIn(),
        exit = fadeOut(animationSpec = tween(400))
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBg),
            contentAlignment = Alignment.Center
        ) {
            Canvas(
                modifier = Modifier
                    .fillMaxSize()
            ) {
                val cx = size.width / 2f
                val cy = size.height / 2f
                val maxRadius = minOf(size.width, size.height) * 0.3f
                val progress = animProgress.value
                val rotRad = Math.toRadians(rotationAngle.value.toDouble())

                // 1. Calculate Hexagon 6 Corner Vertices
                val hexVertices = List(6) { i ->
                    val angle = rotRad + i * (PI / 3.0)
                    Offset(
                        (cx + maxRadius * cos(angle)).toFloat(),
                        (cy + maxRadius * sin(angle)).toFloat()
                    )
                }

                // 2. Draw Hexagon Wireframe & Pulsing Laser Lines
                if (progress > 0.3f) {
                    val lineAlpha = ((progress - 0.3f) / 0.7f).coerceIn(0f, 1f)
                    val path = Path().apply {
                        moveTo(hexVertices[0].x, hexVertices[0].y)
                        for (i in 1 until 6) {
                            lineTo(hexVertices[i].x, hexVertices[i].y)
                        }
                        close()
                    }

                    // Outer glowing hexagon path
                    drawPath(
                        path = path,
                        color = CyberCyan.copy(alpha = 0.8f * lineAlpha),
                        style = Stroke(width = 3.dp.toPx())
                    )

                    // Inner radial laser spokes connecting to center
                    for (i in 0 until 6) {
                        drawLine(
                            color = CyberGold.copy(alpha = 0.5f * lineAlpha),
                            start = Offset(cx, cy),
                            end = hexVertices[i],
                            strokeWidth = 1.5.dp.toPx()
                        )
                    }

                    // Inner smaller spinning hexagon
                    val innerRadius = maxRadius * 0.5f
                    val innerVertices = List(6) { i ->
                        val angle = -rotRad * 1.5 + i * (PI / 3.0)
                        Offset(
                            (cx + innerRadius * cos(angle)).toFloat(),
                            (cy + innerRadius * sin(angle)).toFloat()
                        )
                    }
                    val innerPath = Path().apply {
                        moveTo(innerVertices[0].x, innerVertices[0].y)
                        for (i in 1 until 6) {
                            lineTo(innerVertices[i].x, innerVertices[i].y)
                        }
                        close()
                    }
                    drawPath(
                        path = innerPath,
                        color = CyberGold.copy(alpha = 0.9f * lineAlpha),
                        style = Stroke(width = 2.dp.toPx())
                    )
                }

                // 3. Draw Assembling Hexagon Particles
                particles.forEach { p ->
                    val targetCorner = hexVertices[p.targetHexCorner]
                    val nextCorner = hexVertices[(p.targetHexCorner + 1) % 6]

                    // Target point along the edge of hexagon
                    val edgeTargetX = targetCorner.x + (nextCorner.x - targetCorner.x) * p.cornerWeight
                    val edgeTargetY = targetCorner.y + (nextCorner.y - targetCorner.y) * p.cornerWeight

                    val initialX = cx + p.startX * size.width * 0.45f
                    val initialY = cy + p.startY * size.height * 0.45f

                    // Interpolate particle position from chaotic space to hexagon perimeter
                    val px = initialX + (edgeTargetX - initialX) * progress
                    val py = initialY + (edgeTargetY - initialY) * progress

                    // Particle glow radius
                    drawCircle(
                        color = p.color.copy(alpha = 0.3f + 0.7f * progress),
                        radius = p.size.dp.toPx() * (0.8f + 0.4f * sin(progress * PI.toFloat())),
                        center = Offset(px, py)
                    )

                    // Connecting laser thread when approaching hexagon shape
                    if (progress > 0.6f && Random.nextFloat() > 0.6f) {
                        drawLine(
                            color = p.color.copy(alpha = 0.3f * progress),
                            start = Offset(px, py),
                            end = Offset(cx, cy),
                            strokeWidth = 1.dp.toPx()
                        )
                    }
                }

                // 4. Center Glowing Core Particle
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(
                            CyberGold,
                            CyberCyan.copy(alpha = 0.6f),
                            Color.Transparent
                        ),
                        center = Offset(cx, cy),
                        radius = (30f + 25f * progress).dp.toPx()
                    ),
                    center = Offset(cx, cy),
                    radius = (30f + 25f * progress).dp.toPx()
                )
            }

            // Splash Branding Overlay
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .padding(top = 320.dp)
            ) {
                Text(
                    text = "OKM 3D VISUALIZER PRO",
                    color = CyberGold,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 2.sp
                )
                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "سامانه تاکتیکال تصویربرداری و عمق‌سنجی سه‌بعدی زمین",
                    color = Color.White.copy(alpha = 0.8f),
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

private data class ParticleData(
    val startX: Float,
    val startY: Float,
    val size: Float,
    val color: Color,
    val targetHexCorner: Int,
    val cornerWeight: Float
)
