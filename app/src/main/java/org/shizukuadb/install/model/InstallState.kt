package org.shizukuadb.install.model

import org.shizukuadb.install.apk.ApkInfo

sealed interface InstallState {
    data object Idle : InstallState
    data object LoadingApk : InstallState
    data class Ready(val apk: ApkInfo) : InstallState
    data class Installing(val apk: ApkInfo) : InstallState
    data class Success(val apk: ApkInfo, val message: String) : InstallState
    data class Failure(val message: String, val apk: ApkInfo? = null) : InstallState
}
