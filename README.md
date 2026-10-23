# EasyAndroidPermissions

[![Android Weekly](https://androidweekly.net/issues/issue-694/badge)](https://androidweekly.net/issues/issue-694)
[![Maven Central](https://img.shields.io/badge/Maven%20Central-2.3.1-red.svg)](https://central.sonatype.com/artifact/io.github.ivamsi/easyandroidpermissions-core/2.3.1)
[![License](https://img.shields.io/badge/License-Apache%202.0-orange.svg)](LICENSE)

Request Android runtime permissions with Kotlin suspend functions instead of managing result callbacks. Use the same PermissionManager API in Jetpack Compose, Activities, and Fragments.

## Installation

Make `google()` and `mavenCentral()` available in your dependency repositories. Choose the artifact for your UI:

```kotlin
// Compose apps: includes the core API transitively.
implementation("io.github.ivamsi:easyandroidpermissions-compose:2.3.1")
```

```kotlin
// View/XML apps: no Compose artifact required.
implementation("io.github.ivamsi:easyandroidpermissions-core:2.3.1")
```

Add the selected line inside your application module's `dependencies` block. Upgrading from 1.x? Read [MIGRATION.md](MIGRATION.md).

## Quick start: Jetpack Compose

Declare the permission your feature needs in AndroidManifest.xml, outside the application element:

```xml
<uses-permission android:name="android.permission.CAMERA" />
```

Create the manager in a Composable and launch the request from a user action:

```kotlin
@Composable
fun CameraPermissionButton(
    onGranted: () -> Unit,
    onDenied: (PermissionResult.Denied) -> Unit
) {
    val manager = rememberPermissionManager()
    val scope = rememberCoroutineScope()

    Button(onClick = {
        scope.launch {
            when (val result = manager.request(Manifest.permission.CAMERA)) {
                PermissionResult.Granted -> onGranted()
                is PermissionResult.Denied -> onDenied(result)
            }
        }
    }) {
        Text("Enable camera")
    }
}
```

This snippet belongs in your existing Compose application. Supply application callbacks to start the camera and render denial or rationale UI. It does not implement a camera preview.

## Why use this library?

| Need | Approach |
| --- | --- |
| A simple permission callback already works for your app | Keep the native Activity Result API; another dependency is optional. |
| Sequential coroutine-based permission flows | Await `request()` or `requestMultiple()` and branch on the result. |
| Shared permission handling across Compose and Views | Use the same core API with the appropriate lifecycle integration. |
| Permission status reflected in UI | Observe `permissionStates` and explicitly query permissions you need tracked. |

The library wraps permission requests. Your application still owns manifest declarations, user education, fallback behavior, and platform-specific permission policy.

## Handle denial and lifecycle correctly

- Use `shouldShowRationale` to decide when explanatory UI is appropriate.
- If `canRequestAgain` is false, offer a user-initiated settings path or an alternative feature flow rather than repeatedly requesting.
- Request only permissions needed for the action the user selected.
- Call request APIs on the main thread from a lifecycle-bound coroutine scope.
- When `rememberPermissionManager()` leaves the composition, pending requests are cancelled and later requests on that manager throw IllegalStateException. Do not retain it beyond its owner.

## Activities and Fragments

Create the manager in `onCreate`:

```kotlin
// Inside a ComponentActivity or Fragment:
val manager = this.createPermissionManager()
```

Keep the manager as a field if callbacks need it later. From an Activity, launch requests in `lifecycleScope`; from Fragment view callbacks, use `viewLifecycleOwner.lifecycleScope` after the view has been created. See the [sample app](EasyAndroidPermissionsDemo) for full integration.

## Camera and microphone

Declare both CAMERA and RECORD_AUDIO in your manifest. Inside your coroutine:

```kotlin
val results = manager.requestMultiple(
    listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
)
val denied = results.filterValues { !it.isGranted }
if (denied.isEmpty()) {
    // Start your recording feature.
} else {
    // Explain unavailable functionality or offer a fallback.
}
```

## Query and observe permission state

```kotlin
val camera = manager.getPermissionState(Manifest.permission.CAMERA)
val states = manager.getPermissionStates(
    listOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO)
)

// Inside a Composable:
val trackedStates by manager.permissionStates.collectAsState()
```

`permissionStates` is a StateFlow of tracked permission results. Queries and requests populate tracked state. Tracked permissions are checked again when the host resumes, including after returning from Settings.

## Photos and videos: partial access

Use `requestMedia()` when your feature needs to distinguish full, partial, and denied media access:

```kotlin
when (val access = manager.requestMedia(includeVideo = true)) {
    MediaAccess.Full -> { /* Show all accessible media. */ }
    MediaAccess.Partial -> { /* Show only selected media. */ }
    is MediaAccess.Denied -> { /* Explain denial or offer a fallback. */ }
}

val currentAccess = manager.getMediaAccess()
```

For partial access on Android 14+, declare READ_MEDIA_VISUAL_USER_SELECTED alongside READ_MEDIA_IMAGES, and READ_MEDIA_VIDEO if needed. Review the sample manifest for version-specific declarations. A plain `request(READ_MEDIA_IMAGES)` does not distinguish partial access as a grant.

## Declarative Compose (`rememberPermissionState` & `PermissionRequired`)

For state-driven Compose UIs (including apps migrating from Accompanist Permissions), use `rememberPermissionState()` or `PermissionRequired()`:

```kotlin
@Composable
fun CameraFeatureScreen() {
    val cameraPermission = rememberPermissionState(Manifest.permission.CAMERA)

    if (cameraPermission.isGranted) {
        CameraPreview()
    } else {
        Button(onClick = { cameraPermission.launchPermissionRequest() }) {
            Text("Grant Camera Permission")
        }
    }
}

@Composable
fun DeclarativeCameraSection() {
    PermissionRequired(
        permission = Manifest.permission.CAMERA,
        onGranted = { CameraPreview() },
        onRationale = { shouldShowRationale, onRequest ->
            Column {
                if (shouldShowRationale) {
                    Text("Camera access is needed to scan QR codes.")
                }
                Button(onClick = onRequest) { Text("Allow Camera") }
            }
        },
        onDenied = { _, onOpenSettings ->
            Button(onClick = onOpenSettings) { Text("Enable Camera in Settings") }
        }
    )
}
```

## Special permissions & App Ops

Permissions granted on a dedicated system Settings screen (such as exact alarms, overlay windows, or all-files access) can be checked and requested with `SpecialPermission`:

```kotlin
val canDrawOverlays = manager.isSpecialPermissionGranted(
    SpecialPermission.SystemAlertWindow
)

val granted = manager.requestSpecialPermission(
    SpecialPermission.ExactAlarm
)
```

## Version-aware permission presets

Use `PermissionPresets` to request version-appropriate permission bundles without writing SDK version checks:

```kotlin
val nearbyResults = manager.requestMultiple(PermissionPresets.nearbyDevices())
val locationResults = manager.requestMultiple(PermissionPresets.location(precise = true))
val avResults = manager.requestMultiple(PermissionPresets.cameraAndMicrophone())
```

## Core API

```kotlin
interface PermissionManager {
    val permissionStates: StateFlow<Map<String, PermissionResult>>
    suspend fun request(permission: String): PermissionResult
    suspend fun requestMultiple(permissions: List<String>): Map<String, PermissionResult>
    fun getPermissionState(permission: String): PermissionResult
    fun getPermissionStates(permissions: List<String>): Map<String, PermissionResult>
    fun shouldShowRationale(permission: String): Boolean
    fun canRequestAgain(permission: String): Boolean
}

sealed interface PermissionResult {
    data object Granted : PermissionResult
    data class Denied(
        val canRequestAgain: Boolean,
        val shouldShowRationale: Boolean
    ) : PermissionResult
}
```

Factory entry points include `ComponentActivity.createPermissionManager()`, `Fragment.createPermissionManager()`, and `PermissionManagerFactory.create(activity)` or `create(fragment)`.

## Requirements and compatibility

- Minimum Android SDK: API 24.
- Kotlin: 2.3.20 or higher; repository uses AGP 9 built-in Kotlin.
- Compose: BOM 2026.03.01 or higher for Compose integration.
- Coroutines: 1.7.0 or higher.

The core artifact does not require the Compose artifact. Compatibility has not been verified across every toolchain combination or with older toolchains. Validate the published dependency in your app before rollout.

## Try the sample

The [EasyAndroidPermissionsDemo](EasyAndroidPermissionsDemo) module contains Activity, Fragment, individual-request, multiple-request, and permission-state examples.

## Report an integration problem

[Open an issue](https://github.com/iVamsi/EasyAndroidPermissions/issues) with the library version, Android API and device, Kotlin/AGP/Compose versions, a minimal reproducer, and expected versus actual behavior. Include the permission and lifecycle events involved, but no private application data or secrets.

Contributions are welcome. Discuss major changes in an issue before opening a pull request.

## License

Copyright 2025 Vamsi Vaddavalli

Licensed under the [Apache License, Version 2.0](LICENSE). See LICENSE for the full terms, conditions, and warranty disclaimer.
