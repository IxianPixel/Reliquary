package com.malgra.reliquary.core.network.log

import java.util.logging.Level
import java.util.logging.Logger

actual object Log {
    actual fun d(tag: String, message: String, throwable: Throwable?) = log(Level.FINE, tag, message, throwable)
    actual fun i(tag: String, message: String, throwable: Throwable?) = log(Level.INFO, tag, message, throwable)
    actual fun w(tag: String, message: String, throwable: Throwable?) = log(Level.WARNING, tag, message, throwable)
    actual fun e(tag: String, message: String, throwable: Throwable?) = log(Level.SEVERE, tag, message, throwable)

    private fun log(level: Level, tag: String, message: String, throwable: Throwable?) {
        Logger.getLogger(tag).log(level, message, throwable)
    }
}
