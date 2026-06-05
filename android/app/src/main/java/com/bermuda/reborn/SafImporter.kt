package com.bermuda.reborn

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import org.json.JSONObject
import java.io.File
import java.io.IOException

class SafImporter(private val context: Context) {

    companion object {
        private const val TAG = "SafImporter"
        const val IMPORT_DIR = "imported_game"
        const val BERMUDA_DIR = "BERMUDA"
        const val MANIFEST_FILE = ".import_manifest.json"

        val REQUIRED_FILES = listOf(
            "BERMUDA.SPR",
            "BERMUDA.WGP",
            "SCN/-01.SCN",
            "MIDI/TITLE.MID"
        )
    }

    data class ImportManifest(
        val sourceUri: String,
        val importTime: String,
        val fileCount: Int,
        val validationStatus: String,
        val validatedFiles: List<String>
    )

    fun getTargetDir(): File =
        File(context.filesDir, IMPORT_DIR).resolve(BERMUDA_DIR)

    fun getManifestFile(): File =
        getTargetDir().resolve(MANIFEST_FILE)

    fun isImportValid(): Boolean {
        val manifestFile = getManifestFile()
        if (!manifestFile.exists()) return false

        try {
            val json = JSONObject(manifestFile.readText())
            if (json.optString("validation_status") != "ok") return false

            val targetDir = getTargetDir()
            for (file in REQUIRED_FILES) {
                if (!File(targetDir, file).isFile) return false
            }
            return true
        } catch (e: Exception) {
            Log.w(TAG, "Manifest read failed: ${e.message}")
            return false
        }
    }

    fun validateSource(treeUri: Uri): ValidationResult {
        val documentFile = DocumentFile.fromTreeUri(context, treeUri)
            ?: return ValidationResult(false, "Could not open selected folder", emptyList())

        val children = documentFile.listFiles()
        if (children.isEmpty()) {
            return ValidationResult(false, "Selected folder is empty", emptyList())
        }

        val childNames = children.map { it.name }.toSet()
        val missing = REQUIRED_FILES.filter { required ->
            val parts = required.split("/")
            if (parts.size == 1) {
                required !in childNames
            } else {
                val parentName = parts[0]
                val parentDoc = children.find { it.name == parentName && it.isDirectory }
                if (parentDoc == null) {
                    true
                } else {
                    val grandChildren = parentDoc.listFiles().map { it.name }.toSet()
                    parts[1] !in grandChildren
                }
            }
        }

        if (missing.isNotEmpty()) {
            return ValidationResult(false, "Missing required files: ${missing.joinToString(", ")}", missing)
        }

        return ValidationResult(true, "All required files found", emptyList())
    }

    fun import(treeUri: Uri): ImportResult {
        val startMs = System.currentTimeMillis()
        val targetDir = getTargetDir()

        targetDir.deleteRecursively()
        targetDir.mkdirs()

        val documentFile = DocumentFile.fromTreeUri(context, treeUri)
            ?: return ImportResult(0, "Failed to open source tree")

        var fileCount = 0
        try {
            val children = documentFile.listFiles()
            for (child in children) {
                fileCount += copyRecursive(child, targetDir)
            }
        } catch (e: Exception) {
            Log.e(TAG, "Import failed: ${e.message}", e)
            targetDir.deleteRecursively()
            return ImportResult(fileCount, "Import failed: ${e.message}")
        }

        val elapsed = (System.currentTimeMillis() - startMs) / 1000
        Log.i(TAG, "Imported $fileCount files in ${elapsed}s")

        val validation = validateSource(treeUri)
        writeManifest(
            ImportManifest(
                sourceUri = treeUri.toString(),
                importTime = java.text.SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS", java.util.Locale.US).format(java.util.Date()),
                fileCount = fileCount,
                validationStatus = if (validation.valid) "ok" else "partial",
                validatedFiles = REQUIRED_FILES.filter { f ->
                    validation.missing.isEmpty() || f !in validation.missing
                }
            )
        )

        return ImportResult(fileCount, null)
    }

    private fun writeManifest(manifest: ImportManifest) {
        val json = JSONObject().apply {
            put("source_uri", manifest.sourceUri)
            put("import_time", manifest.importTime)
            put("file_count", manifest.fileCount)
            put("validation_status", manifest.validationStatus)
            put("validated_files", manifest.validatedFiles.joinToString(","))
        }
        getManifestFile().writeText(json.toString(2))
        Log.i(TAG, "Manifest written: validation=${manifest.validationStatus}, files=${manifest.fileCount}")
    }

    private fun copyRecursive(source: DocumentFile, targetDir: File): Int {
        if (source.isDirectory) {
            val subDir = File(targetDir, source.name ?: return 0)
            subDir.mkdirs()
            var count = 0
            val children = source.listFiles()
            for (child in children) {
                count += copyRecursive(child, subDir)
            }
            return count
        } else {
            val targetFile = File(targetDir, source.name ?: return 0)
            return try {
                context.contentResolver.openInputStream(source.uri)?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                1
            } catch (e: IOException) {
                Log.e(TAG, "Failed to copy ${source.name}: ${e.message}")
                0
            } catch (e: SecurityException) {
                Log.e(TAG, "Permission denied for ${source.name}: ${e.message}")
                0
            }
        }
    }

    data class ValidationResult(
        val valid: Boolean,
        val message: String,
        val missing: List<String>
    )

    data class ImportResult(
        val fileCount: Int,
        val error: String?
    ) {
        val isSuccess: Boolean get() = error == null
    }
}
