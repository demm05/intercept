package com.example.ui.dashboard

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.DataStoreRepository
import com.example.model.InstalledAppItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DashboardViewModel(application: Application) : AndroidViewModel(application) {

    private val repository = DataStoreRepository(application)

    val isServiceActive = repository.isServiceActive.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val targetedPackages = repository.targetedPackages.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    val interceptionCount = repository.interceptionCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val countdownDurationSeconds = repository.countdownDurationSeconds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 10)
    val bypassDurationSeconds = repository.bypassDurationSeconds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 60)
    val isDisabledPending = repository.isDisabledPending.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val pendingDisabledPackages = repository.pendingDisabledPackages.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    private val _installedApps = MutableStateFlow<List<InstalledAppItem>>(emptyList())
    val installedApps: StateFlow<List<InstalledAppItem>> = _installedApps.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _isLoadingApps = MutableStateFlow(true)
    val isLoadingApps: StateFlow<Boolean> = _isLoadingApps.asStateFlow()

    init {
        loadInstalledApps()
    }

    private fun loadInstalledApps() {
        viewModelScope.launch(Dispatchers.IO) {
            _isLoadingApps.value = true
            try {
                val context = getApplication<Application>()
                val pm = context.packageManager
                val intent = Intent(Intent.ACTION_MAIN, null).apply {
                    addCategory(Intent.CATEGORY_LAUNCHER)
                }
                val list = pm.queryIntentActivities(intent, 0)
                val items = list.mapNotNull { resolveInfo ->
                    runCatching {
                        val appInfo = resolveInfo.activityInfo.applicationInfo
                        val packageName = appInfo.packageName
                        if (packageName == context.packageName) return@mapNotNull null
                        val label = resolveInfo.loadLabel(pm).toString()
                        val icon = resolveInfo.loadIcon(pm).toBitmap(width = 100, height = 100)
                        val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                        InstalledAppItem(packageName, label, icon, isSystem)
                    }.getOrNull()
                }.distinctBy { it.packageName }
                 .sortedBy { it.appLabel.lowercase() }

                _installedApps.value = items
            } finally {
                _isLoadingApps.value = false
            }
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun setServiceActive(active: Boolean) {
        viewModelScope.launch {
            if (!active) {
                // Anti-bypass reboot constraint
                repository.setDisabledPending(true)
            } else {
                repository.setDisabledPending(false)
                repository.setServiceActive(true)
            }
        }
    }

    fun setCountdownDuration(seconds: Int) {
        viewModelScope.launch { repository.setCountdownDuration(seconds) }
    }

    fun setBypassDuration(seconds: Int) {
        viewModelScope.launch { repository.setBypassDuration(seconds) }
    }

    fun setTargetPackageChecked(packageName: String, shouldBeChecked: Boolean) {
        viewModelScope.launch {
            if (!shouldBeChecked) {
                repository.addPendingDisablePackage(packageName)
            } else {
                repository.addTargetedPackage(packageName)
                repository.removePendingDisablePackage(packageName)
            }
        }
    }
}
