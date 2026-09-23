package com.vamsi.easyandroidpermissions

import android.Manifest
import android.app.Application
import android.content.Context
import android.content.pm.PackageManager
import com.vamsi.easyandroidpermissions.internal.LifecyclePermissionManager
import com.vamsi.easyandroidpermissions.internal.PermissionHost
import io.mockk.every
import io.mockk.mockkStatic
import io.mockk.unmockkAll
import kotlinx.coroutines.CoroutineStart
import kotlinx.coroutines.async
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class PermissionStateTrackingTest {

    private val permissionStates = mutableMapOf<String, Int>()

    private lateinit var context: Application
    private lateinit var lifecycleOwner: TestLifecycleOwner
    private lateinit var caller: FakeActivityResultCaller

    @Before
    fun setup() {
        context = RuntimeEnvironment.getApplication()
        mockkStatic(androidx.core.content.ContextCompat::class)
        every { androidx.core.content.ContextCompat.checkSelfPermission(any(), any()) } answers {
            permissionStates[secondArg<String>()] ?: PackageManager.PERMISSION_DENIED
        }
    }

    @After
    fun tearDown() {
        unmockkAll()
    }

    private fun newManager(): LifecyclePermissionManager {
        lifecycleOwner = TestLifecycleOwner()
        caller = FakeActivityResultCaller()
        return LifecyclePermissionManager(
            PermissionHost(
                lifecycleOwner = lifecycleOwner,
                activityResultCaller = caller,
                contextProvider = { context as Context },
            )
        )
    }

    @Test
    fun `denial is remembered by a recreated manager`() = runTest {
        val first = newManager()
        val attempt = async(start = CoroutineStart.UNDISPATCHED) { first.request(Manifest.permission.CAMERA) }
        caller.dispatchSingleResult(false)
        attempt.await()

        val recreated = newManager()

        val state = recreated.getPermissionState(Manifest.permission.CAMERA) as PermissionResult.Denied
        assertFalse(state.canRequestAgain)
    }

    @Test
    fun `granting clears a remembered denial`() = runTest {
        val first = newManager()
        val attempt = async(start = CoroutineStart.UNDISPATCHED) { first.request(Manifest.permission.CAMERA) }
        caller.dispatchSingleResult(false)
        attempt.await()

        permissionStates[Manifest.permission.CAMERA] = PackageManager.PERMISSION_GRANTED
        first.getPermissionState(Manifest.permission.CAMERA)
        permissionStates[Manifest.permission.CAMERA] = PackageManager.PERMISSION_DENIED

        val state = newManager().getPermissionState(Manifest.permission.CAMERA) as PermissionResult.Denied
        assertTrue(state.canRequestAgain)
    }

    @Test
    fun `interrupted multiple request reports current state without recording a denial`() = runTest {
        val manager = newManager()
        val permissions = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)

        val attempt = async(start = CoroutineStart.UNDISPATCHED) { manager.requestMultiple(permissions) }
        caller.dispatchMultipleResult(emptyMap())
        val result = attempt.await()

        assertEquals(permissions.toSet(), result.keys)
        assertTrue(result.values.all { it is PermissionResult.Denied && it.canRequestAgain })
        val later = newManager().getPermissionState(Manifest.permission.CAMERA) as PermissionResult.Denied
        assertTrue(later.canRequestAgain)
    }

    @Test
    fun `resume refreshes tracked permissions changed in Settings`() = runTest {
        val manager = newManager()
        manager.getPermissionState(Manifest.permission.CAMERA)

        permissionStates[Manifest.permission.CAMERA] = PackageManager.PERMISSION_GRANTED
        lifecycleOwner.handleResume()

        assertEquals(PermissionResult.Granted, manager.permissionStates.value[Manifest.permission.CAMERA])
    }

    @Test
    fun `requestMedia reports partial access when only user-selected media is granted`() = runTest {
        val manager = newManager()

        val attempt = async(start = CoroutineStart.UNDISPATCHED) { manager.requestMedia() }
        caller.dispatchMultipleResult(
            mapOf(
                Manifest.permission.READ_MEDIA_IMAGES to false,
                Manifest.permission.READ_MEDIA_VIDEO to false,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED to true,
            )
        )

        assertEquals(MediaAccess.Partial, attempt.await())
        assertEquals(
            listOf(
                Manifest.permission.READ_MEDIA_IMAGES,
                Manifest.permission.READ_MEDIA_VIDEO,
                Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED,
            ),
            caller.lastMultiplePermissions,
        )
    }

    @Test
    fun `getMediaAccess reports full access and denial`() {
        val manager = newManager()
        assertTrue(manager.getMediaAccess(includeVideo = false) is MediaAccess.Denied)

        permissionStates[Manifest.permission.READ_MEDIA_IMAGES] = PackageManager.PERMISSION_GRANTED

        assertEquals(MediaAccess.Full, manager.getMediaAccess(includeVideo = false))
    }

    @Test
    @Config(sdk = [32])
    fun `media permissions use external storage before Android 13`() {
        assertEquals(listOf(Manifest.permission.READ_EXTERNAL_STORAGE), mediaPermissions(includeVideo = true))
    }
}
