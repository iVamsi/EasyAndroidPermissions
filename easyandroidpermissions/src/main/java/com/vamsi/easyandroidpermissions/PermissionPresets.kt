package com.vamsi.easyandroidpermissions

import android.Manifest
import android.os.Build

/**
 * Version-aware permission bundles for common Android features.
 */
public object PermissionPresets {

    /**
     * Returns camera and microphone permissions (`CAMERA` and `RECORD_AUDIO`).
     */
    public fun cameraAndMicrophone(): List<String> = listOf(
        Manifest.permission.CAMERA,
        Manifest.permission.RECORD_AUDIO,
    )

    /**
     * Returns the visual media read permissions for the current Android version:
     * - Android 14+ (API 34+): `READ_MEDIA_IMAGES`, `READ_MEDIA_VIDEO` (when [includeVideo] is true),
     *   and `READ_MEDIA_VISUAL_USER_SELECTED`.
     * - Android 13 (API 33): `READ_MEDIA_IMAGES` and `READ_MEDIA_VIDEO` (when [includeVideo] is true).
     * - Android 12L and lower (API <= 32): `READ_EXTERNAL_STORAGE`.
     */
    public fun visualMedia(includeVideo: Boolean = true): List<String> =
        mediaPermissions(includeVideo)

    /**
     * Returns Bluetooth and nearby device permissions for the current Android version:
     * - Android 13+ (API 33+): `BLUETOOTH_SCAN`, `BLUETOOTH_CONNECT`, and `NEARBY_WIFI_DEVICES`.
     * - Android 12 (API 31–32): `BLUETOOTH_SCAN` and `BLUETOOTH_CONNECT`.
     * - Android 11 and lower (API <= 30): `ACCESS_FINE_LOCATION`.
     */
    public fun nearbyDevices(): List<String> = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.NEARBY_WIFI_DEVICES,
        )
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> listOf(
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT,
        )
        else -> listOf(Manifest.permission.ACCESS_FINE_LOCATION)
    }

    /**
     * Returns foreground location permissions.
     *
     * When [precise] is true (the default), both `ACCESS_FINE_LOCATION` and `ACCESS_COARSE_LOCATION`
     * are included as required on Android 12+. When false, only `ACCESS_COARSE_LOCATION` is returned.
     */
    public fun location(precise: Boolean = true): List<String> =
        if (precise) {
            listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            )
        } else {
            listOf(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

    /**
     * Returns the local network runtime permission (`android.permission.ACCESS_LOCAL_NETWORK`)
     * introduced in Android 16 (API 36).
     */
    public fun localNetwork(): String = ACCESS_LOCAL_NETWORK

    private const val ACCESS_LOCAL_NETWORK: String = "android.permission.ACCESS_LOCAL_NETWORK"
}
