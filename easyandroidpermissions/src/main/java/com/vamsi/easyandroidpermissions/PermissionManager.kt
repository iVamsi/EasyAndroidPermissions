package com.vamsi.easyandroidpermissions

/**
 * Interface for managing Android runtime permissions using coroutines.
 * 
 * This interface provides suspend functions to request permissions in a sequential,
 * coroutine-friendly manner within Compose applications.
 */
interface PermissionManager {
    
    /**
     * Requests a single runtime permission.
     * 
     * @param permission The permission to request (e.g., Manifest.permission.CAMERA)
     * @return true if the permission is granted, false if denied
     */
    suspend fun request(permission: String): Boolean
    
    /**
     * Requests multiple runtime permissions.
     * 
     * @param permissions List of permissions to request
     * @return Map of permissions to their granted status
     */
    suspend fun requestMultiple(permissions: List<String>): Map<String, Boolean>
    
    /**
     * Checks if a permission is currently granted.
     * 
     * @param permission The permission to check
     * @return true if granted, false if not granted
     */
    fun isPermissionGranted(permission: String): Boolean
    
    /**
     * Checks if multiple permissions are currently granted.
     * 
     * @param permissions List of permissions to check
     * @return Map of permissions to their granted status
     */
    fun arePermissionsGranted(permissions: List<String>): Map<String, Boolean>
}