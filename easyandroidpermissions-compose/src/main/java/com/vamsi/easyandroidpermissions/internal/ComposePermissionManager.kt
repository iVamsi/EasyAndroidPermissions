package com.vamsi.easyandroidpermissions.compose.internal

import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.vamsi.easyandroidpermissions.PermissionManager
import com.vamsi.easyandroidpermissions.PermissionResult
import com.vamsi.easyandroidpermissions.isGranted
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

internal class ComposePermissionManager(
    private val context: Context
) : PermissionManager {

    private val requestMutex = Mutex()
    private val deniedPermissions = mutableSetOf<String>()
    private val pendingSingleRequests = mutableMapOf<String, MutableList<CompletableDeferred<PermissionResult>>>()

    private var currentSingleRequest: PermissionRequest? = null
    private var currentMultipleRequest: MultiplePermissionRequest? = null

    internal var singlePermissionLauncher: ((String) -> Unit)? = null
    internal var multiplePermissionsLauncher: ((Array<String>) -> Unit)? = null
    internal var rationaleProvider: ((String) -> Boolean)? = null

    private val _permissionStates = MutableStateFlow<Map<String, PermissionResult>>(emptyMap())
    override val permissionStates: StateFlow<Map<String, PermissionResult>> = _permissionStates.asStateFlow()

    override fun getPermissionState(permission: String): PermissionResult {
        val result = computeCurrentState(permission)
        cacheState(permission, result)
        return result
    }

    override fun getPermissionStates(permissions: List<String>): Map<String, PermissionResult> {
        val states = permissions.associateWith { computeCurrentState(it) }
        cacheStates(states)
        return states
    }

    override fun shouldShowRationale(permission: String): Boolean {
        return rationaleProvider?.invoke(permission) ?: false
    }

    override suspend fun request(permission: String): PermissionResult {
        val current = getPermissionState(permission)
        if (current.isGranted) return current

        ensureLaunchersAttached()

        return requestMutex.withLock {
            val updated = getPermissionState(permission)
            if (updated.isGranted) {
                return@withLock updated
            }

            val deferred = CompletableDeferred<PermissionResult>()
            val pending = pendingSingleRequests[permission]

            if (pending != null) {
                pending.add(deferred)
            } else {
                pendingSingleRequests[permission] = mutableListOf(deferred)
                currentSingleRequest = PermissionRequest(permission, deferred)
                singlePermissionLauncher!!.invoke(permission)
            }

            try {
                deferred.await().also { cacheState(permission, it) }
            } catch (cancellation: CancellationException) {
                pendingSingleRequests[permission]?.remove(deferred)
                if (pendingSingleRequests[permission].isNullOrEmpty()) {
                    pendingSingleRequests.remove(permission)
                    if (currentSingleRequest?.permission == permission) {
                        currentSingleRequest = null
                    }
                }
                throw cancellation
            }
        }
    }

    override suspend fun requestMultiple(permissions: List<String>): Map<String, PermissionResult> {
        if (permissions.isEmpty()) return emptyMap()

        val current = getPermissionStates(permissions)
        if (current.values.all { it.isGranted }) {
            return current
        }

        ensureLaunchersAttached()

        return requestMutex.withLock {
            val latest = getPermissionStates(permissions)
            if (latest.values.all { it.isGranted }) {
                return@withLock latest
            }

            val deferred = CompletableDeferred<Map<String, PermissionResult>>()
            currentMultipleRequest = MultiplePermissionRequest(permissions, deferred)

            try {
                multiplePermissionsLauncher!!.invoke(permissions.toTypedArray())
                deferred.await().also { cacheStates(it) }
            } catch (cancellation: CancellationException) {
                currentMultipleRequest = null
                throw cancellation
            }
        }
    }

    private fun ensureLaunchersAttached() {
        if (singlePermissionLauncher == null || multiplePermissionsLauncher == null) {
            throw IllegalStateException("PermissionManager not properly initialized. Call rememberPermissionManager() inside a Composition.")
        }
    }

    private fun computeCurrentState(permission: String): PermissionResult {
        val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        if (granted) return PermissionResult.Granted

        val wasDeniedBefore = deniedPermissions.contains(permission)
        val shouldShow = shouldShowRationale(permission)
        val canRequestAgain = shouldShow || !wasDeniedBefore

        return PermissionResult.Denied(
            canRequestAgain = canRequestAgain,
            shouldShowRationale = shouldShow
        )
    }

    private fun cacheState(permission: String, result: PermissionResult) {
        _permissionStates.update { it + (permission to result) }
    }

    private fun cacheStates(states: Map<String, PermissionResult>) {
        if (states.isEmpty()) return
        _permissionStates.update { current -> current + states }
    }

    internal fun onSinglePermissionResult(isGranted: Boolean) {
        val request = currentSingleRequest ?: return
        currentSingleRequest = null

        val result = buildResult(request.permission, isGranted)
        cacheState(request.permission, result)

        val pending = pendingSingleRequests.remove(request.permission).orEmpty()
        pending.forEach { deferred -> deferred.complete(result) }
    }

    internal fun onMultiplePermissionResult(results: Map<String, Boolean>) {
        val request = currentMultipleRequest
        currentMultipleRequest = null

        val mapped = results.mapValues { (permission, granted) ->
            buildResult(permission, granted)
        }

        cacheStates(mapped)
        request?.deferred?.complete(mapped)
    }

    private fun buildResult(permission: String, granted: Boolean): PermissionResult {
        if (granted) {
            deniedPermissions.remove(permission)
            return PermissionResult.Granted
        }

        val shouldShow = shouldShowRationale(permission)
        val wasDeniedBefore = deniedPermissions.contains(permission)
        deniedPermissions.add(permission)

        return PermissionResult.Denied(
            canRequestAgain = shouldShow || !wasDeniedBefore,
            shouldShowRationale = shouldShow
        )
    }

    internal fun detach() {
        singlePermissionLauncher = null
        multiplePermissionsLauncher = null
        rationaleProvider = null
    }
}

private data class PermissionRequest(
    val permission: String,
    val deferred: CompletableDeferred<PermissionResult>
)

private data class MultiplePermissionRequest(
    val permissions: List<String>,
    val deferred: CompletableDeferred<Map<String, PermissionResult>>
)

