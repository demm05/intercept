package com.example.data

import android.content.Context
import androidx.test.core.app.ApplicationProvider

import androidx.datastore.preferences.core.edit
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class DataStoreRepositoryTest {

    private lateinit var context: Context
    private lateinit var repository: DataStoreRepository

    @Before
    fun setup() {
        context = ApplicationProvider.getApplicationContext()
        // Clear DataStore between tests to avoid state bleeding
        runBlocking {
            context.dataStore.edit { it.clear() }
        }
        repository = DataStoreRepository(context)
    }

    @Test
    fun `test initial default values`() = runTest {
        assertTrue(repository.isServiceActive.first())
        assertEquals(0, repository.interceptionCount.first())
        assertEquals(10, repository.countdownDurationSeconds.first())
        assertFalse(repository.isDisabledPending.first())
        assertFalse(repository.isAutoDismissOverlayEnabled.first())
        assertFalse(repository.isStrictAppInfoBlockEnabled.first())
        assertFalse(repository.isAggressivePipProtectionEnabled.first())
        assertTrue(repository.targetedPackages.first().isEmpty())
    }

    @Test
    fun `test setting service active flag`() = runTest {
        repository.setServiceActive(false)
        assertFalse(repository.isServiceActive.first())

        repository.setServiceActive(true)
        assertTrue(repository.isServiceActive.first())
    }

    @Test
    fun `test advanced toggles`() = runTest {
        repository.setStrictAppInfoBlockEnabled(true)
        assertTrue(repository.isStrictAppInfoBlockEnabled.first())

        repository.setAggressivePipProtectionEnabled(true)
        assertTrue(repository.isAggressivePipProtectionEnabled.first())

        repository.setAutoDismissOverlayEnabled(true)
        assertTrue(repository.isAutoDismissOverlayEnabled.first())
    }

    @Test
    fun `test targeted packages additions and removals`() = runTest {
        repository.addTargetedPackage("com.test.app")
        assertTrue(repository.targetedPackages.first().contains("com.test.app"))

        repository.removeTargetedPackage("com.test.app")
        assertFalse(repository.targetedPackages.first().contains("com.test.app"))
    }
}
