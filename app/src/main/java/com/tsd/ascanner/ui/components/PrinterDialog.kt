package com.tsd.ascanner.ui.components

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bluetooth
import androidx.compose.material.icons.filled.BluetoothConnected
import androidx.compose.material.icons.filled.Print
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Divider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import com.tsd.ascanner.AScannerApp
import com.tsd.ascanner.data.printer.TscPrinterService
import com.tsd.ascanner.ui.theme.AppTheme
import kotlinx.coroutines.launch

/**
 * Dialog for managing TSC RE310 printer connection and test printing
 */
@SuppressLint("MissingPermission")
@Composable
fun PrinterDialog(
    visible: Boolean,
    onDismiss: () -> Unit
) {
    if (!visible) return

    val context = LocalContext.current
    val app = context.applicationContext as AScannerApp
    val printerService = app.printerService
    val scope = rememberCoroutineScope()
    val colors = AppTheme.colors

    val connectionState by printerService.connectionState.collectAsState()
    val lastError by printerService.lastError.collectAsState()

    var pairedDevices by remember { mutableStateOf<List<BluetoothDevice>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var printResult by remember { mutableStateOf<String?>(null) }
    var hasPermissions by remember { mutableStateOf(printerService.hasBluetoothPermissions()) }

    // Permission launcher
    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        hasPermissions = permissions.values.all { it }
        if (hasPermissions) {
            pairedDevices = printerService.getPairedDevices()
        }
    }

    // Request permissions on first show
    LaunchedEffect(visible) {
        if (visible && !hasPermissions) {
            val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                arrayOf(
                    Manifest.permission.BLUETOOTH_CONNECT,
                    Manifest.permission.BLUETOOTH_SCAN
                )
            } else {
                arrayOf(
                    Manifest.permission.BLUETOOTH,
                    Manifest.permission.BLUETOOTH_ADMIN,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
            }
            permissionLauncher.launch(permissions)
        } else if (visible && hasPermissions) {
            pairedDevices = printerService.getPairedDevices()
        }
    }

    // Refresh devices list
    fun refreshDevices() {
        if (hasPermissions) {
            pairedDevices = printerService.getPairedDevices()
        }
    }

    // Connect to device
    fun connectToDevice(device: BluetoothDevice) {
        scope.launch {
            isLoading = true
            printResult = null
            printerService.connect(device)
            isLoading = false
        }
    }

    // Disconnect
    fun disconnect() {
        printerService.disconnect()
        printResult = null
    }

    // Print test label (bitmap)
    fun printTestLabel() {
        scope.launch {
            isLoading = true
            printResult = null
            val success = printerService.printTestBitmap()
            printResult = if (success) "Bitmap отправлен на печать" else "Ошибка печати"
            isLoading = false
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(
            dismissOnBackPress = true,
            dismissOnClickOutside = true,
            usePlatformDefaultWidth = false
        ),
        modifier = Modifier
            .fillMaxWidth(0.95f)
            .padding(16.dp),
        containerColor = colors.background,
        title = {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = if (connectionState == TscPrinterService.ConnectionState.CONNECTED) 
                            Icons.Default.BluetoothConnected else Icons.Default.Bluetooth,
                        contentDescription = null,
                        tint = if (connectionState == TscPrinterService.ConnectionState.CONNECTED) 
                            Color(0xFF4CAF50) else colors.textPrimary
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Принтер TSC RE310",
                        style = MaterialTheme.typography.titleLarge,
                        color = colors.textPrimary
                    )
                }
                IconButton(onClick = { refreshDevices() }) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Обновить",
                        tint = colors.textSecondary
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {
                // Status section
                when (connectionState) {
                    TscPrinterService.ConnectionState.CONNECTED -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8F5E9)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(
                                    text = "Подключено",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = Color(0xFF2E7D32),
                                    fontWeight = FontWeight.Bold
                                )
                                printerService.connectedDevice?.let { device ->
                                    Text(
                                        text = "${device.name ?: "Unknown"} (${device.address})",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFF388E3C)
                                    )
                                }
                            }
                        }
                        
                        Spacer(Modifier.height(16.dp))
                        
                        // Print test button
                        Button(
                            onClick = { printTestLabel() },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isLoading,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.primary
                            )
                        ) {
                            if (isLoading) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(20.dp),
                                    color = Color.White,
                                    strokeWidth = 2.dp
                                )
                            } else {
                                Icon(Icons.Default.Print, contentDescription = null)
                            }
                            Spacer(Modifier.width(8.dp))
                            Text("Тестовая печать")
                        }
                        
                        Spacer(Modifier.height(8.dp))
                        
                        // Disconnect button
                        OutlinedButton(
                            onClick = { disconnect() },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Отключиться")
                        }
                    }
                    
                    TscPrinterService.ConnectionState.CONNECTING -> {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF3E0)),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                CircularProgressIndicator(
                                    modifier = Modifier.size(24.dp),
                                    strokeWidth = 2.dp
                                )
                                Spacer(Modifier.width(12.dp))
                                Text(
                                    text = "Подключение...",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFE65100)
                                )
                            }
                        }
                    }
                    
                    TscPrinterService.ConnectionState.DISCONNECTED -> {
                        if (!hasPermissions) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(
                                        text = "Требуются разрешения Bluetooth",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFFC62828)
                                    )
                                    Spacer(Modifier.height(8.dp))
                                    Button(
                                        onClick = {
                                            val permissions = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                                                arrayOf(
                                                    Manifest.permission.BLUETOOTH_CONNECT,
                                                    Manifest.permission.BLUETOOTH_SCAN
                                                )
                                            } else {
                                                arrayOf(
                                                    Manifest.permission.BLUETOOTH,
                                                    Manifest.permission.BLUETOOTH_ADMIN,
                                                    Manifest.permission.ACCESS_FINE_LOCATION
                                                )
                                            }
                                            permissionLauncher.launch(permissions)
                                        }
                                    ) {
                                        Text("Запросить разрешения")
                                    }
                                }
                            }
                        } else if (!printerService.isBluetoothEnabled()) {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = "Bluetooth выключен. Включите Bluetooth в настройках устройства.",
                                    modifier = Modifier.padding(12.dp),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = Color(0xFFC62828)
                                )
                            }
                        } else {
                            Text(
                                text = "Выберите сопряжённое устройство:",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colors.textSecondary
                            )
                            
                            Spacer(Modifier.height(8.dp))
                            
                            if (pairedDevices.isEmpty()) {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFFFFF8E1)),
                                    shape = RoundedCornerShape(8.dp)
                                ) {
                                    Text(
                                        text = "Нет сопряжённых Bluetooth устройств.\nСначала выполните сопряжение с принтером в настройках Bluetooth.",
                                        modifier = Modifier.padding(12.dp),
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFFF57F17)
                                    )
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.height(200.dp)
                                ) {
                                    items(pairedDevices) { device ->
                                        DeviceItem(
                                            device = device,
                                            onClick = { connectToDevice(device) },
                                            isTscPrinter = printerService.getTscPrinters().contains(device)
                                        )
                                        Divider(color = colors.progressTrack)
                                    }
                                }
                            }
                        }
                    }
                }
                
                // Error message
                lastError?.let { error ->
                    Spacer(Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFFFEBEE)),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = error,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = Color(0xFFC62828)
                        )
                    }
                }
                
                // Print result message
                printResult?.let { result ->
                    Spacer(Modifier.height(8.dp))
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(
                            containerColor = if (result.contains("Ошибка")) Color(0xFFFFEBEE) else Color(0xFFE8F5E9)
                        ),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = result,
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodyMedium,
                            color = if (result.contains("Ошибка")) Color(0xFFC62828) else Color(0xFF2E7D32)
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Закрыть", color = colors.textPrimary)
            }
        }
    )
}

@SuppressLint("MissingPermission")
@Composable
private fun DeviceItem(
    device: BluetoothDevice,
    onClick: () -> Unit,
    isTscPrinter: Boolean
) {
    val colors = AppTheme.colors
    
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = Icons.Default.Bluetooth,
            contentDescription = null,
            tint = if (isTscPrinter) Color(0xFF1976D2) else colors.textSecondary
        )
        Spacer(Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = device.name ?: "Unknown Device",
                style = MaterialTheme.typography.bodyLarge,
                color = colors.textPrimary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                fontWeight = if (isTscPrinter) FontWeight.Bold else FontWeight.Normal
            )
            Text(
                text = device.address,
                style = MaterialTheme.typography.bodySmall,
                color = colors.textSecondary
            )
        }
        if (isTscPrinter) {
            Text(
                text = "TSC",
                style = MaterialTheme.typography.labelSmall,
                color = Color(0xFF1976D2),
                fontWeight = FontWeight.Bold
            )
        }
    }
}
