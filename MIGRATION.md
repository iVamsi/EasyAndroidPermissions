# Migrating to 2.0.0

Version **2.0.0** is a **major** release: Maven coordinates changed and the `PermissionManager` API moved from `Boolean`-based results to `PermissionResult`. Upgrade when you are ready to update dependencies and call sites.

## 1. Dependencies

**Before (1.x single artifact):**

```kotlin
implementation("io.github.ivamsi:easyandroidpermissions:1.0.2")
```

**After (2.0.0 split artifacts):**

```kotlin
// View / Activity / Fragment only
implementation("io.github.ivamsi:easyandroidpermissions-core:2.0.0")

// Jetpack Compose (transitively includes -core)
implementation("io.github.ivamsi:easyandroidpermissions-compose:2.0.0")
```

Import paths for the main types stay under `com.vamsi.easyandroidpermissions`. Compose entry points still use `rememberPermissionManager()` from that package, but the artifact is now `-compose`.

## 2. Request APIs

| 1.x | 2.0.0 |
|-----|--------|
| `suspend fun request(permission: String): Boolean` | `suspend fun request(permission: String): PermissionResult` |
| `suspend fun requestMultiple(permissions: List<String>): Map<String, Boolean>` | `Map<String, PermissionResult>` |

**Before:**

```kotlin
if (permissionManager.request(Manifest.permission.CAMERA)) {
    openCamera()
} else {
    showDenied()
}
```

**After:**

```kotlin
when (val result = permissionManager.request(Manifest.permission.CAMERA)) {
    PermissionResult.Granted -> openCamera()
    is PermissionResult.Denied -> handleDenied(result)
}
```

For bulk requests, use `it.value.isGranted` (extension on `PermissionResult`) instead of using the map value as a `Boolean` directly.

## 3. Status checks

| 1.x | 2.0.0 |
|-----|--------|
| `isPermissionGranted(permission)` | `getPermissionState(permission)` and test `isGranted`, or use `when` on `PermissionResult` |
| `arePermissionsGranted(permissions)` | `getPermissionStates(permissions)` |

**Before:**

```kotlin
if (permissionManager.isPermissionGranted(Manifest.permission.CAMERA)) { ... }
```

**After:**

```kotlin
if (permissionManager.getPermissionState(Manifest.permission.CAMERA).isGranted) { ... }
```

## 4. Rationale and “ask again”

2.0.0 adds:

- `shouldShowRationale(permission)`
- `canRequestAgain(permission)` on `PermissionManager`
- `PermissionResult.Denied(canRequestAgain, shouldShowRationale)`

Use these to align with [runtime permission guidance](https://developer.android.com/training/permissions/requesting) and to deep-link to app settings via `createPermissionSettingsIntent()` when the user must grant permission manually.

## 5. Observation

`PermissionManager.permissionStates` exposes a `StateFlow<Map<String, PermissionResult>>` of states the library has learned. This is new in 2.0.0 and is optional for migration.

## 6. Factory extensions

`PermissionManagerFactory.create(ComponentActivity)` and `create(Fragment)` behave the same conceptually; implementations are unified internally. Additional overloads exist for custom `LifecycleOwner` + `ActivityResultCaller` hosts—see the factory and README.

---

If anything in this guide does not match your app after upgrading, open an issue on the project repository with the dependency snippet and the failing call site.
