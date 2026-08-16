// SPDX-License-Identifier: GPL-3.0-or-later
// Adapted from dadaewq/Install-Lion ShizukuShell.java and Shell.java.
// Modified for this project on 2026-08-16; see THIRD_PARTY_NOTICES.md.

package org.shizukuadb.install.installer

import java.io.InputStream
import java.nio.charset.StandardCharsets
import java.util.concurrent.TimeUnit
import kotlin.concurrent.thread

/**
 * GPLv3-derived shell runner based on Install Lion's ShizukuShell implementation.
 *
 * The modern Shizuku API removed the public newProcess() helper and recommends a
 * UserService instead. This class therefore runs /system/bin/sh inside the
 * privileged UserService process while preserving Install Lion's text-command
 * plus stdin-stream protocol.
 */
internal class InstallLionShell {
    fun exec(command: ShellCommand, inputPipe: InputStream? = null): ShellResult {
        val stdout = StringBuilder()
        val stderr = StringBuilder()
        val process = try {
            ProcessBuilder("/system/bin/sh")
                .redirectErrorStream(false)
                .start()
        } catch (error: Exception) {
            return ShellResult(
                command = command,
                exitCode = -1,
                stdout = "",
                stderr = "Unable to start /system/bin/sh: ${error.message ?: error.javaClass.simpleName}"
            )
        }

        val stdoutReader = thread(name = "install-lion-stdout") {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line -> stdout.append(line).append('\n') }
            }
        }
        val stderrReader = thread(name = "install-lion-stderr") {
            process.errorStream.bufferedReader().useLines { lines ->
                lines.forEach { line -> stderr.append(line).append('\n') }
            }
        }

        return try {
            process.outputStream.use { output ->
                output.write(command.toShellLine().toByteArray(StandardCharsets.UTF_8))
                output.write('\n'.code)
                output.flush()

                if (inputPipe != null && process.isAlive) {
                    inputPipe.use { input -> input.copyTo(output) }
                }
            }

            val finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS)
            if (!finished) {
                process.destroyForcibly()
                stdoutReader.join(2_000)
                stderrReader.join(2_000)
                return ShellResult(
                    command = command,
                    exitCode = -2,
                    stdout = stdout.toString().trim(),
                    stderr = stderr.toString().trim().ifBlank {
                        "Shell command timed out after ${TIMEOUT_SECONDS}s"
                    }
                )
            }

            stdoutReader.join(2_000)
            stderrReader.join(2_000)
            ShellResult(
                command = command,
                exitCode = process.exitValue(),
                stdout = stdout.toString().trim(),
                stderr = stderr.toString().trim()
            )
        } catch (error: Exception) {
            if (process.isAlive) process.destroyForcibly()
            ShellResult(
                command = command,
                exitCode = -1,
                stdout = stdout.toString().trim(),
                stderr = "Install Lion shell exception: ${error.message ?: error.javaClass.simpleName}"
            )
        } finally {
            if (process.isAlive) process.destroyForcibly()
        }
    }

    companion object {
        private const val TIMEOUT_SECONDS = 90L
    }
}

internal data class ShellCommand(private val args: List<String>) {
    constructor(command: String, vararg arguments: String) : this(listOf(command) + arguments)

    fun toShellLine(): String = args.joinToString(" ") { quote(it) }

    override fun toString(): String = args.joinToString(" ")

    private fun quote(value: String): String = "'${value.replace("'", "'\\''")}'"
}

internal data class ShellResult(
    val command: ShellCommand,
    val exitCode: Int,
    val stdout: String,
    val stderr: String
) {
    val isSuccessful: Boolean
        get() = exitCode == 0

    fun displayOutput(): String = buildString {
        append("Command: ").append(command).append('\n')
        append("Exit code: ").append(exitCode).append('\n')
        append("Out:\n").append(stdout)
        append("\n=============\nErr:\n").append(stderr)
    }
}
