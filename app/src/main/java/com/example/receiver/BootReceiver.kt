package com.example.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.example.data.DataStoreRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * BootReceiver intercepts the system boot broadcast and processes any pending
 * disable requests or configuration state updates scheduled under the anti-bypass security model.
 */
class BootReceiver : BroadcastReceiver() {

    private val receiverScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == Intent.ACTION_BOOT_COMPLETED) {
            Log.d("BootReceiver", "ACTION_BOOT_COMPLETED received. Resolving pending configurations...")

            val appContext = context.applicationContext
            receiverScope.launch {
                try {
                    val repository = DataStoreRepository(appContext)

                    // 1. Process pending general service active status toggles
                    val isDisabledPending = repository.isDisabledPending.first()
                    if (isDisabledPending) {
                        Log.i("BootReceiver", "Processing pending active blocker disable action...")
                        repository.setServiceActive(false)
                        repository.setDisabledPending(false)
                    }

                    // 2. Process pending per-app targeted package removals
                    val pendingApps = repository.pendingDisabledPackages.first()
                    if (pendingApps.isNotEmpty()) {
                        Log.i("BootReceiver", "Processing pending app removals: $pendingApps")
                        val activeApps = repository.targetedPackages.first().toMutableSet()
                        activeApps.removeAll(pendingApps)
                        repository.setTargetedPackages(activeApps)
                    }

                    // 3. Clear all processed variables
                    repository.clearAllPendingDisables()
                    Log.d("BootReceiver", "Successfully processed and committed boot settings.")
                } catch (e: Exception) {
                    Log.e("BootReceiver", "Error processing anti-bypass boot configurations", e)
                }
            }
        }
    }
}
