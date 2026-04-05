package com.vamsi.easyandroidpermissions

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.platform.LocalContext
import androidx.core.app.ActivityCompat
import com.vamsi.easyandroidpermissions.compose.internal.ComposePermissionManager
import com.vamsi.easyandroidpermissions.internal.findComponentActivity

/**
 * Creates and remembers a [PermissionManager] implementation that works inside Jetpack Compose.
 */
@Composable
fun rememberPermissionManager(): PermissionManager {
    val context = LocalContext.current
    val contextState = rememberUpdatedState(context)

    val permissionManager = remember(context.applicationContext) {
        ComposePermissionManager(context.applicationContext)
    }

    val singleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permissionManager.onSinglePermissionResult(isGranted)
    }

    val multipleLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionManager.onMultiplePermissionResult(results)
    }

    DisposableEffect(permissionManager, singleLauncher, multipleLauncher) {
        permissionManager.singlePermissionLauncher = { permission ->
            singleLauncher.launch(permission)
        }
        permissionManager.multiplePermissionsLauncher = { permissions ->
            multipleLauncher.launch(permissions)
        }
        permissionManager.rationaleProvider = { permission ->
            val host = contextState.value.findComponentActivity()
            host?.let { ActivityCompat.shouldShowRequestPermissionRationale(it, permission) } ?: false
        }

        onDispose {
            permissionManager.detach()
        }
    }

    return permissionManager
}

