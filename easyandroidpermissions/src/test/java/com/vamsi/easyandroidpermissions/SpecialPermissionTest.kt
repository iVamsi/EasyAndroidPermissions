package com.vamsi.easyandroidpermissions

import android.app.AlarmManager
import android.app.Application
import android.content.Context
import android.os.PowerManager
import android.provider.Settings
import com.vamsi.easyandroidpermissions.internal.LifecyclePermissionManager
import com.vamsi.easyandroidpermissions.internal.PermissionHost
import io.mockk.every
import io.mockk.mockk
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowAlarmManager
import org.robolectric.shadows.ShadowSettings

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class SpecialPermissionTest {

    private lateinit var context: Application
    private lateinit var lifecycleOwner: TestLifecycleOwner
    private lateinit var caller: FakeActivityResultCaller
    private lateinit var manager: LifecyclePermissionManager

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        lifecycleOwner = TestLifecycleOwner()
        caller = FakeActivityResultCaller()
        manager = LifecyclePermissionManager(
            PermissionHost(
                lifecycleOwner = lifecycleOwner,
                activityResultCaller = caller,
                contextProvider = { context as Context },
            )
        )
    }

    @Test
    fun `requestSpecialPermission returns true without launching intent when already granted`() = runTest {
        ShadowSettings.setCanDrawOverlays(true)

        val granted = manager.requestSpecialPermission(SpecialPermission.SystemAlertWindow)

        assertTrue(granted)
        assertNull(caller.lastLaunchedIntent)
    }

    @Test
    fun `requestSpecialPermission launches settings intent and returns updated state`() = runTest {
        ShadowSettings.setCanDrawOverlays(false)
        assertFalse(manager.isSpecialPermissionGranted(SpecialPermission.SystemAlertWindow))

        val deferred = async(start = CoroutineStart.UNDISPATCHED) {
            manager.requestSpecialPermission(SpecialPermission.SystemAlertWindow)
        }

        assertEquals(
            Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
            caller.lastLaunchedIntent?.action,
        )
        assertEquals(
            "package:${context.packageName}",
            caller.lastLaunchedIntent?.dataString,
        )

        ShadowSettings.setCanDrawOverlays(true)
        caller.dispatchActivityResult()

        assertTrue(deferred.await())
        assertTrue(manager.isSpecialPermissionGranted(SpecialPermission.SystemAlertWindow))
    }

    @Test
    fun `exact alarm checks AlarmManager on Android 12 and later`() = runTest {
        ShadowAlarmManager.setCanScheduleExactAlarms(false)
        assertFalse(manager.isSpecialPermissionGranted(SpecialPermission.ExactAlarm))

        val deferred = async(start = CoroutineStart.UNDISPATCHED) {
            manager.requestSpecialPermission(SpecialPermission.ExactAlarm)
        }
        assertEquals(
            Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,
            caller.lastLaunchedIntent?.action,
        )

        ShadowAlarmManager.setCanScheduleExactAlarms(true)
        caller.dispatchActivityResult()

        assertTrue(deferred.await())
    }

    @Test
    @Config(sdk = [30])
    fun `exact alarm is granted by default before Android 12`() {
        assertTrue(manager.isSpecialPermissionGranted(SpecialPermission.ExactAlarm))
    }

    @Test
    fun `ignore battery optimization checks PowerManager and builds package intent`() {
        val powerManager = mockk<PowerManager> {
            every { isIgnoringBatteryOptimizations(context.packageName) } returns true
        }
        val mockContext = mockk<Context>(relaxed = true) {
            every { packageName } returns context.packageName
            every { getSystemService(PowerManager::class.java) } returns powerManager
        }
        val customManager = LifecyclePermissionManager(
            PermissionHost(
                lifecycleOwner = lifecycleOwner,
                activityResultCaller = caller,
                contextProvider = { mockContext },
            )
        )
        assertTrue(customManager.isSpecialPermissionGranted(SpecialPermission.IgnoreBatteryOptimization))

        val intent = context.createSpecialPermissionIntent(SpecialPermission.IgnoreBatteryOptimization)
        assertEquals(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, intent.action)
        assertEquals("package:${context.packageName}", intent.dataString)
    }

    @Test
    fun `createSpecialPermissionIntent maps each special permission to expected action`() {
        assertEquals(
            Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION,
            context.createSpecialPermissionIntent(SpecialPermission.ManageExternalStorage).action,
        )
        assertEquals(
            Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS,
            context.createSpecialPermissionIntent(SpecialPermission.NotificationListener).action,
        )
        assertEquals(
            Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
            context.createSpecialPermissionIntent(SpecialPermission.InstallUnknownApps).action,
        )
    }
}
