package org.shizukuadb.install.viewmodel

import android.app.Application
import android.content.Intent
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import rikka.shizuku.Shizuku
import org.shizukuadb.install.apk.ApkInfo
import org.shizukuadb.install.apk.ApkParser
import org.shizukuadb.install.installer.ShizukuInstaller
import org.shizukuadb.install.model.InstallState
import org.shizukuadb.install.model.ShizukuState

class InstallerViewModel(application: Application) : AndroidViewModel(application) {
    companion object {
        const val SHIZUKU_PERMISSION_REQUEST_CODE = 4101
    }

    private val parser = ApkParser(application)
    private val installer = ShizukuInstaller(application)
    private val _installState = MutableStateFlow<InstallState>(InstallState.Idle)
    private val _shizukuState = MutableStateFlow<ShizukuState>(ShizukuState.NotRunning)
    private val _message = MutableStateFlow<String?>(null)
    private val loading = AtomicBoolean(false)
    private var installTimeoutJob: Job? = null

    val installState: StateFlow<InstallState> = _installState.asStateFlow()
    val shizukuState: StateFlow<ShizukuState> = _shizukuState.asStateFlow()
    val message: StateFlow<String?> = _message.asStateFlow()

    private val permissionListener = object : Shizuku.OnRequestPermissionResultListener {
        override fun onRequestPermissionResult(requestCode: Int, grantResult: Int) {
            if (requestCode == SHIZUKU_PERMISSION_REQUEST_CODE) refreshShizuku()
        }
    }

    init {
        Shizuku.addRequestPermissionResultListener(permissionListener)
        refreshShizuku()
    }

    fun refreshShizuku() {
        viewModelScope.launch {
            _shizukuState.value = withContext(Dispatchers.Default) {
                ShizukuState.read(getApplication())
            }
        }
    }

    fun openApk(uri: Uri) {
        if (!loading.compareAndSet(false, true)) return
        _message.value = null
        _installState.value = InstallState.LoadingApk
        viewModelScope.launch {
            try {
                val info = parser.parse(uri)
                _installState.value = InstallState.Ready(info)
            } catch (error: Exception) {
                _installState.value = InstallState.Failure(
                    error.message ?: "Unable to read this APK"
                )
            } finally {
                loading.set(false)
            }
        }
    }

    fun install() {
        val apk = (_installState.value as? InstallState.Ready)?.apk ?: return
        refreshShizuku()
        val currentShizuku = ShizukuState.read(getApplication())
        _shizukuState.value = currentShizuku
        if (currentShizuku !is ShizukuState.Connected) {
            _message.value = currentShizuku.detail
            return
        }
        _message.value = null
        _installState.value = InstallState.Installing(apk)
        installTimeoutJob?.cancel()
        installTimeoutJob = viewModelScope.launch {
            delay(60_000)
            if (_installState.value is InstallState.Installing) {
                _installState.value = InstallState.Failure(
                    "Installation timed out. Check Shizuku and try again.",
                    apk
                )
            }
        }
        installer.install(
            getApplication<Application>().contentResolver,
            Uri.parse(apk.uriString)
        ) { success, message ->
            viewModelScope.launch {
                if (_installState.value !is InstallState.Installing) return@launch
                installTimeoutJob?.cancel()
                installTimeoutJob = null
                _installState.value = if (success) {
                    InstallState.Success(apk, message)
                } else {
                    InstallState.Failure(message, apk)
                }
            }
        }
    }

    fun requestShizukuPermission() {
        try {
            Shizuku.requestPermission(SHIZUKU_PERMISSION_REQUEST_CODE)
        } catch (error: Exception) {
            _message.value = error.message ?: "Unable to request Shizuku permission"
        }
    }

    fun openShizuku(): Intent? {
        return getApplication<Application>().packageManager
            .getLaunchIntentForPackage("moe.shizuku.privileged.api")
    }

    fun clearMessage() {
        _message.value = null
    }

    fun resetToHome() {
        installTimeoutJob?.cancel()
        installTimeoutJob = null
        _installState.value = InstallState.Idle
        _message.value = null
    }

    fun retry() {
        installTimeoutJob?.cancel()
        installTimeoutJob = null
        val apk = (_installState.value as? InstallState.Failure)?.apk
        if (apk == null) resetToHome() else {
            _installState.value = InstallState.Ready(apk)
            _message.value = null
        }
    }

    override fun onCleared() {
        installTimeoutJob?.cancel()
        Shizuku.removeRequestPermissionResultListener(permissionListener)
        installer.unbind()
        super.onCleared()
    }
}
