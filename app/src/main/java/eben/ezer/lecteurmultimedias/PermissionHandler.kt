package eben.ezer.lecteurmultimedias

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.activity.compose.ManagedActivityResultLauncher
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.*
import androidx.core.content.ContextCompat

class PermissionHandler(val context: Context) {

    companion object {
        fun getRequiredPermissions(): Array<String> {
            return when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU -> {
                    arrayOf(
                        Manifest.permission.READ_MEDIA_AUDIO,
                        Manifest.permission.READ_MEDIA_VIDEO
                    )
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                    arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)
                }
                else -> {
                    arrayOf(
                        Manifest.permission.READ_EXTERNAL_STORAGE,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                    )
                }
            }
        }

        fun isMIUI(): Boolean {
            return !getSystemProperty("ro.miui.ui.version.name").isNullOrEmpty()
        }

        private fun getSystemProperty(property: String): String? {
            return try {
                val process = Runtime.getRuntime().exec("getprop $property")
                process.inputStream.bufferedReader().readLine()
            } catch (e: Exception) {
                null
            }
        }
    }

    fun hasAllPermissions(): Boolean {
        val permissions = getRequiredPermissions()
        return permissions.all { permission ->
            ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun hasStoragePermission(): Boolean {
        return when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R -> {
                Environment.isExternalStorageManager() || hasAllPermissions()
            }
            else -> hasAllPermissions()
        }
    }

    fun shouldShowRationale(activity: Activity): Boolean {
        val permissions = getRequiredPermissions()
        return permissions.any { permission ->
            activity.shouldShowRequestPermissionRationale(permission)
        }
    }

    fun openAppSettings(context: Context) {
        val intent = Intent().apply {
            action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
            data = Uri.fromParts("package", context.packageName, null)
            flags = Intent.FLAG_ACTIVITY_NEW_TASK
        }
        context.startActivity(intent)
    }

    fun openManageStorageSettings(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION)
                intent.data = Uri.parse("package:${context.packageName}")
                context.startActivity(intent)
            } catch (e: Exception) {
                openAppSettings(context)
            }
        } else {
            openAppSettings(context)
        }
    }
}

@Composable
fun rememberPermissionHandler(
    context: Context,
    onPermissionResult: (Boolean) -> Unit
): PermissionManager {
    val permissionHandler = remember { PermissionHandler(context) }

    val multiplePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.values.all { it }
        onPermissionResult(allGranted || permissionHandler.hasStoragePermission())
    }

    val storagePermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { _ ->
        onPermissionResult(permissionHandler.hasStoragePermission())
    }

    return remember {
        PermissionManager(
            permissionHandler = permissionHandler,
            multiplePermissionLauncher = multiplePermissionLauncher,
            storagePermissionLauncher = storagePermissionLauncher
        )
    }
}

data class PermissionManager(
    val permissionHandler: PermissionHandler,
    val multiplePermissionLauncher: ManagedActivityResultLauncher<Array<String>, Map<String, Boolean>>,
    val storagePermissionLauncher: ManagedActivityResultLauncher<Intent, ActivityResult>
) {
    fun requestPermissions() {
        when {
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && !Environment.isExternalStorageManager() -> {
                // Pour Android 11+ et MIUI, demander MANAGE_EXTERNAL_STORAGE
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${permissionHandler.context.packageName}")
                }
                storagePermissionLauncher.launch(intent)
            }
            else -> {
                // Pour les versions antérieures ou si MANAGE_EXTERNAL_STORAGE est déjà accordé
                multiplePermissionLauncher.launch(PermissionHandler.getRequiredPermissions())
            }
        }
    }

    fun hasPermissions(): Boolean {
        return permissionHandler.hasStoragePermission()
    }
}