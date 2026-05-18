package com.bermudasyndrome.android.touch

import android.content.Context
import android.util.Log
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class TouchButtonStore(private val filesDir: File) {

    private val jsonFormat = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val configFile: File
        get() = File(filesDir, CONFIG_PATH)

    fun loadOrDefault(): TouchOverlayConfig {
        if (!configFile.exists()) {
            Log.i(TAG, "No existing config, using built-in defaults")
            val defaults = defaultConfig()
            save(defaults)
            return defaults
        }
        return try {
            val raw = configFile.readText()
            val config = jsonFormat.decodeFromString<TouchOverlayConfig>(raw)
            if (config.schemaVersion != TOUCH_OVERLAY_CONFIG_VERSION) {
                Log.i(TAG, "Config version mismatch, normalizing")
                val normalized = config.copy(schemaVersion = TOUCH_OVERLAY_CONFIG_VERSION, layoutLocked = true)
                save(normalized)
                normalized
            } else {
                config
            }
        } catch (e: SerializationException) {
            Log.w(TAG, "Corrupt config, loading defaults: ${e.message}")
            val defaults = defaultConfig()
            save(defaults)
            defaults
        } catch (e: Exception) {
            Log.w(TAG, "Could not read config: ${e.message}")
            defaultConfig()
        }
    }

    fun save(config: TouchOverlayConfig) {
        try {
            val parent = configFile.parentFile
            if (parent != null && !parent.exists()) parent.mkdirs()
            configFile.writeText(jsonFormat.encodeToString(config))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save config: ${e.message}")
        }
    }

    fun defaultConfig(): TouchOverlayConfig = TouchOverlayConfig(
        schemaVersion = TOUCH_OVERLAY_CONFIG_VERSION,
        buttons = defaultButtons()
    )

    fun importFromJson(raw: String): TouchOverlayConfig =
        jsonFormat.decodeFromString<TouchOverlayConfig>(raw)

    fun exportConfig(config: TouchOverlayConfig, targetFile: File) {
        targetFile.writeText(jsonFormat.encodeToString(config))
    }

    companion object {
        private const val TAG = "TouchButtonStore"
        private const val CONFIG_PATH = "touch_buttons.json"
    }
}
