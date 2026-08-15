package org.shizukuadb.install.installer

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.IBinder
import org.shizukuadb.install.IInstallCallback

class InstallResultReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val binder: IBinder? = intent.extras?.let { extras ->
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                extras.getBinder(EXTRA_CALLBACK)
            } else {
                @Suppress("DEPRECATION")
                extras.getBinder(EXTRA_CALLBACK)
            }
        }
        val callback = binder?.let { IInstallCallback.Stub.asInterface(it) } ?: return
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        val rawMessage = intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE)
            ?.takeIf { it.isNotBlank() }
            ?: "Package installer returned status $status"
        val message = if (status == PackageInstaller.STATUS_SUCCESS) {
            "Installation completed"
        } else {
            humanizeError(rawMessage)
        }
        try {
            callback.onResult(status == PackageInstaller.STATUS_SUCCESS, message)
        } catch (_: Exception) {
            // The client may have left the confirmation screen.
        }
    }

    private fun humanizeError(raw: String): String {
        return when {
            raw.contains("INSTALL_FAILED_VERSION_DOWNGRADE", ignoreCase = true) ->
                "A newer version of this app is already installed."
            raw.contains("INSTALL_FAILED_ALREADY_EXISTS", ignoreCase = true) ->
                "This app is already installed."
            raw.contains("INSTALL_FAILED_INSUFFICIENT_STORAGE", ignoreCase = true) ->
                "There is not enough storage space."
            raw.contains("INSTALL_FAILED_INVALID_APK", ignoreCase = true) ->
                "The APK is invalid or corrupted."
            raw.contains("INSTALL_FAILED_NO_MATCHING_ABIS", ignoreCase = true) ->
                "This APK is not compatible with this device."
            raw.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE", ignoreCase = true) ->
                "The installed app has a different signing key."
            else -> raw.replaceFirstChar { it.uppercase() }
        }
    }

    companion object {
        const val EXTRA_CALLBACK = "callback_binder"
    }
}
