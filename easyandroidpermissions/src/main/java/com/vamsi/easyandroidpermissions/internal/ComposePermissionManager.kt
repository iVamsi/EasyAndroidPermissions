package com.vamsi.easyandroidpermissions.internal

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.vamsi.easyandroidpermissions.PermissionManager
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

/**
 * A simplified Compose-integrated implementation of [PermissionManager].
 * 
 * This implementation uses a callback-based approach that integrates cleanly
 * with Compose's ActivityResultLauncher system.
 */
internal class ComposePermissionManager(
    private val context: Context
) : PermissionManager {

    private val requestMutex = Mutex()
    private var currentSingleRequest: PermissionRequest? = null
    private var currentMultipleRequest: MultiplePermissionRequest? = null
    
    // Track pending requests for the same permission to avoid multiple launches
    private val pendingSingleRequests = mutableMapOf<String, MutableList<CompletableDeferred<Boolean>>>()
    
    // These will be set by the Compose integration
    internal var singlePermissionLauncher: ((String) -> Unit)? = null
    internal var multiplePermissionsLauncher: ((Array<String>) -> Unit)? = null

    override suspend fun request(permission: String): Boolean {
        // Check if already granted
        if (isPermissionGranted(permission)) {
            return true
        }

        val launcher = singlePermissionLauncher 
            ?: throw IllegalStateException("PermissionManager not properly initialized. Ensure you're using rememberPermissionManager() within a Composable.")

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
                launcher(permission)
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

        val launcher = multiplePermissionsLauncher 
            ?: throw IllegalStateException("PermissionManager not properly initialized. Ensure you're using rememberPermissionManager() within a Composable.")

        return requestMutex.withLock {
            // Double-check after acquiring lock
            val updatedStatus = arePermissionsGranted(permissions)
            if (updatedStatus.values.all { it }) {
                return@withLock updatedStatus
            }

            val deferred = CompletableDeferred<Map<String, Boolean>>()
            currentMultipleRequest = MultiplePermissionRequest(permissions, deferred)
            
            try {
                launcher(permissions.toTypedArray())
                deferred.await()
            } catch (e: CancellationException) {
                currentMultipleRequest = null
                throw e
            }
        }
    }

    override fun isPermissionGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
    }

    override fun arePermissionsGranted(permissions: List<String>): Map<String, Boolean> {
        return permissions.associateWith { isPermissionGranted(it) }
    }

    /**
     * Called when a single permission result is received.
     * This method should be called from the ActivityResultLauncher callback.
     */
    internal fun onSinglePermissionResult(isGranted: Boolean) {
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
     * This method should be called from the ActivityResultLauncher callback.
     */
    internal fun onMultiplePermissionResult(results: Map<String, Boolean>) {
        val request = currentMultipleRequest
        currentMultipleRequest = null
        request?.deferred?.complete(results)
    }
}
