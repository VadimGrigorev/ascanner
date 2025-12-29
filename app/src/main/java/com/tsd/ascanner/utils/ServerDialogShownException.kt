package com.tsd.ascanner.utils

/**
 * Thrown internally to short-circuit normal response parsing when server returns
 * a server-driven dialog (MessageType="dialog"). UI should not treat this as an error.
 */
class ServerDialogShownException : RuntimeException()


