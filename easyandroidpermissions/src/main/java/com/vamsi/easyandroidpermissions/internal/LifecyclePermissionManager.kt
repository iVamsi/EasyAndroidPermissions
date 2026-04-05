package com.vamsi.easyandroidpermissions.internal

import android.content.Context
import android.content.pm.PackageManager
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
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

/**
 * Concrete [PermissionManager] that works with any [LifecycleOwner] + [ActivityResultCaller].
 *
 * Responsibilities:
 * - Register and cache single/multiple permission launchers against the caller.
 * - Serialize concurrent permission requests via [Mutex] so Android's contract is respected.
 * - Emit observable permission states through [permissionStates] and remember previously denied
 *   permissions to better infer `canRequestAgain`.
 * - Automatically tears down on `Lifecycle.Event.ON_DESTROY` to avoid leaking launchers/request state.
 */
internal class LifecyclePermissionManager(
    private val host: PermissionHost
) : PermissionManager, LifecycleEventObserver {

    private val requestMutex = Mutex()
    private val deniedPermissions = mutableSetOf<String>()
    private val pendingSingleRequests = mutableMapOf<String, MutableList<CompletableDeferred<PermissionResult>>>()

    private var currentSingleRequest: PermissionRequest? = null
    private var currentMultipleRequest: MultiplePermissionRequest? = null

    private lateinit var singlePermissionLauncher: ActivityResultLauncher<String>
    private lateinit var multiplePermissionLauncher: ActivityResultLauncher<Array<String>>

    private val _permissionStates = MutableStateFlow<Map<String, PermissionResult>>(emptyMap())

    override val permissionStates: StateFlow<Map<String, PermissionResult>> = _permissionStates.asStateFlow()

    init {
        registerLaunchers()
        host.lifecycleOwner.lifecycle.addObserver(this)
    }

    private fun registerLaunchers() {
        singlePermissionLauncher = host.activityResultCaller.registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { isGranted ->
            onSinglePermissionResult(isGranted)
        }

        multiplePermissionLauncher = host.activityResultCaller.registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { results ->
            onMultiplePermissionResult(results)
        }
    }

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
        return host.shouldShowRationale(permission)
    }

    override suspend fun request(permission: String): PermissionResult {
        val current = getPermissionState(permission)
        if (current.isGranted) {
            return current
        }

        ensureHostReady()

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
                singlePermissionLauncher.launch(permission)
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

        ensureHostReady()

        return requestMutex.withLock {
            val latest = getPermissionStates(permissions)
            if (latest.values.all { it.isGranted }) {
                return@withLock latest
            }

            val deferred = CompletableDeferred<Map<String, PermissionResult>>()
            currentMultipleRequest = MultiplePermissionRequest(permissions, deferred)

            try {
                multiplePermissionLauncher.launch(permissions.toTypedArray())
                deferred.await().also { cacheStates(it) }
            } catch (cancellation: CancellationException) {
                currentMultipleRequest = null
                throw cancellation
            }
        }
    }

    private fun computeCurrentState(permission: String): PermissionResult {
        val context = host.contextProvider()
            ?: return PermissionResult.Denied(canRequestAgain = false, shouldShowRationale = false)

        val granted = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            return PermissionResult.Granted
        }

        val wasDeniedBefore = deniedPermissions.contains(permission)
        val shouldShow = host.shouldShowRationale(permission)
        val canRequestAgain = shouldShow || !wasDeniedBefore

        return PermissionResult.Denied(
            canRequestAgain = canRequestAgain,
            shouldShowRationale = shouldShow
        )
    }

    private fun cacheState(permission: String, result: PermissionResult) {
        _permissionStates.update { states ->
            states + (permission to result)
        }
    }

    private fun cacheStates(states: Map<String, PermissionResult>) {
        if (states.isEmpty()) return
        _permissionStates.update { current ->
            current + states
        }
    }

    private fun ensureHostReady() {
        if (!this::singlePermissionLauncher.isInitialized || !this::multiplePermissionLauncher.isInitialized) {
            throw IllegalStateException("Permission manager launchers are not initialized. Is the LifecycleOwner already destroyed?")
        }

        if (!host.isReady()) {
            throw IllegalStateException("Permission host is not attached. Ensure the LifecycleOwner is in an active state.")
        }

        if (host.contextProvider() == null) {
            throw IllegalStateException("Permission host context is null. Ensure the host is attached before requesting permissions.")
        }
    }

    private fun onSinglePermissionResult(isGranted: Boolean) {
        val request = currentSingleRequest ?: return
        currentSingleRequest = null

        val result = buildResult(request.permission, isGranted)
        completePending(request.permission, result)
    }

    private fun completePending(permission: String, result: PermissionResult) {
        cacheState(permission, result)

        val pending = pendingSingleRequests.remove(permission).orEmpty()
        pending.forEach { deferred ->
            if (!deferred.isCompleted) {
                deferred.complete(result)
            }
        }
    }

    private fun onMultiplePermissionResult(results: Map<String, Boolean>) {
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

        val shouldShow = host.shouldShowRationale(permission)
        val wasDeniedBefore = deniedPermissions.contains(permission)
        deniedPermissions.add(permission)

        return PermissionResult.Denied(
            canRequestAgain = shouldShow || !wasDeniedBefore,
            shouldShowRationale = shouldShow
        )
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        if (event == Lifecycle.Event.ON_DESTROY) {
            cleanup()
        }
    }

    private fun cleanup() {
        host.lifecycleOwner.lifecycle.removeObserver(this)

        currentSingleRequest?.deferred?.cancel()
        currentMultipleRequest?.deferred?.cancel()

        pendingSingleRequests.values.forEach { list ->
            list.forEach { it.cancel() }
        }
        pendingSingleRequests.clear()

        currentSingleRequest = null
        currentMultipleRequest = null
    }
}

/**
 * Lightweight holder describing the environment the permission manager runs inside.
 *
 * By passing callbacks instead of concrete `Activity`/`Fragment` references we can support
 * custom hosts (e.g., `ActivityResultRegistryOwner`, tests) while still centralizing lifecycle
 * and rationale checks.
 */
internal class PermissionHost(
    val lifecycleOwner: LifecycleOwner,
    val activityResultCaller: ActivityResultCaller,
    val contextProvider: () -> Context?,
    private val readyCheck: () -> Boolean = { true },
    private val rationaleProvider: (String) -> Boolean = { false }
) {
    fun isReady(): Boolean = readyCheck()
    fun shouldShowRationale(permission: String): Boolean = rationaleProvider(permission)
}

