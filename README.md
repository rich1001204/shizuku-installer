# Shizuku Installer

Shizuku Installer is a minimal Android application based on Install Lion's original Shizuku installer path. It keeps the legacy Shizuku V3 API and shell/session installation protocol, removes Install Lion's unrelated installation modes, and presents the workflow in a Material 3 AMOLED interface.

> Shizuku Installer requires Shizuku to perform privileged APK installation.
>
> This application does not require root.

## Features

- Package name: `org.shizukuadb.install`.
- Registers as a handler for `application/vnd.android.package-archive` APK files.
- Supports `ACTION_VIEW` with `content://` and `file://` URIs.
- Reads file name, package name, version name, version code, application label, and file size.
- Shows a confirmation screen before any installation begins.
- Keeps Install Lion's original legacy `moe.shizuku.api.ShizukuService.newProcess()` shell path.
- Runs the original three-step session protocol: `pm install-create`, `pm install-write`, and `pm install-commit`.
- Streams the selected APK into the original Shizuku shell process and reports stdout, stderr, and exit code.
- Uses a true-black AMOLED Material 3 interface with a custom adaptive launcher icon.

## Requirements

- Android 8.0 (API 26) or newer.
- [Shizuku](https://shizuku.rikka.app/) installed and running.
- Access granted to this app in Shizuku.
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
Install Lion legacy ShizukuShell.newProcess()
    ↓
pm install-create
    ↓
pm install-write -S <size> <session id> base.apk
    ↓
pm install-commit <session id>
    ↓
Installation result is shown
```

The app does not automatically install an APK when it receives `ACTION_VIEW`. Installation only starts after the user presses **Install APK**. The **Open APK** button on the home screen is a fallback entry point, not the primary workflow. The backend intentionally uses the original Install Lion V3 API artifact and `ShizukuService.newProcess()` rather than replacing it with the modern UserService API.

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
4. Grant or approve access for this app in the legacy Shizuku permission flow if prompted.
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

For the intended workflow, tap an APK in a file manager. Shizuku Installer displays the APK metadata and current Shizuku status. Press **Install APK** only after reviewing the information. During installation the app displays progress, then a success or detailed failure screen containing the original Install Lion shell command output.

If a file manager does not provide an APK MIME type, the **Open APK** button can be used as a fallback.

## Troubleshooting

### Shizuku is not installed

Install Shizuku from its official distribution channel, then return to the app.

### Shizuku service is not running

Open Shizuku and start its service. The status card can be refreshed from the app.

### Shizuku access is required

Open Shizuku, grant access to `Shizuku Installer` if requested, return to the app, and press **Refresh**. The app checks legacy shell availability using the same `echo test` probe as Install Lion.

### The APK does not appear in Open with

Confirm that the file manager identifies the file as `application/vnd.android.package-archive`. Some file managers use their own MIME detection; the fallback **Open APK** button remains available.

### Installation failed

The result screen includes the exact Install Lion-style command, exit code, stdout, and stderr. Check that the APK is valid, compatible with the device, has sufficient storage, and is not signed with a key incompatible with an already-installed version.

## License

This project is licensed under the [GNU General Public License v3](LICENSE), because the Shizuku installation backend is directly adapted from Install Lion's GPLv3 implementation. See [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md) for the upstream files, bundled legacy Shizuku artifacts, and modification scope. The Material 3 AMOLED UI and APK metadata workflow are project-specific modifications within the combined GPLv3 work.
