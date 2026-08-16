# Third-party source notices

## Install Lion Shizuku shell backend

The files `app/src/main/java/org/shizukuadb/install/installer/InstallLionShell.kt` and the shell/session portions of `InstallerUserService.kt` are adapted from the Shizuku installation backend in [dadaewq/Install-Lion](https://github.com/dadaewq/Install-Lion), including these upstream files:

- `app/src/main/java/com/modosa/apkinstaller/util/shell/ShizukuShell.java`
- `app/src/main/java/com/modosa/apkinstaller/util/shell/Shell.java`
- `app/src/main/java/com/modosa/apkinstaller/util/installer/ShellSAIPackageInstaller.java`

Install Lion is distributed under the GNU General Public License, version 3. This project includes the corresponding GPLv3 license text in `LICENSE`. The backend was reduced to the Shizuku-only single-APK path and adapted from the legacy `moe.shizuku.api` API to the modern `rikka.shizuku` UserService API. The Material 3 AMOLED UI, APK metadata flow, manifest, and project structure are original project components.

The adapted files are modified versions and are provided under GPLv3. The modification date is 2026-08-16.
