package com.tsd.ascanner.ui.components

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusProperties
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import kotlinx.coroutines.launch
import kotlinx.coroutines.yield

sealed interface SearchScanMode {
	data class Marker(val marker: String) : SearchScanMode
	data object ControlChars : SearchScanMode
}

@Composable
fun ServerSearchField(
	visible: Boolean,
	value: String,
	onValueChange: (String) -> Unit,
	label: String,
	scanMode: SearchScanMode,
	onScan: (String) -> Unit,
	modifier: Modifier = Modifier,
	onFocusChanged: ((Boolean) -> Unit)? = null
) {
	if (!visible) return

	val focusRequester = remember { FocusRequester() }
	val scope = rememberCoroutineScope()

	fun commitScan(code: String) {
		val trimmed = code.trim()
		if (trimmed.isBlank()) return
		onValueChange("")
		onScan(trimmed)
	}

	OutlinedTextField(
		value = value,
		onValueChange = { newValue ->
			when (scanMode) {
				is SearchScanMode.Marker -> {
					onValueChange(newValue)
					val idx = newValue.indexOf(scanMode.marker)
					if (idx >= 0) {
						// Keep backward-compatible behavior: send marker+tail as scanned payload
						commitScan(newValue.substring(idx))
					}
				}
				SearchScanMode.ControlChars -> {
					val idx = newValue.indexOfFirst { ch ->
						val code = ch.code
						((code in 0x00..0x1F) && code != 0x1D) || code == 0x7F
					}
					if (idx >= 0) {
						commitScan(newValue.substring(0, idx))
					} else {
						// Manual input should only filter and must NOT trigger scan.
						onValueChange(newValue)
					}
				}
			}
		},
		modifier = modifier
			.focusRequester(focusRequester)
			.onFocusChanged { onFocusChanged?.invoke(it.isFocused) },
		label = { Text(text = label) },
		trailingIcon = {
			if (value.isNotBlank()) {
				IconButton(
					modifier = Modifier.focusProperties { canFocus = false },
					onClick = {
						onValueChange("")
						// Очистка без blur: возвращаем фокус в поле.
						scope.launch {
							// Даем системе обработать возможную смену фокуса на trailing icon,
							// затем возвращаем фокус обратно в TextField.
							yield()
							focusRequester.requestFocus()
						}
					}
				) {
					Icon(
						imageVector = Icons.Outlined.Close,
						contentDescription = "Очистить"
					)
				}
			}
		},
		singleLine = true
	)
}

