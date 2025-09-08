package com.vamsi.easyandroidpermissions

import androidx.activity.ComponentActivity
import androidx.fragment.app.Fragment

/**
 * Factory object for creating [PermissionManager] instances.
 * 
 * Provides convenient methods to create permission managers for different Android contexts.
 */
object PermissionManagerFactory {
    
    /**
     * Creates a [PermissionManager] for the given [ComponentActivity].
     * 
     * @param activity The activity context
     * @return A new [ActivityPermissionManager] instance
     */
    fun create(activity: ComponentActivity): PermissionManager {
        return ActivityPermissionManager(activity)
    }
    
    /**
     * Creates a [PermissionManager] for the given [Fragment].
     * 
     * @param fragment The fragment context
     * @return A new [FragmentPermissionManager] instance
     */
    fun create(fragment: Fragment): PermissionManager {
        return FragmentPermissionManager(fragment)
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
 * @return A new [ActivityPermissionManager] instance
 */
fun ComponentActivity.createPermissionManager(): PermissionManager {
    return ActivityPermissionManager(this)
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
 * @return A new [FragmentPermissionManager] instance
 */
fun Fragment.createPermissionManager(): PermissionManager {
    return FragmentPermissionManager(this)
}