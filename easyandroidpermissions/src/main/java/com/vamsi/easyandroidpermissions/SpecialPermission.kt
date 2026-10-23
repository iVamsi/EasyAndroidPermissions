package com.vamsi.easyandroidpermissions

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.os.PowerManager
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.vamsi.easyandroidpermissions.internal.LifecyclePermissionManager

/**
 * Special Android permissions and App Ops that are granted through dedicated system Settings
 * screens rather than standard runtime permission dialogs.
 */
public enum class SpecialPermission {
    /**
     * `SCHEDULE_EXACT_ALARM` via [Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM] (Android 12+).
     * Declare `<uses-permission android:name="android.permission.SCHEDULE_EXACT_ALARM" />` in your manifest.
     */
    ExactAlarm,

    /**
     * `SYSTEM_ALERT_WINDOW` via [Settings.ACTION_MANAGE_OVERLAY_PERMISSION].
     * Declare `<uses-permission android:name="android.permission.SYSTEM_ALERT_WINDOW" />` in your manifest.
     */
    SystemAlertWindow,

    /**
     * `MANAGE_EXTERNAL_STORAGE` via [Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION] (Android 11+).
     * Declare `<uses-permission android:name="android.permission.MANAGE_EXTERNAL_STORAGE" />` in your manifest.
     * Note: Google Play restricts usage of this permission to approved core use cases (such as file managers).
     */
    ManageExternalStorage,

    /**
     * `REQUEST_IGNORE_BATTERY_OPTIMIZATIONS` via [Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS].
     * Declare `<uses-permission android:name="android.permission.REQUEST_IGNORE_BATTERY_OPTIMIZATIONS" />`
     * in your manifest. Note: Google Play restricts usage of this permission to approved core use cases.
     */
    IgnoreBatteryOptimization,

    /** `BIND_NOTIFICATION_LISTENER_SERVICE` via [Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS]. */
    NotificationListener,

    /**
     * `REQUEST_INSTALL_PACKAGES` via [Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES] (Android 8+).
     * Declare `<uses-permission android:name="android.permission.REQUEST_INSTALL_PACKAGES" />` in your manifest.
     */
    InstallUnknownApps,
}

/**
 * Requests a [SpecialPermission] by launching its dedicated system Settings screen when not already
 * granted, suspending until the user returns, and returning the updated grant state.
 *
 * On devices that omit the dedicated Settings screen (such as Android TV, Wear OS, or certain OEM builds),
 * this falls back to the app details Settings page, or returns `false` if no Settings screen is available.
 */
@androidx.annotation.MainThread
@androidx.annotation.CheckResult
public suspend fun PermissionManager.requestSpecialPermission(
    permission: SpecialPermission
): Boolean {
    val manager = this as? LifecyclePermissionManager
        ?: error("requestSpecialPermission requires the default PermissionManager implementation.")
    return manager.requestSpecial(permission)
}

/**
 * Returns whether the given [SpecialPermission] is currently granted without launching a Settings screen.
 */
public fun PermissionManager.isSpecialPermissionGranted(
    permission: SpecialPermission
): Boolean {
    val manager = this as? LifecyclePermissionManager
        ?: error("isSpecialPermissionGranted requires the default PermissionManager implementation.")
    return manager.isSpecialPermissionGranted(permission)
}

/**
 * Creates the system Settings [Intent] used to grant [permission].
 */
public fun Context.createSpecialPermissionIntent(permission: SpecialPermission): Intent =
    permission.createIntent(this)

internal fun SpecialPermission.isGranted(context: Context): Boolean = when (this) {
    SpecialPermission.ExactAlarm -> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.getSystemService(AlarmManager::class.java)?.canScheduleExactAlarms() == true
        } else {
            true
        }
    }

    SpecialPermission.SystemAlertWindow -> Settings.canDrawOverlays(context)

    SpecialPermission.ManageExternalStorage -> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    SpecialPermission.IgnoreBatteryOptimization -> {
        context.getSystemService(PowerManager::class.java)
            ?.isIgnoringBatteryOptimizations(context.packageName) == true
    }

    SpecialPermission.NotificationListener -> {
        NotificationManagerCompat.getEnabledListenerPackages(context).contains(context.packageName)
    }

    SpecialPermission.InstallUnknownApps -> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.packageManager.canRequestPackageInstalls()
        } else {
            @Suppress("DEPRECATION")
            Settings.Secure.getInt(
                context.contentResolver,
                Settings.Secure.INSTALL_NON_MARKET_APPS,
                0
            ) == 1
        }
    }
}

internal fun SpecialPermission.createIntent(context: Context): Intent = when (this) {
    SpecialPermission.ExactAlarm -> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                data = packageUri(context)
            }
        } else {
            context.createPermissionSettingsIntent()
        }
    }

    SpecialPermission.SystemAlertWindow -> {
        Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION).apply {
            data = packageUri(context)
        }
    }

    SpecialPermission.ManageExternalStorage -> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
        } else {
            context.createPermissionSettingsIntent()
        }
    }

    SpecialPermission.IgnoreBatteryOptimization -> {
        Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS).apply {
            data = packageUri(context)
        }
    }

    SpecialPermission.NotificationListener -> {
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
    }

    SpecialPermission.InstallUnknownApps -> {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES).apply {
                data = packageUri(context)
            }
        } else {
            Intent(Settings.ACTION_SECURITY_SETTINGS)
        }
    }
}

private fun packageUri(context: Context): Uri =
    Uri.fromParts("package", context.packageName, null)
