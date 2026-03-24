// utils/PermissionManager.kt
package com.example.uplyft.utils


import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment

class PermissionManager(private val fragment: Fragment) {

    companion object {
        val GALLERY_PERMISSION = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.READ_MEDIA_IMAGES
        else
            Manifest.permission.READ_EXTERNAL_STORAGE

        val CAMERA_PERMISSION = Manifest.permission.CAMERA
        val NOTIFICATION_PERMISSION = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
            Manifest.permission.POST_NOTIFICATIONS
        else
            null

        private const val PREF_NAME = "permission_denials"
        private const val MAX_DENIALS = 2
    }

    private var onGranted: (() -> Unit)? = null
    private var onDenied: (() -> Unit)? = null
    private var onPermanentlyDenied: (() -> Unit)? = null
    private var lastRequestedPermission: String = ""

    private val prefs = fragment.requireContext()
        .getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val singlePermissionLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            // Reset denial count on grant
            resetDenialCount(lastRequestedPermission)
            onGranted?.invoke()
        } else {
            // Increment denial count
            val denialCount = incrementDenialCount(lastRequestedPermission)

            val shouldShow = fragment.shouldShowRequestPermissionRationale(
                lastRequestedPermission
            )

            if (denialCount >= MAX_DENIALS) {
                // After 2 denials, show settings dialog
                showSettingsDialog(getPermissionName(lastRequestedPermission))
            } else if (shouldShow) {
                onDenied?.invoke()
            } else {
                onPermanentlyDenied?.invoke()
            }
        }
    }

    private val multiplePermissionLauncher = fragment.registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        if (allGranted) onGranted?.invoke()
        else onDenied?.invoke()
    }

    fun request(
        permission: String,
        onGranted: () -> Unit,
        onDenied: () -> Unit = { showDeniedToast() },
        onPermanentlyDenied: () -> Unit = { showSettingsDialog() }
    ) {
        this.onGranted = onGranted
        this.onDenied = onDenied
        this.onPermanentlyDenied = onPermanentlyDenied
        this.lastRequestedPermission = permission

        when {
            isGranted(permission) -> onGranted()
            else -> singlePermissionLauncher.launch(permission)
        }
    }

    fun requestMultiple(
        permissions: Array<String>,
        onGranted: () -> Unit,
        onDenied: () -> Unit = { showDeniedToast() }
    ) {
        this.onGranted = onGranted
        this.onDenied = onDenied

        val notGranted = permissions.filter { !isGranted(it) }.toTypedArray()
        if (notGranted.isEmpty()) onGranted()
        else multiplePermissionLauncher.launch(notGranted)
    }

    fun isGranted(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            fragment.requireContext(), permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    fun requestGallery(
        onGranted: () -> Unit,
        onDenied: () -> Unit = { showDeniedToast() },
        onPermanentlyDenied: () -> Unit = { showSettingsDialog() }
    ) = request(GALLERY_PERMISSION, onGranted, onDenied, onPermanentlyDenied)

    fun requestCamera(
        onGranted: () -> Unit,
        onDenied: () -> Unit = { showDeniedToast() },
        onPermanentlyDenied: () -> Unit = { showSettingsDialog() }
    ) = request(CAMERA_PERMISSION, onGranted, onDenied, onPermanentlyDenied)

    fun requestCameraAndGallery(
        onGranted: () -> Unit,
        onDenied: () -> Unit = { showDeniedToast() }
    ) = requestMultiple(
        arrayOf(CAMERA_PERMISSION, GALLERY_PERMISSION),
        onGranted,
        onDenied
    )

    // ─────────────────────────────────────────────
    // DENIAL COUNT TRACKING
    // ─────────────────────────────────────────────

    private fun getDenialCount(permission: String): Int {
        return prefs.getInt("denial_$permission", 0)
    }

    private fun incrementDenialCount(permission: String): Int {
        val currentCount = getDenialCount(permission)
        val newCount = currentCount + 1
        prefs.edit().putInt("denial_$permission", newCount).apply()
        return newCount
    }

    private fun resetDenialCount(permission: String) {
        prefs.edit().putInt("denial_$permission", 0).apply()
    }

    private fun getPermissionName(permission: String): String {
        return when (permission) {
            CAMERA_PERMISSION -> "Camera"
            GALLERY_PERMISSION -> "Gallery"
            NOTIFICATION_PERMISSION -> "Notifications"
            else -> "This"
        }
    }

    private fun showDeniedToast() {
        Toast.makeText(
            fragment.requireContext(),
            "Permission is required for this feature",
            Toast.LENGTH_SHORT
        ).show()
    }

    private fun showSettingsDialog(permissionName: String = "This") {
        AlertDialog.Builder(fragment.requireContext())
            .setTitle("$permissionName Permission Required")
            .setMessage("You've denied this permission multiple times. Please enable $permissionName permission in Settings to use this feature.")
            .setPositiveButton("Open Settings") { _, _ -> openAppSettings() }
            .setNegativeButton("Cancel", null)
            .setCancelable(false)
            .show()
    }

    private fun openAppSettings() {
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
            data = Uri.fromParts("package", fragment.requireActivity().packageName, null)
            fragment.startActivity(this)
        }
    }
}