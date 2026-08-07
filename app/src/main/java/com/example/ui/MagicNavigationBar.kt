package com.example.ui

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
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*

data class MagicNavItem(
    val id: String,
    val icon: ImageVector,
    val labelKey: String,
    val activeColor: Color
)

@Composable
fun MagicNavigationBar(
    currentTab: String,
    onTabSelected: (String) -> Unit,
    appLanguage: String,
    modifier: Modifier = Modifier
) {
    val navItems = remember {
        listOf(
            MagicNavItem("scan", Icons.Default.Map, "nav_scan", CyberGold),
            MagicNavItem("visualizer", Icons.Default.ViewInAr, "nav_visualizer", CyberCyan),
            MagicNavItem("tracker", Icons.Default.TrendingUp, "nav_live", CyberRed),
            MagicNavItem("history", Icons.Default.History, "nav_history", CyberGold),
            MagicNavItem("ai", Icons.Default.AutoAwesome, "nav_ai", CyberGold),
            MagicNavItem("settings", Icons.Default.Settings, "nav_settings", CyberCyan)
        )
    }

    val selectedIndex = navItems.indexOfFirst { it.id == currentTab }.coerceAtLeast(0)

    // Animated spring index for smooth horizontal sliding of indicator circle
    val animatedIndex by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "MagicNavIndicatorIndex"
    )

    var containerWidthPx by remember { mutableFloatStateOf(0f) }
    val density = LocalDensity.current

    val activeItem = navItems.getOrNull(selectedIndex) ?: navItems[0]

    Box(
        modifier = modifier
            .fillMaxWidth()
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(horizontal = 8.dp, vertical = 4.dp)
            .height(72.dp)
            .onGloballyPositioned { containerWidthPx = it.size.width.toFloat() }
    ) {
        // Main Navigation Container Bar
        Box(
            modifier = Modifier
                .fillMaxSize()
                .clip(RoundedCornerShape(24.dp))
                .background(
                    brush = Brush.verticalGradient(
                        colors = listOf(
                            SurfaceBg.copy(alpha = 0.95f),
                            CardBg.copy(alpha = 0.98f)
                        )
                    )
                )
                .border(
                    width = 1.dp,
                    brush = Brush.horizontalGradient(
                        colors = listOf(
                            activeItem.activeColor.copy(alpha = 0.4f),
                            Color.White.copy(alpha = 0.15f),
                            activeItem.activeColor.copy(alpha = 0.4f)
                        )
                    ),
                    shape = RoundedCornerShape(24.dp)
                )
        )

        // Magic Floating Indicator Circle (Slides horizontally across tabs)
        if (containerWidthPx > 0f) {
            val tabWidthPx = containerWidthPx / navItems.size
            val indicatorCenterX = (animatedIndex + 0.5f) * tabWidthPx
            val indicatorCenterXDp = with(density) { indicatorCenterX.toDp() }

            // Floating Circle Background & Notch Curve Glow
            Box(
                modifier = Modifier
                    .offset(x = indicatorCenterXDp - 26.dp, y = (-18).dp)
                    .size(52.dp)
                    .shadow(12.dp, CircleShape, spotColor = activeItem.activeColor, ambientColor = activeItem.activeColor)
                    .clip(CircleShape)
                    .background(
                        brush = Brush.radialGradient(
                            colors = listOf(
                                activeItem.activeColor,
                                activeItem.activeColor.copy(alpha = 0.85f),
                                CardBg
                            )
                        )
                    )
                    .border(
                        width = 2.dp,
                        brush = Brush.verticalGradient(
                            colors = listOf(Color.White, activeItem.activeColor)
                        ),
                        shape = CircleShape
                    )
            )
        }

        // Row of Navigation Items
        Row(
            modifier = Modifier.fillMaxSize(),
            horizontalArrangement = Arrangement.SpaceAround,
            verticalAlignment = Alignment.CenterVertically
        ) {
            navItems.forEachIndexed { index, item ->
                val isSelected = index == selectedIndex

                // Animate individual item icon vertical offset and scale
                val iconYOffset by animateDpAsState(
                    targetValue = if (isSelected) (-18).dp else 0.dp,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessLow
                    ),
                    label = "IconYOffset_$index"
                )

                val iconScale by animateFloatAsState(
                    targetValue = if (isSelected) 1.22f else 1.0f,
                    animationSpec = spring(
                        dampingRatio = Spring.DampingRatioMediumBouncy,
                        stiffness = Spring.StiffnessMedium
                    ),
                    label = "IconScale_$index"
                )

                val textAlpha by animateFloatAsState(
                    targetValue = if (isSelected) 1.0f else 0.6f,
                    animationSpec = tween(durationMillis = 200),
                    label = "TextAlpha_$index"
                )

                val textYOffset by animateDpAsState(
                    targetValue = if (isSelected) 10.dp else 0.dp,
                    animationSpec = tween(durationMillis = 200),
                    label = "TextYOffset_$index"
                )

                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .clickable(
                            interactionSource = remember { MutableInteractionSource() },
                            indication = null
                        ) {
                            onTabSelected(item.id)
                        },
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        // Icon with elevation & floating offset
                        Box(
                            modifier = Modifier
                                .offset(y = iconYOffset)
                                .graphicsLayer {
                                    scaleX = iconScale
                                    scaleY = iconScale
                                },
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = item.id,
                                tint = if (isSelected) Color.Black else GrayText,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(2.dp))

                        // Label
                        Text(
                            text = AppTranslations.getString(item.labelKey, appLanguage),
                            fontSize = 8.5.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                            color = if (isSelected) item.activeColor else GrayText.copy(alpha = textAlpha),
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            modifier = Modifier
                                .offset(y = textYOffset)
                                .graphicsLayer { alpha = textAlpha }
                        )
                    }
                }
            }
        }
    }
}
