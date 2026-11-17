package com.malgra.reliquary.core.network.log

/** Logging for shared code: Logcat on Android, java.util.logging on desktop. Same shape as `android.util.Log`. */
expect object Log {
    fun d(tag: String, message: String, throwable: Throwable? = null)
    fun i(tag: String, message: String, throwable: Throwable? = null)
    fun w(tag: String, message: String, throwable: Throwable? = null)
    fun e(tag: String, message: String, throwable: Throwable? = null)
}
