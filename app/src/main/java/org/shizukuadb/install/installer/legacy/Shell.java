// SPDX-License-Identifier: GPL-3.0-or-later
// Adapted from dadaewq/Install-Lion.
// Modified for this project on 2026-08-18; see THIRD_PARTY_NOTICES.md.
package org.shizukuadb.install.installer.legacy;

import androidx.annotation.NonNull;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;

public interface Shell {

    boolean isAvailable();

    Result exec(Command command);

    Result exec(Command command, InputStream inputPipe);

    String makeLiteral(String arg);

    class Command {
        private final ArrayList<String> mArgs = new ArrayList<>();

        public Command(String command, String... args) {
            mArgs.add(command);
            mArgs.addAll(Arrays.asList(args));
        }

        @NonNull
        @Override
        public String toString() {
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < mArgs.size(); i++) {
                sb.append(mArgs.get(i));
                if (i < mArgs.size() - 1) sb.append(" ");
            }
            return sb.toString();
        }
    }

    class Result {
        public final int exitCode;
        public final String out;
        public final String err;
        final Command cmd;

        Result(Command cmd, int exitCode, String out, String err) {
            this.cmd = cmd;
            this.exitCode = exitCode;
            this.out = out;
            this.err = err;
        }

        public boolean isSuccessful() {
            return exitCode == 0;
        }

        @NonNull
        @Override
        public String toString() {
            return "Command: " + cmd +
                    "\nExit code: " + exitCode +
                    "\nOut:\n" + out +
                    "\n=============\nErr:\n" + err;
        }
    }
}
