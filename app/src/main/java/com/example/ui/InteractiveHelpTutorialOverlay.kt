package com.example.ui

import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.ui.theme.*
import com.example.hardware.FeedbackManager

data class TutorialStep(
    val stepIndex: Int,
    val titleFa: String,
    val titleEn: String,
    val subtitleFa: String,
    val subtitleEn: String,
    val icon: ImageVector,
    val accentColor: Color
)

@Composable
fun InteractiveHelpTutorialDialog(
    appLanguage: String = "fa",
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var currentStepIndex by remember { mutableStateOf(0) }

    val steps = listOf(
        TutorialStep(
            stepIndex = 0,
            titleFa = "ژست‌های حرکتی و کنترل ۳بعدی",
            titleEn = "3D Gestures & Touch Navigation",
            subtitleFa = "کنترل کامل زاویه دید، زوم و بازرسی اهداف زمین با حرکات لمسی انگشتان",
            subtitleEn = "Master 3D viewport rotation, pinch zoom, and target node probing.",
            icon = Icons.Default.TouchApp,
            accentColor = CyberCyan
        ),
        TutorialStep(
            stepIndex = 1,
            titleFa = "موتورهای رندر و زوایای دوربین",
            titleEn = "Render Engines & Camera Angles",
            subtitleFa = "سویچ بین شبکه سه‌بعدی، نقشه‌برداری توپوگرافی و دید دوبعدی سطح زمین",
            subtitleEn = "Seamlessly switch between 3D Mesh, Heatmap, and Topo contour plans.",
            icon = Icons.Default.Layers,
            accentColor = CyberGold
        ),
        TutorialStep(
            stepIndex = 2,
            titleFa = "بازپخش زنده اسکن و تحلیل عمق",
            titleEn = "Scan Playback & Target Depth Analysis",
            subtitleFa = "بازپخش مرحله‌به‌مرحله ثبت سیگنال با سرعت‌های مختلف و فیلتر لایه‌های خاک",
            subtitleEn = "Replay ground scans step-by-step with live target depth estimation.",
            icon = Icons.Default.PlayCircle,
            accentColor = Color(0xFF00E676)
        ),
        TutorialStep(
            stepIndex = 3,
            titleFa = "حالت آفتاب شدید و ابزارهای تاکتیکی",
            titleEn = "Sunlight Mode & Tactical Tools",
            subtitleFa = "خوانایی ۱۰۰٪ زیر نور مستقیم خورشید، منوی شعاعی FAB و هوش مصنوعی",
            subtitleEn = "High-visibility direct sunlight dark theme, Radial FAB, & AI analysis.",
            icon = Icons.Default.WbSunny,
            accentColor = Color(0xFFFF9100)
        )
    )

    val step = steps[currentStepIndex]
    val isRtl = appLanguage == "fa"

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            dismissOnBackPress = true,
            dismissOnClickOutside = false
        )
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(DarkBg.copy(alpha = 0.92f))
                .padding(16.dp)
                .testTag("help_tutorial_overlay"),
            contentAlignment = Alignment.Center
        ) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .wrapContentHeight()
                    .widthIn(max = 580.dp)
                    .border(BorderStroke(1.5.dp, step.accentColor), RoundedCornerShape(20.dp)),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = SurfaceBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 16.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    // Header Bar with Step Badge & Close Button
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(42.dp)
                                    .clip(CircleShape)
                                    .background(step.accentColor.copy(alpha = 0.2f))
                                    .border(1.dp, step.accentColor, CircleShape),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = step.icon,
                                    contentDescription = null,
                                    tint = step.accentColor,
                                    modifier = Modifier.size(24.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = if (isRtl) "راهنمای کار با رادار سه‌بعدی" else "3D Radar Interactive Guide",
                                    color = GrayText,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = if (isRtl) step.titleFa else step.titleEn,
                                    color = Color.White,
                                    fontSize = 17.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        IconButton(
                            onClick = {
                                FeedbackManager.playClick(context)
                                onDismiss()
                            },
                            modifier = Modifier.testTag("help_tutorial_close_button")
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = GrayText)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    Text(
                        text = if (isRtl) step.subtitleFa else step.subtitleEn,
                        color = GrayText,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Divider(color = CardBg)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Step Specific Interactive Content
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 260.dp, max = 340.dp)
                            .verticalScroll(rememberScrollState())
                    ) {
                        when (currentStepIndex) {
                            0 -> StepGesturesContent(isRtl)
                            1 -> StepRenderEnginesContent(isRtl)
                            2 -> StepPlaybackDepthContent(isRtl)
                            3 -> StepSunlightTacticalContent(isRtl)
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))
                    Divider(color = CardBg)
                    Spacer(modifier = Modifier.height(16.dp))

                    // Bottom Navigation Control Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Previous Button
                        if (currentStepIndex > 0) {
                            OutlinedButton(
                                onClick = {
                                    FeedbackManager.playClick(context)
                                    currentStepIndex -= 1
                                },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan),
                                border = BorderStroke(1.dp, CyberCyan),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.testTag("help_tutorial_prev_button")
                            ) {
                                Icon(
                                    imageVector = if (isRtl) Icons.Default.ArrowForward else Icons.Default.ArrowBack,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(if (isRtl) "قبلی" else "Back")
                            }
                        } else {
                            TextButton(
                                onClick = {
                                    FeedbackManager.playClick(context)
                                    onDismiss()
                                }
                            ) {
                                Text(if (isRtl) "رد شدن" else "Skip", color = GrayText)
                            }
                        }

                        // Step Indicator Dots
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.testTag("help_tutorial_step_indicator")
                        ) {
                            steps.forEachIndexed { idx, item ->
                                val active = idx == currentStepIndex
                                Box(
                                    modifier = Modifier
                                        .size(if (active) 24.dp else 8.dp, 8.dp)
                                        .clip(RoundedCornerShape(4.dp))
                                        .background(if (active) step.accentColor else CardBg)
                                )
                            }
                        }

                        // Next / Finish Button
                        Button(
                            onClick = {
                                FeedbackManager.playClick(context)
                                if (currentStepIndex < steps.size - 1) {
                                    currentStepIndex += 1
                                } else {
                                    onDismiss()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = step.accentColor),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.testTag("help_tutorial_next_button")
                        ) {
                            Text(
                                text = if (currentStepIndex == steps.size - 1) {
                                    if (isRtl) "متوجه شدم" else "Got It"
                                } else {
                                    if (isRtl) "بعدی" else "Next"
                                },
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                            if (currentStepIndex < steps.size - 1) {
                                Spacer(modifier = Modifier.width(6.dp))
                                Icon(
                                    imageVector = if (isRtl) Icons.Default.ArrowBack else Icons.Default.ArrowForward,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun StepGesturesContent(isRtl: Boolean) {
    var demoYaw by remember { mutableStateOf(45f) }
    var demoPitch by remember { mutableStateOf(30f) }
    var demoZoom by remember { mutableStateOf(1.0f) }

    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
        // Interactive Mini Demo Box
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .height(130.dp)
                .clip(RoundedCornerShape(14.dp))
                .border(BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)), RoundedCornerShape(14.dp))
                .pointerInput(Unit) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        demoYaw = (demoYaw + pan.x * 0.5f) % 360f
                        demoPitch = (demoPitch - pan.y * 0.5f).coerceIn(5f, 85f)
                        demoZoom = (demoZoom * zoom).coerceIn(0.6f, 2.5f)
                    }
                },
            colors = CardDefaults.cardColors(containerColor = Color(0xFF0B1019))
        ) {
            Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
                // Background Grid Lines Simulation
                Column(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.SpaceEvenly,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        text = if (isRtl) "👆 اینجا امتحان کنید: انگشت بکشید تا زاویه تغییر کند" else "👆 Interactive Demo: Drag here to test 3D orientation",
                        color = CyberCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CardBg
                        ) {
                            Text(
                                text = "Yaw: ${demoYaw.toInt()}°",
                                color = CyberGold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CardBg
                        ) {
                            Text(
                                text = "Pitch: ${demoPitch.toInt()}°",
                                color = CyberGold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = CardBg
                        ) {
                            Text(
                                text = "Zoom: ${String.format("%.1f", demoZoom)}x",
                                color = CyberGold,
                                fontSize = 11.sp,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Gesture guide items
        GestureItem(
            icon = Icons.Default.RotateRight,
            title = if (isRtl) "چرخش ۱ انگشتی (3D Rotation)" else "1-Finger Drag (Rotation)",
            desc = if (isRtl) "با کشیدن یک انگشت روی صفحه، مدل ۳بعدی زمین را در زاویه ۳۶۰ درجه بچرخانید." else "Drag one finger to rotate 360° around the ground terrain."
        )

        GestureItem(
            icon = Icons.Default.ZoomIn,
            title = if (isRtl) "زوم دو انگشتی (Pinch Zoom)" else "Pinch to Zoom",
            desc = if (isRtl) "با دو انگشت صفحه را باز یا بسته کنید تا روی آنومالی‌های عمقی زوم کنید." else "Pinch with two fingers to zoom in on deep target anomalies."
        )

        GestureItem(
            icon = Icons.Default.TouchApp,
            title = if (isRtl) "بازرسی دقیق نقطه (Node Probe)" else "Tap Node Inspection",
            desc = if (isRtl) "روی هر نقطه از شبکه اسکن بزنید تا مقدار دقیق سیگنال (LSB)، فاز و عمق تخمینی آن نمایش داده شود." else "Tap any grid node to open instant telemetry with exact depth and LSB values."
        )
    }
}

@Composable
private fun StepRenderEnginesContent(isRtl: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FeatureCard(
            title = if (isRtl) "شبکه سه‌بعدی متحرک (3D Mesh)" else "Interactive 3D Mesh",
            desc = if (isRtl) "رندر کامل پستی‌وبلندی‌ها با پالت‌های رنگی حرارتی، تفکیک فلز و حفره." else "Full 3D surface grid rendering with thermal, contrast, and color-coded depth visualization."
        )
        FeatureCard(
            title = if (isRtl) "نقشه‌برداری توپوگرافی (Heatmap Plan)" else "Topographical Heatmap Plan",
            desc = if (isRtl) "نمایش دوبعدی سطح زمین با خطوط هم‌تراز (Contour Lines) برای تحلیل سریع محدوده اسکن." else "2D contour and surface gradient overlay for instant field boundaries."
        )
        FeatureCard(
            title = if (isRtl) "میانبرهای زاویه دوربین (Camera Presets)" else "Preset Camera Viewports",
            desc = if (isRtl) "دکمه‌های سریع برای دید از بالا (Top 90°)، دید ایزومتریک (45°) و دید افقی." else "Quick camera triggers for Top-Down (90° North), Isometric 45°, and Side horizon views."
        )
    }
}

@Composable
private fun StepPlaybackDepthContent(isRtl: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FeatureCard(
            title = if (isRtl) "بازپخش زنده اسکن (Scan Playback)" else "Variable Speed Scan Playback",
            desc = if (isRtl) "مشاهده مجدد مراحل پیمایش زمین با سرعت‌های 0.5x, 1x, 2x, 4x, 8x جهت بررسی نحوه شکل‌گیری آنومالی." else "Replay saved scans step-by-step or automatically at speeds up to 8x to observe target growth."
        )
        FeatureCard(
            title = if (isRtl) "محاسبه هوشمند عمق هدف (Depth Estimator)" else "Smart Target Depth Estimation",
            desc = if (isRtl) "نمایش لحظه‌ای عمق تخمینی بر حسب متر و فوت همراه با لایه‌بندی جنس خاک." else "Real-time depth calculations in meters and feet synchronized with scan playback."
        )
        FeatureCard(
            title = if (isRtl) "فیلتر لایه‌های خاک (Soil Stratification)" else "Soil Layer Stratification",
            desc = if (isRtl) "جداسازی سیگنال‌های سطح (0-3.5m)، زیرسطح (3.5-8m)، عمق زیاد (8-14m) و سنگ بستر." else "Isolate and inspect surface, subsurface, deep, and bedrock soil layers."
        )
    }
}

@Composable
private fun StepSunlightTacticalContent(isRtl: Boolean) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        FeatureCard(
            title = if (isRtl) "حالت کنتراست بالا برای آفتاب شدید (Sunlight Mode)" else "Direct Sunlight High-Contrast Mode",
            desc = if (isRtl) "طراحی شده ویژه کاوش در بیابان و آفتاب مستقیم با پس‌زمینه مشکی خالص و فونت‌های شب‌نما." else "Ultra-high contrast color palette & bold typography optimized for outdoor direct sunlight."
        )
        FeatureCard(
            title = if (isRtl) "منوی شناور تاکتیکی (Radial FAB Menu)" else "Floating Radial FAB Menu",
            desc = if (isRtl) "دسترسی سریع با یک لمس به تنظیمات، فیلترها، کالیبراسیون سنسور و خروجی فایل." else "One-touch radial menu for instant access to calibration, filters, and export."
        )
        FeatureCard(
            title = if (isRtl) "دستیار هوش مصنوعی ژئوفیزیک (AI Analyst)" else "AI Geophysical Anomaly Analyst",
            desc = if (isRtl) "تشخیص هوشمند اهداف ارزشمند، ساختارهای فلزی، تونل‌ها و فیلتر خطاهای خاک." else "Automatic AI anomaly scanner for classifying metallic structures, caves, and mineral noise."
        )
    }
}

@Composable
private fun GestureItem(icon: ImageVector, title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg.copy(alpha = 0.6f))
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(icon, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(24.dp))
        Spacer(modifier = Modifier.width(12.dp))
        Column {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(desc, color = GrayText, fontSize = 11.sp, lineHeight = 15.sp)
        }
    }
}

@Composable
private fun FeatureCard(title: String, desc: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(CardBg.copy(alpha = 0.6f))
            .padding(12.dp),
        verticalAlignment = Alignment.Top
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = CyberGold, modifier = Modifier.size(18.dp).padding(top = 2.dp))
        Spacer(modifier = Modifier.width(10.dp))
        Column {
            Text(title, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
            Text(desc, color = GrayText, fontSize = 11.sp, lineHeight = 16.sp)
        }
    }
}
