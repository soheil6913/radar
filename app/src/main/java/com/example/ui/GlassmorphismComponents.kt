package com.example.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Info
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.CardBg
import com.example.ui.theme.CyberCyan
import com.example.ui.theme.CyberGold
import com.example.ui.theme.CyberRed
import com.example.ui.theme.DarkBg
import com.example.ui.theme.GrayText
import com.example.ui.theme.SurfaceBg

/**
 * Modern Glassmorphic Container Card with 20-24dp rounded corners, soft shadows,
 * subtle cyber gradient borders, and frosted translucent background.
 */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    cornerRadius: Dp = 22.dp,
    borderColor: Color = CyberGold.copy(alpha = 0.25f),
    containerColor: Color = SurfaceBg.copy(alpha = 0.82f),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    val clickableModifier = if (onClick != null) {
        Modifier.clickable { onClick() }
    } else Modifier

    Column(
        modifier = modifier
            .clip(RoundedCornerShape(cornerRadius))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        containerColor,
                        CardBg.copy(alpha = 0.65f)
                    )
                )
            )
            .border(
                width = 1.2.dp,
                brush = Brush.linearGradient(
                    colors = listOf(
                        borderColor,
                        borderColor.copy(alpha = 0.08f)
                    )
                ),
                shape = RoundedCornerShape(cornerRadius)
            )
            .then(clickableModifier)
            .padding(16.dp),
        content = content
    )
}

/**
 * Modern Glassmorphic Signal Legend with animated spectrum gradient, glowing target indicator,
 * and collapsible detailed geophysical breakdown.
 */
@Composable
fun ModernSignalLegend(
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(false) }

    val infiniteTransition = rememberInfiniteTransition(label = "LegendGlow")
    val glowPulse by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "GlowPulse"
    )

    GlassCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 20.dp,
        borderColor = CyberGold.copy(alpha = 0.3f)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { isExpanded = !isExpanded },
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(10.dp)
                        .clip(CircleShape)
                        .background(CyberGold.copy(alpha = glowPulse))
                )
                Text(
                    text = "راهنمای طیف سیگنال و آنومالی (Signal Spectrum Legend)",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Icon(
                imageVector = if (isExpanded) Icons.Default.ExpandMore else Icons.Default.ChevronRight,
                contentDescription = "Toggle Legend",
                tint = CyberGold,
                modifier = Modifier.size(20.dp)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Animated Color Spectrum Bar
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(14.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(
                    Brush.horizontalGradient(
                        colors = listOf(
                            Color(0xFF003366), // Dark Blue (Deep Cavity)
                            Color(0xFF0099FF), // Cyan (Void)
                            Color(0xFF00CC66), // Green (Neutral Soil)
                            Color(0xFFFFFF00), // Yellow (Mineral)
                            Color(0xFFFF9900), // Orange (Ferrous Iron)
                            Color(0xFFFF0033)  // Bright Red / Magenta (Non-Ferrous Gold/Bronze)
                        )
                    )
                )
                .border(0.8.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(8.dp))
        )

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text("حفره / اتاقک (-)", color = Color(0xFF60A5FA), fontSize = 9.sp, fontWeight = FontWeight.Bold)
            Text("خاک معمولی (0)", color = Color(0xFF4ADE80), fontSize = 9.sp, fontWeight = FontWeight.Medium)
            Text("معدنی (+)", color = Color(0xFFFACC15), fontSize = 9.sp, fontWeight = FontWeight.Medium)
            Text("فلز / طلا (++)", color = Color(0xFFEF5350), fontSize = 9.sp, fontWeight = FontWeight.Bold)
        }

        AnimatedVisibility(visible = isExpanded) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HorizontalDivider(color = Color.White.copy(alpha = 0.1f))

                LegendRowItem(
                    color = Color(0xFFFF0033),
                    title = "فلزات گرانبها / طلا و برنز (Non-Ferrous Gold)",
                    desc = "آنومالی با فرکانس بالای قطب مثبت و فاز مغناطیسی شتاب‌یافته"
                )
                LegendRowItem(
                    color = Color(0xFFFF9900),
                    title = "فلزات آهنی و مغناطیسی (Ferrous Iron)",
                    desc = "جذب فاز شدید با میدان بازگشتی قطب جنوب"
                )
                LegendRowItem(
                    color = Color(0xFF0099FF),
                    title = "فضای خالی / تونل و حفره (Void & Cavities)",
                    desc = "کاهش ناگهانی تراکم رسانایی و پتانسیل چگالی زمین"
                )
            }
        }
    }
}

@Composable
private fun LegendRowItem(
    color: Color,
    title: String,
    desc: String
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(12.dp)
                .clip(CircleShape)
                .background(color)
        )
        Column {
            Text(title, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
            Text(desc, color = GrayText, fontSize = 9.sp)
        }
    }
}

/**
 * Material 3 Low-Pass Noise Filter Slider with dynamic percentages,
 * colored progress track, and glowing thumb indicator.
 */
@Composable
fun ModernFilterSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    label: String,
    modifier: Modifier = Modifier
) {
    GlassCard(
        modifier = modifier.fillMaxWidth(),
        cornerRadius = 20.dp
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
                Icon(Icons.Default.FilterList, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(18.dp))
                Text(label, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            }

            Text(
                text = "${(value * 100).toInt()}%",
                color = CyberCyan,
                fontSize = 12.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(modifier = Modifier.height(8.dp))

        Slider(
            value = value,
            onValueChange = onValueChange,
            colors = SliderDefaults.colors(
                thumbColor = CyberGold,
                activeTrackColor = CyberCyan,
                inactiveTrackColor = CardBg
            ),
            modifier = Modifier.fillMaxWidth()
        )
    }
}

/**
 * Animated Material 3 Switch Toggle with scale dynamics,
 * subtle glowing ring, and smooth transitions.
 */
@Composable
fun ModernM3Switch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val scaleAnim by animateFloatAsState(
        targetValue = if (checked) 1.05f else 1.0f,
        animationSpec = spring(stiffness = Spring.StiffnessMedium),
        label = "SwitchScale"
    )

    Switch(
        checked = checked,
        onCheckedChange = onCheckedChange,
        colors = SwitchDefaults.colors(
            checkedThumbColor = CyberGold,
            checkedTrackColor = CyberGold.copy(alpha = 0.35f),
            uncheckedThumbColor = Color.Gray,
            uncheckedTrackColor = CardBg
        ),
        modifier = modifier.scale(scaleAnim)
    )
}
