package org.shizukuadb.install.installer

import android.content.Context
import android.content.pm.PackageInstaller
import android.os.Build
import android.os.ParcelFileDescriptor
import android.content.IntentSender
import org.shizukuadb.install.IInstallerUserService

class InstallerUserService(private val context: Context) : IInstallerUserService.Stub() {
    override fun install(apk: ParcelFileDescriptor, resultIntent: IntentSender) {
        var sessionId = INVALID_SESSION_ID
        try {
            val installer = context.packageManager.packageInstaller
            val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL)
            params.setSize(apk.statSize.takeIf { it >= 0 } ?: 0L)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                params.setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
            }
            sessionId = installer.createSession(params)
            installer.openSession(sessionId).use { session ->
                ParcelFileDescriptor.AutoCloseInputStream(apk).use { input ->
                    session.openWrite("base.apk", 0, apk.statSize).use { output ->
                        input.copyTo(output)
                        session.fsync(output)
                    }
                }
                session.commit(resultIntent)
            }
        } catch (error: Exception) {
            if (sessionId != INVALID_SESSION_ID) {
                try { context.packageManager.packageInstaller.abandonSession(sessionId) } catch (_: Exception) { }
            }
            throw error
        }
    }

    companion object {
        private const val INVALID_SESSION_ID = -1
    }
}
