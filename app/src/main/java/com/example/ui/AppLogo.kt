package com.example.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import androidx.compose.ui.geometry.Offset
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGold

@Composable
fun AppLogo(modifier: Modifier = Modifier) {
    val infiniteTransition = rememberInfiniteTransition(label = "LogoAnimation")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.95f,
        targetValue = 1.05f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "LogoPulse"
    )
    
    val rotateAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(6000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "LogoRotation"
    )

    Box(
        modifier = modifier,
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = size / 2f
            val centerOffset = Offset(size.width / 2f, size.height / 2f)
            val radius = size.minDimension / 2.2f * pulseScale
            
            // Draw outer radar circles with neon gradients
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(CyberGold.copy(alpha = 0.05f), Color.Transparent),
                    center = centerOffset,
                    radius = radius * 1.5f
                ),
                radius = radius * 1.3f
            )

            // Dynamic cyber ring
            drawCircle(
                color = CyberGold.copy(alpha = 0.15f),
                radius = radius,
                style = Stroke(width = 1.5f)
            )

            // Inner cyan ring
            drawCircle(
                color = CyberCyan.copy(alpha = 0.25f),
                radius = radius * 0.75f,
                style = Stroke(width = 1.0f)
            )

            // Center target indicator
            drawCircle(
                color = CyberGold,
                radius = 6f
            )

            // Hexagonal radar target nodes
            val numPoints = 6
            val hexPath = Path()
            for (i in 0 until numPoints) {
                val angleRad = Math.toRadians((i * 60 + rotateAngle).toDouble())
                val x = center.width + radius * 0.75f * kotlin.math.cos(angleRad).toFloat()
                val y = center.height + radius * 0.75f * kotlin.math.sin(angleRad).toFloat()
                if (i == 0) {
                    hexPath.moveTo(x, y)
                } else {
                    hexPath.lineTo(x, y)
                }
                
                // Draw nodes
                drawCircle(
                    color = if (i % 2 == 0) CyberGold else CyberCyan,
                    radius = 4f,
                    center = androidx.compose.ui.geometry.Offset(x, y)
                )
            }
            hexPath.close()
            drawPath(
                path = hexPath,
                color = CyberGold.copy(alpha = 0.3f),
                style = Stroke(width = 1.5f)
            )
            
            // Draw radar sweeping lines
            val sweepAngleRad = Math.toRadians(rotateAngle.toDouble())
            val sweepX = center.width + radius * kotlin.math.cos(sweepAngleRad).toFloat()
            val sweepY = center.height + radius * kotlin.math.sin(sweepAngleRad).toFloat()
            
            drawLine(
                brush = Brush.linearGradient(
                    listOf(CyberGold, CyberGold.copy(alpha = 0.1f))
                ),
                start = centerOffset,
                end = androidx.compose.ui.geometry.Offset(sweepX, sweepY),
                strokeWidth = 2.5f
            )
        }
    }
}
