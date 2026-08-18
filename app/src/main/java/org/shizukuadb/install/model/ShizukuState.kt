package org.shizukuadb.install.model

import android.content.Context
import android.content.pm.PackageManager
import moe.shizuku.api.ShizukuService
import org.shizukuadb.install.installer.legacy.ShizukuShell

sealed interface ShizukuState {
    val title: String
    val detail: String

    data object NotInstalled : ShizukuState {
        override val title = "Shizuku is not installed"
        override val detail = "Install Shizuku to enable privileged APK installation."
    }

    data object NotRunning : ShizukuState {
        override val title = "Shizuku service is not running"
        override val detail = "Open Shizuku and start its service before installing."
    }

    data object PermissionRequired : ShizukuState {
        override val title = "Shizuku permission required"
        override val detail = "Grant this app access in Shizuku, then refresh this screen."
    }

    data object Connected : ShizukuState {
        override val title = "Shizuku Connected"
        override val detail = "Install Lion legacy Shizuku API is ready"
    }

    companion object {
        fun read(context: Context): ShizukuState {
            val installed = try {
                context.packageManager.getApplicationInfo("moe.shizuku.privileged.api", 0)
                true
            } catch (_: PackageManager.NameNotFoundException) {
                false
            }
            if (!installed) return NotInstalled
            if (!ShizukuService.pingBinder()) return NotRunning
            if (!ShizukuShell.getInstance().isAvailable()) return PermissionRequired
            return Connected
        }
    }
}
