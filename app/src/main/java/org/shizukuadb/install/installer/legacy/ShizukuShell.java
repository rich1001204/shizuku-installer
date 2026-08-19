// SPDX-License-Identifier: GPL-3.0-or-later
// Adapted from dadaewq/Install-Lion ShizukuShell.java.
// Modified for this project on 2026-08-18; see THIRD_PARTY_NOTICES.md.
package org.shizukuadb.install.installer.legacy;

import android.os.Build;
import android.util.Log;

import androidx.annotation.Nullable;

import java.io.InputStream;
import java.io.OutputStream;

import moe.shizuku.api.RemoteProcess;
import moe.shizuku.api.ShizukuService;

public final class ShizukuShell implements Shell {
    private static final String TAG = "InstallLionShizukuShell";
    private static ShizukuShell instance;

    private ShizukuShell() {
        instance = this;
    }

    public static ShizukuShell getInstance() {
        synchronized (ShizukuShell.class) {
            return instance != null ? instance : new ShizukuShell();
        }
    }

    @Override
    public boolean isAvailable() {
        if (!ShizukuService.pingBinder()) {
            return false;
        }
        try {
            return exec(new Command("echo", "test")).isSuccessful();
        } catch (Exception error) {
            Log.w(TAG, "Unable to access Shizuku", error);
            return false;
        }
    }

    @Override
    public Result exec(Command command) {
        return execInternal(command, null);
    }

    @Override
    public Result exec(Command command, InputStream inputPipe) {
        return execInternal(command, inputPipe);
    }

    @Override
    public String makeLiteral(String arg) {
        return "'" + arg.replace("'", "'\\''") + "'";
    }

    private Result execInternal(Command command, @Nullable InputStream inputPipe) {
        StringBuilder stdout = new StringBuilder();
        StringBuilder stderr = new StringBuilder();
        try {
            RemoteProcess process = ShizukuService.newProcess(new String[]{"sh"}, null, null);
            Thread stdoutReader = InstallLionIOUtils.writeStreamToStringBuilder(stdout, process.getInputStream());
            Thread stderrReader = InstallLionIOUtils.writeStreamToStringBuilder(stderr, process.getErrorStream());
            OutputStream output = process.getOutputStream();
            output.write(command.toString().getBytes());
            output.write('\n');
            output.flush();

            if (inputPipe != null && process.alive()) {
                try (InputStream input = inputPipe) {
                    InstallLionIOUtils.copyStream(input, output);
                }
            }

            output.close();
            process.waitFor();
            stdoutReader.join();
            stderrReader.join();
            int exitCode = process.exitValue();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                process.destroyForcibly();
            } else {
                process.destroy();
            }
            return new Result(command, exitCode, stdout.toString().trim(), stderr.toString().trim());
        } catch (Exception error) {
            Log.w(TAG, "Unable to execute command", error);
            return new Result(
                    command,
                    -1,
                    "",
                    "Install Lion ShizukuShell exception: " + error
            );
        }
    }
}
