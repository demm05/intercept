package com.example.service

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.example.MainActivity
import com.example.data.DataStoreRepository
import com.example.utils.FocusLogger
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first

/**
 * The core AccessibilityService. Stripped down to only handle the event loop,
 * delegating UI to OverlayManager and time tracking to SessionManager.
 */
class AppInterceptorService : AccessibilityService() {

    companion object {
        var instance: AppInterceptorService? = null
            private set
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)
    private var serviceConnectTime = 0L

    private lateinit var repository: DataStoreRepository
    private lateinit var sessionManager: SessionManager
    private lateinit var overlayManager: OverlayManager
    private lateinit var powerManager: PowerManager
    private lateinit var keyguardManager: android.app.KeyguardManager

    private var cachedTargetedPackages: Set<String> = emptySet()
    private var isServiceActive: Boolean = true
    private var bypassDurationSeconds: Int = 60
    private var countdownDurationSeconds: Int = 10

    private var isStrictAppInfoBlockEnabled: Boolean = false
    private var isAggressivePipProtectionEnabled: Boolean = false
    private var isAutoDismissOverlayEnabled: Boolean = true

    private var currentBlockedPackage: String? = null

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                FocusLogger.d("AppInterceptorService", "Screen off detected. Removing overlay.")
                overlayManager.removeOverlay()
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        instance = this
        serviceConnectTime = System.currentTimeMillis()
        repository = DataStoreRepository(applicationContext)
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as android.app.KeyguardManager
        
        overlayManager = OverlayManager(this, serviceScope)
        sessionManager = SessionManager(serviceScope) { expiredPackage ->
            // Callback when a session expires
            performGlobalAction(GLOBAL_ACTION_HOME)
            FocusLogger.i("AppInterceptorService", "Kicked $expiredPackage to Home due to session expiry.")
        }

        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenStateReceiver, filter)
        FocusLogger.d("AppInterceptorService", "AppInterceptorService created.")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        FocusLogger.d("AppInterceptorService", "AccessibilityService connected successfully. Initializing configurations...")

        // Auto-return to the app after onboarding permission is granted
        val intent = Intent(this, MainActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        }
        startActivity(intent)

        serviceScope.launch {
            val initialSet = repository.targetedPackages.first()
            cachedTargetedPackages = initialSet

            repository.targetedPackages.collect { updatedSet ->
                cachedTargetedPackages = updatedSet
                FocusLogger.d("AppInterceptorService", "Targeted packages cache updated. Count: ${updatedSet.size}")
            }
        }

        serviceScope.launch {
            repository.isServiceActive.collect { active ->
                isServiceActive = active
            }
        }

        serviceScope.launch {
            repository.countdownDurationSeconds.collect { secs ->
                countdownDurationSeconds = secs
            }
        }

        serviceScope.launch(Dispatchers.IO) {
            repository.bypassDurationSeconds.collect { secs ->
                bypassDurationSeconds = secs
            }
        }

        serviceScope.launch {
            repository.isStrictAppInfoBlockEnabled.collect { enabled ->
                isStrictAppInfoBlockEnabled = enabled
            }
        }

        serviceScope.launch {
            repository.isAggressivePipProtectionEnabled.collect { enabled ->
                isAggressivePipProtectionEnabled = enabled
            }
        }

        serviceScope.launch {
            repository.isAutoDismissOverlayEnabled.collect { enabled ->
                isAutoDismissOverlayEnabled = enabled
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        if (!isServiceActive) return
        if (!powerManager.isInteractive || keyguardManager.isKeyguardLocked) return

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageNameSeq = event.packageName ?: return
            val packageName = packageNameSeq.toString()
            if (packageName.isEmpty()) return

            // 1. Anti-Bypass Security Check for Settings Menu
            if (packageName == "com.android.settings") {
                if (System.currentTimeMillis() - serviceConnectTime < 10_000L) return

                val rootNode = rootInActiveWindow
                val appName = getString(com.example.R.string.app_name)
                val targetsOurApp = rootNode != null && scanNodesForText(rootNode, appName)
                val isDeviceAdminScreen = event.className?.toString()?.contains("DeviceAdmin") == true || 
                                          event.className?.toString()?.contains("DevicePolicyManager") == true
                
                rootNode?.recycle()

                val adminComponent = android.content.ComponentName(this, com.example.service.MyDeviceAdminReceiver::class.java)
                val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
                val isAlreadyActive = dpm.isAdminActive(adminComponent)

                if (targetsOurApp && isDeviceAdminScreen && isAlreadyActive) {
                    FocusLogger.w("AppInterceptorService", "Blocked access to Device Admin deactivation.")
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    return
                }

                if (targetsOurApp && isDeviceAdminScreen && !isAlreadyActive) return // Allowed to activate

                // Strict App Info Block
                if (targetsOurApp && isStrictAppInfoBlockEnabled) {
                    val className = event.className?.toString() ?: ""
                    val isAppInfoScreen = className.contains("AppInfo", ignoreCase = true) || 
                                          className.contains("InstalledAppDetails", ignoreCase = true)
                    
                    if (isAppInfoScreen) {
                        FocusLogger.w("AppInterceptorService", "Strict App Info Block triggered. Kicking to Home.")
                        performGlobalAction(GLOBAL_ACTION_HOME)
                        return
                    }
                    
                    // Fallback to checking node IDs in case OEMs use different class names
                    val rootNow = rootInActiveWindow
                    val hasForceStopId = scanNodesForForceStop(rootNow)
                    rootNow?.recycle()
                    if (hasForceStopId) {
                        FocusLogger.w("AppInterceptorService", "Strict App Info Block triggered. Kicking to Home.")
                        performGlobalAction(GLOBAL_ACTION_HOME)
                        return
                    }
                }

                if (targetsOurApp) {
                    if (!sessionManager.isAppUnlocked(packageName)) {
                        FocusLogger.w("AppInterceptorService", "Defensive protection triggered for settings.")
                        deployOverlay(packageName)
                    }
                }
                return
            }

            // 2. Auto-Dismiss Overlay
            if (isAutoDismissOverlayEnabled && overlayManager.isShowing()) {
                val blockedPkg = currentBlockedPackage
                if (blockedPkg != null && !isAppVisibleOnScreen(blockedPkg)) {
                    FocusLogger.d("AppInterceptorService", "Blocked app no longer visible. Auto-dismissing overlay.")
                    overlayManager.removeOverlay()
                    currentBlockedPackage = null
                }
            }

            // 3. Aggressive PiP Protection Check
            if (isAggressivePipProtectionEnabled) {
                for (targetPkg in cachedTargetedPackages) {
                    if (isAppVisibleOnScreen(targetPkg)) {
                        if (!sessionManager.isAppUnlocked(targetPkg)) {
                            // Force PiP to full screen BEFORE overlay
                            if (rootInActiveWindow?.packageName?.toString() != targetPkg) {
                                val intent = packageManager.getLaunchIntentForPackage(targetPkg)
                                if (intent != null) {
                                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                    startActivity(intent)
                                }
                            }
                            deployOverlay(targetPkg)
                            return
                        }
                    }
                }
            }

            // 4. Standard Monitored Apps Check
            if (cachedTargetedPackages.contains(packageName)) {
                if (!isAppVisibleOnScreen(packageName)) return

                if (sessionManager.isAppUnlocked(packageName)) return

                deployOverlay(packageName)
            }
        }
    }

    private fun isAppVisibleOnScreen(packageName: String): Boolean {
        // Quick check
        if (rootInActiveWindow?.packageName?.toString() == packageName) return true

        // Detailed check across all windows to catch PiP (Picture in Picture) or split screen
        try {
            val windowList = windows
            for (window in windowList) {
                if (window.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_APPLICATION) {
                    if (window.root?.packageName?.toString() == packageName) {
                        return true
                    }
                }
            }
        } catch (e: Exception) {
            FocusLogger.e("AppInterceptorService", "Error inspecting windows", e)
        }
        return false
    }

    private fun deployOverlay(packageName: String) {
        val deployed = overlayManager.deployOverlay(
            packageName = packageName,
            countdownDurationSeconds = countdownDurationSeconds,
            onSessionGranted = { limitMinutes ->
                sessionManager.unlockApp(packageName, limitMinutes)
                // We removed startActivity(intent) here to fix the "Thrown Somewhere Else" UX bug.
            }
        )
        if (deployed) {
            currentBlockedPackage = packageName
            serviceScope.launch(Dispatchers.IO) {
                repository.incrementInterceptionCount()
            }
        }
    }

    private fun scanNodesForText(node: AccessibilityNodeInfo?, textToFind: String): Boolean {
        if (node == null) return false
        val nodeText = node.text?.toString() ?: ""
        val nodeContentDesc = node.contentDescription?.toString() ?: ""
        if (nodeText.contains(textToFind, ignoreCase = true) || nodeContentDesc.contains(textToFind, ignoreCase = true)) {
            return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (scanNodesForText(child, textToFind)) {
                child?.recycle()
                return true
            }
            child?.recycle()
        }
        return false
    }

    private fun scanNodesForForceStop(node: AccessibilityNodeInfo?): Boolean {
        if (node == null) return false
        val viewId = node.viewIdResourceName ?: ""
        if (viewId.endsWith("id/force_stop_button", ignoreCase = true) || viewId.endsWith("id/right_button", ignoreCase = true)) {
            return true
        }
        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (scanNodesForForceStop(child)) {
                child?.recycle()
                return true
            }
            child?.recycle()
        }
        return false
    }

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        unregisterReceiver(screenStateReceiver)
        overlayManager.removeOverlay()
        sessionManager.clearAll()
        serviceJob.cancel()
    }
}
