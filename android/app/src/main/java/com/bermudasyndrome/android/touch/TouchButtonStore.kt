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
                Log.i(TAG, "Config version mismatch, migrating")
                val normalized = migrateConfig(config)
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

    private fun migrateConfig(config: TouchOverlayConfig): TouchOverlayConfig {
        val updatedButtons = config.buttons.map { button ->
            when (button.id) {
                "btn_use" -> button.copy(label = "Use", icon = "use")
                "btn_weapon" -> button.copy(label = "Weapon", icon = "weapon")
                "btn_run" -> button.copy(label = "Run", icon = "run")
                "btn_inv" -> button.copy(label = "Inventory", icon = "inventory")
                "btn_status" -> button.copy(label = "Status", icon = "status")
                "btn_menu" -> button.copy(label = "Menu", icon = "menu")
                else -> button
            }
        }.toMutableList()
        val hasJump = updatedButtons.any { button ->
            button.id == "btn_jump" || button.actions.any { it.type == "key" && it.keyName?.uppercase() == "UP" }
        }
        if (!hasJump) {
            defaultButtons().firstOrNull { it.id == "btn_jump" }?.let { updatedButtons.add(it) }
        }
        return config.copy(
            schemaVersion = TOUCH_OVERLAY_CONFIG_VERSION,
            layoutLocked = true,
            buttons = updatedButtons.map { button ->
                if (button.actions.any { it.type == "dpad" }) button else button.copy(dpadDoubleTapRun = false)
            }
        )
    }

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
