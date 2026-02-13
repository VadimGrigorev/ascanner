package com.tsd.ascanner.ui.screens.order

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.border
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.PhotoCamera
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.tsd.ascanner.AScannerApp
import com.tsd.ascanner.data.docs.DocsService
import com.tsd.ascanner.ui.theme.AppTheme
import androidx.compose.ui.Alignment
import kotlinx.coroutines.launch
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.height
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.tsd.ascanner.utils.DebugFlags
import com.tsd.ascanner.utils.DebugSession
import com.tsd.ascanner.ui.components.ServerActionButtons
import com.tsd.ascanner.ui.components.SearchScanMode
import com.tsd.ascanner.ui.components.ServerSearchField
import com.tsd.ascanner.ui.theme.statusCardColor
import com.tsd.ascanner.ui.theme.parseHexColorOrNull
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalFocusManager

@Composable
fun DocScreen(
    paddingValues: PaddingValues,
    formId: String,
    onClose: () -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val app = ctx.applicationContext as AScannerApp
    val docsService = app.docsService
	val doc by docsService.currentDocFlow.collectAsState()
    val colors = AppTheme.colors
    val scope = rememberCoroutineScope()
    val errorMessage = remember { mutableStateOf<String?>(null) }
    val isScanning = remember { mutableStateOf(false) }
    val scanError = remember { mutableStateOf<String?>(null) }
    val lastScan = remember { mutableStateOf<String?>(null) }
    val globalLoading = remember { mutableStateOf(false) }
    val isRequesting = remember { mutableStateOf(false) }
    var loadingPosId by remember { mutableStateOf<String?>(null) }
    var showCamera by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()
	var bottomActionsHeightPx by remember { mutableStateOf(0) }
	val density = LocalDensity.current
	var searchQuery by remember { mutableStateOf("") }
	var searchFocused by remember { mutableStateOf(false) }

    fun handleScan(code: String) {
        val currentFormId = doc?.formId ?: docsService.currentDoc?.formId
        if (code.length < 4 || currentFormId.isNullOrBlank()) return
        lastScan.value = code
        scanError.value = null
        scope.launch {
            try {
                isRequesting.value = true
                when (val res = docsService.scanMark(currentFormId, code)) {
                    is com.tsd.ascanner.data.docs.ScanDocResult.Success -> {
                        isScanning.value = false
                    }
                    is com.tsd.ascanner.data.docs.ScanDocResult.Error -> {
                        scanError.value = res.message
                        isScanning.value = true
                    }
					is com.tsd.ascanner.data.docs.ScanDocResult.DialogShown -> {
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

    // Auto-hide scanned text after 15s
    LaunchedEffect(lastScan.value) {
        val hasText = !lastScan.value.isNullOrBlank()
        if (hasText) {
            kotlinx.coroutines.delay(15000)
            lastScan.value = null
        }
    }

    // Unify error presentation: route local errors to global banner
    LaunchedEffect(errorMessage.value) {
        errorMessage.value?.let { ErrorBus.emit(it) }
    }
    LaunchedEffect(scanError.value) {
        scanError.value?.let { ErrorBus.emit(it) }
    }

	val isSearchAvailable = doc?.searchAvailable?.equals("true", ignoreCase = true) == true
	LaunchedEffect(isSearchAvailable) {
		if (!isSearchAvailable && searchQuery.isNotBlank()) {
			searchQuery = ""
		}
	}

    // Initial load: fetch doc for provided formId (логируем как ручной запрос)
    androidx.compose.runtime.LaunchedEffect(formId) {
        globalLoading.value = true
        try {
            docsService.fetchDoc(formId, logRequest = true)
        } catch (_: Exception) {
            // error will be surfaced if user tries to refresh or through separate UI
        } finally {
            globalLoading.value = false
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

    // Refresh document whenever screen is resumed (avoid stale cache)
    run {
        val lifecycleOwner = LocalLifecycleOwner.current
        // Skip the very first ON_RESUME after initial load
        val skipNextOnResume = remember { mutableStateOf(true) }
        androidx.compose.runtime.DisposableEffect(lifecycleOwner, formId) {
            val observer = LifecycleEventObserver { _, event ->
                if (event == Lifecycle.Event.ON_RESUME) {
                    if (skipNextOnResume.value) {
                        skipNextOnResume.value = false
                    } else {
                        scope.launch {
                            try {
                                globalLoading.value = true
                                // Background refresh on resume must not change screen
                                docsService.fetchDoc(formId, logRequest = true, emitNav = false)
                            } catch (_: Exception) {
                            } finally {
                                globalLoading.value = false
                            }
                        }
                    }
                }
            }
            lifecycleOwner.lifecycle.addObserver(observer)
            onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
        }
    }

	// Auto-refresh every 5 seconds while on this screen (без логов)
	LaunchedEffect(formId) {
		while (true) {
			kotlinx.coroutines.delay(5000)
			if (!globalLoading.value && !isRequesting.value) {
				try {
					// Background refresh must not change screen
					docsService.fetchDoc(formId, logRequest = false, emitNav = false)
				} catch (_: Exception) {
				}
			}
		}
	}

    // If server specified SelectedId, auto-scroll to it
    LaunchedEffect(doc?.selectedId) {
        val selected = doc?.selectedId
        if (!selected.isNullOrBlank()) {
            val idx = doc?.items?.indexOfFirst { it.id == selected } ?: -1
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

	val screenBg = parseHexColorOrNull(doc?.backgroundColor) ?: colors.background
	val focusManager = LocalFocusManager.current
	var rootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
	var searchBoundsInRoot by remember { mutableStateOf<Rect?>(null) }
	LaunchedEffect(isSearchAvailable) {
		if (!isSearchAvailable) searchBoundsInRoot = null
	}

    Box(
		modifier = Modifier
			.fillMaxSize()
			.background(screenBg)
			.padding(paddingValues)
			.onGloballyPositioned { rootCoords = it }
			.pointerInput(rootCoords, searchBoundsInRoot) {
				awaitEachGesture {
					val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
					val root = rootCoords
					val bounds = searchBoundsInRoot
					if (root != null && bounds != null) {
						val downInRoot = root.localToRoot(down.position)
						if (!bounds.contains(downInRoot)) {
							focusManager.clearFocus()
						}
					}
					waitForUpOrCancellation()
				}
			}
	) {
		val serverButtons = doc?.buttons.orEmpty()
		val showRefreshFab = DebugFlags.REFRESH_BUTTONS_ENABLED
		val showCameraFab = DebugFlags.CAMERA_SCAN_ENABLED && DebugSession.debugModeEnabled
		val hasBottomActions = serverButtons.isNotEmpty() || showRefreshFab || showCameraFab
		LaunchedEffect(hasBottomActions) {
			if (!hasBottomActions) bottomActionsHeightPx = 0
		}
		val bottomPaddingDp = with(density) { bottomActionsHeightPx.toDp() } + 8.dp

        LazyColumn(
			modifier = Modifier.fillMaxSize(),
			state = listState,
			contentPadding = PaddingValues(bottom = bottomPaddingDp)
		) {
            item {
                Column(modifier = Modifier.padding(12.dp)) {
                    val header = doc?.headerText ?: ""
                    val statusText = doc?.statusText ?: ""
                    Text(text = header, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    if (statusText.isNotBlank()) {
                        Text(text = statusText, color = colors.textSecondary, modifier = Modifier.padding(top = 4.dp))
                    }
					if (isSearchAvailable) {
						ServerSearchField(
							visible = true,
							value = searchQuery,
							onValueChange = { searchQuery = it },
							label = "Поиск",
							scanMode = SearchScanMode.ControlChars,
							onScan = { code -> handleScan(code) },
							modifier = Modifier
								.fillMaxWidth()
								.padding(top = 8.dp)
								.onGloballyPositioned { searchBoundsInRoot = it.boundsInRoot() },
							onFocusChanged = { focused -> searchFocused = focused }
						)
					}
                    val total = doc?.items?.size ?: 0
                    val done = doc?.items?.count { (it.status ?: "").lowercase() == "closed" } ?: 0
                    if (total > 0) {
                        Text(text = "$done/$total выполнено", color = colors.textPrimary, modifier = Modifier.padding(top = 12.dp))
                        val progress = (done.toFloat() / total.toFloat()).coerceIn(0f, 1f)
						androidx.compose.foundation.layout.Box(
                            modifier = Modifier
                                .padding(top = 8.dp)
                                .fillMaxWidth()
                                .height(6.dp)
								.background(colors.progressTrack, shape = androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
                        ) {
                            val isFull = progress >= 0.999f
                            val barShape = if (isFull) {
                                androidx.compose.foundation.shape.RoundedCornerShape(3.dp)
                            } else {
                                androidx.compose.foundation.shape.RoundedCornerShape(
                                    topStart = 3.dp, bottomStart = 3.dp, topEnd = 0.dp, bottomEnd = 0.dp
                                )
                            }
                            androidx.compose.foundation.layout.Box(
                                modifier = Modifier
                                    .fillMaxWidth(progress)
                                    .fillMaxHeight()
                                    .background(colors.progress, shape = barShape)
                            )
                        }
                    }
                }
            }

            val itemsList = doc?.items.orEmpty()
			val q = if (isSearchAvailable) searchQuery.trim() else ""
			val filteredItems = if (q.isBlank()) {
				itemsList
			} else {
				itemsList.filter { it2 ->
					it2.name.contains(q, ignoreCase = true) ||
						it2.id.contains(q, ignoreCase = true) ||
						(it2.statusText?.contains(q, ignoreCase = true) == true) ||
						((it2.status ?: "").contains(q, ignoreCase = true))
				}
			}
			items(filteredItems) { it ->
				val bg = statusCardColor(
					colors = colors,
					status = it.status,
					statusColor = it.statusColor,
					fallbackStatus = doc?.status,
					fallbackStatusColor = doc?.statusColor
				)
				val textColor = colors.textPrimary
				val subColor = colors.textSecondary
                val isLoadingThis = loadingPosId == it.id
                val containerColor = if (isLoadingThis) Color(0xFFFFF44F) else bg
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .then(
                            if (!doc?.selectedId.isNullOrBlank() && doc?.selectedId == it.id) {
                                Modifier.border(width = 2.dp, color = Color.Black, shape = MaterialTheme.shapes.medium)
                            } else {
                                Modifier
                            }
                        )
                        .fillMaxWidth()
                        .clickable {
                            val formId = it.id
                            scope.launch {
                                try {
                                    loadingPosId = formId
                                    errorMessage.value = null
                                    docsService.fetchPos(formId, logRequest = true)
                                } catch (e: Exception) {
									if (e !is ServerDialogShownException) {
										errorMessage.value = e.message ?: "Ошибка загрузки позиции"
									}
                                } finally {
                                    loadingPosId = null
                                }
                            }
                        },
                    colors = CardDefaults.cardColors(containerColor = containerColor)
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(text = it.name, color = textColor)
                        val st = it.statusText
                        if (!st.isNullOrBlank()) {
                            Text(text = st, color = subColor, modifier = Modifier.padding(top = 4.dp))
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
                update = { v -> v.post { if (!searchFocused) v.requestFocus() } }
            )
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
				horizontalAlignment = Alignment.End,
				verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(12.dp)
			) {
				var serverSending by remember { mutableStateOf(false) }
				if (serverButtons.isNotEmpty()) {
					ServerActionButtons(
						buttons = serverButtons,
						enabled = !serverSending,
						onClick = { b ->
							val fid = doc?.formId ?: formId
							if (!fid.isNullOrBlank()) {
								scope.launch {
									try {
										serverSending = true
										when (val res = docsService.sendButton(
											form = "doc",
											formId = fid,
											buttonId = b.id,
											requestType = "button"
										)) {
											is com.tsd.ascanner.data.docs.ButtonResult.Success -> {
												// state updated via docsService
											}
											is com.tsd.ascanner.data.docs.ButtonResult.DialogShown -> {
												// Dialog shown via DialogBus
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
						}
					)
				}
				if (showRefreshFab) {
					FloatingActionButton(
						onClick = {
							val formId = doc?.formId
							if (!formId.isNullOrBlank()) {
								scope.launch {
									try {
										globalLoading.value = true
										docsService.fetchDoc(formId, logRequest = true)
									} catch (_: Exception) {
									} finally {
										globalLoading.value = false
									}
								}
							}
						},
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

        if (DebugFlags.CAMERA_SCAN_ENABLED && DebugSession.debugModeEnabled) {
            com.tsd.ascanner.ui.components.CameraScannerOverlay(
                visible = showCamera,
                onResult = { code -> handleScan(code) },
                onClose = { showCamera = false }
            )
        }

        if (globalLoading.value || loadingPosId != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = Color(0xFF30323D)
                )
            }
        }
    }

    BackHandler { onClose() }
}


