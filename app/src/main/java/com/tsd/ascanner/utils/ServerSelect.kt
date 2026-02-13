package com.tsd.ascanner.utils

import com.tsd.ascanner.data.docs.ActionButtonDto

data class ServerSelect(
	val form: String,
	val formId: String,
	val selectedId: String? = null,
	val headerText: String? = null,
	val statusText: String? = null,
	val status: String? = null,
	val statusColor: String? = null,
	val backgroundColor: String? = null,
	val searchAvailable: String? = null,
	val items: List<ServerSelectItem> = emptyList(),
	val buttons: List<ActionButtonDto> = emptyList()
)

data class ServerSelectItem(
	val name: String,
	val id: String,
	val comment: String? = null,
	val status: String? = null,
	val statusColor: String? = null,
	val icon: String? = null
)

