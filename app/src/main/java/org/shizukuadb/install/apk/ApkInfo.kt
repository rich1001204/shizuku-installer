package org.shizukuadb.install.apk

data class ApkInfo(
    val uriString: String,
    val fileName: String,
    val packageName: String,
    val versionName: String,
    val versionCode: Long,
    val fileSizeBytes: Long,
    val applicationLabel: String?
) {
    val fileSizeLabel: String
        get() = when {
            fileSizeBytes < 1024L -> "$fileSizeBytes B"
            fileSizeBytes < 1024L * 1024L -> "%.1f KB".format(fileSizeBytes / 1024.0)
            fileSizeBytes < 1024L * 1024L * 1024L -> "%.1f MB".format(fileSizeBytes / (1024.0 * 1024.0))
            else -> "%.2f GB".format(fileSizeBytes / (1024.0 * 1024.0 * 1024.0))
        }
}
