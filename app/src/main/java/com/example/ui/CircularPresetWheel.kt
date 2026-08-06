package com.example.ui

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Star
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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CardBg
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGold
import com.example.ui.theme.DarkBg
import com.example.ui.theme.GrayText
import com.example.ui.theme.SurfaceBg
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

data class PresetWheelItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val color: Color
)

/**
 * Interactive Circular Preset Gallery Wheel Selector.
 * Renders items in a smooth curved rotating wheel layout with drag gesture support and active focus scale.
 */
@Composable
fun CircularPresetWheel(
    items: List<PresetWheelItem>,
    selectedId: String,
    onItemSelected: (PresetWheelItem) -> Unit,
    modifier: Modifier = Modifier
) {
    val selectedIndex = items.indexOfFirst { it.id == selectedId }.coerceAtLeast(0)
    var wheelAngleOffset by remember { mutableFloatStateOf(0f) }

    val animatedIndexOffset by animateFloatAsState(
        targetValue = selectedIndex.toFloat(),
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioLowBouncy,
            stiffness = Spring.StiffnessLow
        ),
        label = "WheelIndex"
    )

    Column(
        modifier = modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(SurfaceBg.copy(alpha = 0.85f))
            .border(1.dp, CyberGold.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.Star, contentDescription = null, tint = CyberGold, modifier = Modifier.size(20.dp))
                Text(
                    text = "چرخه انتخاب قالب و سناریوی اسکن",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "${selectedIndex + 1} از ${items.size}",
                color = CyberCyan,
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Curved Interactive Circular Carousel Wheel Container
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(140.dp)
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { _, dragAmount ->
                        if (dragAmount < -15 && selectedIndex < items.size - 1) {
                            onItemSelected(items[selectedIndex + 1])
                        } else if (dragAmount > 15 && selectedIndex > 0) {
                            onItemSelected(items[selectedIndex - 1])
                        }
                    }
                },
            contentAlignment = Alignment.Center
        ) {
            val totalItems = items.size
            val radiusPx = 130f

            items.forEachIndexed { index, item ->
                val deltaIndex = index - animatedIndexOffset
                val angleRad = deltaIndex * (PI / 3.8)
                val xOffsetDp = (radiusPx * sin(angleRad)).dp
                val zScale = (1.0f - (kotlin.math.abs(deltaIndex) * 0.25f)).coerceIn(0.6f, 1.15f)
                val alphaVal = (1.0f - (kotlin.math.abs(deltaIndex) * 0.35f)).coerceIn(0.2f, 1.0f)

                val isSelected = index == selectedIndex

                Box(
                    modifier = Modifier
                        .offset(x = xOffsetDp)
                        .scale(zScale)
                        .clip(RoundedCornerShape(16.dp))
                        .background(
                            if (isSelected) {
                                Brush.linearGradient(
                                    colors = listOf(
                                        CyberGold.copy(alpha = 0.25f),
                                        CyberCyan.copy(alpha = 0.2f)
                                    )
                                )
                            } else {
                                Brush.linearGradient(
                                    colors = listOf(CardBg, DarkBg.copy(alpha = 0.8f))
                                )
                            }
                        )
                        .border(
                            width = if (isSelected) 2.dp else 1.dp,
                            color = if (isSelected) CyberGold else Color.White.copy(alpha = 0.15f),
                            shape = RoundedCornerShape(16.dp)
                        )
                        .clickable { onItemSelected(item) }
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(item.color.copy(alpha = 0.2f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = item.icon,
                                contentDescription = null,
                                tint = item.color,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(6.dp))
                        Text(
                            text = item.title,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium
                        )
                        Text(
                            text = item.subtitle,
                            color = GrayText,
                            fontSize = 9.sp,
                            maxLines = 1
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(8.dp))

        // Navigation Stepper Arrows
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = {
                    if (selectedIndex > 0) onItemSelected(items[selectedIndex - 1])
                },
                enabled = selectedIndex > 0
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronRight,
                    contentDescription = "Previous",
                    tint = if (selectedIndex > 0) CyberGold else Color.Gray
                )
            }

            Text(
                text = items[selectedIndex].title + " — " + items[selectedIndex].subtitle,
                color = CyberGold,
                fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center
            )

            IconButton(
                onClick = {
                    if (selectedIndex < items.size - 1) onItemSelected(items[selectedIndex + 1])
                },
                enabled = selectedIndex < items.size - 1
            ) {
                Icon(
                    imageVector = Icons.Default.ChevronLeft,
                    contentDescription = "Next",
                    tint = if (selectedIndex < items.size - 1) CyberGold else Color.Gray
                )
            }
        }
    }
}
