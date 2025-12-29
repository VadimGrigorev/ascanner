package com.tsd.ascanner.data.docs

import com.google.gson.annotations.SerializedName

data class DocListRequest(
    @SerializedName("Bearer") val bearer: String,
    @SerializedName("Form") val form: String = "doclist",
    @SerializedName("FormId") val formId: String = "",
    @SerializedName("Request") val request: String = "refresh"
)

data class DocListResponse(
    @SerializedName("Tasks") val tasks: List<TaskDto> = emptyList()
)

data class TaskDto(
    @SerializedName("Name") val name: String,
    @SerializedName("Id") val id: String,
    @SerializedName("Orders") val orders: List<OrderDto> = emptyList()
)

data class OrderDto(
    @SerializedName("Name") val name: String,
    @SerializedName("Comment 1") val comment1: String? = null,
    @SerializedName("Comment 2") val comment2: String? = null,
    @SerializedName("Status") val status: String? = null,
    @SerializedName("Id") val id: String
)

data class DocScanRequest(
    @SerializedName("Bearer") val bearer: String,
    @SerializedName("Form") val form: String = "doclist",
    @SerializedName("FormId") val formId: String = "",
    @SerializedName("Request") val request: String = "scan",
    @SerializedName("Text") val text: String
)

data class DocOneResponse(
    @SerializedName("MessageType") val messageType: String? = null, // "refresh"
    @SerializedName("Form") val form: String? = null,               // "doc"
    @SerializedName("FormId") val formId: String? = null,
    @SerializedName("SelectedId") val selectedId: String? = null,
    @SerializedName("HeaderText") val headerText: String? = null,
    @SerializedName("StatusText") val statusText: String? = null,
    @SerializedName("Status") val status: String? = null,
    @SerializedName("Items") val items: List<DocItemDto> = emptyList()
)

data class DocOneRequest(
    @SerializedName("Bearer") val bearer: String,
    @SerializedName("Form") val form: String = "doc",
    @SerializedName("FormId") val formId: String,
    @SerializedName("Request") val request: String = "refresh"
)

data class DocItemDto(
    @SerializedName("Name") val name: String,
    @SerializedName("Id") val id: String,
    @SerializedName("StatusText") val statusText: String? = null,
    @SerializedName("Status") val status: String? = null
)

data class ErrorResponse(
    @SerializedName("Message") val message: String? = null,
    @SerializedName("MessageType") val messageType: String? = null
)

data class DocScan2Request(
    @SerializedName("Bearer") val bearer: String,
    @SerializedName("Form") val form: String = "doc",
    @SerializedName("FormId") val formId: String,
    @SerializedName("Request") val request: String = "scan",
    @SerializedName("Text") val text: String
)

// POS (one position) requests/responses
data class PosRequest(
    @SerializedName("Bearer") val bearer: String,
    @SerializedName("Form") val form: String = "pos",
    @SerializedName("FormId") val formId: String,
    @SerializedName("Request") val request: String = "refresh"
)

data class PosResponse(
    @SerializedName("MessageType") val messageType: String? = null, // "refresh"
    @SerializedName("Form") val form: String? = null,               // "pos"
    @SerializedName("FormId") val formId: String? = null,
    @SerializedName("SelectedId") val selectedId: String? = null,
    @SerializedName("HeaderText") val headerText: String? = null,
    @SerializedName("StatusText") val statusText: String? = null,
    @SerializedName("Status") val status: String? = null,
    @SerializedName("Items") val items: List<PosItemDto> = emptyList()
)

data class PosItemDto(
    @SerializedName("Name") val name: String,
    @SerializedName("Id") val id: String,
    @SerializedName("Text") val text: String? = null,
    @SerializedName("StatusText") val statusText: String? = null,
    @SerializedName("Status") val status: String? = null
)

// Delete requests for POS
data class PosDeleteRequest(
    @SerializedName("Bearer") val bearer: String,
    @SerializedName("Form") val form: String = "pos",
    @SerializedName("FormId") val formId: String,
    @SerializedName("Request") val request: String = "delete",
    @SerializedName("DeleteId") val deleteId: String = ""
)

// Request triggered by server-driven dialog button press
data class ButtonRequest(
	@SerializedName("Bearer") val bearer: String,
	@SerializedName("Form") val form: String,
	@SerializedName("FormId") val formId: String,
	@SerializedName("Request") val request: String = "dialog",
	@SerializedName("ButtonId") val buttonId: String
)


