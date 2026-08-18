// SPDX-License-Identifier: GPL-3.0-or-later
// Adapted from dadaewq/Install-Lion.
// Modified for this project on 2026-08-18; see THIRD_PARTY_NOTICES.md.
package org.shizukuadb.install.installer.legacy;

import android.util.Log;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;

public final class InstallLionIOUtils {
    private static final String TAG = "InstallLionIOUtils";

    private InstallLionIOUtils() {
    }

    public static void copyStream(InputStream from, OutputStream to) throws IOException {
        byte[] buffer = new byte[1024 * 1024];
        int length;
        while ((length = from.read(buffer)) > 0) {
            to.write(buffer, 0, length);
        }
    }

    public static Thread writeStreamToStringBuilder(StringBuilder builder, InputStream inputStream) {
        Thread thread = new Thread(() -> {
            try {
                char[] buffer = new char[1024];
                int length;
                BufferedReader reader = new BufferedReader(new InputStreamReader(inputStream));
                while ((length = reader.read(buffer)) > 0) {
                    builder.append(buffer, 0, length);
                }
                reader.close();
            } catch (Exception error) {
                Log.w(TAG, "Unable to read shell stream", error);
            }
        }, "install-lion-shell-reader");
        thread.start();
        return thread;
    }
}
