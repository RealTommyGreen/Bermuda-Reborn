package com.bermuda.reborn

import android.os.Bundle
import android.util.Log
import android.view.ViewGroup
import com.bermuda.reborn.touch.ControllerConfig
import com.bermuda.reborn.touch.ControllerConfigStore
import com.bermuda.reborn.touch.TouchButtonStore
import com.bermuda.reborn.touch.TouchOverlayController
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.libsdl.app.SDLActivity
import java.io.File

class BermudaActivity : SDLActivity() {

    companion object {
        private const val TAG = "BermudaActivity"
        private const val TOUCH_OVERLAY_ENABLED = true

        @JvmStatic
        external fun nativeSetCheat(cheatId: Int, enabled: Boolean)

        @JvmStatic
        external fun nativeSetScreenMode(mode: Int)

        @JvmStatic
        external fun nativeSetControllerConfig(enabled: Boolean, mapping: String, dpadDoubleTapRunEnabled: Boolean)

        @JvmStatic
        external fun nativeSetTouchInventoryEnabled(enabled: Boolean)

        @JvmStatic
        external fun nativeGetTouchInputContext(): Int

        @JvmStatic
        external fun nativePerformControlAction(action: Int, pressed: Boolean)

        @JvmStatic
        external fun nativeGetControlState(): Int
    }

    private var touchOverlayController: TouchOverlayController? = null
    private var controllerEnabled: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        controllerEnabled = intent.getBooleanExtra("controller_enabled", false)
        Log.i(TAG, "Controller enabled: $controllerEnabled")

        val controllerStore = ControllerConfigStore(filesDir)
        val controllerConfig: ControllerConfig = controllerStore.loadOrDefault()
        val mappingJson = Json.encodeToString(controllerConfig.mapping)
        val touchStore = TouchButtonStore(filesDir)
        val touchConfig = touchStore.loadOrDefault()
        Log.i(TAG, "Controller config loaded, mapping: $mappingJson, dpadRun=${touchConfig.dpadDoubleTapRunEnabled}")
        nativeSetControllerConfig(controllerEnabled, mappingJson, touchConfig.dpadDoubleTapRunEnabled)
        nativeSetTouchInventoryEnabled(touchConfig.touchInventoryEnabled)

        if (!TOUCH_OVERLAY_ENABLED) {
            Log.i(TAG, "Touch overlay disabled for startup crash isolation")
            return
        }

        val root = getContentView() as? ViewGroup
        if (root != null) {
            touchOverlayController = TouchOverlayController(filesDir, this, root, controllerEnabled, controllerConfig)
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
        val importedDir = File(filesDir, SafImporter.IMPORT_DIR).resolve(SafImporter.BERMUDA_DIR)
        val savePath = File(filesDir, "saves")
        val musicPath = importedDir.resolve("MIDI")

        savePath.mkdirs()

        // Copy bundled SoundFont from assets to internal storage
        val sfDir = File(filesDir, "soundfont")
        val sfFile = File(sfDir, "default.sf2")
        if (!sfFile.exists()) {
            try {
                sfDir.mkdirs()
                assets.open("soundfont/default.sf2").use { input ->
                    sfFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }
                Log.i(TAG, "SoundFont copied: ${sfFile.absolutePath} (${sfFile.length()} bytes)")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to copy SoundFont: ${e.message}")
            }
        }

        val soundfontArg = if (sfFile.exists()) "--soundfont=${sfFile.absolutePath}" else ""

        Log.i(TAG, "datapath=${importedDir.absolutePath}")
        Log.i(TAG, "savepath=${savePath.absolutePath}")
        Log.i(TAG, "musicpath=${musicPath.absolutePath}")
        Log.i(TAG, "soundfont=$soundfontArg")

        return arrayOf(
            "--datapath=${importedDir.absolutePath}",
            "--savepath=${savePath.absolutePath}",
            "--musicpath=${musicPath.absolutePath}",
            "--fullscreen",
            "--widescreen=default"
        ) + (if (soundfontArg.isNotEmpty()) arrayOf(soundfontArg) else emptyArray())
    }
}
