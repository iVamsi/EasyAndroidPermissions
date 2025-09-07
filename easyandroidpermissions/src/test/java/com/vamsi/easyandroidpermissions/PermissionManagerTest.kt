package com.vamsi.easyandroidpermissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.vamsi.easyandroidpermissions.internal.ComposePermissionManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Unit tests for PermissionManager implementation.
 */
class PermissionManagerTest {

    private lateinit var context: Context
    private lateinit var permissionManager: ComposePermissionManager

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        permissionManager = ComposePermissionManager(context)
        
        // Mock ContextCompat.checkSelfPermission
        mockkStatic(ContextCompat::class)
    }

    @Test
    fun `isPermissionGranted returns true when permission is granted`() {
        // Arrange
        val permission = Manifest.permission.CAMERA
        every { 
            ContextCompat.checkSelfPermission(context, permission) 
        } returns PackageManager.PERMISSION_GRANTED

        // Act
        val result = permissionManager.isPermissionGranted(permission)

        // Assert
        assertTrue(result)
    }

    @Test
    fun `isPermissionGranted returns false when permission is denied`() {
        // Arrange
        val permission = Manifest.permission.CAMERA
        every { 
            ContextCompat.checkSelfPermission(context, permission) 
        } returns PackageManager.PERMISSION_DENIED

        // Act
        val result = permissionManager.isPermissionGranted(permission)

        // Assert
        assertFalse(result)
    }

    @Test
    fun `arePermissionsGranted returns correct status for multiple permissions`() {
        // Arrange
        val permissions = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        every { 
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) 
        } returns PackageManager.PERMISSION_GRANTED
        every { 
            ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) 
        } returns PackageManager.PERMISSION_DENIED

        // Act
        val result = permissionManager.arePermissionsGranted(permissions)

        // Assert
        assertTrue(result[Manifest.permission.CAMERA] == true)
        assertTrue(result[Manifest.permission.RECORD_AUDIO] == false)
    }

    @Test
    fun `request returns true immediately when permission is already granted`() = runTest {
        // Arrange
        val permission = Manifest.permission.CAMERA
        every { 
            ContextCompat.checkSelfPermission(context, permission) 
        } returns PackageManager.PERMISSION_GRANTED

        // Act
        val result = permissionManager.request(permission)

        // Assert
        assertTrue(result)
    }

    @Test
    fun `request throws exception when launcher not initialized`() = runTest {
        // Arrange
        val permission = Manifest.permission.CAMERA
        every { 
            ContextCompat.checkSelfPermission(context, permission) 
        } returns PackageManager.PERMISSION_DENIED

        // Act & Assert
        try {
            permissionManager.request(permission)
            fail("Expected IllegalStateException")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("not properly initialized"))
        }
    }

    @Test
    fun `requestMultiple returns empty map for empty input`() = runTest {
        // Act
        val result = permissionManager.requestMultiple(emptyList())

        // Assert
        assertTrue(result.isEmpty())
    }

    @Test
    fun `requestMultiple returns immediately when all permissions are granted`() = runTest {
        // Arrange
        val permissions = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        every { 
            ContextCompat.checkSelfPermission(context, any()) 
        } returns PackageManager.PERMISSION_GRANTED

        // Act
        val result = permissionManager.requestMultiple(permissions)

        // Assert
        assertEquals(2, result.size)
        assertTrue(result.values.all { it })
    }

    @Test
    fun `request completes when launcher callback is triggered`() = runTest {
        // Arrange
        val permission = Manifest.permission.CAMERA
        every { 
            ContextCompat.checkSelfPermission(context, permission) 
        } returns PackageManager.PERMISSION_DENIED

        var launchedPermission: String? = null
        permissionManager.singlePermissionLauncher = { perm ->
            launchedPermission = perm
            // Simulate immediate callback
            permissionManager.onSinglePermissionResult(true)
        }

        // Act
        val result = permissionManager.request(permission)

        // Assert
        assertEquals(permission, launchedPermission)
        assertTrue(result)
    }

    @Test
    fun `requestMultiple completes when launcher callback is triggered`() = runTest {
        // Arrange
        val permissions = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
        every { 
            ContextCompat.checkSelfPermission(context, any()) 
        } returns PackageManager.PERMISSION_DENIED

        var launchedPermissions: Array<String>? = null
        permissionManager.multiplePermissionsLauncher = { perms ->
            launchedPermissions = perms
            // Simulate immediate callback
            val results = mapOf(
                Manifest.permission.CAMERA to true,
                Manifest.permission.RECORD_AUDIO to false
            )
            permissionManager.onMultiplePermissionResult(results)
        }

        // Act
        val result = permissionManager.requestMultiple(permissions)

        // Assert
        assertArrayEquals(permissions.toTypedArray(), launchedPermissions)
        assertEquals(2, result.size)
        assertTrue(result[Manifest.permission.CAMERA] == true)
        assertTrue(result[Manifest.permission.RECORD_AUDIO] == false)
    }
}