# Third-party source notices

## Install Lion Shizuku backend

The following source files are adapted from the Shizuku installation path in [dadaewq/Install-Lion](https://github.com/dadaewq/Install-Lion):

- `app/src/main/java/com/modosa/apkinstaller/util/shell/Shell.java`
- `app/src/main/java/com/modosa/apkinstaller/util/shell/ShizukuShell.java`
- `app/src/main/java/com/modosa/apkinstaller/util/IOUtils.java`
- `app/src/main/java/com/modosa/apkinstaller/util/installer/ShellSAIPackageInstaller.java`
- `app/src/main/java/com/modosa/apkinstaller/util/installer/shizuku/ShizukuSAIPackageInstaller.java`

This project keeps the original legacy Shizuku V3 API route, including the `moe.shizuku.api.ShizukuService.newProcess()` and `moe.shizuku.api.RemoteProcess` protocol, and removes Install Lion's IceBox, DSM, Root, DPM, uninstall, settings, analytics, and unrelated installer paths. The single-APK session flow remains `pm install-create`, `pm install-write`, and `pm install-commit`.

The adapted Java files under `app/src/main/java/org/shizukuadb/install/installer/legacy/` and the shell/session portion of `ShizukuInstaller.kt` are modified versions and are provided under the GNU General Public License, version 3. The corresponding GPLv3 license text is in `LICENSE`; the modification date is 2026-08-18.

The Material 3 AMOLED interface, APK metadata parser, Android manifest integration, and Compose UI are project-specific modifications. The bundled `app/libs/shizuku-api-3.1.0.aar` and `app/libs/shizuku-shared-3.1.0.aar` are the legacy Shizuku V3 artifacts used by Install Lion; their upstream metadata identifies them as Apache-2.0 licensed components from the Shizuku project.
