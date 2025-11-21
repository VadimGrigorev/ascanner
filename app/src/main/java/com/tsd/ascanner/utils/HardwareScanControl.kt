package com.tsd.ascanner.utils

import android.content.Context

/**
 * Aggregates multiple vendor integrations (Zebra, Newland).
 * Safe to call on any device; unsupported actions are ignored.
 */
object HardwareScanControl {
    fun start(context: Context) {
        DataWedge.startScanning(context)
        Newland.startScanning(context)
    }

    fun stop(context: Context) {
        DataWedge.stopScanning(context)
        Newland.stopScanning(context)
    }
}


