package com.tsd.ascanner.utils

/**
 * Preserves optional response metadata for MessageType="error" so UI can
 * react to server-provided selection hints while still treating the response as an error.
 */
class ServerErrorResponseException(
    message: String,
    val selectedId: String? = null
) : IllegalStateException(message)
