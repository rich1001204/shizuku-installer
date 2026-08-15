package org.shizukuadb.install.apk

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.OpenableColumns
import java.io.File
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ApkParser(private val context: Context) {
    suspend fun parse(uri: Uri): ApkInfo = withContext(Dispatchers.IO) {
        val metadataFile = File(context.cacheDir, "apk-metadata/${System.nanoTime()}.apk")
        metadataFile.parentFile?.mkdirs()
        try {
            val declaredName = queryDisplayName(uri)
            val declaredSize = querySize(uri)
            copyUriToFile(uri, metadataFile)
            val packageInfo = readPackageInfo(metadataFile)
                ?: throw IOException("The selected file is not a valid APK")
            val fileName = declaredName?.takeIf { it.isNotBlank() }
                ?: metadataFile.name
            val size = declaredSize.takeIf { it >= 0 } ?: metadataFile.length()
            val label = packageInfo.applicationInfo?.let {
                it.sourceDir = metadataFile.absolutePath
                it.publicSourceDir = metadataFile.absolutePath
                it.loadLabel(context.packageManager).toString()
            }
            ApkInfo(
                uriString = uri.toString(),
                fileName = fileName,
                packageName = packageInfo.packageName,
                versionName = packageInfo.versionName ?: "Unknown",
                versionCode = packageInfo.versionCodeCompat,
                fileSizeBytes = size,
                applicationLabel = label
            )
        } finally {
            metadataFile.delete()
        }
    }

    private fun queryDisplayName(uri: Uri): String? {
        if (uri.scheme == "file") return uri.lastPathSegment
        return context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.DISPLAY_NAME),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    private fun querySize(uri: Uri): Long {
        if (uri.scheme == "file") return File(uri.path.orEmpty()).length()
        return context.contentResolver.query(
            uri,
            arrayOf(OpenableColumns.SIZE),
            null,
            null,
            null
        )?.use { cursor ->
            if (cursor.moveToFirst() && !cursor.isNull(0)) cursor.getLong(0) else -1L
        } ?: -1L
    }

    private fun copyUriToFile(uri: Uri, destination: File) {
        val input = if (uri.scheme == "file") {
            java.io.FileInputStream(File(uri.path.orEmpty()))
        } else {
            context.contentResolver.openInputStream(uri)
                ?: throw IOException("Unable to read the selected APK")
        }
        input.use { source ->
            destination.outputStream().use { target -> source.copyTo(target) }
        }
    }

    @Suppress("DEPRECATION")
    private fun readPackageInfo(file: File): PackageInfo? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            context.packageManager.getPackageArchiveInfo(
                file.absolutePath,
                PackageManager.PackageInfoFlags.of(PackageManager.GET_META_DATA.toLong())
            )
        } else {
            context.packageManager.getPackageArchiveInfo(
                file.absolutePath,
                PackageManager.GET_META_DATA
            )
        }
    }

    private val PackageInfo.versionCodeCompat: Long
        get() = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) longVersionCode else versionCode.toLong()
}
