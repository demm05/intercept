package com.example.ui.dashboard

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import com.example.data.DataStoreRepository
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.setMain
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@OptIn(ExperimentalCoroutinesApi::class)
@RunWith(RobolectricTestRunner::class)
class DashboardViewModelTest {

    private lateinit var application: Application
    private lateinit var repository: DataStoreRepository
    private lateinit var viewModel: DashboardViewModel
    

    private val testDispatcher = StandardTestDispatcher()

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)
        application = ApplicationProvider.getApplicationContext()
        File(application.filesDir, "datastore").deleteRecursively()
        repository = DataStoreRepository(application)
        viewModel = DashboardViewModel(application, repository)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
    }

    @Test
    fun `test initial states map from repository correctly`() = runTest {
        testDispatcher.scheduler.advanceUntilIdle()
        
        assertTrue(viewModel.isServiceActive.value)
        assertEquals(10, viewModel.countdownDurationSeconds.value)
        assertFalse(viewModel.isAutoDismissOverlayEnabled.value)
        assertFalse(viewModel.isStrictAppInfoBlockEnabled.value)
    }

    @Test
    fun `test setServiceActive updates repository and handles anti bypass`() = runTest {
        viewModel.setServiceActive(false)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Anti-bypass kicks in when turning off
        assertTrue(repository.isDisabledPending.first { it })
        
        viewModel.setServiceActive(true)
        testDispatcher.scheduler.advanceUntilIdle()
        
        // Turns back on immediately and clears pending
        assertTrue(repository.isServiceActive.first { it })
        assertFalse(repository.isDisabledPending.first { !it })
    }

    @Test
    fun `test search query updates`() {
        viewModel.updateSearchQuery("youtube")
        assertEquals("youtube", viewModel.searchQuery.value)
    }

    @Test
    fun `test target package checks and anti bypass rules`() = runTest {
        // Check true
        viewModel.setTargetPackageChecked("com.twitter.android", true)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(repository.targetedPackages.first { it.contains("com.twitter.android") }.isNotEmpty())
        
        // Unchecking forces a pending reboot rule
        viewModel.setTargetPackageChecked("com.twitter.android", false)
        testDispatcher.scheduler.advanceUntilIdle()
        assertTrue(repository.pendingDisabledPackages.first { it.contains("com.twitter.android") }.isNotEmpty())
    }
}
