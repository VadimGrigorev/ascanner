package com.tsd.ascanner.utils

/**
 * Data class representing a print request from the server.
 * 
 * When server responds with MessageType="print", the response contains
 * a bitmap image to print along with paper dimensions.
 */
data class ServerPrintRequest(
    val form: String,
    val formId: String,
    val pictureBase64: String,
    val pictureType: String,  // "bmp"
    val paperWidthMm: Float,
    val paperHeightMm: Float,
    val copies: Int
)
