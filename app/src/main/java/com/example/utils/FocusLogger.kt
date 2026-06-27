package com.example.utils

import android.util.Log
import com.google.firebase.crashlytics.FirebaseCrashlytics

/**
 * A central logger that routes all logs to Logcat (for local debugging)
 * and to Firebase Crashlytics (for production breadcrumbs during crashes).
 */
object FocusLogger {
    private fun logToCrashlytics(tag: String, message: String) {
        try {
            FirebaseCrashlytics.getInstance().log("$tag: $message")
        } catch (e: Exception) {
            // Firebase not initialized
        }
    }

    fun d(tag: String, message: String) {
        Log.d(tag, message)
        logToCrashlytics("D/$tag", message)
    }

    fun i(tag: String, message: String) {
        Log.i(tag, message)
        logToCrashlytics("I/$tag", message)
    }

    fun w(tag: String, message: String) {
        Log.w(tag, message)
        logToCrashlytics("W/$tag", message)
    }

    fun e(tag: String, message: String, throwable: Throwable? = null) {
        Log.e(tag, message, throwable)
        logToCrashlytics("E/$tag", message)
        if (throwable != null) {
            try {
                FirebaseCrashlytics.getInstance().recordException(throwable)
            } catch (e: Exception) {
                // Firebase not initialized
            }
        }
    }
}
