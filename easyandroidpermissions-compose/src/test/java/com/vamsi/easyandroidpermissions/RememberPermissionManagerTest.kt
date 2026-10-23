package com.vamsi.easyandroidpermissions

import android.Manifest
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.junit4.createComposeRule
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import android.content.Context
import android.provider.Settings

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

    @Test
    fun `rememberPermissionState reflects current grant state`() {
        shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.CAMERA)
        lateinit var cameraState: PermissionState
        lateinit var audioState: PermissionState

        composeRule.setContent {
            cameraState = rememberPermissionState(Manifest.permission.CAMERA)
            audioState = rememberPermissionState(Manifest.permission.RECORD_AUDIO)
        }

        assertTrue(cameraState.isGranted)
        assertEquals(PermissionResult.Granted, cameraState.status)
        assertFalse(audioState.isGranted)
        assertTrue(audioState.status is PermissionResult.Denied)
    }

    @Test
    fun `PermissionRequired renders branch matching current permission state`() {
        val app = RuntimeEnvironment.getApplication()
        var renderedBranch = ""
        var requestAction: (() -> Unit)? = null

        composeRule.setContent {
            PermissionRequired(
                permission = Manifest.permission.CAMERA,
                onGranted = { renderedBranch = "granted" },
                onRationale = { _, onRequest ->
                    renderedBranch = "rationale"
                    requestAction = onRequest
                },
                onDenied = { _, _ -> renderedBranch = "denied" },
            )
        }
        composeRule.waitForIdle()
        assertEquals("rationale", renderedBranch)

        shadowOf(app).grantPermissions(Manifest.permission.CAMERA)
        requestAction?.invoke()
        composeRule.waitForIdle()
        assertEquals("granted", renderedBranch)
    }

    @Test
    fun `PermissionRequired renders onDenied and opens settings when permanently denied`() {
        val app = RuntimeEnvironment.getApplication()
        app.getSharedPreferences("com.vamsi.easyandroidpermissions.denied", Context.MODE_PRIVATE)
            .edit()
            .putBoolean(Manifest.permission.RECORD_AUDIO, true)
            .commit()

        var renderedBranch = ""
        var openSettingsAction: (() -> Unit)? = null

        composeRule.setContent {
            PermissionRequired(
                permission = Manifest.permission.RECORD_AUDIO,
                onGranted = { renderedBranch = "granted" },
                onRationale = { _, _ -> renderedBranch = "rationale" },
                onDenied = { canRequestAgain, onOpenSettings ->
                    renderedBranch = "denied:$canRequestAgain"
                    openSettingsAction = onOpenSettings
                },
            )
        }
        composeRule.waitForIdle()

        assertEquals("denied:false", renderedBranch)
        openSettingsAction?.invoke()
        val startedIntent = shadowOf(app).nextStartedActivity
        assertEquals(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, startedIntent.action)
    }
}
