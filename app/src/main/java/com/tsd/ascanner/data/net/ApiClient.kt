package com.tsd.ascanner.data.net

import android.util.Log
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.BufferedReader
import java.io.BufferedWriter
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.lang.IllegalStateException
import java.net.HttpURLConnection
import java.net.URL
import javax.net.ssl.HttpsURLConnection
import com.tsd.ascanner.utils.ErrorBus
import com.tsd.ascanner.utils.AppEventBus

class ApiClient(
    private val gson: Gson = Gson()
) {
    private val prettyGson: Gson = com.google.gson.GsonBuilder()
        .setPrettyPrinting()
        .create()

    suspend fun <T> postAndParse(path: String, body: Any, responseClass: Class<T>): T =
        withContext(Dispatchers.IO) {
                val url = URL(composeUrl(path))
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doInput = true
                    doOutput = true
                    useCaches = false
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                try {
                    val json = gson.toJson(body)
                    Log.d("ApiClient", buildString {
                        append("REQUEST POST ").append(url)
                        if (path.isNotEmpty()) append(" (path=").append(path).append(')')
                        append('\n').append(formatForLog(json))
                    })
                    BufferedWriter(OutputStreamWriter(connection.outputStream, Charsets.UTF_8)).use { w ->
                        w.write(json)
                    }
                    val code = connection.responseCode
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    val responseText = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { br ->
                        br.readText()
                    }
                    Log.d("ApiClient", buildString {
                        append("RESPONSE ").append(code).append(" from ").append(url)
                        append('\n').append(formatForLog(responseText))
                    })
                    // Global server-side error detection
                    throwIfServerError(responseText)
                    if (code !in 200..299) {
                        throw IllegalStateException("HTTP $code: $responseText")
                    }
                    // Be lenient to tolerate trailing commas or minor format issues in mock responses
                    val reader = com.google.gson.stream.JsonReader(java.io.StringReader(responseText)).apply {
                        isLenient = true
                    }
                    gson.fromJson(reader, responseClass)
                } finally {
                    connection.disconnect()
                }
        }

    suspend fun postForJsonElement(path: String, body: Any): com.google.gson.JsonElement =
        withContext(Dispatchers.IO) {
                val url = URL(composeUrl(path))
                val connection = (url.openConnection() as HttpURLConnection).apply {
                    requestMethod = "POST"
                    doInput = true
                    doOutput = true
                    useCaches = false
                    setRequestProperty("Content-Type", "application/json; charset=utf-8")
                    setRequestProperty("Accept", "application/json")
                    connectTimeout = 5000
                    readTimeout = 5000
                }
                try {
                    val json = gson.toJson(body)
                    Log.d("ApiClient", buildString {
                        append("REQUEST POST ").append(url)
                        if (path.isNotEmpty()) append(" (path=").append(path).append(')')
                        append('\n').append(formatForLog(json))
                    })
                    BufferedWriter(OutputStreamWriter(connection.outputStream, Charsets.UTF_8)).use { w ->
                        w.write(json)
                    }
                    val code = connection.responseCode
                    val stream = if (code in 200..299) connection.inputStream else connection.errorStream
                    val responseText = BufferedReader(InputStreamReader(stream, Charsets.UTF_8)).use { br ->
                        br.readText()
                    }
                    Log.d("ApiClient", buildString {
                        append("RESPONSE ").append(code).append(" from ").append(url)
                        append('\n').append(formatForLog(responseText))
                    })
                    // Global server-side error detection
                    throwIfServerError(responseText)
                    if (code !in 200..299) {
                        throw IllegalStateException("HTTP $code: $responseText")
                    }
                    gson.fromJson(responseText, com.google.gson.JsonElement::class.java)
                } finally {
                    connection.disconnect()
                }
        }

    private fun throwIfServerError(responseText: String) {
        try {
            val element = gson.fromJson(responseText, com.google.gson.JsonElement::class.java)
            if (element != null && element.isJsonObject) {
                val obj = element.asJsonObject
                val mt = if (obj.has("MessageType")) obj.get("MessageType").asString else null
				val form = if (obj.has("Form")) obj.get("Form").asString else null
                if (mt != null && mt.equals("error", ignoreCase = true)) {
                    val msg = if (obj.has("Message")) obj.get("Message").asString else "Ошибка"
					// Emit to global error bus for top-of-screen banner
                    ErrorBus.emit(msg)
					// Navigate to login if server instructs "Form":"login"
					if (form != null && form.equals("login", ignoreCase = true)) {
						AppEventBus.requireLogin()
					}
                    throw IllegalStateException(msg)
                }
            }
        } catch (_: Exception) {
            // Non-JSON or invalid JSON: ignore here, HTTP code handling covers non-JSON errors
        }
    }

    private fun composeUrl(path: String): String {
        val base = ApiConfig.baseUrl.trimEnd('/')
        // Real server expects all requests at root path without endpoints
        return "$base/"
    }

    // Retries disabled by design: each request uses a single attempt with 5s timeouts.

    private fun formatForLog(raw: String): String {
        return try {
            val masked = maskSensitive(raw)
            val element = gson.fromJson(masked, com.google.gson.JsonElement::class.java)
            prettyGson.toJson(element)
        } catch (_: Exception) {
            raw
        }
    }

    private fun maskSensitive(raw: String): String {
        return try {
            val element = gson.fromJson(raw, com.google.gson.JsonElement::class.java)
            maskRecursive(element)
            gson.toJson(element)
        } catch (_: Exception) {
            raw
        }
    }

    private fun maskRecursive(element: com.google.gson.JsonElement) {
        if (element.isJsonObject) {
            val obj = element.asJsonObject
            for ((key, value) in obj.entrySet()) {
                if (key.equals("Bearer", ignoreCase = true) ||
                    key.equals("Password", ignoreCase = true) ||
                    key.equals("Token", ignoreCase = true)
                ) {
                    obj.addProperty(key, "****")
                } else {
                    maskRecursive(value)
                }
            }
        } else if (element.isJsonArray) {
            val arr = element.asJsonArray
            arr.forEach { maskRecursive(it) }
        }
    }
}


