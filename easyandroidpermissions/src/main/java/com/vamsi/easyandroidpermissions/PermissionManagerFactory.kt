package com.vamsi.easyandroidpermissions

import android.content.Context
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.ActivityResultRegistryOwner
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import com.vamsi.easyandroidpermissions.internal.LifecyclePermissionManager
import com.vamsi.easyandroidpermissions.internal.PermissionHost
import java.util.UUID

/**
 * Factory object for creating [PermissionManager] instances.
 * 
 * Provides convenient methods to create permission managers for different Android contexts.
 */
public object PermissionManagerFactory {
    
    /**
     * Creates a [PermissionManager] for the given [ComponentActivity].
     * 
     * @param activity The activity context
     * @return A new [PermissionManager] tied to this activity's lifecycle and activity-result APIs.
     */
    public fun create(activity: ComponentActivity): PermissionManager {
        return LifecyclePermissionManager(
            PermissionHost(
                lifecycleOwner = activity,
                activityResultCaller = activity,
                contextProvider = { activity },
                readyCheck = { !activity.isFinishing && !activity.isDestroyed },
                rationaleProvider = { permission ->
                    ActivityCompat.shouldShowRequestPermissionRationale(activity, permission)
                }
            )
        )
    }
    
    /**
     * Creates a [PermissionManager] for the given [Fragment].
     * 
     * @param fragment The fragment context
     * @return A new [PermissionManager] tied to this fragment's lifecycle and activity-result APIs.
     */
    public fun create(fragment: Fragment): PermissionManager {
        return LifecyclePermissionManager(
            PermissionHost(
                lifecycleOwner = fragment,
                activityResultCaller = fragment,
                contextProvider = { fragment.context },
                readyCheck = { fragment.isAdded && fragment.context != null },
                rationaleProvider = { permission ->
                    fragment.shouldShowRequestPermissionRationale(permission)
                }
            )
        )
    }

    /**
     * Creates a [PermissionManager] for any pair of [LifecycleOwner] and [ActivityResultCaller].
     *
     * @param lifecycleOwner Owner that controls cleanup
     * @param caller ActivityResult caller used to register launchers
     * @param contextProvider Supplies the current [Context] (must be non-null when requesting)
     * @param rationaleProvider Delegate mirroring [ActivityCompat.shouldShowRequestPermissionRationale]
     */
    public fun create(
        lifecycleOwner: LifecycleOwner,
        caller: ActivityResultCaller,
        contextProvider: () -> Context?,
        rationaleProvider: (String) -> Boolean = { false }
    ): PermissionManager {
        return LifecyclePermissionManager(
            PermissionHost(
                lifecycleOwner = lifecycleOwner,
                activityResultCaller = caller,
                contextProvider = contextProvider,
                readyCheck = {
                    contextProvider() != null &&
                        lifecycleOwner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)
                },
                rationaleProvider = rationaleProvider
            )
        )
    }

    /**
     * Creates a [PermissionManager] for an [ActivityResultRegistryOwner] (e.g., custom lifecycle components).
     */
    public fun create(
        registryOwner: ActivityResultRegistryOwner,
        contextProvider: () -> Context?,
        rationaleProvider: (String) -> Boolean = { false }
    ): PermissionManager {
        val lifecycleOwner = registryOwner as? LifecycleOwner
            ?: error("ActivityResultRegistryOwner must also implement LifecycleOwner.")
        return create(
            lifecycleOwner = lifecycleOwner,
            caller = RegistryActivityResultCaller(registryOwner),
            contextProvider = contextProvider,
            rationaleProvider = rationaleProvider
        )
    }
}

/**
 * Extension function to create a [PermissionManager] for this [ComponentActivity].
 * 
 * Usage:
 * ```kotlin
 * class MainActivity : ComponentActivity() {
 *     private val permissionManager = this.createPermissionManager()
 * }
 * ```
 * 
 * @return A new [PermissionManager] for this activity.
 */
public fun ComponentActivity.createPermissionManager(): PermissionManager {
    return PermissionManagerFactory.create(this)
}

/**
 * Extension function to create a [PermissionManager] for this [Fragment].
 * 
 * Usage:
 * ```kotlin
 * class CameraFragment : Fragment() {
 *     private val permissionManager = this.createPermissionManager()
 * }
 * ```
 * 
 * @return A new [PermissionManager] for this fragment.
 */
public fun Fragment.createPermissionManager(): PermissionManager {
    return PermissionManagerFactory.create(this)
}

private class RegistryActivityResultCaller(
    private val owner: ActivityResultRegistryOwner
) : ActivityResultCaller {
    private val lifecycleOwner: LifecycleOwner = owner as? LifecycleOwner
        ?: error("ActivityResultRegistryOwner must also implement LifecycleOwner.")
    override fun <I, O> registerForActivityResult(
        contract: ActivityResultContract<I, O>,
        callback: ActivityResultCallback<O>
    ): ActivityResultLauncher<I> {
        return registerWithRegistry(owner.activityResultRegistry, contract, callback)
    }

    override fun <I, O> registerForActivityResult(
        contract: ActivityResultContract<I, O>,
        registry: ActivityResultRegistry,
        callback: ActivityResultCallback<O>
    ): ActivityResultLauncher<I> {
        return registerWithRegistry(registry, contract, callback)
    }

    private fun <I, O> registerWithRegistry(
        registry: ActivityResultRegistry,
        contract: ActivityResultContract<I, O>,
        callback: ActivityResultCallback<O>
    ): ActivityResultLauncher<I> {
        return registry.register(
            "easyandroidpermissions-${UUID.randomUUID()}",
            lifecycleOwner,
            contract,
            callback
        )
    }
}
