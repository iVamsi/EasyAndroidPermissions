package com.vamsi.easyandroidpermissions

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.Stable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.vamsi.easyandroidpermissions.internal.findComponentActivity
import java.util.UUID
import kotlinx.coroutines.launch

/**
 * Creates and remembers a [PermissionManager] implementation that works inside Jetpack Compose.
 *
 * The manager stops working when this call leaves the composition: pending requests are
 * cancelled and later requests throw [IllegalStateException].
 */
@Composable
public fun rememberPermissionManager(): PermissionManager {
    val context = LocalContext.current
    val currentContext by rememberUpdatedState(context)
    val parentLifecycle = LocalLifecycleOwner.current.lifecycle
    val registry = checkNotNull(LocalActivityResultRegistryOwner.current) {
        "rememberPermissionManager() needs an ActivityResultRegistryOwner, such as a ComponentActivity."
    }.activityResultRegistry

    val compositionLifecycle = remember(parentLifecycle) { CompositionLifecycleOwner(parentLifecycle) }
    return remember(compositionLifecycle, registry) {
        PermissionManagerFactory.create(
            lifecycleOwner = compositionLifecycle,
            caller = RegistryCaller(registry),
            contextProvider = { currentContext },
            rationaleProvider = { permission ->
                val activity = currentContext.findComponentActivity()
                activity != null && ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
            },
        )
    }
}

/**
 * State holder for a single runtime [permission] in Jetpack Compose.
 *
 * Read [status] or [isGranted] during composition to recompose automatically when the
 * permission is granted or denied, or when the user returns from system Settings.
 *
 * Note for callers migrating from Accompanist Permissions: [status] returns EasyAndroidPermissions'
 * [PermissionResult] (`Granted` or `Denied(canRequestAgain, shouldShowRationale)`) rather than
 * Accompanist's `PermissionStatus`.
 */
@Stable
public interface PermissionState {
    /** The Android permission string being tracked (e.g. `Manifest.permission.CAMERA`). */
    public val permission: String

    /** Current [PermissionResult] for [permission]. */
    public val status: PermissionResult

    /** True when [status] is [PermissionResult.Granted]. */
    public val isGranted: Boolean
        get() = status.isGranted

    /** Launches the system permission dialog for [permission]. */
    public fun launchPermissionRequest()
}

/**
 * Creates and remembers a reactive [PermissionState] for [permission], backed by the
 * shared [PermissionManager] engine.
 *
 * The state automatically refreshes on `ON_RESUME` (for example, after returning from
 * the system Settings screen).
 */
@Composable
public fun rememberPermissionState(
    permission: String,
    onPermissionResult: (PermissionResult) -> Unit = {},
): PermissionState {
    val manager = rememberPermissionManager()
    val scope = rememberCoroutineScope()
    val currentOnResult by rememberUpdatedState(onPermissionResult)
    val initialStatus = remember(manager, permission) {
        manager.getPermissionState(permission)
    }
    val trackedStates = manager.permissionStates.collectAsState()

    return remember(manager, permission, scope) {
        object : PermissionState {
            override val permission: String = permission

            override val status: PermissionResult
                get() = trackedStates.value[permission] ?: initialStatus

            override fun launchPermissionRequest() {
                scope.launch {
                    val result = manager.request(permission)
                    currentOnResult(result)
                }
            }
        }
    }
}

/**
 * Declarative wrapper that renders [onGranted] when [permission] is granted, [onRationale]
 * while the system permission dialog can still be requested, and [onDenied] once the
 * permission has been permanently denied (`canRequestAgain == false`).
 */
@Composable
public fun PermissionRequired(
    permission: String,
    onGranted: @Composable () -> Unit,
    onRationale: @Composable (shouldShowRationale: Boolean, onRequest: () -> Unit) -> Unit,
    onDenied: @Composable (canRequestAgain: Boolean, onOpenSettings: () -> Unit) -> Unit,
) {
    val context = LocalContext.current
    val currentContext by rememberUpdatedState(context)
    val state = rememberPermissionState(permission)

    when (val currentStatus = state.status) {
        PermissionResult.Granted -> onGranted()
        is PermissionResult.Denied -> {
            if (currentStatus.canRequestAgain) {
                onRationale(currentStatus.shouldShowRationale) {
                    state.launchPermissionRequest()
                }
            } else {
                onDenied(currentStatus.canRequestAgain) {
                    currentContext.startActivity(currentContext.createPermissionSettingsIntent())
                }
            }
        }
    }
}

/**
 * Follows the host lifecycle while remembered, and moves to DESTROYED when it leaves the
 * composition. The shared engine cleans up on DESTROYED, so the manager's life ends with the
 * composable instead of the Activity.
 */
private class CompositionLifecycleOwner(
    private val parent: Lifecycle
) : LifecycleOwner, LifecycleEventObserver, RememberObserver {

    override val lifecycle = LifecycleRegistry(this)

    init {
        parent.addObserver(this)
    }

    override fun onStateChanged(source: LifecycleOwner, event: Lifecycle.Event) {
        lifecycle.handleLifecycleEvent(event)
    }

    override fun onRemembered() = Unit

    override fun onForgotten() = destroy()

    override fun onAbandoned() = destroy()

    private fun destroy() {
        parent.removeObserver(this)
        if (lifecycle.currentState.isAtLeast(Lifecycle.State.CREATED)) {
            lifecycle.currentState = Lifecycle.State.DESTROYED
        }
    }
}

/**
 * Registers launchers without tying them to a lifecycle, so it works after the host has started.
 * The engine unregisters them when it cleans up.
 */
private class RegistryCaller(private val registry: ActivityResultRegistry) : ActivityResultCaller {

    override fun <I, O> registerForActivityResult(
        contract: ActivityResultContract<I, O>,
        callback: ActivityResultCallback<O>
    ): ActivityResultLauncher<I> = registerForActivityResult(contract, registry, callback)

    override fun <I, O> registerForActivityResult(
        contract: ActivityResultContract<I, O>,
        registry: ActivityResultRegistry,
        callback: ActivityResultCallback<O>
    ): ActivityResultLauncher<I> =
        registry.register("easyandroidpermissions-${UUID.randomUUID()}", contract, callback)
}
