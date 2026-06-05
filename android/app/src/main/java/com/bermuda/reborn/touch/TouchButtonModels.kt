package com.bermuda.reborn.touch

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val TOUCH_OVERLAY_CONFIG_VERSION = 8
const val CONTROLLER_CONFIG_VERSION = 1

const val SCREEN_MODE_4_3 = 0
const val SCREEN_MODE_16_9_STRETCHED = 1

@Serializable
data class TouchOverlayConfig(
    @SerialName("schema_version") val schemaVersion: Int = TOUCH_OVERLAY_CONFIG_VERSION,
    val enabled: Boolean = true,
    @SerialName("layout_locked") val layoutLocked: Boolean = true,
    val buttons: List<TouchButtonConfig> = emptyList(),
    @SerialName("dpad_double_tap_run_enabled") val dpadDoubleTapRunEnabled: Boolean = true,
    @SerialName("cheat_god_mode") val cheatGodMode: Boolean = false,
    @SerialName("cheat_infinite_ammo") val cheatInfiniteAmmo: Boolean = false,
    @SerialName("cheat_all_weapons") val cheatAllWeapons: Boolean = false,
    @SerialName("touch_inventory_enabled") val touchInventoryEnabled: Boolean = true,
    @SerialName("screen_mode") val screenMode: Int = SCREEN_MODE_4_3
)

@Serializable
data class TouchButtonConfig(
    val id: String,
    val label: String = "",
    val icon: String? = null,
    val shape: String = BUTTON_SHAPE_CIRCLE,
    val x: Float,
    val y: Float,
    val size: Float,
    val alpha: Float = 0.45f,
    val visible: Boolean = true,
    @SerialName("anchor_x") val anchorX: String? = null,
    @SerialName("anchor_y") val anchorY: String? = null,
    @SerialName("offset_x") val offsetX: Float? = null,
    @SerialName("offset_y") val offsetY: Float? = null,
    @SerialName("icon_fill") val iconFill: Float = -1f,
    @SerialName("dpad_double_tap_run") val dpadDoubleTapRun: Boolean = false,
    val actions: List<TouchButtonAction> = emptyList()
)

@Serializable
data class TouchButtonAction(
    val type: String,
    val button: String? = null,
    val mode: String = "hold",
    @SerialName("key_code") val keyCode: Int? = null,
    @SerialName("key_name") val keyName: String? = null,
    @SerialName("key_codes") val keyCodes: List<Int> = emptyList(),
    @SerialName("key_names") val keyNames: List<String> = emptyList(),
    @SerialName("text") val text: String? = null,
    @SerialName("modifiers") val modifiers: List<String> = emptyList()
)

@Serializable
data class ControllerConfig(
    @SerialName("schema_version") val schemaVersion: Int = CONTROLLER_CONFIG_VERSION,
    val mapping: Map<String, String> = defaultControllerMapping()
) {
    companion object {
        fun defaultControllerMapping(): Map<String, String> = mapOf(
            "A" to "jump",
            "X" to "run",
            "B" to "weapon",
            "Y" to "use",
            "START" to "menu",
            "SELECT" to "inventory",
            "L1" to "quick_load",
            "R1" to "quick_save",
            "L3" to "status"
        )

        val actions = listOf("jump", "run", "weapon", "use", "menu", "inventory", "quick_load", "quick_save", "status")
        val buttons = listOf("A", "X", "B", "Y", "START", "SELECT", "L1", "R1", "L3")
        val actionLabels = mapOf(
            "jump" to "Jump",
            "run" to "Run",
            "weapon" to "Weapon",
            "use" to "Use",
            "menu" to "Menu",
            "inventory" to "Inventory",
            "quick_load" to "Quick Load",
            "quick_save" to "Quick Save",
            "status" to "Status"
        )
    }
}

const val BUTTON_SHAPE_CIRCLE = "circle"
const val BUTTON_SHAPE_SQUARE = "square"
const val BUTTON_SHAPE_RECTANGLE = "rectangle"
const val BUTTON_ANCHOR_START = "start"
const val BUTTON_ANCHOR_END = "end"
const val BUTTON_ANCHOR_TOP = "top"
const val BUTTON_ANCHOR_BOTTOM = "bottom"

// Bermuda default touch overlay layout, matched to the final 2400x1080 reference layout.
fun defaultButtons(): List<TouchButtonConfig> = listOf(
    // Left side
    TouchButtonConfig(id = "btn_menu", label = "Menu", icon = "menu", shape = BUTTON_SHAPE_CIRCLE,
        x = 0.018f, y = 0.040f, size = 0.165f, alpha = 0.34f, visible = true,
        anchorX = BUTTON_ANCHOR_START, anchorY = BUTTON_ANCHOR_TOP, offsetX = 0.040f, offsetY = 0.040f,
        actions = listOf(TouchButtonAction(type = "key", mode = "tap", keyName = "ESCAPE"))),
    TouchButtonConfig(id = "btn_inv", label = "Inventory", icon = "inventory", shape = BUTTON_SHAPE_CIRCLE,
        x = 0.018f, y = 0.234f, size = 0.165f, alpha = 0.34f, visible = true,
        anchorX = BUTTON_ANCHOR_START, anchorY = BUTTON_ANCHOR_TOP, offsetX = 0.040f, offsetY = 0.234f,
        actions = listOf(TouchButtonAction(type = "key", mode = "tap", keyName = "TAB"))),
    TouchButtonConfig(id = "dpad", label = "", icon = "dpad_map", shape = BUTTON_SHAPE_SQUARE,
        x = 0.052f, y = 0.400f, size = 0.430f, alpha = 0.30f, visible = true, dpadDoubleTapRun = true,
        anchorX = BUTTON_ANCHOR_START, anchorY = BUTTON_ANCHOR_BOTTOM, offsetX = 0.116f, offsetY = 0.170f,
        actions = listOf(TouchButtonAction(type = "dpad", mode = "hold"))),

    // Top-right utility buttons
    TouchButtonConfig(id = "btn_quick_save", label = "Quick Save", icon = "quick_save", shape = BUTTON_SHAPE_CIRCLE,
        x = 0.840f, y = 0.040f, size = 0.165f, alpha = 0.34f, visible = true,
        anchorX = BUTTON_ANCHOR_END, anchorY = BUTTON_ANCHOR_TOP, offsetX = 0.253f, offsetY = 0.040f,
        actions = listOf(TouchButtonAction(type = "key_combo", mode = "tap", keyNames = listOf("ALT", "S")))),
    TouchButtonConfig(id = "btn_quick_load", label = "Quick Load", icon = "quick_load", shape = BUTTON_SHAPE_CIRCLE,
        x = 0.928f, y = 0.040f, size = 0.165f, alpha = 0.34f, visible = true,
        anchorX = BUTTON_ANCHOR_END, anchorY = BUTTON_ANCHOR_TOP, offsetX = 0.060f, offsetY = 0.040f,
        actions = listOf(TouchButtonAction(type = "key_combo", mode = "tap", keyNames = listOf("ALT", "L")))),

    // Right-side action cluster
    TouchButtonConfig(id = "btn_status", label = "Status", icon = "status", shape = BUTTON_SHAPE_CIRCLE,
        x = 0.945f, y = 0.234f, size = 0.165f, alpha = 0.34f, visible = true,
        anchorX = BUTTON_ANCHOR_END, anchorY = BUTTON_ANCHOR_TOP, offsetX = 0.020f, offsetY = 0.234f,
        actions = listOf(TouchButtonAction(type = "key", mode = "hold", keyName = "CTRL"))),
    TouchButtonConfig(id = "btn_use", label = "Use", icon = "use", shape = BUTTON_SHAPE_CIRCLE,
        x = 0.875f, y = 0.425f, size = 0.184f, alpha = 0.34f, visible = true,
        anchorX = BUTTON_ANCHOR_END, anchorY = BUTTON_ANCHOR_TOP, offsetX = 0.163f, offsetY = 0.425f,
        actions = listOf(TouchButtonAction(type = "key", mode = "tap", keyName = "ENTER"))),
    TouchButtonConfig(id = "btn_run", label = "Run", icon = "run", shape = BUTTON_SHAPE_CIRCLE,
        x = 0.823f, y = 0.549f, size = 0.184f, alpha = 0.34f, visible = true,
        anchorX = BUTTON_ANCHOR_END, anchorY = BUTTON_ANCHOR_BOTTOM, offsetX = 0.279f, offsetY = 0.336f,
        actions = listOf(TouchButtonAction(type = "key", mode = "hold", keyName = "SHIFT"))),
    TouchButtonConfig(id = "btn_weapon", label = "Weapon", icon = "weapon", shape = BUTTON_SHAPE_CIRCLE,
        x = 0.940f, y = 0.549f, size = 0.184f, alpha = 0.34f, visible = true,
        anchorX = BUTTON_ANCHOR_END, anchorY = BUTTON_ANCHOR_BOTTOM, offsetX = 0.018f, offsetY = 0.336f,
        actions = listOf(TouchButtonAction(type = "key", mode = "tap", keyName = "SPACE"))),
    TouchButtonConfig(id = "btn_jump", label = "Jump", icon = "jump", shape = BUTTON_SHAPE_CIRCLE,
        x = 0.877f, y = 0.665f, size = 0.184f, alpha = 0.34f, visible = true,
        anchorX = BUTTON_ANCHOR_END, anchorY = BUTTON_ANCHOR_BOTTOM, offsetX = 0.158f, offsetY = 0.221f,
        actions = listOf(TouchButtonAction(type = "key", mode = "hold", keyName = "UP")))
)
