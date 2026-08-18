// SPDX-License-Identifier: GPL-3.0-or-later
// Adapted from dadaewq/Install-Lion ShizukuSAIPackageInstaller and ShellSAIPackageInstaller.
// Modified for this project on 2026-08-18; see THIRD_PARTY_NOTICES.md.
package org.shizukuadb.install.installer

import android.content.ContentResolver
import android.net.Uri
import android.provider.OpenableColumns
import java.io.IOException
import java.util.concurrent.Executors
import org.shizukuadb.install.installer.legacy.ShizukuShell
import org.shizukuadb.install.installer.legacy.Shell

/**
 * Minimal single-APK port of Install Lion's Shizuku installer.
 * It intentionally keeps the original legacy Shizuku V3 API path.
 */
class ShizukuInstaller {
    private val executor = Executors.newSingleThreadExecutor()
    private val shell = ShizukuShell.getInstance()

    fun isAvailable(): Boolean = shell.isAvailable()

    fun install(
        resolver: ContentResolver,
        uri: Uri,
        onResult: (Boolean, String) -> Unit
    ) {
        executor.execute {
            try {
                if (!shell.isAvailable()) {
                    onResult(false, "Install Lion ShizukuShell is unavailable. Start Shizuku and grant this app access.")
                    return@execute
                }

                val descriptor = resolver.openFileDescriptor(uri, "r")
                    ?: throw IOException("Unable to open the selected APK")
                val sizeBytes = resolveSize(resolver, uri, descriptor.statSize)
                if (sizeBytes <= 0) {
                    descriptor.close()
                    onResult(false, "Unable to determine APK size")
                    return@execute
                }

                var sessionId: Int? = null
                try {
                    val create = createSession()
                    sessionId = create.first
                    val createResult = create.second
                    if (!createResult.isSuccessful) {
                        onResult(false, formatFailure("pm install-create", createResult))
                        return@execute
                    }

                    val writeResult = descriptor.use { pfd ->
                        val input = android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd)
                        shell.exec(
                            Shell.Command(
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
                    if (!writeResult.isSuccessful) {
                        abandonSession(sessionId)
                        onResult(false, formatFailure("pm install-write (session $sessionId)", writeResult))
                        return@execute
                    }

                    val commitResult = shell.exec(
                        Shell.Command("pm", "install-commit", sessionId.toString())
                    )
                    if (commitResult.isSuccessful) {
                        onResult(true, "Installation completed")
                    } else {
                        abandonSession(sessionId)
                        onResult(false, formatFailure("pm install-commit (session $sessionId)", commitResult))
                    }
                } catch (error: Exception) {
                    sessionId?.let(::abandonSession)
                    try { descriptor.close() } catch (_: Exception) { }
                    onResult(false, "Install Lion Shizuku exception: ${error.message ?: error.javaClass.simpleName}")
                }
            } catch (error: Exception) {
                onResult(false, error.message ?: "Unable to start Install Lion Shizuku installer")
            }
        }
    }

    fun shutdown() {
        executor.shutdownNow()
    }

    private fun createSession(): Pair<Int, Shell.Result> {
        val commands = listOf(
            Shell.Command(
                "pm",
                "install-create",
                "-r",
                "-d",
                "--user 0",
                "--install-location",
                "0",
                "-i",
                shell.makeLiteral("org.shizukuadb.install")
            ),
            Shell.Command(
                "pm",
                "install-create",
                "-r",
                "-d",
                "-- user 0",
                "-i",
                shell.makeLiteral("org.shizukuadb.install")
            )
        )
        val attempts = StringBuilder()
        for (command in commands) {
            val result = shell.exec(command)
            if (result.isSuccessful) {
                val id = extractSessionId(result.out)
                if (id != null) return id to result
            }
            attempts.append("\n\n").append(result)
        }
        throw IllegalStateException("Unable to create Install Lion session:$attempts")
    }

    private fun extractSessionId(output: String): Int? {
        return Regex("(\\d+)").find(output)?.groupValues?.getOrNull(1)?.toIntOrNull()
    }

    private fun abandonSession(sessionId: Int) {
        shell.exec(Shell.Command("pm", "install-abandon", sessionId.toString()))
    }

    private fun resolveSize(resolver: ContentResolver, uri: Uri, statSize: Long): Long {
        if (statSize > 0) return statSize
        return resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else -1L
        } ?: -1L
    }

    private fun formatFailure(stage: String, result: Shell.Result): String {
        return "$stage failed\n${result}"
    }
}
