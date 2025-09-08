package com.vamsi.easyandroidpermissions.demo

import android.Manifest
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.vamsi.easyandroidpermissions.PermissionManager
import com.vamsi.easyandroidpermissions.createPermissionManager
import kotlinx.coroutines.launch

/**
 * Demo Fragment showing FragmentPermissionManager usage of EasyAndroidPermissions.
 * 
 * This demonstrates how to use FragmentPermissionManager in traditional Android Fragments
 * without Jetpack Compose, with proper Fragment lifecycle integration.
 */
class DemoFragment : Fragment() {
    
    private lateinit var permissionManager: PermissionManager
    private lateinit var statusText: TextView
    
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize PermissionManager using extension function - demonstrates Fragment-based permission handling
        permissionManager = this.createPermissionManager()
    }
    
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        return inflater.inflate(R.layout.fragment_demo, container, false)
    }
    
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        
        // Find views from the XML layout
        statusText = view.findViewById(R.id.status_text)
        
        val cameraButton: Button = view.findViewById(R.id.camera_button)
        val locationButton: Button = view.findViewById(R.id.location_button)
        val microphoneButton: Button = view.findViewById(R.id.microphone_button)
        val multipleButton: Button = view.findViewById(R.id.multiple_button)
        
        // Set click listeners
        cameraButton.setOnClickListener { requestCameraPermission() }
        locationButton.setOnClickListener { requestLocationPermission() }
        microphoneButton.setOnClickListener { requestMicrophonePermission() }
        multipleButton.setOnClickListener { requestMultiplePermissions() }
        
        updatePermissionStatus()
    }
    
    private fun requestCameraPermission() {
        // Using viewLifecycleOwner to properly handle Fragment lifecycle
        viewLifecycleOwner.lifecycleScope.launch {
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
        viewLifecycleOwner.lifecycleScope.launch {
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
        viewLifecycleOwner.lifecycleScope.launch {
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
        viewLifecycleOwner.lifecycleScope.launch {
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
        Toast.makeText(requireContext(), message, Toast.LENGTH_SHORT).show()
    }
    
    companion object {
        fun newInstance() = DemoFragment()
    }
}