package com.tsd.ascanner.ui.screens.auth

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material3.Checkbox
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.ui.unit.dp
import androidx.compose.material.icons.outlined.Refresh
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.tsd.ascanner.data.auth.AuthService
import com.tsd.ascanner.data.auth.UserDto
import com.tsd.ascanner.data.net.ServerSettings
import com.tsd.ascanner.ui.theme.AppTheme
import kotlinx.coroutines.launch
import com.tsd.ascanner.AScannerApp
import com.tsd.ascanner.utils.ErrorBus
import com.tsd.ascanner.utils.UserMessageMapper
import com.tsd.ascanner.utils.DebugSession
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.draw.alpha

class LoginViewModel(
    private val authService: AuthService
) : ViewModel() {
    var users by mutableStateOf<List<UserDto>>(emptyList())
        private set
    var selectedUser by mutableStateOf<UserDto?>(null)
        private set
    var password by mutableStateOf("")
        private set
    var loading by mutableStateOf(false)
        private set
    var errorMessage by mutableStateOf<String?>(null)
        private set
    var loginSucceeded by mutableStateOf(false)
        private set

    fun init() {
        if (loading || users.isNotEmpty()) return
        loading = true
        errorMessage = null
        viewModelScope.launch {
            try {
                val fetched = authService.fetchUsers()
                users = fetched
                selectedUser = fetched.firstOrNull()
            } catch (e: Exception) {
                errorMessage = UserMessageMapper.map(e) ?: e.message ?: "Ошибка загрузки пользователей"
            } finally {
                loading = false
            }
        }
    }

    fun selectUser(user: UserDto) {
        selectedUser = user
    }

    fun onPasswordChange(value: String) {
        password = value
    }

    fun login() {
        val u = selectedUser ?: run {
            errorMessage = "Выберите пользователя"
            return
        }
        if (password.isEmpty()) {
            errorMessage = "Введите пароль"
            return
        }
        loading = true
        errorMessage = null
        viewModelScope.launch {
            try {
                when (val res = authService.login(u.id, password)) {
                    is com.tsd.ascanner.data.auth.LoginResult.Success -> {
                        password = ""
                        loginSucceeded = true
                    }
                    is com.tsd.ascanner.data.auth.LoginResult.Error -> {
                        errorMessage = res.message
                    }
					is com.tsd.ascanner.data.auth.LoginResult.DialogShown -> {
						// Dialog will be shown via global DialogBus
					}
                }
            } catch (e: Exception) {
                errorMessage = UserMessageMapper.map(e) ?: e.message ?: "Ошибка сети"
            } finally {
                loading = false
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun LoginScreen(
    onLoggedIn: () -> Unit
) {
    val colors = AppTheme.colors
    val ctx = androidx.compose.ui.platform.LocalContext.current
    val app = ctx.applicationContext as AScannerApp
    val scope = rememberCoroutineScope()
	var serverUrl by remember { mutableStateOf(ServerSettings.getBaseUrl(ctx)) }
    val vm = viewModel<LoginViewModel>(factory = object : androidx.lifecycle.ViewModelProvider.Factory {
        override fun <T : ViewModel> create(modelClass: Class<T>): T {
            @Suppress("UNCHECKED_CAST")
            return LoginViewModel(app.authService) as T
        }
    })

    LaunchedEffect(Unit) { vm.init() }
    LaunchedEffect(vm.loginSucceeded) {
        if (vm.loginSucceeded) onLoggedIn()
    }
    LaunchedEffect(vm.errorMessage) {
        vm.errorMessage?.let { ErrorBus.emit(it) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
		OutlinedTextField(
			value = serverUrl,
			onValueChange = { value ->
				serverUrl = value
				ServerSettings.setBaseUrl(ctx, value)
			},
			modifier = Modifier.fillMaxWidth(),
			label = { Text(text = "Адрес сервера/номер компьютера") },
			singleLine = true
		)
		Spacer(Modifier.height(12.dp))

        // Hidden input to capture hardware scanner text at login screen
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
                fun commitIfReady(code: String) {
                    if (code.length < 4) return
                    scope.launch {
                        try {
                            when (val res = app.authService.scanLogin(code)) {
                                is com.tsd.ascanner.data.auth.LoginResult.Success -> {
                                    onLoggedIn()
                                }
                                is com.tsd.ascanner.data.auth.LoginResult.Error -> {
                                    ErrorBus.emit(res.message)
                                }
								is com.tsd.ascanner.data.auth.LoginResult.DialogShown -> {
									// Dialog will be shown via global DialogBus
								}
                            }
                        } catch (e: Exception) {
                            ErrorBus.emit(UserMessageMapper.map(e) ?: e.message ?: "Ошибка сети")
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
                            if (code.isNotEmpty()) commitIfReady(code)
                            editText.setText("")
                        } else {
                            debounceJob?.cancel()
                            debounceJob = scope.launch {
                                kotlinx.coroutines.delay(120)
                                val code = editText.text.toString().trim()
                                if (code.isNotEmpty()) {
                                    commitIfReady(code)
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
                        if (code.isNotEmpty()) commitIfReady(code)
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

        Text(text = androidx.compose.ui.res.stringResource(id = com.tsd.ascanner.R.string.user_select), color = colors.textPrimary)
        Spacer(Modifier.height(8.dp))

        var expanded by remember { mutableStateOf(false) }
        ExposedDropdownMenuBox(
            expanded = expanded,
            onExpandedChange = { expanded = !expanded },
        ) {
            OutlinedTextField(
                modifier = Modifier
                    .menuAnchor()
                    .fillMaxWidth(),
                value = vm.selectedUser?.name ?: "",
                onValueChange = {},
                readOnly = true,
                label = { Text(text = androidx.compose.ui.res.stringResource(id = com.tsd.ascanner.R.string.user_label)) },
                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
                colors = TextFieldDefaults.colors()
            )
            ExposedDropdownMenu(
                expanded = expanded,
                onDismissRequest = { expanded = false }
            ) {
                vm.users.forEach { user ->
                    DropdownMenuItem(
                        text = { Text(user.name) },
                        onClick = {
                            vm.selectUser(user)
                            expanded = false
                        }
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))

        OutlinedTextField(
            value = vm.password,
            onValueChange = vm::onPasswordChange,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(text = androidx.compose.ui.res.stringResource(id = com.tsd.ascanner.R.string.password_label)) },
			visualTransformation = PasswordVisualTransformation(),
			keyboardOptions = KeyboardOptions.Default.copy(
				keyboardType = KeyboardType.Password,
				imeAction = ImeAction.Done
			),
			keyboardActions = KeyboardActions(
				onDone = {
					vm.login()
				}
			),
			singleLine = true
        )

        Spacer(Modifier.height(16.dp))

        Button(
            onClick = { vm.login() },
            modifier = Modifier.fillMaxWidth().height(56.dp),
            enabled = !vm.loading && vm.selectedUser != null
        ) {
            if (vm.loading) {
                CircularProgressIndicator(
                    modifier = Modifier
                        .size(20.dp)
                        .align(Alignment.CenterVertically),
                    strokeWidth = 2.dp,
                    color = androidx.compose.ui.graphics.Color(0xFF30323D)
                )
            } else {
                Text(text = androidx.compose.ui.res.stringResource(id = com.tsd.ascanner.R.string.login_action))
            }
        }

		Spacer(Modifier.height(12.dp))

		Row(
			modifier = Modifier.fillMaxWidth(),
			verticalAlignment = Alignment.CenterVertically
		) {
			Checkbox(
				checked = DebugSession.debugModeEnabled,
				onCheckedChange = { DebugSession.debugModeEnabled = it }
			)
			Spacer(modifier = Modifier.width(8.dp))
			Text(text = "Режим отладки", color = colors.textSecondary)
		}

        Spacer(Modifier.height(8.dp))
    }

    // Retry FAB shown only if initial users fetch failed
    if (vm.errorMessage != null && !vm.loading && vm.users.isEmpty()) {
        androidx.compose.foundation.layout.Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
        ) {
            FloatingActionButton(
                onClick = { vm.init() },
                modifier = Modifier.align(Alignment.BottomEnd),
                containerColor = colors.secondary,
                contentColor = colors.textPrimary
            ) {
                Icon(imageVector = Icons.Outlined.Refresh, contentDescription = "Обновить")
            }
        }
    }

	// Version label at bottom-right
	androidx.compose.foundation.layout.Box(
		modifier = Modifier
			.fillMaxSize()
			.padding(16.dp)
	) {
		Text(
			text = "1.7 версия",
			modifier = Modifier.align(Alignment.BottomStart),
			color = colors.textSecondary
		)
	}

    BackHandler(enabled = false) { /* disable back from login */ }
}


