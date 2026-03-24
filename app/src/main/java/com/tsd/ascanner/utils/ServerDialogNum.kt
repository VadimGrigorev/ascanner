package com.tsd.ascanner.utils

data class DialogNumEditField(
	val text: String,
	val fieldType: String,
	val defaultText: String,
	val fieldLength: Int,
	val fieldScale: Int,
	val fieldId: String
)

data class DialogNumButton(
	val name: String,
	val id: String,
	val icon: String? = null,
	val color: String? = null
)

data class ServerDialogNum(
	val form: String,
	val formId: String,
	val header: String,
	val text: String,
	val status: String,
	val editFields: List<DialogNumEditField>,
	val buttons: List<DialogNumButton>
)
