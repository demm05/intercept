package com.example.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityService.GLOBAL_ACTION_HOME
import android.content.Context
import android.graphics.PixelFormat
import android.os.Bundle
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.ui.theme.MyApplicationTheme
import com.example.utils.FocusLogger
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Handles injecting and removing the Compose overlay from the WindowManager.
 */
class OverlayManager(
    private val service: AccessibilityService,
    private val scope: CoroutineScope
) {
    private val windowManager = service.getSystemService(Context.WINDOW_SERVICE) as WindowManager?
    
    private var currentOverlayView: View? = null
    private var currentLifecycleOwner: MyLifecycleOwner? = null
    private var overlayDeploymentPending = false

    fun isShowing(): Boolean = currentOverlayView != null || overlayDeploymentPending

    fun deployOverlay(
        packageName: String,
        countdownDurationSeconds: Int,
        onSessionGranted: (Int) -> Unit
    ): Boolean {
        if (currentOverlayView != null || overlayDeploymentPending) return false
        overlayDeploymentPending = true

        scope.launch(Dispatchers.Main) {
            val lifecycleOwner = MyLifecycleOwner()
            
            val container = object : FrameLayout(service) {
                override fun dispatchKeyEvent(event: KeyEvent): Boolean {
                    if (event.keyCode == KeyEvent.KEYCODE_BACK && event.action == KeyEvent.ACTION_UP) {
                        FocusLogger.d("OverlayManager", "Back button intercepted in overlay.")
                        service.performGlobalAction(GLOBAL_ACTION_HOME)
                        removeOverlay()
                        return true
                    }
                    return super.dispatchKeyEvent(event)
                }
            }

            container.setViewTreeLifecycleOwner(lifecycleOwner)
            container.setViewTreeViewModelStoreOwner(lifecycleOwner)
            container.setViewTreeSavedStateRegistryOwner(lifecycleOwner)

            val composeView = ComposeView(service).apply {
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
                            onSessionGranted(limitMinutes)
                            removeOverlay()
                        },
                        onGoHome = {
                            service.performGlobalAction(GLOBAL_ACTION_HOME)
                            removeOverlay()
                        }
                    )
                }
            }

            val params = WindowManager.LayoutParams().apply {
                width = WindowManager.LayoutParams.MATCH_PARENT
                height = WindowManager.LayoutParams.MATCH_PARENT
                type = if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                    WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY
                } else {
                    @Suppress("DEPRECATION")
                    WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
                }
                format = PixelFormat.TRANSLUCENT
                flags = (WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        or WindowManager.LayoutParams.FLAG_FULLSCREEN
                        or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL)
                gravity = Gravity.CENTER
            }

            try {
                windowManager?.addView(container, params)
                FocusLogger.d("OverlayManager", "Mindfulness overlay deployed successfully.")
            } catch (e: Exception) {
                FocusLogger.e("OverlayManager", "Error injecting overlay window", e)
                currentOverlayView = null
                currentLifecycleOwner = null
                lifecycleOwner.destroy()
            }
            overlayDeploymentPending = false
        }
        return true
    }

    fun removeOverlay() {
        scope.launch(Dispatchers.Main) {
            val view = currentOverlayView ?: return@launch
            val lifecycle = currentLifecycleOwner
            currentOverlayView = null
            currentLifecycleOwner = null

            try {
                windowManager?.removeView(view)
                FocusLogger.d("OverlayManager", "Overlay removed successfully.")
            } catch (e: Exception) {
                FocusLogger.e("OverlayManager", "Error releasing overlay window", e)
            } finally {
                lifecycle?.destroy()
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
        controller.performRestore(Bundle())
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
