package com.tsd.ascanner.utils

import android.content.Context
import android.content.Intent

/**
 * Best-effort Newland scanner control via common broadcast actions.
 * Different firmware may support one of the following.
 */
object Newland {
    fun startScanning(context: Context) {
        // Variant 1
        send(context, Intent("nlscan.action.TRIGSCAN").apply { putExtra("SCAN_ON", true) })
        // Variant 2
        send(context, Intent("nlscan.action.SCANNER_TRIG").apply { putExtra("SCAN_TRIGGER", "START") })
        send(context, Intent("nlscan.action.SCANNER_TRIG").apply { putExtra("SCAN_TRIGGER", "on") })
        // Variant 3
        send(context, Intent("nlscan.action.START_SCAN"))
    }

    fun stopScanning(context: Context) {
        // Variant 1
        send(context, Intent("nlscan.action.TRIGSCAN").apply { putExtra("SCAN_ON", false) })
        // Variant 2
        send(context, Intent("nlscan.action.SCANNER_TRIG").apply { putExtra("SCAN_TRIGGER", "STOP") })
        send(context, Intent("nlscan.action.SCANNER_TRIG").apply { putExtra("SCAN_TRIGGER", "off") })
        // Variant 3
        send(context, Intent("nlscan.action.STOP_SCAN"))
    }

    private fun send(context: Context, intent: Intent) {
        runCatching { context.sendBroadcast(intent) }
    }
}


