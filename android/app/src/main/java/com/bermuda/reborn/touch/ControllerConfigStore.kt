package com.bermuda.reborn.touch

import android.util.Log
import kotlinx.serialization.SerializationException
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File

class ControllerConfigStore(private val filesDir: File) {

    private val jsonFormat = Json { ignoreUnknownKeys = true; prettyPrint = true }

    private val configFile: File
        get() = File(filesDir, CONFIG_PATH)

    fun loadOrDefault(): ControllerConfig {
        if (!configFile.exists()) {
            Log.i(TAG, "No existing controller config, using defaults")
            val defaults = ControllerConfig()
            save(defaults)
            return defaults
        }
        return try {
            val raw = configFile.readText()
            val config = jsonFormat.decodeFromString<ControllerConfig>(raw)
            if (config.schemaVersion != CONTROLLER_CONFIG_VERSION) {
                Log.i(TAG, "Controller config version mismatch, migrating")
                val defaults = ControllerConfig()
                save(defaults)
                defaults
            } else {
                config
            }
        } catch (e: SerializationException) {
            Log.w(TAG, "Corrupt controller config, loading defaults: ${e.message}")
            val defaults = ControllerConfig()
            save(defaults)
            defaults
        } catch (e: Exception) {
            Log.w(TAG, "Could not read controller config: ${e.message}")
            ControllerConfig()
        }
    }

    fun save(config: ControllerConfig) {
        try {
            val parent = configFile.parentFile
            if (parent != null && !parent.exists()) parent.mkdirs()
            configFile.writeText(jsonFormat.encodeToString(config))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to save controller config: ${e.message}")
        }
    }

    companion object {
        private const val TAG = "ControllerConfigStore"
        private const val CONFIG_PATH = "controller_config.json"
    }
}
