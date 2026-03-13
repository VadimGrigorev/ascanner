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

@Composable
fun ServerSearchField(
	visible: Boolean,
	value: String,
	onValueChange: (String) -> Unit,
	label: String,
	modifier: Modifier = Modifier,
	onFocusChanged: ((Boolean) -> Unit)? = null
) {
	if (!visible) return

	val focusRequester = remember { FocusRequester() }
	val scope = rememberCoroutineScope()

	OutlinedTextField(
		value = value,
		onValueChange = onValueChange,
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
						scope.launch {
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
