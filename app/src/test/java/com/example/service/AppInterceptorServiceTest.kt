package com.example.service

import android.view.accessibility.AccessibilityNodeInfo
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.Shadows.shadowOf

@RunWith(RobolectricTestRunner::class)
class AppInterceptorServiceTest {

    @Test
    fun `scanNodesForForceStop detects force stop view id`() {
        val service = AppInterceptorService()
        
        // Use reflection to access the private method
        val scanMethod = AppInterceptorService::class.java.getDeclaredMethod("scanNodesForForceStop", AccessibilityNodeInfo::class.java)
        scanMethod.isAccessible = true
        
        // Create an AccessibilityNodeInfo with the force stop id
        val forceStopNode = AccessibilityNodeInfo.obtain()
        forceStopNode.viewIdResourceName = "com.android.settings:id/force_stop_button"
        
        val result = scanMethod.invoke(service, forceStopNode) as Boolean
        assertTrue(result)
        
        forceStopNode.recycle()
    }

    @Test
    fun `scanNodesForForceStop ignores generic ids`() {
        val service = AppInterceptorService()
        
        val scanMethod = AppInterceptorService::class.java.getDeclaredMethod("scanNodesForForceStop", AccessibilityNodeInfo::class.java)
        scanMethod.isAccessible = true
        
        val genericNode = AccessibilityNodeInfo.obtain()
        genericNode.viewIdResourceName = "com.android.settings:id/some_generic_button"
        
        val result = scanMethod.invoke(service, genericNode) as Boolean
        assertFalse(result)
        
        genericNode.recycle()
    }

    @Test
    fun `scanNodesForForceStop detects nested child id`() {
        val service = AppInterceptorService()
        
        val scanMethod = AppInterceptorService::class.java.getDeclaredMethod("scanNodesForForceStop", AccessibilityNodeInfo::class.java)
        scanMethod.isAccessible = true
        
        val rootNode = AccessibilityNodeInfo.obtain()
        val childNode = AccessibilityNodeInfo.obtain()
        val grandChildNode = AccessibilityNodeInfo.obtain()
        
        grandChildNode.viewIdResourceName = "com.android.settings:id/force_stop_button"
        
        shadowOf(childNode).addChild(grandChildNode)
        shadowOf(rootNode).addChild(childNode)
        
        val result = scanMethod.invoke(service, rootNode) as Boolean
        assertTrue(result)
        
        rootNode.recycle()
        // Note: child nodes are recycled by the garbage collector or internally. 
    }

}
