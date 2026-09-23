package com.vamsi.easyandroidpermissions

import android.Manifest
import android.os.Build

/**
 * Access to the user's photos and videos.
 *
 * On Android 14 and later, the user can grant access to only the items they pick. That case is
 * [Partial]: the app can read the picked items, while `READ_MEDIA_IMAGES` itself stays denied.
 */
public sealed interface MediaAccess {

    /** The app can read all photos (and videos, when requested). */
    public data object Full : MediaAccess

    /** The app can read only the photos and videos the user picked. Request again to let them pick more. */
    public data object Partial : MediaAccess

    /**
     * The app has no access.
     *
     * @param canRequestAgain True when the system dialog can be shown again.
     * @param shouldShowRationale True if Android recommends showing an in-app rationale before re-requesting.
     */
    public data class Denied(
        val canRequestAgain: Boolean,
        val shouldShowRationale: Boolean
    ) : MediaAccess
}

/**
 * Requests read access to photos, and to videos when [includeVideo] is true, using the right
 * permissions for the device's Android version.
 *
 * To get [MediaAccess.Partial] on Android 14 and later, declare
 * `READ_MEDIA_VISUAL_USER_SELECTED` in your manifest next to `READ_MEDIA_IMAGES`.
 */
@androidx.annotation.MainThread
@androidx.annotation.CheckResult
public suspend fun PermissionManager.requestMedia(includeVideo: Boolean = true): MediaAccess =
    requestMultiple(mediaPermissions(includeVideo)).toMediaAccess()

/**
 * Returns the current photo and video access without showing a dialog.
 */
public fun PermissionManager.getMediaAccess(includeVideo: Boolean = true): MediaAccess =
    getPermissionStates(mediaPermissions(includeVideo)).toMediaAccess()

internal fun mediaPermissions(includeVideo: Boolean): List<String> = when {
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> listOfNotNull(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO.takeIf { includeVideo },
        Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
    )
    Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOfNotNull(
        Manifest.permission.READ_MEDIA_IMAGES,
        Manifest.permission.READ_MEDIA_VIDEO.takeIf { includeVideo },
    )
    else -> listOf(Manifest.permission.READ_EXTERNAL_STORAGE)
}

private fun Map<String, PermissionResult>.toMediaAccess(): MediaAccess {
    val userSelected = this[Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED]
    val required = this - Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED
    val denied = required.values.firstOrNull { it is PermissionResult.Denied } as PermissionResult.Denied?
    return when {
        denied == null -> MediaAccess.Full
        userSelected is PermissionResult.Granted -> MediaAccess.Partial
        else -> MediaAccess.Denied(denied.canRequestAgain, denied.shouldShowRationale)
    }
}
