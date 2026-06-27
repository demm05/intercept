package com.example.ui.dashboard

import android.app.Application
import android.content.Intent
import android.content.pm.ApplicationInfo
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.data.DataStoreRepository
import com.example.model.InstalledAppItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

class DashboardViewModel(
    application: Application,
    private val repository: DataStoreRepository
) : AndroidViewModel(application) {

    val isServiceActive = repository.isServiceActive.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)
    val targetedPackages = repository.targetedPackages.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())
    val interceptionCount = repository.interceptionCount.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 0)
    val countdownDurationSeconds = repository.countdownDurationSeconds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 10)
    val bypassDurationSeconds = repository.bypassDurationSeconds.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), 60)
    val isDisabledPending = repository.isDisabledPending.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val pendingDisabledPackages = repository.pendingDisabledPackages.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptySet())

    val isStrictAppInfoBlockEnabled = repository.isStrictAppInfoBlockEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val isAggressivePipProtectionEnabled = repository.isAggressivePipProtectionEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)
    val isAutoDismissOverlayEnabled = repository.isAutoDismissOverlayEnabled.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), false)

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

    fun setStrictAppInfoBlockEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setStrictAppInfoBlockEnabled(enabled) }
    }

    fun setAggressivePipProtectionEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setAggressivePipProtectionEnabled(enabled) }
    }

    fun setAutoDismissOverlayEnabled(enabled: Boolean) {
        viewModelScope.launch { repository.setAutoDismissOverlayEnabled(enabled) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : androidx.lifecycle.ViewModel> create(
                modelClass: Class<T>,
                extras: CreationExtras
            ): T {
                require(modelClass.isAssignableFrom(DashboardViewModel::class.java)) {
                    "Unknown ViewModel class: ${modelClass.name}"
                }
                val application = checkNotNull(extras[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY])
                val repository = DataStoreRepository(application)
                return DashboardViewModel(application, repository) as T
            }
        }
    }
}
