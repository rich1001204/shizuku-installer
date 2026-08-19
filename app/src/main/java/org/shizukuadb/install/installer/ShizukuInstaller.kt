// SPDX-License-Identifier: GPL-3.0-or-later
// Adapted from dadaewq/Install-Lion ShizukuSAIPackageInstaller and ShellSAIPackageInstaller.
// Modified for this project on 2026-08-19; see THIRD_PARTY_NOTICES.md.
package org.shizukuadb.install.installer

import android.content.ContentResolver
import android.net.Uri
import android.os.ParcelFileDescriptor
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
            var descriptor: ParcelFileDescriptor? = null
            var sessionId: Int? = null
            var stagingPath: String? = null
            try {
                if (!shell.isAvailable()) {
                    onResult(false, "Install Lion ShizukuShell is unavailable. Start Shizuku and grant this app access.")
                    return@execute
                }

                descriptor = resolver.openFileDescriptor(uri, "r")
                    ?: throw IOException("Unable to open the selected APK")
                val sizeBytes = resolveSize(resolver, uri, descriptor!!.statSize)
                if (sizeBytes <= 0) {
                    onResult(false, "Unable to determine APK size")
                    return@execute
                }

                val create = createSession()
                sessionId = create.first
                val createResult = create.second
                if (!createResult.isSuccessful) {
                    onResult(false, formatFailure("pm install-create", createResult))
                    return@execute
                }

                stagingPath = "/data/local/tmp/org.shizukuadb.install-$sessionId.apk"
                val stageScript = "set -e; cat > ${shell.makeLiteral(stagingPath!!)}; printf 'staged\\n'"
                val stageResult = ParcelFileDescriptor.AutoCloseInputStream(descriptor!!).use { input ->
                    shell.exec(
                        Shell.Command("sh", "-c", shell.makeLiteral(stageScript)),
                        input
                    )
                }
                descriptor = null
                if (!stageResult.isSuccessful) {
                    abandonSession(sessionId!!)
                    cleanupStaging(stagingPath)
                    onResult(false, formatFailure("stage APK to $stagingPath", stageResult))
                    return@execute
                }

                val writeResult = shell.exec(
                    Shell.Command(
                        "pm",
                        "install-write",
                        "-S",
                        sizeBytes.toString(),
                        sessionId.toString(),
                        "base.apk",
                        stagingPath!!
                    )
                )
                if (!writeResult.isSuccessful) {
                    abandonSession(sessionId!!)
                    cleanupStaging(stagingPath)
                    onResult(false, formatFailure("pm install-write (session $sessionId)", writeResult))
                    return@execute
                }

                val commitResult = shell.exec(
                    Shell.Command("pm", "install-commit", sessionId.toString())
                )
                cleanupStaging(stagingPath)
                if (commitResult.isSuccessful) {
                    onResult(true, "Installation completed")
                } else {
                    abandonSession(sessionId!!)
                    onResult(false, formatFailure("pm install-commit (session $sessionId)", commitResult))
                }
            } catch (error: Exception) {
                sessionId?.let(::abandonSession)
                cleanupStaging(stagingPath)
                onResult(false, "Install Lion Shizuku exception: ${error.message ?: error.javaClass.simpleName}")
            } finally {
                try { descriptor?.close() } catch (_: Exception) { }
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

    private fun cleanupStaging(path: String?) {
        if (!path.isNullOrBlank()) {
            shell.exec(Shell.Command("rm", "-f", path))
        }
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
