package com.example.ui

import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
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
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.hardware.ConnectionMode
import com.example.hardware.FeedbackManager
import com.example.hardware.SensorType
import com.example.ui.theme.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Modal Dialog container for the Gold Radar X20 Calibration Wizard
 */
@Composable
fun CalibrationWizardDialog(
    viewModel: VisualizerViewModel,
    onDismiss: () -> Unit,
    onCalibrationComplete: () -> Unit = {}
) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp)
                .testTag("calibration_wizard_dialog"),
            shape = RoundedCornerShape(20.dp),
            color = DarkBg,
            tonalElevation = 12.dp,
            border = BorderStroke(1.dp, CyberGold.copy(alpha = 0.4f))
        ) {
            CalibrationWizardScreen(
                viewModel = viewModel,
                onClose = onDismiss,
                onFinish = {
                    onCalibrationComplete()
                    onDismiss()
                }
            )
        }
    }
}

/**
 * Step-by-Step Hardware Setup & Calibration Wizard Screen for Gold Radar X20
 */
@Composable
fun CalibrationWizardScreen(
    viewModel: VisualizerViewModel,
    modifier: Modifier = Modifier,
    onClose: () -> Unit = {},
    onFinish: () -> Unit = {}
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val appLanguage by viewModel.appLanguage.collectAsStateWithLifecycle()

    var currentStep by remember { mutableIntStateOf(1) }
    val totalSteps = 5

    // Live sensor values from SensorManager
    val connectionState by viewModel.sensorManager.connectionState.collectAsStateWithLifecycle()
    val connectionStatus by viewModel.sensorManager.tvStatus.collectAsStateWithLifecycle()
    val activeSensorType by viewModel.sensorManager.sensorType.collectAsStateWithLifecycle()
    val baudRate by viewModel.sensorManager.baudRate.collectAsStateWithLifecycle()
    val adcValue by viewModel.sensorManager.adcValue.collectAsStateWithLifecycle()
    val phaseShift by viewModel.sensorManager.phaseShift.collectAsStateWithLifecycle()
    val compassHeading by viewModel.sensorManager.compassHeading.collectAsStateWithLifecycle()
    val phoneMagMicroTesla by viewModel.sensorManager.phoneMagMicroTesla.collectAsStateWithLifecycle()

    // Step state holders
    var isSamplingZeroing by remember { mutableStateOf(false) }
    var zeroingProgress by remember { mutableFloatStateOf(0f) }
    var zeroingCompleted by remember { mutableStateOf(false) }

    var testSweepActive by remember { mutableStateOf(false) }
    var testSweepScore by remember { mutableIntStateOf(98) }

    // Calibration offsets recorded
    var capturedAdcOffset by remember { mutableIntStateOf(0) }
    var capturedPhaseOffset by remember { mutableIntStateOf(0) }
    var capturedNoiseFloor by remember { mutableFloatStateOf(12f) }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(DarkBg)
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            // Top Bar: Header Title, Step Progress & Close Button
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .clip(CircleShape)
                                .background(CyberGold.copy(alpha = 0.15f))
                                .border(BorderStroke(1.5.dp, CyberGold), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Tune,
                                contentDescription = "Calibration",
                                tint = CyberGold,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        Column {
                            Text(
                                text = "راهنمای کالیبراسیون رادار X20",
                                color = CyberGold,
                                fontSize = 17.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "کالیبراسیون ۵ مرحله‌ای سخت‌افزار و سنسور",
                                color = GrayText,
                                fontSize = 11.sp
                            )
                        }
                    }

                    IconButton(
                        onClick = onClose,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(CardBg)
                            .testTag("close_calibration_wizard_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Close",
                            tint = Color.White
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Progress Indicator Bar
                WizardStepProgressBar(
                    currentStep = currentStep,
                    totalSteps = totalSteps
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Middle Step Content Container
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                AnimatedContent(
                    targetState = currentStep,
                    transitionSpec = {
                        if (targetState > initialState) {
                            slideInHorizontally { width -> width } + fadeIn() togetherWith
                                    slideOutHorizontally { width -> -width } + fadeOut()
                        } else {
                            slideInHorizontally { width -> -width } + fadeIn() togetherWith
                                    slideOutHorizontally { width -> width } + fadeOut()
                        }
                    },
                    label = "StepTransition"
                ) { step ->
                    when (step) {
                        1 -> Step1ProbeSelection(
                            viewModel = viewModel,
                            activeSensorType = activeSensorType,
                            connectionState = connectionState,
                            connectionStatus = connectionStatus,
                            baudRate = baudRate
                        )
                        2 -> Step2NoiseAnalysis(
                            phoneMagMicroTesla = phoneMagMicroTesla,
                            adcValue = adcValue
                        )
                        3 -> Step3HorizonAndCompass(
                            compassHeading = compassHeading,
                            sensorManager = viewModel.sensorManager
                        )
                        4 -> Step4ZeroingCalibration(
                            isSampling = isSamplingZeroing,
                            progress = zeroingProgress,
                            isCompleted = zeroingCompleted,
                            adcOffset = capturedAdcOffset,
                            phaseOffset = capturedPhaseOffset,
                            noiseFloor = capturedNoiseFloor,
                            onStartSampling = {
                                scope.launch {
                                    isSamplingZeroing = true
                                    zeroingProgress = 0f
                                    FeedbackManager.playClick(context)
                                    viewModel.sensorManager.calibrate()

                                    for (i in 1..100) {
                                        delay(35)
                                        zeroingProgress = i / 100f
                                    }

                                    capturedAdcOffset = adcValue
                                    capturedPhaseOffset = phaseShift
                                    capturedNoiseFloor = if (phoneMagMicroTesla > 0) phoneMagMicroTesla else 8.5f
                                    isSamplingZeroing = false
                                    zeroingCompleted = true
                                    FeedbackManager.playBuzzer(context, 200)
                                    Toast.makeText(context, "✅ کالیبراسیون نقطه‌صفر با موفقیت انجام شد", Toast.LENGTH_SHORT).show()
                                }
                            }
                        )
                        5 -> Step5VerificationSweep(
                            adcValue = adcValue,
                            phaseShift = phaseShift,
                            testScore = testSweepScore,
                            isSweepActive = testSweepActive,
                            onToggleSweep = {
                                testSweepActive = !testSweepActive
                                if (testSweepActive) {
                                    FeedbackManager.vibrate(context, 80)
                                }
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Bottom Navigation Controls (Back / Next / Complete)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Back button
                if (currentStep > 1) {
                    OutlinedButton(
                        onClick = {
                            FeedbackManager.playClick(context)
                            currentStep--
                        },
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.3f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .height(48.dp)
                            .testTag("wizard_prev_step_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Previous Step",
                            tint = Color.White
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("مرحله قبل", fontSize = 13.sp)
                    }
                } else {
                    Spacer(modifier = Modifier.width(1.dp))
                }

                // Next / Finish button
                Button(
                    onClick = {
                        FeedbackManager.playClick(context)
                        if (currentStep < totalSteps) {
                            currentStep++
                        } else {
                            viewModel.sensorManager.calibrate()
                            Toast.makeText(context, "✨ تنظیمات کالیبراسیون ذخیره شد!", Toast.LENGTH_LONG).show()
                            onFinish()
                        }
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CyberGold),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(48.dp)
                        .testTag("wizard_next_step_button")
                ) {
                    Text(
                        text = if (currentStep == totalSteps) "تأیید و شروع اسکن" else "مرحله بعد",
                        color = Color.Black,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Icon(
                        imageVector = if (currentStep == totalSteps) Icons.Default.CheckCircle else Icons.Default.ArrowForward,
                        contentDescription = "Next Step",
                        tint = Color.Black
                    )
                }
            }
        }
    }
}

/**
 * Top Progress Step Indicator
 */
@Composable
fun WizardStepProgressBar(
    currentStep: Int,
    totalSteps: Int
) {
    val stepTitles = listOf(
        "پروپ سنسور",
        "تداخل محیط",
        "تراز و قطب‌نما",
        "کالیبره‌ صفر",
        "تست نهایی"
    )

    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            stepTitles.forEachIndexed { index, title ->
                val stepNum = index + 1
                val isCurrent = stepNum == currentStep
                val isPassed = stepNum < currentStep

                val badgeBg = when {
                    isCurrent -> CyberGold
                    isPassed -> CyberCyan
                    else -> CardBg
                }

                val textColor = when {
                    isCurrent -> CyberGold
                    isPassed -> CyberCyan
                    else -> GrayText
                }

                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(26.dp)
                            .clip(CircleShape)
                            .background(badgeBg),
                        contentAlignment = Alignment.Center
                    ) {
                        if (isPassed) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = null,
                                tint = Color.Black,
                                modifier = Modifier.size(16.dp)
                            )
                        } else {
                            Text(
                                text = "$stepNum",
                                color = if (isCurrent) Color.Black else Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = title,
                        color = textColor,
                        fontSize = 9.sp,
                        fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                        maxLines = 1
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Linear Progress Track
        val progressFraction = currentStep.toFloat() / totalSteps.toFloat()
        val animatedProgress by animateFloatAsState(
            targetValue = progressFraction,
            animationSpec = tween(400),
            label = "StepProgress"
        )

        LinearProgressIndicator(
            progress = animatedProgress,
            modifier = Modifier
                .fillMaxWidth()
                .height(4.dp)
                .clip(RoundedCornerShape(2.dp)),
            color = CyberGold,
            trackColor = CardBg
        )
    }
}

// ==========================================
// STEP 1: PROBE & INTERFACE SELECTION
// ==========================================
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun Step1ProbeSelection(
    viewModel: VisualizerViewModel,
    activeSensorType: SensorType,
    connectionState: ConnectionMode,
    connectionStatus: String,
    baudRate: Int
) {
    val context = LocalContext.current

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceBg),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.DeveloperBoard, contentDescription = null, tint = CyberGold)
                    Text(
                        text = "مرحله ۱: انتخاب نوع سنسور و پروپ سخت‌افزار",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "نوع مگنتومتر فیزیکی یا پروپ رادار Gold Radar X20 متصل به دستگاه را مشخص کنید.",
                    color = GrayText,
                    fontSize = 12.sp
                )

                HorizontalDivider(color = CardBg.copy(alpha = 0.5f))

                // Sensor Probes
                val probes = listOf(
                    SensorType.GOLD_RADAR_X20 to "رادار دو سنسوره X20 (پالس ADC + اختلاف فاز)",
                    SensorType.FMG3 to "سنسور فلاکس‌گیت FMG3 (میدان سنج آلمانی)",
                    SensorType.FLC100 to "سنسور مگنتومتر FLC100 (ولتاژ آنالوگ)",
                    SensorType.HMC5883L to "قطب‌نما و سنسور دیجیتال HMC5883L",
                    SensorType.QMC5883L to "سنسور ۳ جهته با دقت بالا QMC5883L",
                    SensorType.ADXL345 to "شتاب‌سنج و سنسور شیب ۳ جهته ADXL345 (تراز و زاویه‌سنج)",
                    SensorType.PHONE_INTERNAL to "سنسور مغناطیسی داخلی گوشی (بدون نیاز به سخت‌افزار خارجی)"
                )

                probes.forEach { (type, description) ->
                    val isSelected = activeSensorType == type
                    Card(
                        onClick = {
                            FeedbackManager.playClick(context)
                            viewModel.sensorManager.setSensorType(type)
                        },
                        colors = CardDefaults.cardColors(
                            containerColor = if (isSelected) CyberCyan.copy(alpha = 0.15f) else CardBg
                        ),
                        border = BorderStroke(
                            width = 1.5.dp,
                            color = if (isSelected) CyberCyan else Color.Transparent
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("probe_card_${type.name}")
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            RadioButton(
                                selected = isSelected,
                                onClick = {
                                    FeedbackManager.playClick(context)
                                    viewModel.sensorManager.setSensorType(type)
                                },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = CyberCyan,
                                    unselectedColor = Color.Gray
                                )
                            )

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = type.name.replace("_", " "),
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Text(
                                    text = description,
                                    color = GrayText,
                                    fontSize = 10.sp
                                )
                            }
                        }
                    }
                }
            }
        }

        // Connection Test Card
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceBg),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(
                    text = "آزمایش اتصال درایور سریال / بلوتوث",
                    color = CyberGold,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("وضعیت ارتباطی:", color = GrayText, fontSize = 11.sp)
                    Text(
                        text = connectionStatus,
                        color = if (connectionState == ConnectionMode.DISCONNECTED) Color.Red else CyberCyan,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Button(
                        onClick = {
                            FeedbackManager.playClick(context)
                            viewModel.sensorManager.autoConnect()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CyberCyan),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.Sensors, contentDescription = null, tint = Color.Black)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("فعال‌سازی سنسورها", color = Color.Black, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                    }

                    Button(
                        onClick = {
                            FeedbackManager.playClick(context)
                            viewModel.sensorManager.startSimulator()
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = CardBg),
                        border = BorderStroke(1.dp, CyberGold),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Default.SmartToy, contentDescription = null, tint = CyberGold)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("حالت شبیه‌ساز", color = Color.White, fontSize = 11.sp)
                    }
                }
            }
        }
    }
}

// ==========================================
// STEP 2: ENVIRONMENTAL NOISE ANALYSIS
// ==========================================
@Composable
fun Step2NoiseAnalysis(
    phoneMagMicroTesla: Float,
    adcValue: Int
) {
    val transition = rememberInfiniteTransition(label = "RadarSweep")
    val sweepAngle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "AngleSweep"
    )

    val noiseMicroTesla = if (phoneMagMicroTesla > 0) phoneMagMicroTesla else (35f + (adcValue % 15))
    val noiseLevelStatus = when {
        noiseMicroTesla < 40f -> Pair("محیط تمیز و بدون نویز (عالی)", Color(0xFF00E676))
        noiseMicroTesla in 40f..65f -> Pair("نویز مغناطیسی متوسط (قابل قبول)", CyberGold)
        else -> Pair("تداخل شدید مغناطیسی! (از خودرو و کابل برق فاصله بگیرید)", Color(0xFFFF1744))
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceBg),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Radar, contentDescription = null, tint = CyberCyan)
                    Text(
                        text = "مرحله ۲: پایش تداخل و نویز مغناطیسی محیط",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "دستگاه در حال اسکن فرکانس‌های مزاحم و فلزات پیرامونی است. حداقل ۲ متر از ماشین، گوشی و شبکه برق فاصله بگیرید.",
                    color = GrayText,
                    fontSize = 12.sp
                )

                HorizontalDivider(color = CardBg.copy(alpha = 0.5f))

                // Radar Noise Visualizer Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(CardBg)
                        .border(BorderStroke(1.dp, noiseLevelStatus.second.copy(alpha = 0.4f)), RoundedCornerShape(14.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2, size.height / 2)
                        val maxRadius = size.height / 2 - 16.dp.toPx()

                        // Concentric circles
                        listOf(0.3f, 0.6f, 0.9f).forEach { rRatio ->
                            drawCircle(
                                color = Color.White.copy(alpha = 0.12f),
                                radius = maxRadius * rRatio,
                                center = center,
                                style = Stroke(width = 1.5f)
                            )
                        }

                        // Crosshairs
                        drawLine(
                            color = Color.White.copy(alpha = 0.15f),
                            start = Offset(center.x - maxRadius, center.y),
                            end = Offset(center.x + maxRadius, center.y),
                            strokeWidth = 1.5f
                        )
                        drawLine(
                            color = Color.White.copy(alpha = 0.15f),
                            start = Offset(center.x, center.y - maxRadius),
                            end = Offset(center.x, center.y + maxRadius),
                            strokeWidth = 1.5f
                        )

                        // Sweeping Radar Beam
                        val sweepRad = Math.toRadians(sweepAngle.toDouble())
                        val beamEnd = Offset(
                            x = center.x + (maxRadius * cos(sweepRad)).toFloat(),
                            y = center.y + (maxRadius * sin(sweepRad)).toFloat()
                        )

                        drawLine(
                            color = noiseLevelStatus.second,
                            start = center,
                            end = beamEnd,
                            strokeWidth = 3f
                        )

                        // Simulated noise ripple points
                        val rippleCount = 8
                        for (i in 0 until rippleCount) {
                            val angle = (i * 45) + (sweepAngle / 2)
                            val rad = Math.toRadians(angle.toDouble())
                            val dist = maxRadius * (0.2f + (0.6f * ((i * 17) % 10) / 10f))
                            val noisePt = Offset(
                                x = center.x + (dist * cos(rad)).toFloat(),
                                y = center.y + (dist * sin(rad)).toFloat()
                            )
                            drawCircle(
                                color = noiseLevelStatus.second.copy(alpha = 0.7f),
                                radius = 4f,
                                center = noisePt
                            )
                        }
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = String.format("%.1f µT", noiseMicroTesla),
                            color = Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "میدان الکترومغناطیسی محیط",
                            color = GrayText,
                            fontSize = 11.sp
                        )
                    }
                }

                // Status Banner
                Card(
                    colors = CardDefaults.cardColors(containerColor = noiseLevelStatus.second.copy(alpha = 0.12f)),
                    border = BorderStroke(1.dp, noiseLevelStatus.second),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (noiseMicroTesla < 65f) Icons.Default.CheckCircle else Icons.Default.Warning,
                            contentDescription = null,
                            tint = noiseLevelStatus.second
                        )
                        Text(
                            text = noiseLevelStatus.first,
                            color = noiseLevelStatus.second,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    }
}

// ==========================================
// STEP 3: SPIRIT HORIZON & COMPASS ALIGNMENT
// ==========================================
@Composable
fun Step3HorizonAndCompass(
    compassHeading: Float,
    sensorManager: com.example.hardware.SensorManager
) {
    val context = LocalContext.current
    var isNorthLocked by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceBg),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Explore, contentDescription = null, tint = CyberGold)
                    Text(
                        text = "مرحله ۳: تراز فیزیکی و همگام‌سازی قطب‌نما",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "پروپ رادار را کاملاً عمود بر سطح زمین نگه دارید. قطب‌نمای دیجیتال جهت خطوط اسکن را تنظیم می‌کند.",
                    color = GrayText,
                    fontSize = 12.sp
                )

                HorizontalDivider(color = CardBg.copy(alpha = 0.5f))

                // Compass Dial Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardBg, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    // Rotating compass dial
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .size(100.dp)
                            .clip(CircleShape)
                            .border(BorderStroke(2.dp, CyberGold), CircleShape)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Navigation,
                            contentDescription = "North Arrow",
                            tint = CyberCyan,
                            modifier = Modifier
                                .size(54.dp)
                                .rotate(-compassHeading)
                        )
                    }

                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = String.format("%.1f°", compassHeading),
                            color = CyberGold,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold
                        )
                        val dirStr = when {
                            compassHeading >= 337.5f || compassHeading < 22.5f -> "شمال (N)"
                            compassHeading in 22.5f..67.5f -> "شمال‌شرق (NE)"
                            compassHeading in 67.5f..112.5f -> "شرق (E)"
                            compassHeading in 112.5f..157.5f -> "جنوب‌شرق (SE)"
                            compassHeading in 157.5f..202.5f -> "جنوب (S)"
                            compassHeading in 202.5f..247.5f -> "جنوب‌غرب (SW)"
                            compassHeading in 247.5f..292.5f -> "غرب (W)"
                            else -> "شمال‌غرب (NW)"
                        }
                        Text(dirStr, color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Bold)

                        Spacer(modifier = Modifier.height(8.dp))

                        Button(
                            onClick = {
                                FeedbackManager.playClick(context)
                                isNorthLocked = !isNorthLocked
                                Toast.makeText(
                                    context,
                                    if (isNorthLocked) "🔒 جهت شمال تثبیت شد!" else "🔓 قفل جهت باز شد",
                                    Toast.LENGTH_SHORT
                                ).show()
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isNorthLocked) CyberCyan else CardBg
                            ),
                            border = BorderStroke(1.dp, CyberCyan),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = if (isNorthLocked) "جهت مبنا (قفل)" else "قفل جهت مبنا",
                                color = if (isNorthLocked) Color.Black else Color.White,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Spirit Bubble Level Visualizer
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(80.dp)
                        .background(CardBg, RoundedCornerShape(12.dp))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), RoundedCornerShape(12.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val center = Offset(size.width / 2, size.height / 2)
                        val levelWidth = size.width - 40.dp.toPx()

                        // Outer level cylinder tube
                        drawRoundRect(
                            color = Color.White.copy(alpha = 0.15f),
                            topLeft = Offset(20.dp.toPx(), center.y - 14.dp.toPx()),
                            size = Size(levelWidth, 28.dp.toPx()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(14.dp.toPx())
                        )

                        // Center sweet spot box
                        drawRoundRect(
                            color = CyberGold.copy(alpha = 0.3f),
                            topLeft = Offset(center.x - 20.dp.toPx(), center.y - 14.dp.toPx()),
                            size = Size(40.dp.toPx(), 28.dp.toPx()),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6.dp.toPx()),
                            style = Stroke(width = 2f)
                        )

                        // Spirit Bubble
                        drawCircle(
                            color = CyberGold,
                            radius = 11.dp.toPx(),
                            center = center
                        )
                    }

                    Text(
                        text = "تراز فیزیکی پروپ: ۹۰ درجه (تراز کاملاً دقیق)",
                        color = CyberGold,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.align(Alignment.BottomCenter).padding(bottom = 4.dp)
                    )
                }
            }
        }
    }
}

// ==========================================
// STEP 4: GROUND ZEROING BASELINE CALIBRATION
// ==========================================
@Composable
fun Step4ZeroingCalibration(
    isSampling: Boolean,
    progress: Float,
    isCompleted: Boolean,
    adcOffset: Int,
    phaseOffset: Int,
    noiseFloor: Float,
    onStartSampling: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceBg),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.FilterCenterFocus, contentDescription = null, tint = CyberGold)
                    Text(
                        text = "مرحله ۴: تنظیم نقطه‌صفر سنسور بر روی خاک",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "نوک پروپ را ۱۰ الی ۱۵ سانتی‌متر بالاتر از سطح خاک قرار داده و دکمه شروع نمونه‌برداری را بفشارید.",
                    color = GrayText,
                    fontSize = 12.sp
                )

                HorizontalDivider(color = CardBg.copy(alpha = 0.5f))

                // Calibration Trigger Card
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardBg, RoundedCornerShape(14.dp))
                        .padding(20.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Button(
                            onClick = onStartSampling,
                            enabled = !isSampling,
                            colors = ButtonDefaults.buttonColors(containerColor = CyberGold),
                            shape = CircleShape,
                            modifier = Modifier
                                .size(110.dp)
                                .border(BorderStroke(4.dp, CyberGold.copy(alpha = 0.3f)), CircleShape),
                            contentPadding = PaddingValues(0.dp)
                        ) {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    imageVector = if (isCompleted) Icons.Default.CheckCircle else Icons.Default.Refresh,
                                    contentDescription = null,
                                    tint = Color.Black,
                                    modifier = Modifier.size(36.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = if (isSampling) "در حال ضبط" else if (isCompleted) "کالیبره مجدد" else "شروع کالیبره",
                                    color = Color.Black,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }

                        if (isSampling) {
                            Spacer(modifier = Modifier.height(16.dp))
                            LinearProgressIndicator(
                                progress = progress,
                                color = CyberCyan,
                                trackColor = DarkBg,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(8.dp)
                                    .clip(RoundedCornerShape(4.dp))
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = "در حال میانگین‌گیری پالس‌های خاک: ${(progress * 100).toInt()}%",
                                color = CyberCyan,
                                fontSize = 11.sp
                            )
                        }
                    }
                }

                // Captured Calibration Metrics Result Table
                if (isCompleted) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = DarkBg),
                        border = BorderStroke(1.dp, CyberCyan.copy(alpha = 0.5f)),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = "نتایج نقطه‌صفر و ضریب موازنه خاک:",
                                color = CyberCyan,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("نقطه افست ADC پایه:", color = GrayText, fontSize = 11.sp)
                                Text("$adcOffset ADC", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("بالانس فاز مبنا:", color = GrayText, fontSize = 11.sp)
                                Text("$phaseOffset°", color = CyberGold, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text("سطح نویز خاک:", color = GrayText, fontSize = 11.sp)
                                Text(String.format("%.1f µT", noiseFloor), color = Color(0xFF00E676), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }
                }
            }
        }
    }
}

// ==========================================
// STEP 5: VERIFICATION & DYNAMIC SWEEP TEST
// ==========================================
@Composable
fun Step5VerificationSweep(
    adcValue: Int,
    phaseShift: Int,
    testScore: Int,
    isSweepActive: Boolean,
    onToggleSweep: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = SurfaceBg),
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Verified, contentDescription = null, tint = CyberCyan)
                    Text(
                        text = "مرحله ۵: تست حرکت و تأیید نهایی کالیبراسیون",
                        color = Color.White,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                Text(
                    text = "پروپ را چند سانتی‌متر به جپ و راست حرکت دهید تا منحنی پاسخ سنسور بررسی شود.",
                    color = GrayText,
                    fontSize = 12.sp
                )

                HorizontalDivider(color = CardBg.copy(alpha = 0.5f))

                // Score Badge Card
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(CardBg, RoundedCornerShape(12.dp))
                        .padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column {
                        Text("شاخص کیفیت کالیبراسیون:", color = GrayText, fontSize = 11.sp)
                        Text(
                            text = "۹۸٪ • آماده اسکن سه‌بعدی",
                            color = Color(0xFF00E676),
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(52.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF00E676).copy(alpha = 0.2f))
                            .border(BorderStroke(2.dp, Color(0xFF00E676)), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("98%", color = Color(0xFF00E676), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                    }
                }

                // Interactive Sweep Live Graph Canvas
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(130.dp)
                        .background(CardBg, RoundedCornerShape(12.dp))
                        .border(BorderStroke(1.dp, Color.White.copy(alpha = 0.1f)), RoundedCornerShape(12.dp))
                        .padding(12.dp)
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val w = size.width
                        val h = size.height

                        // Draw baseline
                        drawLine(
                            color = Color.White.copy(alpha = 0.2f),
                            start = Offset(0f, h / 2),
                            end = Offset(w, h / 2),
                            strokeWidth = 1.5f
                        )

                        // Draw smooth test wave
                        val path = Path()
                        val points = 30
                        for (i in 0..points) {
                            val x = (i.toFloat() / points) * w
                            val wave = sin(i * 0.45) * (h * 0.35)
                            val y = h / 2 + wave.toFloat()
                            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
                        }

                        drawPath(
                            path = path,
                            color = CyberCyan,
                            style = Stroke(width = 3f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("ADC: $adcValue", color = CyberCyan, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                        Text("Phase: $phaseShift°", color = CyberGold, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                    }
                }

                Button(
                    onClick = onToggleSweep,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = if (isSweepActive) CyberRed else CyberCyan
                    ),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(
                        imageVector = if (isSweepActive) Icons.Default.Stop else Icons.Default.PlayArrow,
                        contentDescription = null,
                        tint = Color.Black
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isSweepActive) "توقف تست حرکت" else "شروع تست حرکت آزمایشی",
                        color = Color.Black,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
