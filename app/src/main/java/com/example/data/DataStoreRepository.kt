package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.*
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/**
 * DataStoreRepository handles the persistence of user configurations securely
 * and asynchronously using Jetpack DataStore Preferences.
 */
val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "focus_interceptor_prefs")

class DataStoreRepository(private val context: Context) {

    companion object {
        private val KEY_SERVICE_ACTIVE = booleanPreferencesKey("service_active")
        private val KEY_TARGETED_PACKAGES = stringPreferencesKey("targeted_packages")
        private val KEY_INTERCEPTION_COUNT = intPreferencesKey("interception_count")
        private val KEY_COUNTDOWN_DURATION = intPreferencesKey("countdown_duration")
        private val KEY_SESSION_LIMIT_MINUTES = intPreferencesKey("session_limit_minutes")
        private val KEY_BYPASS_DURATION_SECONDS = intPreferencesKey("bypass_duration_seconds")
        
        // Anti-bypass reboot flags:
        // is_disabled_pending: general active toggle is queued to turn OFF on next boot
        private val KEY_IS_DISABLED_PENDING = booleanPreferencesKey("is_disabled_pending")
        // pending_disabled_packages: individual apps queued to be untargeted/removed on next boot
        private val KEY_PENDING_DISABLED_PACKAGES = stringPreferencesKey("pending_disabled_packages")

        // Advanced Protection Features
        private val KEY_STRICT_APP_INFO_BLOCK = booleanPreferencesKey("strict_app_info_block")
        private val KEY_AGGRESSIVE_PIP_PROTECTION = booleanPreferencesKey("aggressive_pip_protection")
        private val KEY_AUTO_DISMISS_OVERLAY = booleanPreferencesKey("auto_dismiss_overlay")
    }

    /**
     * Live active state of the interceptor service blocker.
     */
    val isServiceActive: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_SERVICE_ACTIVE] ?: true
    }

    /**
     * Set of currently targeted application package names.
     */
    val targetedPackages: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        val csv = preferences[KEY_TARGETED_PACKAGES] ?: ""
        if (csv.isEmpty()) emptySet() else csv.split(",").toSet()
    }

    /**
     * Counter for completed mindful intervals.
     */
    val interceptionCount: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_INTERCEPTION_COUNT] ?: 0
    }

    /**
     * Configured mindfulness countdown duration in seconds.
     */
    val countdownDurationSeconds: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_COUNTDOWN_DURATION] ?: 10
    }

    /**
     * Configured session limit in minutes (0 means no limit).
     */
    val sessionLimitMinutes: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_SESSION_LIMIT_MINUTES] ?: 0
    }

    /**
     * Configured bypass grace period in seconds.
     */
    val bypassDurationSeconds: Flow<Int> = context.dataStore.data.map { preferences ->
        preferences[KEY_BYPASS_DURATION_SECONDS] ?: 60
    }

    /**
     * Flow of general pending disable toggle status.
     */
    val isDisabledPending: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_IS_DISABLED_PENDING] ?: false
    }

    /**
     * Flow of per-app package names pending disable status upon restart.
     */
    val pendingDisabledPackages: Flow<Set<String>> = context.dataStore.data.map { preferences ->
        val csv = preferences[KEY_PENDING_DISABLED_PACKAGES] ?: ""
        if (csv.isEmpty()) emptySet() else csv.split(",").toSet()
    }

    /**
     * Advanced: Strict App Info Block (Kicks to Home if Force Stop screen is opened).
     */
    val isStrictAppInfoBlockEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_STRICT_APP_INFO_BLOCK] ?: false // Default OFF
    }

    /**
     * Advanced: Aggressive PiP Protection (Forces apps out of PiP mode).
     */
    val isAggressivePipProtectionEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_AGGRESSIVE_PIP_PROTECTION] ?: false // Default OFF
    }

    /**
     * Advanced: Auto-Dismiss Overlay (Dismisses overlay if app goes to background).
     */
    val isAutoDismissOverlayEnabled: Flow<Boolean> = context.dataStore.data.map { preferences ->
        preferences[KEY_AUTO_DISMISS_OVERLAY] ?: false // Default OFF
    }

    suspend fun setServiceActive(active: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SERVICE_ACTIVE] = active
        }
    }

    suspend fun setTargetedPackages(packages: Set<String>) {
        context.dataStore.edit { preferences ->
            preferences[KEY_TARGETED_PACKAGES] = packages.joinToString(",")
        }
    }

    suspend fun addTargetedPackage(packageName: String) {
        context.dataStore.edit { preferences ->
            val csv = preferences[KEY_TARGETED_PACKAGES] ?: ""
            val current = if (csv.isEmpty()) mutableSetOf() else csv.split(",").toMutableSet()
            current.add(packageName)
            // Ensure we remove it from pending disabled if we re-enable it
            val pendingCsv = preferences[KEY_PENDING_DISABLED_PACKAGES] ?: ""
            val pending = if (pendingCsv.isEmpty()) mutableSetOf() else pendingCsv.split(",").toMutableSet()
            pending.remove(packageName)
            
            preferences[KEY_TARGETED_PACKAGES] = current.joinToString(",")
            preferences[KEY_PENDING_DISABLED_PACKAGES] = pending.joinToString(",")
        }
    }

    suspend fun removeTargetedPackage(packageName: String) {
        context.dataStore.edit { preferences ->
            val csv = preferences[KEY_TARGETED_PACKAGES] ?: ""
            val current = if (csv.isEmpty()) mutableSetOf() else csv.split(",").toMutableSet()
            current.remove(packageName)
            preferences[KEY_TARGETED_PACKAGES] = current.joinToString(",")
        }
    }

    suspend fun incrementInterceptionCount() {
        context.dataStore.edit { preferences ->
            val current = preferences[KEY_INTERCEPTION_COUNT] ?: 0
            preferences[KEY_INTERCEPTION_COUNT] = current + 1
        }
    }

    suspend fun setCountdownDuration(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_COUNTDOWN_DURATION] = seconds
        }
    }

    suspend fun setSessionLimit(minutes: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_SESSION_LIMIT_MINUTES] = minutes
        }
    }

    suspend fun setBypassDuration(seconds: Int) {
        context.dataStore.edit { preferences ->
            preferences[KEY_BYPASS_DURATION_SECONDS] = seconds
        }
    }

    /**
     * Sets the pending general service disable flag (anti-bypass).
     */
    suspend fun setDisabledPending(pending: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_IS_DISABLED_PENDING] = pending
        }
    }

    /**
     * Adds an app package to the pending disable queue (reboot constraint).
     */
    suspend fun addPendingDisablePackage(packageName: String) {
        context.dataStore.edit { preferences ->
            val csv = preferences[KEY_PENDING_DISABLED_PACKAGES] ?: ""
            val current = if (csv.isEmpty()) mutableSetOf() else csv.split(",").toMutableSet()
            current.add(packageName)
            preferences[KEY_PENDING_DISABLED_PACKAGES] = current.joinToString(",")
        }
    }

    /**
     * Removes an app package from the pending disable queue.
     */
    suspend fun removePendingDisablePackage(packageName: String) {
        context.dataStore.edit { preferences ->
            val csv = preferences[KEY_PENDING_DISABLED_PACKAGES] ?: ""
            val current = if (csv.isEmpty()) mutableSetOf() else csv.split(",").toMutableSet()
            current.remove(packageName)
            preferences[KEY_PENDING_DISABLED_PACKAGES] = current.joinToString(",")
        }
    }

    /**
     * Resets all pending boot disable flags and syncs them.
     */
    suspend fun clearAllPendingDisables() {
        context.dataStore.edit { preferences ->
            preferences.remove(KEY_IS_DISABLED_PENDING)
            preferences.remove(KEY_PENDING_DISABLED_PACKAGES)
        }
    }

    suspend fun setStrictAppInfoBlockEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_STRICT_APP_INFO_BLOCK] = enabled
        }
    }

    suspend fun setAggressivePipProtectionEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AGGRESSIVE_PIP_PROTECTION] = enabled
        }
    }

    suspend fun setAutoDismissOverlayEnabled(enabled: Boolean) {
        context.dataStore.edit { preferences ->
            preferences[KEY_AUTO_DISMISS_OVERLAY] = enabled
        }
    }
}
