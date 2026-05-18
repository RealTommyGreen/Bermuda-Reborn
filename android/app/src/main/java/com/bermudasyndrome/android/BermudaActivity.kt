package com.bermudasyndrome.android

import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import android.view.WindowManager
import com.bermudasyndrome.android.touch.TouchOverlayController
import org.libsdl.app.SDLActivity
import java.io.File

class BermudaActivity : SDLActivity() {

    companion object {
        private const val TAG = "BermudaActivity"
        private const val ASSET_ROOT = "BERMUDA"
        private const val ASSET_VERSION = 4
        private const val TOUCH_OVERLAY_ENABLED = true
    }

    private var touchOverlayController: TouchOverlayController? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN)

        if (!TOUCH_OVERLAY_ENABLED) {
            Log.i(TAG, "Touch overlay disabled for startup crash isolation")
            return
        }

        val root = getContentView() as? ViewGroup
        if (root != null) {
            touchOverlayController = TouchOverlayController(filesDir, this, root)
            touchOverlayController?.attach()
        } else {
            Log.w(TAG, "SDL content view is not available; touch overlay disabled")
        }
    }

    override fun onPause() {
        touchOverlayController?.releasePressedInputs()
        super.onPause()
    }

    override fun onDestroy() {
        touchOverlayController?.detach()
        touchOverlayController = null
        super.onDestroy()
    }

    override fun getLibraries(): Array<String> = arrayOf("bs")

    override fun getArguments(): Array<String> {
        val assetDir = File(filesDir, "bermuda_assets")
        AssetExtractor(this).extract(
            ASSET_ROOT,
            File(assetDir, ASSET_ROOT),
            ASSET_VERSION,
            requiredFiles = listOf("SCN/-01.SCN", "BERMUDA.SPR", "BERMUDA.WGP")
        )

        val dataPath = File(assetDir, "BERMUDA")
        val savePath = File(filesDir, "saves")
        val musicPath = File(assetDir, ASSET_ROOT).resolve("MUSIC")

        savePath.mkdirs()

        Log.i(TAG, "datapath=${dataPath.absolutePath}")
        Log.i(TAG, "savepath=${savePath.absolutePath}")
        Log.i(TAG, "musicpath=${musicPath.absolutePath}")

        return arrayOf(
            "--datapath=${dataPath.absolutePath}",
            "--savepath=${savePath.absolutePath}",
            "--musicpath=${musicPath.absolutePath}",
            "--fullscreen",
            "--widescreen=default"
        )
    }
}
