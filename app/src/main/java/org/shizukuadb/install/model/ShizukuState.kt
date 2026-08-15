package org.shizukuadb.install.model

import android.content.Context
import android.content.pm.PackageManager
import rikka.shizuku.Shizuku

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
        override val detail = "Grant this app permission in Shizuku to continue."
    }

    data object Connected : ShizukuState {
        override val title = "Shizuku Connected"
        override val detail = "Permission granted"
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
            if (!Shizuku.pingBinder()) return NotRunning
            if (Shizuku.checkSelfPermission() != PackageManager.PERMISSION_GRANTED) {
                return PermissionRequired
            }
            return Connected
        }
    }
}
