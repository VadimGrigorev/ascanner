package com.tsd.ascanner.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Minimal Zebra DataWedge helper to sync app scanning UI with HW trigger.
 * Works only on devices with DataWedge. Safe no-ops elsewhere.
 */
object DataWedge {
    const val ACTION = "com.symbol.datawedge.api.ACTION"
    const val NOTIFICATION_ACTION = "com.symbol.datawedge.api.NOTIFICATION_ACTION"

    private const val EXTRA_SOFT_SCAN_TRIGGER = "com.symbol.datawedge.api.SOFT_SCAN_TRIGGER"

    private const val EXTRA_REGISTER_FOR_NOTIFICATION = "com.symbol.datawedge.api.REGISTER_FOR_NOTIFICATION"
    private const val EXTRA_UNREGISTER_FOR_NOTIFICATION = "com.symbol.datawedge.api.UNREGISTER_FOR_NOTIFICATION"
    private const val EXTRA_APPLICATION_NAME = "com.symbol.datawedge.api.APPLICATION_NAME"
    private const val EXTRA_NOTIFICATION_TYPE = "com.symbol.datawedge.api.NOTIFICATION_TYPE"
    private const val NOTIF_TYPE_SCANNER_STATUS = "SCANNER_STATUS"

    private const val EXTRA_NOTIFICATION_BUNDLE = "com.symbol.datawedge.api.NOTIFICATION"
    private const val KEY_STATUS = "STATUS" // "SCANNING", "IDLE", etc.

    fun startScanning(context: Context) {
        sendBroadcast(context) {
            putExtra(EXTRA_SOFT_SCAN_TRIGGER, "START_SCANNING")
        }
    }

    fun stopScanning(context: Context) {
        sendBroadcast(context) {
            putExtra(EXTRA_SOFT_SCAN_TRIGGER, "STOP_SCANNING")
        }
    }

    fun registerScannerStatus(context: Context) {
        val b = Bundle().apply {
            putString(EXTRA_APPLICATION_NAME, context.packageName)
            putString(EXTRA_NOTIFICATION_TYPE, NOTIF_TYPE_SCANNER_STATUS)
        }
        sendBroadcast(context) {
            putExtra(EXTRA_REGISTER_FOR_NOTIFICATION, b)
        }
    }

    fun unregisterScannerStatus(context: Context) {
        val b = Bundle().apply {
            putString(EXTRA_APPLICATION_NAME, context.packageName)
            putString(EXTRA_NOTIFICATION_TYPE, NOTIF_TYPE_SCANNER_STATUS)
        }
        sendBroadcast(context) {
            putExtra(EXTRA_UNREGISTER_FOR_NOTIFICATION, b)
        }
    }

    fun parseScannerStatus(intent: Intent): String? {
        if (intent.action != NOTIFICATION_ACTION) return null
        val b = intent.getBundleExtra(EXTRA_NOTIFICATION_BUNDLE) ?: return null
        val type = b.getString(EXTRA_NOTIFICATION_TYPE) ?: return null
        if (type != NOTIF_TYPE_SCANNER_STATUS) return null
        return b.getString(KEY_STATUS)
    }

    private fun sendBroadcast(context: Context, configure: Intent.() -> Unit) {
        runCatching {
            val i = Intent(ACTION).apply(configure)
            context.sendBroadcast(i)
        }
    }
}


