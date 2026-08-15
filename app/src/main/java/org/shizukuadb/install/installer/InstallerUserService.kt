package org.shizukuadb.install.installer

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.Bundle
import android.os.ParcelFileDescriptor
import java.util.concurrent.atomic.AtomicBoolean
import rikka.shizuku.Shizuku
import org.shizukuadb.install.IInstallCallback
import org.shizukuadb.install.IInstallerUserService

class InstallerUserService(private val context: Context) : IInstallerUserService.Stub() {
    override fun install(apk: ParcelFileDescriptor, callback: IInstallCallback) {
        var sessionId = INVALID_SESSION_ID
        val completed = AtomicBoolean(false)
        try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setSize(apk.statSize.takeIf { it >= 0 } ?: 0L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            sessionId = installer.createSession(params)
            val session = installer.openSession(sessionId)
            try {
                ParcelFileDescriptor.AutoCloseInputStream(apk).use { input ->
                    session.openWrite("base.apk", 0, apk.statSize).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                val intent = Intent(context, InstallResultReceiver::class.java).apply {
                    action = "$RESULT_ACTION_PREFIX.$sessionId"
                    putExtras(Bundle().apply {
                        putBinder(InstallResultReceiver.EXTRA_CALLBACK, callback.asBinder())
                    })
                }
                val flags = PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                val pendingIntent = PendingIntent.getBroadcast(
                    context,
                    sessionId,
                    intent,
                    flags
                )
                session.commit(pendingIntent.intentSender)
            } finally {
                session.close()
            }
        } catch (error: Exception) {
            if (completed.compareAndSet(false, true)) {
                if (sessionId != INVALID_SESSION_ID) {
                    try { context.packageManager.packageInstaller.abandonSession(sessionId) } catch (_: Exception) { }
                }
                try {
                    callback.onResult(false, humanizeError(error.message ?: "Unable to install APK"))
                } catch (_: Exception) {
                    // The client may have gone away while the privileged operation failed.
                }
            }
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
        const val RESULT_ACTION_PREFIX = "org.shizukuadb.install.INSTALL_RESULT"
        private const val INVALID_SESSION_ID = -1
    }
}
