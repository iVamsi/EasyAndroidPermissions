package com.vamsi.easyandroidpermissions.demo

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.vamsi.easyandroidpermissions.PermissionResult
import com.vamsi.easyandroidpermissions.isGranted
import com.vamsi.easyandroidpermissions.demo.ui.theme.EasyAndroidPermissionsDemoTheme
import com.vamsi.easyandroidpermissions.rememberPermissionManager
import com.vamsi.snapnotify.SnapNotify
import com.vamsi.snapnotify.SnapNotifyProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            EasyAndroidPermissionsDemoTheme {
                // Set up SnapNotify
                SnapNotifyProvider {
                    PermissionDemoScreen()
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PermissionDemoScreen(modifier: Modifier = Modifier) {
    val permissionManager = rememberPermissionManager()
    val scope = rememberCoroutineScope()
    var isLoading by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val trackedStates by permissionManager.permissionStates.collectAsState()
    val commonPermissions = remember {
        buildList {
            add(Manifest.permission.CAMERA to "Camera")
            add(Manifest.permission.RECORD_AUDIO to "Microphone")
            add(Manifest.permission.ACCESS_FINE_LOCATION to "Location")

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT to "Bluetooth")
            } else {
                add(Manifest.permission.ACCESS_COARSE_LOCATION to "Bluetooth (Legacy)")
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.NEARBY_WIFI_DEVICES to "Nearby Wi-Fi")
            }

        }
    }

    // Prime the state flow with current permission states
    LaunchedEffect(Unit) {
        commonPermissions.forEach { (permission, _) ->
            permissionManager.getPermissionState(permission)
        }
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
    ) { paddingValues ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
        Text(
            text = "This demo shows how to use EasyAndroidPermissions to request Android permissions using coroutines in Compose. Watch for snackbar feedback powered by SnapNotify!",
            style = MaterialTheme.typography.bodyMedium
        )

        // Navigation buttons for other demos
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            OutlinedButton(
                onClick = {
                    val intent = Intent(context, TraditionalActivity::class.java)
                    context.startActivity(intent)
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Activity Demo", maxLines = 1)
            }
            
            OutlinedButton(
                onClick = {
                    val intent = Intent(context, FragmentHostActivity::class.java)
                    context.startActivity(intent)
                },
                modifier = Modifier.weight(1f)
            ) {
                Text("Fragment Demo", maxLines = 1)
            }
        }

        // Show welcome message
        LaunchedEffect(Unit) {
            delay(500) // Small delay to let UI settle
            SnapNotify.showInfo("👋 Welcome! Try requesting permissions to see EasyAndroidPermissions in action")
        }

        // Request All Permissions Button
        ElevatedButton(
            onClick = {
                scope.launch {
                    isLoading = true
                    val permissions = commonPermissions.map { it.first }

                    // Only request permissions that are not already granted
                    val currentStatus = permissionManager.getPermissionStates(permissions)
                    val permissionsToRequest = currentStatus
                        .filterValues { !it.isGranted }
                        .keys
                        .toList()

                    if (permissionsToRequest.isNotEmpty()) {
                        val results = permissionManager.requestMultiple(permissionsToRequest)
                        // Show snackbar based on results
                        val grantedCount = results.values.count { it.isGranted }
                        val deniedCount = results.values.count { !it.isGranted }
                        val totalRequested = results.size

                        when {
                            grantedCount == totalRequested -> {
                                SnapNotify.showSuccess("✅ All $totalRequested permissions granted!")
                            }

                            grantedCount > 0 -> {
                                SnapNotify.showWarning("⚠️ $grantedCount of $totalRequested permissions granted. Check individual permissions for details.")
                            }

                            else -> {
                                SnapNotify.showError("❌ All $totalRequested permission requests denied. Tap individual permissions for details.")
                            }
                        }

                        // Check for any potential permanent denials and show hint
                        if (deniedCount > 0) {
                            delay(1000) // Small delay after first message
                            SnapNotify.showInfo("📝 Tip: If a permission was denied twice, you may need to enable it in Settings")
                        }
                    } else {
                        // All permissions are already granted
                        SnapNotify.showSuccess("✅ All permissions are already granted!")
                    }

                    isLoading = false
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !isLoading
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.size(16.dp),
                    strokeWidth = 2.dp
                )
                Spacer(modifier = Modifier.width(8.dp))
            }
            Text("Request All Permissions")
        }

        HorizontalDivider()

        Text(
            text = "Individual Permissions",
            style = MaterialTheme.typography.titleMedium
        )

        // Individual permission items
        commonPermissions.forEach { (permission, displayName) ->
            PermissionItem(
                displayName = displayName,
                state = trackedStates[permission],
                onRequest = {
                    scope.launch {
                        // Request the permission - this will show dialog if not granted
                        val result = permissionManager.request(permission)

                        when (result) {
                            PermissionResult.Granted -> {
                                SnapNotify.showSuccess("✅ $displayName permission granted")
                            }

                            is PermissionResult.Denied -> {
                                when {
                                    result.shouldShowRationale -> {
                                        SnapNotify.showError("❌ $displayName permission denied. Tap to try again.")
                                    }

                                    !result.canRequestAgain -> {
                                        showSettingsDialog = displayName
                                        SnapNotify.showWarning("⚠️ $displayName permission denied permanently. Open settings.")
                                    }

                                    else -> {
                                        SnapNotify.showInfo("ℹ️ $displayName permission denied. Try again or check settings.")
                                    }
                                }
                            }
                        }
                    }
                }
            )
        }
        }
    }

    // Settings Dialog
    showSettingsDialog?.let { permissionName ->
        PermissionSettingsDialog(
            permissionName = permissionName,
            onDismiss = { showSettingsDialog = null },
            onOpenSettings = {
                showSettingsDialog = null
                val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
                context.startActivity(intent)
            }
        )
    }
}

@Composable
fun PermissionSettingsDialog(
    permissionName: String,
    onDismiss: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                text = "Permission Required",
                style = MaterialTheme.typography.headlineSmall
            )
        },
        text = {
            Column {
                Text(
                    text = "$permissionName permission has been permanently denied.",
                    style = MaterialTheme.typography.bodyMedium
                )
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = "To enable it:",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = androidx.compose.ui.text.font.FontWeight.Medium
                )
                Text(
                    text = "1. Tap 'Open Settings'",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "2. Find 'Permissions'",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    text = "3. Enable $permissionName",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onOpenSettings) {
                Text("Open Settings")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}

@Composable
fun PermissionItem(
    displayName: String,
    state: PermissionResult?,
    onRequest: () -> Unit,
) {
    val granted = state?.isGranted == true
    val statusText = when (state) {
        null -> "Not evaluated"
        PermissionResult.Granted -> "Granted"
        is PermissionResult.Denied -> if (state.canRequestAgain) "Denied" else "Denied (Settings needed)"
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    text = statusText,
                    style = MaterialTheme.typography.bodySmall,
                    color = if (granted)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
            }

            if (!granted) {
                Button(onClick = onRequest) {
                    Text("Request")
                }
            } else {
                Text(
                    text = "✓",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.primary
                )
            }
        }
    }
}

@Preview(showBackground = true)
@Composable
fun PermissionDemoPreview() {
    EasyAndroidPermissionsDemoTheme {
        PermissionDemoScreen()
    }
}
