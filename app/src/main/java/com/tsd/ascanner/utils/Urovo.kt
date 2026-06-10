package com.tsd.ascanner.utils

import android.content.Intent

/**
 * Urovo scanner result parsing via intent broadcast (ScanManager / ScanWedge).
 * Default action and extras per Urovo SDK: [SCAN_ACTION], [EXTRA_BARCODE_STRING].
 */
object Urovo {

    /** Default action when output mode is Intent (not keyboard wedge). */
    const val SCAN_ACTION = "android.intent.ACTION_DECODE_DATA"

    private const val EXTRA_BARCODE_STRING = "barcode_string"
    private const val EXTRA_BARCODE_BYTES = "barcode"

    fun parseScanData(intent: Intent): String? {
        if (intent.action != SCAN_ACTION) return null
        intent.getStringExtra(EXTRA_BARCODE_STRING)?.trim()?.ifEmpty { null }?.let { return it }
        val bytes = intent.getByteArrayExtra(EXTRA_BARCODE_BYTES) ?: return null
        return bytes.toString(Charsets.UTF_8).trim().ifEmpty { null }
    }
}
