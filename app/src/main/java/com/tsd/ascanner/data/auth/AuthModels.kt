package com.tsd.ascanner.data.auth

import com.google.gson.annotations.SerializedName

data class UserListRequest(
    @SerializedName("Form") val form: String = "login",
    @SerializedName("Request") val request: String = "userlist"
)

data class UserDto(
    @SerializedName("Name") val name: String,
    @SerializedName("Id") val id: String
)

data class UserListResponse(
    @SerializedName("Users") val users: List<UserDto> = emptyList()
)

data class LoginRequest(
    @SerializedName("Form") val form: String = "login",
    @SerializedName("User") val user: String,
    @SerializedName("Password") val password: String,
    @SerializedName("Request") val request: String = "login"
)

data class LoginResponse(
    @SerializedName("MessageType") val messageType: String? = null,
    @SerializedName("Bearer") val bearer: String? = null,
    @SerializedName("Message") val message: String? = null
)

data class LoginScanRequest(
    @SerializedName("Form") val form: String = "login",
    @SerializedName("FormId") val formId: String = "",
    @SerializedName("Request") val request: String = "scan",
    @SerializedName("Text") val text: String
)

data class LogoutRequest(
    @SerializedName("Form") val form: String = "login",
    @SerializedName("Bearer") val bearer: String,
    @SerializedName("Request") val request: String = "logout"
)

sealed interface LoginResult {
    data class Success(val bearer: String) : LoginResult
    data class Error(val message: String) : LoginResult
}


