package com.ssafy.s309.ui.screen.ble

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Bluetooth
import androidx.compose.material.icons.outlined.BluetoothSearching
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssafy.s309.data.ble.BleConnectionState
import com.ssafy.s309.data.ble.ScannedDevice
import com.ssafy.s309.ui.theme.Primary
import kotlinx.coroutines.delay

@Composable
fun BleScreen(
    onBack: () -> Unit = {},
    viewModel: BleViewModel = hiltViewModel(),
) {
    val connectionState by viewModel.connectionState.collectAsStateWithLifecycle()
    val scannedDevices by viewModel.scannedDevices.collectAsStateWithLifecycle()

    val blePermissions =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            arrayOf(Manifest.permission.BLUETOOTH_SCAN, Manifest.permission.BLUETOOTH_CONNECT)
        } else {
            arrayOf(Manifest.permission.ACCESS_FINE_LOCATION)
        }

    val permissionLauncher =
        rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { results ->
            if (results.values.all { it }) viewModel.startScan()
        }

    DisposableEffect(Unit) {
        permissionLauncher.launch(blePermissions)
        onDispose { viewModel.stopScan() }
    }

    // 연결 성공 시 3초 후 자동으로 뒤로가기
    LaunchedEffect(connectionState) {
        if (connectionState is BleConnectionState.Connected) {
            delay(3_000)
            onBack()
        }
    }

    Column(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color(0xFFF2F4F5)),
    ) {
        Column(
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 52.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Outlined.ArrowBack,
                    contentDescription = "뒤로가기",
                    tint = Color(0xFF333333),
                    modifier = Modifier.size(28.dp).clickable(onClick = onBack),
                )
                Text(
                    text = "기기 연동",
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color(0xFF333333),
                )
                Spacer(modifier = Modifier.size(28.dp))
            }

            Spacer(modifier = Modifier.height(32.dp))

            when (val state = connectionState) {
                is BleConnectionState.Connected ->
                    ConnectedView(
                        device = state.device,
                        onDisconnect = viewModel::disconnect,
                    )

                is BleConnectionState.Connecting -> ConnectingView(device = state.device)

                else ->
                    ScanView(
                        isScanning = connectionState is BleConnectionState.Scanning,
                        devices = scannedDevices,
                        onScanToggle = {
                            if (connectionState is BleConnectionState.Scanning) {
                                viewModel.stopScan()
                            } else {
                                viewModel.startScan()
                            }
                        },
                        onConnect = viewModel::connect,
                    )
            }
        }
    }
}

@Composable
private fun ConnectedView(
    device: ScannedDevice,
    onDisconnect: () -> Unit,
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        Box(
            modifier =
                Modifier
                    .size(80.dp)
                    .background(Color(0xFFE8F7FA), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.CheckCircle,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(44.dp),
            )
        }
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "연결됐어요!",
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF333333),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = device.name ?: device.address,
            fontSize = 16.sp,
            color = Color(0xFF888888),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(
            text = device.address,
            fontSize = 12.sp,
            color = Color(0xFFAAAAAA),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = "잠시 후 자동으로 이동합니다",
            fontSize = 13.sp,
            color = Color(0xFFAAAAAA),
        )
        Spacer(modifier = Modifier.height(32.dp))
        OutlinedButton(
            onClick = onDisconnect,
            shape = RoundedCornerShape(12.dp),
        ) {
            Text(text = "연결 해제", color = Color(0xFF888888))
        }
    }
}

@Composable
private fun ConnectingView(device: ScannedDevice) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(modifier = Modifier.height(40.dp))
        CircularProgressIndicator(color = Primary, modifier = Modifier.size(52.dp))
        Spacer(modifier = Modifier.height(24.dp))
        Text(
            text = "연결 중...",
            fontSize = 18.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF333333),
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = device.name ?: device.address,
            fontSize = 14.sp,
            color = Color(0xFF888888),
        )
    }
}

@Composable
private fun ScanView(
    isScanning: Boolean,
    devices: List<ScannedDevice>,
    onScanToggle: () -> Unit,
    onConnect: (ScannedDevice) -> Unit,
) {
    Column(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = "주변 기기",
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF333333),
            )
            Button(
                onClick = onScanToggle,
                colors =
                    ButtonDefaults.buttonColors(
                        containerColor = if (isScanning) Color(0xFFEEEEEE) else Primary,
                    ),
                shape = RoundedCornerShape(10.dp),
            ) {
                if (isScanning) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(14.dp),
                        color = Primary,
                        strokeWidth = 2.dp,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "스캔 중지", color = Color(0xFF555555), fontSize = 13.sp)
                } else {
                    Icon(
                        imageVector = Icons.Outlined.BluetoothSearching,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = Color.White,
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(text = "스캔", fontSize = 13.sp)
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        if (devices.isEmpty()) {
            EmptyDeviceList(isScanning = isScanning)
        } else {
            Column(
                modifier =
                    Modifier
                        .fillMaxWidth()
                        .shadow(4.dp, RoundedCornerShape(16.dp))
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color.White),
            ) {
                devices.forEachIndexed { index, device ->
                    DeviceItem(device = device, onConnect = { onConnect(device) })
                    if (index < devices.lastIndex) {
                        HorizontalDivider(color = Color(0xFFF0F0F0))
                    }
                }
            }
        }
    }
}

@Composable
private fun EmptyDeviceList(isScanning: Boolean) {
    Column(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(vertical = 60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            imageVector = Icons.Outlined.Bluetooth,
            contentDescription = null,
            tint = Color(0xFFCCCCCC),
            modifier = Modifier.size(48.dp),
        )
        Spacer(modifier = Modifier.height(12.dp))
        Text(
            text = if (isScanning) "기기를 검색하고 있어요" else "발견된 기기가 없어요",
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = Color(0xFF888888),
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (isScanning) "CGM 패치가 근처에 있는지 확인해 주세요" else "스캔 버튼을 눌러 기기를 검색하세요",
            fontSize = 13.sp,
            color = Color(0xFFAAAAAA),
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun DeviceItem(
    device: ScannedDevice,
    onConnect: () -> Unit,
) {
    Row(
        modifier =
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier =
                Modifier
                    .size(40.dp)
                    .background(Color(0xFFE8F7FA), CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Outlined.Bluetooth,
                contentDescription = null,
                tint = Primary,
                modifier = Modifier.size(20.dp),
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name ?: "알 수 없는 기기",
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                color = Color(0xFF333333),
            )
            Text(
                text = "${device.address}  ·  ${device.rssi} dBm",
                fontSize = 12.sp,
                color = Color(0xFFAAAAAA),
            )
        }
        Button(
            onClick = onConnect,
            colors = ButtonDefaults.buttonColors(containerColor = Primary),
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier.height(34.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp),
        ) {
            Text(text = "연결", fontSize = 13.sp)
        }
    }
}
