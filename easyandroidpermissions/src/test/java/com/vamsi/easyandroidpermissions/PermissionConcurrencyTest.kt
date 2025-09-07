package com.vamsi.easyandroidpermissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import com.vamsi.easyandroidpermissions.internal.ComposePermissionManager
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import kotlinx.coroutines.async
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.runTest
import org.junit.Before
import org.junit.Test
import org.junit.Assert.*

/**
 * Tests for concurrent permission requests.
 */
class PermissionConcurrencyTest {

    private lateinit var context: Context
    private lateinit var permissionManager: ComposePermissionManager

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        permissionManager = ComposePermissionManager(context)
        
        mockkStatic(ContextCompat::class)
    }

    @Test
    fun `multiple requests for same permission return same result`() = runTest {
        // Arrange
        val permission = Manifest.permission.CAMERA
        every { 
            ContextCompat.checkSelfPermission(context, permission) 
        } returns PackageManager.PERMISSION_DENIED

        var launchCount = 0
        permissionManager.singlePermissionLauncher = { _ ->
            launchCount++
            // Simulate response
            permissionManager.onSinglePermissionResult(true)
        }

        // Act - Make sequential requests to test the behavior
        val result1 = permissionManager.request(permission)
        
        // Reset permission as denied for next test
        every { 
            ContextCompat.checkSelfPermission(context, permission) 
        } returns PackageManager.PERMISSION_DENIED
        
        val result2 = permissionManager.request(permission)

        // Assert - Each request should launch the permission dialog
        assertEquals(2, launchCount)
        assertTrue(result1)
        assertTrue(result2)
    }

    @Test
    fun `concurrent requests for different permissions work independently`() = runTest {
        // Arrange
        val permission1 = Manifest.permission.CAMERA
        val permission2 = Manifest.permission.RECORD_AUDIO
        every { 
            ContextCompat.checkSelfPermission(context, any()) 
        } returns PackageManager.PERMISSION_DENIED

        val launchedPermissions = mutableListOf<String>()
        permissionManager.singlePermissionLauncher = { perm ->
            launchedPermissions.add(perm)
            // Simulate different results for different permissions
            val result = when (perm) {
                permission1 -> true
                permission2 -> false
                else -> false
            }
            permissionManager.onSinglePermissionResult(result)
        }

        // Act
        val deferred1 = async { permissionManager.request(permission1) }
        val deferred2 = async { permissionManager.request(permission2) }

        val result1 = deferred1.await()
        val result2 = deferred2.await()

        // Assert
        assertEquals(2, launchedPermissions.size)
        assertTrue(launchedPermissions.contains(permission1))
        assertTrue(launchedPermissions.contains(permission2))
        assertTrue(result1)
        assertFalse(result2)
    }

    @Test
    fun `mixed single and multiple requests are handled correctly`() = runTest {
        // Arrange
        val singlePermission = Manifest.permission.CAMERA
        val multiplePermissions = listOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.ACCESS_FINE_LOCATION)
        
        every { 
            ContextCompat.checkSelfPermission(context, any()) 
        } returns PackageManager.PERMISSION_DENIED

        var singleLaunched = false
        var multipleLaunched = false

        permissionManager.singlePermissionLauncher = { _ ->
            singleLaunched = true
            permissionManager.onSinglePermissionResult(true)
        }

        permissionManager.multiplePermissionsLauncher = { perms ->
            multipleLaunched = true
            val results = perms.associateWith { false }
            permissionManager.onMultiplePermissionResult(results)
        }

        // Act
        val singleDeferred = async { permissionManager.request(singlePermission) }
        val multipleDeferred = async { permissionManager.requestMultiple(multiplePermissions) }

        val singleResult = singleDeferred.await()
        val multipleResult = multipleDeferred.await()

        // Assert
        assertTrue(singleLaunched)
        assertTrue(multipleLaunched)
        assertTrue(singleResult)
        assertEquals(2, multipleResult.size)
        assertFalse(multipleResult.values.any { it })
    }
}