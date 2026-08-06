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
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.ui.theme.*
import kotlin.math.abs
import kotlin.math.max

/**
 * Visual legend component mapping signal strength values (LSB / ADC) to color gradients,
 * distinctively highlighting cavity/void vs. metal/gold targets in 3D ground scans.
 */
@Composable
fun SignalGradientLegendCard(
    maxAbsSignal: Float = 500f,
    selectedSignalValue: Float? = null,
    colorPalette: String = "MetalCavity",
    isExpandedDefault: Boolean = false,
    modifier: Modifier = Modifier
) {
    var isExpanded by remember { mutableStateOf(isExpandedDefault) }
    
    val safeMaxSignal = max(abs(maxAbsSignal), 100f)
    
    // Calculate classification and active pointer position if a node/point is selected
    val selectedNorm = selectedSignalValue?.let { (it / safeMaxSignal).coerceIn(-1f, 1f) }
    
    // Dynamic animated position for pointer needle
    val targetPointerPos = selectedNorm?.let { (it + 1f) / 2f } ?: 0.5f
    val animatedPointerPos by animateFloatAsState(
        targetValue = targetPointerPos,
        animationSpec = tween(durationMillis = 400, easing = FastOutSlowInEasing),
        label = "PointerPosAnimation"
    )

    Card(
        colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.85f)),
        shape = RoundedCornerShape(14.dp),
        border = androidx.compose.foundation.BorderStroke(1.dp, CyberCyan.copy(alpha = 0.35f)),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .padding(12.dp)
                .animateContentSize()
        ) {
            // Header Row with Title & Toggle Button
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        Icons.Default.Palette,
                        contentDescription = "Signal Legend",
                        tint = CyberGold,
                        modifier = Modifier.size(16.dp)
                    )
                    Text(
                        text = "راهنمای طیف رنگی سنسور (Signal Legend)",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    // Palette badge
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(CardBg)
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = colorPalette,
                            color = CyberCyan,
                            fontSize = 9.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Expand toggle icon
                    IconButton(
                        onClick = { isExpanded = !isExpanded },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = if (isExpanded) Icons.Default.KeyboardArrowUp else Icons.Default.KeyboardArrowDown,
                            contentDescription = "Toggle Expand",
                            tint = Color.White
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Active Point Selection Badge (if point is selected)
            if (selectedSignalValue != null) {
                val valInt = selectedSignalValue.toInt()
                val (categoryName, categoryColor, categoryIcon) = when {
                    selectedSignalValue > 150f -> Triple("طلا / فلز گرانبها (Noble Metal)", Color(0xFFFF1744), Icons.Default.Star)
                    selectedSignalValue > 30f -> Triple("فلز آهنی / معدنی (Mineral)", Color(0xFFFFD700), Icons.Default.Memory)
                    selectedSignalValue < -150f -> Triple("حفره عمیق / دالان (Deep Void)", Color(0xFFD500F9), Icons.Default.Layers)
                    selectedSignalValue < -30f -> Triple("حفره / تونل (Cavity)", Color(0xFF00E5FF), Icons.Default.Inbox)
                    else -> Triple("خاک معمولی / خنثی (Neutral Soil)", Color(0xFF2E7D32), Icons.Default.CheckCircle)
                }

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(8.dp))
                        .background(categoryColor.copy(alpha = 0.15f))
                        .border(1.dp, categoryColor.copy(alpha = 0.4f), RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Icon(categoryIcon, contentDescription = null, tint = categoryColor, modifier = Modifier.size(14.dp))
                        Text(
                            text = categoryName,
                            color = categoryColor,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = "شدت: ${if (valInt >= 0) "+$valInt" else "$valInt"} LSB",
                        color = Color.White,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))
            }

            // COLOR GRADIENT SPECTRUM BAR WITH ACTIVE NEEDLE POINTER
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(34.dp)
            ) {
                // Spectrum Bar Canvas
                Canvas(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(18.dp)
                        .align(Alignment.Center)
                        .clip(RoundedCornerShape(6.dp))
                        .border(1.dp, Color.White.copy(alpha = 0.2f), RoundedCornerShape(6.dp))
                ) {
                    val width = size.width
                    val height = size.height

                    // Generate multi-step color stop spectrum matching current palette
                    val colorStops = Array(11) { i ->
                        val norm = -1f + (i / 10f) * 2f
                        getSignalGradientColor(norm * safeMaxSignal, safeMaxSignal, colorPalette)
                    }

                    drawRect(
                        brush = Brush.horizontalGradient(colors = colorStops.toList()),
                        topLeft = Offset.Zero,
                        size = Size(width, height)
                    )

                    // Draw neutral zero line marker in center
                    val zeroX = width / 2f
                    drawLine(
                        color = Color.White.copy(alpha = 0.8f),
                        start = Offset(zeroX, 0f),
                        end = Offset(zeroX, height),
                        strokeWidth = 2f
                    )
                }

                // Active Pointer Needle (if point is selected)
                if (selectedSignalValue != null) {
                    Canvas(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        val width = size.width
                        val height = size.height
                        val pointerX = width * animatedPointerPos

                        // Top Pointer Triangle
                        val path = Path().apply {
                            moveTo(pointerX - 6f, 0f)
                            lineTo(pointerX + 6f, 0f)
                            lineTo(pointerX, 8f)
                            close()
                        }
                        drawPath(path = path, color = CyberGold)

                        // Vertical Indicator Line
                        drawLine(
                            color = CyberGold,
                            start = Offset(pointerX, 0f),
                            end = Offset(pointerX, height),
                            strokeWidth = 2f
                        )

                        // Bottom Pointer Triangle
                        val bottomPath = Path().apply {
                            moveTo(pointerX - 6f, height)
                            lineTo(pointerX + 6f, height)
                            lineTo(pointerX, height - 8f)
                            close()
                        }
                        drawPath(path = bottomPath, color = CyberGold)
                    }
                }
            }

            Spacer(modifier = Modifier.height(4.dp))

            // NUMERIC VALUE TICKS & LABELS
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Negative Dip (Cavity)
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        text = "🕳 حفره / دالان",
                        color = Color(0xFF00E5FF),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "-${safeMaxSignal.toInt()} LSB",
                        color = GrayText,
                        fontSize = 8.sp
                    )
                }

                // Mid Negative
                Text(
                    text = "-${(safeMaxSignal / 2).toInt()}",
                    color = GrayText,
                    fontSize = 8.sp
                )

                // Neutral Zero Ground Level
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "🌱 خاک خنثی",
                        color = Color(0xFF00E676),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "0 LSB",
                        color = Color.White,
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Mid Positive
                Text(
                    text = "+${(safeMaxSignal / 2).toInt()}",
                    color = GrayText,
                    fontSize = 8.sp
                )

                // Positive Peak (Noble Metal)
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "🏆 فلز / طلا",
                        color = Color(0xFFFF1744),
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "+${safeMaxSignal.toInt()} LSB",
                        color = GrayText,
                        fontSize = 8.sp
                    )
                }
            }

            // EXPANDED DETAILED ANALYSIS BREAKDOWN
            if (isExpanded) {
                Spacer(modifier = Modifier.height(10.dp))
                HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "تفکیک طیف سیگنال و هدایت الکترومغناطیسی:",
                    color = CyberGold,
                    fontSize = 10.sp,
                    fontWeight = FontWeight.Bold
                )

                Spacer(modifier = Modifier.height(6.dp))

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    LegendItemRow(
                        color = Color(0xFFFF1744),
                        rangeText = "> +150 LSB",
                        label = "طلا / فلزات باارزش غیرآهنی",
                        desc = "رسانایی و نفوذپذیری بالا (مگنتیت/طلا)"
                    )
                    LegendItemRow(
                        color = Color(0xFFFFD700),
                        rangeText = "+30 تا +150 LSB",
                        label = "فلزات گرانبها / مس و نقره",
                        desc = "پالس مثبت و امواج بازگشتی قوی"
                    )
                    LegendItemRow(
                        color = Color(0xFF2E7D32),
                        rangeText = "-30 تا +30 LSB",
                        label = "ماتریس خاک معمولی",
                        desc = "پاسخ مغناطیسی نرمال بستر زمین"
                    )
                    LegendItemRow(
                        color = Color(0xFF00E5FF),
                        rangeText = "-150 تا -30 LSB",
                        label = "حفره / اتاقک / چاه",
                        desc = "کاهش تراکم خاک و افت میدان مغناطیسی"
                    )
                    LegendItemRow(
                        color = Color(0xFFD500F9),
                        rangeText = "< -150 LSB",
                        label = "دالان عمیق / تونل زیرزمینی",
                        desc = "خلاء الکترومغناطیسی شدید و افت فاز"
                    )
                }
            }
        }
    }
}

@Composable
private fun LegendItemRow(
    color: Color,
    rangeText: String,
    label: String,
    desc: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(CardBg.copy(alpha = 0.5f), RoundedCornerShape(6.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Column {
                Text(text = label, color = Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                Text(text = desc, color = GrayText, fontSize = 7.5.sp)
            }
        }
        Text(
            text = rangeText,
            color = CyberCyan,
            fontSize = 8.5.sp,
            fontWeight = FontWeight.Bold
        )
    }
}
