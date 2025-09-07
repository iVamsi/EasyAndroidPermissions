package com.vamsi.easyandroidpermissions

import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalContext
import androidx.activity.compose.rememberLauncherForActivityResult
import com.vamsi.easyandroidpermissions.internal.ComposePermissionManager

/**
 * Composable function that creates and remembers a [PermissionManager] instance.
 * 
 * This function sets up the necessary ActivityResultLaunchers for both single and multiple
 * permission requests and provides a coroutine-friendly interface for permission handling.
 * 
 * Usage:
 * ```kotlin
 * @Composable
 * fun MyScreen() {
 *     val permissionManager = rememberPermissionManager()
 *     val scope = rememberCoroutineScope()
 *     
 *     Button(
 *         onClick = {
 *             scope.launch {
 *                 val isGranted = permissionManager.request(Manifest.permission.CAMERA)
 *                 if (isGranted) {
 *                     // Permission granted, proceed with camera
 *                 } else {
 *                     // Permission denied, handle accordingly
 *                 }
 *             }
 *         }
 *     ) {
 *         Text("Request Camera Permission")
 *     }
 * }
 * ```
 * 
 * @return A [PermissionManager] instance that can be used to request permissions
 */
@Composable
fun rememberPermissionManager(): PermissionManager {
    val context = LocalContext.current
    
    // Create the permission manager
    val permissionManager = remember(context) {
        ComposePermissionManager(context)
    }
    
    // Set up single permission launcher
    val singlePermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        permissionManager.onSinglePermissionResult(isGranted)
    }
    
    // Set up multiple permissions launcher
    val multiplePermissionsLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) { results ->
        permissionManager.onMultiplePermissionResult(results)
    }
    
    // Wire up the launchers to the permission manager
    LaunchedEffect(singlePermissionLauncher, multiplePermissionsLauncher) {
        permissionManager.singlePermissionLauncher = { permission ->
            singlePermissionLauncher.launch(permission)
        }
        permissionManager.multiplePermissionsLauncher = { permissions ->
            multiplePermissionsLauncher.launch(permissions)
        }
    }
    
    return permissionManager
}