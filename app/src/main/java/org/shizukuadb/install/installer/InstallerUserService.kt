package org.shizukuadb.install.installer

import android.os.ParcelFileDescriptor
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.shizukuadb.install.IInstallCallback
import org.shizukuadb.install.IInstallerUserService

class InstallerUserService : IInstallerUserService.Stub() {
    private val executor = Executors.newSingleThreadExecutor()

    override fun install(apk: ParcelFileDescriptor, sizeBytes: Long, callback: IInstallCallback) {
        executor.execute {
            var process: Process? = null
            try {
                process = startPackageManagerInstall(sizeBytes)
                val output = StringBuilder()
                val outputReader = thread(name = "pm-install-output") {
                    process.inputStream.bufferedReader().useLines { lines ->
                        lines.forEach { line ->
                            output.append(line).append('\n')
                        }
                    }
                }

                ParcelFileDescriptor.AutoCloseInputStream(apk).use { input ->
                    process.outputStream.use { outputStream ->
                        input.copyTo(outputStream)
                    }
                }

                val finished = process.waitFor(INSTALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
                if (!finished) {
                    process.destroyForcibly()
                    outputReader.join(2_000)
                    callback.onResult(false, "Installation timed out while running package manager")
                    return@execute
                }
                outputReader.join(2_000)

                val message = output.toString().trim()
                if (process.exitValue() == 0) {
                    callback.onResult(true, "Installation completed")
                } else {
                    callback.onResult(false, humanizeError(message))
                }
            } catch (error: Exception) {
                process?.destroyForcibly()
                try {
                    callback.onResult(false, humanizeError(error.message ?: "Unable to run package manager"))
                } catch (_: Exception) {
                    // The client may have left the confirmation screen.
                }
            } finally {
                try { apk.close() } catch (_: Exception) { }
            }
        }
    }

    private fun startPackageManagerInstall(sizeBytes: Long): Process {
        require(sizeBytes > 0) { "The selected APK has no readable size" }
        return ProcessBuilder(
            "/system/bin/pm",
            "install",
            "--user",
            "current",
            "-S",
            sizeBytes.toString(),
            "-"
        ).redirectErrorStream(true).start()
    }

    private fun humanizeError(raw: String): String {
        val normalized = raw.trim()
        return when {
            normalized.isBlank() -> "Package manager failed without an error message."
            normalized.contains("INSTALL_FAILED_VERSION_DOWNGRADE", ignoreCase = true) ->
                "A newer version of this app is already installed."
            normalized.contains("INSTALL_FAILED_ALREADY_EXISTS", ignoreCase = true) ->
                "This app is already installed."
            normalized.contains("INSTALL_FAILED_INSUFFICIENT_STORAGE", ignoreCase = true) ->
                "There is not enough storage space."
            normalized.contains("INSTALL_FAILED_INVALID_APK", ignoreCase = true) ->
                "The APK is invalid or corrupted."
            normalized.contains("INSTALL_FAILED_NO_MATCHING_ABIS", ignoreCase = true) ->
                "This APK is not compatible with this device."
            normalized.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE", ignoreCase = true) ->
                "The installed app has a different signing key."
            normalized.contains("INSTALL_FAILED_USER_RESTRICTED", ignoreCase = true) ->
                "The device user is not allowed to install this APK."
            normalized.contains("INSTALL_FAILED_DEPRECATED_SDK_VERSION", ignoreCase = true) ->
                "This APK targets an Android version that this device no longer allows."
            else -> normalized.replaceFirstChar { it.uppercase() }
        }
    }

    companion object {
        private const val INSTALL_TIMEOUT_SECONDS = 90L
    }
}
