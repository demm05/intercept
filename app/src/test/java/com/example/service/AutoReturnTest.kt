package com.example.service

import android.content.Context
import android.content.Intent
import com.example.MainActivity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class AutoReturnTest {

    @Test
    fun testDeviceAdmin_OnEnabled_LaunchesMainActivity() {
        val context = RuntimeEnvironment.getApplication()
        val receiver = MyDeviceAdminReceiver()
        
        val intent = Intent("android.app.action.DEVICE_ADMIN_ENABLED")
        
        // Trigger onEnabled manually
        receiver.onReceive(context, intent)
        
        val shadowContext = shadowOf(context)
        val startedIntent = shadowContext.nextStartedActivity
        
        assertNotNull("Expected an activity to be started", startedIntent)
        assertEquals(MainActivity::class.java.name, startedIntent.component?.className)
        
        val expectedFlags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        assertTrue("Intent should have NEW_TASK and CLEAR_TOP flags", (startedIntent.flags and expectedFlags) == expectedFlags)
    }
}
