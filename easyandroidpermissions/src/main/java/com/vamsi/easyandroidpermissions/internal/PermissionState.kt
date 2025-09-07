package com.vamsi.easyandroidpermissions.internal

import kotlinx.coroutines.CompletableDeferred

/**
 * Represents the state of a permission request.
 */
internal data class PermissionRequest(
    val permission: String,
    val deferred: CompletableDeferred<Boolean>
)

/**
 * Represents the state of multiple permission requests.
 */
internal data class MultiplePermissionRequest(
    val permissions: List<String>,
    val deferred: CompletableDeferred<Map<String, Boolean>>
)