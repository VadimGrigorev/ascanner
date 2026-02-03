package com.tsd.ascanner

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
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
import android.view.KeyEvent
import com.tsd.ascanner.utils.ScanTriggerBus
import com.tsd.ascanner.utils.ErrorBus
import com.tsd.ascanner.utils.DialogBus
import com.tsd.ascanner.utils.PrintBus
import com.tsd.ascanner.utils.ServerDialog
import com.tsd.ascanner.utils.ServerPrintRequest
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

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
		val app = applicationContext as AScannerApp

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
				val scope = rememberCoroutineScope()
                LaunchedEffect(Unit) {
                    ErrorBus.events.collectLatest { msg ->
                        globalError = msg
                    }
                }
				LaunchedEffect(Unit) {
					DialogBus.events.collectLatest { dlg ->
						globalDialog = dlg
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
						when (target.form.lowercase()) {
							"doc" -> {
								val fid = target.formId
								if (!fid.isNullOrBlank()) {
									val alreadyOnSameDoc = route == "doc/{formId}" && currentDocId == fid
									if (!alreadyOnSameDoc) {
										navController.navigate("doc/${Uri.encode(fid)}") {
											launchSingleTop = true
										}
									}
								}
							}
							"pos" -> {
								if (route != "pos") {
									navController.navigate("pos") {
										launchSingleTop = true
									}
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
                        }
                        val err = globalError
                        LaunchedEffect(err) {
                            if (err != null) {
                                delay(15000)
                                if (globalError == err) {
                                    globalError = null
                                }
                            }
                        }
                        if (err != null) {
                            Column(
                                modifier = Modifier
                                    .align(Alignment.TopCenter)
                                    .fillMaxWidth()
                                    .padding(8.dp)
                                    .background(Color(0xFFFFCDD2))
                                    .clickable { globalError = null }
                                    .padding(10.dp)
                            ) {
                                Text(text = err, color = Color.Red)
                            }
                        }

						// Global server-driven dialog (MessageType="dialog")
						val dlg = globalDialog
						if (dlg != null) {
							val bg = statusCardColor(
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

						// Global printer dialog (for MessageType="print")
						PrinterDialog(
							visible = showPrinterDialog,
							printRequest = currentPrintRequest,
							onDismiss = {
								showPrinterDialog = false
								currentPrintRequest = null
							},
							onPrintSuccess = {
								showPrinterDialog = false
								currentPrintRequest = null
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
        return super.dispatchKeyEvent(event)
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


