package org.shizukuadb.install.installer

import android.content.ComponentName
import android.content.ContentResolver
import android.content.ServiceConnection
import android.net.Uri
import android.os.IBinder
import java.io.IOException
import rikka.shizuku.Shizuku
import org.shizukuadb.install.IInstallCallback
import org.shizukuadb.install.IInstallerUserService

class ShizukuInstaller {
    private var userService: IInstallerUserService? = null
    private var bound = false
    private var pending: PendingInstall? = null

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName("org.shizukuadb.install", InstallerUserService::class.java.name)
    ).daemon(false).processNameSuffix("installer").version(1)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            userService = service?.let(IInstallerUserService.Stub::asInterface)
            dispatchPending()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            userService = null
            bound = false
        }
    }

    fun install(
        resolver: ContentResolver,
        uri: Uri,
        onResult: (Boolean, String) -> Unit
    ) {
        pending = PendingInstall(resolver, uri, onResult)
        if (userService == null) {
            if (!bound) {
                bound = true
                Shizuku.bindUserService(userServiceArgs, connection)
            }
        } else {
            dispatchPending()
        }
    }

    fun unbind() {
        if (bound) {
            try { Shizuku.unbindUserService(userServiceArgs, connection, true) } catch (_: Exception) { }
            bound = false
            userService = null
        }
    }

    private fun dispatchPending() {
        val service = userService ?: return
        val request = pending ?: return
        pending = null
        try {
            val descriptor = request.resolver.openFileDescriptor(request.uri, "r")
                ?: throw IOException("Unable to open the selected APK")
            val callback = object : IInstallCallback.Stub() {
                override fun onResult(success: Boolean, message: String?) {
                    request.onResult(success, message ?: "The installer returned no details")
                }
            }
            service.install(descriptor, callback)
        } catch (error: Exception) {
            request.onResult(false, error.message ?: "Unable to start installation")
        }
    }

    private data class PendingInstall(
        val resolver: ContentResolver,
        val uri: Uri,
        val onResult: (Boolean, String) -> Unit
    )
}
