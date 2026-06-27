package com.example.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.util.Log
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import com.example.data.DataStoreRepository
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.*
import java.util.concurrent.ConcurrentHashMap

/**
 * AppInterceptorService is an AccessibilityService running in the background.
 * It features ultra-low-latency in-memory package lookup and leverages a lightweight
 * Compose system WindowManager overlay for countdowns.
 */
@Suppress("DEPRECATION")
class AppInterceptorService : AccessibilityService() {

    companion object {
        var instance: AppInterceptorService? = null
            private set
    }

    private val serviceJob = SupervisorJob()
    private val serviceScope = CoroutineScope(Dispatchers.Main + serviceJob)

    private var windowManager: WindowManager? = null
    private var currentOverlayView: ComposeView? = null
    private var currentLifecycleOwner: MyLifecycleOwner? = null

    // Volatile, in-memory caches for ultra-low latency lookups without allocations
    @Volatile
    private var isServiceActive = true

    @Volatile
    private var cachedTargetedPackages = HashSet<String>()

    @Volatile
    private var countdownDurationSeconds = 10

    @Volatile
    private var sessionLimitMinutes = 0

    @Volatile
    private var bypassDurationSeconds = 60

    private var activeSessionPackage: String? = null
    private var sessionMonitorJob: Job? = null

    private var serviceConnectTime = 0L

    private lateinit var powerManager: PowerManager
    private lateinit var keyguardManager: KeyguardManager

    private val screenStateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            if (intent.action == Intent.ACTION_SCREEN_OFF) {
                Log.d("AppInterceptorService", "Screen off detected. Removing overlay.")
                removeOverlay()
            }
        }
    }

    // Bypass list with timestamps
    private val unlockedApps = ConcurrentHashMap<String, Long>()

    override fun onCreate() {
        super.onCreate()
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        keyguardManager = getSystemService(Context.KEYGUARD_SERVICE) as KeyguardManager
        
        val filter = IntentFilter(Intent.ACTION_SCREEN_OFF)
        registerReceiver(screenStateReceiver, filter)
        
        Log.d("PausePoint", "AppInterceptorService created.")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceConnectTime = System.currentTimeMillis()
        instance = this
        Log.d("PausePoint", "AccessibilityService connected successfully. Initializing configurations...")

        // Automatically return to the app after enabling the service
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        if (intent != null) {
            startActivity(intent)
        }

        val repository = DataStoreRepository(applicationContext)

        // Read once and keep updated asynchronously via background Coroutines
        serviceScope.launch(Dispatchers.IO) {
            repository.isServiceActive.collect { active ->
                isServiceActive = active
                Log.d("PausePoint", "Service active status cache loaded/updated: $active")
            }
        }

        serviceScope.launch(Dispatchers.IO) {
            repository.targetedPackages.collect { packages ->
                // Swap local volatile reference to avoid concurrent modification issues
                val updatedSet = HashSet(packages)
                cachedTargetedPackages = updatedSet
                Log.d("PausePoint", "Targeted packages cache loaded/updated. Package count: ${updatedSet.size}. Targets: $updatedSet")
            }
        }

        serviceScope.launch(Dispatchers.IO) {
            repository.countdownDurationSeconds.collect { secs ->
                countdownDurationSeconds = secs
                Log.d("PausePoint", "Countdown duration cache loaded/updated: $secs seconds")
            }
        }

        serviceScope.launch(Dispatchers.IO) {
            repository.sessionLimitMinutes.collect { mins ->
                sessionLimitMinutes = mins
                Log.d("PausePoint", "Session limit cache loaded/updated: $mins minutes")
            }
        }

        serviceScope.launch(Dispatchers.IO) {
            repository.bypassDurationSeconds.collect { secs ->
                bypassDurationSeconds = secs
                Log.d("PausePoint", "Bypass duration cache loaded/updated: $secs seconds")
            }
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        // Zero-allocation, fast lookups in the hot path
        if (!isServiceActive) return

        // New Safety Check: Don't trigger or show anything if the screen is off or locked
        if (!powerManager.isInteractive || keyguardManager.isKeyguardLocked) {
            return
        }

        if (event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            val packageNameSeq = event.packageName ?: return
            val packageName = packageNameSeq.toString()
            if (packageName.isEmpty()) return

            // 1. Anti-Bypass Security Check for Settings Menu
            if (packageName == "com.android.settings") {
                // Grace period: allow user to exit settings after enabling without immediate interception
                if (System.currentTimeMillis() - serviceConnectTime < 10_000L) {
                    return
                }

                val rootNode = rootInActiveWindow
                val appName = getString(com.example.R.string.app_name)
                val targetsOurApp = rootNode != null && scanNodesForText(rootNode, appName)
                val isDeviceAdminScreen = event.className?.toString()?.contains("DeviceAdmin") == true || 
                                          event.className?.toString()?.contains("DevicePolicyManager") == true
                
                rootNode?.recycle() // Always recycle immediately after scanning

                val adminComponent = android.content.ComponentName(this, MyDeviceAdminReceiver::class.java)
                val dpm = getSystemService(android.content.Context.DEVICE_POLICY_SERVICE) as android.app.admin.DevicePolicyManager
                val isAlreadyActive = dpm.isAdminActive(adminComponent)

                if (targetsOurApp && isDeviceAdminScreen && isAlreadyActive) {
                    Log.w("AppInterceptorService", "Blocked access to Device Admin deactivation.")
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    return
                }

                if (targetsOurApp && isDeviceAdminScreen && !isAlreadyActive) {
                    // User is trying to activate Device Admin. Do not trigger the overlay.
                    return
                }

                val lastUnlock = unlockedApps[packageName] ?: 0L
                val bypassDurationMs = bypassDurationSeconds * 1000L
                val elapsed = System.currentTimeMillis() - lastUnlock
                
                if (elapsed > bypassDurationMs) {
                    if (targetsOurApp) {
                        Log.w("AppInterceptorService", "Defensive protection triggered for settings bypass attempt.")
                        deployOverlay(packageName, launchAppOnUnlock = false)
                        return
                    }
                }
            }

            // 2. Standard Monitored Apps Check
            if (cachedTargetedPackages.contains(packageName)) {
                // Defensive check: Ensure the event package matches the current active window
                // This prevents false interceptions when closing apps or returning home.
                val activeWindowPackage = rootInActiveWindow?.packageName?.toString()
                if (activeWindowPackage != packageName) {
                    Log.d("AppInterceptorService", "Ignoring event for $packageName as it is no longer in foreground.")
                    return
                }

                val lastUnlock = unlockedApps[packageName] ?: 0L
                val bypassDurationMs = bypassDurationSeconds * 1000L
                val elapsed = System.currentTimeMillis() - lastUnlock

                if (elapsed > bypassDurationMs) {
                    // "Instant Minimize" Strategy:
                    // Send the user home immediately to stop the distracting app from loading.
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    
                    // Then deploy our mindfulness overlay on top of the home screen.
                    deployOverlay(packageName)
                } else {
                    // We don't have a specific limit for re-entry, 
                    // but we can assume no limit if it's within the grace period 
                    // unless we track the original limit. For now, just allow.
                    // startSessionMonitor(packageName, lastUnlock, 0)
                }
            }
        }
    }

    private fun startSessionMonitor(packageName: String, startTime: Long, limitMinutes: Int) {
        if (limitMinutes <= 0) return
        if (activeSessionPackage == packageName && sessionMonitorJob?.isActive == true) return

        activeSessionPackage = packageName
        sessionMonitorJob?.cancel()
        sessionMonitorJob = serviceScope.launch {
            val limitMs = limitMinutes * 60 * 1000L
            while (isActive) {
                val elapsed = System.currentTimeMillis() - startTime
                if (elapsed >= limitMs) {
                    Log.i("AppInterceptorService", "Session limit reached for $packageName. Kicking to home.")
                    unlockedApps.remove(packageName)
                    performGlobalAction(GLOBAL_ACTION_HOME)
                    break
                }
                delay(5000) // Check every 5 seconds
            }
        }
    }

    private fun deployOverlay(packageName: String, launchAppOnUnlock: Boolean = true) {
        serviceScope.launch(Dispatchers.Main) {
            if (currentOverlayView != null) return@launch // Already active

            // Trigger incrementing of the mindfulness counter
            serviceScope.launch(Dispatchers.IO) {
                DataStoreRepository(applicationContext).incrementInterceptionCount()
            }

            val lifecycleOwner = MyLifecycleOwner()
            val composeView = ComposeView(this@AppInterceptorService).apply {
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeViewModelStoreOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            }

            currentOverlayView = composeView
            currentLifecycleOwner = lifecycleOwner

            composeView.setContent {
                MyApplicationTheme {
                    CountdownOverlayContent(
                        initialSeconds = countdownDurationSeconds,
                        onFinished = { limitMinutes ->
                            unlockApp(packageName, limitMinutes)
                            removeOverlay()
                            
                            if (launchAppOnUnlock) {
                                // Once unlocked, launch the app for the user
                                val intent = packageManager.getLaunchIntentForPackage(packageName)
                                if (intent != null) {
                                    startActivity(intent)
                                }
                            }
                        },
                        onGoHome = {
                            performGlobalAction(GLOBAL_ACTION_HOME)
                            removeOverlay()
                        }
                    )
                }
            }

            // Window manager layout specs to lay view directly over current screen
            val params = WindowManager.LayoutParams().apply {
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.MATCH_PARENT
                type = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                }
                format = android.graphics.PixelFormat.TRANSLUCENT
                flags = (WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        or WindowManager.LayoutParams.FLAG_FULLSCREEN
                        or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE)
                gravity = android.view.Gravity.CENTER
            }

            try {
                windowManager?.addView(composeView, params)
                Log.d("AppInterceptorService", "Mindfulness overlay deployed successfully.")
            } catch (e: Exception) {
                Log.e("AppInterceptorService", "Error injecting overlay window", e)
            }
        }
    }

    private fun removeOverlay() {
        serviceScope.launch(Dispatchers.Main) {
            val view = currentOverlayView ?: return@launch
            val lifecycle = currentLifecycleOwner
            currentOverlayView = null
            currentLifecycleOwner = null

            try {
                windowManager?.removeView(view)
                Log.d("AppInterceptorService", "Overlay removed successfully.")
            } catch (e: Exception) {
                Log.e("AppInterceptorService", "Error releasing overlay window", e)
            } finally {
                lifecycle?.destroy()
            }
        }
    }

    private fun unlockApp(packageName: String, limitMinutes: Int) {
        val now = System.currentTimeMillis()
        unlockedApps[packageName] = now
        startSessionMonitor(packageName, now, limitMinutes)
    }

    /**
     * Recursively inspects the active Accessibility node tree to detect references to our app's name.
     */
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

    override fun onInterrupt() {}

    override fun onDestroy() {
        super.onDestroy()
        instance = null
        unregisterReceiver(screenStateReceiver)
        removeOverlay()
        serviceJob.cancel()
    }
}

/**
 * Simple, stark, dark countdown overlay Composable containing zero graphics or quotes.
 */
@Composable
fun CountdownOverlayContent(
    initialSeconds: Int,
    onFinished: (Int) -> Unit,
    onGoHome: () -> Unit
) {
    var secondsLeft by remember { mutableIntStateOf(initialSeconds) }
    var showTimeSelection by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft--
        }
        showTimeSelection = true
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF1C1B1F)),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            if (!showTimeSelection) {
                Text(
                    text = secondsLeft.toString(),
                    fontSize = 120.sp,
                    fontWeight = FontWeight.Light,
                    fontFamily = FontFamily.Monospace,
                    color = Color(0xFFD0BCFF)
                )

                Spacer(modifier = Modifier.height(32.dp))

                Button(
                    onClick = onGoHome,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF49454F),
                        contentColor = Color(0xFFE6E1E5)
                    ),
                    shape = RoundedCornerShape(100)
                ) {
                    Text(
                        text = "Go Home",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp
                    )
                }
            } else {
                Text(
                    text = "How long do you need?",
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE6E1E5),
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(32.dp))

                val options = listOf(
                    "1 min" to 1,
                    "5 min" to 5,
                    "15 min" to 15,
                    "30 min" to 30
                )

                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    options.forEach { (label, minutes) ->
                        FilledTonalButton(
                            onClick = { onFinished(minutes) },
                            modifier = Modifier
                                .fillMaxWidth(0.6f)
                                .height(56.dp),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.filledTonalButtonColors(
                                containerColor = Color(0xFF49454F),
                                contentColor = Color(0xFFD0BCFF)
                            )
                        ) {
                            Text(text = label, fontWeight = FontWeight.Bold)
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    TextButton(onClick = onGoHome) {
                        Text(
                            text = "Nevermind, go home",
                            color = Color(0xFFE6E1E5).copy(alpha = 0.6f)
                        )
                    }
                }
            }
        }
    }
}

/**
 * Custom lifecycle owner representing a view tree lifecycle for Compose rendering.
 */
class MyLifecycleOwner : LifecycleOwner, ViewModelStoreOwner, SavedStateRegistryOwner {
    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val controller = SavedStateRegistryController.create(this)

    init {
        controller.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
    }

    override val lifecycle: Lifecycle
        get() = lifecycleRegistry

    override val viewModelStore: ViewModelStore
        get() = store

    override val savedStateRegistry: SavedStateRegistry
        get() = controller.savedStateRegistry

    fun destroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
    }
}
