package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.ui.theme.CardBg
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGold
import com.example.ui.theme.DarkBg
import com.example.ui.theme.SurfaceBg
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class RadialMenuItem(
    val id: String,
    val title: String,
    val icon: ImageVector,
    val color: Color
)

/**
 * Floating Circular Radial Tool Menu (Glassmorphism + Smooth Motion).
 * Provides rapid touch access to all core application modules in a compact, elegant radial overlay.
 */
@Composable
fun RadialFabMenu(
    items: List<RadialMenuItem>,
    onItemSelected: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    // Spring animation for expansion radius and menu button rotation
    val animationProgress by animateFloatAsState(
        targetValue = if (isExpanded) 1f else 0f,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "RadialProgress"
    )

    val fabRotation by animateFloatAsState(
        targetValue = if (isExpanded) 135f else 0f,
        animationSpec = tween(durationMillis = 350, easing = FastOutSlowInEasing),
        label = "FabRotation"
    )

    val menuRadius = 110.dp

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.BottomEnd
    ) {
        // Full screen translucent dark backdrop when radial menu is open
        if (isExpanded || animationProgress > 0.05f) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(DarkBg.copy(alpha = 0.65f * animationProgress))
                    .clickable(
                        interactionSource = remember { MutableInteractionSource() },
                        indication = null
                    ) { isExpanded = false }
                    .zIndex(10f)
            )
        }

        Box(
            modifier = Modifier
                .padding(bottom = 80.dp, end = 20.dp)
                .zIndex(20f),
            contentAlignment = Alignment.Center
        ) {
            // Render Radial Circular Buttons
            if (animationProgress > 0.01f) {
                val totalItems = items.size
                // Arrange buttons in an arc/circle
                val startAngle = -PI * 0.95
                val angleStep = (PI * 0.9) / (totalItems - 1).coerceAtLeast(1)

                items.forEachIndexed { index, item ->
                    val angle = startAngle + index * angleStep
                    val offsetX = (menuRadius.value * cos(angle) * animationProgress).dp
                    val offsetY = (menuRadius.value * sin(angle) * animationProgress).dp

                    Box(
                        modifier = Modifier
                            .offset(x = offsetX, y = offsetY)
                            .scale(animationProgress.coerceIn(0.1f, 1f))
                            .clip(CircleShape)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        SurfaceBg,
                                        CardBg.copy(alpha = 0.95f)
                                    )
                                )
                            )
                            .border(
                                width = 1.5.dp,
                                brush = Brush.linearGradient(
                                    colors = listOf(item.color, item.color.copy(alpha = 0.3f))
                                ),
                                shape = CircleShape
                            )
                            .clickable {
                                isExpanded = false
                                onItemSelected(item.id)
                            }
                            .padding(12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = item.icon,
                            contentDescription = item.title,
                            tint = item.color,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // Main Central Floating Action Trigger Button
            FloatingActionButton(
                onClick = { isExpanded = !isExpanded },
                containerColor = Color.Transparent,
                contentColor = Color.White,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 8.dp),
                modifier = Modifier
                    .size(58.dp)
                    .clip(CircleShape)
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                CyberGold,
                                CyberCyan
                            )
                        )
                    )
                    .border(
                        width = 2.dp,
                        color = Color.White.copy(alpha = 0.6f),
                        shape = CircleShape
                    )
            ) {
                Icon(
                    imageVector = Icons.Default.Add,
                    contentDescription = "Quick Tools Menu",
                    tint = Color.Black,
                    modifier = Modifier
                        .size(28.dp)
                        .rotate(fabRotation)
                )
            }
        }
    }
}
