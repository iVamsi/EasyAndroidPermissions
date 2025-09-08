package com.vamsi.easyandroidpermissions

import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import com.vamsi.easyandroidpermissions.internal.MultiplePermissionRequest
import com.vamsi.easyandroidpermissions.internal.PermissionRequest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

/**
 * Activity-based implementation of [PermissionManager] for use in ComponentActivity contexts.
 * 
 * This implementation handles permission requests using ActivityResultContracts and provides
 * proper lifecycle management to ensure launchers are properly cleaned up.
 * 
 * This class is internal and should not be instantiated directly. Instead, use:
 * - `PermissionManagerFactory.create(activity)` or
 * - `activity.createPermissionManager()` extension function
 * 
 * Usage:
 * ```kotlin
 * class MainActivity : ComponentActivity() {
 *     private lateinit var permissionManager: PermissionManager
 *     
 *     override fun onCreate(savedInstanceState: Bundle?) {
 *         super.onCreate(savedInstanceState)
 *         permissionManager = this.createPermissionManager()
 *         
 *         // Use permissionManager.request() or permissionManager.requestMultiple()
 *     }
 * }
 * ```
 */
internal class ActivityPermissionManager(
    private val activity: ComponentActivity
) : PermissionManager, LifecycleEventObserver {

    private val requestMutex = Mutex()
    private var currentSingleRequest: PermissionRequest? = null
    private var currentMultipleRequest: MultiplePermissionRequest? = null
    
    // Track pending requests for the same permission to avoid multiple launches
    private val pendingSingleRequests = mutableMapOf<String, MutableList<CompletableDeferred<Boolean>>>()
    
    private lateinit var singlePermissionLauncher: ActivityResultLauncher<String>
    private lateinit var multiplePermissionsLauncher: ActivityResultLauncher<Array<String>>
    
    init {
        initializeLaunchers()
        activity.lifecycle.addObserver(this)
    }
    
    private fun initializeLaunchers() {
        singlePermissionLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            onSinglePermissionResult(isGranted)
        }
        
        multiplePermissionsLauncher = activity.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            onMultiplePermissionResult(results)
        }
    }

    override suspend fun request(permission: String): Boolean {
        // Check if already granted
        if (isPermissionGranted(permission)) {
            return true
        }

        ensureLaunchersInitialized()

        return requestMutex.withLock {
            // Double-check after acquiring lock
            if (isPermissionGranted(permission)) {
                return@withLock true
            }

            val deferred = CompletableDeferred<Boolean>()
            
            // Check if there's already a request for this permission
            val existingRequests = pendingSingleRequests[permission]
            if (existingRequests != null) {
                // Add this deferred to the existing requests
                existingRequests.add(deferred)
            } else {
                // This is the first request for this permission
                pendingSingleRequests[permission] = mutableListOf(deferred)
                currentSingleRequest = PermissionRequest(permission, deferred)
                singlePermissionLauncher.launch(permission)
            }
            
            try {
                deferred.await()
            } catch (e: CancellationException) {
                // Remove this deferred from pending requests
                pendingSingleRequests[permission]?.remove(deferred)
                if (pendingSingleRequests[permission]?.isEmpty() == true) {
                    pendingSingleRequests.remove(permission)
                    if (currentSingleRequest?.permission == permission) {
                        currentSingleRequest = null
                    }
                }
                throw e
            }
        }
    }

    override suspend fun requestMultiple(permissions: List<String>): Map<String, Boolean> {
        if (permissions.isEmpty()) {
            return emptyMap()
        }

        // Check if all are already granted
        val currentStatus = arePermissionsGranted(permissions)
        if (currentStatus.values.all { it }) {
            return currentStatus
        }

        ensureLaunchersInitialized()

        return requestMutex.withLock {
            // Double-check after acquiring lock
            val updatedStatus = arePermissionsGranted(permissions)
            if (updatedStatus.values.all { it }) {
                return@withLock updatedStatus
            }

            val deferred = CompletableDeferred<Map<String, Boolean>>()
            currentMultipleRequest = MultiplePermissionRequest(permissions, deferred)
            
            try {
                multiplePermissionsLauncher.launch(permissions.toTypedArray())
                deferred.await()
            } catch (e: CancellationException) {
                currentMultipleRequest = null
                throw e
            }
        }
    }

    override fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(activity, permission) == PackageManager.PERMISSION_GRANTED
    }

    override fun arePermissionsGranted(permissions: List<String>): Map<String, Boolean> {
        return permissions.associateWith { isPermissionGranted(it) }
    }
    
    private fun ensureLaunchersInitialized() {
        if (!::singlePermissionLauncher.isInitialized || !::multiplePermissionsLauncher.isInitialized) {
            throw IllegalStateException("ActivityPermissionManager launchers not initialized. This might happen if the Activity is being destroyed.")
        }
    }

    /**
     * Called when a single permission result is received.
     */
    private fun onSinglePermissionResult(isGranted: Boolean) {
        val request = currentSingleRequest
        if (request != null) {
            currentSingleRequest = null
            
            // Complete all pending requests for this permission
            val pending = pendingSingleRequests.remove(request.permission)
            pending?.forEach { deferred ->
                deferred.complete(isGranted)
            }
        }
    }

    /**
     * Called when multiple permission results are received.
     */
    private fun onMultiplePermissionResult(results: Map<String, Boolean>) {
        val request = currentMultipleRequest
        currentMultipleRequest = null
        request?.deferred?.complete(results)
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_DESTROY) {
            cleanup()
        }
    }
    
    private fun cleanup() {
        activity.lifecycle.removeObserver(this)
        
        // Cancel any pending requests
        currentSingleRequest?.deferred?.cancel()
        currentMultipleRequest?.deferred?.cancel()
        
        pendingSingleRequests.values.forEach { requests ->
            requests.forEach { it.cancel() }
        }
        pendingSingleRequests.clear()
        
        currentSingleRequest = null
        currentMultipleRequest = null
    }
}