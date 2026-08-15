package org.shizukuadb.install.installer

import android.app.PendingIntent
import android.content.ComponentName
import android.content.ContentResolver
import android.content.Intent
import android.content.ServiceConnection
import android.net.Uri
import android.os.Bundle
import android.os.IBinder
import java.io.IOException
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger
import rikka.shizuku.Shizuku
import org.shizukuadb.install.IInstallCallback
import org.shizukuadb.install.IInstallerUserService

class ShizukuInstaller(private val context: android.content.Context) {
    private var userService: IInstallerUserService? = null
    private var bound = false
    private var pending: PendingInstall? = null
    private var active: ResultOnce? = null
    private val requestCounter = AtomicInteger(7000)

    private val userServiceArgs = Shizuku.UserServiceArgs(
        ComponentName("org.shizukuadb.install", InstallerUserService::class.java.name)
    ).daemon(false).processNameSuffix("installer").version(2)

    private val connection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            userService = service?.let(IInstallerUserService.Stub::asInterface)
            dispatchPending()
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            userService = null
            bound = false
            active?.complete(false, "Shizuku installer service disconnected")
            active = null
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
                try {
                    Shizuku.bindUserService(userServiceArgs, connection)
                } catch (error: Exception) {
                    bound = false
                    pending = null
                    onResult(false, error.message ?: "Unable to connect to Shizuku installer service")
                }
            }
        } else {
            dispatchPending()
        }
    }

    fun unbind() {
        active?.complete(false, "Installation was interrupted")
        active = null
        pending = null
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
            val requestCode = requestCounter.incrementAndGet()
            val result = ResultOnce(request.onResult)
            val callback = object : IInstallCallback.Stub() {
                override fun onResult(success: Boolean, message: String?) {
                    result.complete(success, message ?: "The installer returned no details")
                    if (active === result) active = null
                }
            }
            val resultIntent = Intent(context, InstallResultReceiver::class.java).apply {
                action = "org.shizukuadb.install.INSTALL_RESULT.$requestCode"
                putExtras(Bundle().apply {
                    putBinder(InstallResultReceiver.EXTRA_CALLBACK, callback.asBinder())
                })
            }
            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                resultIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            )
            active = result
            descriptor.use {
                service.install(it, pendingIntent.intentSender)
            }
        } catch (error: Exception) {
            active = null
            request.onResult(false, error.message ?: "Unable to start installation")
        }
    }

    private class ResultOnce(private val deliver: (Boolean, String) -> Unit) {
        private val completed = AtomicBoolean(false)

        fun complete(success: Boolean, message: String) {
            if (completed.compareAndSet(false, true)) deliver(success, message)
        }
    }

    private data class PendingInstall(
        val resolver: ContentResolver,
        val uri: Uri,
        val onResult: (Boolean, String) -> Unit
    )
}
