package com.tsd.ascanner.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.tsd.ascanner.AScannerApp
import com.tsd.ascanner.data.printer.TscPrinterService
import com.tsd.ascanner.utils.ServerPrintRequest
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@Composable
fun AutoPrintOverlay(
    visible: Boolean,
    printRequest: ServerPrintRequest?,
    app: AScannerApp,
    onPrintSuccess: () -> Unit,
    onFallbackToManual: () -> Unit
) {
    if (!visible || printRequest == null) return

    val printerService = app.printerService
    var printProgress by remember { mutableStateOf("Подключение к принтеру...") }
    var printJob by remember { mutableStateOf<Job?>(null) }

    LaunchedEffect(printRequest) {
        printProgress = "Подключение к принтеру..."
        printJob = launch {
            val result = printerService.smartPrintBatch(
                items = printRequest.items,
                onProgress = { current, total ->
                    printProgress = "Печать $current из $total..."
                }
            )

            when (result) {
                is TscPrinterService.PrintResult.Success -> {
                    onPrintSuccess()
                }
                else -> {
                    // NeedPrinterSelection or Error -> fallback to manual dialog
                    onFallbackToManual()
                }
            }
        }
    }

    Dialog(
        onDismissRequest = { /* Ignored */ },
        properties = DialogProperties(
            dismissOnBackPress = false,
            dismissOnClickOutside = false
        )
    ) {
        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(48.dp),
                    color = MaterialTheme.colorScheme.primary
                )
                
                Spacer(modifier = Modifier.height(16.dp))
                
                Text(
                    text = printProgress,
                    style = MaterialTheme.typography.bodyLarge,
                    textAlign = TextAlign.Center
                )
                
                Spacer(modifier = Modifier.height(24.dp))
                
                Button(
                    onClick = {
                        printJob?.cancel()
                        onFallbackToManual()
                    },
                    modifier = Modifier.fillMaxWidth(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                        contentColor = MaterialTheme.colorScheme.onErrorContainer
                    )
                ) {
                    Text("Отмена")
                }
            }
        }
    }
}