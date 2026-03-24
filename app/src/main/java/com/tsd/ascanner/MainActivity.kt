package com.tsd.ascanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.core.view.WindowCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import android.net.Uri
import com.tsd.ascanner.ui.screens.auth.LoginScreen
import com.tsd.ascanner.ui.screens.order.DocScreen
import com.tsd.ascanner.ui.screens.order.PosScreen
import com.tsd.ascanner.ui.screens.tasks.TasksScreen
import com.tsd.ascanner.ui.theme.AScannerTheme
import android.content.BroadcastReceiver
import android.content.IntentFilter
import android.view.KeyEvent
import com.tsd.ascanner.utils.DataWedge
import com.tsd.ascanner.utils.Newland
import com.tsd.ascanner.utils.KeyboardWedgeInterceptor
import com.tsd.ascanner.utils.ScanDataBus
import com.tsd.ascanner.utils.ScannerSettings
import com.tsd.ascanner.utils.ScanTriggerBus
import com.tsd.ascanner.utils.ErrorBus
import com.tsd.ascanner.utils.DialogBus
import com.tsd.ascanner.utils.PrintBus
import com.tsd.ascanner.utils.SelectBus
import com.tsd.ascanner.utils.ServerDialog
import com.tsd.ascanner.utils.ServerPrintRequest
import com.tsd.ascanner.utils.ServerSelect
import com.tsd.ascanner.utils.AppEvent
import com.tsd.ascanner.utils.AppEventBus
import com.tsd.ascanner.ui.components.PrinterDialog
import com.tsd.ascanner.data.printer.TscPrinterService
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.material3.Text
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.clickable
import kotlinx.coroutines.delay
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.window.DialogProperties
import androidx.compose.material3.CircularProgressIndicator
import com.tsd.ascanner.ui.theme.statusCardColor
import com.tsd.ascanner.ui.theme.parseHexColorOrNull
import com.tsd.ascanner.ui.screens.select.SelectScreen
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Icon
import androidx.compose.foundation.layout.size
import com.tsd.ascanner.ui.components.parseServerIconOrFallback
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ButtonDefaults
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import com.tsd.ascanner.utils.DialogNumBus
import com.tsd.ascanner.utils.ServerDialogNum
import com.tsd.ascanner.data.docs.DialogNumEditFieldValue
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.type
import androidx.compose.ui.input.key.onPreviewKeyEvent

@OptIn(ExperimentalLayoutApi::class)
class MainActivity : ComponentActivity() {

    private val keyboardWedge = KeyboardWedgeInterceptor()

    private val scanReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: android.content.Context?, intent: android.content.Intent?) {
            intent ?: return
            val barcode = DataWedge.parseScanData(intent)
                ?: Newland.parseScanData(intent)
                ?: return
            ScanDataBus.emit(barcode)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
		val app = applicationContext as AScannerApp

        ScannerSettings.init(this)
        DataWedge.configureIntentOutput(this)

        val scanFilter = IntentFilter().apply {
            addAction(DataWedge.SCAN_ACTION)
            addAction(Newland.SCAN_RESULT_ACTION)
        }
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(scanReceiver, scanFilter, RECEIVER_EXPORTED)
        } else {
            registerReceiver(scanReceiver, scanFilter)
        }

        setContent {
            AScannerTheme(dynamicColor = false) {
                val navController = rememberNavController()
                val backStack by navController.currentBackStackEntryAsState()
                val currentRoute = backStack?.destination?.route ?: "tasks"

                val appColors = com.tsd.ascanner.ui.theme.AppTheme.colors
                var globalError by remember { mutableStateOf<String?>(null) }
				var globalDialog by remember { mutableStateOf<ServerDialog?>(null) }
				var dialogSending by remember { mutableStateOf(false) }
				var currentPrintRequest by remember { mutableStateOf<ServerPrintRequest?>(null) }
				var showPrinterDialog by remember { mutableStateOf(false) }
				var globalSelect by remember { mutableStateOf<ServerSelect?>(null) }
				var globalDialogNum by remember { mutableStateOf<ServerDialogNum?>(null) }
				var dialogNumSending by remember { mutableStateOf(false) }
				var dialogNumFieldValues by remember { mutableStateOf<Map<Int, String>>(emptyMap()) }
				val scope = rememberCoroutineScope()
                LaunchedEffect(Unit) {
                    ErrorBus.events.collectLatest { msg ->
                        globalError = msg
                    }
                }
				LaunchedEffect(Unit) {
					DialogBus.events.collectLatest { dlg ->
						val prev = globalDialog
						globalDialog = if (parseHexColorOrNull(dlg.backgroundColor) != null) {
							dlg
						} else if (prev != null && prev.form == dlg.form && prev.formId == dlg.formId) {
							dlg.copy(backgroundColor = prev.backgroundColor)
						} else {
							dlg
						}
						dialogSending = false
					}
				}
				// Handle print requests from server (MessageType="print")
				LaunchedEffect(Unit) {
					PrintBus.events.collectLatest { printRequest ->
						currentPrintRequest = printRequest
						// Always show printer selection dialog. Printing must be user-confirmed.
						showPrinterDialog = true
					}
				}
				LaunchedEffect(Unit) {
					SelectBus.events.collectLatest { select ->
						val prev = globalSelect
						globalSelect = if (parseHexColorOrNull(select.backgroundColor) != null) {
							select
						} else if (prev != null && prev.form == select.form && prev.formId == select.formId) {
							select.copy(backgroundColor = prev.backgroundColor)
						} else {
							select
						}
						val route = navController.currentBackStackEntry?.destination?.route
						if (route != "select") {
							navController.navigate("select") {
								launchSingleTop = true
							}
						}
					}
				}
				LaunchedEffect(Unit) {
					DialogNumBus.events.collectLatest { dlgNum ->
						globalDialogNum = dlgNum
						dialogNumSending = false
						dialogNumFieldValues = dlgNum.editFields.mapIndexed { idx, f -> idx to f.defaultText }.toMap()
					}
				}
				LaunchedEffect(Unit) {
					AppEventBus.events.collectLatest { ev ->
						when (ev) {
							is AppEvent.RequireLogin -> {
								// Clear local session and in-memory document state
								app.authService.clearLocalSession()
								app.docsService.clear()
								val route = navController.currentBackStackEntry?.destination?.route
								if (route != "login") {
									navController.navigate("login") {
										popUpTo(navController.graph.startDestinationId) { inclusive = true }
										launchSingleTop = true
									}
								}
							}
						}
					}
				}
				// Global navigation driven by server responses (Form field)
				LaunchedEffect(Unit) {
					app.docsService.navEvents.collectLatest { target ->
						val entry = navController.currentBackStackEntry
						val route = entry?.destination?.route
						val currentDocId = entry?.arguments?.getString("formId")?.let { Uri.decode(it) }
						val fromSelect = route == "select"
						when (target.form.lowercase()) {
							"doc" -> {
								val fid = target.formId
								if (!fid.isNullOrBlank()) {
									val alreadyOnSameDoc = route == "doc/{formId}" && currentDocId == fid
									if (!alreadyOnSameDoc) {
										navController.navigate("doc/${Uri.encode(fid)}") {
											if (fromSelect) {
												popUpTo("select") { inclusive = true }
											}
											launchSingleTop = true
										}
									}
								}
							}
							"pos" -> {
								if (route != "pos") {
									navController.navigate("pos") {
										if (fromSelect) {
											popUpTo("select") { inclusive = true }
										}
										launchSingleTop = true
									}
								}
							}
							"doclist", "tasks" -> {
								if (route != "tasks") {
									navController.navigate("tasks") {
										if (fromSelect) {
											popUpTo("select") { inclusive = true }
										}
										launchSingleTop = true
									}
								} else if (fromSelect) {
									// Defensive: if nav thinks we're on tasks but select is still on stack, pop it.
									navController.popBackStack()
								}
							}
						}
					}
				}
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    contentWindowInsets = WindowInsets.safeDrawing,
                    containerColor = appColors.background
                ) { padding ->
                    Box(modifier = Modifier.fillMaxSize()) {
                        NavHost(
                            navController = navController,
                            startDestination = "login"
                        ) {
                            composable("login") {
                                LoginScreen(
                                    onLoggedIn = {
                                        navController.navigate("tasks")
                                    }
                                )
                            }
                            composable("tasks") {
                                TasksScreen(
                                    paddingValues = padding,
                                    onOpenDoc = { formId ->
                                        navController.navigate("doc/${Uri.encode(formId)}")
                                    }
                                )
                            }
                            composable(
                                route = "doc/{formId}",
                                arguments = listOf(navArgument("formId") { type = NavType.StringType })
                            ) { entry ->
                                val formId = Uri.decode(entry.arguments!!.getString("formId")!!)
                                DocScreen(
                                    paddingValues = padding,
                                    formId = formId,
                                    onClose = { navController.popBackStack() }
                                )
                            }
                            composable("pos") {
                                PosScreen(
                                    paddingValues = padding,
                                    onClose = { navController.popBackStack() },
                                    onScanPosition = { formId ->
                                        // full-screen scan route removed; scanning handled inside PosScreen
                                    }
                                )
                            }
							composable("select") {
								SelectScreen(
									paddingValues = padding,
									select = globalSelect,
									onClose = { navController.popBackStack() }
								)
							}
                        }
                        val err = globalError
                        LaunchedEffect(err) {
                            if (err != null) {
                                delay(10000)
                                if (globalError == err) {
                                    globalError = null
                                }
                            }
                        }
                        if (err != null) {
                            Card(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp)
                                    .clickable { globalError = null },
                                shape = RoundedCornerShape(12.dp),
                                colors = CardDefaults.cardColors(
                                    containerColor = Color(0xFFD32F2F)
                                ),
                                elevation = CardDefaults.cardElevation(defaultElevation = 6.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        Icons.Rounded.Warning,
                                        contentDescription = null,
                                        tint = Color.White
                                    )
                                    Spacer(Modifier.width(10.dp))
                                    Text(
                                        text = err,
                                        color = Color.White,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                }
                            }
                        }

						// Global server-driven dialog (MessageType="dialog")
						val dlg = globalDialog
						if (dlg != null) {
							val bg = parseHexColorOrNull(dlg.backgroundColor) ?: statusCardColor(
								colors = appColors,
								status = dlg.status,
								statusColor = dlg.statusColor
							)
							val fg = if (bg.luminance() < 0.45f) Color.White else Color.Black
							AlertDialog(
								onDismissRequest = { /* non-dismissible */ },
								properties = DialogProperties(
									dismissOnBackPress = false,
									dismissOnClickOutside = false
								),
								containerColor = bg,
								title = {
									val title = dlg.header.ifBlank { "Сообщение" }
									Text(text = title, color = fg)
								},
								text = {
									val text = dlg.text.ifBlank { "" }
									Text(text = text, color = fg)
								},
								confirmButton = {
									Row(
										horizontalArrangement = Arrangement.spacedBy(8.dp),
										verticalAlignment = Alignment.CenterVertically
									) {
										val buttons = if (dlg.buttons.isNotEmpty()) dlg.buttons
										else listOf(com.tsd.ascanner.utils.ServerDialogButton(name = "OK", id = ""))
										buttons.forEach { b ->
											TextButton(
												enabled = !dialogSending,
												onClick = {
													val clickedDialog = dlg
													dialogSending = true
													if (b.id.isBlank()) {
														// Local OK/close button
														if (globalDialog === clickedDialog) {
															globalDialog = null
														}
														dialogSending = false
														return@TextButton
													}
													scope.launch {
														try {
															when (val res = app.docsService.sendButton(dlg.form, dlg.formId, b.id)) {
																is com.tsd.ascanner.data.docs.ButtonResult.Success -> {
																	// Close only if this is still the same dialog (avoid dismissing a newly arrived one)
																	if (globalDialog === clickedDialog) {
																		globalDialog = null
																	}
																}
																is com.tsd.ascanner.data.docs.ButtonResult.DialogShown -> {
																	// Next dialog will be shown via DialogBus; don't dismiss here to avoid races.
																}
																is com.tsd.ascanner.data.docs.ButtonResult.Error -> {
																	// User chose: close dialog on error and show banner
																	if (globalDialog === clickedDialog) {
																		globalDialog = null
																	}
																	ErrorBus.emit(res.message)
																}
															}
														} finally {
															dialogSending = false
														}
													}
												}
											) {
												Text(text = b.name.ifBlank { "OK" }, color = fg)
											}
										}
									}
								}
							)

							// Center-screen spinner while dialog button request is in-flight
							if (dialogSending) {
								Box(modifier = Modifier.fillMaxSize()) {
									CircularProgressIndicator(
										modifier = Modifier.align(Alignment.Center),
										color = Color(0xFF30323D)
									)
								}
							}
						}

						// Global numeric input dialog (MessageType="dialognum")
						val dlgNum = globalDialogNum
						if (dlgNum != null) {
							val numBg = statusCardColor(
								colors = appColors,
								status = dlgNum.status,
								statusColor = null
							)
							val numFg = if (numBg.luminance() < 0.45f) Color.White else Color.Black
							val firstFieldFocusRequester = remember { FocusRequester() }

							fun validateNumericField(field: com.tsd.ascanner.utils.DialogNumEditField, newVal: String): String? {
								val filtered = newVal.replace(',', '.')
								val maxIntDigits = field.fieldLength - field.fieldScale
								val dotIndex = filtered.indexOf('.')
								if (dotIndex < 0) {
									if (filtered.length <= maxIntDigits && filtered.all { c -> c.isDigit() }) return filtered
								} else {
									val intPart = filtered.substring(0, dotIndex)
									val fracPart = filtered.substring(dotIndex + 1)
									if (intPart.length <= maxIntDigits &&
										fracPart.length <= field.fieldScale &&
										intPart.all { c -> c.isDigit() } &&
										fracPart.all { c -> c.isDigit() } &&
										filtered.count { c -> c == '.' } == 1
									) return filtered
								}
								return null
							}

							fun validateStringField(field: com.tsd.ascanner.utils.DialogNumEditField, newVal: String): String? {
								return if (newVal.length <= field.fieldLength) newVal else null
							}

							AlertDialog(
								onDismissRequest = { /* non-dismissible */ },
								properties = DialogProperties(
									dismissOnBackPress = false,
									dismissOnClickOutside = false
								),
								containerColor = numBg,
								title = {
									Text(text = dlgNum.header.ifBlank { "Ввод данных" }, color = numFg)
								},
							text = {
								val renderField: @Composable (Int, com.tsd.ascanner.utils.DialogNumEditField, Modifier) -> Unit = { idx, field, baseModifier ->
									val currentVal = dialogNumFieldValues[idx] ?: ""
									val isNumber = field.fieldType.equals("Number", ignoreCase = true)
									OutlinedTextField(
										value = currentVal,
										onValueChange = { newVal ->
											val validated = if (isNumber) validateNumericField(field, newVal) else validateStringField(field, newVal)
											if (validated != null) {
												dialogNumFieldValues = dialogNumFieldValues.toMutableMap().apply { put(idx, validated) }
											}
										},
										label = if (field.text.isNotBlank()) { { Text(field.text) } } else null,
										readOnly = isNumber,
										keyboardOptions = if (isNumber) KeyboardOptions(keyboardType = KeyboardType.Decimal)
											else KeyboardOptions.Default,
										singleLine = true,
										colors = OutlinedTextFieldDefaults.colors(
											focusedTextColor = numFg,
											unfocusedTextColor = numFg,
											cursorColor = numFg,
											focusedLabelColor = numFg.copy(alpha = 0.7f),
											unfocusedLabelColor = numFg.copy(alpha = 0.7f),
											focusedBorderColor = numFg.copy(alpha = 0.7f),
											unfocusedBorderColor = numFg.copy(alpha = 0.4f)
										),
										modifier = baseModifier
											.then(if (idx == 0) Modifier.focusRequester(firstFieldFocusRequester) else Modifier)
											.then(if (isNumber) Modifier.onPreviewKeyEvent { event ->
												if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
												when (event.key) {
													Key.Backspace -> {
														val cur = dialogNumFieldValues[idx] ?: ""
														if (cur.isNotEmpty()) {
															dialogNumFieldValues = dialogNumFieldValues.toMutableMap().apply { put(idx, cur.dropLast(1)) }
														}
														true
													}
													else -> {
														val ch = when (event.key) {
															Key.Zero, Key.NumPad0 -> '0'
															Key.One, Key.NumPad1 -> '1'
															Key.Two, Key.NumPad2 -> '2'
															Key.Three, Key.NumPad3 -> '3'
															Key.Four, Key.NumPad4 -> '4'
															Key.Five, Key.NumPad5 -> '5'
															Key.Six, Key.NumPad6 -> '6'
															Key.Seven, Key.NumPad7 -> '7'
															Key.Eight, Key.NumPad8 -> '8'
															Key.Nine, Key.NumPad9 -> '9'
															Key.Period, Key.NumPadDot -> '.'
															Key.Comma -> ','
															else -> null
														}
														if (ch != null) {
															val cur = dialogNumFieldValues[idx] ?: ""
															val validated = validateNumericField(field, cur + ch)
															if (validated != null) {
																dialogNumFieldValues = dialogNumFieldValues.toMutableMap().apply { put(idx, validated) }
															}
															true
														} else false
													}
												}
											} else Modifier)
									)
								}
								Column(modifier = Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
										if (dlgNum.text.isNotBlank()) {
											Text(text = dlgNum.text, color = numFg)
											Spacer(Modifier.padding(top = 4.dp))
										}
									val indexedFields = dlgNum.editFields.mapIndexed { idx, f -> idx to f }
									val fieldGroups = mutableListOf<MutableList<Pair<Int, com.tsd.ascanner.utils.DialogNumEditField>>>()
									for (pair in indexedFields) {
										val isNum = pair.second.fieldType.equals("Number", ignoreCase = true)
										val lastGroup = fieldGroups.lastOrNull()
										val lastIsNum = lastGroup?.first()?.second?.fieldType?.equals("Number", ignoreCase = true) == true
										if (lastGroup != null && isNum && lastIsNum) {
											lastGroup.add(pair)
										} else {
											fieldGroups.add(mutableListOf(pair))
										}
									}
									fieldGroups.forEachIndexed { gi, group ->
										if (gi > 0) Spacer(Modifier.padding(top = 2.dp))
										val isNumGroup = group.first().second.fieldType.equals("Number", ignoreCase = true)
										if (isNumGroup && group.size > 1) {
											Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
												group.forEach { (idx, field) ->
													renderField(idx, field, Modifier.weight(1f))
												}
											}
										} else {
											group.forEach { (idx, field) ->
												renderField(idx, field, Modifier.fillMaxWidth())
											}
										}
									}
										LaunchedEffect(dlgNum) {
											firstFieldFocusRequester.requestFocus()
										}
									}
								},
							confirmButton = {
								val buttons = if (dlgNum.buttons.isNotEmpty()) dlgNum.buttons
									else listOf(com.tsd.ascanner.utils.DialogNumButton(name = "OK", id = ""))
								Column(verticalArrangement = Arrangement.spacedBy(2.dp), modifier = Modifier.fillMaxWidth()) {
									buttons.chunked(2).forEach { rowButtons ->
										Row(horizontalArrangement = Arrangement.spacedBy(4.dp), modifier = Modifier.fillMaxWidth()) {
											rowButtons.forEach { b ->
												val btnColor = parseHexColorOrNull(b.color)
												val btnFg = if (btnColor != null && btnColor.luminance() < 0.45f) Color.White
													else if (btnColor != null) Color.Black
													else numFg
												TextButton(
													enabled = !dialogNumSending,
													modifier = Modifier.weight(1f).heightIn(min = 44.dp),
													colors = ButtonDefaults.textButtonColors(
														containerColor = btnColor ?: Color.Transparent
													),
													onClick = {
														if (b.id.isBlank()) {
															globalDialogNum = null
															dialogNumFieldValues = emptyMap()
															dialogNumSending = false
															return@TextButton
														}
														val clickedDlg = dlgNum
														dialogNumSending = true
														scope.launch {
															try {
																val fields = dlgNum.editFields.mapIndexed { idx, ef ->
																	DialogNumEditFieldValue(
																		fieldId = ef.fieldId,
																		value = dialogNumFieldValues[idx] ?: ""
																	)
																}
																when (val res = app.docsService.sendDialogNum(
																	form = dlgNum.form,
																	formId = dlgNum.formId,
																	selectedId = b.id,
																	editFields = fields
																)) {
																	is com.tsd.ascanner.data.docs.ButtonResult.Success -> {
																		if (globalDialogNum === clickedDlg) {
																			globalDialogNum = null
																			dialogNumFieldValues = emptyMap()
																		}
																	}
																	is com.tsd.ascanner.data.docs.ButtonResult.DialogShown -> {
																		if (globalDialogNum === clickedDlg) {
																			globalDialogNum = null
																			dialogNumFieldValues = emptyMap()
																		}
																	}
																	is com.tsd.ascanner.data.docs.ButtonResult.Error -> {
																		if (globalDialogNum === clickedDlg) {
																			globalDialogNum = null
																			dialogNumFieldValues = emptyMap()
																		}
																		ErrorBus.emit(res.message)
																	}
																}
															} finally {
																dialogNumSending = false
															}
														}
													}
												) {
													val iconVec = b.icon?.takeIf { it.isNotBlank() }?.let { parseServerIconOrFallback(it) }
													if (iconVec != null) {
														Icon(imageVector = iconVec, contentDescription = null, tint = btnFg, modifier = Modifier.size(18.dp))
														Spacer(Modifier.width(4.dp))
													}
													Text(text = b.name.ifBlank { "OK" }, color = btnFg)
												}
											}
											if (rowButtons.size == 1) Spacer(Modifier.weight(1f))
										}
									}
								}
							}
							)

							if (dialogNumSending) {
								Box(modifier = Modifier.fillMaxSize()) {
									CircularProgressIndicator(
										modifier = Modifier.align(Alignment.Center),
										color = Color(0xFF30323D)
									)
								}
							}
						}

						// Global printer dialog (for MessageType="print")
						PrinterDialog(
							visible = showPrinterDialog,
							printRequest = currentPrintRequest,
							onDismiss = {
								showPrinterDialog = false
								currentPrintRequest = null
							},
							onPrintSuccess = {
								val req = currentPrintRequest
								showPrinterDialog = false
								currentPrintRequest = null
								if (req != null && req.selectOnExit.isNotBlank()) {
									scope.launch {
										app.docsService.sendSelect(
											form = req.form,
											formId = req.formId,
											selectedId = req.selectOnExit
										)
									}
								}
							}
						)
                    }
                }
            }
        }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (isHardwareScanKey(event.keyCode)) {
            if (event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
                ScanTriggerBus.emitPressed()
            } else if (event.action == KeyEvent.ACTION_UP) {
                ScanTriggerBus.emitReleased()
            }
        }
        if (ScannerSettings.keyboardModeEnabled) {
            if (keyboardWedge.onKeyEvent(event) { super.dispatchKeyEvent(it) }) {
                return true
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onDestroy() {
        runCatching { unregisterReceiver(scanReceiver) }
        super.onDestroy()
    }

    override fun onStop() {
        // IMPORTANT:
        // Do NOT logout on simple backgrounding (Home/app switch), otherwise returning to the app
        // causes server-side bearer invalidation and the app gets redirected to login on next request.
        // Logout only when Activity is actually finishing (closed by user / removed from recents).
        val app = applicationContext as AScannerApp
        if (isFinishing && app.authService.bearer != null) {
            app.appScope.launch {
                runCatching { app.authService.logout() }
            }
        }
        super.onStop()
    }

    private fun isHardwareScanKey(code: Int): Boolean {
        return when (code) {
            KeyEvent.KEYCODE_F1, KeyEvent.KEYCODE_F2, KeyEvent.KEYCODE_F3, KeyEvent.KEYCODE_F4,
            KeyEvent.KEYCODE_F5, KeyEvent.KEYCODE_F6, KeyEvent.KEYCODE_F7, KeyEvent.KEYCODE_F8,
            KeyEvent.KEYCODE_F9, KeyEvent.KEYCODE_F10, KeyEvent.KEYCODE_F11, KeyEvent.KEYCODE_F12,
            KeyEvent.KEYCODE_BUTTON_L1, KeyEvent.KEYCODE_BUTTON_R1,
            KeyEvent.KEYCODE_BUTTON_L2, KeyEvent.KEYCODE_BUTTON_R2,
            KeyEvent.KEYCODE_BUTTON_START, KeyEvent.KEYCODE_BUTTON_SELECT,
            KeyEvent.KEYCODE_BUTTON_1, KeyEvent.KEYCODE_BUTTON_2, KeyEvent.KEYCODE_BUTTON_3, KeyEvent.KEYCODE_BUTTON_4,
            KeyEvent.KEYCODE_BUTTON_5, KeyEvent.KEYCODE_BUTTON_6, KeyEvent.KEYCODE_BUTTON_7, KeyEvent.KEYCODE_BUTTON_8,
            KeyEvent.KEYCODE_BUTTON_9, KeyEvent.KEYCODE_BUTTON_10, KeyEvent.KEYCODE_BUTTON_11, KeyEvent.KEYCODE_BUTTON_12,
            KeyEvent.KEYCODE_CAMERA, KeyEvent.KEYCODE_FOCUS -> true
            else -> false
        }
    }
}


