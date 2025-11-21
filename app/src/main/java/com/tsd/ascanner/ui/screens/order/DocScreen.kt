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
import androidx.compose.runtime.Composable
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
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.height
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

@Composable
fun DocScreen(
    paddingValues: PaddingValues,
    formId: String,
    onClose: () -> Unit,
    onOpenPosition: () -> Unit
) {
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val app = ctx.applicationContext as AScannerApp
    val docsService = app.docsService
    val docState = remember { mutableStateOf(docsService.currentDoc) }
    val doc = docState.value
    val colors = AppTheme.colors
    val scope = rememberCoroutineScope()
    val errorMessage = remember { mutableStateOf<String?>(null) }
    val isScanning = remember { mutableStateOf(false) }
    val scanError = remember { mutableStateOf<String?>(null) }
    val lastScan = remember { mutableStateOf<String?>(null) }
    val globalLoading = remember { mutableStateOf(false) }
    val isRequesting = remember { mutableStateOf(false) }
    var loadingPosId by remember { mutableStateOf<String?>(null) }

    // Auto-hide scanned text after 3s
    LaunchedEffect(lastScan.value) {
        val hasText = !lastScan.value.isNullOrBlank()
        if (hasText) {
            kotlinx.coroutines.delay(3000)
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

    // Initial load: fetch doc for provided formId
    androidx.compose.runtime.LaunchedEffect(formId) {
        globalLoading.value = true
        try {
            val fresh = docsService.fetchDoc(formId)
            docsService.currentDoc = fresh
            docState.value = fresh
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
                                val fresh = docsService.fetchDoc(formId)
                                docsService.currentDoc = fresh
                                docState.value = fresh
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

    Box(modifier = Modifier.fillMaxSize().padding(paddingValues)) {
        LazyColumn(modifier = Modifier.fillMaxSize()) {
            item {
                Column(modifier = Modifier.padding(12.dp)) {
                    val header = doc?.headerText ?: ""
                    val statusText = doc?.statusText ?: ""
                    Text(text = header, style = MaterialTheme.typography.titleMedium, color = colors.textPrimary)
                    if (statusText.isNotBlank()) {
                        Text(text = statusText, color = colors.textSecondary, modifier = Modifier.padding(top = 4.dp))
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
                                .background(colors.statusTodoBg, shape = androidx.compose.foundation.shape.RoundedCornerShape(3.dp))
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
            items(itemsList) { it ->
                val isClosed = (it.status ?: "").lowercase() == "closed"
                val bg = if (isClosed) colors.statusDoneBg else colors.statusTodoBg
                val textColor = if (isClosed) colors.textPrimary else colors.textPrimary
                val subColor = if (isClosed) colors.textSecondary else colors.textSecondary
                val isLoadingThis = loadingPosId == it.id
                val containerColor = if (isLoadingThis) Color(0xFFFFF59D) else bg
                Card(
                    modifier = Modifier
                        .padding(horizontal = 12.dp, vertical = 6.dp)
                        .fillMaxWidth()
                        .clickable {
                            val formId = it.id
                            scope.launch {
                                try {
                                    loadingPosId = formId
                                    errorMessage.value = null
                                    val pos = docsService.fetchPos(formId)
                                    docsService.currentPos = pos
                                    onOpenPosition()
                                } catch (e: Exception) {
                                    errorMessage.value = e.message ?: "Ошибка загрузки позиции"
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
                    fun handle(code: String) {
                        val currentFormId = docState.value?.formId ?: docsService.currentDoc?.formId
                        if (code.length < 4 || currentFormId.isNullOrBlank()) return
                        lastScan.value = code
                        scanError.value = null
                        scope.launch {
                            try {
                                isRequesting.value = true
                                when (val res = docsService.scanMark(currentFormId, code)) {
                                    is com.tsd.ascanner.data.docs.ScanDocResult.Success -> {
                                        docState.value = docsService.currentDoc
                                        isScanning.value = false
                                    }
                                    is com.tsd.ascanner.data.docs.ScanDocResult.Error -> {
                                        scanError.value = res.message
                                        isScanning.value = true
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
                                if (code.isNotEmpty()) handle(code)
                                editText.setText("")
                            } else {
                                if (!isScanning.value && text.length >= 1) isScanning.value = true
                                debounceJob?.cancel()
                                debounceJob = scope.launch {
                                    kotlinx.coroutines.delay(120)
                                    val code = editText.text.toString().trim()
                                    if (code.isNotEmpty()) {
                                        handle(code)
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
                            if (code.isNotEmpty()) handle(code)
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

        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp),
            horizontalAlignment = Alignment.End
        ) {
            FloatingActionButton(
                onClick = {
                    val formId = doc?.formId
                    if (!formId.isNullOrBlank()) {
                        // Refresh current doc
                        scope.launch {
                            try {
                                globalLoading.value = true
                                val fresh = docsService.fetchDoc(formId)
                                docsService.currentDoc = fresh
                                docState.value = fresh
                            } catch (_: Exception) {
                            } finally { globalLoading.value = false }
                        }
                    }
                },
                containerColor = colors.secondary,
                contentColor = colors.textPrimary
            ) {
                Icon(imageVector = Icons.Outlined.Refresh, contentDescription = "Обновить")
            }
        }

        if (globalLoading.value || loadingPosId != null) {
            Box(modifier = Modifier.fillMaxSize()) {
                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
            }
        }
    }

    BackHandler { onClose() }
}


