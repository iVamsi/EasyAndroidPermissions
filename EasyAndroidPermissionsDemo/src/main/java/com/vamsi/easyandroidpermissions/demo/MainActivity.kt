package com.vamsi.easyandroidpermissions.demo

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.core.app.ActivityCompat
import com.vamsi.easyandroidpermissions.rememberPermissionManager
import com.vamsi.easyandroidpermissions.demo.ui.theme.EasyAndroidPermissionsDemoTheme
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
fun PermissionDemoScreen() {
    val permissionManager = rememberPermissionManager()
    val scope = rememberCoroutineScope()
    var permissionResults by remember { mutableStateOf<Map<String, Boolean>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(false) }
    var showSettingsDialog by remember { mutableStateOf<String?>(null) }
    val context = LocalContext.current
    val activity = context as? ComponentActivity

    val commonPermissions =
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            listOf(
                Manifest.permission.CAMERA to "Camera",
                Manifest.permission.RECORD_AUDIO to "Microphone",
                Manifest.permission.ACCESS_FINE_LOCATION to "Location",
                Manifest.permission.BLUETOOTH_CONNECT to "Bluetooth"
            )
        } else {
            listOf(
                Manifest.permission.CAMERA to "Camera",
                Manifest.permission.RECORD_AUDIO to "Microphone",
                Manifest.permission.ACCESS_FINE_LOCATION to "Location",
                Manifest.permission.ACCESS_COARSE_LOCATION to "Bluetooth (Legacy)"
            )
        }

    // Check current permission states
    LaunchedEffect(Unit) {
        val currentStates = commonPermissions.associate { (permission, _) ->
            permission to permissionManager.isPermissionGranted(permission)
        }
        permissionResults = currentStates
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
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
                    val currentStatus = permissionManager.arePermissionsGranted(permissions)
                    val permissionsToRequest = currentStatus.filterValues { !it }.keys.toList()

                    if (permissionsToRequest.isNotEmpty()) {
                        val results = permissionManager.requestMultiple(permissionsToRequest)
                        // Update all permission results
                        permissionResults = permissionResults + currentStatus + results

                        // Show snackbar based on results
                        val grantedCount = results.values.count { it }
                        val deniedCount = results.values.count { !it }
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
                            kotlinx.coroutines.delay(1000) // Small delay after first message
                            SnapNotify.showInfo("📝 Tip: If a permission was denied twice, you may need to enable it in Settings")
                        }
                    } else {
                        // All permissions are already granted, just update the UI
                        permissionResults = permissionResults + currentStatus
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
                isGranted = permissionResults[permission] ?: false,
                onRequest = {
                    scope.launch {
                        // Request the permission - this will show dialog if not granted
                        val result = permissionManager.request(permission)

                        // Update the UI with the result - create new map to trigger recomposition
                        permissionResults = permissionResults.toMutableMap().apply {
                            put(permission, result)
                        }

                        // Show snackbar feedback and handle permanent denial
                        if (result) {
                            SnapNotify.showSuccess("✅ $displayName permission granted")
                        } else {
                            // Check if this is permanent denial
                            val shouldShowRationale = activity?.let {
                                ActivityCompat.shouldShowRequestPermissionRationale(it, permission)
                            } ?: false

                            if (shouldShowRationale) {
                                // User denied but can ask again
                                SnapNotify.showError("❌ $displayName permission denied. Tap to try again.")
                            } else {
                                // User denied with "Don't ask again" OR first denial (Android behavior varies)
                                // Show settings dialog for commonly permanently denied permissions
                                showSettingsDialog = displayName
                                SnapNotify.showWarning("⚠️ $displayName permission denied. Check settings if needed.")
                            }
                        }
                    }
                }
            )
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
    isGranted: Boolean,
    onRequest: () -> Unit,
) {
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
                    text = if (isGranted) "Granted" else "Not granted",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (isGranted)
                        MaterialTheme.colorScheme.primary
                    else
                        MaterialTheme.colorScheme.error
                )
            }

            if (!isGranted) {
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
