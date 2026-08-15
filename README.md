# Shizuku Installer

Shizuku Installer is a minimal Android application for installing APK files through [Shizuku](https://shizuku.rikka.app/). Its primary workflow is intentionally file-manager-first: tap an APK in any file manager, choose **Shizuku Installer**, review the APK information, and then explicitly confirm installation.

> Shizuku Installer requires Shizuku to perform privileged APK installation.
>
> This application does not require root.

## Features

- Package name: `org.shizukuadb.install`
- Registers as a handler for `application/vnd.android.package-archive` APK files.
- Supports `ACTION_VIEW` with `content://` and `file://` URIs.
- Reads file name, package name, version name, version code, application label, and file size.
- Shows a confirmation screen before any installation begins.
- Uses Shizuku's UserService and Android `PackageInstaller.Session` for the privileged installation operation.
- Reports Shizuku states separately: not installed, service not running, permission required, and connected.
- Uses a true-black AMOLED Material 3 interface with a custom adaptive launcher icon.

## Requirements

- Android 8.0 (API 26) or newer.
- [Shizuku](https://shizuku.rikka.app/) installed and running.
- Shizuku permission granted to Shizuku Installer.
- Android Studio Ladybug or newer, or a machine with the Android SDK and JDK 17+.

The app does not require root, a PC, ADB commands, or USB debugging for its installation operation. Shizuku itself may offer different startup methods depending on the Android device and version; consult the official Shizuku documentation for that setup.

## How it works

```text
File manager
    ↓
Tap example.apk
    ↓
Choose Shizuku Installer
    ↓
Read APK metadata from the granted URI
    ↓
Review confirmation screen
    ↓
Press Install APK
    ↓
Shizuku UserService creates a PackageInstaller session
    ↓
Installation result is shown
```

The app does not automatically install an APK when it receives `ACTION_VIEW`. Installation only starts after the user presses **Install APK**. The **Open APK** button on the home screen is a fallback entry point, not the primary workflow.

## APK file association

The manifest registers the following Android intent contract:

- `android.intent.action.VIEW`
- `android.intent.category.DEFAULT`
- `android.intent.category.OPENABLE`
- `application/vnd.android.package-archive`
- `content://` and `file://` URI support

The app reads the selected file using `ContentResolver`. It does not assume that APKs are stored in `/sdcard/Download/`, and it does not require users to manually copy APKs into a fixed app directory.

## Installation

1. Install and start Shizuku on the Android device.
2. Build the debug APK with the command below.
3. Install `app/build/outputs/apk/debug/app-debug.apk` on the device.
4. Grant Shizuku permission when prompted.
5. Open an APK from a file manager and select **Shizuku Installer**.

## Building

```bash
./gradlew assembleDebug
```

The generated debug APK is located at:

```text
app/build/outputs/apk/debug/app-debug.apk
```

The project has been verified in this repository with `./gradlew assembleDebug`.

## Usage

For the intended workflow, tap an APK in a file manager. Shizuku Installer displays the APK metadata and current Shizuku status. Press **Install APK** only after reviewing the information. During installation the app displays progress, then a success or human-readable failure screen.

If a file manager does not provide an APK MIME type, the **Open APK** button can be used as a fallback.

## Troubleshooting

### Shizuku is not installed

Install Shizuku from its official distribution channel, then return to the app.

### Shizuku service is not running

Open Shizuku and start its service. The status card can be refreshed from the app.

### Shizuku permission required

Press **Grant Shizuku Permission** and approve the request in Shizuku.

### The APK does not appear in Open with

Confirm that the file manager identifies the file as `application/vnd.android.package-archive`. Some file managers use their own MIME detection; the fallback **Open APK** button remains available.

### Installation failed

Check that the APK is valid, compatible with the device, has sufficient storage, and is not signed with a key incompatible with an already-installed version. The app reports a concise error instead of exposing a raw exception stack trace.

## License

This project is licensed under the [Apache License 2.0](LICENSE).
