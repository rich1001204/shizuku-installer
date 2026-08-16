package org.shizukuadb.install.installer

import android.os.ParcelFileDescriptor
import java.io.InputStream
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread
import org.shizukuadb.install.IInstallCallback
import org.shizukuadb.install.IInstallerUserService

class InstallerUserService : IInstallerUserService.Stub() {
    private val executor = Executors.newSingleThreadExecutor()

    override fun install(apk: ParcelFileDescriptor, sizeBytes: Long, callback: IInstallCallback) {
        executor.execute {
            var stage = "preparing APK stream"
            var sessionId: Int? = null
            try {
                if (sizeBytes <= 0) {
                    callback.onResult(false, "preparing APK stream failed: The selected APK has no readable size")
                    return@execute
                }

                stage = "pm install-create"
                val create = runCommand(
                    listOf(
                        "/system/bin/pm",
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
                    callback.onResult(false, formatStageFailure(stage, create))
                    return@execute
                }

                sessionId = extractSessionId(create)
                if (sessionId == null) {
                    callback.onResult(false, "$stage returned no session ID.\n${create.displayOutput()}")
                    return@execute
                }

                stage = "pm install-write"
                val write = ParcelFileDescriptor.AutoCloseInputStream(apk).use { input ->
                    runCommand(
                        listOf(
                            "/system/bin/pm",
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
                    callback.onResult(false, formatStageFailure("$stage (session $sessionId)", write))
                    return@execute
                }

                stage = "pm install-commit"
                val commit = runCommand(
                    listOf("/system/bin/pm", "install-commit", sessionId.toString())
                )
                if (commit.isSuccessful) {
                    callback.onResult(true, "Installation completed")
                } else {
                    callback.onResult(false, formatStageFailure("$stage (session $sessionId)", commit))
                }
            } catch (error: Exception) {
                sessionId?.let(::abandonSession)
                try {
                    callback.onResult(
                        false,
                        "$stage raised an exception: ${error.message ?: error.javaClass.simpleName}"
                    )
                } catch (_: Exception) {
                    // The client may have left the confirmation screen.
                }
            } finally {
                try { apk.close() } catch (_: Exception) { }
            }
        }
    }

    private fun extractSessionId(result: CommandResult): Int? {
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
        try {
            runCommand(listOf("/system/bin/pm", "install-abandon", sessionId.toString()))
        } catch (_: Exception) {
            // Best-effort cleanup only.
        }
    }

    private fun runCommand(command: List<String>, input: InputStream? = null): CommandResult {
        val process = ProcessBuilder(command).redirectErrorStream(false).start()
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val stdoutReader = thread(name = "pm-stdout") {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { stdout.append(it).append('\n') }
            }
        }
        val stderrReader = thread(name = "pm-stderr") {
            process.errorStream.bufferedReader().useLines { lines ->
                lines.forEach { stderr.append(it).append('\n') }
            }
        }

        try {
            process.outputStream.use { output ->
                input?.use { source -> source.copyTo(output) }
            }
            val finished = process.waitFor(COMMAND_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                stdoutReader.join(2_000)
                stderrReader.join(2_000)
                return CommandResult(
                    -2,
                    stdout.toString(),
                    stderr.toString().ifBlank {
                        "Command timed out after ${COMMAND_TIMEOUT_SECONDS}s: ${command.joinToString(" ")}"
                    }
                )
            }
            stdoutReader.join(2_000)
            stderrReader.join(2_000)
            return CommandResult(process.exitValue(), stdout.toString(), stderr.toString())
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    private fun formatStageFailure(stage: String, result: CommandResult): String {
        return buildString {
            append(stage)
            append(" failed (exit code ")
            append(result.exitCode)
            append(")")
            val details = result.displayOutput()
            if (details.isNotBlank()) {
                append("\n")
                append(humanizeError(details))
            }
        }
    }

    private fun humanizeError(raw: String): String {
        val normalized = raw.trim()
        return when {
            normalized.isBlank() -> "Package manager returned no diagnostic output."
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
            normalized.contains("INSTALL_FAILED_DEPRECATED_SDK_VERSION", ignoreCase = true) ->
                "This APK targets an Android version that this device no longer allows.\nDetails: $normalized"
            else -> normalized
        }
    }

    private data class CommandResult(
        val exitCode: Int,
        val stdout: String,
        val stderr: String
    ) {
        val isSuccessful: Boolean
            get() = exitCode == 0

        fun displayOutput(): String = buildString {
            if (stdout.isNotBlank()) append("stdout: ").append(stdout.trim())
            if (stderr.isNotBlank()) {
                if (isNotEmpty()) append('\n')
                append("stderr: ").append(stderr.trim())
            }
            if (isEmpty()) append("Command returned no output")
        }
    }

    companion object {
        private const val COMMAND_TIMEOUT_SECONDS = 60L
    }
}
