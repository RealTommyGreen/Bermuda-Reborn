package com.bermuda.reborn.touch

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
                Log.i(TAG, "Config version mismatch (v${config.schemaVersion} -> v${TOUCH_OVERLAY_CONFIG_VERSION}), migrating")
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
        if (config.schemaVersion < 7) {
            return config.copy(
                schemaVersion = TOUCH_OVERLAY_CONFIG_VERSION,
                layoutLocked = true,
                buttons = defaultButtons()
            )
        }

        // v7 → v9: update control_action buttons to keep positions, update actions & icons
        val migratedButtons = config.buttons.map { button ->
            val newActions = migrateActionsForButton(button)
            val newIcon = migrateIconForButton(button)
            val newLabel = migrateLabelForButton(button)
            button.copy(actions = newActions, icon = newIcon, label = newLabel)
        }

        return config.copy(
            schemaVersion = TOUCH_OVERLAY_CONFIG_VERSION,
            layoutLocked = true,
            buttons = migratedButtons
        )
    }

    private val buttonActionMigrations: Map<String, List<TouchButtonAction>> = mapOf(
        "btn_menu" to listOf(TouchButtonAction(type = "control_action", mode = "tap", button = "menu_back")),
        "btn_use"  to listOf(TouchButtonAction(type = "control_action", mode = "tap", button = "use")),
        "btn_run"  to listOf(TouchButtonAction(type = "control_action", mode = "hold", button = "run")),
        "btn_weapon" to listOf(TouchButtonAction(type = "control_action", mode = "tap", button = "weapon_toggle")),
        "btn_jump" to listOf(TouchButtonAction(type = "control_action", mode = "hold", button = "jump_button"))
    )

    private fun migrateActionsForButton(button: TouchButtonConfig): List<TouchButtonAction> {
        return buttonActionMigrations[button.id] ?: button.actions
    }

    private val buttonIconMigrations: Map<String, String> = mapOf(
        "btn_menu" to "menu",
        "btn_use"  to "use",
        "btn_run"  to "run",
        "btn_weapon" to "weapon",
        "btn_jump" to "jump",
        "btn_status" to "status",
        "btn_inv" to "inventory"
    )

    private fun migrateIconForButton(button: TouchButtonConfig): String? {
        return buttonIconMigrations[button.id] ?: button.icon
    }

    private val buttonLabelMigrations: Map<String, String> = mapOf(
        "btn_menu" to "Menu",
        "btn_use"  to "Use",
        "btn_run"  to "Run",
        "btn_weapon" to "Weapon",
        "btn_jump" to "Jump",
        "btn_status" to "Status",
        "btn_inv" to "Inventory"
    )

    private fun migrateLabelForButton(button: TouchButtonConfig): String {
        return buttonLabelMigrations[button.id] ?: button.label
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