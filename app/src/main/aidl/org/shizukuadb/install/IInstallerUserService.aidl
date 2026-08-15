package org.shizukuadb.install;

import android.content.IntentSender;
import android.os.ParcelFileDescriptor;

interface IInstallerUserService {
    void install(in ParcelFileDescriptor apk, in IntentSender resultIntent);
}
