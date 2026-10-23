package com.vamsi.easyandroidpermissions.internal

import kotlinx.coroutines.CompletableDeferred

/**
 * Represents the state of a permission request.
 */
internal data class PermissionRequest(
    val permission: String,
    val deferred: CompletableDeferred<com.vamsi.easyandroidpermissions.PermissionResult>
)

/**
 * Represents the state of multiple permission requests.
 */
internal data class MultiplePermissionRequest(
    val permissions: List<String>,
    val deferred: CompletableDeferred<Map<String, com.vamsi.easyandroidpermissions.PermissionResult>>
)

/**
 * Represents the state of a special (Settings-based) permission request.
 */
internal data class SpecialPermissionRequest(
    val permission: com.vamsi.easyandroidpermissions.SpecialPermission,
    val deferred: CompletableDeferred<Boolean>
)
