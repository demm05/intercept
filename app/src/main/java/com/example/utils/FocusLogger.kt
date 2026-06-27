package com.example.utils

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * A central logger that routes all logs to Logcat (for local debugging)
 * and to Firebase Crashlytics (for production breadcrumbs during crashes).
 */
object FocusLogger {
    fun d(tag: String, message: String) {
        Log.d(tag, message)
        FirebaseCrashlytics.getInstance().log("D/$tag: $message")
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        FirebaseCrashlytics.getInstance().log("I/$tag: $message")
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
        FirebaseCrashlytics.getInstance().log("W/$tag: $message")
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        FirebaseCrashlytics.getInstance().log("E/$tag: $message")
        throwable?.let {
            FirebaseCrashlytics.getInstance().recordException(it)
        }
    }
}
