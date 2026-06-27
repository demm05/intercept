package com.example.ui.dashboard

import android.app.admin.DevicePolicyManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.compose.foundation.selection.toggleable
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(modifier: Modifier = Modifier, viewModel: DashboardViewModel = viewModel(factory = DashboardViewModel.Factory)) {
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    val isServiceActive by viewModel.isServiceActive.collectAsState()
    val isDisabledPending by viewModel.isDisabledPending.collectAsState()
    val countdownDurationSeconds by viewModel.countdownDurationSeconds.collectAsState()
    val bypassDurationSeconds by viewModel.bypassDurationSeconds.collectAsState()

    val isStrictAppInfoBlockEnabled by viewModel.isStrictAppInfoBlockEnabled.collectAsState()
    val isAggressivePipProtectionEnabled by viewModel.isAggressivePipProtectionEnabled.collectAsState()
    val isAutoDismissOverlayEnabled by viewModel.isAutoDismissOverlayEnabled.collectAsState()

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

    LazyColumn(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 24.dp),
        contentPadding = PaddingValues(top = 16.dp, bottom = 100.dp)
    ) {
        item {
            Text(
                text = "Settings",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                modifier = Modifier.padding(bottom = 24.dp)
            )
        }

        item {
            // Device Admin Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                val deviceAdminEnabled = !isDeviceAdminEnabled || (!isServiceActive && !isDisabledPending)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .toggleable(
                            value = isDeviceAdminEnabled,
                            enabled = deviceAdminEnabled,
                            role = Role.Switch,
                            onValueChange = { activate ->
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
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Prevent Uninstallation",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = if (isDeviceAdminEnabled) "Uninstall locked by Device Admin" else "Requires deactivating Device Admin before uninstall",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    Switch(
                        checked = isDeviceAdminEnabled,
                        enabled = deviceAdminEnabled,
                        onCheckedChange = null
                    )
                }
            }
        }

        item {
            // Slider for delay configuration
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
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
                            color = MaterialTheme.colorScheme.onSurface
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
                        steps = 4,
                        modifier = Modifier.semantics { contentDescription = "Countdown delay duration slider" }
                    )
                }
            }
        }

        item {
            // Slider for bypass duration
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, MaterialTheme.colorScheme.outline.copy(alpha = 0.1f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
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
                            color = MaterialTheme.colorScheme.onSurface
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
                        steps = 9,
                        modifier = Modifier.semantics { contentDescription = "Re-entry grace period slider" }
                    )
                }
            }
        }

        item {
            // Advanced Protections Card
            Card(
                shape = RoundedCornerShape(24.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF03DAC5).copy(alpha = 0.05f)),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF03DAC5).copy(alpha = 0.2f)),
                modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Text(
                        text = "Advanced Experimental Protections",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.padding(bottom = 16.dp)
                    )

                    // Strict App Info Block
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = isStrictAppInfoBlockEnabled,
                                role = Role.Switch,
                                onValueChange = { viewModel.setStrictAppInfoBlockEnabled(it) }
                            )
                            .padding(bottom = 8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Strict App Info Block", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                            Text("Kicks to home if Force Stop screen is opened.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isStrictAppInfoBlockEnabled,
                            onCheckedChange = null
                        )
                    }

                    // Aggressive PiP Protection
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = isAggressivePipProtectionEnabled,
                                role = Role.Switch,
                                onValueChange = { viewModel.setAggressivePipProtectionEnabled(it) }
                            )
                            .padding(bottom = 8.dp)
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Aggressive PiP Protection", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                            Text("Forces blocked Picture-in-Picture apps back to full screen.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isAggressivePipProtectionEnabled,
                            onCheckedChange = null
                        )
                    }

                    // Auto-Dismiss Overlay
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween,
                        modifier = Modifier
                            .fillMaxWidth()
                            .toggleable(
                                value = isAutoDismissOverlayEnabled,
                                role = Role.Switch,
                                onValueChange = { viewModel.setAutoDismissOverlayEnabled(it) }
                            )
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Dismiss Overlay", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onBackground)
                            Text("Dismisses overlay if you swipe home or open recents.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = isAutoDismissOverlayEnabled,
                            onCheckedChange = null
                        )
                    }
                }
            }
        }
    }
}
