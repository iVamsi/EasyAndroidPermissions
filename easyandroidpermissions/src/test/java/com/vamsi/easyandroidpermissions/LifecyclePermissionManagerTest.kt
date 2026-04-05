package com.vamsi.easyandroidpermissions

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import com.vamsi.easyandroidpermissions.internal.LifecyclePermissionManager
import com.vamsi.easyandroidpermissions.internal.PermissionHost
import io.mockk.every
import io.mockk.mockk
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.flow.filter
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class LifecyclePermissionManagerTest {

    private val permissionStates = mutableMapOf<String, Int>()
    private val rationaleStates = mutableMapOf<String, Boolean>()

    private lateinit var context: Context
    private lateinit var lifecycleOwner: TestLifecycleOwner
    private lateinit var caller: FakeActivityResultCaller
    private lateinit var manager: LifecyclePermissionManager

    @Before
    fun setup() {
        context = mockk(relaxed = true)
        lifecycleOwner = TestLifecycleOwner()
        caller = FakeActivityResultCaller()

        mockkStatic(androidx.core.content.ContextCompat::class)
        every { androidx.core.content.ContextCompat.checkSelfPermission(any(), any()) } answers {
            val permission = secondArg<String>()
            permissionStates[permission] ?: PackageManager.PERMISSION_DENIED
        }

        manager = LifecyclePermissionManager(
            PermissionHost(
                lifecycleOwner = lifecycleOwner,
                activityResultCaller = caller,
                contextProvider = { context },
                readyCheck = { true },
                rationaleProvider = { permission ->
                    rationaleStates[permission] ?: false
                }
            )
        )
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    @Test
    fun `getPermissionState returns granted when permission already granted`() {
        permissionStates[Manifest.permission.CAMERA] = PackageManager.PERMISSION_GRANTED

        val result = manager.getPermissionState(Manifest.permission.CAMERA)

        assertTrue(result is PermissionResult.Granted)
    }

    @Test
    fun `request returns immediately when permission granted`() = runTest {
        permissionStates[Manifest.permission.CAMERA] = PackageManager.PERMISSION_GRANTED

        val result = manager.request(Manifest.permission.CAMERA)

        assertTrue(result is PermissionResult.Granted)
        assertEquals(null, caller.lastSinglePermission)
    }

    @Test
    fun `request launches system dialog when permission denied`() = runTest {
        permissionStates[Manifest.permission.CAMERA] = PackageManager.PERMISSION_DENIED

        val deferred = async(start = CoroutineStart.UNDISPATCHED) { manager.request(Manifest.permission.CAMERA) }

        assertEquals(Manifest.permission.CAMERA, caller.lastSinglePermission)

        caller.dispatchSingleResult(true)

        val result = deferred.await()
        assertTrue(result is PermissionResult.Granted)
    }

    @Test
    fun `requestMultiple returns map of results`() = runTest {
        permissionStates[Manifest.permission.CAMERA] = PackageManager.PERMISSION_DENIED
        permissionStates[Manifest.permission.RECORD_AUDIO] = PackageManager.PERMISSION_DENIED

        val deferred = async(start = CoroutineStart.UNDISPATCHED) {
            manager.requestMultiple(
                listOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                )
            )
        }

        caller.dispatchMultipleResult(
            mapOf(
                Manifest.permission.CAMERA to true,
                Manifest.permission.RECORD_AUDIO to false
            )
        )

        val result = deferred.await()
        assertTrue(result[Manifest.permission.CAMERA] is PermissionResult.Granted)
        val deniedResult = result[Manifest.permission.RECORD_AUDIO] as PermissionResult.Denied
        assertTrue(deniedResult.canRequestAgain)
    }

    @Test
    fun `permissionStates flow emits updates`() = runTest {
        permissionStates[Manifest.permission.CAMERA] = PackageManager.PERMISSION_DENIED

        val collectJob = async(start = CoroutineStart.UNDISPATCHED) {
            manager.permissionStates
                .filter { it.containsKey(Manifest.permission.CAMERA) }
                .first()
        }

        val deferred = async(start = CoroutineStart.UNDISPATCHED) { manager.request(Manifest.permission.CAMERA) }
        caller.dispatchSingleResult(false)
        deferred.await()

        val states = collectJob.await()
        val state = states[Manifest.permission.CAMERA] as PermissionResult.Denied
        assertTrue(state.canRequestAgain)
    }

    @Test
    fun `pending requests are cancelled on lifecycle destroy`() = runTest {
        permissionStates[Manifest.permission.CAMERA] = PackageManager.PERMISSION_DENIED

        val deferred = async(start = CoroutineStart.UNDISPATCHED) { manager.request(Manifest.permission.CAMERA) }
        lifecycleOwner.handleDestroy()

        try {
            deferred.await()
            assertTrue("Expected CancellationException", false)
        } catch (expected: Exception) {
            assertTrue(expected is kotlinx.coroutines.CancellationException)
        }
    }

    @Test
    fun `denied permission becomes non-requestable after repeated denial`() = runTest {
        val permission = Manifest.permission.RECORD_AUDIO
        permissionStates[permission] = PackageManager.PERMISSION_DENIED

        val firstAttempt = async(start = CoroutineStart.UNDISPATCHED) { manager.request(permission) }
        caller.dispatchSingleResult(false)
        val firstResult = firstAttempt.await() as PermissionResult.Denied
        assertTrue(firstResult.canRequestAgain)

        val secondAttempt = async(start = CoroutineStart.UNDISPATCHED) { manager.request(permission) }
        caller.dispatchSingleResult(false)
        val secondResult = secondAttempt.await() as PermissionResult.Denied
        assertFalse(secondResult.canRequestAgain)
    }
}

