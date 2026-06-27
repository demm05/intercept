package com.example

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.TextUtils
import android.view.accessibility.AccessibilityManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
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
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.graphics.drawable.toBitmap
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import com.example.data.DataStoreRepository
import com.example.service.AppInterceptorService
import com.example.ui.theme.MyApplicationTheme
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                Scaffold(
                    modifier = Modifier.fillMaxSize(),
                    containerColor = MaterialTheme.colorScheme.background
                ) { innerPadding ->
                    MainHandshakeContainer(
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

data class InstalledAppItem(
    val packageName: String,
    val appLabel: String,
    val icon: Bitmap, // Ultra-fast rendering
    val isSystem: Boolean
)

/**
 * Sequential Permission Handshake checking overlay and accessibility permissions first.
 */
@Composable
fun MainHandshakeContainer(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var hasOverlayPermission by remember { mutableStateOf(false) }
    var hasAccessibilityPermission by remember { mutableStateOf(false) }

    // Re-check permissions logic
    val checkPermissions = {
        hasOverlayPermission = Settings.canDrawOverlays(context)
        hasAccessibilityPermission = isAccessibilityServiceEnabled(context, AppInterceptorService::class.java)
    }

    // Polling check while onboarding is visible
    LaunchedEffect(hasOverlayPermission, hasAccessibilityPermission) {
        while (!hasOverlayPermission || !hasAccessibilityPermission) {
            checkPermissions()
            delay(1000) 
        }
    }

    // Trigger recheck on activity resume
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                checkPermissions()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    if (!hasOverlayPermission) {
        PermissionOnboardingScreen(
            headline = "Display Over Apps",
            description = "This allows the breathing timer to appear immediately. \n\nAfter clicking below, find 'Focus Interceptor' and toggle 'Allow display over other apps'.",
            buttonLabel = "1. Grant Overlay Permission",
            onButtonClick = {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${context.packageName}")
                ).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            },
            modifier = modifier
        )
    } else if (!hasAccessibilityPermission) {
        PermissionOnboardingScreen(
            headline = "Accessibility Service",
            description = "This detects when distracting apps are opened. \n\nAfter clicking, go to 'Downloaded apps' (or 'Installed apps'), select 'Focus Interceptor', and turn it ON.",
            buttonLabel = "2. Enable Accessibility Service",
            onButtonClick = {
                val intent = Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                context.startActivity(intent)
            },
            modifier = modifier
        )
    } else {
        DashboardScreen(
            modifier = modifier
        )
    }
}

/**
 * Stark, industrial, and clean Material 3 Onboarding gate.
 */
@Composable
fun PermissionOnboardingScreen(
    headline: String,
    description: String,
    buttonLabel: String,
    onButtonClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(24.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                text = headline,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onBackground,
                textAlign = TextAlign.Center
            )

            Spacer(modifier = Modifier.height(12.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(32.dp))

            FilledTonalButton(
                onClick = onButtonClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(56.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Text(
                    text = buttonLabel,
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp
                )
            }
        }
    }
}

/**
 * Industrial Main Dashboard with clean components.
 */
@OptIn(ExperimentalMaterial3Api::class, androidx.compose.foundation.ExperimentalFoundationApi::class)
@Composable
fun DashboardScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val repository = remember { DataStoreRepository(context.applicationContext) }
    val haptic = LocalHapticFeedback.current

    val isServiceActive by repository.isServiceActive.collectAsState(initial = true)
    val targetedPackages by repository.targetedPackages.collectAsState(initial = emptySet())
    val interceptionCount by repository.interceptionCount.collectAsState(initial = 0)
    val countdownDurationSeconds by repository.countdownDurationSeconds.collectAsState(initial = 10)
    val bypassDurationSeconds by repository.bypassDurationSeconds.collectAsState(initial = 60)
    val isDisabledPending by repository.isDisabledPending.collectAsState(initial = false)
    val pendingDisabledPackages by repository.pendingDisabledPackages.collectAsState(initial = emptySet())

    var installedApps by remember { mutableStateOf<List<InstalledAppItem>>(emptyList()) }
    var searchQuery by remember { mutableStateOf("") }
    var isLoadingApps by remember { mutableStateOf(true) }
    var isSearchFocused by remember { mutableStateOf(false) }

    val lazyListState = rememberLazyListState()
    
    // Smooth transition for the list expansion
    val isListExpanded by remember {
        derivedStateOf { isSearchFocused || lazyListState.firstVisibleItemIndex > 0 || lazyListState.firstVisibleItemScrollOffset > 200 }
    }

    val headerAlpha by animateFloatAsState(targetValue = if (isListExpanded) 0f else 1f, label = "header_alpha")

    // Optimization: Pre-sort and pre-filter targeting to avoid work in the UI loop
    val sortedApps = remember(installedApps) {
        installedApps.sortedBy { it.appLabel.lowercase() }
    }

    val filteredApps = remember(searchQuery, sortedApps, targetedPackages, pendingDisabledPackages) {
        val query = searchQuery.trim().lowercase()
        val filtered = if (query.isEmpty()) {
            sortedApps
        } else {
            sortedApps.filter {
                it.appLabel.lowercase().contains(query) ||
                        it.packageName.lowercase().contains(query)
            }
        }

        // Split into sections: Active/Pending first, then others
        val active = filtered.filter { targetedPackages.contains(it.packageName) || pendingDisabledPackages.contains(it.packageName) }
        val others = filtered.filter { !targetedPackages.contains(it.packageName) && !pendingDisabledPackages.contains(it.packageName) }
        
        active to others
    }

    // Query launchable applications in a low-priority background thread
    LaunchedEffect(Unit) {
        isLoadingApps = true
        withContext(Dispatchers.IO) {
            val pm = context.packageManager
            val intent = Intent(Intent.ACTION_MAIN, null).apply {
                addCategory(Intent.CATEGORY_LAUNCHER)
            }
            val list = pm.queryIntentActivities(intent, 0)
            val items = list.mapNotNull { resolveInfo ->
                val appInfo = resolveInfo.activityInfo.applicationInfo
                val packageName = appInfo.packageName
                if (packageName == context.packageName) return@mapNotNull null
                val label = resolveInfo.loadLabel(pm).toString()
                
                // Convert to bitmap ONCE on background thread to prevent scroll lag
                val icon = resolveInfo.loadIcon(pm).toBitmap(width = 100, height = 100)
                
                val isSystem = (appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0
                InstalledAppItem(packageName, label, icon, isSystem)
            }.distinctBy { it.packageName }

            withContext(Dispatchers.Main) {
                installedApps = items
                isLoadingApps = false
            }
        }
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
                                        coroutineScope.launch {
                                            if (!active) {
                                                // Anti-bypass reboot constraint
                                                repository.setDisabledPending(true)
                                            } else {
                                                repository.setDisabledPending(false)
                                                repository.setServiceActive(true)
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
                                        coroutineScope.launch { repository.setCountdownDuration(seconds.toInt()) }
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
                                        coroutineScope.launch { repository.setBypassDuration(seconds.toInt()) }
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
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.background)
                    .padding(horizontal = 24.dp, vertical = 8.dp)
            ) {
                TextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    placeholder = { Text("Search installed applications...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search") },
                    trailingIcon = {
                        if (searchQuery.isNotEmpty()) {
                            IconButton(onClick = { searchQuery = "" }) {
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
                                    coroutineScope.launch {
                                        if (!checked) {
                                            repository.addPendingDisablePackage(item.packageName)
                                        } else {
                                            repository.addTargetedPackage(item.packageName)
                                            repository.removePendingDisablePackage(item.packageName)
                                        }
                                    }
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
                                    coroutineScope.launch {
                                        if (checked) {
                                            repository.addTargetedPackage(item.packageName)
                                        }
                                    }
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
            .heightIn(min = 72.dp) // Taller row for better touch targets and prominence
            .clip(RoundedCornerShape(20.dp))
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.25f))
            .clickable { onToggleTarget(!isTargeted || isPendingDisable) }
            .padding(horizontal = 16.dp, vertical = 10.dp)
    ) {
        // App icon with consistent sizing and better framing
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
            modifier = Modifier.scale(0.9f) // Slightly smaller switch to keep focus on labels
        )
    }
}

/**
 * Robust check if Accessibility Service is enabled in System settings.
 */
fun isAccessibilityServiceEnabled(context: Context, serviceClass: Class<out android.accessibilityservice.AccessibilityService>): Boolean {
    val expectedComponentName = ComponentName(context, serviceClass)
    val enabledServicesSetting = Settings.Secure.getString(
        context.contentResolver,
        Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
    ) ?: return false

    val colonSplitter = TextUtils.SimpleStringSplitter(':')
    colonSplitter.setString(enabledServicesSetting)
    while (colonSplitter.hasNext()) {
        val componentNameString = colonSplitter.next()
        val enabledService = ComponentName.unflattenFromString(componentNameString)
        if (enabledService != null && enabledService == expectedComponentName) {
            return true
        }
    }
    return false
}
