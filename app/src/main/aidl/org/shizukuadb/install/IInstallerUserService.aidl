package org.shizukuadb.install;

import android.os.ParcelFileDescriptor;
import org.shizukuadb.install.IInstallCallback;

interface IInstallerUserService {
    void install(in ParcelFileDescriptor apk, IInstallCallback callback);
}
