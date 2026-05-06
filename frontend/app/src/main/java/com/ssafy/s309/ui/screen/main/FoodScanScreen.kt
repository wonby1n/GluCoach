package com.ssafy.s309.ui.screen.main

import android.Manifest
import android.content.pm.PackageManager
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
import androidx.compose.foundation.Image
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ssafy.s309.R
import com.ssafy.s309.data.model.FoodSearchItem
import com.ssafy.s309.ui.theme.GlucoachColors
import com.ssafy.s309.ui.theme.GlucoachCorner
import com.ssafy.s309.ui.theme.GlucoachSpacing
import kotlinx.coroutines.delay
import java.io.File

// ── 상태 ──────────────────────────────────────────────

private enum class ScanStep { ANALYZING, RESULT, SIMULATION }

private enum class AnalysisStatus { PENDING, IN_PROGRESS, COMPLETED }

// ── 진입점 (카메라 제외, 탭 콘텐츠 영역에서 렌더링) ─────

@Composable
fun FoodScanContent(
    onBack: () -> Unit,
    onRetakePhoto: () -> Unit,
    photoFile: File? = null,
    viewModel: FoodScanViewModel = hiltViewModel(),
) {
    LaunchedEffect(photoFile) {
        viewModel.setPhotoFile(photoFile)
    }

    val uiState by viewModel.uiState.collectAsState()
    var step by remember { mutableStateOf(ScanStep.ANALYZING) }

    when (step) {
        ScanStep.ANALYZING -> {
            BackHandler(enabled = false) { }
            AnalyzingScreen(
                onComplete = { step = ScanStep.RESULT },
            )
        }

        ScanStep.RESULT -> {
            BackHandler { onBack() }
            FoodSearchResultScreen(
                uiState = uiState,
                onQueryChange = viewModel::searchFoods,
                onSelect = { food ->
                    viewModel.selectFood(food)
                    step = ScanStep.SIMULATION
                },
                onBack = onBack,
            )
        }

        ScanStep.SIMULATION -> {
            BackHandler { step = ScanStep.RESULT }
            val food = uiState.selectedFood
            if (food != null) {
                SimulationScreen(
                    food = food,
                    predictedRise = viewModel.estimatePredictedRise(food),
                    isSaving = uiState.isSaving,
                    saveSuccess = uiState.saveSuccess,
                    onBack = {
                        viewModel.clearSelection()
                        step = ScanStep.RESULT
                    },
                    onRetakePhoto = {
                        viewModel.clearSelection()
                        onRetakePhoto()
                    },
                    onRecordMeal = viewModel::recordMeal,
                )
            } else {
                step = ScanStep.RESULT
            }
        }
    }
}

// ── 1. 카메라 화면 (FoodScan.png) ───────────────────────

@Composable
fun CameraScreen(
    onClose: () -> Unit,
    onPhotoTaken: (File?) -> Unit,
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

        // 상단 바: X + 제목
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

        // 안내 배지
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

        // 카메라 프리뷰
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

        // 셔터 버튼
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
                            // 카메라를 지원하지 않는 에뮬레이터·기기에서 안전하게 무시
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
    onResult: (File?) -> Unit,
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
                onResult(null)
            }
        },
    )
}

// ── 2. 분석 중 화면 (FoodAnalyzing.png) ─────────────────

@Composable
private fun AnalyzingScreen(onComplete: () -> Unit) {
    val steps =
        remember {
            mutableStateListOf(
                AnalysisStatus.PENDING,
                AnalysisStatus.PENDING,
                AnalysisStatus.PENDING,
                AnalysisStatus.PENDING,
            )
        }
    var currentStep by remember { mutableIntStateOf(0) }

    val labels =
        listOf(
            Triple("사진 업로드", "사진 업로드 중...", "사진 업로드 완료"),
            Triple("음식 종류 인식", "음식 종류 인식 중...", "음식 종류 인식 완료"),
            Triple("영양 성분 계산", "영양 성분 계산 중...", "영양 성분 계산 완료"),
            Triple("혈당 예측 분석", "혈당 예측 분석 중...", "혈당 예측 분석 완료"),
        )

    LaunchedEffect(Unit) {
        for (i in 0 until 4) {
            steps[i] = AnalysisStatus.IN_PROGRESS
            currentStep = i
            delay(1200)
            steps[i] = AnalysisStatus.COMPLETED
            currentStep = i + 1
        }
        delay(500)
        onComplete()
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(GlucoachColors.Background),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // 상단 이미지 영역
        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(260.dp)
                    .clip(RoundedCornerShape(bottomStart = 32.dp, bottomEnd = 32.dp))
                    .background(GlucoachColors.Primary.copy(alpha = 0.15f)),
            contentAlignment = Alignment.Center,
        ) {
            Image(
                painter = painterResource(id = R.drawable.jjajangmyeon),
                contentDescription = null,
                modifier = Modifier.size(160.dp),
                contentScale = ContentScale.Fit,
            )
        }

        Spacer(modifier = Modifier.height(GlucoachSpacing.xl))

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
        Text(
            text = "잠깐만 기다려 주세요",
            color = GlucoachColors.TextSecondary,
            fontSize = 14.sp,
        )

        Spacer(modifier = Modifier.height(GlucoachSpacing.xl))

        // 진행 상태 카드
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
            // 마스코트
            Image(
                painter = painterResource(id = R.drawable.kiki_main),
                contentDescription = null,
                modifier =
                    Modifier
                        .size(100.dp)
                        .align(Alignment.CenterHorizontally),
                contentScale = ContentScale.Fit,
            )

            Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

            labels.forEachIndexed { index, (pending, progress, completed) ->
                val status = steps[index]
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
            if (status == AnalysisStatus.COMPLETED) {
                Icon(
                    imageVector = Icons.Filled.Check,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(16.dp),
                )
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

// ── 3. 음식 검색 화면 ────────────────────────────────────

@Composable
private fun FoodSearchResultScreen(
    uiState: FoodScanUiState,
    onQueryChange: (String) -> Unit,
    onSelect: (FoodSearchItem) -> Unit,
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
                text = "음식 검색",
                color = GlucoachColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Spacer(modifier = Modifier.height(GlucoachSpacing.lg))

        OutlinedTextField(
            value = uiState.searchQuery,
            onValueChange = onQueryChange,
            placeholder = { Text("음식 이름을 검색하세요", color = GlucoachColors.TextSecondary) },
            modifier =
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp),
            shape = RoundedCornerShape(12.dp),
            colors =
                OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = GlucoachColors.Primary,
                    unfocusedBorderColor = GlucoachColors.Border,
                ),
            singleLine = true,
            trailingIcon = {
                if (uiState.isSearching) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = GlucoachColors.Primary,
                    )
                }
            },
        )

        Spacer(modifier = Modifier.height(GlucoachSpacing.md))

        Column(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 22.dp),
        ) {
            if (uiState.searchResults.isEmpty() && uiState.searchQuery.isBlank()) {
                Spacer(modifier = Modifier.height(GlucoachSpacing.xxl))
                Text(
                    text = "음식 이름을 입력하면 검색 결과가 나타납니다",
                    color = GlucoachColors.TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            } else if (uiState.searchResults.isEmpty() && !uiState.isSearching && uiState.searchQuery.isNotBlank()) {
                Spacer(modifier = Modifier.height(GlucoachSpacing.xxl))
                Text(
                    text = "검색 결과가 없습니다",
                    color = GlucoachColors.TextSecondary,
                    fontSize = 14.sp,
                    modifier = Modifier.align(Alignment.CenterHorizontally),
                )
            } else {
                uiState.searchResults.forEachIndexed { index, food ->
                    Row(
                        modifier =
                            Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(12.dp))
                                .border(1.dp, GlucoachColors.Border, RoundedCornerShape(12.dp))
                                .background(GlucoachColors.Surface)
                                .clickable { onSelect(food) }
                                .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = food.name,
                                color = GlucoachColors.TextPrimary,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold,
                            )
                            if (food.category != null) {
                                Text(
                                    text = food.category,
                                    color = GlucoachColors.TextSecondary,
                                    fontSize = 12.sp,
                                )
                            }
                        }
                        if (food.kcal != null) {
                            Text(
                                text = "${food.kcal.toInt()}kcal",
                                color = GlucoachColors.TextSecondary,
                                fontSize = 14.sp,
                            )
                        }
                    }
                    if (index < uiState.searchResults.lastIndex) {
                        Spacer(modifier = Modifier.height(GlucoachSpacing.sm))
                    }
                }
            }
            Spacer(modifier = Modifier.height(GlucoachSpacing.xxl))
        }
    }
}

// ── 4. 시뮬레이션 화면 ──────────────────────────────────

@Composable
private fun SimulationScreen(
    food: FoodSearchItem,
    predictedRise: Int,
    isSaving: Boolean,
    saveSuccess: Boolean,
    onBack: () -> Unit,
    onRetakePhoto: () -> Unit,
    onRecordMeal: () -> Unit,
) {
    val baseGlucose = 110
    val peakGlucose = baseGlucose + predictedRise
    val afterTwoHours = baseGlucose + (predictedRise * 0.3).toInt()

    LaunchedEffect(saveSuccess) {
        if (saveSuccess) onBack()
    }

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
                text = "식사기록",
                color = GlucoachColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        Spacer(modifier = Modifier.height(GlucoachSpacing.xl))

        Column(modifier = Modifier.padding(horizontal = 22.dp)) {
            Text(
                text = "식후 예상 혈당 상승",
                color = GlucoachColors.TextSecondary,
                fontSize = 14.sp,
            )
            Spacer(modifier = Modifier.height(GlucoachSpacing.xs))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = "+$predictedRise",
                    color = GlucoachColors.Primary,
                    fontSize = 48.sp,
                    fontWeight = FontWeight.Bold,
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = "mg/dL",
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

                Image(
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

            Text(
                text = if (predictedRise <= 20) "식후 완만한" else "식후 주의가 필요한",
                color = GlucoachColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )
            Text(
                text = "혈당 상승이 예상돼요",
                color = GlucoachColors.TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold,
            )

            Spacer(modifier = Modifier.height(GlucoachSpacing.xl))

            GlucosePredictionChart(
                baseGlucose = baseGlucose,
                peakGlucose = peakGlucose,
                afterTwoHours = afterTwoHours,
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
                        ),
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = Color.White,
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

        Box(
            modifier =
                Modifier
                    .fillMaxWidth()
                    .height(chartHeight),
        ) {
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

                // 커브 그리기
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

                // 시작점 dot
                drawCircle(
                    color = GlucoachColors.Primary,
                    radius = 6f,
                    center = Offset(points[0].first, yFor(points[0].second)),
                )
            }
        }

        Spacer(modifier = Modifier.height(GlucoachSpacing.md))

        // 라벨
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
