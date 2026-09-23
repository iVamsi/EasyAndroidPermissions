package com.vamsi.easyandroidpermissions

import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.compose.runtime.Composable
import androidx.compose.runtime.RememberObserver
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
