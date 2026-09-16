package com.example.ui

import android.app.Activity
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.zIndex
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.withFrameMillis
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.text.TextStyle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.ScanRecord
import com.example.hardware.ConnectionMode
import com.example.hardware.SensorType
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*
import kotlin.math.abs

@Composable
fun AppDashboard(
    viewModel: VisualizerViewModel,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var currentTab by remember { mutableStateOf("scan") }
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()

    // Navigation state
    val isScanActive by viewModel.isScanActive.collectAsStateWithLifecycle()
    val viewedScan by viewModel.viewedScan.collectAsStateWithLifecycle()
    val showCalibrationWizard by viewModel.showCalibrationWizard.collectAsStateWithLifecycle()
    val showHelpTutorial by viewModel.showHelpTutorial.collectAsStateWithLifecycle()

    if (showCalibrationWizard) {
        CalibrationWizardDialog(
            viewModel = viewModel,
            onDismiss = { viewModel.closeCalibrationWizard() },
            onCalibrationComplete = {
                // Keep connected / calibrated
            }
        )
    }

    if (showHelpTutorial) {
        InteractiveHelpTutorialDialog(
            appLanguage = appLanguage,
            onDismiss = { viewModel.closeHelpTutorial() }
        )
    }

    var showSplash by remember { mutableStateOf(true) }

    val layoutDirection = if (appLanguage == "fa") LayoutDirection.Rtl else LayoutDirection.Ltr

    CompositionLocalProvider(LocalLayoutDirection provides layoutDirection) {
        if (showSplash) {
            HexagonParticleSplashScreen(
                onSplashFinished = { showSplash = false }
            )
        } else {
            Scaffold(
        bottomBar = {
            MagicNavigationBar(
                currentTab = currentTab,
                onTabSelected = { currentTab = it },
                appLanguage = appLanguage
            )
        },
        containerColor = DarkBg,
        modifier = modifier
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            AmbientParticleBackground()

            val sessionRestoredFromRoom by viewModel.sessionRestoredFromRoom.collectAsStateWithLifecycle()

            when (currentTab) {
                "scan" -> ScanScreen(viewModel = viewModel, onNavigateToAi = { currentTab = "ai" })
                "visualizer" -> VisualizerScreen(viewModel = viewModel, onNavigateToAi = { currentTab = "ai" })
                "tracker" -> TrackerScreen(viewModel = viewModel)
                "history" -> HistoryScreen(viewModel = viewModel, onLoadScan = {
                    currentTab = "visualizer"
                })
                "ai" -> AiAnalysisScreen(viewModel = viewModel)
                "settings" -> SettingsScreen(viewModel = viewModel)
            }

            if (sessionRestoredFromRoom) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceBg),
                    shape = RoundedCornerShape(12.dp),
                    border = BorderStroke(1.dp, CyberGold),
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                        .align(Alignment.TopCenter)
                        .zIndex(99f)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Default.Storage, contentDescription = null, tint = CyberGold)
                            Column {
                                Text(
                                    text = "بازآوری نشست از دیتابیس (Room Auto-Restored)",
                                    color = Color.White,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = "اطلاعات اسکن و مدل سه‌بعدی قبلی شما به‌طور خودکار از Room بازیابی شد.",
                                    color = GrayText,
                                    fontSize = 10.sp
                                )
                            }
                        }
                        IconButton(onClick = { viewModel.dismissSessionRestoredNotice() }) {
                            Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
                        }
                    }
                }
            }

            // Floating Circular Action Menu Overlay
            val radialItems = remember {
                listOf(
                    RadialMenuItem("scan", "اسکن زمین", Icons.Default.Map, CyberGold),
                    RadialMenuItem("visualizer", "سه‌بعدی 3D", Icons.Default.ViewInAr, CyberCyan),
                    RadialMenuItem("tracker", "پایش زنده", Icons.Default.TrendingUp, CyberRed),
                    RadialMenuItem("history", "تاریخچه", Icons.Default.History, CyberGold),
                    RadialMenuItem("ai", "هوش مصنوعی", Icons.Default.AutoAwesome, CyberGold),
                    RadialMenuItem("settings", "تنظیمات", Icons.Default.Settings, CyberCyan)
                )
            }

            RadialFabMenu(
                items = radialItems,
                onItemSelected = { selectedTab ->
                    currentTab = selectedTab
                }
            )
        }
    }
}
}

}

// ==========================================
// TAB 1: GROUND SCAN SCREEN
// ==========================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ScanScreen(viewModel: VisualizerViewModel, onNavigateToAi: () -> Unit = {}) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    val gridWidth by viewModel.gridWidth.collectAsStateWithLifecycle()
    val gridLength by viewModel.gridLength.collectAsStateWithLifecycle()
    val soilType by viewModel.soilType.collectAsStateWithLifecycle()
    val scanPattern by viewModel.scanPattern.collectAsStateWithLifecycle()

    val isScanActive by viewModel.isScanActive.collectAsStateWithLifecycle()
    val currentCol by viewModel.currentCol.collectAsStateWithLifecycle()
    val currentRow by viewModel.currentRow.collectAsStateWithLifecycle()
    val activeScanData by viewModel.activeScanData.collectAsStateWithLifecycle()

    val connectionState by viewModel.sensorManager.connectionState.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.sensorManager.tvStatus.collectAsStateWithLifecycle()
    val batteryPercentage by viewModel.sensorManager.batteryPercentage.collectAsStateWithLifecycle()
    val adcValue by viewModel.sensorManager.adcValue.collectAsStateWithLifecycle()
    val phaseShift by viewModel.sensorManager.phaseShift.collectAsStateWithLifecycle()

    val activeSensorType by viewModel.sensorManager.sensorType.collectAsStateWithLifecycle()
    val activeBaudRate by viewModel.sensorManager.baudRate.collectAsStateWithLifecycle()
    val compassHeading by viewModel.sensorManager.compassHeading.collectAsStateWithLifecycle()
    val adxlPitch by viewModel.sensorManager.adxlPitch.collectAsStateWithLifecycle()
    val adxlRoll by viewModel.sensorManager.adxlRoll.collectAsStateWithLifecycle()
    val adxlGForce by viewModel.sensorManager.adxlGForce.collectAsStateWithLifecycle()
    val adxlX by viewModel.sensorManager.adxlX.collectAsStateWithLifecycle()
    val adxlY by viewModel.sensorManager.adxlY.collectAsStateWithLifecycle()
    val adxlZ by viewModel.sensorManager.adxlZ.collectAsStateWithLifecycle()
    val adxlStabilityScore by viewModel.sensorManager.adxlStabilityScore.collectAsStateWithLifecycle()
    val adxlVibrationRms by viewModel.sensorManager.adxlVibrationRms.collectAsStateWithLifecycle()
    val adxlStatusText by viewModel.sensorManager.adxlStatusText.collectAsStateWithLifecycle()
    val adxlIsCalibrated by viewModel.sensorManager.adxlIsCalibrated.collectAsStateWithLifecycle()
    val isLiveLogging by viewModel.isLiveLogging.collectAsStateWithLifecycle()

    val isAdxlLoggingActive by viewModel.isAdxlLoggingActive.collectAsStateWithLifecycle()
    val adxlLoggedCount by viewModel.adxlLoggedCount.collectAsStateWithLifecycle()
    val adxlReadingCountFromRoom by viewModel.adxlReadingCountFromRoom.collectAsStateWithLifecycle(0)

    var autoPulseEnabled by remember { mutableStateOf(false) }
    var selectedConfigTab by remember { mutableIntStateOf(0) }

    // Polling Coroutine for Automatic Impulses
    LaunchedEffect(isScanActive, autoPulseEnabled) {
        if (isScanActive && autoPulseEnabled) {
            while (true) {
                delay(1600)
                viewModel.recordCurrentStep()
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // App Header
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Text(
                    text = "OKM 3D VISUALIZER",
                    color = CyberGold,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
                Text(
                    text = "اسکن زمین و رادار عمق‌سنج",
                    color = GrayText,
                    fontSize = 11.sp
                )
            }
            
            // Connection Status Widget
            Card(
                onClick = { viewModel.sensorManager.autoConnect() },
                colors = CardDefaults.cardColors(containerColor = CardBg),
                shape = RoundedCornerShape(12.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val indicatorColor = when (connectionState) {
                        ConnectionMode.DISCONNECTED -> Color.Red
                        ConnectionMode.CONNECTING_USB, ConnectionMode.CONNECTING_BT -> Color.Yellow
                        ConnectionMode.USB -> Color.Green
                        ConnectionMode.BLUETOOTH -> CyberCyan
                        ConnectionMode.SIMULATOR -> CyberGold
                    }
                    Box(
                        modifier = Modifier
                            .size(10.dp)
                            .clip(CircleShape)
                            .background(indicatorColor)
                    )
                    Text(
                        text = connectionStatus + (batteryPercentage?.let { " | 🔋 $it%" } ?: ""),
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Hardware Monitor & Battery Status Component
        HardwareStatusPanel(
            viewModel = viewModel,
            modifier = Modifier.padding(bottom = 8.dp)
        )

        if (!isScanActive) {
            // Interactive Circular Preset Gallery Wheel
            val scanPresetItems = remember {
                listOf(
                    PresetWheelItem("gold_deep", "دفینه و طلا (Deep Gold)", "شبکه ۱۰×۱۰ - خاک معدنی", Icons.Default.FilterHdr, CyberGold),
                    PresetWheelItem("tunnel", "حفره و تونل (Tunnel / Void)", "شبکه ۸×۱۲ - زیگزاگ", Icons.Default.Explore, CyberCyan),
                    PresetWheelItem("fast_recon", "شناسایی سریع (Fast Recon)", "شبکه ۵×۵ - موازی", Icons.Default.FlashOn, CyberRed),
                    PresetWheelItem("ancient_ruin", "سازه‌های باستانی (Ancient Ruins)", "شبکه ۱۵×۱۵ - سنگ و صخره", Icons.Default.AccountBalance, Color(0xFF00FFCC))
                )
            }
            var selectedPresetId by remember { mutableStateOf("gold_deep") }

            CircularPresetWheel(
                items = scanPresetItems,
                selectedId = selectedPresetId,
                onItemSelected = { preset ->
                    selectedPresetId = preset.id
                    when (preset.id) {
                        "gold_deep" -> {
                            viewModel.updateGridWidth(10)
                            viewModel.updateGridLength(10)
                            viewModel.updateSoilType("خاک معدنی (Mineral)")
                            viewModel.updateScanPattern("زیگزاگ (Zig-Zag)")
                        }
                        "tunnel" -> {
                            viewModel.updateGridWidth(8)
                            viewModel.updateGridLength(12)
                            viewModel.updateSoilType("خاک کشاورزی (Soil)")
                            viewModel.updateScanPattern("زیگزاگ (Zig-Zag)")
                        }
                        "fast_recon" -> {
                            viewModel.updateGridWidth(5)
                            viewModel.updateGridLength(5)
                            viewModel.updateSoilType("خاک کشاورزی (Soil)")
                            viewModel.updateScanPattern("موازی (Parallel)")
                        }
                        "ancient_ruin" -> {
                            viewModel.updateGridWidth(15)
                            viewModel.updateGridLength(15)
                            viewModel.updateSoilType("سنگ و صخره (Hard Rock)")
                            viewModel.updateScanPattern("زیگزاگ (Zig-Zag)")
                        }
                    }
                },
                modifier = Modifier.padding(bottom = 12.dp)
            )

            // Calibration Wizard Quick Launch Banner
            Card(
                onClick = { viewModel.openCalibrationWizard() },
                colors = CardDefaults.cardColors(containerColor = SurfaceBg),
                border = BorderStroke(1.dp, CyberGold.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
                    .testTag("launch_calibration_wizard_card")
            ) {
                Row(
                    modifier = Modifier.padding(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(CyberGold.copy(alpha = 0.15f))
                            .border(BorderStroke(1.5.dp, CyberGold), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            tint = CyberGold,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "راهنمای ۵ مرحله‌ای کالیبراسیون رادار",
                            color = CyberGold,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "کالیبراسیون نقطه‌صفر خاک، حذف نویز و تنظیم قطب‌نما",
                            color = GrayText,
                            fontSize = 10.sp
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Start Wizard",
                        tint = CyberGold
                    )
                }
            }

            // Segmented Configuration Tabs
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceBg),
                shape = RoundedCornerShape(16.dp),
                border = BorderStroke(1.dp, CardBg),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Segmented Control Switcher
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(CardBg, RoundedCornerShape(12.dp))
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Surface(
                            onClick = { selectedConfigTab = 0 },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selectedConfigTab == 0) CyberGold.copy(alpha = 0.2f) else Color.Transparent,
                            border = BorderStroke(1.dp, if (selectedConfigTab == 0) CyberGold else Color.Transparent),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.GridOn,
                                        contentDescription = null,
                                        tint = if (selectedConfigTab == 0) CyberGold else GrayText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "ابعاد و زمینه اسکن",
                                        color = if (selectedConfigTab == 0) CyberGold else GrayText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }

                        Surface(
                            onClick = { selectedConfigTab = 1 },
                            shape = RoundedCornerShape(10.dp),
                            color = if (selectedConfigTab == 1) CyberCyan.copy(alpha = 0.2f) else Color.Transparent,
                            border = BorderStroke(1.dp, if (selectedConfigTab == 1) CyberCyan else Color.Transparent),
                            modifier = Modifier.weight(1f)
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Sensors,
                                        contentDescription = null,
                                        tint = if (selectedConfigTab == 1) CyberCyan else GrayText,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = "سخت‌افزار و سنسور",
                                        color = if (selectedConfigTab == 1) CyberCyan else GrayText,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }

                    if (selectedConfigTab == 0) {
                        // TAB 0: Grid Dimensions, Soil Type, Scan Pattern
                        // Grid size configuration
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            // Width (Columns)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("تعداد پالس در خط (عرض)", color = CyberGold, fontSize = 12.sp)
                                Text("Grid Width (Cols)", color = GrayText, fontSize = 10.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(CardBg, RoundedCornerShape(8.dp))
                                        .padding(4.dp)
                                ) {
                                    IconButton(onClick = { viewModel.updateGridWidth(gridWidth - 1) }) {
                                        Icon(Icons.Default.Remove, contentDescription = "Minus", tint = Color.White)
                                    }
                                    Text("$gridWidth", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    IconButton(onClick = { viewModel.updateGridWidth(gridWidth + 1) }) {
                                        Icon(Icons.Default.Add, contentDescription = "Plus", tint = Color.White)
                                    }
                                }
                            }
                            
                            // Length (Lines/Rows)
                            Column(modifier = Modifier.weight(1f)) {
                                Text("تعداد خطوط اسکن (طول)", color = CyberGold, fontSize = 12.sp)
                                Text("Grid Length (Rows)", color = GrayText, fontSize = 10.sp)
                                Spacer(modifier = Modifier.height(6.dp))
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(CardBg, RoundedCornerShape(8.dp))
                                        .padding(4.dp)
                                ) {
                                    IconButton(onClick = { viewModel.updateGridLength(gridLength - 1) }) {
                                        Icon(Icons.Default.Remove, contentDescription = "Minus", tint = Color.White)
                                    }
                                    Text("$gridLength", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                                    IconButton(onClick = { viewModel.updateGridLength(gridLength + 1) }) {
                                        Icon(Icons.Default.Add, contentDescription = "Plus", tint = Color.White)
                                    }
                                }
                            }
                        }

                        // Soil Selection
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("نوع خاک هدف (Soil Type)", color = CyberGold, fontSize = 12.sp)
                            Text("بر روی سرعت امواج عمق‌سنج اثرگذار است", color = GrayText, fontSize = 10.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            val soils = listOf("خاک کشاورزی (Soil)", "خاک معدنی (Mineral)", "سنگ و صخره (Hard Rock)", "ماسه خیس (Wet Sand)")
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                soils.forEach { s ->
                                    val selected = soilType == s
                                    FilterChip(
                                        selected = selected,
                                        onClick = { viewModel.updateSoilType(s) },
                                        label = { Text(s, fontSize = 11.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = CyberGold.copy(alpha = 0.2f),
                                            selectedLabelColor = CyberGold,
                                            selectedLeadingIconColor = CyberGold,
                                            containerColor = CardBg,
                                            labelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }

                        // Scan Mode Selection
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("طرح حرکت اسکن (Scan Pattern)", color = CyberGold, fontSize = 12.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                                Card(
                                    onClick = { viewModel.updateScanPattern("موازی (Parallel)") },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (scanPattern.contains("Parallel") || scanPattern.contains("موازی")) {
                                            CyberCyan.copy(alpha = 0.15f)
                                        } else CardBg
                                    ),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (scanPattern.contains("Parallel") || scanPattern.contains("موازی")) CyberCyan else Color.Transparent
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.FormatAlignJustify, contentDescription = null, tint = CyberCyan)
                                        Text("موازی (Parallel)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text("شروع خطوط همیشه از چپ", color = GrayText, fontSize = 9.sp)
                                    }
                                }

                                Card(
                                    onClick = { viewModel.updateScanPattern("زیگزاگ (Zig-Zag)") },
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (scanPattern.contains("Zig-Zag") || scanPattern.contains("زیگزاگ")) {
                                            CyberCyan.copy(alpha = 0.15f)
                                        } else CardBg
                                    ),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = if (scanPattern.contains("Zig-Zag") || scanPattern.contains("زیگزاگ")) CyberCyan else Color.Transparent
                                    ),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Column(modifier = Modifier.padding(12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                        Icon(Icons.Default.Repeat, contentDescription = null, tint = CyberCyan)
                                        Text("زیگزاگ (Zig-Zag)", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                        Text("رفت و برگشتی متناوب", color = GrayText, fontSize = 9.sp)
                                    }
                                }
                            }
                        }
                    } else {
                        // TAB 1: Sensor & Connection Configuration
                        // 1. Sensor Profile Selection
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("نوع سنسور مگنتومتر (Sensor Type)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            FlowRow(
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                SensorType.values().forEach { type ->
                                    val label = when (type) {
                                        SensorType.GOLD_RADAR_X20 -> "استاندارد X20"
                                        SensorType.FMG3 -> "FMG3 فلاکس‌گیت"
                                        SensorType.FLC100 -> "FLC100 فلاکس‌گیت"
                                        SensorType.HMC5883L -> "قطب‌نما HMC5883L"
                                        SensorType.QMC5883L -> "مگنتومتر QMC5883L"
                                        SensorType.ADXL345 -> "شتاب‌سنج ADXL345 📐"
                                        SensorType.PHONE_INTERNAL -> "سنسور داخلی گوشی 📱"
                                    }
                                    val isSelected = activeSensorType == type
                                    FilterChip(
                                        selected = isSelected,
                                        onClick = { viewModel.sensorManager.setSensorType(type) },
                                        label = { Text(label, fontSize = 10.sp) },
                                        colors = FilterChipDefaults.filterChipColors(
                                            selectedContainerColor = CyberGold.copy(alpha = 0.2f),
                                            selectedLabelColor = CyberGold,
                                            containerColor = CardBg,
                                            labelColor = Color.White
                                        )
                                    )
                                }
                            }
                        }

                        // 2. Baud Rate Selection
                        Column(modifier = Modifier.fillMaxWidth()) {
                            Text("سرعت اتصال درایور CH340 (Baud Rate)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(modifier = Modifier.height(6.dp))
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf(9600, 115200).forEach { rate ->
                                    val isSelected = activeBaudRate == rate
                                    Card(
                                        onClick = { viewModel.sensorManager.setBaudRate(rate) },
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) CyberCyan.copy(alpha = 0.15f) else CardBg
                                        ),
                                        border = BorderStroke(
                                            width = 1.dp,
                                            color = if (isSelected) CyberCyan else Color.Transparent
                                        ),
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Box(modifier = Modifier.fillMaxWidth().padding(10.dp), contentAlignment = Alignment.Center) {
                                            Text("$rate bps", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }

                        // 3. Dynamic Digital Compass Section
                        if (activeSensorType == SensorType.ADXL345) {
                            // ADXL345 Dedicated 3-Axis Accelerometer & Tilt Gauge Card with Background Room Logger
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CardBg, RoundedCornerShape(10.dp))
                                    .padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.ScreenRotation,
                                        contentDescription = null,
                                        tint = CyberGold,
                                        modifier = Modifier.size(32.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("درايور سخت‌افزاری شتاب‌سنج ADXL345", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text("شیب X/Y: P:${String.format("%.1f", adxlPitch)}° R:${String.format("%.1f", adxlRoll)}° | ثبات: $adxlStabilityScore%", color = GrayText, fontSize = 9.sp)
                                        Text("شتاب ۳ جهته: X:${String.format("%.1f", adxlX)} Y:${String.format("%.1f", adxlY)} Z:${String.format("%.1f", adxlZ)}", color = CyberCyan, fontSize = 9.sp)
                                    }
                                    Column(horizontalAlignment = Alignment.End) {
                                        Text(String.format("%.2f G", adxlGForce), color = CyberGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        val tiltStatus = when {
                                            kotlin.math.abs(adxlPitch) < 5f && kotlin.math.abs(adxlRoll) < 5f -> "تراز 🎯"
                                            else -> "شیب‌دار 📐"
                                        }
                                        Text(tiltStatus, color = CyberCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }

                                // Status Banner & Calibration Row
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .background(SurfaceBg, RoundedCornerShape(8.dp))
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(adxlStatusText, color = CyberGold, fontSize = 9.sp, fontWeight = FontWeight.Medium)
                                        Text("نویز/لرزش: ${String.format("%.2f", adxlVibrationRms)} RMS ${if (adxlIsCalibrated) "• کالیبره شده" else ""}", color = GrayText, fontSize = 8.sp)
                                    }

                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        Button(
                                            onClick = { viewModel.calibrateAdxlZeroG() },
                                            colors = ButtonDefaults.buttonColors(containerColor = CyberCyan.copy(alpha = 0.2f), contentColor = CyberCyan),
                                            shape = RoundedCornerShape(6.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(28.dp)
                                        ) {
                                            Text("کالیبراسیون Zero-G", fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                        }

                                        if (adxlIsCalibrated) {
                                            IconButton(
                                                onClick = { viewModel.resetAdxlCalibration() },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(Icons.Default.Refresh, contentDescription = "Reset", tint = Color.Gray, modifier = Modifier.size(14.dp))
                                            }
                                        }
                                    }
                                }

                                HorizontalDivider(color = Color.Gray.copy(alpha = 0.2f))

                                // Background Service Room Database Logger Control Row
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (isAdxlLoggingActive) "سرویس پس‌زمینه Room: فعال 🟢" else "سرویس پس‌زمینه Room: غیرفعال ⚪",
                                            color = if (isAdxlLoggingActive) CyberGold else Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "ذخیره‌سازی خام در دیتابیس: $adxlReadingCountFromRoom رکورد",
                                            color = CyberCyan,
                                            fontSize = 9.sp
                                        )
                                    }

                                    Row(
                                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        if (adxlReadingCountFromRoom > 0) {
                                            IconButton(
                                                onClick = { viewModel.clearAdxlRoomLogs() },
                                                modifier = Modifier.size(28.dp)
                                            ) {
                                                Icon(
                                                    Icons.Default.DeleteSweep,
                                                    contentDescription = "Clear Room Logs",
                                                    tint = Color.Red,
                                                    modifier = Modifier.size(16.dp)
                                                )
                                            }
                                        }

                                        Button(
                                            onClick = {
                                                if (isAdxlLoggingActive) {
                                                    viewModel.stopAdxlLogging()
                                                } else {
                                                    viewModel.startAdxlLogging()
                                                }
                                            },
                                            colors = ButtonDefaults.buttonColors(
                                                containerColor = if (isAdxlLoggingActive) Color.Red.copy(alpha = 0.8f) else CyberGold
                                            ),
                                            shape = RoundedCornerShape(8.dp),
                                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                                            modifier = Modifier.height(30.dp)
                                        ) {
                                            Text(
                                                text = if (isAdxlLoggingActive) "توقف ثبت Room" else "شروع ثبت Room",
                                                color = if (isAdxlLoggingActive) Color.White else Color.Black,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold
                                            )
                                        }
                                    }
                                }
                            }
                        } else {
                            // Digital Compass Display
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(CardBg, RoundedCornerShape(10.dp))
                                    .padding(12.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Explore,
                                    contentDescription = null,
                                    tint = CyberGold,
                                    modifier = Modifier
                                        .size(36.dp)
                                        .rotate(-compassHeading)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    val compassModelName = when (activeSensorType) {
                                        SensorType.QMC5883L -> "قطب‌نمای دیجیتال QMC5883L"
                                        SensorType.HMC5883L -> "قطب‌نمای دیجیتال HMC5883L"
                                        else -> "قطب‌نمای دیجیتال (QMC / HMC)"
                                    }
                                    Text(compassModelName, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                    Text("هماهنگ‌کننده جهت اسکن زمین سه‌بعدی", color = GrayText, fontSize = 9.sp)
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(String.format("%.1f°", compassHeading), color = CyberGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                                    val directionName = when {
                                        compassHeading >= 337.5f || compassHeading < 22.5f -> "شمال (N)"
                                        compassHeading >= 22.5f && compassHeading < 67.5f -> "شمال‌شرق (NE)"
                                        compassHeading >= 67.5f && compassHeading < 112.5f -> "شرق (E)"
                                        compassHeading >= 112.5f && compassHeading < 157.5f -> "جنوب‌شرق (SE)"
                                        compassHeading >= 157.5f && compassHeading < 202.5f -> "جنوب (S)"
                                        compassHeading >= 202.5f && compassHeading < 247.5f -> "جنوب‌غرب (SW)"
                                        compassHeading >= 247.5f && compassHeading < 292.5f -> "غرب (W)"
                                        else -> "شمال‌غرب (NW)"
                                    }
                                    Text(directionName, color = CyberCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // Start scanning button
            Button(
                onClick = { viewModel.startNewScan() },
                colors = ButtonDefaults.buttonColors(containerColor = CyberGold),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp)
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, tint = Color.Black)
                Spacer(modifier = Modifier.width(8.dp))
                Text("شروع اسکن سه‌بعدی جدید", color = Color.Black, fontSize = 16.sp, fontWeight = FontWeight.Bold)
            }

            Spacer(modifier = Modifier.height(16.dp))

            // Quick Hardware Setup Guide Card
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg.copy(alpha = 0.6f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(28.dp))
                    Column(modifier = Modifier.weight(1f)) {
                        Text("اتصال سخت‌افزار Gold Radar X20", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "برای اسکن فیزیکی زمین، دستگاه را با کابل USB یا بلوتوث جفت کنید. در غیر اینصورت، شبیه‌ساز خودکار مقادیر فعال می‌شود.",
                            color = GrayText,
                            fontSize = 10.sp
                        )
                    }
                }
            }
        } else {
            // Scan acquisition mode
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceBg),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    // Scan State Info
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("در حال تصویربرداری زمین...", color = CyberGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            Text("سایز کل شبکه: $gridWidth × $gridLength", color = GrayText, fontSize = 11.sp)
                        }
                        
                        // Cancel button
                        IconButton(
                            onClick = { viewModel.cancelScan() },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = CardBg)
                        ) {
                            Icon(Icons.Default.Close, contentDescription = "Discard", tint = Color.Red)
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Real-time Scanning Progress Bar (Sleek Interface Style)
                    val totalPoints = gridWidth * gridLength
                    val scannedPoints = activeScanData.size
                    val rawProgressFraction = if (totalPoints > 0) scannedPoints.toFloat() / totalPoints else 0f
                    val animatedProgress by animateFloatAsState(
                        targetValue = rawProgressFraction,
                        animationSpec = tween(durationMillis = 600, easing = androidx.compose.animation.core.LinearOutSlowInEasing),
                        label = "ScanProgress"
                    )

                    val infiniteTransition = rememberInfiniteTransition(label = "AcquisitionAnimation")

                    // Pulsing neon breath effect
                    val pulseAlpha by infiniteTransition.animateFloat(
                        initialValue = 0.5f,
                        targetValue = 1.0f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(1200, easing = androidx.compose.animation.core.FastOutSlowInEasing),
                            repeatMode = RepeatMode.Reverse
                        ),
                        label = "PulseAlpha"
                    )

                    // Scanner laser glow sweeping effect
                    val sweepOffset by infiniteTransition.animateFloat(
                        initialValue = -0.2f,
                        targetValue = 1.2f,
                        animationSpec = infiniteRepeatable(
                            animation = tween(2000, easing = androidx.compose.animation.core.LinearEasing),
                            repeatMode = RepeatMode.Restart
                        ),
                        label = "SweepOffset"
                    )

                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                // Animated red/gold recording beacon
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .clip(CircleShape)
                                        .background(
                                            if (scannedPoints < totalPoints) {
                                                CyberCyan.copy(alpha = pulseAlpha)
                                            } else {
                                                Color.Green
                                            }
                                        )
                                )
                                Text(
                                    text = "پیشرفت تصویربرداری (Scan Progress)",
                                    color = Color.White,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                            
                            Text(
                                text = "$scannedPoints / $totalPoints نقطه (${(rawProgressFraction * 100).toInt()}%)",
                                color = CyberGold,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        // High tech custom progress bar with sweep highlight
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(16.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .background(CardBg.copy(alpha = 0.5f))
                                .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.08f)), RoundedCornerShape(6.dp))
                        ) {
                            // Draw the progress gradient and the sweeping laser highlights
                            Canvas(modifier = Modifier.fillMaxSize()) {
                                val width = size.width
                                val height = size.height

                                // 1. Draw Dotted Background Track for tech look
                                val dotSpacing = 16f
                                var xPos = 8f
                                while (xPos < width) {
                                    drawCircle(
                                        color = Color.White.copy(alpha = 0.15f),
                                        radius = 1.5f,
                                        center = Offset(xPos, height / 2)
                                    )
                                    xPos += dotSpacing
                                }

                                // 2. Draw Active Progress Bar
                                if (animatedProgress > 0f) {
                                    val progressWidth = width * animatedProgress
                                    
                                    // Draw progress filling
                                    drawRect(
                                        brush = Brush.horizontalGradient(
                                            colors = listOf(
                                                CyberCyan.copy(alpha = 0.75f),
                                                CyberGold.copy(alpha = 0.9f)
                                            )
                                        ),
                                        topLeft = Offset.Zero,
                                        size = Size(progressWidth, height)
                                    )

                                    // 3. Draw Sweeping Scan Highlight (simulating CSS laser acquisition sweep)
                                    val sweepCenter = progressWidth * sweepOffset
                                    val sweepWidth = 80f
                                    if (sweepCenter > -sweepWidth && sweepCenter < progressWidth + sweepWidth) {
                                        drawRect(
                                            brush = Brush.horizontalGradient(
                                                colors = listOf(
                                                    Color.Transparent,
                                                    Color.White.copy(alpha = 0.45f * pulseAlpha),
                                                    Color.White.copy(alpha = 0.85f * pulseAlpha),
                                                    Color.White.copy(alpha = 0.45f * pulseAlpha),
                                                    Color.Transparent
                                                )
                                            ),
                                            topLeft = Offset(sweepCenter - sweepWidth / 2, 0f),
                                            size = Size(sweepWidth, height)
                                        )
                                    }

                                    // 4. Draw leading edge glowing particle
                                    drawCircle(
                                        color = Color.White,
                                        radius = 3.5f,
                                        center = Offset(progressWidth, height / 2)
                                    )
                                    drawCircle(
                                        color = CyberGold.copy(alpha = pulseAlpha),
                                        radius = 8f,
                                        center = Offset(progressWidth, height / 2)
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // DYNAMIC CANVAS GRADIENT VISUALIZATION (Real-time incoming scan points with smooth color transitions)
                    DynamicScanCanvasVisualizer(
                        activeScanData = activeScanData,
                        gridWidth = gridWidth,
                        gridLength = gridLength,
                        currentCol = currentCol,
                        currentRow = currentRow,
                        isScanActive = isScanActive,
                        scanPattern = scanPattern,
                        viewModel = viewModel,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    // Current Point Coordinates legend
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceAround
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("موقعیت فعلی X", color = GrayText, fontSize = 11.sp)
                            Text("ستون ${currentCol + 1} از $gridWidth", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("موقعیت فعلی Y", color = GrayText, fontSize = 11.sp)
                            Text("خط ${currentRow + 1} از $gridLength", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Live Sensor Readout Card
                    Card(
                        colors = CardDefaults.cardColors(containerColor = CardBg),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceAround,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("پالس دریافتی سنسور", color = CyberCyan, fontSize = 10.sp)
                                Text("ADC: $adcValue", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                            Divider(modifier = Modifier.height(30.dp).width(1.dp), color = Color.Gray.copy(alpha = 0.3f))
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text("اختلاف فاز", color = CyberCyan, fontSize = 10.sp)
                                Text("PHASE: $phaseShift", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Live CSV/JSON Telemetry Logger Control Card
                    Card(
                        colors = CardDefaults.cardColors(
                            containerColor = if (isLiveLogging) CyberCyan.copy(alpha = 0.15f) else CardBg
                        ),
                        border = BorderStroke(
                            width = 1.dp,
                            color = if (isLiveLogging) CyberCyan else Color.Transparent
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Assessment,
                                        contentDescription = null,
                                        tint = if (isLiveLogging) CyberCyan else CyberGold,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Text(
                                        text = if (isLiveLogging) "سرویس ثبت زنده (CSV/JSON) فعال است" else "سرویس لاگ‌گیری داده‌های اسکن",
                                        color = Color.White,
                                        fontSize = 11.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Text(
                                    text = "ذخیره‌سازی مستقیم پالس‌ها جهت آنالیز در Excel/Surfer",
                                    color = GrayText,
                                    fontSize = 9.sp
                                )
                            }

                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                if (isLiveLogging) {
                                    Button(
                                        onClick = { viewModel.stopLiveLogging() },
                                        colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.85f)),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("توقف Log", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            viewModel.startLiveLogging(
                                                context,
                                                "ScanSession_${System.currentTimeMillis() % 10000}",
                                                com.example.service.ScanLoggingService.ExportFormat.CSV
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("Log CSV", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                    Button(
                                        onClick = {
                                            viewModel.startLiveLogging(
                                                context,
                                                "ScanSession_${System.currentTimeMillis() % 10000}",
                                                com.example.service.ScanLoggingService.ExportFormat.JSON
                                            )
                                        },
                                        colors = ButtonDefaults.buttonColors(containerColor = CyberGold),
                                        shape = RoundedCornerShape(8.dp),
                                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text("Log JSON", color = Color.Black, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Action Controls
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Auto record switch
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "ثبت خودکار\n(Auto-Pulse)",
                                color = Color.White,
                                fontSize = 11.sp,
                                textAlign = TextAlign.Right
                            )
                            Switch(
                                checked = autoPulseEnabled,
                                onCheckedChange = { autoPulseEnabled = it },
                                colors = SwitchDefaults.colors(
                                    checkedThumbColor = CyberGold,
                                    checkedTrackColor = CyberGold.copy(alpha = 0.3f),
                                    uncheckedThumbColor = Color.Gray,
                                    uncheckedTrackColor = CardBg
                                )
                            )
                        }

                        // Capture Step Trigger (Big Ping Circle)
                        Button(
                            onClick = { viewModel.recordCurrentStep() },
                            colors = ButtonDefaults.buttonColors(containerColor = CyberGold),
                            shape = CircleShape,
                            modifier = Modifier
                                .size(92.dp)
                                .border(BorderStroke(4.dp, CyberGold.copy(alpha = 0.3f)), CircleShape),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(Icons.Default.FlashOn, contentDescription = null, tint = Color.Black, modifier = Modifier.size(28.dp))
                                Text("ثبت پالس", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                        
                        // Fake manual forward key just in case they are offline
                        IconButton(
                            onClick = {
                                // Inject a high peak for testing
                                if (connectionState == ConnectionMode.SIMULATOR) {
                                    // Inject metal / gold peak randomly
                                    val phaseSim = if (Math.random() > 0.7) 45 else -10
                                    val adcSim = if (phaseSim > 0) 780 else 240
                                    viewModel.sensorManager.setSimulatedReading(adcSim, phaseSim)
                                }
                                viewModel.recordCurrentStep()
                            },
                            colors = IconButtonDefaults.iconButtonColors(containerColor = CardBg)
                        ) {
                            Icon(Icons.Default.ArrowForward, contentDescription = "Skip", tint = CyberCyan)
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// TAB 2: 3D VISUALIZER SCREEN
// ==========================================
@Composable
fun VisualizerScreen(viewModel: VisualizerViewModel, onNavigateToAi: () -> Unit = {}) {
    val context = LocalContext.current
    val viewedScan by viewModel.viewedScan.collectAsStateWithLifecycle()
    val filteredScan by viewModel.filteredScan.collectAsStateWithLifecycle()
    val displayScan by viewModel.displayScan.collectAsStateWithLifecycle()
    val isPlaybackActive by viewModel.isPlaybackActive.collectAsStateWithLifecycle()
    val savedScans by viewModel.savedScans.collectAsStateWithLifecycle()

    val yaw by viewModel.yaw.collectAsStateWithLifecycle()
    val pitch by viewModel.pitch.collectAsStateWithLifecycle()
    val zoom by viewModel.zoom.collectAsStateWithLifecycle()
    val panX by viewModel.panX.collectAsStateWithLifecycle()
    val panY by viewModel.panY.collectAsStateWithLifecycle()
    val zScale by viewModel.zScale.collectAsStateWithLifecycle()
    val isAutoScaleEnabled by viewModel.isAutoScaleEnabled.collectAsStateWithLifecycle()
    val colorThreshold by viewModel.colorThreshold.collectAsStateWithLifecycle()
    val renderStyle by viewModel.renderStyle.collectAsStateWithLifecycle()
    val isRgbAnalysis by viewModel.isRgbAnalysis.collectAsStateWithLifecycle()
    val selectedNodeIndex by viewModel.selectedNodeIndex.collectAsStateWithLifecycle()
    val resetTrigger by viewModel.resetTrigger.collectAsStateWithLifecycle()
    val cameraPresetTrigger by viewModel.cameraPresetTrigger.collectAsStateWithLifecycle()
    val measurementUnit by viewModel.measurementUnit.collectAsStateWithLifecycle()
    val selectedDepthLayer by viewModel.selectedDepthLayer.collectAsStateWithLifecycle()
    val soilNoiseFilterEnabled by viewModel.soilNoiseFilterEnabled.collectAsStateWithLifecycle()
    val colorAutoScaleEnabled by viewModel.colorAutoScaleEnabled.collectAsStateWithLifecycle()
    val flattenBaseEnabled by viewModel.flattenBaseEnabled.collectAsStateWithLifecycle()
    val groundPlaneStats by viewModel.groundPlaneStats.collectAsStateWithLifecycle()
    val colorPalette by viewModel.colorPalette.collectAsStateWithLifecycle()

    val userAnnotations by viewModel.userAnnotations.collectAsStateWithLifecycle()
    var showAddAnnotationDialog by remember { mutableStateOf(false) }
    var showMarkersListDialog by remember { mutableStateOf(false) }
    var annotationCol by remember { mutableStateOf(1) }
    var annotationRow by remember { mutableStateOf(1) }
    var annotationLabel by remember { mutableStateOf("") }
    var annotationNote by remember { mutableStateOf("") }
    var annotationColor by remember { mutableStateOf("#FFD700") }

    var showSaveDialog by remember { mutableStateOf(false) }
    var scanSaveName by remember { mutableStateOf("") }
    var scanSaveNotes by remember { mutableStateOf("") }
    var renderEngine by remember { mutableStateOf("ThreeJS") } // "ThreeJS", "D3JS", "Native", "Plan2D"
    var showExportMenu by remember { mutableStateOf(false) }
    var showPresetMenu by remember { mutableStateOf(false) }

    var isTopHudExpanded by remember { mutableStateOf(false) }
    var isLeftMetricsVisible by remember { mutableStateOf(false) }
    var isBottomDrawerExpanded by remember { mutableStateOf(false) }
    var isAllUiHidden by remember { mutableStateOf(false) }
    var selectedSettingsTab by remember { mutableStateOf(0) } // 0: Render & Camera, 1: Filter & Noise, 2: Palette & Layers, 3: Markers & Export

    // Sequential Capability Bar & Single Viewport Overlay State (Clean, Uncluttered 3D Scan View)
    var activeCapabilityPanel by remember { mutableStateOf<Int?>(null) } // null: 100% unobstructed 3D scan, 0: AI, 1: Sensors, 2: Depth, 3: Settings
    var showTutorialDialog by remember { mutableStateOf(false) }

    // Realtime Hardware Sensor State
    val connectionState by viewModel.sensorManager.connectionState.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.sensorManager.tvStatus.collectAsStateWithLifecycle()
    val adcValue by viewModel.sensorManager.adcValue.collectAsStateWithLifecycle()
    val phaseShift by viewModel.sensorManager.phaseShift.collectAsStateWithLifecycle()
    val compassHeading by viewModel.sensorManager.compassHeading.collectAsStateWithLifecycle()
    val adxlPitch by viewModel.sensorManager.adxlPitch.collectAsStateWithLifecycle()
    val adxlRoll by viewModel.sensorManager.adxlRoll.collectAsStateWithLifecycle()
    val adxlGForce by viewModel.sensorManager.adxlGForce.collectAsStateWithLifecycle()
    val adxlStabilityScore by viewModel.sensorManager.adxlStabilityScore.collectAsStateWithLifecycle()
    val activeSensorType by viewModel.sensorManager.sensorType.collectAsStateWithLifecycle()
    val batteryPercentage by viewModel.sensorManager.batteryPercentage.collectAsStateWithLifecycle()
    val aiAnalysisResult by viewModel.aiAnalysisResult.collectAsStateWithLifecycle()
    val filterStrength by viewModel.filterStrength.collectAsStateWithLifecycle()
    val isOutdoorSunlightMode by viewModel.isOutdoorSunlightMode.collectAsStateWithLifecycle()

    if (viewedScan == null) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(Icons.Default.ViewInAr, contentDescription = null, tint = CyberGold, modifier = Modifier.size(64.dp))
            Spacer(modifier = Modifier.height(16.dp))
            Text("هیچ اسکن فعالی یافت نشد", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                "می‌توانید یکی از مدل‌های شبیه‌سازی‌شده زیر را بارگذاری کنید یا در تب اسکن، اسکن جدیدی ثبت نمایید.",
                color = GrayText,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 24.dp)
            )
            Spacer(modifier = Modifier.height(24.dp))
            
            Column(
                verticalArrangement = Arrangement.spacedBy(10.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.widthIn(max = 320.dp)
            ) {
                Button(
                    onClick = { viewModel.loadPredefinedScan("buried_gold_chest") },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberGold),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.Black)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("بارگذاری صندوقچه طلای مدفون (۳D)", color = Color.Black, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { viewModel.loadPredefinedScan("deep_cave") },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan),
                    border = BorderStroke(1.dp, CyberCyan),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.Explore, contentDescription = null, tint = CyberCyan)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("بارگذاری غار و حفره زیرزمینی", color = CyberCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }

                OutlinedButton(
                    onClick = { viewModel.loadPredefinedScan("ancient_grave_and_tunnel") },
                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                    border = BorderStroke(1.dp, CardBg),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Default.TurnedIn, contentDescription = null, tint = Color.White)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("بارگذاری راهرو و تونل باستانی", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
            }
        }
        return
    }

    val currentScan = displayScan ?: filteredScan ?: viewedScan!!
    val gridScanData = remember(currentScan) { currentScan.getGridData() }

    // Helper Lambda 1: Viewport Panel (Render Engine Canvas + View Toolbar)
    @Composable
    fun ViewportPanelContent(modifier: Modifier = Modifier) {
        Column(modifier = modifier.fillMaxSize()) {
            // Viewport Top Control Bar
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(SurfaceBg.copy(alpha = 0.95f))
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.ViewInAr, contentDescription = null, tint = CyberGold, modifier = Modifier.size(16.dp))
                    Text(currentScan.name, color = CyberGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("(${currentScan.width}×${currentScan.length})", color = GrayText, fontSize = 10.sp)
                }

                // Render Engine Picker
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val engines = listOf(
                        "ThreeJS" to "WebGL 3D",
                        "D3JS" to "D3.js",
                        "Native" to "بومی Canvas",
                        "Plan2D" to "۲D Plan"
                    )
                    engines.forEach { (id, label) ->
                        val isSel = renderEngine == id
                        Button(
                            onClick = { renderEngine = id },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isSel) CyberCyan.copy(alpha = 0.25f) else CardBg,
                                contentColor = if (isSel) CyberCyan else Color.White
                            ),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, if (isSel) CyberCyan else Color.Transparent),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(26.dp)
                        ) {
                            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Reset Camera & Actions
                    IconButton(
                        onClick = { viewModel.resetView() },
                        modifier = Modifier.size(26.dp)
                    ) {
                        Icon(Icons.Default.CenterFocusWeak, contentDescription = "Reset Camera", tint = CyberCyan, modifier = Modifier.size(16.dp))
                    }

                    // Interactive Help Tutorial Overlay Trigger Button
                    IconButton(
                        onClick = { viewModel.openHelpTutorial() },
                        modifier = Modifier.size(26.dp).testTag("help_tutorial_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.HelpOutline,
                            contentDescription = "Help Tutorial",
                            tint = CyberCyan,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // Outdoor Direct Sunlight High Contrast Mode Toggle Button
                    IconButton(
                        onClick = { viewModel.toggleOutdoorSunlightMode() },
                        modifier = Modifier.size(26.dp).testTag("toggle_outdoor_sunlight_mode")
                    ) {
                        Icon(
                            imageVector = if (isOutdoorSunlightMode) Icons.Default.WbSunny else Icons.Default.WbTwilight,
                            contentDescription = "Outdoor Sunlight Mode",
                            tint = if (isOutdoorSunlightMode) CyberGold else GrayText,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    // TestTag Container for overlay mode test requirements
                    Box(modifier = Modifier.testTag("visual_overlay_toggle_card")) {
                        Row(horizontalArrangement = Arrangement.spacedBy(2.dp)) {
                            IconButton(
                                onClick = { viewModel.setRenderStyle("Wireframe") },
                                modifier = Modifier.size(26.dp).testTag("toggle_wireframe_mode")
                            ) {
                                Icon(Icons.Default.GridOn, contentDescription = "Wireframe", tint = if (renderStyle == "Wireframe") CyberCyan else GrayText, modifier = Modifier.size(16.dp))
                            }
                            IconButton(
                                onClick = { viewModel.setRenderStyle("Heatmap") },
                                modifier = Modifier.size(26.dp).testTag("toggle_heatmap_mode")
                            ) {
                                Icon(Icons.Default.Whatshot, contentDescription = "Heatmap", tint = if (renderStyle == "Heatmap") CyberGold else GrayText, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }
            }

            // Canvas Body
            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                when (renderEngine) {
                    "ThreeJS" -> {
                        ThreeDWebViewVisualizer(
                            scan = currentScan,
                            selectedNodeIndex = selectedNodeIndex,
                            resetTrigger = resetTrigger,
                            cameraPresetTrigger = cameraPresetTrigger,
                            renderStyle = renderStyle,
                            isRgbAnalysis = isRgbAnalysis,
                            zScale = zScale,
                            colorPalette = colorPalette,
                            userAnnotations = userAnnotations,
                            onNodeSelected = { viewModel.selectNode(it) },
                            onRenderStyleChanged = { viewModel.setRenderStyle(it) },
                            onRgbAnalysisChanged = { viewModel.setIsRgbAnalysis(it) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    "D3JS" -> {
                        D3WebViewVisualizer(
                            scan = currentScan,
                            selectedNodeIndex = selectedNodeIndex,
                            resetTrigger = resetTrigger,
                            cameraPresetTrigger = cameraPresetTrigger,
                            renderStyle = renderStyle,
                            isRgbAnalysis = isRgbAnalysis,
                            zScale = zScale,
                            colorPalette = colorPalette,
                            onNodeSelected = { viewModel.selectNode(it) },
                            onRenderStyleChanged = { viewModel.setRenderStyle(it) },
                            onRgbAnalysisChanged = { viewModel.setIsRgbAnalysis(it) },
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                    "Plan2D" -> {
                        PlanViewScreen(viewModel = viewModel)
                    }
                    else -> {
                        ThreeDGridVisualizer(
                            scan = currentScan,
                            yaw = yaw,
                            pitch = pitch,
                            zoom = zoom,
                            panX = panX,
                            panY = panY,
                            zScale = zScale,
                            colorThreshold = colorThreshold,
                            renderStyle = renderStyle,
                            isRgbAnalysis = isRgbAnalysis,
                            selectedNodeIndex = selectedNodeIndex,
                            onNodeSelected = { viewModel.selectNode(it) },
                            onRotate = { dy, dp -> viewModel.rotate(dy, dp) },
                            onZoom = { s -> viewModel.changeZoom(s) },
                            onPan = { dx, dy -> viewModel.pan(dx, dy) },
                            colorPalette = colorPalette,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }

                // Overlay Playback controller when active
                if (isPlaybackActive) {
                    ScanPlaybackControllerCard(
                        viewModel = viewModel,
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(12.dp)
                            .zIndex(50f)
                    )
                }
            }
        }
    }

    // Helper Lambda 2: AI Insights & Anomaly Panel
    @Composable
    fun AiInsightsContent(modifier: Modifier = Modifier) {
        val peakVal = remember(gridScanData) { gridScanData.maxOfOrNull { it } ?: 0f }
        val minVal = remember(gridScanData) { gridScanData.minOfOrNull { it } ?: 0f }
        val maxAbsSignal = maxOf(abs(peakVal), abs(minVal))
        val metalProb = ((peakVal / 800f).coerceIn(0f, 1f) * 100).toInt()
        val cavityProb = ((abs(minVal) / 800f).coerceIn(0f, 1f) * 100).toInt()
        val estDepthM = 0.8f + (maxAbsSignal / 1000f) * 4.2f

        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Header
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = CyberGold, modifier = Modifier.size(18.dp))
                    Text("تحلیل هوشمند آنومالی و فلزیابی", color = CyberGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Text("هوش مصنوعی OKM", color = GrayText, fontSize = 10.sp)
            }

            HorizontalDivider(color = CardBg.copy(alpha = 0.4f))

            // Probabilities Cards
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Metal Probability
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("احتمال فلز (Metal)", color = CyberGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("$metalProb%", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        LinearProgressIndicator(
                            progress = metalProb / 100f,
                            color = CyberGold,
                            trackColor = SurfaceBg,
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                        )
                    }
                }

                // Cavity Probability
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("احتمال حفره (Cavity)", color = CyberCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        Text("$cavityProb%", color = Color.White, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        LinearProgressIndicator(
                            progress = cavityProb / 100f,
                            color = CyberCyan,
                            trackColor = SurfaceBg,
                            modifier = Modifier.fillMaxWidth().height(4.dp).clip(RoundedCornerShape(2.dp))
                        )
                    }
                }

                // Est Depth
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("عمق تخمینی", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Spacer(modifier = Modifier.height(4.dp))
                        val depthStr = if (measurementUnit == "Feet") String.format("%.1fft", estDepthM * 3.28084f) else String.format("%.1fm", estDepthM)
                        Text(depthStr, color = CyberGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("مبنای خاک", color = GrayText, fontSize = 8.sp)
                    }
                }
            }

            // Summary Text Card
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceBg.copy(alpha = 0.8f)),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, CardBg),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("گزارش هوش مصنوعی:", color = CyberGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(4.dp))
                    val aiText = aiAnalysisResult ?: if (metalProb > 60) {
                        "سیگنال فوق‌العاده قوی با قله تداخل الکترومغناطیسی مثبت. نشان‌دهنده شیء فلزی با رسانایی بالا در خاک. توصیه می‌شود فیلتر خاک اعمال گردد."
                    } else if (cavityProb > 60) {
                        "آنومالی منفی شدید کشف شد. نشان‌دهنده ساختار حفره، راهرو یا اتاقک زیرزمینی دست‌ساز."
                    } else {
                        "سیگنال خاک یکنواخت و طبیعی. فاقد آلودگی معدنی شدید."
                    }
                    Text(aiText, color = Color.White, fontSize = 11.sp, lineHeight = 16.sp)
                }
            }

            // Trigger AI Button
            Button(
                onClick = { viewModel.analyzeCurrentScanWithAi() },
                colors = ButtonDefaults.buttonColors(containerColor = CyberGold),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(36.dp)
            ) {
                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("اجرای تحلیل عمیق هوش مصنوعی", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }

            // Open Full AI Screen Button
            OutlinedButton(
                onClick = onNavigateToAi,
                colors = ButtonDefaults.outlinedButtonColors(contentColor = CyberCyan),
                border = BorderStroke(1.dp, CyberCyan),
                shape = RoundedCornerShape(8.dp),
                modifier = Modifier.fillMaxWidth().height(36.dp)
            ) {
                Icon(Icons.Default.OpenInNew, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(16.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("ورود به دستیار پیشرفته AI (چت کامل)", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }

    // Helper Lambda 3: Sensor Readings & Telemetry Panel
    @Composable
    fun SensorReadingsContent(modifier: Modifier = Modifier) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Sensors, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(18.dp))
                    Text("سنسورها و داده‌های تله‌متری زنده", color = CyberCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                
                // Status Pill
                val modeColor = when (connectionState) {
                    ConnectionMode.DISCONNECTED -> Color.Red
                    ConnectionMode.CONNECTING_USB, ConnectionMode.CONNECTING_BT -> Color.Yellow
                    ConnectionMode.USB -> Color.Green
                    ConnectionMode.BLUETOOTH -> CyberCyan
                    ConnectionMode.SIMULATOR -> CyberGold
                }
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    Box(modifier = Modifier.size(8.dp).clip(CircleShape).background(modeColor))
                    Text(connectionStatus, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            HorizontalDivider(color = CardBg.copy(alpha = 0.4f))

            // Main Sensor Signal Metrics
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("مقدار ADC سنسور", color = GrayText, fontSize = 9.sp)
                        Text("$adcValue", color = CyberGold, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("سیگنال خام LSB", color = GrayText, fontSize = 8.sp)
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("اختلاف فاز", color = GrayText, fontSize = 9.sp)
                        Text("$phaseShift°", color = CyberCyan, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text("زاویه فاز الکترومغناطیس", color = GrayText, fontSize = 8.sp)
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("مدل سنسور", color = GrayText, fontSize = 9.sp)
                        Text(activeSensorType.name.take(7), color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                        Text("درایور CH340", color = GrayText, fontSize = 8.sp)
                    }
                }
            }

            // Compass & Motion Readings
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceBg.copy(alpha = 0.8f)),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, CardBg),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Icon(Icons.Default.Explore, contentDescription = null, tint = CyberGold, modifier = Modifier.size(20.dp).rotate(-compassHeading))
                            Column {
                                Text("قطب‌نما HMC5883L", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                Text("جهت‌گیری جغرافیایی اسکن", color = GrayText, fontSize = 9.sp)
                            }
                        }
                        Text(String.format("%.1f°", compassHeading), color = CyberGold, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }

                    HorizontalDivider(color = CardBg.copy(alpha = 0.3f))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text("شیب Pitch", color = GrayText, fontSize = 9.sp)
                            Text(String.format("%.1f°", adxlPitch), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("انحراف Roll", color = GrayText, fontSize = 9.sp)
                            Text(String.format("%.1f°", adxlRoll), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("شتاب G-Force", color = GrayText, fontSize = 9.sp)
                            Text(String.format("%.2f G", adxlGForce), color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                        Column {
                            Text("پایداری", color = GrayText, fontSize = 9.sp)
                            Text("$adxlStabilityScore%", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Helper Lambda 4: Depth & Soil Stratification Panel
    @Composable
    fun DepthProfileContent(modifier: Modifier = Modifier) {
        val selIndex = selectedNodeIndex ?: 0
        val selCol = selIndex % currentScan.width
        val selRow = selIndex / currentScan.width
        val selVal = gridScanData.getOrNull(selIndex) ?: 0f
        val calculatedDepthM = 0.5f + (abs(selVal) / 1000f) * 4.5f

        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Layers, contentDescription = null, tint = CyberGold, modifier = Modifier.size(18.dp))
                    Text("سنجش عمق و لایه‌های زمین‌شناسی", color = CyberGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Text("نوع خاک: ${currentScan.soilType}", color = GrayText, fontSize = 10.sp)
            }

            HorizontalDivider(color = CardBg.copy(alpha = 0.4f))

            // Selected Point & Depth Gauge Box
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("نقطه انتخابی", color = GrayText, fontSize = 9.sp)
                        Text("X:${selCol + 1} | Y:${selRow + 1}", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("موقعیت در شبکه", color = GrayText, fontSize = 8.sp)
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("عمق محاسبه‌شده", color = CyberGold, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        val depthValStr = if (measurementUnit == "Feet") String.format("%.2f ft", calculatedDepthM * 3.28084f) else String.format("%.2f m", calculatedDepthM)
                        Text(depthValStr, color = CyberGold, fontSize = 16.sp, fontWeight = FontWeight.Bold)
                        Text("دقت ±۰.۱m", color = GrayText, fontSize = 8.sp)
                    }
                }

                Card(
                    colors = CardDefaults.cardColors(containerColor = CardBg),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Column(modifier = Modifier.padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("شدت سیگنال", color = CyberCyan, fontSize = 9.sp)
                        Text(String.format("%.0f LSB", selVal), color = CyberCyan, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Text("پالس رادار", color = GrayText, fontSize = 8.sp)
                    }
                }
            }

            // Soil Layers Strata Visualizer
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceBg.copy(alpha = 0.8f)),
                shape = RoundedCornerShape(10.dp),
                border = BorderStroke(1.dp, CardBg),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("نیمرخ لایه‌های خاک مدفون (Soil Stratification):", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)

                    val strata = listOf(
                        Triple("سطحی (Humus)", "۰ تا ۳.۵ متر", CyberCyan),
                        Triple("خاک رس و سنگریزه (Subsoil)", "۳.۵ تا ۸ متر", CyberGold),
                        Triple("سنگ معدنی عمیق (Mineral Rock)", "۸ تا ۱۴ متر", CyberRed),
                        Triple("بستر سخت زمین (Bedrock)", "۱۴ تا ۲۰ متر", Color(0xFF9C27B0))
                    )

                    strata.forEach { (name, depth, color) ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(CardBg, RoundedCornerShape(6.dp))
                                .padding(horizontal = 8.dp, vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(color))
                                Text(name, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Medium)
                            }
                            Text(depth, color = color, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }
    }

    // Helper Lambda 5: Render Controls & Filter Panel
    @Composable
    fun RenderControlsContent(modifier: Modifier = Modifier) {
        Column(
            modifier = modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Default.Tune, contentDescription = null, tint = CyberCyan, modifier = Modifier.size(18.dp))
                    Text("تنظیمات رندر و حذف نویز", color = CyberCyan, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                }
                Text("Z-Scale & Filter", color = GrayText, fontSize = 10.sp)
            }

            HorizontalDivider(color = CardBg.copy(alpha = 0.4f))

            // Render Style Switcher
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("حالت رندر سه‌بعدی:", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    listOf("Solid" to "حجمی", "Wireframe" to "شبکه", "Points" to "نقاط", "Heatmap" to "حرارتی").forEach { (style, label) ->
                        val active = renderStyle == style
                        Button(
                            onClick = { viewModel.setRenderStyle(style) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (active) CyberCyan.copy(alpha = 0.25f) else CardBg,
                                contentColor = if (active) CyberCyan else Color.White
                            ),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, if (active) CyberCyan else Color.Transparent),
                            contentPadding = PaddingValues(horizontal = 6.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp).weight(1f)
                        ) {
                            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Color Palette Switcher
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("پالت رنگ آنومالی:", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("Thermal" to "حرارتی", "Classic" to "کلاسیک", "Grayscale" to "خاکستری", "Contrast" to "کنتراست", "IronOxide" to "معدنی").forEach { (id, label) ->
                        val active = colorPalette == id
                        Button(
                            onClick = { viewModel.setColorPalette(id) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (active) CyberGold.copy(alpha = 0.25f) else CardBg,
                                contentColor = if (active) CyberGold else Color.White
                            ),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, if (active) CyberGold else Color.Transparent),
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            modifier = Modifier.height(28.dp)
                        ) {
                            Text(label, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }

            // Sliders: Z-scale, Threshold, Filter strength
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                // Z-Scale
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("تقویت ارتفاع (Z):", color = CyberCyan, fontSize = 10.sp, modifier = Modifier.width(85.dp))
                    Slider(
                        value = zScale,
                        onValueChange = { viewModel.setZScale(it) },
                        valueRange = 0.1f..3.0f,
                        colors = SliderDefaults.colors(thumbColor = CyberCyan, activeTrackColor = CyberCyan, inactiveTrackColor = CardBg),
                        modifier = Modifier.weight(1f)
                    )
                    Text(String.format("%.1fx", zScale), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                // Threshold
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("حذف ذرات:", color = CyberGold, fontSize = 10.sp, modifier = Modifier.width(85.dp))
                    Slider(
                        value = colorThreshold,
                        onValueChange = { viewModel.setColorThreshold(it) },
                        valueRange = 0.0f..0.85f,
                        colors = SliderDefaults.colors(thumbColor = CyberGold, activeTrackColor = CyberGold, inactiveTrackColor = CardBg),
                        modifier = Modifier.weight(1f)
                    )
                    Text(String.format("%d%%", (colorThreshold * 100).toInt()), color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }

                // Digital Filter Strength
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("فیلتر دیجیتال:", color = CyberRed, fontSize = 10.sp, modifier = Modifier.width(85.dp))
                    Slider(
                        value = filterStrength,
                        onValueChange = { viewModel.setFilterStrength(it) },
                        valueRange = -1.0f..1.0f,
                        colors = SliderDefaults.colors(thumbColor = CyberRed, activeTrackColor = CyberRed, inactiveTrackColor = CardBg),
                        modifier = Modifier.weight(1f)
                    )
                    val filterPercent = (abs(filterStrength) * 100).toInt()
                    val filterLabel = when {
                        filterStrength > 0.05f -> "LPF $filterPercent%"
                        filterStrength < -0.05f -> "HPF $filterPercent%"
                        else -> "RAW"
                    }
                    Text(filterLabel, color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                }
            }

            // Switches: Soil noise filter, RGB analysis, Base flatten
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("حذف آلودگی خاک (Soil Filter)", color = Color.White, fontSize = 10.sp)
                    Switch(
                        checked = soilNoiseFilterEnabled,
                        onCheckedChange = { viewModel.setSoilNoiseFilterEnabled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyberGold, checkedTrackColor = CyberGold.copy(alpha = 0.4f))
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("تحلیل سه رنگ (RGB Analysis)", color = Color.White, fontSize = 10.sp)
                    Switch(
                        checked = isRgbAnalysis,
                        onCheckedChange = { viewModel.setIsRgbAnalysis(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyberCyan, checkedTrackColor = CyberCyan.copy(alpha = 0.4f))
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("تراز بستر زمین (Base Flattening)", color = Color.White, fontSize = 10.sp)
                    Switch(
                        checked = flattenBaseEnabled,
                        onCheckedChange = { viewModel.setFlattenBaseEnabled(it) },
                        colors = SwitchDefaults.colors(checkedThumbColor = CyberGold, checkedTrackColor = CyberGold.copy(alpha = 0.4f))
                    )
                }
            }
        }
    }

    // MAIN CONTAINER: Clean Single-Viewport 3D Ground Scan View with Sequential Capability Bar
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBg)
    ) {
        // 1. Fullscreen 3D Viewport Canvas (Primary Focus: 100% Clear Ground Scan Area)
        ViewportPanelContent(modifier = Modifier.fillMaxSize())

        // 2. Top Sleek Status & Actions Header (Non-Obstructive Floating Overlay)
        if (!isAllUiHidden) {
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceBg.copy(alpha = 0.92f)),
                shape = RoundedCornerShape(bottomStart = 16.dp, bottomEnd = 16.dp),
                border = BorderStroke(1.dp, CardBg),
                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Left: Scan Title & Specifications
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Icon(Icons.Default.ViewInAr, contentDescription = null, tint = CyberGold, modifier = Modifier.size(20.dp))
                        Column {
                            Text(currentScan.name, color = CyberGold, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                            Text("شبکه: ${currentScan.width}×${currentScan.length} | خاک: ${currentScan.soilType}", color = GrayText, fontSize = 9.sp)
                        }
                    }

                    // Right: Quick Action Buttons (Tutorial, Sunlight, Reset, Replay, Save, Markers, Export, UI Toggle)
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Help Tutorial Button
                        IconButton(
                            onClick = { showTutorialDialog = true },
                            modifier = Modifier.size(32.dp).testTag("help_tutorial_button")
                        ) {
                            Icon(Icons.Default.HelpOutline, contentDescription = "Help", tint = CyberCyan, modifier = Modifier.size(16.dp))
                        }

                        // Outdoor Sunlight High-Contrast Mode Toggle
                        IconButton(
                            onClick = { viewModel.toggleOutdoorSunlightMode() },
                            modifier = Modifier.size(32.dp).testTag("toggle_outdoor_sunlight_mode")
                        ) {
                            Icon(
                                imageVector = if (isOutdoorSunlightMode) Icons.Default.WbSunny else Icons.Default.Brightness2,
                                contentDescription = "Sunlight Mode",
                                tint = if (isOutdoorSunlightMode) CyberGold else GrayText,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Reset Camera Position
                        IconButton(
                            onClick = { viewModel.resetView() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset Camera", tint = Color.White, modifier = Modifier.size(16.dp))
                        }

                        // Replay Scan Playback
                        IconButton(
                            onClick = { viewModel.togglePlaybackMode() },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isPlaybackActive) Icons.Default.PauseCircle else Icons.Default.PlayCircle,
                                contentDescription = "Replay Scan",
                                tint = if (isPlaybackActive) CyberGold else CyberCyan,
                                modifier = Modifier.size(16.dp)
                            )
                        }

                        // Save Scan
                        if (currentScan.id < 0 && currentScan.id != -100) {
                            IconButton(
                                onClick = {
                                    scanSaveName = "اسکن زمین ${SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date())}"
                                    scanSaveNotes = ""
                                    showSaveDialog = true
                                },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Save, contentDescription = "Save Scan", tint = CyberGold, modifier = Modifier.size(16.dp))
                            }
                        }

                        // Add Marker
                        IconButton(
                            onClick = {
                                if (selectedNodeIndex != null) {
                                    annotationCol = (selectedNodeIndex!! % currentScan.width) + 1
                                    annotationRow = (selectedNodeIndex!! / currentScan.width) + 1
                                } else {
                                    annotationCol = 1
                                    annotationRow = 1
                                }
                                annotationLabel = ""
                                annotationNote = ""
                                annotationColor = "#FFD700"
                                showAddAnnotationDialog = true
                            },
                            modifier = Modifier.size(32.dp).testTag("add_annotation_button")
                        ) {
                            Icon(Icons.Default.AddLocation, contentDescription = "Add Marker", tint = CyberGold, modifier = Modifier.size(16.dp))
                        }

                        // List Markers
                        IconButton(
                            onClick = { showMarkersListDialog = true },
                            modifier = Modifier.size(32.dp).testTag("list_annotations_button")
                        ) {
                            Icon(Icons.Default.Bookmark, contentDescription = "List Markers", tint = CyberCyan, modifier = Modifier.size(16.dp))
                        }

                        // Export Data
                        Box {
                            IconButton(
                                onClick = { showExportMenu = true },
                                modifier = Modifier.size(32.dp)
                            ) {
                                Icon(Icons.Default.Share, contentDescription = "Export Data", tint = CyberCyan, modifier = Modifier.size(16.dp))
                            }

                            DropdownMenu(
                                expanded = showExportMenu,
                                onDismissRequest = { showExportMenu = false },
                                modifier = Modifier.background(CardBg)
                            ) {
                                DropdownMenuItem(
                                    text = { Text("📤 اشتراک‌گذاری گزارش متنی (پیام‌رسان‌ها)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        showExportMenu = false
                                        currentScan?.let { shareScanRecord(context, it, "summary") }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("📊 اشتراک‌گذاری فایل CSV (Surfer/Excel)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        showExportMenu = false
                                        currentScan?.let { shareScanRecord(context, it, "csv") }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("💾 دانلود فایل CSV در Downloads", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        showExportMenu = false
                                        currentScan?.let { exportScanRecord(context, it, "csv") }
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("💾 دانلود فایل JSON در Downloads", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                                    onClick = {
                                        showExportMenu = false
                                        currentScan?.let { exportScanRecord(context, it, "json") }
                                    }
                                )
                            }
                        }

                        // Toggle UI Overlay Visibility
                        IconButton(
                            onClick = { isAllUiHidden = !isAllUiHidden },
                            modifier = Modifier.size(32.dp)
                        ) {
                            Icon(
                                imageVector = if (isAllUiHidden) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                contentDescription = "Toggle UI",
                                tint = GrayText,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }

        // 3. Floating Quick Visual Overlay Switcher (Top Right: Wireframe vs Heatmap)
        if (!isAllUiHidden) {
            Card(
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.75f)),
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.25f)),
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(top = 54.dp, end = 12.dp)
                    .width(150.dp)
                    .testTag("visual_overlay_toggle_card")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(4.dp)
                        .background(CardBg, RoundedCornerShape(8.dp)),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    val isWireframe = renderStyle == "Wireframe"
                    val isHeatmap = renderStyle == "Heatmap"

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                            .background(
                                color = if (isWireframe) CyberCyan.copy(alpha = 0.2f) else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isWireframe) CyberCyan else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { viewModel.setRenderStyle("Wireframe") }
                            .testTag("toggle_wireframe_mode"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("شبکه", color = if (isWireframe) CyberCyan else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }

                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(28.dp)
                            .background(
                                color = if (isHeatmap) CyberCyan.copy(alpha = 0.2f) else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .border(
                                width = 1.dp,
                                color = if (isHeatmap) CyberCyan else Color.Transparent,
                                shape = RoundedCornerShape(6.dp)
                            )
                            .clickable { viewModel.setRenderStyle("Heatmap") }
                            .testTag("toggle_heatmap_mode"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("حرارتی", color = if (isHeatmap) CyberCyan else Color.White, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }

        // 4. Sequential Capability Toolbar & Expandable Drawer Panel (Bottom Alignment)
        if (!isAllUiHidden) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Expandable Feature Drawer (Appears neatly above the capability bar when a tab is clicked)
                AnimatedVisibility(
                    visible = activeCapabilityPanel != null,
                    enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
                    exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
                ) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceBg.copy(alpha = 0.95f)),
                        shape = RoundedCornerShape(16.dp),
                        border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.4f)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 280.dp)
                    ) {
                        Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                            // Drawer Header with Feature Title & Close Button
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                val panelTitle = when (activeCapabilityPanel) {
                                    0 -> "🤖 تحلیل هوشمند هوش مصنوعی (AI Analysis)"
                                    1 -> "⚡ داده‌های زنده حسگر و تله‌متری (Sensors)"
                                    2 -> "📏 پروفایل عمق و لایه‌های خاک (Depth Profile)"
                                    3 -> "⚙️ تنظیمات رندر، فیلتر و رنگ (Render Settings)"
                                    else -> ""
                                }
                                Text(panelTitle, color = CyberGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                IconButton(
                                    onClick = { activeCapabilityPanel = null },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(Icons.Default.Close, contentDescription = "Close Panel", tint = Color.White, modifier = Modifier.size(16.dp))
                                }
                            }

                            HorizontalDivider(color = CardBg, modifier = Modifier.padding(vertical = 6.dp))

                            // Drawer Active Content Component
                            Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                                when (activeCapabilityPanel) {
                                    0 -> AiInsightsContent()
                                    1 -> SensorReadingsContent()
                                    2 -> DepthProfileContent()
                                    3 -> RenderControlsContent()
                                }
                            }
                        }
                    }
                }

                // Tidy, Orderly Sequential Capability Bar
                Card(
                    colors = CardDefaults.cardColors(containerColor = SurfaceBg.copy(alpha = 0.92f)),
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.dp, CardBg),
                    elevation = CardDefaults.cardElevation(defaultElevation = 6.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        val capabilities = listOf(
                            Triple(0, "🤖 هوش مصنوعی", CyberGold),
                            Triple(1, "⚡ حسگرها", CyberCyan),
                            Triple(2, "📏 پروفایل عمق", CyberGold),
                            Triple(3, "⚙️ تنظیمات رندر", CyberCyan)
                        )

                        capabilities.forEach { (index, label, accentColor) ->
                            val isSelected = activeCapabilityPanel == index
                            Button(
                                onClick = {
                                    activeCapabilityPanel = if (isSelected) null else index
                                },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = if (isSelected) accentColor.copy(alpha = 0.25f) else CardBg,
                                    contentColor = if (isSelected) accentColor else Color.White
                                ),
                                shape = RoundedCornerShape(10.dp),
                                border = BorderStroke(1.dp, if (isSelected) accentColor else Color.Transparent),
                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                modifier = Modifier.height(36.dp).weight(1f)
                            ) {
                                Text(label, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                            }
                        }
                    }
                }
            }
        }
    }

    // Tutorial Help Dialog
    if (showTutorialDialog) {
        AlertDialog(
            onDismissRequest = { showTutorialDialog = false },
            containerColor = SurfaceBg,
            title = {
                Text(
                    "راهنمای کار با نمایشگر سه‌بعدی OKM",
                    color = CyberGold,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("۱. چرخش و زاویه دید: لمس و کشیدن روی صفحه برای تغییر زاویه دید سه‌بعدی.", color = Color.White, fontSize = 11.sp)
                    Text("۲. جابه‌جایی و زوم: حرکت دو انگشتی برای بزرگ‌نمایی نقشه.", color = Color.White, fontSize = 11.sp)
                    Text("۳. نوار قابلیت‌ها (پایین صفحه): انتخاب قابلیت‌های تحلیل AI، حسگر زنده، پروفایل عمق و تنظیمات رندر به ترتیب دلخواه.", color = Color.White, fontSize = 11.sp)
                    Text("۴. نشانگرها: لمس هر نقطه روی اسکن و فشردن + برای ثبت یادداشت تخصصی.", color = Color.White, fontSize = 11.sp)
                }
            },
            confirmButton = {
                Button(
                    onClick = { showTutorialDialog = false },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberGold)
                ) {
                    Text("متوجه شدم", color = Color.Black, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                }
            }
        )
    }

    // Elegant Persian Save dialog
    if (showSaveDialog) {
        AlertDialog(
            onDismissRequest = { showSaveDialog = false },
            containerColor = SurfaceBg,
            title = {
                Text(
                    "ذخیره تصویر سه‌بعدی اسکن زمین",
                    color = CyberGold,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Right,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            text = {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedTextField(
                        value = scanSaveName,
                        onValueChange = { scanSaveName = it },
                        label = { Text("نام فایل اسکن", color = GrayText) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = CyberGold,
                            unfocusedBorderColor = CardBg
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = scanSaveNotes,
                        onValueChange = { scanSaveNotes = it },
                        label = { Text("یادداشت و اطلاعات عمق", color = GrayText) },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White,
                            focusedBorderColor = CyberGold,
                            unfocusedBorderColor = CardBg
                        ),
                        modifier = Modifier.fillMaxWidth(),
                        maxLines = 3
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveScan(scanSaveName, scanSaveNotes)
                        showSaveDialog = false
                        Toast.makeText(context, "فایل با موفقیت در تاریخچه ذخیره شد", Toast.LENGTH_SHORT).show()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberGold)
                ) {
                    Text("ذخیره", color = Color.Black, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showSaveDialog = false }) {
                    Text("انصراف", color = Color.White)
                }
            }
        )
    }

    if (showAddAnnotationDialog) {
        AddAnnotationDialog(
            initialCol = annotationCol,
            initialRow = annotationRow,
            gridWidth = currentScan.width,
            gridLength = currentScan.length,
            onDismiss = { showAddAnnotationDialog = false },
            onSave = { col, row, label, note, colorHex ->
                viewModel.addUserAnnotation(col, row, label, note, colorHex)
                Toast.makeText(context, "نشانگر با موفقیت در فضای 3D ثبت شد", Toast.LENGTH_SHORT).show()
            }
        )
    }

    if (showMarkersListDialog) {
        MarkersListDialog(
            annotations = userAnnotations,
            scanWidth = currentScan.width,
            scanLength = currentScan.length,
            scanData = currentScan.getGridData(),
            onDismiss = { showMarkersListDialog = false },
            onFocusMarker = { col, row ->
                val index = row * currentScan.width + col
                viewModel.selectNode(index)
            },
            onDeleteMarker = { id ->
                viewModel.deleteUserAnnotation(id)
            },
            onClearAll = {
                viewModel.clearUserAnnotations()
            }
        )
    }
}

// ==========================================
// 3D ANNOTATIONS & MARKERS DIALOG COMPONENTS
// ==========================================
private fun parseHexColor(hex: String): Color {
    return try {
        val cleanHex = hex.removePrefix("#")
        val colorInt = android.graphics.Color.parseColor("#$cleanHex")
        Color(colorInt)
    } catch (e: Exception) {
        CyberGold
    }
}

@Composable
fun AddAnnotationDialog(
    initialCol: Int,
    initialRow: Int,
    gridWidth: Int,
    gridLength: Int,
    onDismiss: () -> Unit,
    onSave: (col: Int, row: Int, label: String, note: String, colorHex: String) -> Unit
) {
    var col by remember { mutableStateOf(initialCol) }
    var row by remember { mutableStateOf(initialRow) }
    var label by remember { mutableStateOf("") }
    var note by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf("#FFD700") }

    val colorList = listOf(
        "#FFD700" to "Cyber Gold",
        "#00E5FF" to "Cyber Cyan",
        "#FF1744" to "Neon Red",
        "#76FF03" to "Neon Green",
        "#D500F9" to "Neon Purple"
    )

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceBg,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Default.AddLocation, contentDescription = null, tint = CyberGold)
                Column {
                    Text("افزودن نشانگر سه‌بعدی و یادداشت", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text("علامت‌گذاری نقطه در فضای 3D اسکن", color = GrayText, fontSize = 10.sp)
                }
            }
        },
        text = {
            Column(
                verticalArrangement = Arrangement.spacedBy(14.dp),
                modifier = Modifier.fillMaxWidth().verticalScroll(rememberScrollState())
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("موقعیت افقی X (ستون): $col", color = CyberGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Slider(
                            value = col.toFloat(),
                            onValueChange = { col = it.toInt() },
                            valueRange = 1f..gridWidth.toFloat(),
                            steps = (gridWidth - 2).coerceAtLeast(0),
                            colors = SliderDefaults.colors(
                                thumbColor = CyberGold,
                                activeTrackColor = CyberGold
                            )
                        )
                    }
                    Column(modifier = Modifier.weight(1f)) {
                        Text("موقعیت عمودی Y (سطر): $row", color = CyberCyan, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                        Slider(
                            value = row.toFloat(),
                            onValueChange = { row = it.toInt() },
                            valueRange = 1f..gridLength.toFloat(),
                            steps = (gridLength - 2).coerceAtLeast(0),
                            colors = SliderDefaults.colors(
                                thumbColor = CyberCyan,
                                activeTrackColor = CyberCyan
                            )
                        )
                    }
                }

                OutlinedTextField(
                    value = label,
                    onValueChange = { label = it },
                    label = { Text("عنوان نشانگر / لیبل روی 3D", fontSize = 11.sp) },
                    placeholder = { Text("مثال: نقطه حفاری ۱، سیگنال طلا، حفره", fontSize = 11.sp, color = GrayText) },
                    singleLine = true,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberGold,
                        unfocusedBorderColor = CardBg,
                        focusedLabelColor = CyberGold,
                        unfocusedLabelColor = GrayText,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("annotation_label_input")
                )

                OutlinedTextField(
                    value = note,
                    onValueChange = { note = it },
                    label = { Text("توضیحات تکمیلی (اختیاری)", fontSize = 11.sp) },
                    placeholder = { Text("مثال: عمق ۲.۵ متر، شدت سیگنال ۴۵۰ درصد", fontSize = 11.sp, color = GrayText) },
                    maxLines = 3,
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = CyberCyan,
                        unfocusedBorderColor = CardBg,
                        focusedLabelColor = CyberCyan,
                        unfocusedLabelColor = GrayText,
                        focusedTextColor = Color.White,
                        unfocusedTextColor = Color.White
                    ),
                    modifier = Modifier.fillMaxWidth().testTag("annotation_note_input")
                )

                Column {
                    Text("رنگ پین نشانگر 3D:", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(6.dp))
                    Row(
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        colorList.forEach { (hex, name) ->
                            val c = parseHexColor(hex)
                            val isSel = selectedColor == hex
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(c)
                                    .border(
                                        width = if (isSel) 3.dp else 1.dp,
                                        color = if (isSel) Color.White else Color.Transparent,
                                        shape = CircleShape
                                    )
                                    .clickable { selectedColor = hex },
                                contentAlignment = Alignment.Center
                            ) {
                                if (isSel) {
                                    Icon(Icons.Default.Check, contentDescription = null, tint = Color.Black, modifier = Modifier.size(18.dp))
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val finalLabel = if (label.isBlank()) "نشانگر X:$col Y:$row" else label.trim()
                    onSave(col - 1, row - 1, finalLabel, note.trim(), selectedColor)
                    onDismiss()
                },
                colors = ButtonDefaults.buttonColors(containerColor = CyberGold)
            ) {
                Text("ثبت نشانگر", color = Color.Black, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("انصراف", color = GrayText)
            }
        }
    )
}

@Composable
fun MarkersListDialog(
    annotations: List<com.example.data.User3DAnnotation>,
    scanWidth: Int,
    scanLength: Int,
    scanData: List<Float>,
    onDismiss: () -> Unit,
    onFocusMarker: (col: Int, row: Int) -> Unit,
    onDeleteMarker: (id: String) -> Unit,
    onClearAll: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = SurfaceBg,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Bookmark, contentDescription = null, tint = CyberCyan)
                    Text("لیست نشانگرها و یادداشت‌های 3D", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Bold)
                }
                if (annotations.isNotEmpty()) {
                    TextButton(onClick = onClearAll) {
                        Text("پاک‌سازی همه", color = Color.Red, fontSize = 11.sp)
                    }
                }
            }
        },
        text = {
            if (annotations.isEmpty()) {
                Column(
                    modifier = Modifier.fillMaxWidth().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(Icons.Default.BookmarkBorder, contentDescription = null, tint = GrayText, modifier = Modifier.size(48.dp))
                    Spacer(modifier = Modifier.height(12.dp))
                    Text("هیچ نشانگری ثبت نشده است", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    Text("می‌توانید روی هر نقطه از مدل 3D کلیک کرده یا از دکمه «نشانگر +» استفاده کنید.", color = GrayText, fontSize = 11.sp, textAlign = TextAlign.Center)
                }
            } else {
                LazyColumn(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth().heightIn(max = 380.dp)
                ) {
                    items(annotations) { item ->
                        val itemColor = parseHexColor(item.colorHex)
                        val idx = item.row * scanWidth + item.col
                        val signal = scanData.getOrNull(idx) ?: 0f

                        Card(
                            colors = CardDefaults.cardColors(containerColor = CardBg),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, itemColor.copy(alpha = 0.4f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .clip(CircleShape)
                                        .background(itemColor)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Text(item.label, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)
                                        Surface(
                                            color = itemColor.copy(alpha = 0.15f),
                                            shape = RoundedCornerShape(4.dp)
                                        ) {
                                            Text(
                                                "X: ${item.col + 1}, Y: ${item.row + 1}",
                                                color = itemColor,
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    if (item.note.isNotBlank()) {
                                        Text(item.note, color = GrayText, fontSize = 10.sp)
                                    }
                                    Text("سیگنال: ${signal.toInt()} | تاریخ: ${item.createdAtFormatted()}", color = CyberCyan, fontSize = 9.sp)
                                }

                                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                    IconButton(
                                        onClick = {
                                            onFocusMarker(item.col, item.row)
                                            onDismiss()
                                        },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.CenterFocusWeak, contentDescription = "Focus", tint = CyberGold, modifier = Modifier.size(18.dp))
                                    }
                                    IconButton(
                                        onClick = { onDeleteMarker(item.id) },
                                        modifier = Modifier.size(32.dp)
                                    ) {
                                        Icon(Icons.Default.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(18.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onDismiss,
                colors = ButtonDefaults.buttonColors(containerColor = CardBg)
            ) {
                Text("بستن", color = Color.White)
            }
        }
    )
}

// ==========================================
// HARDWARE STATUS PANEL & BATTERY VISUALIZER COMPONENTS
// ==========================================
@Composable
fun BatteryVisualizer(
    percentage: Int?,
    modifier: Modifier = Modifier
) {
    val isConnected = percentage != null
    val pct = percentage ?: 0
    
    val color = when {
        !isConnected -> GrayText.copy(alpha = 0.4f)
        pct > 60 -> Color(0xFF10B981) // Emerald Green
        pct > 20 -> Color(0xFFF59E0B) // Amber Orange
        else -> Color(0xFFEF4444)     // Crimson Red
    }
    
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
        modifier = modifier
    ) {
        // Battery Body
        Box(
            modifier = Modifier
                .width(42.dp)
                .height(22.dp)
                .border(BorderStroke(1.5.dp, if (isConnected) color else GrayText.copy(alpha = 0.4f)), RoundedCornerShape(4.dp))
                .padding(2.dp)
        ) {
            // Fill level
            val fillWidthFraction = pct / 100f
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(fillWidthFraction)
                    .clip(RoundedCornerShape(1.5.dp))
                    .background(color)
            )
        }
        // Battery Cap
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(8.dp)
                .clip(RoundedCornerShape(topEnd = 1.dp, bottomEnd = 1.dp))
                .background(if (isConnected) color else GrayText.copy(alpha = 0.4f))
        )
    }
}

@Composable
fun ConnectionPulseIndicator(
    connectionState: ConnectionMode,
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val scale by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 1.6f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "scale"
    )
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.6f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "alpha"
    )
    
    val indicatorColor = when (connectionState) {
        ConnectionMode.DISCONNECTED -> Color.Red
        ConnectionMode.CONNECTING_USB, ConnectionMode.CONNECTING_BT -> Color.Yellow
        ConnectionMode.USB -> Color(0xFF10B981) // Green
        ConnectionMode.BLUETOOTH -> CyberCyan
        ConnectionMode.SIMULATOR -> Color(0xFFF59E0B) // Orange/Gold
    }
    
    Box(
        contentAlignment = Alignment.Center,
        modifier = modifier.size(24.dp)
    ) {
        if (connectionState != ConnectionMode.DISCONNECTED) {
            Box(
                modifier = Modifier
                    .size(16.dp)
                    .clip(CircleShape)
                    .background(indicatorColor.copy(alpha = alpha))
                    .drawBehind {
                        drawCircle(
                            color = indicatorColor.copy(alpha = alpha),
                            radius = size.minDimension / 2f * scale
                        )
                    }
            )
        }
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(indicatorColor)
        )
    }
}

@Composable
fun HardwareStatusPanel(
    viewModel: VisualizerViewModel,
    modifier: Modifier = Modifier
) {
    val sensorManager = viewModel.sensorManager
    val connectionState by sensorManager.connectionState.collectAsStateWithLifecycle()
    val statusText by sensorManager.tvStatus.collectAsStateWithLifecycle()
    val batteryPercentage by sensorManager.batteryPercentage.collectAsStateWithLifecycle()
    val activeSensorType by sensorManager.sensorType.collectAsStateWithLifecycle()
    val signalStrength by sensorManager.signalStrength.collectAsStateWithLifecycle()
    val metalType by sensorManager.metalType.collectAsStateWithLifecycle()
    val depth by sensorManager.depthMeters.collectAsStateWithLifecycle()
    val measurementUnit by viewModel.measurementUnit.collectAsStateWithLifecycle()

    val context = LocalContext.current

    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceBg),
        shape = RoundedCornerShape(16.dp),
        modifier = modifier
            .fillMaxWidth()
            .border(BorderStroke(1.dp, CardBg), RoundedCornerShape(16.dp))
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Header Row: Title & Active Sensor Type
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Sensors,
                        contentDescription = "Hardware status",
                        tint = CyberCyan,
                        modifier = Modifier.size(18.dp)
                    )
                    Column {
                        Text(
                            text = "سنسور و شبیه‌ساز Gold Radar",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Gold Radar Sensor Monitor",
                            color = GrayText,
                            fontSize = 10.sp
                        )
                    }
                }

                // Active Sensor Chip
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(6.dp))
                        .background(CardBg)
                        .padding(horizontal = 8.dp, vertical = 4.dp)
                ) {
                    Text(
                        text = when (activeSensorType) {
                            SensorType.GOLD_RADAR_X20 -> "سنسور فرکانسی X20"
                            SensorType.FMG3 -> "سنسور FMG-3"
                            SensorType.FLC100 -> "مگنتومتر FLC100"
                            SensorType.HMC5883L -> "قطب‌نمای HMC"
                            SensorType.QMC5883L -> "قطب‌نما QMC5883L"
                            SensorType.ADXL345 -> "شتاب‌سنج و شیب‌سنج ADXL345 📐"
                            SensorType.PHONE_INTERNAL -> "مگنتومتر داخلی گوشی 📱"
                        },
                        color = CyberCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Divider(color = CardBg.copy(alpha = 0.5f), thickness = 1.dp)

            // Main Details Row: Pulse, Status, Battery percentage
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Connection Pulse + Status Text
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    ConnectionPulseIndicator(connectionState = connectionState)
                    Column {
                        Text(
                            text = when (connectionState) {
                                ConnectionMode.DISCONNECTED -> "آفلاین (قطع ارتباط)"
                                ConnectionMode.CONNECTING_USB -> "در حال اتصال USB..."
                                ConnectionMode.CONNECTING_BT -> "در حال جفت‌سازی بلوتوث..."
                                ConnectionMode.USB -> "متصل به پورت USB (سیمی)"
                                ConnectionMode.BLUETOOTH -> "متصل به بلوتوث (بی‌سیم)"
                                ConnectionMode.SIMULATOR -> "شبیه‌ساز فعال (تست نرم‌افزار)"
                            },
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = statusText,
                            color = GrayText,
                            fontSize = 11.sp
                        )
                    }
                }

                // Battery Section
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (batteryPercentage != null) "$batteryPercentage%" else "--%",
                            color = if (batteryPercentage != null) Color.White else GrayText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Bold
                        )
                        BatteryVisualizer(percentage = batteryPercentage)
                    }
                    Text(
                        text = if (batteryPercentage != null) "شارژ باتری سنسور" else "باتری غیرفعال",
                        color = GrayText,
                        fontSize = 9.sp
                    )
                }
            }

            // Live telemetry strip if connected
            AnimatedVisibility(
                visible = connectionState != ConnectionMode.DISCONNECTED,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardBg.copy(alpha = 0.3f), RoundedCornerShape(10.dp))
                        .padding(10.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("دیتا لایو سنسور (Live Telemetry):", color = CyberCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("قدرت سیگنال: $signalStrength%", color = Color.White, fontSize = 10.sp)
                    }
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Display depth and metal type
                        Text(
                            text = "نوع هدف یافت شده: $metalType",
                            color = if (metalType.contains("طلا")) Color(0xFFFFD700) else Color.White,
                            fontSize = 11.sp
                        )
                        
                        val displayDepth = if (measurementUnit == "ft") depth * 3.28084 else depth
                        val depthUnitSymbol = if (measurementUnit == "ft") "ft" else "m"
                        Text(
                            text = String.format("عمق تقریبی: %.2f %s", displayDepth, depthUnitSymbol),
                            color = CyberCyan,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }
            }

            // Action Buttons Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Connect/Disconnect Button
                Button(
                    onClick = {
                        if (connectionState == ConnectionMode.DISCONNECTED) {
                            sensorManager.autoConnect()
                        } else {
                            sensorManager.disconnect()
                        }
                    },
                    modifier = Modifier.weight(1.3f),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (connectionState == ConnectionMode.DISCONNECTED) CyberCyan else Color(0xFFEF4444)
                    ),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(vertical = 10.dp)
                ) {
                    Icon(
                        imageVector = if (connectionState == ConnectionMode.DISCONNECTED) Icons.Default.PlayArrow else Icons.Default.Close,
                        contentDescription = "Connect Toggle",
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (connectionState == ConnectionMode.DISCONNECTED) "اتصال سخت‌افزار" else "قطع اتصال",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )
                }

                // Calibrate Button
                Button(
                    onClick = {
                        if (connectionState != ConnectionMode.DISCONNECTED) {
                            sensorManager.calibrate()
                        } else {
                            Toast.makeText(context, "ابتدا سنسور را متصل کنید", Toast.LENGTH_SHORT).show()
                        }
                    },
                    modifier = Modifier.weight(1f),
                    colors = ButtonDefaults.buttonColors(containerColor = CardBg),
                    shape = RoundedCornerShape(10.dp),
                    contentPadding = PaddingValues(vertical = 10.dp),
                    border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.3f))
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Calibrate",
                        modifier = Modifier.size(16.dp),
                        tint = CyberCyan
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "کالیبراسیون",
                        fontSize = 11.sp,
                        color = Color.White
                    )
                }

                // Simulator Button (if disconnected)
                if (connectionState == ConnectionMode.DISCONNECTED || connectionState == ConnectionMode.SIMULATOR) {
                    Button(
                        onClick = {
                            if (connectionState == ConnectionMode.SIMULATOR) {
                                sensorManager.disconnect()
                            } else {
                                sensorManager.startSimulator()
                            }
                        },
                        modifier = Modifier.weight(1f),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (connectionState == ConnectionMode.SIMULATOR) Color(0xFFF59E0B) else CardBg
                        ),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(vertical = 10.dp),
                        border = BorderStroke(1.dp, if (connectionState == ConnectionMode.SIMULATOR) Color.Transparent else GrayText.copy(alpha = 0.3f))
                    ) {
                        Icon(
                            imageVector = Icons.Default.FlashOn,
                            contentDescription = "Simulator",
                            modifier = Modifier.size(16.dp),
                            tint = if (connectionState == ConnectionMode.SIMULATOR) Color.White else GrayText
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (connectionState == ConnectionMode.SIMULATOR) "توقف شبیه‌ساز" else "شبیه‌ساز",
                            fontSize = 11.sp,
                            color = Color.White
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// TAB 3: REAL-TIME SCOPE & GAUGES (LIVE TRACKER)
// ==========================================
@Composable
fun TrackerScreen(viewModel: VisualizerViewModel) {
    val sensorManager = viewModel.sensorManager
    val connectionState by sensorManager.connectionState.collectAsStateWithLifecycle()
    val statusText by sensorManager.tvStatus.collectAsStateWithLifecycle()
    val measurementUnit by viewModel.measurementUnit.collectAsStateWithLifecycle()

    val adc by sensorManager.adcValue.collectAsStateWithLifecycle()
    val phase by sensorManager.phaseShift.collectAsStateWithLifecycle()
    val signalStrength by sensorManager.signalStrength.collectAsStateWithLifecycle()
    val depth by sensorManager.depthMeters.collectAsStateWithLifecycle()
    val metalType by sensorManager.metalType.collectAsStateWithLifecycle()

    // Capture rolling data points for Live Wave scope
    val wavePoints = remember { mutableStateListOf<Float>() }

    LaunchedEffect(adc) {
        wavePoints.add(adc.toFloat())
        if (wavePoints.size > 40) {
            wavePoints.removeAt(0)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Live Tracker Header
        Column(modifier = Modifier.fillMaxWidth(), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                "رادار ردیاب زنده (Continuous Target Analyzer)",
                color = CyberRed,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center
            )
            Text(
                "اسکن پیوسته خاک و آنالیز فاز فلزات گرانبها",
                color = GrayText,
                fontSize = 11.sp,
                textAlign = TextAlign.Center
            )
        }

        // 0. REAL-TIME 3D TOPOGRAPHICAL SURFACE MAP
        RealTime3DTopographicalMap(
            viewModel = viewModel,
            modifier = Modifier.fillMaxWidth()
        )

        // 1. SCI-FI CIRCULAR RADAR SCANNER
        SciFiRadarSweep(
            signalStrength = signalStrength,
            metalType = metalType,
            depth = depth,
            measurementUnit = measurementUnit,
            modifier = Modifier.fillMaxWidth()
        )

        // 1. LIVE SCOPE CANVAS CHART (Rolling wave)
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceBg),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .height(180.dp)
                .border(BorderStroke(1.dp, CardBg), RoundedCornerShape(16.dp))
        ) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("نمودار زنده پالس سنسور (Live Sensor Waveform)", color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Draw reference grid lines
                    for (i in 1..4) {
                        val gridY = (h / 5) * i
                        drawLine(
                            color = Color(0x1A00E5FF),
                            start = Offset(0f, gridY),
                            end = Offset(w, gridY),
                            strokeWidth = 1f
                        )
                    }

                    if (wavePoints.size > 1) {
                        val maxWave = 1024f
                        val minWave = 0f
                        val waveRange = maxWave - minWave

                        val xSpacing = w / 39f
                        val path = Path()

                        wavePoints.forEachIndexed { idx, value ->
                            // Map value to Y coordinate
                            val normY = (value - minWave) / waveRange
                            val yCoord = h - (normY * h).coerceIn(0f, h)
                            val xCoord = idx * xSpacing

                            if (idx == 0) {
                                path.moveTo(xCoord, yCoord)
                            } else {
                                path.lineTo(xCoord, yCoord)
                            }
                        }

                        // Draw continuous wave path with glowing shader
                        drawPath(
                            path = path,
                            color = CyberCyan,
                            style = Stroke(width = 3f)
                        )
                        
                        // Draw horizontal baseline
                        drawLine(
                            color = Color(0x33FFD700),
                            start = Offset(0f, h - (200f/1024f)*h),
                            end = Offset(w, h - (200f/1024f)*h),
                            strokeWidth = 1.5f,
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(10f, 10f))
                        )
                    }
                }
            }
        }

        // 2. LARGE DIGITAL TARGET CLASSIFIER
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceBg),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(20.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text("نوع فلز شناسایی شده (Metal Discrimination)", color = GrayText, fontSize = 11.sp)
                
                // Big neon indicator
                Text(
                    text = metalType,
                    color = when {
                        metalType.contains("طلا") -> CyberGold
                        metalType.contains("نقره") -> CyberCyan
                        metalType.contains("آهن") -> Color(0xFFC248FF)
                        else -> Color.White
                    },
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Black,
                    letterSpacing = 1.sp
                )

                // Linear bar for Signal Strength
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("شدت سیگنال طلا‌یاب", color = GrayText, fontSize = 11.sp)
                        Text("$signalStrength%", color = CyberGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    LinearProgressIndicator(
                        progress = { signalStrength / 100f },
                        color = when {
                            metalType.contains("طلا") -> CyberGold
                            metalType.contains("نقره") -> CyberCyan
                            else -> CyberRed
                        },
                        trackColor = CardBg,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(14.dp)
                            .clip(RoundedCornerShape(8.dp))
                    )
                }
            }
        }

        // 3. UNDERGROUND DEPTH TRACKER (Industrial drill vertical visualization)
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceBg),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Row(
                modifier = Modifier.padding(16.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Vertical Depth Shaft drawing
                Box(
                    modifier = Modifier
                        .width(60.dp)
                        .height(200.dp)
                        .background(CardBg, RoundedCornerShape(8.dp))
                        .padding(vertical = 12.dp),
                    contentAlignment = Alignment.TopCenter
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height
                        
                        // Draw 5m grid ticks
                        for (i in 0..20 step 5) {
                            val tickY = (h / 20f) * i
                            drawLine(
                                color = Color(0x3300E5FF),
                                start = Offset(0f, tickY),
                                end = Offset(w * 0.4f, tickY),
                                strokeWidth = 2f
                            )
                        }

                        // Draw drill indicator
                        val drillY = (h / 20f) * depth.toFloat()
                        
                        // Draw red indicator line for depth
                        drawLine(
                            color = CyberRed,
                            start = Offset(0f, drillY),
                            end = Offset(w, drillY),
                            strokeWidth = 3f
                        )
                        
                        drawCircle(
                            color = CyberRed,
                            radius = 6f,
                            center = Offset(w / 2f, drillY)
                        )
                    }
                }

                // Depth numeric descriptions
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text("📏 برآورد عمق سنسور (Estimated Target Depth)", color = CyberCyan, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(
                        String.format("%.2f متر", depth),
                        color = Color.White,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        "بر اساس تضعیف میدان مغناطیسی خاک و نوع خاک تنظیم شده. عمق نهایی طلا ممکن است تحت تاثیر هدایت الکتریکی خاک تغییر کند.",
                        color = GrayText,
                        fontSize = 10.sp,
                        lineHeight = 14.sp
                    )

                    // Calibrate / Connection control button
                    Button(
                        onClick = { viewModel.sensorManager.calibrate() },
                        colors = ButtonDefaults.buttonColors(containerColor = CardBg),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Tune, contentDescription = null, tint = CyberGold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("کالیبراسیون و حذف نویز خاک", color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        }
        
        // Manual simulator controls if simulated mode
        if (connectionState == ConnectionMode.SIMULATOR) {
            Card(
                colors = CardDefaults.cardColors(containerColor = CardBg.copy(alpha = 0.5f)),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("🛠️ کنترل‌های تست شبیه‌ساز (Simulator Override)", color = CyberGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { sensorManager.setSimulatedReading(850, 48) }, // Gold
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E3D52)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("شبیه‌سازی طلا", fontSize = 11.sp)
                        }
                        Button(
                            onClick = { sensorManager.setSimulatedReading(380, -28) }, // Iron
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E3D52)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("شبیه‌سازی آهن", fontSize = 11.sp)
                        }
                        Button(
                            onClick = { sensorManager.setSimulatedReading(110, 5) }, // Cavity / Void
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2E3D52)),
                            modifier = Modifier.weight(1f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text("شبیه‌سازی غار", fontSize = 11.sp)
                        }
                    }
                }
            }
        }
        
        Spacer(modifier = Modifier.height(20.dp))
    }
}

// ==========================================
// TAB 4: SCAN HISTORY & PRELOADED TEMPLATES
// ==========================================
@Composable
fun HistoryScreen(
    viewModel: VisualizerViewModel,
    onLoadScan: () -> Unit
) {
    val savedScans by viewModel.savedScans.collectAsStateWithLifecycle()
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "تاریخچه فایل‌ها و تصاویر اسکن زمین",
                color = CyberGold,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )
            Text(
                "بارگذاری اسکن‌های زمین در موتور سه‌بعدی و قالب‌های پیش‌فرض",
                color = GrayText,
                fontSize = 11.sp
            )
        }

        val (userScans, demoScans) = remember(savedScans) {
            savedScans.partition { scan ->
                !scan.name.contains("پیش‌فرض") && 
                !scan.name.contains("گنجینه") && 
                !scan.name.contains("غار عمیق") && 
                !scan.name.contains("مقبره") && 
                !scan.name.contains("خط لوله")
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // User Scans Section
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "اسکن‌های اختصاصی ثبت‌شده شما (${userScans.size})",
                        color = CyberGold,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    if (userScans.isNotEmpty()) {
                        Text(
                            text = "ذخیره‌شده در دیتابیس دستگاه",
                            color = GrayText,
                            fontSize = 10.sp
                        )
                    }
                }

                if (userScans.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = SurfaceBg),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                "هنوز هیچ اسکن جدیدی توسط شما ثبت نشده است",
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "پس از اتمام هر اسکن در تب اسکن (Scan)، فایل پردازش شده به صورت خودکار در این قسمت ذخیره و نمایش داده خواهد شد.",
                                color = GrayText,
                                fontSize = 10.sp,
                                textAlign = TextAlign.Center
                            )
                        }
                    }
                } else {
                    userScans.forEach { scan ->
                        ScanHistoryCard(
                            scan = scan,
                            isUserScan = true,
                            viewModel = viewModel,
                            onLoadScan = onLoadScan,
                            context = context
                        )
                    }
                }
            }

            // Demo / Standard Templates Section
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = "قالب‌ها و سناریوهای استاندارد پیش‌فرض (${if (demoScans.isEmpty()) savedScans.size else demoScans.size})",
                    color = CyberCyan,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Bold
                )

                val displayDemoList = if (demoScans.isEmpty()) savedScans else demoScans
                displayDemoList.forEach { scan ->
                    ScanHistoryCard(
                        scan = scan,
                        isUserScan = false,
                        viewModel = viewModel,
                        onLoadScan = onLoadScan,
                        context = context
                    )
                }
            }
        }
    }
}

@Composable
fun ScanHistoryCard(
    scan: ScanRecord,
    isUserScan: Boolean,
    viewModel: VisualizerViewModel,
    onLoadScan: () -> Unit,
    context: android.content.Context
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = SurfaceBg),
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier.fillMaxWidth(),
        onClick = {
            viewModel.loadScan(scan)
            onLoadScan()
        }
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Text(
                        scan.name,
                        color = if (isUserScan) CyberGold else Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Surface(
                        color = if (isUserScan) CyberGold.copy(alpha = 0.2f) else CyberCyan.copy(alpha = 0.15f),
                        shape = RoundedCornerShape(4.dp)
                    ) {
                        Text(
                            text = if (isUserScan) "اسکن اختصاصی شما" else "قالب پیش‌فرض",
                            color = if (isUserScan) CyberGold else CyberCyan,
                            fontSize = 8.sp,
                            fontWeight = FontWeight.Bold,
                            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp)
                        )
                    }
                }
                Text(
                    "ابعاد شبکه: ${scan.width}×${scan.length} | نوع خاک: ${scan.soilType}",
                    color = GrayText,
                    fontSize = 11.sp
                )
                if (scan.notes.isNotEmpty()) {
                    Text(
                        scan.notes,
                        color = Color.White.copy(alpha = 0.6f),
                        fontSize = 10.sp,
                        maxLines = 1
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Playback button
                IconButton(
                    onClick = {
                        viewModel.loadScan(scan)
                        viewModel.togglePlaybackMode()
                        onLoadScan()
                    },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = CyberGold.copy(alpha = 0.2f))
                ) {
                    Icon(
                        Icons.Default.PlayCircle,
                        contentDescription = "Replay",
                        tint = CyberGold,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Share menu
                var showShareMenu by remember { mutableStateOf(false) }
                Box {
                    IconButton(
                        onClick = { showShareMenu = true },
                        colors = IconButtonDefaults.iconButtonColors(containerColor = CardBg)
                    ) {
                        Icon(
                            Icons.Default.Share,
                            contentDescription = "Share",
                            tint = CyberGold,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = showShareMenu,
                        onDismissRequest = { showShareMenu = false },
                        modifier = Modifier.background(CardBg)
                    ) {
                        DropdownMenuItem(
                            text = { Text("📤 اشتراک گزارش متنی", color = Color.White, fontSize = 11.sp) },
                            onClick = {
                                showShareMenu = false
                                shareScanRecord(context, scan, "summary")
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("📊 اشتراک فایل CSV (Excel)", color = Color.White, fontSize = 11.sp) },
                            onClick = {
                                showShareMenu = false
                                shareScanRecord(context, scan, "csv")
                            }
                        )
                    }
                }

                // Delete button
                IconButton(
                    onClick = { viewModel.deleteScan(scan.id) },
                    colors = IconButtonDefaults.iconButtonColors(containerColor = CardBg)
                ) {
                    Icon(
                        Icons.Default.Delete,
                        contentDescription = "Delete",
                        tint = Color.Red,
                        modifier = Modifier.size(18.dp)
                    )
                }

                Icon(Icons.Default.ChevronRight, contentDescription = "Load", tint = CyberCyan)
            }
        }
    }
}

@Composable
fun ThreeDWebViewVisualizer(
    scan: ScanRecord,
    selectedNodeIndex: Int?,
    resetTrigger: Int,
    cameraPresetTrigger: Pair<String, Int>?,
    renderStyle: String,
    isRgbAnalysis: Boolean,
    zScale: Float,
    colorPalette: String,
    userAnnotations: List<com.example.data.User3DAnnotation> = emptyList(),
    onNodeSelected: (Int) -> Unit,
    onRenderStyleChanged: (String) -> Unit,
    onRgbAnalysisChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    var isLoaded by remember { mutableStateOf(false) }

    fun sendUserMarkersToWebView(view: WebView?) {
        val jsonArray = userAnnotations.joinToString(prefix = "[", postfix = "]") { anno ->
            val safeLabel = anno.label.replace("'", "\\'").replace("\"", "\\\"")
            val safeNote = anno.note.replace("'", "\\'").replace("\"", "\\\"")
            "{\"id\":\"${anno.id}\",\"col\":${anno.col},\"row\":${anno.row},\"label\":\"$safeLabel\",\"note\":\"$safeNote\",\"colorHex\":\"${anno.colorHex}\"}"
        }
        view?.evaluateJavascript("setUserMarkers('$jsonArray')", null)
    }

    // Synchronize 3D Markers / Annotations to Three.js scene
    LaunchedEffect(userAnnotations, isLoaded) {
        if (isLoaded) {
            sendUserMarkersToWebView(webViewRef.value)
        }
    }

    // Update 3D Scan Data whenever scan changes or page finished loading
    LaunchedEffect(scan, isLoaded) {
        if (isLoaded) {
            webViewRef.value?.evaluateJavascript("loadScanData(${scan.width}, ${scan.length}, '${scan.gridDataJson}')", null)
            webViewRef.value?.evaluateJavascript("setRenderStyle(\"$renderStyle\")", null)
            webViewRef.value?.evaluateJavascript("setRgbAnalysis($isRgbAnalysis)", null)
            webViewRef.value?.evaluateJavascript("setZScale($zScale)", null)
            webViewRef.value?.evaluateJavascript("setColorPalette(\"$colorPalette\")", null)
        }
    }

    // Synchronize Palette from Native UI to WebGL
    LaunchedEffect(colorPalette, isLoaded) {
        if (isLoaded) {
            webViewRef.value?.evaluateJavascript("setColorPalette(\"$colorPalette\")", null)
        }
    }

    // Synchronize Node selection from Native UI to WebGL
    LaunchedEffect(selectedNodeIndex, isLoaded) {
        if (isLoaded && selectedNodeIndex != null) {
            webViewRef.value?.evaluateJavascript("selectNodeNatively($selectedNodeIndex)", null)
        }
    }

    // Synchronize rendering style from Native UI to WebGL
    LaunchedEffect(renderStyle, isLoaded) {
        if (isLoaded) {
            webViewRef.value?.evaluateJavascript("setRenderStyle(\"$renderStyle\")", null)
        }
    }

    // Synchronize RGB Analysis mode from Native UI to WebGL
    LaunchedEffect(isRgbAnalysis, isLoaded) {
        if (isLoaded) {
            webViewRef.value?.evaluateJavascript("setRgbAnalysis($isRgbAnalysis)", null)
        }
    }

    // Synchronize Z scale from Native UI to WebGL
    LaunchedEffect(zScale, isLoaded) {
        if (isLoaded) {
            webViewRef.value?.evaluateJavascript("setZScale($zScale)", null)
        }
    }

    // Trigger camera reset inside Three.js scene
    LaunchedEffect(resetTrigger, isLoaded) {
        if (isLoaded && resetTrigger > 0) {
            webViewRef.value?.evaluateJavascript("resetCamera()", null)
        }
    }

    // Trigger camera preset inside Three.js scene
    LaunchedEffect(cameraPresetTrigger, isLoaded) {
        if (isLoaded && cameraPresetTrigger != null) {
            webViewRef.value?.evaluateJavascript("setCameraPreset('${cameraPresetTrigger.first}')", null)
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                }
                
                // Set transparent background for a premium integrated look
                setBackgroundColor(0)
                
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        isLoaded = true
                        // Initial load
                        view?.evaluateJavascript("loadScanData(${scan.width}, ${scan.length}, '${scan.gridDataJson}')", null)
                        view?.evaluateJavascript("setRenderStyle(\"$renderStyle\")", null)
                        view?.evaluateJavascript("setRgbAnalysis($isRgbAnalysis)", null)
                        view?.evaluateJavascript("setZScale($zScale)", null)
                        view?.evaluateJavascript("setColorPalette(\"$colorPalette\")", null)
                        sendUserMarkersToWebView(view)
                        selectedNodeIndex?.let {
                            view?.evaluateJavascript("selectNodeNatively($it)", null)
                        }
                    }
                }

                // Add Javascript bridge to communicate Node taps back to Compose ViewModel
                addJavascriptInterface(object {
                    @android.webkit.JavascriptInterface
                    fun onNodeSelected(index: Int) {
                        // Dispatch to main thread
                        post {
                            onNodeSelected(index)
                        }
                    }

                    @android.webkit.JavascriptInterface
                    fun setRenderStyle(style: String) {
                        // Dispatch to main thread
                        post {
                            onRenderStyleChanged(style)
                        }
                    }

                    @android.webkit.JavascriptInterface
                    fun setRgbAnalysis(enabled: Boolean) {
                        // Dispatch to main thread
                        post {
                            onRgbAnalysisChanged(enabled)
                        }
                    }
                }, "Android")

                loadUrl("file:///android_asset/three_visualizer.html")
                webViewRef.value = this
            }
        },
        update = { webView ->
            // Re-trigger load if scan record changes in background update
            if (isLoaded) {
                webView.evaluateJavascript("loadScanData(${scan.width}, ${scan.length}, '${scan.gridDataJson}')", null)
                webView.evaluateJavascript("setRenderStyle(\"$renderStyle\")", null)
                webView.evaluateJavascript("setRgbAnalysis($isRgbAnalysis)", null)
                webView.evaluateJavascript("setZScale($zScale)", null)
                webView.evaluateJavascript("setColorPalette(\"$colorPalette\")", null)
                sendUserMarkersToWebView(webView)
                selectedNodeIndex?.let {
                    webView.evaluateJavascript("selectNodeNatively($it)", null)
                }
            }
        },
        modifier = modifier
    )
}

@Composable
fun D3WebViewVisualizer(
    scan: ScanRecord,
    selectedNodeIndex: Int?,
    resetTrigger: Int,
    cameraPresetTrigger: Pair<String, Int>?,
    renderStyle: String,
    isRgbAnalysis: Boolean,
    zScale: Float,
    colorPalette: String,
    onNodeSelected: (Int) -> Unit,
    onRenderStyleChanged: (String) -> Unit,
    onRgbAnalysisChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val webViewRef = remember { mutableStateOf<WebView?>(null) }
    var isLoaded by remember { mutableStateOf(false) }

    // Synchronize updates to WebView when Compose state variables change
    LaunchedEffect(scan, isLoaded) {
        if (isLoaded) {
            webViewRef.value?.evaluateJavascript("loadScanData(${scan.width}, ${scan.length}, '${scan.gridDataJson}')", null)
            webViewRef.value?.evaluateJavascript("setRenderStyle(\"$renderStyle\")", null)
            webViewRef.value?.evaluateJavascript("setRgbAnalysis($isRgbAnalysis)", null)
            webViewRef.value?.evaluateJavascript("setZScale($zScale)", null)
            webViewRef.value?.evaluateJavascript("setColorPalette(\"$colorPalette\")", null)
        }
    }

    // Synchronize Palette from Native UI to WebGL
    LaunchedEffect(colorPalette, isLoaded) {
        if (isLoaded) {
            webViewRef.value?.evaluateJavascript("setColorPalette(\"$colorPalette\")", null)
        }
    }

    LaunchedEffect(selectedNodeIndex, isLoaded) {
        if (isLoaded && selectedNodeIndex != null) {
            webViewRef.value?.evaluateJavascript("selectNodeNatively($selectedNodeIndex)", null)
        }
    }

    LaunchedEffect(renderStyle, isLoaded) {
        if (isLoaded) {
            webViewRef.value?.evaluateJavascript("setRenderStyle(\"$renderStyle\")", null)
        }
    }

    LaunchedEffect(isRgbAnalysis, isLoaded) {
        if (isLoaded) {
            webViewRef.value?.evaluateJavascript("setRgbAnalysis($isRgbAnalysis)", null)
        }
    }

    LaunchedEffect(zScale, isLoaded) {
        if (isLoaded) {
            webViewRef.value?.evaluateJavascript("setZScale($zScale)", null)
        }
    }

    LaunchedEffect(resetTrigger, isLoaded) {
        if (isLoaded && resetTrigger > 0) {
            webViewRef.value?.evaluateJavascript("resetCamera()", null)
        }
    }

    LaunchedEffect(cameraPresetTrigger, isLoaded) {
        if (isLoaded && cameraPresetTrigger != null) {
            webViewRef.value?.evaluateJavascript("setCameraPreset('${cameraPresetTrigger.first}')", null)
        }
    }

    AndroidView(
        factory = { ctx ->
            WebView(ctx).apply {
                settings.apply {
                    javaScriptEnabled = true
                    domStorageEnabled = true
                    allowFileAccess = true
                    allowContentAccess = true
                    useWideViewPort = true
                    loadWithOverviewMode = true
                }
                
                // Keep the webview background transparent to match the app's dark scifi background
                setBackgroundColor(0)
                
                webViewClient = object : WebViewClient() {
                    override fun onPageFinished(view: WebView?, url: String?) {
                        super.onPageFinished(view, url)
                        isLoaded = true
                        // Initial load
                        view?.evaluateJavascript("loadScanData(${scan.width}, ${scan.length}, '${scan.gridDataJson}')", null)
                        view?.evaluateJavascript("setRenderStyle(\"$renderStyle\")", null)
                        view?.evaluateJavascript("setRgbAnalysis($isRgbAnalysis)", null)
                        view?.evaluateJavascript("setZScale($zScale)", null)
                        view?.evaluateJavascript("setColorPalette(\"$colorPalette\")", null)
                        selectedNodeIndex?.let {
                            view?.evaluateJavascript("selectNodeNatively($it)", null)
                        }
                    }
                }

                // Add Javascript bridge to communicate Node taps back to Compose ViewModel
                addJavascriptInterface(object {
                    @android.webkit.JavascriptInterface
                    fun onNodeSelected(index: Int) {
                        // Dispatch to main thread
                        post {
                            onNodeSelected(index)
                        }
                    }

                    @android.webkit.JavascriptInterface
                    fun setRenderStyle(style: String) {
                        // Dispatch to main thread
                        post {
                            onRenderStyleChanged(style)
                        }
                    }

                    @android.webkit.JavascriptInterface
                    fun setRgbAnalysis(enabled: Boolean) {
                        // Dispatch to main thread
                        post {
                            onRgbAnalysisChanged(enabled)
                        }
                    }
                }, "Android")

                loadUrl("file:///android_asset/d3_visualizer.html")
                webViewRef.value = this
            }
        },
        update = { webView ->
            // Re-trigger load if scan record changes in background update
            if (isLoaded) {
                webView.evaluateJavascript("loadScanData(${scan.width}, ${scan.length}, '${scan.gridDataJson}')", null)
                webView.evaluateJavascript("setRenderStyle(\"$renderStyle\")", null)
                webView.evaluateJavascript("setRgbAnalysis($isRgbAnalysis)", null)
                webView.evaluateJavascript("setZScale($zScale)", null)
                webView.evaluateJavascript("setColorPalette(\"$colorPalette\")", null)
                selectedNodeIndex?.let {
                    webView.evaluateJavascript("selectNodeNatively($it)", null)
                }
            }
        },
        modifier = modifier
    )
}

fun exportScanRecord(context: android.content.Context, scan: ScanRecord, format: String) {
    val exportFormat = if (format.lowercase() == "json") {
        com.example.service.ScanLoggingService.ExportFormat.JSON
    } else {
        com.example.service.ScanLoggingService.ExportFormat.CSV
    }
    val content = if (exportFormat == com.example.service.ScanLoggingService.ExportFormat.JSON) {
        com.example.service.ScanLoggingService.exportScanRecordToJson(scan)
    } else {
        com.example.service.ScanLoggingService.exportScanRecordToCsv(scan)
    }
    com.example.service.ScanLoggingService.saveAndShare(context, scan.name, content, exportFormat)
}

fun shareScanRecord(context: android.content.Context, scan: ScanRecord, shareMode: String = "summary") {
    try {
        val dataList = scan.getGridData()
        val maxVal = dataList.maxOrNull() ?: 0f
        val minVal = dataList.minOrNull() ?: 0f
        val avgVal = if (dataList.isNotEmpty()) dataList.average() else 0.0
        val dateStr = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(Date(scan.timestamp))

        val textToShare = when (shareMode) {
            "csv" -> {
                val sb = StringBuilder()
                sb.append("# OKM 3D VISUALIZER SCAN DATA EXPORT\n")
                sb.append("# Name: ${scan.name}\n# Date: $dateStr\n# Grid: ${scan.width}x${scan.length}\n# Soil: ${scan.soilType}\n\n")
                sb.append("Index,X,Y,Value\n")
                for (y in 0 until scan.length) {
                    for (x in 0 until scan.width) {
                        val idx = y * scan.width + x
                        sb.append("$idx,$x,$y,${dataList.getOrNull(idx) ?: 0f}\n")
                    }
                }
                sb.toString()
            }
            "json" -> {
                """
                {
                  "name": "${scan.name.replace("\"", "\\\"")}",
                  "date": "$dateStr",
                  "width": ${scan.width},
                  "length": ${scan.length},
                  "soilType": "${scan.soilType.replace("\"", "\\\"")}",
                  "scanPattern": "${scan.scanPattern.replace("\"", "\\\"")}",
                  "notes": "${scan.notes.replace("\"", "\\\"")}",
                  "data": [${dataList.joinToString(",")}]
                }
                """.trimIndent()
            }
            else -> {
                """
                📊 گزارش تحلیل اسکن سه‌بعدی زمین - OKM 3D VISUALIZER
                --------------------------------------------------
                📌 نام اسکن: ${scan.name}
                📅 تاریخ ثبت: $dateStr
                📐 ابعاد شبکه: ${scan.width} ستون × ${scan.length} سطر (${scan.width * scan.length} نقطه)
                🌍 نوع خاک: ${scan.soilType}
                🔄 الگوی اسکن: ${scan.scanPattern}
                
                📈 شاخص‌های سیگنال مگنتومتر:
                • بیشترین پیک آنومالی (Peak): ${maxVal.toInt()} LSB
                • کمترین فرورفتگی (Cavity): ${minVal.toInt()} LSB
                • میانگین نویز خاک: ${String.format("%.1f", avgVal)} LSB
                
                📝 یادداشت اپراتور:
                ${if (scan.notes.isNotBlank()) scan.notes else "بدون یادداشت ثبت شده"}
                
                📱 اشتراک‌گذاری شده از اپلیکیشن OKM 3D Visualizer Pro
                --------------------------------------------------
                """.trimIndent()
            }
        }

        // Copy text to clipboard
        val clipboard = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
        val clip = android.content.ClipData.newPlainText("Scan Data", textToShare)
        clipboard.setPrimaryClip(clip)

        // Launch Share Intent
        val shareIntent = android.content.Intent(android.content.Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(android.content.Intent.EXTRA_SUBJECT, "اشتراک‌گذاری اسکن: ${scan.name}")
            putExtra(android.content.Intent.EXTRA_TEXT, textToShare)
        }

        context.startActivity(android.content.Intent.createChooser(shareIntent, "اشتراک‌گذاری اسکن (${scan.name})"))
        Toast.makeText(context, "کپی در حافظه شد و منوی اشتراک‌گذاری باز گردید", Toast.LENGTH_SHORT).show()
    } catch (e: Exception) {
        Toast.makeText(context, "خطا در اشتراک‌گذاری: ${e.localizedMessage}", Toast.LENGTH_SHORT).show()
    }
}

// ==========================================
// DETECTOR AMBIENT PARTICLE BACKGROUND
// ==========================================
data class SpaceParticle(
    var x: Float,
    var y: Float,
    var vx: Float,
    var vy: Float,
    var radius: Float,
    var alpha: Float,
    val color: Color
)

@Composable
fun AmbientParticleBackground(modifier: Modifier = Modifier) {
    val particles = remember { mutableStateListOf<SpaceParticle>() }
    val density = androidx.compose.ui.platform.LocalDensity.current
    
    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val width = with(density) { maxWidth.toPx() }
        val height = with(density) { maxHeight.toPx() }
        
        LaunchedEffect(width, height) {
            if (width > 0 && height > 0 && particles.isEmpty()) {
                val goldColor = Color(0xFFFFD700)
                val cyanColor = Color(0xFF00E5FF)
                for (i in 0..30) {
                    particles.add(
                        SpaceParticle(
                            x = (Math.random() * width).toFloat(),
                            y = (Math.random() * height).toFloat(),
                            vx = ((Math.random() - 0.5) * 0.3).toFloat(),
                            vy = ((Math.random() - 0.5) * 0.3).toFloat(),
                            radius = (1.5f + Math.random() * 3.0f).toFloat(),
                            alpha = (0.08f + Math.random() * 0.35f).toFloat(),
                            color = if (Math.random() > 0.65) goldColor else cyanColor
                        )
                    )
                }
            }
        }
        
        LaunchedEffect(Unit) {
            while (true) {
                withFrameMillis {
                    for (i in particles.indices) {
                        val p = particles[i]
                        p.x += p.vx
                        p.y += p.vy
                        
                        if (p.x < 0) p.x = width
                        if (p.x > width) p.x = 0f
                        if (p.y < 0) p.y = height
                        if (p.y > height) p.y = 0f
                    }
                }
            }
        }
        
        Canvas(modifier = Modifier.fillMaxSize()) {
            particles.forEach { p ->
                drawCircle(
                    color = p.color.copy(alpha = p.alpha),
                    radius = p.radius,
                    center = Offset(p.x, p.y)
                )
            }
        }
    }
}

// ==========================================
// RADAR SCANNER SWEEP SCREEN (LIVE ANALYZER)
// ==========================================
@Composable
fun SciFiRadarSweep(
    signalStrength: Int,
    metalType: String,
    depth: Double,
    measurementUnit: String = "m",
    modifier: Modifier = Modifier
) {
    val infiniteTransition = rememberInfiniteTransition(label = "RadarSweep")
    
    val rotationAngle by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(4000, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "RadarAngle"
    )
    
    val blipPulse by infiniteTransition.animateFloat(
        initialValue = 0.3f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "BlipPulse"
    )
    
    val themeColor = when {
        metalType.contains("طلا") || metalType.contains("✨") -> Color(0xFFFFD700)
        metalType.contains("نقره") || metalType.contains("💎") -> Color(0xFF00E5FF)
        metalType.contains("آهن") || metalType.contains("🧲") -> Color(0xFFFF3D00)
        else -> Color(0xFF4CAF50)
    }
    
    val isFt = measurementUnit == "ft"
    val displayDepth = if (isFt) depth * 3.28084 else depth
    val unitSymbol = if (isFt) "ft" else "m"

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .background(Color(0xFF0C0E14), RoundedCornerShape(24.dp))
            .border(BorderStroke(1.5.dp, Color(0x3300E5FF)), RoundedCornerShape(24.dp))
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val center = Offset(size.width / 2, size.height / 2)
            val maxRadius = size.width.coerceAtMost(size.height) / 2 * 0.9f
            
            // 1. Compass housing
            drawCircle(
                color = Color(0x1F00E5FF),
                radius = maxRadius + 10f,
                center = center,
                style = Stroke(width = 2f)
            )
            
            for (angleDeg in 0 until 360 step 30) {
                val angleRad = Math.toRadians(angleDeg.toDouble())
                val startX = center.x + (maxRadius + 4f) * Math.cos(angleRad).toFloat()
                val startY = center.y + (maxRadius + 4f) * Math.sin(angleRad).toFloat()
                val endX = center.x + (maxRadius + 12f) * Math.cos(angleRad).toFloat()
                val endY = center.y + (maxRadius + 12f) * Math.sin(angleRad).toFloat()
                
                drawLine(
                    color = if (angleDeg % 90 == 0) Color(0xFF00E5FF) else Color(0x5500E5FF),
                    start = Offset(startX, startY),
                    end = Offset(endX, endY),
                    strokeWidth = if (angleDeg % 90 == 0) 3f else 1.5f
                )
            }
            
            // 2. Concentric rings
            val ringCount = 4
            for (i in 1..ringCount) {
                val r = (maxRadius / ringCount) * i
                drawCircle(
                    color = Color(0x1A00E5FF),
                    radius = r,
                    center = center,
                    style = Stroke(
                        width = 1f,
                        pathEffect = if (i < ringCount) androidx.compose.ui.graphics.PathEffect.dashPathEffect(floatArrayOf(8f, 6f)) else null
                    )
                )
            }
            
            // 3. Grid axes
            drawLine(
                color = Color(0x2200E5FF),
                start = Offset(center.x - maxRadius, center.y),
                end = Offset(center.x + maxRadius, center.y),
                strokeWidth = 1.5f
            )
            drawLine(
                color = Color(0x2200E5FF),
                start = Offset(center.x, center.y - maxRadius),
                end = Offset(center.x, center.y + maxRadius),
                strokeWidth = 1.5f
            )
            
            // 4. Sweep arm & trail wedge
            val sweepRad = Math.toRadians(rotationAngle.toDouble())
            val endX = center.x + maxRadius * Math.cos(sweepRad).toFloat()
            val endY = center.y + maxRadius * Math.sin(sweepRad).toFloat()
            
            drawLine(
                color = Color(0xFF00E5FF),
                start = center,
                end = Offset(endX, endY),
                strokeWidth = 2.5f
            )
            
            val path = Path().apply {
                moveTo(center.x, center.y)
                for (offset in 0..45 step 5) {
                    val trailRad = Math.toRadians((rotationAngle - offset).toDouble())
                    val tx = center.x + maxRadius * Math.cos(trailRad).toFloat()
                    val ty = center.y + maxRadius * Math.sin(trailRad).toFloat()
                    lineTo(tx, ty)
                }
                close()
            }
            
            drawPath(
                path = path,
                brush = Brush.radialGradient(
                    colors = listOf(Color(0x2E00E5FF), Color.Transparent),
                    center = center,
                    radius = maxRadius
                )
            )
            
            // 5. Target Blips
            if (signalStrength > 10) {
                val depthFraction = (depth / 20.0).toFloat().coerceIn(0.15f, 0.85f)
                val targetRadius = maxRadius * depthFraction
                val targetAngleRad = Math.toRadians(60.0)
                val blipX = center.x + targetRadius * Math.cos(targetAngleRad).toFloat()
                val blipY = center.y + targetRadius * Math.sin(targetAngleRad).toFloat()
                
                drawCircle(
                    color = themeColor.copy(alpha = 0.15f * blipPulse),
                    radius = 35f,
                    center = Offset(blipX, blipY)
                )
                drawCircle(
                    color = themeColor.copy(alpha = 0.4f * blipPulse),
                    radius = 20f,
                    center = Offset(blipX, blipY)
                )
                drawCircle(
                    color = themeColor,
                    radius = 8f,
                    center = Offset(blipX, blipY)
                )
            }
        }
        
        // Custom Sci-Fi Text HUD Overlay inside Radar
        Column(
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                .border(BorderStroke(1.dp, Color(0x3300E5FF)), RoundedCornerShape(8.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text("SYS: SCANNING", color = Color(0xFF00E5FF), fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            Text("BW: 20 METERS", color = Color(0x8800E5FF), fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            Text("RF: 15.4 KHZ", color = Color(0x8800E5FF), fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        }

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(12.dp)
                .background(Color.Black.copy(alpha = 0.65f), RoundedCornerShape(8.dp))
                .border(BorderStroke(1.dp, Color(0x3300E5FF)), RoundedCornerShape(8.dp))
                .padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text("TARGET: ${if (signalStrength > 10) "LOCKED" else "SEARCHING"}", color = if (signalStrength > 10) Color(0xFFFFD700) else Color(0x8800E5FF), fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            Text("AMP: $signalStrength%", color = Color(0x8800E5FF), fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
            Text("Z-DEP: ${String.format("%.2f%s", displayDepth, unitSymbol)}", color = Color(0x8800E5FF), fontSize = 8.sp, fontWeight = FontWeight.Bold, fontFamily = androidx.compose.ui.text.font.FontFamily.Monospace)
        }
    }
}

// ==========================================
// TAB 5: AI TACTICAL ANALYSIS SCREEN
// ==========================================
@Composable
fun AiAnalysisScreen(viewModel: VisualizerViewModel) {
    val viewedScan by viewModel.viewedScan.collectAsStateWithLifecycle()
    val isAiLoading by viewModel.isAiLoading.collectAsStateWithLifecycle()
    val chatMessages by viewModel.chatMessages.collectAsStateWithLifecycle()
    val aiAnalysisResult by viewModel.aiAnalysisResult.collectAsStateWithLifecycle()

    val isTtsSpeaking by viewModel.isTtsSpeaking.collectAsStateWithLifecycle()
    val ttsSpeakingText by viewModel.ttsSpeakingText.collectAsStateWithLifecycle()

    val gridWidth by viewModel.gridWidth.collectAsStateWithLifecycle()
    val gridLength by viewModel.gridLength.collectAsStateWithLifecycle()
    val soilType by viewModel.soilType.collectAsStateWithLifecycle()
    val scanPattern by viewModel.scanPattern.collectAsStateWithLifecycle()
    val activeScanData by viewModel.activeScanData.collectAsStateWithLifecycle()

    var userQuery by remember { mutableStateOf("") }
    val chatScrollState = rememberLazyListState()
    val coroutineScope = rememberCoroutineScope()

    // Scroll chat to bottom when new messages arrive
    LaunchedEffect(chatMessages.size) {
        if (chatMessages.isNotEmpty()) {
            chatScrollState.animateScrollToItem(chatMessages.size - 1)
        }
    }

    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Screen Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(CyberGold.copy(alpha = 0.15f))
                            .border(1.dp, CyberGold, RoundedCornerShape(12.dp)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.AutoAwesome,
                            contentDescription = null,
                            tint = CyberGold,
                            modifier = Modifier.size(24.dp)
                        )
                    }
                    Column {
                        Text(
                            text = "تحلیلگر هوشمند تاکتیکال (AI Copilot)",
                            color = Color.White,
                            fontSize = 17.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "پردازش سه‌بعدی و طبقه‌بندی هوشمند آنومالی‌های ژئوفیزیک با هسته Gemini",
                            color = GrayText,
                            fontSize = 10.sp
                        )
                    }
                }

                // Header Audio Playback Toggle Button
                IconButton(
                    onClick = {
                        if (isTtsSpeaking) {
                            viewModel.stopSpeech()
                        } else {
                            val textToRead = chatMessages.lastOrNull { !it.isUser }?.text
                                ?: aiAnalysisResult
                                ?: "تحلیلی برای پخش صوتی وجود ندارد. ابتدا بر روی پردازش هوش مصنوعی کلیک کنید."
                            viewModel.speakText(textToRead)
                        }
                    },
                    colors = IconButtonDefaults.iconButtonColors(
                        containerColor = if (isTtsSpeaking) Color.Red.copy(alpha = 0.2f) else CardBg
                    ),
                    modifier = Modifier
                        .size(42.dp)
                        .border(
                            1.dp,
                            if (isTtsSpeaking) Color.Red else CyberGold,
                            CircleShape
                        )
                ) {
                    Icon(
                        imageVector = if (isTtsSpeaking) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                        contentDescription = "Text To Speech Audio",
                        tint = if (isTtsSpeaking) Color.Red else CyberGold
                    )
                }
            }

            // Current Target Quick Info Panel
            Card(
                colors = CardDefaults.cardColors(containerColor = SurfaceBg),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(1.dp, CardBg),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 12.dp)
            ) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(
                        text = "🎯 مشخصات فیزیکی هدف اسکن‌شده:",
                        color = CyberGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        val scanName = viewedScan?.name ?: "اسکن موقت فعال"
                        val soil = viewedScan?.soilType ?: soilType
                        val pattern = viewedScan?.scanPattern ?: scanPattern
                        val width = viewedScan?.width ?: gridWidth
                        val length = viewedScan?.length ?: gridLength
                        val totalPoints = viewedScan?.getGridData()?.size ?: activeScanData.size

                        Column(modifier = Modifier.weight(1f)) {
                            Text("عنوان: $scanName", color = Color.White, fontSize = 10.sp)
                            Text("نوع خاک: $soil", color = GrayText, fontSize = 9.sp)
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text("ابعاد ماتریس: $width در $length", color = Color.White, fontSize = 10.sp)
                            Text("الگو: $pattern (نقاط: $totalPoints)", color = GrayText, fontSize = 9.sp)
                        }
                    }
                }
            }

            if (aiAnalysisResult == null && chatMessages.isEmpty()) {
                // Empty State: Prompt the user to start tactical processing
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(16.dp))
                        .background(CardBg.copy(alpha = 0.5f))
                        .border(1.dp, CardBg, RoundedCornerShape(16.dp))
                        .padding(24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Box(
                            modifier = Modifier
                                .size(80.dp)
                                .clip(CircleShape)
                                .background(CyberGold.copy(alpha = 0.1f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoAwesome,
                                contentDescription = null,
                                tint = CyberGold,
                                modifier = Modifier.size(40.dp)
                            )
                        }
                        Spacer(modifier = Modifier.height(16.dp))
                        Text(
                            text = "سیستم آماده تحلیل فرکانسی هدف",
                            color = Color.White,
                            fontSize = 14.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "هوش مصنوعی با پردازش مقادیر عددی ماتریس اسکن، ساختار لایه‌های زمین، وجود فلزات مغناطیسی (آهن)، فلزات گرانبها (طلا/نقره) و ساختارهای توخالی (حفره/تونل) را با دقت مهندسی تخمین می‌زند.",
                            color = GrayText,
                            fontSize = 11.sp,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.padding(horizontal = 16.dp)
                        )
                        Spacer(modifier = Modifier.height(24.dp))
                        
                        if (isAiLoading) {
                            Column(
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                CircularProgressIndicator(color = CyberGold, modifier = Modifier.size(28.dp))
                                Text(
                                    text = "در حال ارتباط با مغز هوش مصنوعی و تحلیل لایه‌های خاک...",
                                    color = CyberGold,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        } else {
                            Button(
                                onClick = { viewModel.analyzeCurrentScanWithAi() },
                                colors = ButtonDefaults.buttonColors(containerColor = CyberGold),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth(0.8f)
                            ) {
                                Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = Color.Black)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("پردازش و تحلیل ژئوفیزیک با هوش مصنوعی", color = Color.Black, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            } else {
                // Interactive Chat Interface with the Tactical AI Coach
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth()
                ) {
                    LazyColumn(
                        state = chatScrollState,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(16.dp))
                            .background(CardBg.copy(alpha = 0.4f))
                            .border(1.dp, CardBg, RoundedCornerShape(16.dp))
                            .padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        items(chatMessages) { msg ->
                            val alignment = if (msg.isUser) Alignment.End else Alignment.Start
                            val containerColor = if (msg.isUser) CyberCyan.copy(alpha = 0.15f) else SurfaceBg
                            val borderColor = if (msg.isUser) CyberCyan.copy(alpha = 0.3f) else CardBg
                            val textColor = if (msg.isUser) CyberCyan else Color.White

                            Column(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalAlignment = alignment
                            ) {
                                Row(
                                    verticalAlignment = Alignment.Top,
                                    horizontalArrangement = if (msg.isUser) Arrangement.End else Arrangement.Start,
                                    modifier = Modifier.fillMaxWidth(0.9f)
                                ) {
                                    if (!msg.isUser) {
                                        Box(
                                            modifier = Modifier
                                                .padding(end = 8.dp, top = 4.dp)
                                                .size(24.dp)
                                                .clip(CircleShape)
                                                .background(CyberGold.copy(alpha = 0.1f)),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Icon(Icons.Default.AutoAwesome, contentDescription = null, tint = CyberGold, modifier = Modifier.size(12.dp))
                                        }
                                    }

                                    Card(
                                        colors = CardDefaults.cardColors(containerColor = containerColor),
                                        shape = RoundedCornerShape(
                                            topStart = 12.dp,
                                            topEnd = 12.dp,
                                            bottomStart = if (msg.isUser) 12.dp else 0.dp,
                                            bottomEnd = if (msg.isUser) 0.dp else 12.dp
                                        ),
                                        border = BorderStroke(1.dp, borderColor)
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Text(
                                                text = msg.text,
                                                color = textColor,
                                                fontSize = 12.sp,
                                                lineHeight = 18.sp,
                                                textAlign = TextAlign.Right
                                            )

                                            if (!msg.isUser) {
                                                Spacer(modifier = Modifier.height(6.dp))
                                                HorizontalDivider(color = Color.White.copy(alpha = 0.08f))
                                                val isThisSpeaking = isTtsSpeaking && ttsSpeakingText == msg.text
                                                Row(
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .padding(top = 4.dp),
                                                    horizontalArrangement = Arrangement.End,
                                                    verticalAlignment = Alignment.CenterVertically
                                                ) {
                                                    TextButton(
                                                        onClick = {
                                                            if (isThisSpeaking) {
                                                                viewModel.stopSpeech()
                                                            } else {
                                                                viewModel.speakText(msg.text)
                                                            }
                                                        },
                                                        contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                                                        modifier = Modifier.height(26.dp)
                                                    ) {
                                                        Icon(
                                                            imageVector = if (isThisSpeaking) Icons.Default.VolumeOff else Icons.Default.VolumeUp,
                                                            contentDescription = null,
                                                            tint = if (isThisSpeaking) Color.Red else CyberGold,
                                                            modifier = Modifier.size(14.dp)
                                                        )
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Text(
                                                            text = if (isThisSpeaking) "توقف پخش صوتی" else "پخش صوتی این پاسخ 🔊",
                                                            color = if (isThisSpeaking) Color.Red else CyberGold,
                                                            fontSize = 10.sp,
                                                            fontWeight = FontWeight.Bold
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        if (isAiLoading) {
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.padding(start = 12.dp, top = 4.dp)
                                ) {
                                    CircularProgressIndicator(color = CyberGold, modifier = Modifier.size(16.dp))
                                    Text("در حال پردازش گزارش تکمیلی...", color = CyberGold, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }

                    // Quick prompt options
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        val prompts = listOf(
                            "احتمال طلا؟" to "بر اساس مقادیر فاز سنسور، چقدر احتمال وجود طلا در نقاط قرمز اسکن وجود دارد؟",
                            "عمق فرضی؟" to "عمق تقریبی قوی‌ترین آنومالی ثبت شده بر اساس تخمین محاسباتی چقدر است؟",
                            "روش حذف خطا؟" to "چه تاکتیک فیزیکی برای فیلتر کردن نویز یا کالیبراسیون زمین پیشنهاد می‌دهید؟"
                        )

                        prompts.forEach { (label, fullText) ->
                            Button(
                                onClick = { viewModel.sendMessageToAi(fullText) },
                                colors = ButtonDefaults.buttonColors(containerColor = CardBg),
                                shape = RoundedCornerShape(20.dp),
                                contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                modifier = Modifier.height(28.dp),
                                enabled = !isAiLoading
                            ) {
                                Text(label, color = CyberGold, fontSize = 9.sp, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }

                    // Chat Input Bar
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        TextField(
                            value = userQuery,
                            onValueChange = { userQuery = it },
                            placeholder = { Text("از هوش مصنوعی رادار بپرسید...", color = GrayText, fontSize = 11.sp) },
                            colors = TextFieldDefaults.colors(
                                focusedContainerColor = SurfaceBg,
                                unfocusedContainerColor = SurfaceBg,
                                focusedIndicatorColor = CyberCyan,
                                unfocusedIndicatorColor = Color.Transparent,
                                focusedTextColor = Color.White,
                                unfocusedTextColor = Color.White
                            ),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(50.dp)
                                .border(1.dp, CardBg, RoundedCornerShape(12.dp)),
                            textStyle = TextStyle(fontSize = 12.sp),
                            singleLine = true,
                            enabled = !isAiLoading
                        )

                        IconButton(
                            onClick = {
                                if (userQuery.isNotBlank()) {
                                    viewModel.sendMessageToAi(userQuery)
                                    userQuery = ""
                                }
                            },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = CyberCyan,
                                contentColor = Color.Black
                            ),
                            modifier = Modifier.size(50.dp),
                            enabled = !isAiLoading && userQuery.isNotBlank()
                        ) {
                            Icon(Icons.Default.Send, contentDescription = "Send Message", modifier = Modifier.size(20.dp))
                        }

                        IconButton(
                            onClick = { viewModel.clearAiState() },
                            colors = IconButtonDefaults.iconButtonColors(
                                containerColor = CardBg,
                                contentColor = CyberRed
                            ),
                            modifier = Modifier.size(50.dp),
                            enabled = !isAiLoading
                        ) {
                            Icon(Icons.Default.Refresh, contentDescription = "Reset Chat", modifier = Modifier.size(20.dp))
                        }
                    }

                    // Floating Animated Audio Playback Banner when TTS is active
                    AnimatedVisibility(
                        visible = isTtsSpeaking,
                        enter = fadeIn() + expandVertically(),
                        exit = fadeOut() + shrinkVertically()
                    ) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = SurfaceBg),
                            shape = RoundedCornerShape(12.dp),
                            border = BorderStroke(1.dp, CyberGold),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(32.dp)
                                            .clip(CircleShape)
                                            .background(CyberGold.copy(alpha = 0.2f)),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.VolumeUp,
                                            contentDescription = null,
                                            tint = CyberGold,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }

                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "🎙️ در حال خواندن گزارش هوش مصنوعی...",
                                            color = CyberGold,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "پخش صوتی موتور Text-to-Speech",
                                            color = GrayText,
                                            fontSize = 9.sp
                                        )
                                    }
                                }

                                Button(
                                    onClick = { viewModel.stopSpeech() },
                                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red.copy(alpha = 0.8f)),
                                    shape = RoundedCornerShape(8.dp),
                                    contentPadding = PaddingValues(horizontal = 10.dp, vertical = 2.dp),
                                    modifier = Modifier.height(30.dp)
                                ) {
                                    Icon(Icons.Default.Stop, contentDescription = "Stop", tint = Color.White, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("توقف", color = Color.White, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PlanViewScreen(viewModel: VisualizerViewModel) {
    val context = LocalContext.current
    val viewedScan by viewModel.viewedScan.collectAsStateWithLifecycle()
    val filteredScan by viewModel.filteredScan.collectAsStateWithLifecycle()
    val currentScan = filteredScan ?: viewedScan

    val scanData = currentScan?.getGridData() ?: emptyList()
    val gridCols = currentScan?.width ?: 0
    val gridRows = currentScan?.length ?: 0
    val maxVal = if (scanData.isNotEmpty()) scanData.maxOrNull() ?: 1f else 1f

    // States for interactive crosshair analysis
    var selectedCol by remember(gridCols, gridRows) { mutableStateOf(0) }
    var selectedRow by remember(gridCols, gridRows) { mutableStateOf(0) }
    var smoothInterpolation by remember { mutableStateOf(true) }
    val selectedDepthLayer by viewModel.selectedDepthLayer.collectAsStateWithLifecycle()
    val colorAutoScaleEnabled by viewModel.colorAutoScaleEnabled.collectAsStateWithLifecycle()
    val colorPalette by viewModel.colorPalette.collectAsStateWithLifecycle()

    var showTargetMarkers by remember { mutableStateOf(true) }
    var detectedTargetsList by remember { mutableStateOf<List<com.example.ui.DetectedTarget>>(emptyList()) }

    val idx = selectedRow * gridCols + selectedCol
    val value = scanData.getOrNull(idx) ?: 0f

    // Highly realistic geophysical depth estimation based on attenuation ratio
    val targetDepth = if (maxVal > 0f) {
        val ratio = (abs(value) / maxVal).coerceIn(0f, 1f)
        if (value > 0f) {
            // Metal conductors: strong signals are shallow, weaker signals are deep
            (18.0 * (1.0 - ratio)).coerceIn(0.65, 12.0)
        } else {
            // Cavities/Tunnels: weaker signals indicate deeper geophysical anomalies
            (8.0 + (1.0 - ratio) * 12.0).coerceIn(1.8, 20.0)
        }
    } else {
        0.0
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header with Smoothing Switch
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "🗺️ نمای ۲ بعدی فیلترشده (OKM Realtime Plan)",
                color = CyberGold,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Soil Noise Filter Button
                val soilNoiseFilterEnabled by viewModel.soilNoiseFilterEnabled.collectAsStateWithLifecycle()
                
                Button(
                    onClick = { viewModel.setSoilNoiseFilterEnabled(!soilNoiseFilterEnabled) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (soilNoiseFilterEnabled) CyberGold.copy(alpha = 0.15f) else CardBg,
                        contentColor = if (soilNoiseFilterEnabled) CyberGold else Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (soilNoiseFilterEnabled) CyberGold else Color.Transparent),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.FilterList,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (soilNoiseFilterEnabled) "حذف آلودگی روشن" else "حذف آلودگی خاموش",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // High-tech toggle button for Bilinear Smoothing
                Button(
                    onClick = { smoothInterpolation = !smoothInterpolation },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (smoothInterpolation) CyberCyan.copy(alpha = 0.15f) else CardBg,
                        contentColor = if (smoothInterpolation) CyberCyan else Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (smoothInterpolation) CyberCyan else Color.Transparent),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(
                        imageVector = if (smoothInterpolation) Icons.Default.BlurOn else Icons.Default.BlurOff,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (smoothInterpolation) "هموارسازی روشن" else "هموارسازی خاموش",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Auto-Scale (Color normalizer) button
                Button(
                    onClick = { viewModel.setColorAutoScaleEnabled(!colorAutoScaleEnabled) },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (colorAutoScaleEnabled) CyberCyan.copy(alpha = 0.15f) else CardBg,
                        contentColor = if (colorAutoScaleEnabled) CyberCyan else Color.White
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (colorAutoScaleEnabled) CyberCyan else Color.Transparent),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.AutoAwesome,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = if (colorAutoScaleEnabled) "مقیاس خودکار" else "مقیاس دستی",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                // Direct CSV Export Button
                Button(
                    onClick = { currentScan?.let { exportScanRecord(context, it, "csv") } },
                    enabled = currentScan != null,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = CardBg,
                        contentColor = CyberGold
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, CyberGold.copy(alpha = 0.3f)),
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                    modifier = Modifier.height(30.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Download,
                        contentDescription = null,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = "خروجی CSV",
                        fontSize = 9.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }

        // Main Heatmap Canvas Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceBg),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, CardBg),
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
        ) {
            PlanView(
                scanData = scanData,
                gridCols = gridCols,
                gridRows = gridRows,
                maxVal = maxVal,
                selectedCol = selectedCol,
                selectedRow = selectedRow,
                onSelectPoint = { col, row ->
                    selectedCol = col
                    selectedRow = row
                },
                smoothInterpolation = smoothInterpolation,
                selectedDepthLayer = selectedDepthLayer,
                colorAutoScale = colorAutoScaleEnabled,
                colorPalette = colorPalette,
                showTargetMarkers = showTargetMarkers,
                onTargetsDetected = { targets ->
                    detectedTargetsList = targets
                },
                modifier = Modifier.fillMaxSize()
            )
        }

        // Smart Target Indicators & Detection Control Panel
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceBg),
            shape = RoundedCornerShape(16.dp),
            border = BorderStroke(1.dp, CardBg),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(modifier = Modifier.padding(14.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = "🎯 نشانگرهای هدف هوشمند (Smart Targets)",
                            color = CyberGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Toggle Switch for Markers
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = if (showTargetMarkers) "نشانگرها فعال" else "نشانگرها غیرفعال",
                            color = GrayText,
                            fontSize = 9.sp
                        )
                        Switch(
                            checked = showTargetMarkers,
                            onCheckedChange = { showTargetMarkers = it },
                            colors = SwitchDefaults.colors(
                                checkedThumbColor = CyberGold,
                                checkedTrackColor = CyberGold.copy(alpha = 0.3f),
                                uncheckedThumbColor = GrayText,
                                uncheckedTrackColor = CardBg
                            ),
                            modifier = Modifier.scale(0.8f)
                        )
                    }
                }

                if (showTargetMarkers && detectedTargetsList.isNotEmpty()) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "آنومالی‌های فرکانسی فلزی با احتمال بالا کشف شدند (کلیک جهت فوکوس):",
                        color = GrayText,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                    
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        detectedTargetsList.forEachIndexed { index, target ->
                            val isSelected = selectedCol == target.col && selectedRow == target.row
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(
                                        color = if (isSelected) CyberCyan.copy(alpha = 0.12f) else CardBg.copy(alpha = 0.4f),
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .border(
                                        width = 1.dp,
                                        color = if (isSelected) CyberCyan else Color.Transparent,
                                        shape = RoundedCornerShape(8.dp)
                                    )
                                    .clickable {
                                        selectedCol = target.col
                                        selectedRow = target.row
                                    }
                                    .padding(horizontal = 10.dp, vertical = 8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .background(
                                                color = if (target.type.contains("طلا")) CyberGold else CyberCyan,
                                                shape = CircleShape
                                            ),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Text(
                                            text = "${index + 1}",
                                            color = Color.Black,
                                            fontSize = 9.sp,
                                            fontWeight = FontWeight.Black
                                        )
                                    }
                                    Column {
                                        Text(
                                            text = "هدف ${target.id}: ${target.type}",
                                            color = Color.White,
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold
                                        )
                                        Text(
                                            text = "موقعیت: X: ${target.col + 1}, Y: ${target.row + 1}",
                                            color = GrayText,
                                            fontSize = 8.sp
                                        )
                                    }
                                }
                                
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "عمق: ${String.format("%.2f", target.depth)} متر",
                                        color = CyberCyan,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "سیگنال: ${String.format("%.1f", target.value)} Hz",
                                        color = GrayText,
                                        fontSize = 8.sp
                                    )
                                }
                            }
                        }
                    }
                } else if (showTargetMarkers) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "جستجوی خودکار... هیچ آنومالی فلزی بالاتر از حد آستانه شناسایی نشد.",
                        color = GrayText,
                        fontSize = 9.sp,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                    )
                }
            }
        }

        // Live Real-time Signal waveform chart of selected profile
        SignalProfileChart(
            scanData = scanData,
            gridCols = gridCols,
            gridRows = gridRows,
            selectedCol = selectedCol,
            selectedRow = selectedRow,
            maxVal = maxVal
        )

        // Professional Geo-analysis Panel (Directly matching the user's software mockup)
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceBg),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.5.dp, CyberGold.copy(alpha = 0.25f)),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(12.dp)
            ) {
                // Large primary depth reading
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "📐 عمق تخمینی در نقطه هدف",
                            color = GrayText,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = String.format("%.2f", targetDepth),
                                color = CyberCyan,
                                fontSize = 24.sp,
                                fontWeight = FontWeight.Black
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "متر",
                                color = CyberCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }

                    // Scan details badge
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "فرکانس کالیبراسیون",
                            color = GrayText,
                            fontSize = 9.sp
                        )
                        Text(
                            text = currentScan?.soilType ?: "معمولی",
                            color = CyberGold,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }

                Divider(
                    color = CardBg,
                    thickness = 1.dp,
                    modifier = Modifier.padding(vertical = 8.dp)
                )

                // Sub-parameters grids
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    // Position Coordinate
                    Column(modifier = Modifier.weight(1f)) {
                        Text("موقعیت سنسور", color = GrayText, fontSize = 9.sp)
                        Text(
                            text = "X: ${selectedCol + 1} | Y: ${selectedRow + 1}",
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Raw Value
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("شدت فرکانس", color = GrayText, fontSize = 9.sp)
                        Text(
                            text = String.format("%.2f Hz", value),
                            color = Color.White,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // Conductivity Percentage
                    Column(modifier = Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                        Text("رسانایی خاک", color = GrayText, fontSize = 9.sp)
                        val conductivity = if (maxVal > 0f) (value / maxVal * 100).coerceIn(-100f, 100f) else 0f
                        Text(
                            text = String.format("%.1f%%", conductivity),
                            color = if (conductivity > 0) Color(0xFFFFD700) else Color(0xFF00E5FF),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }

        // Export 3D Scan Data Card for professional analysis
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceBg),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(1.dp, CardBg),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Share,
                        contentDescription = null,
                        tint = CyberGold,
                        modifier = Modifier.size(18.dp)
                    )
                    Text(
                        text = "💾 خروجی اطلاعات اسکن ۳ بعدی",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "جهت بررسی حرفه‌ای‌تر در نرم‌افزارهای دسکتاپ (مانند Voxler ،Visualizer 3D یا نرم‌افزارهای مهندسی زمین‌شناسی)، داده‌های اسکن جاری را با فرمت صنعتی دانلود و اشتراک‌گذاری کنید.",
                    color = GrayText,
                    fontSize = 9.sp,
                    lineHeight = 14.sp
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Share Summary Button
                    Button(
                        onClick = { currentScan?.let { shareScanRecord(context, it, "summary") } },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberRed.copy(alpha = 0.15f),
                            contentColor = CyberRed
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, CyberRed.copy(alpha = 0.3f)),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Share,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp)
                            )
                            Text("اشتراک‌گذاری", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Export CSV Button
                    Button(
                        onClick = { currentScan?.let { exportScanRecord(context, it, "csv") } },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberGold.copy(alpha = 0.15f),
                            contentColor = CyberGold
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, CyberGold.copy(alpha = 0.3f)),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.GridOn,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp)
                            )
                            Text("دانلود CSV", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }

                    // Export JSON Button
                    Button(
                        onClick = { currentScan?.let { exportScanRecord(context, it, "json") } },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = CyberCyan.copy(alpha = 0.15f),
                            contentColor = CyberCyan
                        ),
                        shape = RoundedCornerShape(8.dp),
                        border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.3f)),
                        contentPadding = PaddingValues(horizontal = 6.dp, vertical = 6.dp),
                        modifier = Modifier.weight(1f).height(36.dp)
                    ) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Code,
                                contentDescription = null,
                                modifier = Modifier.size(13.dp)
                            )
                            Text("دانلود JSON", fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Soil Depth Layers Selector within 2D Screen
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceBg),
            shape = RoundedCornerShape(12.dp),
            border = BorderStroke(1.dp, CardBg),
            modifier = Modifier.fillMaxWidth().padding(bottom = 10.dp)
        ) {
            Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Layers,
                            contentDescription = null,
                            tint = CyberGold,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = "لایه عمق خاک (Soil Layer Filtering)",
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    Text(
                        text = when (selectedDepthLayer) {
                            "Surface" -> "سطحی (0-3.5m)"
                            "Subsurface" -> "میان‌سطحی (3.5-8m)"
                            "Deep" -> "عمیق (8-14m)"
                            "Bedrock" -> "بستر (14-20m)"
                            else -> "تمام لایه‌ها (All)"
                        },
                        color = CyberGold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("All", "Surface", "Subsurface", "Deep", "Bedrock").forEach { layer ->
                        val active = selectedDepthLayer == layer
                        val label = when (layer) {
                            "All" -> "همه"
                            "Surface" -> "سطحی"
                            "Subsurface" -> "میان‌سطحی"
                            "Deep" -> "عمیق"
                            else -> "بستر"
                        }
                        val layerColor = when (layer) {
                            "Surface" -> CyberCyan
                            "Subsurface" -> CyberGold
                            "Deep" -> CyberRed
                            "Bedrock" -> Color(0xFF9C27B0)
                            else -> Color.White
                        }
                        Button(
                            onClick = { viewModel.setSelectedDepthLayer(layer) },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (active) layerColor.copy(alpha = 0.2f) else CardBg,
                                contentColor = if (active) layerColor else Color.White
                            ),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, if (active) layerColor else Color.Transparent),
                            contentPadding = PaddingValues(horizontal = 4.dp, vertical = 2.dp),
                            modifier = Modifier.height(26.dp).weight(1f)
                        ) {
                            Text(text = label, fontSize = 8.sp, fontWeight = FontWeight.Bold)
                        }
                    }
                }
            }
        }

        // Quick Recent Scans list
        Text(
            text = "📂 انتخاب سریع اسکن‌های اخیر (Recent Scans)",
            color = Color.White,
            fontSize = 11.sp,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 5.dp)
        )

        val savedScans by viewModel.savedScans.collectAsStateWithLifecycle()

        LazyRow(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(savedScans) { scan ->
                val isSelected = viewedScan?.id == scan.id
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isSelected) CyberGold.copy(alpha = 0.25f) else CardBg
                    ),
                    shape = RoundedCornerShape(8.dp),
                    border = BorderStroke(1.dp, if (isSelected) CyberGold else Color.Transparent),
                    modifier = Modifier
                        .width(130.dp)
                        .clickable { viewModel.loadScan(scan) }
                ) {
                    Column(
                        modifier = Modifier.padding(8.dp)
                    ) {
                        Text(
                            text = scan.name,
                            color = if (isSelected) CyberGold else Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "${scan.width}x${scan.length} | ${scan.soilType}",
                            color = GrayText,
                            fontSize = 9.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}



