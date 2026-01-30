package com.tsd.ascanner.ui.screens.tasks

import androidx.activity.ComponentActivity
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tsd.ascanner.AScannerApp
import com.tsd.ascanner.data.docs.DocsService
import com.tsd.ascanner.ui.theme.AppTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import kotlinx.coroutines.launch
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import com.tsd.ascanner.utils.DataWedge
import com.tsd.ascanner.utils.ScanTriggerBus
import kotlinx.coroutines.flow.collectLatest
import com.tsd.ascanner.utils.ErrorBus
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tsd.ascanner.utils.DebugFlags
import com.tsd.ascanner.utils.DebugSession
import com.tsd.ascanner.ui.components.ServerActionButtons
import com.tsd.ascanner.ui.theme.statusCardColor

class TasksViewModel(private val service: DocsService) : ViewModel()

@Composable
fun TasksScreen(
    paddingValues: PaddingValues,
    onOpenDoc: (String) -> Unit
) {
    val context = androidx.compose.ui.platform.LocalContext.current
    val app = context.applicationContext as AScannerApp
    val vm = viewModel<RemoteTasksViewModel>(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            // IMPORTANT: use the shared app.docsService so that MainActivity (which listens to navEvents)
            // can react to Form-driven navigation triggered by fetchDoc/fetchPos.
            return RemoteTasksViewModel(app.docsService) as T
        }
    })

    val colors = AppTheme.colors
    var isScanning by remember { mutableStateOf(false) }
    var scanError by remember { mutableStateOf<String?>(null) }
    var lastScan by remember { mutableStateOf<String?>(null) }
    val scope = rememberCoroutineScope()
    var isRequesting by remember { mutableStateOf(false) }
    var loadingOrderId by remember { mutableStateOf<String?>(null) }
	var searchFocused by remember { mutableStateOf(false) }
    var showCamera by remember { mutableStateOf(false) }
	var bottomActionsHeightPx by remember { mutableStateOf(0) }
	val density = LocalDensity.current

    fun commitScan(code: String) {
        if (code.length < 4) return
        lastScan = code
        scanError = null
        scope.launch {
            try {
                isRequesting = true
                when (val res = app.docsService.scanDocList(code)) {
                    is com.tsd.ascanner.data.docs.ScanDocResult.Success -> {
                        isScanning = false
                        val id = res.doc.formId ?: app.docsService.currentDoc?.formId ?: code
                        onOpenDoc(id)
                    }
                    is com.tsd.ascanner.data.docs.ScanDocResult.Error -> {
                        scanError = res.message
                        isScanning = true
                    }
					is com.tsd.ascanner.data.docs.ScanDocResult.DialogShown -> {
						// Dialog will be shown via global DialogBus
						isScanning = false
					}
                }
            } catch (e: Exception) {
                scanError = e.message ?: "Ошибка запроса"
                isScanning = true
            } finally {
                isRequesting = false
            }
        }
    }

    // Auto-hide scanned text after 15s
    LaunchedEffect(lastScan) {
        val hasText = !lastScan.isNullOrBlank()
        if (hasText) {
            kotlinx.coroutines.delay(15000)
            lastScan = null
        }
    }

    // Unify error presentation: route VM and scan errors to global banner
    LaunchedEffect(vm.errorMessage) {
        vm.errorMessage?.let { ErrorBus.emit(it) }
    }
    LaunchedEffect(scanError) {
        scanError?.let { ErrorBus.emit(it) }
    }

    // Clear highlighted card when network loading for openOrder finishes
    androidx.compose.runtime.LaunchedEffect(vm.isLoading) {
        if (!vm.isLoading) loadingOrderId = null
    }

    // React to physical trigger press/release (vendor-agnostic via Activity)
    androidx.compose.runtime.LaunchedEffect(Unit) {
        ScanTriggerBus.events.collectLatest { pressed ->
            isScanning = pressed
        }
    }

    // Sync scan overlay with HW trigger via Zebra DataWedge (if available)
    androidx.compose.runtime.DisposableEffect(Unit) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(context: Context?, intent: Intent?) {
                val status = intent?.let { DataWedge.parseScannerStatus(it) } ?: return
                when (status.uppercase()) {
                    "SCANNING" -> isScanning = true
                    "IDLE", "WAITING" -> if (isScanning) isScanning = false
                }
            }
        }
		val filter = IntentFilter(DataWedge.NOTIFICATION_ACTION)
		if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
			context.registerReceiver(receiver, filter, Context.RECEIVER_EXPORTED)
		} else {
			context.registerReceiver(receiver, filter)
		}
        DataWedge.registerScannerStatus(context)
        onDispose {
            runCatching { context.unregisterReceiver(receiver) }
            DataWedge.unregisterScannerStatus(context)
        }
    }

	// Auto-refresh every 5 seconds while screen is visible
	LaunchedEffect(Unit) {
		while (true) {
			kotlinx.coroutines.delay(5000)
			if (!vm.isLoading) {
				vm.refresh(userInitiated = false)
			}
		}
	}

    // Always refresh tasks when screen becomes visible again
    run {
        val lifecycleOwner = LocalLifecycleOwner.current
        androidx.compose.runtime.DisposableEffect(lifecycleOwner) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    // При первом показе экрана и возврате на него делаем ручной refresh
                    // с индикатором загрузки, как и раньше.
                    vm.refresh(userInitiated = true)
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

    Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
		val serverButtons = vm.buttons
		val showRefreshFab = DebugFlags.REFRESH_BUTTONS_ENABLED
		val showCameraFab = DebugFlags.CAMERA_SCAN_ENABLED && DebugSession.debugModeEnabled
		val hasBottomActions = serverButtons.isNotEmpty() || showRefreshFab || showCameraFab
		LaunchedEffect(hasBottomActions) {
			if (!hasBottomActions) bottomActionsHeightPx = 0
		}
		val bottomPaddingDp = with(density) { bottomActionsHeightPx.toDp() } + 8.dp

        // Always-on hidden input to catch wedge text even without overlay
        AndroidView(
            factory = { ctx ->
                val editText = android.widget.EditText(ctx).apply {
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
                            if (code.isNotEmpty()) commitScan(code)
                            editText.setText("")
                        } else {
                            // On first chars, show overlay for UX
                            if (!isScanning && text.length >= 1) isScanning = true
                            debounceJob?.cancel()
                            debounceJob = scope.launch {
                                kotlinx.coroutines.delay(120)
                                val code = editText.text.toString().trim()
                                if (code.isNotEmpty()) {
                                    commitScan(code)
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
                        if (code.isNotEmpty()) commitScan(code)
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
			update = { v -> v.post { if (!searchFocused) v.requestFocus() } }
        )

        LazyColumn(
			modifier = Modifier.fillMaxSize(),
			contentPadding = PaddingValues(bottom = bottomPaddingDp)
		) {
            // Header: filter and errors
            item {
                Column(modifier = Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(checked = vm.showOnlyOpen, onCheckedChange = { vm.toggleShowOnlyOpen() })
                        Text(text = "скрыть завершенные", color = colors.textSecondary)
                    }
					Spacer(Modifier.height(8.dp))
					OutlinedTextField(
						value = vm.searchQuery,
						onValueChange = { value ->
							vm.updateSearchQuery(value)
							if (searchFocused) {
								val marker = "@!@!@NEWDOCUMENT!@!@!"
								val idx = value.indexOf(marker)
								if (idx >= 0) {
									val code = value.substring(idx).trim()
									// Очистим поиск, чтобы не триггерить повторно
									vm.updateSearchQuery("")
									lastScan = code
									scanError = null
									scope.launch {
										try {
											isRequesting = true
											when (val res = app.docsService.scanDocList(code)) {
												is com.tsd.ascanner.data.docs.ScanDocResult.Success -> {
													isScanning = false
													val id = res.doc.formId ?: app.docsService.currentDoc?.formId ?: code
													onOpenDoc(id)
												}
												is com.tsd.ascanner.data.docs.ScanDocResult.Error -> {
													scanError = res.message
													isScanning = true
												}
												is com.tsd.ascanner.data.docs.ScanDocResult.DialogShown -> {
													isScanning = false
												}
											}
										} catch (e: Exception) {
											scanError = e.message ?: "Ошибка запроса"
											isScanning = true
										} finally {
											isRequesting = false
										}
									}
								}
							}
						},
						modifier = Modifier
							.fillMaxWidth()
							.onFocusChanged { searchFocused = it.isFocused },
						label = { Text(text = "Поиск") },
						singleLine = true
					)
                }
            }

			// Filter tasks/orders by open/closed and search query
			val q = vm.searchQuery.trim().lowercase()
			val filteredTasks = vm.tasks.mapNotNull { t ->
				val baseOrders = if (!vm.showOnlyOpen) t.orders else t.orders.filter { (it.status ?: "").lowercase() != "closed" }
				if (q.isEmpty()) {
					if (baseOrders.isNotEmpty()) t.copy(orders = baseOrders) else null
				} else {
					val taskMatch = t.name.contains(q, ignoreCase = true) || t.id.contains(q, ignoreCase = true)
					val ordersMatched = baseOrders.filter { o ->
						o.name.contains(q, ignoreCase = true) ||
						(o.comment1?.contains(q, ignoreCase = true) == true) ||
						(o.comment2?.contains(q, ignoreCase = true) == true) ||
						((o.status ?: "").contains(q, ignoreCase = true)) ||
						o.id.contains(q, ignoreCase = true)
					}
					val finalOrders = if (taskMatch) baseOrders else ordersMatched
					if (taskMatch || finalOrders.isNotEmpty()) t.copy(orders = finalOrders) else null
				}
			}

            items(filteredTasks) { t ->
                val original = vm.tasks.firstOrNull { it.id == t.id }
                val totalOrders = original?.orders?.size ?: 0
                val statuses = original?.orders?.map { (it.status ?: "").lowercase() } ?: emptyList()
                val hasError = statuses.any { it == "error" }
                val hasWarning = statuses.any { it == "warning" }
                val allClosed = totalOrders > 0 && statuses.all { it == "closed" }
                val hasPending = statuses.any { it == "pending" }
				val hasNote = statuses.any { it == "note" }
                val closedCount = statuses.count { it == "closed" }
                val headerBg = when {
                    hasError -> colors.statusErrorBg
                    hasWarning -> colors.statusWarningBg
                    allClosed -> colors.statusDoneBg
                    hasPending -> colors.statusPendingBg
					hasNote -> colors.statusNoteBg
                    else -> colors.statusTodoBg
                }
                val headerTextColor = colors.textPrimary
                val headerIconTint = headerTextColor

                Card(
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .fillMaxWidth()
                        .clickable { vm.toggleTask(t.id) },
                    colors = CardDefaults.cardColors(containerColor = headerBg)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                modifier = Modifier
                                    .weight(1f)
                                    .padding(end = 8.dp),
                                text = t.name,
                                style = MaterialTheme.typography.titleMedium,
                                color = headerTextColor,
                                // allow wrapping to multiple lines to keep ratio+indicator visible
                            )
                            val expanded = vm.expandedTaskIds.contains(t.id)
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "$closedCount/$totalOrders",
                                    color = headerTextColor
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Icon(
                                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = if (expanded) "Свернуть" else "Развернуть",
                                    tint = headerIconTint
                                )
                            }
                        }
                    }
                }

                if (vm.expandedTaskIds.contains(t.id)) {
                    t.orders.forEach { o ->
                        val orderBg = statusCardColor(
							colors = colors,
							status = o.status,
							statusColor = o.statusColor
						)
                        val orderTextColor = colors.textPrimary
                        val orderSubTextColor = colors.textSecondary
                        val isLoadingThis = vm.isLoading && loadingOrderId == o.id
                        val orderContainer = if (isLoadingThis) Color(0xFFFFF44F) else orderBg
                    Card(
                            modifier = Modifier
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                            .fillMaxWidth()
                            .clickable {
                                loadingOrderId = o.id
                                // Navigation is handled globally via DocsService.navEvents based on server Form.
                                vm.openOrder(o.id) { /* no-op */ }
                            },
                            colors = CardDefaults.cardColors(containerColor = orderContainer)
                        ) {
                            Column(modifier = Modifier.padding(12.dp)) {
                                Text(text = o.name, color = orderTextColor)
                                val c1 = o.comment1
                                if (!c1.isNullOrBlank()) Text(text = c1, color = orderSubTextColor)
                                val c2 = o.comment2
                                if (!c2.isNullOrBlank()) Text(text = c2, color = orderSubTextColor)
                            }
                        }
                    }
                }
            }
        }

        // Floating scan message at bottom; auto hides after 3s
        if (!lastScan.isNullOrBlank()) {
            Column(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(8.dp)
                    .background(Color(0xFFFFF59D))
                    .padding(10.dp)
            ) {
                val last = lastScan
                if (!last.isNullOrBlank()) {
                    Text(text = last, color = Color.Black)
                }
            }
        }

        // Global loading overlay without dimming (only for user-initiated loads)
        if (vm.isLoading && vm.showLoadingIndicator) {
            Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFF30323D)
                )
            }
        }

		if (hasBottomActions) {
			Column(
				modifier = Modifier
					.align(Alignment.BottomEnd)
					.padding(16.dp)
					.onGloballyPositioned { coords ->
						val h = coords.size.height
						if (bottomActionsHeightPx != h) bottomActionsHeightPx = h
					},
				verticalArrangement = Arrangement.spacedBy(12.dp),
				horizontalAlignment = Alignment.End
			) {
				var serverSending by remember { mutableStateOf(false) }
				if (serverButtons.isNotEmpty()) {
					ServerActionButtons(
						buttons = serverButtons,
						enabled = !serverSending,
						onClick = { b ->
							val buttonId = b.id
							serverSending = true
							scope.launch {
								try {
									when (val res = app.docsService.sendButton(
										form = "doclist",
										formId = "",
										buttonId = buttonId,
										requestType = "button"
									)) {
										is com.tsd.ascanner.data.docs.ButtonResult.Success -> {
											vm.refresh(userInitiated = false)
										}
										is com.tsd.ascanner.data.docs.ButtonResult.DialogShown -> {
											// Dialog will be shown via DialogBus
										}
										is com.tsd.ascanner.data.docs.ButtonResult.Error -> {
											ErrorBus.emit(res.message)
										}
									}
								} finally {
									serverSending = false
								}
							}
						}
					)
				}
				if (showRefreshFab) {
					FloatingActionButton(
						onClick = { vm.refresh() },
						containerColor = colors.secondary,
						contentColor = colors.textPrimary
					) {
						Icon(imageVector = Icons.Outlined.Refresh, contentDescription = "Обновить")
					}
				}
				if (showCameraFab) {
					FloatingActionButton(
						onClick = { showCamera = true },
						containerColor = colors.secondary,
						contentColor = colors.textPrimary
					) {
						Icon(imageVector = Icons.Outlined.PhotoCamera, contentDescription = "Сканировать камерой")
					}
				}
			}
		}

        // Camera overlay
        if (DebugFlags.CAMERA_SCAN_ENABLED && DebugSession.debugModeEnabled) {
            com.tsd.ascanner.ui.components.CameraScannerOverlay(
                visible = showCamera,
                onResult = { code -> commitScan(code) },
                onClose = { showCamera = false }
            )
        }
    }

    val activity = context as? ComponentActivity
    BackHandler { activity?.finish() }
}



