# EasyAndroidPermissions 🔐

[![Kotlin](https://img.shields.io/badge/Kotlin-2.0.0+-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Compose-BOM%202025.08.01+-blue.svg)](https://developer.android.com/jetpack/compose)
[![Android](https://img.shields.io/badge/Android-API%2024+-green.svg)](https://android-arsenal.com/api?level=24)
[![License](https://img.shields.io/badge/License-Apache%202.0-orange.svg)](https://opensource.org/licenses/Apache-2.0)
[![Maven Central](https://img.shields.io/badge/Maven%20Central-1.0.1-red.svg)](https://central.sonatype.com/artifact/io.github.ivamsi/easyandroidpermissions/1.0.1)

A lightweight Android library that bridges the gap between ActivityResultContracts permission API and Kotlin Coroutines, enabling developers to request permissions using clean, sequential suspend functions in both traditional Android components (Activities/Fragments) and Jetpack Compose applications.

## Features ✨

- **Coroutine-First**: Use suspend functions for permission requests
- **Multiple Contexts**: Works with Activities, Fragments, and Compose
- **Compose Integration**: Seamless integration with Jetpack Compose
- **Thread-Safe**: Handle concurrent permission requests correctly
- **Lifecycle-Aware**: Proper integration with Android lifecycle
- **Zero Boilerplate**: No need for callback management
- **Memory Efficient**: Optimized for performance with proper resource cleanup

## Installation 📦

Add the dependency to your `build.gradle.kts` file:

```kotlin
dependencies {
    implementation("io.github.ivamsi:easyandroidpermissions:1.0.1")
}
```

## Quick Start 🚀

### Activity Usage

```kotlin
class MainActivity : ComponentActivity() {
    private lateinit var permissionManager: PermissionManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create permission manager
        permissionManager = this.createPermissionManager()
        // Or: permissionManager = PermissionManagerFactory.create(this)
        
        findViewById<Button>(R.id.cameraButton).setOnClickListener {
            lifecycleScope.launch {
                val isGranted = permissionManager.request(Manifest.permission.CAMERA)
                if (isGranted) {
                    // Permission granted - proceed with camera functionality
                    openCamera()
                } else {
                    // Permission denied - show user feedback
                    showPermissionDeniedMessage()
                }
            }
        }
    }
}
```

### Fragment Usage

```kotlin
class CameraFragment : Fragment() {
    private lateinit var permissionManager: PermissionManager
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Create permission manager
        permissionManager = this.createPermissionManager()
        // Or: permissionManager = PermissionManagerFactory.create(this)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        binding.recordButton.setOnClickListener {
            viewLifecycleOwner.lifecycleScope.launch {
                val permissions = listOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO
                )
                
                val results = permissionManager.requestMultiple(permissions)
                val allGranted = results.values.all { it }
                
                if (allGranted) {
                    startRecording()
                } else {
                    val denied = results.filterValues { !it }.keys
                    handleDeniedPermissions(denied)
                }
            }
        }
    }
}
```

### Compose Usage

```kotlin
@Composable
fun CameraScreen() {
    val permissionManager = rememberPermissionManager()
    val scope = rememberCoroutineScope()
    
    Button(
        onClick = {
            scope.launch {
                val isGranted = permissionManager.request(Manifest.permission.CAMERA)
                if (isGranted) {
                    // Permission granted - proceed with camera functionality
                    openCamera()
                } else {
                    // Permission denied - show user feedback
                    showPermissionDeniedMessage()
                }
            }
        }
    ) {
        Text("Open Camera")
    }
}
```

### Multiple Permissions (Works in all contexts)

```kotlin
// In Activity, Fragment, or Compose - same API!
val permissions = listOf(
    Manifest.permission.CAMERA,
    Manifest.permission.RECORD_AUDIO,
    Manifest.permission.WRITE_EXTERNAL_STORAGE
)

val results = permissionManager.requestMultiple(permissions)
val allGranted = results.values.all { it }

if (allGranted) {
    // All permissions granted
    startMediaRecording()
} else {
    // Handle denied permissions
    val denied = results.filterValues { !it }.keys
    handleDeniedPermissions(denied)
}
```

### Check Permission Status (Works in all contexts)

```kotlin
// Check single permission
val hasCameraPermission = permissionManager.isPermissionGranted(Manifest.permission.CAMERA)

// Check multiple permissions
val permissions = listOf(
    Manifest.permission.CAMERA,
    Manifest.permission.RECORD_AUDIO
)
val permissionStatus = permissionManager.arePermissionsGranted(permissions)

// Use the results as needed
if (hasCameraPermission) {
    // Camera is available
} else {
    // Request camera permission
}
```

## API Reference 📚

### PermissionManager Interface

```kotlin
interface PermissionManager {
    /**
     * Requests a single runtime permission.
     * @param permission The permission to request
     * @return true if granted, false if denied
     */
    suspend fun request(permission: String): Boolean
    
    /**
     * Requests multiple runtime permissions.
     * @param permissions List of permissions to request
     * @return Map of permissions to their granted status
     */
    suspend fun requestMultiple(permissions: List<String>): Map<String, Boolean>
    
    /**
     * Checks if a permission is currently granted.
     * @param permission The permission to check
     * @return true if granted, false otherwise
     */
    fun isPermissionGranted(permission: String): Boolean
    
    /**
     * Checks multiple permissions' granted status.
     * @param permissions List of permissions to check
     * @return Map of permissions to their granted status
     */
    fun arePermissionsGranted(permissions: List<String>): Map<String, Boolean>
}
```

### Factory Methods

```kotlin
// Extension functions for easy creation
fun ComponentActivity.createPermissionManager(): PermissionManager
fun Fragment.createPermissionManager(): PermissionManager

// Factory methods
PermissionManagerFactory.create(activity: ComponentActivity): PermissionManager
PermissionManagerFactory.create(fragment: Fragment): PermissionManager
```

### Composable Functions

```kotlin
/**
 * Creates and remembers a PermissionManager instance.
 * Must be called within a Composable context.
 */
@Composable
fun rememberPermissionManager(): PermissionManager
```

## Key Benefits 🌟

### Before (Traditional Approach)
```kotlin
class MainActivity : ComponentActivity() {
    private val requestPermissionLauncher = 
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { isGranted: Boolean ->
            if (isGranted) {
                // Permission granted
            } else {
                // Permission denied
            }
        }
    
    private fun requestCameraPermission() {
        when {
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == 
                PackageManager.PERMISSION_GRANTED -> {
                // Permission already granted
            }
            shouldShowRequestPermissionRationale(Manifest.permission.CAMERA) -> {
                // Show rationale
            }
            else -> {
                requestPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }
    }
}
```

### After (With EasyAndroidPermissions)
```kotlin
// Works the same in Activity, Fragment, or Compose!
lifecycleScope.launch { // or viewLifecycleOwner.lifecycleScope in Fragment
    if (permissionManager.request(Manifest.permission.CAMERA)) {
        // Permission granted
    } else {
        // Permission denied
    }
}
```

## Advanced Usage 🔧

### Error Handling

```kotlin
scope.launch {
    try {
        val isGranted = permissionManager.request(Manifest.permission.CAMERA)
        if (isGranted) {
            openCamera()
        } else {
            showPermissionEducation()
        }
    } catch (e: Exception) {
        // Handle any unexpected errors
        Log.e("Permission", "Error requesting permission", e)
    }
}
```

### Conditional Permission Requests

```kotlin
scope.launch {
    // Only request if not already granted
    if (!permissionManager.isPermissionGranted(Manifest.permission.LOCATION)) {
        val isGranted = permissionManager.request(Manifest.permission.LOCATION)
        if (isGranted) {
            startLocationUpdates()
        }
    } else {
        // Already granted
        startLocationUpdates()
    }
}
```

## Best Practices 📋

1. **Always check permissions before requesting**: The library optimizes by checking current status first, but explicit checks make your intent clear.

2. **Handle permission denials gracefully**: Provide alternative functionality or clear explanations when permissions are denied.

3. **Request permissions contextually**: Request permissions when the user initiates an action that requires them, not upfront.

4. **Use the minimal required permissions**: Only request permissions your app actually needs.

## Requirements 📋

- **Minimum SDK**: API 24 (Android 7.0)
- **Kotlin**: 2.0.0 or higher
- **Jetpack Compose**: BOM 2025.08.01 or higher
- **Coroutines**: 1.7.0 or higher

## Sample App 📱

Check out the [EasyAndroidPermissionsDemo](./EasyAndroidPermissionsDemo) module for a complete sample application demonstrating various use cases:

- **Traditional Activity Demo**: XML layouts with ActivityPermissionManager
- **Fragment Demo**: XML layouts with FragmentPermissionManager
- **Individual Permission Requests**: Camera, microphone, location permissions
- **Multiple Permission Requests**: Request multiple permissions at once
- **Permission Status Tracking**: Real-time status display
- **Proper Lifecycle Integration**: Activity and Fragment lifecycle handling

## Contributing 🤝

Contributions are welcome! Please feel free to submit a Pull Request. For major changes, please open an issue first to discuss what you would like to change.

## License 📄

```
Copyright 2025 Vamsi Vaddavalli

Licensed under the Apache License, Version 2.0 (the "License");
you may not use this file except in compliance with the License.
You may obtain a copy of the License at

    http://www.apache.org/licenses/LICENSE-2.0

Unless required by applicable law or agreed to in writing, software
distributed under the License is distributed on an "AS IS" BASIS,
WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
See the License for the specific language governing permissions and
limitations under the License.
```

---

**EasyAndroidPermissions** - *Making Android permissions simple, clean, and coroutine-friendly* 🚀