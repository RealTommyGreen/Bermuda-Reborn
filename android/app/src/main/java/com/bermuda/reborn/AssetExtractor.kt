package com.bermuda.reborn

import android.content.Context
import android.util.Log
import java.io.File
import java.io.IOException

class AssetExtractor(private val context: Context) {

    companion object {
        private const val TAG = "AssetExtractor"
        private const val MANIFEST_FILE = ".extracted_version"
    }

    fun extract(assetRoot: String, targetDir: File, versionCode: Int, requiredFiles: List<String> = emptyList()) {
        val manifest = File(targetDir, MANIFEST_FILE)
        if (manifest.exists()) {
            val existing = manifest.readText().trim().toIntOrNull() ?: 0
            val missingFiles = requiredFiles.filter { !File(targetDir, it).isFile }
            if (existing >= versionCode && missingFiles.isEmpty()) {
                Log.d(TAG, "Assets up-to-date (v$existing >= v$versionCode), skipping")
                return
            }
            if (missingFiles.isNotEmpty()) {
                Log.w(TAG, "Asset verification failed, missing: ${missingFiles.joinToString()}")
            } else {
                Log.d(TAG, "Version mismatch (v$existing < v$versionCode), re-extracting")
            }
        }

        targetDir.deleteRecursively()
        targetDir.mkdirs()
        val startMs = System.currentTimeMillis()
        var fileCount = 0
        fileCount = extractDir(assetRoot, targetDir)
        manifest.writeText(versionCode.toString())

        val elapsed = (System.currentTimeMillis() - startMs) / 1000
        Log.i(TAG, "Extracted $fileCount files to ${targetDir.absolutePath} in ${elapsed}s")
    }

    private fun extractDir(assetPath: String, targetDir: File): Int {
        val assets = context.assets

        val children = try {
            assets.list(assetPath)
        } catch (e: IOException) {
            null
        }

        if (children == null || children.isEmpty()) {
            // not a directory (or empty) — try extracting as file
            return extractFile(assetPath, targetDir)
        }

        targetDir.mkdirs()
        var count = 0
        for (child in children) {
            val childAssetPath = if (assetPath.isEmpty()) child else "$assetPath/$child"
            val childTarget = File(targetDir, child)
            count += extractDir(childAssetPath, childTarget)
        }
        return count
    }

    private fun extractFile(assetPath: String, targetFile: File): Int {
        return try {
            context.assets.open(assetPath).use { input ->
                targetFile.parentFile?.mkdirs()
                targetFile.outputStream().use { output ->
                    input.copyTo(output)
                }
            }
            1
        } catch (e: IOException) {
            Log.e(TAG, "Failed to extract $assetPath: ${e.message}")
            0
        }
    }
}
