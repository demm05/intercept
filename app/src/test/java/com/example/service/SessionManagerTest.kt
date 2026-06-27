package com.example.service

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class SessionManagerTest {

    private lateinit var sessionManager: SessionManager
    private val testDispatcher = UnconfinedTestDispatcher()
    private val testScope = TestScope(testDispatcher)
    private var expiredPackages = mutableListOf<String>()
    
    private var currentTime = 0L

    @Before
    fun setup() {
        expiredPackages.clear()
        currentTime = 10000L // Start at some arbitrary time
        sessionManager = SessionManager(
            scope = testScope,
            timeProvider = { currentTime },
            onSessionExpired = { packageName ->
                expiredPackages.add(packageName)
            }
        )
    }

    @After
    fun tearDown() {
        sessionManager.clearAll()
    }

    @Test
    fun testUnlockApp_AllowsAccessWithinTimeLimit() = testScope.runTest {
        val packageName = "com.test.app"
        
        // Unlock for 5 minutes
        sessionManager.unlockApp(packageName, 5)

        // Immediately after unlocking, it should be true
        assertTrue("App should be unlocked immediately", sessionManager.isAppUnlocked(packageName))

        // Advance time by 4 minutes (4 * 60 * 1000 = 240,000 ms)
        currentTime += 240_000L
        assertTrue("App should still be unlocked before 5 minutes", sessionManager.isAppUnlocked(packageName))
        
        sessionManager.clearAll()
    }

    @Test
    fun testUnlockApp_BlocksAccessAfterTimeLimit() = testScope.runTest {
        val packageName = "com.test.app"
        
        // Unlock for 5 minutes
        sessionManager.unlockApp(packageName, 5)

        // Advance time by 6 minutes
        currentTime += 6 * 60 * 1000L
        
        // App should be locked now
        assertFalse("App should be locked after 6 minutes", sessionManager.isAppUnlocked(packageName))
        
        sessionManager.clearAll()
    }
    
    @Test
    fun testTransientCooldown_AppliesCorrectly() = testScope.runTest {
        val packageName = "com.test.app"
        
        // Apply 60s cooldown
        sessionManager.applyTransientCooldown(packageName, 60)
        
        assertTrue("App should be unlocked during cooldown", sessionManager.isAppUnlocked(packageName))
        
        // Advance by 61 seconds
        currentTime += 61_000L
        
        assertFalse("App should be locked after cooldown expires", sessionManager.isAppUnlocked(packageName))
        
        sessionManager.clearAll()
    }

    @Test
    fun testSessionMonitor_FiresCallbackOnExpiry() = testScope.runTest {
        val packageName = "com.test.app"
        
        // Unlock for 1 minute
        sessionManager.unlockApp(packageName, 1)
        
        // Coroutine loop checks every 1000ms real time. 
        // We advance the coroutine virtual time by 61 seconds.
        // We also must advance our simulated currentTime so the check triggers.
        currentTime += 61_000L
        advanceTimeBy(61_000L) 
        
        assertTrue("Expired package should be in the callback list", expiredPackages.contains(packageName))
        
        sessionManager.clearAll()
    }

    @Test
    fun testClearAll_ResetsState() {
        val packageName = "com.test.app"
        sessionManager.unlockApp(packageName, 5)
        
        sessionManager.clearAll()
        
        assertFalse("App should be locked after clearAll()", sessionManager.isAppUnlocked(packageName))
    }
}
