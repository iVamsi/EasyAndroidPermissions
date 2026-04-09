# EasyAndroidPermissions 🔐

[![Android Weekly](https://androidweekly.net/issues/issue-694/badge)](https://androidweekly.net/issues/issue-694)
[![Kotlin](https://img.shields.io/badge/Kotlin-2.3.20+-purple.svg)](https://kotlinlang.org)
[![Compose](https://img.shields.io/badge/Compose-BOM%202026.03.01+-blue.svg)](https://developer.android.com/jetpack/compose)
[![Android](https://img.shields.io/badge/Android-API%2024+-green.svg)](https://android-arsenal.com/api?level=24)
[![License](https://img.shields.io/badge/License-Apache%202.0-orange.svg)](https://opensource.org/licenses/Apache-2.0)
[![Maven Central](https://img.shields.io/badge/Maven%20Central-2.1.0-red.svg)](https://central.sonatype.com/artifact/io.github.ivamsi/easyandroidpermissions-core/2.1.0)

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

Add the dependencies to your `build.gradle.kts` file:

```kotlin
dependencies {
    // Non-Compose apps: include only this line
    implementation("io.github.ivamsi:easyandroidpermissions-core:2.1.0")

    // Compose apps: include this line (it already pulls in -core transitively)
    implementation("io.github.ivamsi:easyandroidpermissions-compose:2.1.0")
}
```

**Upgrading from 1.x?** See [MIGRATION.md](./MIGRATION.md) for coordinates, API changes, and code patterns.

- **Only XML / View-based UI?** Keep just the `-core` line.
- **Compose UI?** You can add only the `-compose` line because it has an `api` dependency on `-core`, or keep both lines if you want the explicit documentation-style block.

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
                when (val result = permissionManager.request(Manifest.permission.CAMERA)) {
                    PermissionResult.Granted -> {
                        // Permission granted - proceed with camera functionality
                        openCamera()
                    }
                    is PermissionResult.Denied -> {
                        if (!result.canRequestAgain) {
                            showSettingsPrompt()
                        } else if (result.shouldShowRationale) {
                            showPermissionEducation()
                        }
                    }
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
                val denied = results.filterValues { !it.isGranted }
                
                if (denied.isEmpty()) {
                    startRecording()
                } else {
                    handleDeniedPermissions(denied.keys)
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
                when (val result = permissionManager.request(Manifest.permission.CAMERA)) {
                    PermissionResult.Granted -> openCamera()
                    is PermissionResult.Denied -> {
                        if (!result.canRequestAgain) {
                            showSettingsPrompt()
                        } else {
                            showPermissionDeniedMessage()
                        }
                    }
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
val allGranted = results.values.all { it.isGranted }

if (allGranted) {
    // All permissions granted
    startMediaRecording()
} else {
    // Handle denied permissions
    val denied = results.filterValues { !it.isGranted }.keys
    handleDeniedPermissions(denied)
}
```

### Check Permission Status (Works in all contexts)

```kotlin
// Check single permission
val cameraState = permissionManager.getPermissionState(Manifest.permission.CAMERA)
if (cameraState.isGranted) {
    startCamera()
} else if (cameraState is PermissionResult.Denied && !cameraState.canRequestAgain) {
    showSettingsPrompt()
}

// Check multiple permissions
val permissions = listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
val permissionStatus = permissionManager.getPermissionStates(permissions)
val denied = permissionStatus.filterValues { !it.isGranted }.keys

// Observe tracked states (Compose example)
val trackedStates by permissionManager.permissionStates.collectAsState()
```

`permissionStates` is a cold `StateFlow` that emits whenever EasyAndroidPermissions learns about a new permission state (e.g., after a request or an explicit `getPermissionState()` call). It plugs directly into Compose via `collectAsState()` or into View-based UIs via `lifecycleScope.launch { permissionStates.collect { … } }`.

## API Reference 📚

### PermissionManager Interface

```kotlin
interface PermissionManager {
    val permissionStates: StateFlow<Map<String, PermissionResult>>

    @MainThread
    @CheckResult
    suspend fun request(permission: String): PermissionResult

    @MainThread
    @CheckResult
    suspend fun requestMultiple(permissions: List<String>): Map<String, PermissionResult>

    fun getPermissionState(permission: String): PermissionResult
    fun getPermissionStates(permissions: List<String>): Map<String, PermissionResult>

    fun shouldShowRationale(permission: String): Boolean
    fun canRequestAgain(permission: String): Boolean
}
```

### PermissionResult

```kotlin
sealed interface PermissionResult {
    data object Granted : PermissionResult
    data class Denied(
        val canRequestAgain: Boolean,
        val shouldShowRationale: Boolean
    ) : PermissionResult
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
PermissionManagerFactory.create(
    lifecycleOwner: LifecycleOwner,
    caller: ActivityResultCaller,
    contextProvider: () -> Context?,
    rationaleProvider: (String) -> Boolean = { false }
): PermissionManager
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
    when (permissionManager.request(Manifest.permission.CAMERA)) {
        PermissionResult.Granted -> { /* proceed */ }
        is PermissionResult.Denied -> { /* explain or open settings */ }
    }
}
```

## Advanced Usage 🔧

### Error Handling

```kotlin
scope.launch {
    try {
        when (val result = permissionManager.request(Manifest.permission.CAMERA)) {
            PermissionResult.Granted -> openCamera()
            is PermissionResult.Denied -> showPermissionEducation()
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
    if (!permissionManager.getPermissionState(Manifest.permission.LOCATION).isGranted) {
        when (permissionManager.request(Manifest.permission.LOCATION)) {
            PermissionResult.Granted -> startLocationUpdates()
            is PermissionResult.Denied -> { /* handle */ }
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

### Android 14 & 15 Updates

- Android 13+ introduces `NEARBY_WIFI_DEVICES`; Android 14 adds background sensor gating via `BODY_SENSORS_BACKGROUND`. Declare these permissions (see the demo manifest) and request them only on supported API levels.
- When `PermissionResult.Denied.canRequestAgain` is `false`, Google expects you to route the user to Settings using `Context.createPermissionSettingsIntent()`. Follow the [official guidance](https://developer.android.com/training/permissions/requesting).
- Android 15 special cases (e.g., `SCHEDULE_EXACT_ALARM`, background sensors) sometimes require additional UX or policy disclosures. Review the [Android 15 behavior changes](https://developer.android.com/about/versions/15/behavior-changes-all#runtime-permissions) before shipping.

## Requirements 📋

- **Minimum SDK**: API 24 (Android 7.0)
- **Kotlin**: 2.3.20 or higher (AGP 9 built-in Kotlin)
- **Jetpack Compose**: BOM 2026.03.01 or higher
- **Coroutines**: 1.7.0 or higher

## Sample App 📱

Check out the [EasyAndroidPermissionsDemo](./EasyAndroidPermissionsDemo) module for a complete sample application demonstrating various use cases:

- **Traditional Activity Demo**: XML layouts with the lifecycle-aware permission manager
- **Fragment Demo**: XML layouts with the same lifecycle-aware APIs
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