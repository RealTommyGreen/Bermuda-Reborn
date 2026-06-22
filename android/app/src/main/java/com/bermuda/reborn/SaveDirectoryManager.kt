package com.bermuda.reborn

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import java.io.File

class SaveDirectoryManager(private val context: Context) {

    companion object {
        private const val TAG = "SaveDirectory"
        private const val PREFS_NAME = "save_directory"
        private const val KEY_TREE_URI = "tree_uri"
        const val CACHE_DIR = "saves"

        private val SAVE_FILE_PATTERN = Regex("""^bermuda\.\d{3}(\.thumb)?$""")
        private val CRASH_LOG_FILE_PATTERN = Regex("""^bermuda-crash-\d{8}-\d{6}(-native)?\.txt$""")

        fun isSaveFileName(fileName: String): Boolean =
            SAVE_FILE_PATTERN.matches(fileName)

        fun isCrashLogFileName(fileName: String): Boolean =
            CRASH_LOG_FILE_PATTERN.matches(fileName)

        fun isExportFileName(fileName: String): Boolean =
            isSaveFileName(fileName) || isCrashLogFileName(fileName)
    }

    data class ConfigureResult(
        val isSuccess: Boolean,
        val error: String? = null,
        val importedCount: Int = 0,
        val exportedCount: Int = 0
    )

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun getCacheDir(): File =
        File(context.filesDir, CACHE_DIR)

    fun getConfiguredUri(): Uri? =
        prefs.getString(KEY_TREE_URI, null)?.let(Uri::parse)

    fun isConfigured(): Boolean {
        val root = getRootDocument() ?: return false
        return root.exists() && root.isDirectory && root.canWrite()
    }

    fun configure(treeUri: Uri, grantFlags: Int): ConfigureResult {
        val persistedFlags = grantFlags and (
            Intent.FLAG_GRANT_READ_URI_PERMISSION or
                Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            )

        try {
            val flagsToPersist = if (persistedFlags != 0) {
                persistedFlags
            } else {
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            }
            context.contentResolver.takePersistableUriPermission(treeUri, flagsToPersist)
        } catch (e: SecurityException) {
            Log.e(TAG, "Failed to persist save directory permission: ${e.message}", e)
            return ConfigureResult(false, "Could not keep access to the selected savegame folder: ${e.message}")
        }

        val root = DocumentFile.fromTreeUri(context, treeUri)
            ?: return ConfigureResult(false, "Could not open the selected savegame folder")
        if (!root.exists() || !root.isDirectory) {
            return ConfigureResult(false, "Selected savegame location is not a folder")
        }
        if (!root.canWrite()) {
            return ConfigureResult(false, "Selected savegame folder is not writable")
        }

        prefs.edit().putString(KEY_TREE_URI, treeUri.toString()).apply()
        getCacheDir().mkdirs()

        val imported = syncSelectedToCache()
        val exported = exportCacheToSelected()
        Log.i(TAG, "Configured save directory imported=$imported exported=$exported uri=$treeUri")
        return ConfigureResult(true, importedCount = imported, exportedCount = exported)
    }

    fun syncSelectedToCache(): Int {
        val root = getRootDocument() ?: return 0
        val cacheDir = getCacheDir()
        cacheDir.mkdirs()

        val children = try {
            root.listFiles()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to list selected save directory: ${e.message}")
            return 0
        }

        var count = 0
        for (child in children) {
            val name = child.name ?: continue
            if (!child.isFile || !isSaveFileName(name)) continue

            val target = File(cacheDir, name)
            try {
                context.contentResolver.openInputStream(child.uri)?.use { input ->
                    target.outputStream().use { output ->
                        input.copyTo(output)
                    }
                } ?: continue
                ++count
            } catch (e: Exception) {
                Log.w(TAG, "Failed to import save file $name: ${e.message}")
            }
        }
        if (count != 0) {
            Log.i(TAG, "Imported $count save file(s) from selected directory")
        }
        return count
    }

    fun exportCacheToSelected(): Int {
        val root = getRootDocument() ?: return 0
        if (!root.exists() || !root.isDirectory || !root.canWrite()) return 0

        val files = exportableCacheFiles { isExportFileName(it.name) }
            ?: return 0

        var count = 0
        for (file in files) {
            if (exportFile(root, file)) {
                ++count
            }
        }
        if (count != 0) {
            Log.i(TAG, "Exported $count save file(s) to selected directory")
        }
        return count
    }

    fun exportCrashLogsToSelected(): Int {
        val root = getRootDocument() ?: return 0
        if (!root.exists() || !root.isDirectory || !root.canWrite()) return 0

        val files = exportableCacheFiles { isCrashLogFileName(it.name) }
            ?: return 0

        var count = 0
        for (file in files) {
            if (exportFile(root, file)) {
                ++count
            }
        }
        if (count != 0) {
            Log.i(TAG, "Exported $count crash log(s) to selected directory")
        }
        return count
    }

    fun writeCrashLog(fileName: String, content: String): File {
        val cacheFile = File(getCacheDir(), fileName)
        cacheFile.parentFile?.mkdirs()
        cacheFile.writeText(content)

        val root = getRootDocument()
        if (root != null && root.exists() && root.isDirectory && root.canWrite()) {
            writeTextFile(root, fileName, content)
        }

        return cacheFile
    }

    private fun getRootDocument(): DocumentFile? {
        val uri = getConfiguredUri() ?: return null
        return try {
            DocumentFile.fromTreeUri(context, uri)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to open configured save directory: ${e.message}")
            null
        }
    }

    private fun exportableCacheFiles(predicate: (File) -> Boolean): List<File>? =
        getCacheDir().listFiles()
            ?.filter { it.isFile && predicate(it) }

    private fun exportFile(root: DocumentFile, source: File): Boolean {
        val target = findOrCreateFile(root, source.name) ?: return false
        return try {
            val output = try {
                context.contentResolver.openOutputStream(target.uri, "rwt")
            } catch (e: Exception) {
                Log.w(TAG, "Falling back to default write mode for ${source.name}: ${e.message}")
                context.contentResolver.openOutputStream(target.uri)
            } ?: return false

            source.inputStream().use { input ->
                output.use { out ->
                    input.copyTo(out)
                }
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to export save file ${source.name}: ${e.message}")
            false
        }
    }

    private fun writeTextFile(root: DocumentFile, fileName: String, content: String): Boolean {
        val target = findOrCreateFile(root, fileName, "text/plain") ?: return false
        return try {
            val output = try {
                context.contentResolver.openOutputStream(target.uri, "rwt")
            } catch (e: Exception) {
                Log.w(TAG, "Falling back to default write mode for $fileName: ${e.message}")
                context.contentResolver.openOutputStream(target.uri)
            } ?: return false

            output.use { out ->
                out.write(content.toByteArray(Charsets.UTF_8))
            }
            true
        } catch (e: Exception) {
            Log.w(TAG, "Failed to write text file $fileName: ${e.message}")
            false
        }
    }

    private fun findOrCreateFile(
        root: DocumentFile,
        fileName: String,
        mimeType: String = "application/octet-stream"
    ): DocumentFile? {
        root.listFiles().firstOrNull { it.name == fileName && it.isFile }?.let { return it }
        return try {
            root.createFile(mimeType, fileName)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to create save file $fileName: ${e.message}")
            null
        }
    }
}
