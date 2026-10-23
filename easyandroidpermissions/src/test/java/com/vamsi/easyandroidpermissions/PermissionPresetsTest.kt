package com.vamsi.easyandroidpermissions

import android.Manifest
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PermissionPresetsTest {

    @Test
    fun `cameraAndMicrophone returns camera and record audio`() {
        assertEquals(
            listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
            PermissionPresets.cameraAndMicrophone(),
        )
    }

    @Test
    fun `visualMedia on Android 14 includes user-selected permission`() {
        assertEquals(
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            ),
            PermissionPresets.visualMedia(includeVideo = true),
        )
        assertEquals(
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            ),
            PermissionPresets.visualMedia(includeVideo = false),
        )
    }

    @Test
    @Config(sdk = [33])
    fun `visualMedia on Android 13 uses granular media permissions without user-selected`() {
        assertEquals(
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
            ),
            PermissionPresets.visualMedia(includeVideo = true),
        )
    }

    @Test
    @Config(sdk = [32])
    fun `visualMedia before Android 13 uses external storage`() {
        assertEquals(
            listOf(Manifest.permission.READ_EXTERNAL_STORAGE),
            PermissionPresets.visualMedia(includeVideo = true),
        )
    }

    @Test
    @Config(sdk = [33])
    fun `nearbyDevices on Android 13 includes bluetooth and nearby wifi`() {
        assertEquals(
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
                Manifest.permission.NEARBY_WIFI_DEVICES,
            ),
            PermissionPresets.nearbyDevices(),
        )
    }

    @Test
    @Config(sdk = [31])
    fun `nearbyDevices on Android 12 includes bluetooth scan and connect`() {
        assertEquals(
            listOf(
                Manifest.permission.BLUETOOTH_SCAN,
                Manifest.permission.BLUETOOTH_CONNECT,
            ),
            PermissionPresets.nearbyDevices(),
        )
    }

    @Test
    @Config(sdk = [30])
    fun `nearbyDevices before Android 12 falls back to fine location`() {
        assertEquals(
            listOf(Manifest.permission.ACCESS_FINE_LOCATION),
            PermissionPresets.nearbyDevices(),
        )
    }

    @Test
    fun `location returns fine and coarse when precise and coarse only otherwise`() {
        assertEquals(
            listOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION,
            ),
            PermissionPresets.location(precise = true),
        )
        assertEquals(
            listOf(Manifest.permission.ACCESS_COARSE_LOCATION),
            PermissionPresets.location(precise = false),
        )
    }

    @Test
    fun `localNetwork returns Android 16 local network permission constant`() {
        assertEquals(
            "android.permission.ACCESS_LOCAL_NETWORK",
            PermissionPresets.localNetwork(),
        )
    }
}
