package com.example.ui.dashboard

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.model.InstalledAppItem

/**
 * Industrial Main Dashboard with clean components.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(modifier: Modifier = Modifier, viewModel: DashboardViewModel = viewModel()) {
    val haptic = LocalHapticFeedback.current

    val isServiceActive by viewModel.isServiceActive.collectAsState()
    val targetedPackages by viewModel.targetedPackages.collectAsState()
    val interceptionCount by viewModel.interceptionCount.collectAsState()
    val countdownDurationSeconds by viewModel.countdownDurationSeconds.collectAsState()
    val bypassDurationSeconds by viewModel.bypassDurationSeconds.collectAsState()
    val isDisabledPending by viewModel.isDisabledPending.collectAsState()
    val pendingDisabledPackages by viewModel.pendingDisabledPackages.collectAsState()

    val installedApps by viewModel.installedApps.collectAsState()
    val searchQuery by viewModel.searchQuery.collectAsState()
    val isLoadingApps by viewModel.isLoadingApps.collectAsState()

    var isSearchFocused by remember { mutableStateOf(false) }

    val context = LocalContext.current
    val dpm = remember { context.getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager }
    val adminComponent = remember { ComponentName(context, com.example.service.MyDeviceAdminReceiver::class.java) }
    
    var isDeviceAdminEnabled by remember { mutableStateOf(dpm.isAdminActive(adminComponent)) }

    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isDeviceAdminEnabled = dpm.isAdminActive(adminComponent)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    val lazyListState = rememberLazyListState()
    
    // Smooth transition for the list expansion
    val isListExpanded by remember {
        derivedStateOf { isSearchFocused || lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 200 }
    }

    val headerAlpha by animateFloatAsState(targetValue = if (isListExpanded) 0f else 1f, label = "header_alpha")

    val filteredApps = remember(searchQuery, installedApps, targetedPackages, pendingDisabledPackages) {
        val query = searchQuery.trim().lowercase()
        val filtered = if (query.isEmpty()) {
            installedApps
        } else {
            installedApps.filter {
                it.appLabel.lowercase().contains(query) ||
                        it.packageName.lowercase().contains(query)
            }
        }

        // Split into sections: Active/Pending first, then others
        val active = filtered.filter { targetedPackages.contains(it.packageName) || pendingDisabledPackages.contains(it.packageName) }
        val others = filtered.filter { !targetedPackages.contains(it.packageName) && !pendingDisabledPackages.contains(it.packageName) }
        
        active to others
    }

    LazyColumn(
        state = lazyListState,
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentPadding = PaddingValues(bottom = 24.dp)
    ) {
        item(key = "dashboard_header") {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .animateContentSize()
            ) {
                if (!isListExpanded) {
                    Column(
                        modifier = Modifier
                            .padding(horizontal = 24.dp, vertical = 16.dp)
                            .alpha(headerAlpha)
                    ) {
                        // Stark Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column {
                                Text(
                                    text = "Intercept Pro",
                                    style = MaterialTheme.typography.headlineMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onBackground
                                )
                                Text(
                                    text = "High-performance low-latency mindful delay util",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // Live stats block
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text(
                                        text = "Mindful Pauses Completed",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = "System active and listening",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                                    )
                                }

                                Text(
                                    text = interceptionCount.toString(),
                                    style = MaterialTheme.typography.headlineLarge,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.primary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Main Blocker Status Card (With Reboot constraint logic)
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(
                                containerColor = if (isDisabledPending) {
                                    MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.2f)
                                } else {
                                    MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.15f)
                                }
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = if (isDisabledPending) "Blocker: Queue Disable" else "Blocker Status: Active",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = if (isDisabledPending) {
                                            "Status will change to Disabled upon next reboot."
                                        } else {
                                            "Defensive protection active. Turn off requires reboot."
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Switch(
                                    checked = !isDisabledPending && isServiceActive,
                                    onCheckedChange = { active ->
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        viewModel.setServiceActive(active)
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Device Admin Card
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(20.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Prevent Uninstallation",
                                        style = MaterialTheme.typography.titleMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = if (isDeviceAdminEnabled) "Uninstall locked by Device Admin" else "Require blocker disable to uninstall",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }

                                Switch(
                                    checked = isDeviceAdminEnabled,
                                    enabled = !isDeviceAdminEnabled || (!isServiceActive && !isDisabledPending),
                                    onCheckedChange = { activate ->
                                        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                        if (activate) {
                                            val intent = Intent(DevicePolicyManager.ACTION_ADD_DEVICE_ADMIN).apply {
                                                putExtra(DevicePolicyManager.EXTRA_DEVICE_ADMIN, adminComponent)
                                                putExtra(DevicePolicyManager.EXTRA_ADD_EXPLANATION, "Required to prevent uninstallation while the blocker is active.")
                                            }
                                            context.startActivity(intent)
                                        } else {
                                            if (!isServiceActive && !isDisabledPending) {
                                                dpm.removeActiveAdmin(adminComponent)
                                                isDeviceAdminEnabled = false
                                            } else {
                                                isDeviceAdminEnabled = dpm.isAdminActive(adminComponent)
                                            }
                                        }
                                    }
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Slider for delay configuration
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Countdown delay duration",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = "${countdownDurationSeconds}s",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Slider(
                                    value = countdownDurationSeconds.toFloat(),
                                    onValueChange = { seconds ->
                                        viewModel.setCountdownDuration(seconds.toInt())
                                    },
                                    valueRange = 5f..30f,
                                    steps = 4
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        // Slider for bypass duration
                        Card(
                            shape = RoundedCornerShape(20.dp),
                            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(20.dp)) {
                                Row(
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text(
                                        text = "Re-entry grace period",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    Text(
                                        text = "${bypassDurationSeconds}s",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Slider(
                                    value = bypassDurationSeconds.toFloat(),
                                    onValueChange = { seconds ->
                                        viewModel.setBypassDuration(seconds.toInt())
                                    },
                                    valueRange = 0f..300f,
                                    steps = 9
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(24.dp))

                        // App list Header
                        Text(
                            text = "TARGETED APPLICATIONS",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            letterSpacing = 1.2.sp
                        )
                    }
                }
            }
        }

        stickyHeader(key = "search_bar") {
            val focusManager = androidx.compose.ui.platform.LocalFocusManager.current
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { viewModel.updateSearchQuery(it) },
                    placeholder = { Text("Search installed applications...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty() || isSearchFocused) {
                            IconButton(onClick = { 
                                viewModel.updateSearchQuery("")
                                focusManager.clearFocus()
                            }) {
                                Icon(Icons.Default.Clear, contentDescription = "Clear")
                            }
                        }
                    },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    colors = TextFieldDefaults.colors(
                        focusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        unfocusedContainerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                        focusedIndicatorColor = Color.Transparent,
                        unfocusedIndicatorColor = Color.Transparent
                    ),
                    modifier = Modifier
                        .fillMaxWidth()
                        .onFocusChanged { isSearchFocused = it.isFocused }
                )
            }
        }

        if (isLoadingApps) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 100.dp),
                    contentAlignment = Alignment.Center
                ) {
                    CircularProgressIndicator(strokeWidth = 3.dp, modifier = Modifier.size(32.dp))
                }
            }
        } else {
            val (activeApps, otherApps) = filteredApps

            if (activeApps.isEmpty() && otherApps.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 100.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "No apps match your search",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            } else {
                if (activeApps.isNotEmpty()) {
                    item(key = "header_active") {
                        Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                            SectionHeader("ACTIVE TARGETS")
                        }
                    }
                    items(activeApps, key = { "active_${it.packageName}" }) { item ->
                        Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 5.dp)) {
                            AppRowItem(
                                appItem = item,
                                isTargeted = targetedPackages.contains(item.packageName),
                                isPendingDisable = pendingDisabledPackages.contains(item.packageName),
                                onToggleTarget = { checked ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.setTargetPackageChecked(item.packageName, checked)
                                }
                            )
                        }
                    }
                }

                if (otherApps.isNotEmpty()) {
                    item(key = "header_others") {
                        Box(modifier = Modifier.padding(horizontal = 24.dp)) {
                            SectionHeader(if (activeApps.isEmpty()) "ALL APPLICATIONS" else "AVAILABLE APPS")
                        }
                    }
                    items(otherApps, key = { "other_${it.packageName}" }) { item ->
                        Box(modifier = Modifier.padding(horizontal = 24.dp, vertical = 5.dp)) {
                            AppRowItem(
                                appItem = item,
                                isTargeted = false,
                                isPendingDisable = false,
                                onToggleTarget = { checked ->
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    viewModel.setTargetPackageChecked(item.packageName, checked)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 16.dp, bottom = 8.dp),
        letterSpacing = 1.sp
    )
}

/**
 * App list row with physical touch target height >= 48dp.
 */
@Composable
fun AppRowItem(
    appItem: InstalledAppItem,
    isTargeted: Boolean,
    isPendingDisable: Boolean,
    onToggleTarget: (Boolean) -> Unit
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 72.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .clickable { onToggleTarget(!isTargeted || isPendingDisable) }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        Box(
            modifier = Modifier
                .size(48.dp)
                .clip(RoundedCornerShape(12.dp))
                .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.4f)),
            contentAlignment = Alignment.Center
        ) {
            Image(
                bitmap = appItem.icon.asImageBitmap(),
                contentDescription = null,
                modifier = Modifier.size(34.dp)
            )
        }

        Spacer(modifier = Modifier.width(16.dp))

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = appItem.appLabel,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1
            )
            Text(
                text = if (isPendingDisable) {
                    "Pending Reboot to Disable"
                } else if (isTargeted) {
                    "Delay Active"
                } else {
                    appItem.packageName
                },
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                color = if (isPendingDisable) {
                    MaterialTheme.colorScheme.error
                } else if (isTargeted) {
                    MaterialTheme.colorScheme.primary
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
                }
            )
        }

        Switch(
            checked = isTargeted && !isPendingDisable,
            onCheckedChange = onToggleTarget,
            modifier = Modifier
                .scale(0.9f)
                .semantics {
                    contentDescription = "${appItem.appLabel} toggle"
                }
        )
    }
}
