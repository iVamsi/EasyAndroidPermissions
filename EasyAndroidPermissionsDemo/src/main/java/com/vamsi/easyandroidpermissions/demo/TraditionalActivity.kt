package com.vamsi.easyandroidpermissions.demo

import android.Manifest
import android.os.Bundle
import android.view.View
import android.widget.Button
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.lifecycle.lifecycleScope
import com.vamsi.easyandroidpermissions.PermissionManager
import com.vamsi.easyandroidpermissions.createPermissionManager
import kotlinx.coroutines.launch

/**
 * Demo Activity showing non-Compose usage of EasyAndroidPermissions.
 * 
 * This demonstrates how to use the library in traditional Android Activities
 * without Jetpack Compose.
 */
class TraditionalActivity : AppCompatActivity() {
    
    private lateinit var permissionManager: PermissionManager
    private lateinit var statusText: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_traditional)
        
        // Set up action bar with back navigation
        supportActionBar?.apply {
            setDisplayHomeAsUpEnabled(true)
            setDisplayShowHomeEnabled(true)
            title = "Traditional Activity Demo"
        }
        
        // Initialize PermissionManager using extension function - demonstrates Activity-based permission handling
        permissionManager = this.createPermissionManager()
        
        setupUI()
        updatePermissionStatus()
    }
    
    override fun onSupportNavigateUp(): Boolean {
        onBackPressedDispatcher.onBackPressed()
        return true
    }
    
    private fun setupUI() {
        // Find views from the XML layout
        statusText = findViewById(R.id.status_text)
        
        val cameraButton: Button = findViewById(R.id.camera_button)
        val locationButton: Button = findViewById(R.id.location_button)
        val microphoneButton: Button = findViewById(R.id.microphone_button)
        val multipleButton: Button = findViewById(R.id.multiple_button)
        
        // Set click listeners
        cameraButton.setOnClickListener { requestCameraPermission() }
        locationButton.setOnClickListener { requestLocationPermission() }
        microphoneButton.setOnClickListener { requestMicrophonePermission() }
        multipleButton.setOnClickListener { requestMultiplePermissions() }
        
        // Apply window insets for proper system UI handling
        val rootLayout = findViewById<View>(android.R.id.content)
        ViewCompat.setOnApplyWindowInsetsListener(rootLayout) { view, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            view.setPadding(
                systemBars.left,
                systemBars.top,
                systemBars.right,
                systemBars.bottom
            )
            insets
        }
    }
    
    private fun requestCameraPermission() {
        lifecycleScope.launch {
            try {
                val granted = permissionManager.request(Manifest.permission.CAMERA)
                if (granted) {
                    showToast("✅ Camera permission granted!")
                } else {
                    showToast("❌ Camera permission denied")
                }
                updatePermissionStatus()
            } catch (e: Exception) {
                showToast("Error requesting permission: ${e.message}")
            }
        }
    }
    
    private fun requestLocationPermission() {
        lifecycleScope.launch {
            try {
                val granted = permissionManager.request(Manifest.permission.ACCESS_FINE_LOCATION)
                if (granted) {
                    showToast("✅ Location permission granted!")
                } else {
                    showToast("❌ Location permission denied")
                }
                updatePermissionStatus()
            } catch (e: Exception) {
                showToast("Error requesting permission: ${e.message}")
            }
        }
    }
    
    private fun requestMicrophonePermission() {
        lifecycleScope.launch {
            try {
                val granted = permissionManager.request(Manifest.permission.RECORD_AUDIO)
                if (granted) {
                    showToast("✅ Microphone permission granted!")
                } else {
                    showToast("❌ Microphone permission denied")
                }
                updatePermissionStatus()
            } catch (e: Exception) {
                showToast("Error requesting permission: ${e.message}")
            }
        }
    }
    
    private fun requestMultiplePermissions() {
        lifecycleScope.launch {
            try {
                val permissions = listOf(
                    Manifest.permission.CAMERA,
                    Manifest.permission.RECORD_AUDIO,
                    Manifest.permission.ACCESS_FINE_LOCATION
                )
                
                val results = permissionManager.requestMultiple(permissions)
                val granted = results.values.count { it }
                val total = results.size
                
                if (granted == total) {
                    showToast("✅ All $total permissions granted!")
                } else {
                    showToast("⚠️ $granted of $total permissions granted")
                }
                
                updatePermissionStatus()
            } catch (e: Exception) {
                showToast("Error requesting permissions: ${e.message}")
            }
        }
    }
    
    private fun updatePermissionStatus() {
        val permissions = listOf(
            Manifest.permission.CAMERA to "Camera",
            Manifest.permission.RECORD_AUDIO to "Microphone",
            Manifest.permission.ACCESS_FINE_LOCATION to "Location"
        )
        
        val status = permissions.map { (permission, name) ->
            val granted = permissionManager.isPermissionGranted(permission)
            "$name: ${if (granted) "✅ Granted" else "❌ Not granted"}"
        }.joinToString("\n")
        
        statusText.text = "Permission Status:\n$status"
    }
    
    private fun showToast(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_SHORT).show()
    }
}