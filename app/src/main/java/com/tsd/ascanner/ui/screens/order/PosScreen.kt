package com.tsd.ascanner.ui.screens.order

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.border
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.IconButton
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tsd.ascanner.AScannerApp
import com.tsd.ascanner.ui.theme.AppTheme
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.height
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.launch
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.tsd.ascanner.utils.DataWedge
import com.tsd.ascanner.utils.ScanTriggerBus
import kotlinx.coroutines.flow.collectLatest
import com.tsd.ascanner.utils.ErrorBus
import com.tsd.ascanner.utils.ServerDialogShownException
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tsd.ascanner.utils.DebugFlags
import com.tsd.ascanner.utils.DebugSession

@Composable
fun PosScreen(
    paddingValues: PaddingValues,
    onClose: () -> Unit,
    onScanPosition: (String) -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val app = ctx.applicationContext as AScannerApp
    val posState = remember { mutableStateOf(app.docsService.currentPos) }
    val pos = posState.value
    val colors = AppTheme.colors
    val scope = rememberCoroutineScope()
    val isScanning = remember { mutableStateOf(false) }
    val scanError = remember { mutableStateOf<String?>(null) }
    val lastScan = remember { mutableStateOf<String?>(null) }
    val isRequesting = remember { mutableStateOf(false) }
    val showDeleteAll = remember { mutableStateOf(false) }
    val pendingDeleteItemId = remember { mutableStateOf<String?>(null) }
    val posLoading = remember { mutableStateOf(false) }
    val showCamera = remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
    LaunchedEffect(lastScan.value) {
        val hasText = !lastScan.value.isNullOrBlank()
        if (hasText) {
            kotlinx.coroutines.delay(15000)
            lastScan.value = null
        }
    }

    fun handleScan(code: String) {
        val currentFormId = posState.value?.formId ?: app.docsService.currentPos?.formId
        if (code.length < 4 || currentFormId.isNullOrBlank()) return
        lastScan.value = code
        scanError.value = null
        scope.launch {
            try {
                isRequesting.value = true
                when (val res = app.docsService.scanPosMark(currentFormId, code)) {
                    is com.tsd.ascanner.data.docs.ScanPosResult.Success -> {
                        posState.value = app.docsService.currentPos
                        isScanning.value = false
                    }
                    is com.tsd.ascanner.data.docs.ScanPosResult.Error -> {
                        scanError.value = res.message
                        isScanning.value = true
                    }
					is com.tsd.ascanner.data.docs.ScanPosResult.DialogShown -> {
						// Dialog will be shown via global DialogBus
						isScanning.value = false
					}
                }
            } catch (e: Exception) {
                scanError.value = e.message ?: "Ошибка запроса"
                isScanning.value = true
            } finally {
                isRequesting.value = false
            }
        }
    }

    // Refresh POS whenever screen is resumed (avoid stale cache)
    run {
        val lifecycleOwner = LocalLifecycleOwner.current
		// Skip the very first ON_RESUME after entering screen (prefetched in DocScreen)
		val skipNextOnResume = remember { mutableStateOf(true) }
		androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
					if (skipNextOnResume.value) {
						skipNextOnResume.value = false
					} else {
						val currentFormId = posState.value?.formId ?: app.docsService.currentPos?.formId
						if (!currentFormId.isNullOrBlank()) {
							scope.launch {
								try {
									posLoading.value = true
									val fresh = app.docsService.fetchPos(currentFormId, logRequest = true)
									app.docsService.currentPos = fresh
									posState.value = fresh
								} catch (_: Exception) {
								} finally {
									posLoading.value = false
								}
							}
						}
					}
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    // Unify error presentation
    LaunchedEffect(scanError.value) {
        scanError.value?.let { ErrorBus.emit(it) }
    }

	// Auto-refresh every 5 seconds while on this screen (без логов)
	LaunchedEffect(Unit) {
		while (true) {
			kotlinx.coroutines.delay(5000)
			if (!posLoading.value && !isRequesting.value) {
				val currentFormId = posState.value?.formId ?: app.docsService.currentPos?.formId
				if (!currentFormId.isNullOrBlank()) {
					try {
						val fresh = app.docsService.fetchPos(currentFormId, logRequest = false)
						app.docsService.currentPos = fresh
						posState.value = fresh
					} catch (_: Exception) {
					}
				}
			}
		}
	}

	// If server specified SelectedId, auto-scroll to it
	LaunchedEffect(pos?.selectedId) {
		val selected = pos?.selectedId
		if (!selected.isNullOrBlank()) {
			val idx = pos?.items?.indexOfFirst { it.id == selected } ?: -1
			if (idx >= 0) {
				// +1 for the header item at the top
				val targetIndex = idx + 1
				val total = listState.layoutInfo.totalItemsCount
				if (targetIndex in 0 until total) {
					listState.animateScrollToItem(targetIndex)
				}
			}
		}
	}

    // Sync scan overlay with HW trigger via Zebra DataWedge (if available)
    androidx.compose.runtime.DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val status = intent?.let { DataWedge.parseScannerStatus(it) } ?: return
                when (status.uppercase()) {
                    "SCANNING" -> isScanning.value = true
                    "IDLE", "WAITING" -> if (isScanning.value) isScanning.value = false
                }
            }
        }
		val filter = IntentFilter(DataWedge.NOTIFICATION_ACTION)
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
			ctx.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
		} else {
			ctx.registerReceiver(receiver, filter)
		}
        DataWedge.registerScannerStatus(ctx)
        onDispose {
            runCatching { ctx.unregisterReceiver(receiver) }
            DataWedge.unregisterScannerStatus(ctx)
        }
    }

    // React to physical trigger press/release (vendor-agnostic via Activity)
    androidx.compose.runtime.LaunchedEffect(Unit) {
        ScanTriggerBus.events.collectLatest { pressed ->
            isScanning.value = pressed
        }
    }

    Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        LazyColumn(modifier = Modifier.fillMaxSize(), state = listState) {
            item {
                Column(modifier = Modifier.padding(12.dp)) {
                    val header = pos?.headerText ?: ""
                    val statusText = pos?.statusText ?: ""
                    Text(text = header, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    if (statusText.isNotBlank()) {
                        Text(text = statusText, color = colors.textSecondary, modifier = Modifier.padding(top = 4.dp))
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 8.dp),
                        horizontalArrangement = Arrangement.End,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                            val currentFormId = posState.value?.formId ?: app.docsService.currentPos?.formId
                            if (!currentFormId.isNullOrBlank()) {
                                scope.launch {
                                    try {
                                        posLoading.value = true
                                        val fresh = app.docsService.fetchPos(currentFormId, logRequest = true)
                                        app.docsService.currentPos = fresh
                                        posState.value = fresh
                                    } catch (e: Exception) {
										if (e !is ServerDialogShownException) {
											ErrorBus.emit(e.message ?: "Ошибка запроса")
										}
                                    } finally {
                                        posLoading.value = false
                                    }
                                }
                            }
                        },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.secondary,
                                contentColor = colors.textPrimary
                            )
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Outlined.Refresh,
                                contentDescription = "Обновить"
                            )
                            Text(text = "Обновить", modifier = Modifier.padding(start = 8.dp))
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Button(
                            onClick = { showDeleteAll.value = true },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = colors.secondary,
                                contentColor = colors.textPrimary
                            )
                        ) {
                            androidx.compose.material3.Icon(
                                imageVector = Icons.Outlined.Delete,
                                contentDescription = "Удалить все"
                            )
                            Text(text = "Удалить", modifier = Modifier.padding(start = 8.dp))
                        }
                    }
                }
            }

			val itemsList = pos?.items.orEmpty()
			val stDefault = (pos?.status ?: "").lowercase()
			val defaultBg = when (stDefault) {
				"closed" -> colors.statusDoneBg
				"pending" -> colors.statusPendingBg
				"warning" -> colors.statusWarningBg
				"error" -> colors.statusErrorBg
				else -> colors.statusTodoBg
			}
            val textColor = colors.textPrimary
            val subColor = colors.textSecondary

            items(itemsList) { it ->
				val stItem = (it.status ?: pos?.status ?: "").lowercase()
				val itemBg = when (stItem) {
					"closed" -> colors.statusDoneBg
					"pending" -> colors.statusPendingBg
					"warning" -> colors.statusWarningBg
					"error" -> colors.statusErrorBg
					else -> defaultBg
				}
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .then(
                            if (!pos?.selectedId.isNullOrBlank() && pos?.selectedId == it.id) {
                                Modifier.border(width = 2.dp, color = Color.Black, shape = MaterialTheme.shapes.medium)
                            } else {
                                Modifier
                            }
                        )
                        .fillMaxWidth(),
					colors = CardDefaults.cardColors(containerColor = itemBg)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(text = it.name, color = textColor)
                                val txt = it.text
                                if (!txt.isNullOrBlank()) {
                                    Text(text = txt, color = subColor, modifier = Modifier.padding(top = 4.dp))
                                }
                            }
                            IconButton(onClick = { pendingDeleteItemId.value = it.id }) {
                                androidx.compose.material3.Icon(
                                    imageVector = Icons.Outlined.Delete,
                                    contentDescription = "Удалить код",
                                    tint = colors.textPrimary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Floating scan message at bottom; auto hides after 3s
        if (!lastScan.value.isNullOrBlank()) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(8.dp)
                    .background(Color(0xFFFFF59D))
                    .padding(10.dp)
            ) {
                val last = lastScan.value
                if (!last.isNullOrBlank()) {
                    Text(text = last, color = Color.Black)
                }
            }
        }

        // Always-on hidden input to catch wedge text even without overlay
        run {
            AndroidView(
                factory = { ctx2 ->
                    val editText = android.widget.EditText(ctx2).apply {
                        setShowSoftInputOnFocus(false)
                        isSingleLine = true
                        isFocusable = true
                        isFocusableInTouchMode = true
                        inputType = android.text.InputType.TYPE_CLASS_TEXT or
                            android.text.InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD or
                            android.text.InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
                        imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI or android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN
                    }
                    var debounceJob: kotlinx.coroutines.Job? = null
                    val watcher = object : android.text.TextWatcher {
                        override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
                        override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
                        override fun afterTextChanged(s: android.text.Editable?) {
                            val text = s?.toString() ?: return
                            val idx = text.indexOfFirst { ch ->
                                val code = ch.code
                                ((code in 0x00..0x1F) && code != 0x1D) || code == 0x7F
                            }
                            if (idx >= 0) {
                                val code = text.substring(0, idx).trim()
                                if (code.isNotEmpty()) handleScan(code)
                                editText.setText("")
                            } else {
                                if (!isScanning.value && text.length >= 1) isScanning.value = true
                                debounceJob?.cancel()
                                debounceJob = scope.launch {
                                    kotlinx.coroutines.delay(120)
                                    val code = editText.text.toString().trim()
                                    if (code.isNotEmpty()) {
                                        handleScan(code)
                                        editText.setText("")
                                    }
                                }
                            }
                        }
                    }
                    editText.addTextChangedListener(watcher)
                    editText.setOnKeyListener { _, keyCode, event ->
                        if (event.action == android.view.KeyEvent.ACTION_UP &&
                            (keyCode == android.view.KeyEvent.KEYCODE_ENTER || keyCode == android.view.KeyEvent.KEYCODE_TAB)
                        ) {
                            val code = editText.text.toString().trim()
                            if (code.isNotEmpty()) handleScan(code)
                            editText.setText("")
                            true
                        } else false
                    }
                    editText
                },
                modifier = Modifier
                    .alpha(0f)
                    .fillMaxWidth()
                    .height(1.dp),
                update = { v -> v.post { v.requestFocus() } }
            )
        }
		// (floating actions removed; actions are under heading now)

        // Progress overlay during deletion or refresh
        if (isRequesting.value || posLoading.value) {
            Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFF30323D)
                )
            }
        }

        if (DebugFlags.CAMERA_SCAN_ENABLED && DebugSession.debugModeEnabled) {
            com.tsd.ascanner.ui.components.CameraScannerOverlay(
                visible = showCamera.value,
                onResult = { code -> handleScan(code) },
                onClose = { showCamera.value = false }
            )
        }

        if (DebugFlags.CAMERA_SCAN_ENABLED && DebugSession.debugModeEnabled) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(16.dp),
                horizontalAlignment = Alignment.End
            ) {
                FloatingActionButton(
                    onClick = { showCamera.value = true },
                    containerColor = colors.secondary,
                    contentColor = colors.textPrimary
                ) {
                    Icon(imageVector = Icons.Outlined.PhotoCamera, contentDescription = "Сканировать камерой")
                }
            }
        }

        // Confirm: delete all
        if (showDeleteAll.value) {
            AlertDialog(
                onDismissRequest = { if (!isRequesting.value) showDeleteAll.value = false },
                title = { Text("Удалить все коды?") },
                text = { Text("Вы уверены, что хотите удалить все отсканированные коды?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val currentFormId = posState.value?.formId ?: app.docsService.currentPos?.formId
                            if (currentFormId.isNullOrBlank()) {
                                showDeleteAll.value = false
                                return@TextButton
                            }
                            scope.launch {
                                try {
                                    isRequesting.value = true
                                    when (val res = app.docsService.deletePosAll(currentFormId)) {
                                        is com.tsd.ascanner.data.docs.ScanPosResult.Success -> {
                                            posState.value = app.docsService.currentPos
                                        }
                                        is com.tsd.ascanner.data.docs.ScanPosResult.Error -> {
                                            ErrorBus.emit(res.message)
                                        }
										is com.tsd.ascanner.data.docs.ScanPosResult.DialogShown -> {
											// Dialog will be shown via global DialogBus
										}
                                    }
                                } catch (e: Exception) {
                                    ErrorBus.emit(e.message ?: "Ошибка запроса")
                                } finally {
                                    isRequesting.value = false
                                    showDeleteAll.value = false
                                }
                            }
                        },
                        enabled = !isRequesting.value
                    ) { Text("Удалить") }
                },
                dismissButton = {
                    TextButton(onClick = { if (!isRequesting.value) showDeleteAll.value = false }) { Text("Отмена") }
                }
            )
        }

        // Confirm: delete single item
        pendingDeleteItemId.value?.let { toDeleteId ->
            AlertDialog(
                onDismissRequest = { if (!isRequesting.value) pendingDeleteItemId.value = null },
                title = { Text("Удалить код?") },
                text = { Text("Вы уверены, что хотите удалить выбранный код?") },
                confirmButton = {
                    TextButton(
                        onClick = {
                            val currentFormId = posState.value?.formId ?: app.docsService.currentPos?.formId
                            if (currentFormId.isNullOrBlank()) {
                                pendingDeleteItemId.value = null
                                return@TextButton
                            }
                            scope.launch {
                                try {
                                    isRequesting.value = true
                                    when (val res = app.docsService.deletePosItem(currentFormId, toDeleteId)) {
                                        is com.tsd.ascanner.data.docs.ScanPosResult.Success -> {
                                            posState.value = app.docsService.currentPos
                                        }
                                        is com.tsd.ascanner.data.docs.ScanPosResult.Error -> {
                                            ErrorBus.emit(res.message)
                                        }
										is com.tsd.ascanner.data.docs.ScanPosResult.DialogShown -> {
											// Dialog will be shown via global DialogBus
										}
                                    }
                                } catch (e: Exception) {
                                    ErrorBus.emit(e.message ?: "Ошибка запроса")
                                } finally {
                                    isRequesting.value = false
                                    pendingDeleteItemId.value = null
                                }
                            }
                        },
                        enabled = !isRequesting.value
                    ) { Text("Удалить") }
                },
                dismissButton = {
                    TextButton(onClick = { if (!isRequesting.value) pendingDeleteItemId.value = null }) { Text("Отмена") }
                }
            )
        }
    }

    BackHandler { onClose() }
}


