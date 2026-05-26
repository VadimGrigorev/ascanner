package com.tsd.ascanner.utils

/**
 * Data class representing a single item to print.
 */
data class PrintItem(
    val pictureBase64: String,
    val pictureType: String = "bmp",
    val paperWidthMm: Float = 50f,
    val paperHeightMm: Float = 30f,
    val copies: Int = 1,
    val compression: String = ""
)

/**
 * Data class representing a print request from the server.
 * 
 * When server responds with MessageType="print", the response contains
 * an array of images to print along with paper dimensions.
 */
data class ServerPrintRequest(
    val form: String,
    val formId: String,
    val items: List<PrintItem>,
    val selectOnExit: String = ""
)
