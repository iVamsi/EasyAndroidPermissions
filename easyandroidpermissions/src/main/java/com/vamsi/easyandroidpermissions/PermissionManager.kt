package com.vamsi.easyandroidpermissions

import kotlinx.coroutines.flow.StateFlow

/**
 * Interface for managing Android runtime permissions using coroutines.
 * 
 * This interface provides suspend functions to request permissions in a sequential,
 * coroutine-friendly manner from activities, fragments, or other hosts that provide
 * [androidx.lifecycle.LifecycleOwner] and [androidx.activity.result.ActivityResultCaller].
 */
public interface PermissionManager {

    /**
     * Cold [StateFlow] that reflects the latest known permission states tracked by this manager.
     */
    public val permissionStates: StateFlow<Map<String, PermissionResult>>

    /**
     * Requests a single runtime permission.
     *
     * @param permission The permission to request (e.g., Manifest.permission.CAMERA)
     * @return A [PermissionResult] describing the outcome.
     */
    @androidx.annotation.MainThread
    @androidx.annotation.CheckResult
    public suspend fun request(permission: String): PermissionResult

    /**
     * Requests multiple runtime permissions.
     *
     * @param permissions List of permissions to request
     * @return Map of permissions to their granted status.
     */
    @androidx.annotation.MainThread
    @androidx.annotation.CheckResult
    public suspend fun requestMultiple(permissions: List<String>): Map<String, PermissionResult>

    /**
     * Returns the current status of a permission without triggering a system dialog.
     */
    public fun getPermissionState(permission: String): PermissionResult

    /**
     * Returns the current status for multiple permissions without triggering dialogs.
     */
    public fun getPermissionStates(permissions: List<String>): Map<String, PermissionResult>

    /**
     * Mirrors [androidx.core.app.ActivityCompat.shouldShowRequestPermissionRationale] for the current host.
     */
    public fun shouldShowRationale(permission: String): Boolean

    /**
     * Returns true if the permission can be requested again without directing the user to Settings.
     */
    public fun canRequestAgain(permission: String): Boolean = when (val state = getPermissionState(permission)) {
        is PermissionResult.Granted -> false
        is PermissionResult.Denied -> state.canRequestAgain
    }
}
