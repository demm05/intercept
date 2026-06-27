package com.example.service

import com.example.utils.FocusLogger
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * Manages active sessions and unlock expirations for intercepted apps.
 */
class SessionManager(
    private val scope: CoroutineScope,
    private val timeProvider: () -> Long = { System.currentTimeMillis() },
    private val onSessionExpired: (String) -> Unit
) {
    // Maps packageName to the absolute timestamp (in ms) when the unlock expires.
    private val sessionExpiries = ConcurrentHashMap<String, Long>()
    
    private var sessionMonitorJob: Job? = null
    private var activeSessionPackage: String? = null

    /**
     * Unlocks an app for the specified number of minutes.
     */
    fun unlockApp(packageName: String, limitMinutes: Int) {
        val now = timeProvider()
        val limitMs = limitMinutes * 60 * 1000L
        val expiryTime = now + limitMs
        
        sessionExpiries[packageName] = expiryTime
        FocusLogger.d("SessionManager", "Unlocked $packageName until $expiryTime ($limitMinutes min)")
        
        startSessionMonitor(packageName, expiryTime)
    }

    /**
     * Checks whether an app is currently within its unlocked grace period/session.
     */
    fun isAppUnlocked(packageName: String): Boolean {
        val expiryTime = sessionExpiries[packageName] ?: return false
        if (timeProvider() < expiryTime) {
            return true
        }
        
        // Expiry has passed. Clean it up.
        sessionExpiries.remove(packageName)
        return false
    }

    /**
     * Actively monitors the current foreground session so we can aggressively kick the user
     * out the second their time expires, rather than waiting for them to trigger a new event.
     */
    private fun startSessionMonitor(packageName: String, expiryTime: Long) {
        if (activeSessionPackage == packageName && sessionMonitorJob?.isActive == true) return

        activeSessionPackage = packageName
        sessionMonitorJob?.cancel()
        sessionMonitorJob = scope.launch {
            while (isActive) {
                val now = timeProvider()
                if (now >= expiryTime) {
                    FocusLogger.i("SessionManager", "Session limit reached for $packageName. Firing expiry callback.")
                    sessionExpiries.remove(packageName)
                    onSessionExpired(packageName)
                    break
                }
                delay(1000) // Check every 1 second
            }
        }
    }

    /**
     * Applies a short, transient bypass cooldown (e.g. 1 min) for temporary actions
     * like exiting settings or closing an app and coming back immediately.
     */
    fun applyTransientCooldown(packageName: String, bypassDurationSeconds: Int) {
        val expiryTime = timeProvider() + (bypassDurationSeconds * 1000L)
        sessionExpiries[packageName] = expiryTime
        FocusLogger.d("SessionManager", "Applied transient $bypassDurationSeconds s cooldown for $packageName")
    }

    fun clearAll() {
        sessionExpiries.clear()
        sessionMonitorJob?.cancel()
        activeSessionPackage = null
    }
}
