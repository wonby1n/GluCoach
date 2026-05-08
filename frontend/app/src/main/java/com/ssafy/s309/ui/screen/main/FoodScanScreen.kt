package com.ssafy.s309.ui.screen.main

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil3.compose.AsyncImage
import com.ssafy.s309.R
import com.ssafy.s309.ui.theme.GlucoachColors
import com.ssafy.s309.ui.theme.GlucoachCorner
import com.ssafy.s309.ui.theme.GlucoachSpacing
import java.io.File

// ── 상태 ──────────────────────────────────────────────

private enum class AnalysisStatus { PENDING, IN_PROGRESS, COMPLETED }

// ── 진입점 ─────────────────────────────────────────────────

@Composable
fun FoodScanContent(
    photoFile: File,
    onBack: () -> Unit,
    onRetakePhoto: () -> Unit,
    onMealSaved: () -> Unit,
    viewModel: FoodScanViewModel = hiltViewModel(),
) {
    val scanState by viewModel.state.collectAsStateWithLifecycle()
    var selectedIndex by remember { mutableIntStateOf(-1) }
    var showSimulation by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.analyze(photoFile)
    }

    LaunchedEffect(scanState) {
        if (scanState is FoodScanState.Saved) onMealSaved()
    }

    when (val s = scanState) {
        is FoodScanState.Idle -> {}

        is FoodScanState.Analyzing -> {
            BackHandler(enabled = false) {}
            AnalyzingScreen(photoFile = photoFile, stage = s.stage)
        }

        is FoodScanState.Result -> {
            if (showSimulation && selectedIndex >= 0 && selectedIndex < s.candidates.size) {
                val candidate = s.candidates[selectedIndex]
                var memo by remember { mutableStateOf("") }
                var selectedDateTime by remember { mutableStateOf(java.time.LocalDateTime.now()) }
                BackHandler { showSimulation = false }
                SimulationScreen(
                    food = candidate,
                    isSaving = s.isSaving,
                    memo = memo,
                    onMemoChange = { memo = it },
                    selectedDateTime = selectedDateTime,
                    onDateTimeChange = { selectedDateTime = it },
                    onBack = { showSimulation = false },
                    onRetakePhoto = onRetakePhoto,
                    onRecordMeal = { viewModel.saveMeal(candidate, photoFile, memo, selectedDateTime) },
                )
            } else {
                BackHandler { onBack() }
                AnalyzingResultScreen(
                    photoFile = photoFile,
                    candidates = s.candidates,
                    selectedIndex = selectedIndex,
                    onSelect = { selectedIndex = it },
                    onViewDetail = { showSimulation = true },
                    onBack = onBack,
                )
            }
        }

        is FoodScanState.Error -> {
            BackHandler { onBack() }
            ErrorScreen(
                message = s.message,
                onRetake = {
                    viewModel.resetError()
                    onRetakePhoto()
                },
                onBack = onBack,
            )
        }

        FoodScanState.Saved -> {}
    }
}

// ── 1. 카메라 화면 ───────────────────────────────────────

@Composable
fun CameraScreen(
    onClose: () -> Unit,
    onPhotoTaken: (File) -> Unit,
) {
    BackHandler { onClose() }
    val context = LocalContext.current
    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            hasCameraPermission = granted
        }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    val imageCapture = remember { ImageCapture.Builder().build() }
    var isTaking by remember { mutableStateOf(false) }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(GlucoachSpacing.xxl))

        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            IconButton(onClick = onClose, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = "닫기",
                    tint = Color.White,
                )
            }
            Text(
                text = "음식 촬영",
                color = Color.White,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

        Box(
            modifier =
                Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(GlucoachColors.PrimaryDark.copy(alpha = 0.6f))
                    .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text(
                text = "음식을 프레임 안에 맞춰주세요",
                color = Color.White,
                fontSize = 14.sp,
            )
        }

        Spacer(modifier = Modifier.height(GlucoachSpacing.xxl))

        Box(
            modifier =
                Modifier
                    .padding(horizontal = 40.dp)
                    .fillMaxWidth()
                    .aspectRatio(1f)
                    .clip(RoundedCornerShape(16.dp))
                    .border(2.dp, GlucoachColors.Primary, RoundedCornerShape(16.dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (hasCameraPermission) {
                CameraPreviewView(imageCapture = imageCapture)
            } else {
                Text(
                    text = "카메라 권한이 필요합니다",
                    color = Color.White,
                    fontSize = 14.sp,
                )
            }
        }

        Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

        Text(
            text = "AI가 음식을 자동으로 인식합니다",
            color = Color.White.copy(alpha = 0.7f),
            fontSize = 14.sp,
        )

        Spacer(modifier = Modifier.weight(1f))

        Box(
            modifier =
                Modifier
                    .size(72.dp)
                    .clip(CircleShape)
                    .border(3.dp, Color.White, CircleShape)
                    .clickable {
                        if (!isTaking) {
                            isTaking = true
                            takePhoto(imageCapture, context) { file -> onPhotoTaken(file) }
                        }
                    },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier =
                    Modifier
                        .size(58.dp)
                        .clip(CircleShape)
                        .background(Color.White),
            )
        }

        Spacer(modifier = Modifier.height(48.dp))
    }
}

@Composable
private fun CameraPreviewView(imageCapture: ImageCapture) {
    val lifecycleOwner = LocalLifecycleOwner.current

    AndroidView(
        factory = { ctx ->
            PreviewView(ctx).also { previewView ->
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener(
                    {
                        val cameraProvider = cameraProviderFuture.get()
                        val preview =
                            Preview.Builder().build().also {
                                it.surfaceProvider = previewView.surfaceProvider
                            }
                        try {
                            cameraProvider.unbindAll()
                            cameraProvider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                imageCapture,
                            )
                        } catch (_: Exception) {
                            // 카메라 미지원 환경에서 무시
                        }
                    },
                    ContextCompat.getMainExecutor(ctx),
                )
            }
        },
        modifier = Modifier.fillMaxSize(),
    )
}

private fun takePhoto(
    imageCapture: ImageCapture,
    context: android.content.Context,
    onResult: (File) -> Unit,
) {
    val photoFile = File.createTempFile("food_", ".jpg", context.cacheDir)
    val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

    imageCapture.takePicture(
        outputOptions,
        ContextCompat.getMainExecutor(context),
        object : ImageCapture.OnImageSavedCallback {
            override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                onResult(photoFile)
            }

            override fun onError(exc: ImageCaptureException) {
                // 에러 시 콜백 없음 — UI에서 재촬영 유도
            }
        },
    )
}

// ── 2. 분석 중 화면 ──────────────────────────────────────

@Composable
private fun AnalyzingScreen(
    photoFile: File,
    stage: Int,
) {
    val labels =
        listOf(
            Triple("AI 음식 탐지", "AI 음식 탐지 중...", "AI 음식 탐지 완료"),
            Triple("영양 정보 검색", "영양 정보 검색 중...", "영양 정보 검색 완료"),
            Triple("분석 완료", "분석 완료 중...", "분석 완료"),
        )

    val statuses =
        List(3) { i ->
            when {
                stage > i + 1 -> AnalysisStatus.COMPLETED
                stage == i + 1 -> AnalysisStatus.IN_PROGRESS
                else -> AnalysisStatus.PENDING
            }
        }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(GlucoachColors.Background),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(GlucoachSpacing.xxl))

        Text(
            text = "분석하는 중이에요",
            color = GlucoachColors.TextPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(GlucoachSpacing.sm))

        Text(
            text = "키키가 음식을 꼼꼼히 살펴보고 있어요",
            color = GlucoachColors.TextSecondary,
            fontSize = 14.sp,
        )

        Spacer(modifier = Modifier.height(GlucoachSpacing.xl))

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .shadow(3.dp, RoundedCornerShape(GlucoachCorner.card))
                    .clip(RoundedCornerShape(GlucoachCorner.card))
                    .background(GlucoachColors.Surface)
                    .padding(GlucoachSpacing.xl),
        ) {
            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                contentAlignment = Alignment.Center,
            ) {
                AsyncImage(
                    model = Uri.fromFile(photoFile),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(160.dp)
                            .clip(RoundedCornerShape(12.dp)),
                    contentScale = ContentScale.Crop,
                )
            }

            Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

            labels.forEachIndexed { index, (pending, progress, completed) ->
                val status = statuses[index]
                AnalysisStepRow(
                    text =
                        when (status) {
                            AnalysisStatus.PENDING -> pending
                            AnalysisStatus.IN_PROGRESS -> progress
                            AnalysisStatus.COMPLETED -> completed
                        },
                    status = status,
                )
                if (index < labels.lastIndex) {
                    Spacer(modifier = Modifier.height(GlucoachSpacing.md))
                }
            }
        }
    }
}

@Composable
private fun AnalysisStepRow(
    text: String,
    status: AnalysisStatus,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
    ) {
        Box(
            modifier =
                Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(
                        when (status) {
                            AnalysisStatus.COMPLETED -> GlucoachColors.Primary
                            AnalysisStatus.IN_PROGRESS -> GlucoachColors.Primary
                            AnalysisStatus.PENDING -> GlucoachColors.Border
                        },
                    ),
            contentAlignment = Alignment.Center,
        ) {
            when (status) {
                AnalysisStatus.COMPLETED ->
                    Icon(
                        imageVector = Icons.Filled.Check,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(16.dp),
                    )
                AnalysisStatus.IN_PROGRESS ->
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        color = Color.White,
                        strokeWidth = 2.dp,
                    )
                AnalysisStatus.PENDING -> {}
            }
        }

        Spacer(modifier = Modifier.width(GlucoachSpacing.md))

        Text(
            text = text,
            color =
                when (status) {
                    AnalysisStatus.PENDING -> GlucoachColors.TextSecondary
                    else -> GlucoachColors.TextPrimary
                },
            fontSize = 15.sp,
            fontWeight =
                when (status) {
                    AnalysisStatus.IN_PROGRESS -> FontWeight.SemiBold
                    else -> FontWeight.Normal
                },
        )
    }
}

// ── 3. 분석 결과 화면 ────────────────────────────────────────

@Composable
private fun AnalyzingResultScreen(
    photoFile: File,
    candidates: List<FoodScanCandidate>,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onViewDetail: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(GlucoachColors.Background),
    ) {
        Spacer(modifier = Modifier.height(GlucoachSpacing.xxl))

        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "뒤로",
                    tint = GlucoachColors.TextPrimary,
                )
            }
            Text(
                text = "인식 결과",
                color = GlucoachColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp)
                    .shadow(3.dp, RoundedCornerShape(GlucoachCorner.card))
                    .clip(RoundedCornerShape(GlucoachCorner.card))
                    .background(GlucoachColors.Surface)
                    .padding(32.dp),
            contentAlignment = Alignment.Center,
        ) {
            AsyncImage(
                model = Uri.fromFile(photoFile),
                contentDescription = null,
                modifier =
                    Modifier
                        .size(160.dp)
                        .clip(RoundedCornerShape(12.dp)),
                contentScale = ContentScale.Crop,
            )
        }

        Spacer(modifier = Modifier.height(GlucoachSpacing.md))

        Column(
            modifier =
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp),
        ) {
            Text(
                text = "인식된 음식",
                color = GlucoachColors.TextPrimary,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(GlucoachSpacing.md))

            candidates.forEachIndexed { index, food ->
                val isSelected = index == selectedIndex
                Row(
                    modifier =
                        Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .border(
                                width = if (isSelected) 2.dp else 1.dp,
                                color = if (isSelected) GlucoachColors.Primary else GlucoachColors.Border,
                                shape = RoundedCornerShape(12.dp),
                            )
                            .background(GlucoachColors.Surface)
                            .clickable { onSelect(index) }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = "${food.rank}",
                        color = GlucoachColors.Primary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Spacer(modifier = Modifier.width(GlucoachSpacing.md))
                    Text(
                        text = food.name,
                        color = GlucoachColors.TextPrimary,
                        fontSize = 16.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = food.kcal?.let { "${it.toInt()}kcal" } ?: "-",
                        color = GlucoachColors.TextSecondary,
                        fontSize = 14.sp,
                    )
                }
                if (index < candidates.lastIndex) {
                    Spacer(modifier = Modifier.height(GlucoachSpacing.sm))
                }
            }

            Spacer(modifier = Modifier.height(GlucoachSpacing.xxl))
        }

        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 22.dp).padding(bottom = GlucoachSpacing.xxl),
        ) {
            Button(
                onClick = onViewDetail,
                enabled = selectedIndex >= 0,
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(24.dp),
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = GlucoachColors.Primary,
                        contentColor = Color.White,
                        disabledContainerColor = GlucoachColors.Border,
                        disabledContentColor = GlucoachColors.TextSecondary,
                    ),
            ) {
                Text(
                    text = "분석 결과 자세히 보기",
                    fontSize = 14.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// ── 4. 식사 기록 화면 ────────────────────────────────────────

@Composable
private fun SimulationScreen(
    food: FoodScanCandidate,
    isSaving: Boolean,
    memo: String,
    onMemoChange: (String) -> Unit,
    selectedDateTime: java.time.LocalDateTime,
    onDateTimeChange: (java.time.LocalDateTime) -> Unit,
    onBack: () -> Unit,
    onRetakePhoto: () -> Unit,
    onRecordMeal: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(GlucoachColors.Background)
                .verticalScroll(rememberScrollState()),
    ) {
        Spacer(modifier = Modifier.height(GlucoachSpacing.xxl))

        Box(modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp)) {
            IconButton(onClick = onBack, modifier = Modifier.align(Alignment.CenterStart)) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "뒤로",
                    tint = GlucoachColors.TextPrimary,
                )
            }
            Text(
                text = "식사 기록",
                color = GlucoachColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Spacer(modifier = Modifier.height(GlucoachSpacing.xl))

        Column(modifier = Modifier.padding(horizontal = 22.dp)) {
            Text(
                text = "칼로리",
                color = GlucoachColors.TextSecondary,
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(GlucoachSpacing.xs))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = food.kcal?.toInt()?.toString() ?: "-",
                    color = GlucoachColors.Primary,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "kcal",
                    color = GlucoachColors.TextPrimary,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(bottom = 8.dp),
                )
            }

            Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

            Box(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .shadow(3.dp, RoundedCornerShape(GlucoachCorner.card))
                        .clip(RoundedCornerShape(GlucoachCorner.card))
                        .background(GlucoachColors.Surface)
                        .padding(GlucoachSpacing.xl),
            ) {
                Box(
                    modifier =
                        Modifier
                            .align(Alignment.TopStart)
                            .clip(RoundedCornerShape(8.dp))
                            .background(GlucoachColors.Primary)
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text(
                        text = food.name,
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                androidx.compose.foundation.Image(
                    painter = painterResource(id = R.drawable.kiki_main),
                    contentDescription = null,
                    modifier =
                        Modifier
                            .size(180.dp)
                            .align(Alignment.Center),
                    contentScale = ContentScale.Fit,
                )
            }

            Spacer(modifier = Modifier.height(GlucoachSpacing.xl))

            NutritionCard(food = food)

            Spacer(modifier = Modifier.height(GlucoachSpacing.xl))

            Text(
                text = "식사 시간",
                color = GlucoachColors.TextSecondary,
                fontSize = 13.sp,
            )
            Spacer(modifier = Modifier.height(GlucoachSpacing.sm))
            com.ssafy.s309.ui.component.MealDateTimePicker(
                initialDateTime = selectedDateTime,
                onDateTimeChanged = onDateTimeChange,
            )

            Spacer(modifier = Modifier.height(GlucoachSpacing.xl))

            OutlinedTextField(
                value = memo,
                onValueChange = onMemoChange,
                modifier = Modifier.fillMaxWidth(),
                placeholder = {
                    Text(
                        text = "메모를 입력하세요",
                        color = GlucoachColors.TextSecondary,
                        fontSize = 14.sp,
                    )
                },
                shape = RoundedCornerShape(GlucoachCorner.card),
                colors =
                    OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = GlucoachColors.Primary,
                        unfocusedBorderColor = GlucoachColors.Border,
                        focusedContainerColor = GlucoachColors.Surface,
                        unfocusedContainerColor = GlucoachColors.Surface,
                    ),
                minLines = 2,
                maxLines = 4,
            )

            Spacer(modifier = Modifier.height(GlucoachSpacing.xl))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(GlucoachSpacing.md),
            ) {
                OutlinedButton(
                    onClick = onRetakePhoto,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors =
                        ButtonDefaults.outlinedButtonColors(
                            contentColor = GlucoachColors.TextPrimary,
                        ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, GlucoachColors.Border),
                ) {
                    Text(
                        text = "다시 찍기",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }

                Button(
                    onClick = onRecordMeal,
                    enabled = !isSaving,
                    modifier = Modifier.weight(1f).height(48.dp),
                    shape = RoundedCornerShape(24.dp),
                    colors =
                        ButtonDefaults.buttonColors(
                            containerColor = GlucoachColors.Primary,
                            contentColor = Color.White,
                            disabledContainerColor = GlucoachColors.Primary.copy(alpha = 0.6f),
                            disabledContentColor = Color.White,
                        ),
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            color = Color.White,
                            strokeWidth = 2.dp,
                        )
                    } else {
                        Text(
                            text = "식사 기록하기",
                            fontSize = 14.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(GlucoachSpacing.xxl))
        }
    }
}

@Composable
private fun NutritionCard(food: FoodScanCandidate) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .shadow(3.dp, RoundedCornerShape(GlucoachCorner.card))
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(GlucoachColors.Surface)
                .padding(GlucoachSpacing.xl),
    ) {
        Text(
            text = "영양 성분 (1인분 기준)",
            color = GlucoachColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            NutritionItem(
                label = "탄수화물",
                value = food.carbsG?.let { "${it.toInt()}g" } ?: "-",
            )
            NutritionItem(
                label = "단백질",
                value = food.proteinG?.let { "${it.toInt()}g" } ?: "-",
            )
            NutritionItem(
                label = "지방",
                value = food.fatG?.let { "${it.toInt()}g" } ?: "-",
            )
        }
    }
}

@Composable
private fun NutritionItem(
    label: String,
    value: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = value,
            color = GlucoachColors.Primary,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = label,
            color = GlucoachColors.TextSecondary,
            fontSize = 12.sp,
        )
    }
}

// ── 5. 에러 화면 ─────────────────────────────────────────────

@Composable
private fun ErrorScreen(
    message: String,
    onRetake: () -> Unit,
    onBack: () -> Unit,
) {
    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(GlucoachColors.Background)
                .padding(horizontal = 22.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        androidx.compose.foundation.Image(
            painter = painterResource(id = R.drawable.kiki_main),
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            contentScale = ContentScale.Fit,
        )

        Spacer(modifier = Modifier.height(GlucoachSpacing.xl))

        Text(
            text = message,
            color = GlucoachColors.TextPrimary,
            fontSize = 16.sp,
            textAlign = TextAlign.Center,
        )

        Spacer(modifier = Modifier.height(GlucoachSpacing.xxl))

        Button(
            onClick = onRetake,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(24.dp),
            colors =
                ButtonDefaults.buttonColors(
                    containerColor = GlucoachColors.Primary,
                    contentColor = Color.White,
                ),
        ) {
            Text(text = "다시 촬영하기", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }

        Spacer(modifier = Modifier.height(GlucoachSpacing.md))

        OutlinedButton(
            onClick = onBack,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(24.dp),
            colors =
                ButtonDefaults.outlinedButtonColors(contentColor = GlucoachColors.TextPrimary),
            border = androidx.compose.foundation.BorderStroke(1.dp, GlucoachColors.Border),
        ) {
            Text(text = "홈으로 돌아가기", fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
        }
    }
}

// ── 글루코스 예측 차트 (레거시, 현재 미사용) ────────────────────

@Suppress("unused")
@Composable
private fun GlucosePredictionChart(
    baseGlucose: Int,
    peakGlucose: Int,
    afterTwoHours: Int,
) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .shadow(3.dp, RoundedCornerShape(GlucoachCorner.card))
                .clip(RoundedCornerShape(GlucoachCorner.card))
                .background(GlucoachColors.Surface)
                .padding(GlucoachSpacing.xl),
    ) {
        Text(
            text = "예상 혈당 반응",
            color = GlucoachColors.TextPrimary,
            fontSize = 14.sp,
            fontWeight = FontWeight.Bold,
        )

        Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

        val chartHeight = 120.dp

        Box(modifier = Modifier.fillMaxWidth().height(chartHeight)) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val w = size.width
                val h = size.height
                val minVal = 80f
                val maxVal = 170f
                val range = maxVal - minVal

                fun yFor(v: Float) = h - ((v - minVal) / range) * h

                val points =
                    listOf(
                        0f to baseGlucose.toFloat(),
                        w * 0.45f to peakGlucose.toFloat(),
                        w to afterTwoHours.toFloat(),
                    )

                val path =
                    Path().apply {
                        moveTo(points[0].first, yFor(points[0].second))
                        val cp1x = (points[0].first + points[1].first) / 2f
                        cubicTo(
                            cp1x,
                            yFor(points[0].second),
                            cp1x,
                            yFor(points[1].second),
                            points[1].first,
                            yFor(points[1].second),
                        )
                        val cp2x = (points[1].first + points[2].first) / 2f
                        cubicTo(
                            cp2x,
                            yFor(points[1].second),
                            cp2x,
                            yFor(points[2].second),
                            points[2].first,
                            yFor(points[2].second),
                        )
                    }
                drawPath(
                    path = path,
                    color = GlucoachColors.ChartLineInactive,
                    style = Stroke(width = 2.5f, cap = StrokeCap.Round),
                )

                drawCircle(
                    color = GlucoachColors.Primary,
                    radius = 6f,
                    center = Offset(points[0].first, yFor(points[0].second)),
                )
            }
        }

        Spacer(modifier = Modifier.height(GlucoachSpacing.md))

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            ChartLabel(title = "식전", value = "${baseGlucose}mg/dL")
            ChartLabel(title = "식후 최고점", value = "${peakGlucose}mg/dL")
            ChartLabel(title = "2시간 후", value = "${afterTwoHours}mg/dL")
        }
    }
}

@Suppress("unused")
@Composable
private fun ChartLabel(
    title: String,
    value: String,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(
            text = title,
            color = GlucoachColors.TextSecondary,
            fontSize = 11.sp,
        )
        Text(
            text = value,
            color = GlucoachColors.Primary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Bold,
        )
    }
}
