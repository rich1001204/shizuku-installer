// SPDX-License-Identifier: GPL-3.0-or-later
// Shell/session portions adapted from dadaewq/Install-Lion ShellSAIPackageInstaller.java.
// Modified for this project on 2026-08-16; see THIRD_PARTY_NOTICES.md.

package org.shizukuadb.install.installer

import android.os.ParcelFileDescriptor
import java.util.concurrent.Executors
import org.shizukuadb.install.IInstallCallback
import org.shizukuadb.install.IInstallerUserService

/**
 * Minimal Shizuku-only installer backend.
 *
 * The shell/session flow is adapted from Install Lion's GPLv3 ShizukuShell and
 * ShellSAIPackageInstaller classes. UI, APK metadata, and Material 3 remain
 * separate in this project.
 */
class InstallerUserService : IInstallerUserService.Stub() {
    private val executor = Executors.newSingleThreadExecutor()
    private val shell = InstallLionShell()

    override fun install(apk: ParcelFileDescriptor, sizeBytes: Long, callback: IInstallCallback) {
        val ownedApk = try {
            apk.dup()
        } catch (error: Exception) {
            try {
                callback.onResult(
                    false,
                    "Unable to duplicate APK descriptor: ${error.message ?: error.javaClass.simpleName}"
                )
            } catch (_: Exception) {
                // The client may have left the confirmation screen.
            } finally {
                try { apk.close() } catch (_: Exception) { }
            }
            return
        }
        try { apk.close() } catch (_: Exception) { }

        executor.execute {
            var sessionId: Int? = null
            try {
                if (sizeBytes <= 0) {
                    callback.onResult(false, "The selected APK has no readable size")
                    return@execute
                }

                val create = shell.exec(
                    ShellCommand(
                        "pm",
                        "install-create",
                        "-r",
                        "-d",
                        "--user",
                        "0",
                        "--install-location",
                        "0",
                        "-i",
                        "org.shizukuadb.install"
                    )
                )
                if (!create.isSuccessful) {
                    callback.onResult(false, formatFailure("pm install-create", create))
                    return@execute
                }

                sessionId = extractSessionId(create)
                if (sessionId == null) {
                    callback.onResult(false, "pm install-create returned no session ID.\n${create.displayOutput()}")
                    return@execute
                }

                val write = ParcelFileDescriptor.AutoCloseInputStream(ownedApk).use { input ->
                    shell.exec(
                        ShellCommand(
                            "pm",
                            "install-write",
                            "-S",
                            sizeBytes.toString(),
                            sessionId.toString(),
                            "base.apk"
                        ),
                        input
                    )
                }
                if (!write.isSuccessful) {
                    abandonSession(sessionId)
                    callback.onResult(false, formatFailure("pm install-write (session $sessionId)", write))
                    return@execute
                }

                val commit = shell.exec(
                    ShellCommand("pm", "install-commit", sessionId.toString())
                )
                if (commit.isSuccessful) {
                    callback.onResult(true, "Installation completed")
                } else {
                    abandonSession(sessionId)
                    callback.onResult(false, formatFailure("pm install-commit (session $sessionId)", commit))
                }
            } catch (error: Exception) {
                sessionId?.let(::abandonSession)
                try {
                    callback.onResult(
                        false,
                        "Shizuku install exception: ${error.message ?: error.javaClass.simpleName}"
                    )
                } catch (_: Exception) {
                    // The client may have left the confirmation screen.
                }
            } finally {
                try { ownedApk.close() } catch (_: Exception) { }
            }
        }
    }

    private fun extractSessionId(result: ShellResult): Int? {
        val output = result.stdout + "\n" + result.stderr
        val patterns = listOf(
            Regex("\\[(\\d+)]"),
            Regex("\\b(?:session(?:\\s+id)?|id)\\s*[:=\\[]?\\s*(\\d+)\\b", RegexOption.IGNORE_CASE),
            Regex("^\\s*(\\d+)\\s*$", setOf(RegexOption.MULTILINE))
        )
        return patterns.asSequence()
            .mapNotNull { it.find(output)?.groupValues?.getOrNull(1)?.toIntOrNull() }
            .firstOrNull()
    }

    private fun abandonSession(sessionId: Int) {
        shell.exec(ShellCommand("pm", "install-abandon", sessionId.toString()))
    }

    private fun formatFailure(stage: String, result: ShellResult): String {
        return "$stage failed (exit code ${result.exitCode})\n${humanizeError(result.displayOutput())}"
    }

    private fun humanizeError(raw: String): String {
        val normalized = raw.trim()
        return when {
            normalized.contains("INSTALL_FAILED_VERSION_DOWNGRADE", ignoreCase = true) ->
                "A newer version of this app is already installed.\nDetails: $normalized"
            normalized.contains("INSTALL_FAILED_ALREADY_EXISTS", ignoreCase = true) ->
                "This app is already installed.\nDetails: $normalized"
            normalized.contains("INSTALL_FAILED_INSUFFICIENT_STORAGE", ignoreCase = true) ->
                "There is not enough storage space.\nDetails: $normalized"
            normalized.contains("INSTALL_FAILED_INVALID_APK", ignoreCase = true) ->
                "The APK is invalid or corrupted.\nDetails: $normalized"
            normalized.contains("INSTALL_FAILED_NO_MATCHING_ABIS", ignoreCase = true) ->
                "This APK is not compatible with this device.\nDetails: $normalized"
            normalized.contains("INSTALL_FAILED_UPDATE_INCOMPATIBLE", ignoreCase = true) ->
                "The installed app has a different signing key.\nDetails: $normalized"
            normalized.contains("INSTALL_FAILED_USER_RESTRICTED", ignoreCase = true) ->
                "The device user is not allowed to install this APK.\nDetails: $normalized"
            normalized.isBlank() -> "Package manager returned no diagnostic output."
            else -> normalized
        }
    }
}
