package com.vamsi.easyandroidpermissions

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings

/**
 * Represents the outcome of a permission request or status check.
 */
public sealed interface PermissionResult {

    /**
     * The permission is currently granted.
     */
    public data object Granted : PermissionResult

    /**
     * The permission is denied.
     *
     * @param canRequestAgain True when the system dialog can be shown again.
     * @param shouldShowRationale True if Android recommends showing an in-app rationale before re-requesting.
     */
    public data class Denied(
        val canRequestAgain: Boolean,
        val shouldShowRationale: Boolean
    ) : PermissionResult
}

/**
 * Convenience accessor that returns true when the permission is granted.
 */
public val PermissionResult.isGranted: Boolean
    get() = this is PermissionResult.Granted

/**
 * Creates an [Intent] that opens the app-specific settings page so that users can manually grant permissions.
 */
public fun Context.createPermissionSettingsIntent(): Intent {
    return Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
        data = Uri.fromParts("package", packageName, null)
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}

