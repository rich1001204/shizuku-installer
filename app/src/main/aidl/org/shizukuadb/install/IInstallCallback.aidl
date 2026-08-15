package org.shizukuadb.install;

interface IInstallCallback {
    void onResult(boolean success, String message);
}
