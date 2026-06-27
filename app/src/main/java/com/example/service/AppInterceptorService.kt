package com.example.service

import android.accessibilityservice.AccessibilityService
import android.app.KeyguardManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.PowerManager
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.compose.animation.core.*
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.*
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.MainActivity
import com.example.data.DataStoreRepository
import com.example.ui.theme.MyApplicationTheme
import com.example.utils.FocusLogger
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
    private var currentOverlayView: android.view.View? = null
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
                FocusLogger.d("AppInterceptorService", "Screen off detected. Removing overlay.")
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
        
        FocusLogger.d("PausePoint", "AppInterceptorService created.")
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        serviceConnectTime = System.currentTimeMillis()
        instance = this
        FocusLogger.d("PausePoint", "AccessibilityService connected successfully. Initializing configurations...")

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
                FocusLogger.d("PausePoint", "Service active status cache loaded/updated: $active")
            }
        }

        serviceScope.launch(Dispatchers.IO) {
            repository.targetedPackages.collect { packages ->
                // Swap local volatile reference to avoid concurrent modification issues
                val updatedSet = HashSet(packages)
                cachedTargetedPackages = updatedSet
                FocusLogger.d("PausePoint", "Targeted packages cache loaded/updated. Package count: ${updatedSet.size}. Targets: $updatedSet")
            }
        }

        serviceScope.launch(Dispatchers.IO) {
            repository.countdownDurationSeconds.collect { secs ->
                countdownDurationSeconds = secs
                FocusLogger.d("PausePoint", "Countdown duration cache loaded/updated: $secs seconds")
            }
        }

        serviceScope.launch(Dispatchers.IO) {
            repository.sessionLimitMinutes.collect { mins ->
                sessionLimitMinutes = mins
                FocusLogger.d("PausePoint", "Session limit cache loaded/updated: $mins minutes")
            }
        }

        serviceScope.launch(Dispatchers.IO) {
            repository.bypassDurationSeconds.collect { secs ->
                bypassDurationSeconds = secs
                FocusLogger.d("PausePoint", "Bypass duration cache loaded/updated: $secs seconds")
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
                    FocusLogger.w("AppInterceptorService", "Blocked access to Device Admin deactivation.")
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
                        FocusLogger.w("AppInterceptorService", "Defensive protection triggered for settings bypass attempt.")
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
                    FocusLogger.d("AppInterceptorService", "Ignoring event for $packageName as it is no longer in foreground.")
                    return
                }

                val lastUnlock = unlockedApps[packageName] ?: 0L
                val bypassDurationMs = bypassDurationSeconds * 1000L
                val elapsed = System.currentTimeMillis() - lastUnlock

                if (elapsed > bypassDurationMs) {
                    // Deploy our mindfulness overlay directly on top of the targeted app.
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
                    FocusLogger.i("AppInterceptorService", "Session limit reached for $packageName. Kicking to home.")
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
            
            val container = object : android.widget.FrameLayout(this@AppInterceptorService) {
                override fun dispatchKeyEvent(event: android.view.KeyEvent): Boolean {
                    if (event.keyCode == android.view.KeyEvent.KEYCODE_BACK && event.action == android.view.KeyEvent.ACTION_UP) {
                        FocusLogger.d("AppInterceptorService", "Back button intercepted in overlay.")
                        performGlobalAction(GLOBAL_ACTION_HOME)
                        removeOverlay()
                        return true
                    }
                    return super.dispatchKeyEvent(event)
                }
            }

            container.setViewTreeLifecycleOwner(lifecycleOwner)
            container.setViewTreeViewModelStoreOwner(lifecycleOwner)
            container.setViewTreeSavedStateRegistryOwner(lifecycleOwner)

            val composeView = ComposeView(this@AppInterceptorService).apply {
                // Also set them here just in case
                setViewTreeLifecycleOwner(lifecycleOwner)
                setViewTreeViewModelStoreOwner(lifecycleOwner)
                setViewTreeSavedStateRegistryOwner(lifecycleOwner)
            }
            
            container.addView(composeView)

            currentOverlayView = container
            currentLifecycleOwner = lifecycleOwner

            composeView.setContent {
                MyApplicationTheme {
                    CountdownOverlayContent(
                        packageName = packageName,
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
                        or WindowManager.LayoutParams.FLAG_FULLSCREEN) // Removed FLAG_NOT_FOCUSABLE
                gravity = android.view.Gravity.CENTER
            }

            try {
                windowManager?.addView(container, params)
                FocusLogger.d("AppInterceptorService", "Mindfulness overlay deployed successfully.")
            } catch (e: Exception) {
                FocusLogger.e("AppInterceptorService", "Error injecting overlay window", e)
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
                FocusLogger.d("AppInterceptorService", "Overlay removed successfully.")
            } catch (e: Exception) {
                FocusLogger.e("AppInterceptorService", "Error releasing overlay window", e)
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
    packageName: String,
    initialSeconds: Int,
    onFinished: (Int) -> Unit,
    onGoHome: () -> Unit
) {
    var secondsLeft by remember { mutableIntStateOf(initialSeconds) }
    var showTimeSelection by remember { mutableStateOf(false) }

    val context = androidx.compose.ui.platform.LocalContext.current
    val pm = context.packageManager
    
    val appInfo = remember(packageName) {
        try {
            pm.getApplicationInfo(packageName, 0)
        } catch (e: Exception) {
            null
        }
    }
    
    val appLabel = remember(appInfo) {
        appInfo?.loadLabel(pm)?.toString() ?: packageName
    }
    
    val appIcon = remember(appInfo) {
        try {
            val drawable = appInfo?.loadIcon(pm)
            drawable?.let { 
                val bitmap = android.graphics.Bitmap.createBitmap(
                    it.intrinsicWidth.coerceAtLeast(1), 
                    it.intrinsicHeight.coerceAtLeast(1), 
                    android.graphics.Bitmap.Config.ARGB_8888
                )
                val canvas = android.graphics.Canvas(bitmap)
                it.setBounds(0, 0, canvas.width, canvas.height)
                it.draw(canvas)
                bitmap.asImageBitmap()
            }
        } catch (e: Exception) {
            null
        }
    }

    LaunchedEffect(Unit) {
        while (secondsLeft > 0) {
            delay(1000L)
            secondsLeft--
        }
        showTimeSelection = true
    }

    // Breathing animation for the ring
    val infiniteTransition = rememberInfiniteTransition(label = "breathing")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.4f,
        animationSpec = infiniteRepeatable(
            animation = tween(1800, easing = LinearOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "breathing_scale"
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = 0.98f)), // Near solid black
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(24.dp)
        ) {
            if (!showTimeSelection) {
                // App Context Header
                if (appIcon != null) {
                    Image(
                        bitmap = appIcon,
                        contentDescription = null,
                        modifier = Modifier
                            .size(64.dp)
                            .clip(RoundedCornerShape(16.dp))
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                
                Text(
                    text = "Opening $appLabel",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Medium,
                    color = Color(0xFFE6E1E5).copy(alpha = 0.8f)
                )
                Text(
                    text = "Take a mindful breath",
                    fontSize = 14.sp,
                    color = Color(0xFFE6E1E5).copy(alpha = 0.6f)
                )

                Spacer(modifier = Modifier.height(64.dp))

                // Breathing Ring & Timer
                Box(contentAlignment = Alignment.Center) {
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .scale(scale)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color(0xFFD0BCFF).copy(alpha = 0.1f))
                    )
                    Box(
                        modifier = Modifier
                            .size(160.dp)
                            .clip(androidx.compose.foundation.shape.CircleShape)
                            .background(Color(0xFFD0BCFF).copy(alpha = 0.05f))
                    )
                    Text(
                        text = secondsLeft.toString(),
                        fontSize = 80.sp,
                        fontWeight = FontWeight.Light,
                        fontFamily = FontFamily.Monospace,
                        color = Color(0xFFD0BCFF)
                    )
                }

                Spacer(modifier = Modifier.height(80.dp))

                OutlinedButton(
                    onClick = onGoHome,
                    colors = ButtonDefaults.outlinedButtonColors(
                        contentColor = Color(0xFFE6E1E5)
                    ),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFE6E1E5).copy(alpha = 0.3f)),
                    shape = RoundedCornerShape(50)
                ) {
                    Text(
                        text = "Nevermind, go home",
                        fontWeight = FontWeight.Medium,
                        fontSize = 14.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
                    )
                }
            } else {
                // Post-Countdown Options
                if (appIcon != null) {
                    Image(
                        bitmap = appIcon,
                        contentDescription = null,
                        modifier = Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                }
                Text(
                    text = "How long do you need in $appLabel?",
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

                    Spacer(modifier = Modifier.height(24.dp))

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
