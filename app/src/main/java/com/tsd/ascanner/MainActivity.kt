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
import com.tsd.ascanner.utils.AppEvent
import com.tsd.ascanner.utils.AppEventBus
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.graphics.Color
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.flow.collectLatest
import androidx.compose.foundation.clickable
import kotlinx.coroutines.delay
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

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
                LaunchedEffect(Unit) {
                    ErrorBus.events.collectLatest { msg ->
                        globalError = msg
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
                                    onClose = { navController.popBackStack() },
                                    onOpenPosition = {
                                        navController.navigate("pos")
                                    }
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
                                delay(3000)
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
        // App going to background or finishing from tasks screen -> notify server
        val app = applicationContext as AScannerApp
        if (app.authService.bearer != null) {
            lifecycleScope.launch {
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


