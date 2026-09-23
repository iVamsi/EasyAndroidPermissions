package com.vamsi.easyandroidpermissions

import android.Manifest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class RememberPermissionManagerTest {

    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun `request returns granted without a dialog when already granted`() = runTest {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.CAMERA)
        lateinit var manager: PermissionManager
        composeRule.setContent { manager = rememberPermissionManager() }

        assertEquals(PermissionResult.Granted, manager.request(Manifest.permission.CAMERA))
    }

    @Test
    fun `request throws after the composable leaves the composition`() = runTest {
        var isShown by mutableStateOf(true)
        lateinit var manager: PermissionManager
        composeRule.setContent {
            if (isShown) manager = rememberPermissionManager()
        }

        isShown = false
        composeRule.waitForIdle()

        assertThrows(IllegalStateException::class.java) {
            kotlinx.coroutines.runBlocking { manager.request(Manifest.permission.CAMERA) }
        }
    }
}
