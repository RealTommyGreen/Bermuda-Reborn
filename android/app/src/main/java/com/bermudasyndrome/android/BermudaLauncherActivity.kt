package com.bermudasyndrome.android

import android.app.Activity
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.Gravity
import android.view.View
import android.widget.Button
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.app.AlertDialog

class BermudaLauncherActivity : Activity() {

    companion object {
        private const val TAG = "BSLauncher"
        private const val REQUEST_IMPORT = 1001
    }

    private var progressBar: ProgressBar? = null
    private var statusText: TextView? = null
    private var importButton: Button? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (!isTaskRoot) {
            val intent = intent
            if (intent.hasCategory(Intent.CATEGORY_LAUNCHER) && Intent.ACTION_MAIN == intent.action) {
                finish()
                return
            }
        }

        val importer = SafImporter(this)

        if (importer.isImportValid()) {
            Log.i(TAG, "Import valid, launching game")
            startGame()
            return
        }

        createImportUI()
    }

    private fun createImportUI() {
        val root = FrameLayout(this)

        val background = ImageView(this).apply {
            setImageResource(R.drawable.launcher_background)
            scaleType = ImageView.ScaleType.CENTER_CROP
            adjustViewBounds = false
        }
        root.addView(background, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        root.addView(View(this).apply {
            setBackgroundColor(0x66000000)
        }, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER
            setPadding(64, 32, 64, 32)
        }

        val title = TextView(this).apply {
            text = "Bermuda Syndrome"
            textSize = 28f
            setTextColor(0xFFFFFFFF.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 48, 0, 16)
        }
        content.addView(title)

        val subtitle = TextView(this).apply {
            text = "To play, select your Bermuda Syndrome game folder.\nThe folder must contain BERMUDA.SPR, BERMUDA.WGP,\nSCN/-01.SCN, and MIDI/TITLE.MID."
            textSize = 14f
            setTextColor(0xFFAAAAAA.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 8, 0, 32)
        }
        content.addView(subtitle)

        importButton = Button(this).apply {
            text = "Select Game Folder"
            textSize = 18f
            setBackgroundColor(0xFF4A90D9.toInt())
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(48, 16, 48, 16)
            setOnClickListener { startFolderPicker() }
        }
        content.addView(importButton)

        progressBar = ProgressBar(this).apply {
            visibility = ProgressBar.GONE
            setPadding(0, 24, 0, 0)
        }
        content.addView(progressBar)

        statusText = TextView(this).apply {
            textSize = 13f
            setTextColor(0xFFCCCCCC.toInt())
            gravity = Gravity.CENTER
            setPadding(0, 16, 0, 0)
        }
        content.addView(statusText)

        root.addView(content, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.MATCH_PARENT
        ))

        setContentView(root)
    }

    private fun startFolderPicker() {
        try {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
                addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                )
            }
            startActivityForResult(intent, REQUEST_IMPORT)
        } catch (e: Exception) {
            Log.e(TAG, "Failed to start folder picker: ${e.message}", e)
            showError("Could not open folder picker: ${e.message}")
        }
    }

    @Deprecated("Use registerForActivityResult instead")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != REQUEST_IMPORT) return
        if (resultCode != Activity.RESULT_OK || data?.data == null) {
            Log.i(TAG, "Import cancelled by user")
            return
        }

        val treeUri = data.data!!

        try {
            contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        } catch (e: SecurityException) {
            Log.w(TAG, "Could not persist URI permission: ${e.message}")
        }

        performImport(treeUri)
    }

    private fun performImport(treeUri: Uri) {
        importButton?.isEnabled = false
        progressBar?.visibility = ProgressBar.VISIBLE
        statusText?.text = "Validating..."

        val importer = SafImporter(this)

        // Run on background via simple thread
        Thread {
            val validation = importer.validateSource(treeUri)
            if (!validation.valid) {
                runOnUiThread {
                    progressBar?.visibility = ProgressBar.GONE
                    importButton?.isEnabled = true
                    showError(validation.message)
                }
                return@Thread
            }

            runOnUiThread { statusText?.text = "Importing game files..." }

            val result = importer.import(treeUri)

            runOnUiThread {
                progressBar?.visibility = ProgressBar.GONE
                if (result.isSuccess) {
                    statusText?.text = "Imported ${result.fileCount} files successfully!"
                    startGame()
                } else {
                    importButton?.isEnabled = true
                    showError(result.error ?: "Unknown import error")
                }
            }
        }.start()
    }

    private fun startGame() {
        val intent = Intent(this, BermudaActivity::class.java).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK)
        }
        startActivity(intent)
        finish()
    }

    private fun showError(message: String) {
        Log.e(TAG, message)
        statusText?.text = message
        AlertDialog.Builder(this)
            .setTitle("Import Error")
            .setMessage(message)
            .setPositiveButton("OK", null)
            .show()
    }
}
