package com.tsd.ascanner.utils

import android.content.Context
import android.content.Intent

/**
 * Best-effort Newland scanner control and result parsing via common broadcast actions.
 * Different firmware may support one of the following variants.
 */
object Newland {

    // ── Scan result broadcast ──────────────────────────────────────────

    /** Action sent by Newland scanners when a barcode is decoded. */
    const val SCAN_RESULT_ACTION = "nlscan.action.SCANNER_RESULT"

    private const val EXTRA_BARCODE = "SCAN_BARCODE1"

    fun parseScanData(intent: Intent): String? {
        if (intent.action != SCAN_RESULT_ACTION) return null
        return intent.getStringExtra(EXTRA_BARCODE)?.trim()?.ifEmpty { null }
    }

    // ── Trigger control ────────────────────────────────────────────────

    fun startScanning(context: Context) {
        send(context, Intent("nlscan.action.TRIGSCAN").apply { putExtra("SCAN_ON", true) })
        send(context, Intent("nlscan.action.SCANNER_TRIG").apply { putExtra("SCAN_TRIGGER", "START") })
        send(context, Intent("nlscan.action.SCANNER_TRIG").apply { putExtra("SCAN_TRIGGER", "on") })
        send(context, Intent("nlscan.action.START_SCAN"))
    }

    fun stopScanning(context: Context) {
        send(context, Intent("nlscan.action.TRIGSCAN").apply { putExtra("SCAN_ON", false) })
        send(context, Intent("nlscan.action.SCANNER_TRIG").apply { putExtra("SCAN_TRIGGER", "STOP") })
        send(context, Intent("nlscan.action.SCANNER_TRIG").apply { putExtra("SCAN_TRIGGER", "off") })
        send(context, Intent("nlscan.action.STOP_SCAN"))
    }

    private fun send(context: Context, intent: Intent) {
        runCatching { context.sendBroadcast(intent) }
    }
}
