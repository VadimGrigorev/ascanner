package com.tsd.ascanner.utils

data class ServerDialog(
	val form: String,
	val formId: String,
	val header: String,
	val text: String,
	val status: String,
	val buttons: List<ServerDialogButton>
)

data class ServerDialogButton(
	val name: String,
	val id: String
)


