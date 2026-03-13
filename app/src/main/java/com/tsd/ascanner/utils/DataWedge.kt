package com.tsd.ascanner.utils

import android.content.Context
import android.content.Intent
import android.os.Bundle

/**
 * Zebra DataWedge helper.
 * Configures the DataWedge profile for intent-based output (no keyboard wedge)
 * and provides scan-data parsing from the broadcast intent.
 * Safe no-ops on non-Zebra devices.
 */
object DataWedge {
    const val ACTION = "com.symbol.datawedge.api.ACTION"
    const val NOTIFICATION_ACTION = "com.symbol.datawedge.api.NOTIFICATION_ACTION"

    /** Custom action used for intent-output delivery of scanned barcodes. */
    const val SCAN_ACTION = "com.tsd.ascanner.SCAN"

    private const val EXTRA_SOFT_SCAN_TRIGGER = "com.symbol.datawedge.api.SOFT_SCAN_TRIGGER"
    private const val EXTRA_SET_CONFIG = "com.symbol.datawedge.api.SET_CONFIG"

    private const val EXTRA_REGISTER_FOR_NOTIFICATION = "com.symbol.datawedge.api.REGISTER_FOR_NOTIFICATION"
    private const val EXTRA_UNREGISTER_FOR_NOTIFICATION = "com.symbol.datawedge.api.UNREGISTER_FOR_NOTIFICATION"
    private const val EXTRA_APPLICATION_NAME = "com.symbol.datawedge.api.APPLICATION_NAME"
    private const val EXTRA_NOTIFICATION_TYPE = "com.symbol.datawedge.api.NOTIFICATION_TYPE"
    private const val NOTIF_TYPE_SCANNER_STATUS = "SCANNER_STATUS"

    private const val EXTRA_NOTIFICATION_BUNDLE = "com.symbol.datawedge.api.NOTIFICATION"
    private const val KEY_STATUS = "STATUS"

    private const val EXTRA_DATA_STRING = "com.symbol.datawedge.data_string"

    // ── Profile configuration ──────────────────────────────────────────

    /**
     * Programmatically create/update a DataWedge profile for this app
     * that uses **Intent Output** (broadcast) instead of Keystroke Output.
     * Should be called once at activity startup.
     */
    fun configureIntentOutput(context: Context) {
        val keystrokeOff = Bundle().apply {
            putString("PLUGIN_NAME", "KEYSTROKE")
            putString("RESET_CONFIG", "true")
            putBundle("PARAM_LIST", Bundle().apply {
                putString("keystroke_output_enabled", "false")
            })
        }
        sendBroadcast(context) {
            putExtra(EXTRA_SET_CONFIG, Bundle().apply {
                putString("PROFILE_NAME", context.packageName)
                putString("PROFILE_ENABLED", "true")
                putString("CONFIG_MODE", "CREATE_IF_NOT_EXIST")
                putBundle("PLUGIN_CONFIG", keystrokeOff)
            })
        }

        val intentOn = Bundle().apply {
            putString("PLUGIN_NAME", "INTENT")
            putString("RESET_CONFIG", "true")
            putBundle("PARAM_LIST", Bundle().apply {
                putString("intent_output_enabled", "true")
                putString("intent_action", SCAN_ACTION)
                putString("intent_delivery", "2") // 2 = broadcast
            })
        }
        sendBroadcast(context) {
            putExtra(EXTRA_SET_CONFIG, Bundle().apply {
                putString("PROFILE_NAME", context.packageName)
                putString("PROFILE_ENABLED", "true")
                putString("CONFIG_MODE", "UPDATE")
                putBundle("PLUGIN_CONFIG", intentOn)
            })
        }
    }

    // ── Scan data parsing ──────────────────────────────────────────────

    fun parseScanData(intent: Intent): String? {
        if (intent.action != SCAN_ACTION) return null
        return intent.getStringExtra(EXTRA_DATA_STRING)?.trim()?.ifEmpty { null }
    }

    // ── Soft-scan trigger ──────────────────────────────────────────────

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

    // ── Scanner-status notifications ───────────────────────────────────

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

    // ── Internal ───────────────────────────────────────────────────────

    private fun sendBroadcast(context: Context, configure: Intent.() -> Unit) {
        runCatching {
            val i = Intent(ACTION).apply(configure)
            context.sendBroadcast(i)
        }
    }
}
