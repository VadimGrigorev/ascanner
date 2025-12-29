package com.tsd.ascanner.data.auth

import com.tsd.ascanner.data.net.ApiClient
import com.tsd.ascanner.utils.ServerDialogShownException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class AuthService(
    private val apiClient: ApiClient
){
    @Volatile
    var bearer: String? = null
        private set

    suspend fun fetchUsers(): List<UserDto> {
        val req = UserListRequest()
		return try {
			val resp = apiClient.postAndParse("/users", req, UserListResponse::class.java, logRequest = true)
			resp.users
		} catch (e: ServerDialogShownException) {
			// Dialog will be shown via global DialogBus
			emptyList()
		}
    }

    suspend fun login(userId: String, password: String): LoginResult {
        val req = LoginRequest(user = userId, password = password)
		val resp = try {
			apiClient.postAndParse("/login", req, LoginResponse::class.java, logRequest = true)
		} catch (e: ServerDialogShownException) {
			// Dialog will be shown via global DialogBus
			return LoginResult.DialogShown
		}
		if (resp.messageType?.equals("dialog", ignoreCase = true) == true) {
			// Dialog will be shown via global DialogBus
			return LoginResult.DialogShown
		}
        return if (resp.messageType?.equals("login", ignoreCase = true) == true && !resp.bearer.isNullOrBlank()) {
            bearer = resp.bearer
            LoginResult.Success(resp.bearer!!)
        } else {
            val msg = resp.message ?: "Ошибка авторизации"
            LoginResult.Error(msg)
        }
    }

    suspend fun scanLogin(text: String): LoginResult {
        val req = LoginScanRequest(text = text)
		val element = apiClient.postForJsonElement("/scanlogin", req, logRequest = true)
        val obj = element.asJsonObject
        val messageType = if (obj.has("MessageType")) obj.get("MessageType").asString else null
		if (messageType != null && messageType.equals("dialog", ignoreCase = true)) {
			return LoginResult.DialogShown
		}
        if (messageType != null && messageType.equals("error", ignoreCase = true)) {
            val msg = if (obj.has("Message")) obj.get("Message").asString else "Ошибка авторизации"
            return LoginResult.Error(msg)
        }
        val resp = com.google.gson.Gson().fromJson(obj, LoginResponse::class.java)
		if (resp.messageType?.equals("dialog", ignoreCase = true) == true) {
			return LoginResult.DialogShown
		}
        return if (resp.messageType?.equals("login", ignoreCase = true) == true && !resp.bearer.isNullOrBlank()) {
            bearer = resp.bearer
            LoginResult.Success(resp.bearer!!)
        } else {
            LoginResult.Error(resp.message ?: "Ошибка авторизации")
        }
    }

    // Fire-and-forget logout to inform server; does not clear local bearer
    suspend fun logout() {
        val b = bearer ?: return
        val req = LogoutRequest(bearer = b)
        // Ignore response and errors; best-effort ping
		runCatching { apiClient.postForJsonElement("/logout", req, logRequest = false) }
    }

	fun clearLocalSession() {
		bearer = null
	}
}


