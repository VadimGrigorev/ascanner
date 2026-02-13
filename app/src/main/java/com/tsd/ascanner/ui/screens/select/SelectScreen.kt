package com.tsd.ascanner.ui.screens.select

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.snapshotFlow
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.unit.dp
import com.tsd.ascanner.AScannerApp
import com.tsd.ascanner.ui.components.ServerActionButtons
import com.tsd.ascanner.ui.components.parseServerIconOrFallback
import com.tsd.ascanner.ui.components.SearchScanMode
import com.tsd.ascanner.ui.components.ServerSearchField
import com.tsd.ascanner.ui.theme.AppTheme
import com.tsd.ascanner.ui.theme.statusCardColor
import com.tsd.ascanner.ui.theme.parseHexColorOrNull
import com.tsd.ascanner.utils.ErrorBus
import com.tsd.ascanner.utils.ServerSelect
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import androidx.compose.foundation.layout.height

@Composable
fun SelectScreen(
	paddingValues: PaddingValues,
	select: ServerSelect?,
	onClose: () -> Unit
) {
	val ctx = androidx.compose.ui.platform.LocalContext.current
	val app = ctx.applicationContext as AScannerApp
	val docsService = app.docsService
	val colors = AppTheme.colors
	val screenBg = parseHexColorOrNull(select?.backgroundColor) ?: colors.background
	val scope = rememberCoroutineScope()
	var sending by remember { mutableStateOf(false) }
	val listState = rememberLazyListState()
	var searchQuery by remember { mutableStateOf("") }
	val focusManager = LocalFocusManager.current
	var rootCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
	var searchBoundsInRoot by remember { mutableStateOf<Rect?>(null) }
	var searchFocused by remember { mutableStateOf(false) }

	fun handleScan(raw: String) {
		val code = raw.trim()
		if (code.isBlank()) return
		if (sending) return
		scope.launch {
			try {
				sending = true
				when (val res = docsService.scanSelect(code)) {
					is com.tsd.ascanner.data.docs.ButtonResult.Success -> {
						// Next screen/state will be driven by server response.
					}
					is com.tsd.ascanner.data.docs.ButtonResult.DialogShown -> {
						// Dialog/select will be shown globally (DialogBus/SelectBus).
					}
					is com.tsd.ascanner.data.docs.ButtonResult.Error -> {
						ErrorBus.emit(res.message)
					}
				}
			} finally {
				sending = false
			}
		}
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
		val payload = select
		if (payload == null) {
			CircularProgressIndicator(
				modifier = Modifier.align(Alignment.Center),
				color = Color(0xFF30323D)
			)
		} else {
			// Always-on hidden input to catch wedge/scanner text even without focusing search field
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
						imeOptions = android.view.inputmethod.EditorInfo.IME_FLAG_NO_EXTRACT_UI or
							android.view.inputmethod.EditorInfo.IME_FLAG_NO_FULLSCREEN
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

			val isSearchAvailable = payload.searchAvailable?.equals("true", ignoreCase = true) == true
			LaunchedEffect(isSearchAvailable) {
				if (!isSearchAvailable && searchQuery.isNotBlank()) {
					searchQuery = ""
				}
				if (!isSearchAvailable) searchBoundsInRoot = null
			}
			val q = if (isSearchAvailable) searchQuery.trim() else ""
			val displayedItems = if (q.isBlank()) {
				payload.items
			} else {
				payload.items.filter { it2 ->
					it2.name.contains(q, ignoreCase = true) ||
						it2.id.contains(q, ignoreCase = true) ||
						(it2.comment?.contains(q, ignoreCase = true) == true) ||
						((it2.status ?: "").contains(q, ignoreCase = true))
				}
			}

			LaunchedEffect(payload.selectedId, displayedItems, q) {
				val selected = payload.selectedId
				if (selected.isNullOrBlank()) return@LaunchedEffect

				val idx = displayedItems.indexOfFirst { it.id == selected }
				if (idx < 0) return@LaunchedEffect

				// +1 for the header item at the top
				val targetIndex = idx + 1

				// Wait until LazyColumn is measured and contains enough items.
				snapshotFlow { listState.layoutInfo.totalItemsCount }
					.filter { total -> targetIndex in 0 until total }
					.first()

				listState.animateScrollToItem(targetIndex)
			}

			LazyColumn(
				modifier = Modifier.fillMaxSize(),
				state = listState
			) {
				item {
					Column(modifier = Modifier.padding(12.dp)) {
						val header = payload.headerText ?: ""
						val statusText = payload.statusText ?: ""
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
									.onGloballyPositioned { searchBoundsInRoot = it.boundsInRoot() }
								,
								onFocusChanged = { focused -> searchFocused = focused }
							)
						}
					}
				}

				items(displayedItems) { it ->
					val bg = statusCardColor(
						colors = colors,
						status = it.status,
						statusColor = it.statusColor,
						fallbackStatus = payload.status,
						fallbackStatusColor = payload.statusColor
					)
					val iconSpec = it.icon
					val icon = if (!iconSpec.isNullOrBlank()) parseServerIconOrFallback(iconSpec) else null
					Card(
						modifier = Modifier
							.padding(horizontal = 12.dp, vertical = 6.dp)
							.then(
								if (!payload.selectedId.isNullOrBlank() && payload.selectedId == it.id) {
									Modifier.border(width = 2.dp, color = Color.Black, shape = MaterialTheme.shapes.medium)
								} else {
									Modifier
								}
							)
							.fillMaxWidth()
							.clickable(enabled = !sending) {
								scope.launch {
									try {
										sending = true
										when (val res = docsService.sendSelect(
											form = payload.form,
											formId = payload.formId,
											selectedId = it.id
										)) {
											is com.tsd.ascanner.data.docs.ButtonResult.Success -> {
												// Next screen/state will be driven by server response.
											}
											is com.tsd.ascanner.data.docs.ButtonResult.DialogShown -> {
												// Dialog/select will be shown globally (DialogBus/SelectBus).
											}
											is com.tsd.ascanner.data.docs.ButtonResult.Error -> {
												ErrorBus.emit(res.message)
											}
										}
									} finally {
										sending = false
									}
								}
							},
						colors = CardDefaults.cardColors(containerColor = bg)
					) {
						Row(
							modifier = Modifier
								.fillMaxWidth()
								.padding(12.dp),
							verticalAlignment = Alignment.CenterVertically,
							horizontalArrangement = Arrangement.spacedBy(12.dp)
						) {
							if (icon != null) {
								Icon(
									imageVector = icon,
									contentDescription = null,
									tint = colors.textPrimary
								)
							}
							Column(modifier = Modifier.weight(1f)) {
								Text(text = it.name, color = colors.textPrimary)
								val comment = it.comment
								if (!comment.isNullOrBlank()) {
									Text(
										text = comment,
										color = colors.textSecondary,
										modifier = Modifier.padding(top = 4.dp)
									)
								}
							}
						}
					}
				}
			}

			// Optional server buttons (same behavior as on other screens)
			val serverButtons = payload.buttons
			if (serverButtons.isNotEmpty()) {
				Column(
					modifier = Modifier
						.align(Alignment.BottomEnd)
						.padding(16.dp),
					horizontalAlignment = Alignment.End,
					verticalArrangement = Arrangement.spacedBy(12.dp)
				) {
					ServerActionButtons(
						buttons = serverButtons,
						enabled = !sending,
						onClick = { b ->
							scope.launch {
								try {
									sending = true
									when (val res = docsService.sendButton(
										form = payload.form,
										formId = payload.formId,
										buttonId = b.id,
										requestType = "button"
									)) {
										is com.tsd.ascanner.data.docs.ButtonResult.Success -> {
										}
										is com.tsd.ascanner.data.docs.ButtonResult.DialogShown -> {
										}
										is com.tsd.ascanner.data.docs.ButtonResult.Error -> {
											ErrorBus.emit(res.message)
										}
									}
								} finally {
									sending = false
								}
							}
						}
					)
				}
			}
		}

		if (sending) {
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
