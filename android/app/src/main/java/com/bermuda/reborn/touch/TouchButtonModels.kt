package com.bermuda.reborn.touch

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val TOUCH_OVERLAY_CONFIG_VERSION = 11
const val CONTROLLER_CONFIG_VERSION = 1

const val SCREEN_MODE_4_3 = 0
const val SCREEN_MODE_16_9_STRETCHED = 1

@Serializable
data class TouchOverlayConfig(
    @SerialName("schema_version") val schemaVersion: Int = TOUCH_OVERLAY_CONFIG_VERSION,
    val enabled: Boolean = true,
    @SerialName("layout_locked") val layoutLocked: Boolean = true,
    val buttons: List<TouchButtonConfig> = emptyList(),
    @SerialName("menu_buttons") val menuButtons: List<TouchButtonConfig> = emptyList(),
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

// Exported from the validated device preset in Downloads/bermuda_touch_preset.json.
fun defaultButtons(): List<TouchButtonConfig> = listOf(
    TouchButtonConfig(id = "btn_menu", label = "Menu", icon = "menu", shape = BUTTON_SHAPE_CIRCLE,
        x = 0.032916665f, y = 0.03425926f, size = 0.180f, alpha = 0.35f, visible = true,
        actions = listOf(TouchButtonAction(type = "control_action", mode = "tap", button = "menu_back"))),
    TouchButtonConfig(id = "btn_inv", label = "Inventory", icon = "inventory", shape = BUTTON_SHAPE_CIRCLE,
        x = 0.032916665f, y = 0.19074073f, size = 0.180f, alpha = 0.35f, visible = true,
        actions = listOf(TouchButtonAction(type = "key", mode = "tap", keyName = "TAB"))),
    TouchButtonConfig(id = "dpad", label = "", icon = "dpad_map", shape = BUTTON_SHAPE_SQUARE,
        x = 0.035f, y = 0.3888889f, size = 0.405f, alpha = 0.30f, visible = true, dpadDoubleTapRun = true,
        actions = listOf(TouchButtonAction(type = "dpad", mode = "hold"))),
    TouchButtonConfig(id = "btn_quick_save", label = "Quick Save", icon = "quick_save", shape = BUTTON_SHAPE_CIRCLE,
        x = 0.9125f, y = 0.03425926f, size = 0.160f, alpha = 0.35f, visible = true,
        actions = listOf(TouchButtonAction(type = "key_combo", mode = "tap", keyNames = listOf("ALT", "S")))),
    TouchButtonConfig(id = "btn_quick_load", label = "Quick Load", icon = "quick_load", shape = BUTTON_SHAPE_CIRCLE,
        x = 0.8616667f, y = 0.03425926f, size = 0.160f, alpha = 0.35f, visible = true,
        actions = listOf(TouchButtonAction(type = "key_combo", mode = "tap", keyNames = listOf("ALT", "L")))),
    TouchButtonConfig(id = "btn_status", label = "Status", icon = "status", shape = BUTTON_SHAPE_CIRCLE,
        x = 0.80833334f, y = 0.03425926f, size = 0.160f, alpha = 0.35f, visible = true,
        actions = listOf(TouchButtonAction(type = "key", mode = "hold", keyName = "CTRL"))),
    TouchButtonConfig(id = "btn_use", label = "Use", icon = "use", shape = BUTTON_SHAPE_CIRCLE,
        x = 0.83958334f, y = 0.3888889f, size = 0.220f, alpha = 0.35f, visible = true,
        actions = listOf(TouchButtonAction(type = "control_action", mode = "tap", button = "use"))),
    TouchButtonConfig(id = "btn_run", label = "Run", icon = "run", shape = BUTTON_SHAPE_CIRCLE,
        x = 0.7875f, y = 0.50555557f, size = 0.220f, alpha = 0.35f, visible = true,
        actions = listOf(TouchButtonAction(type = "control_action", mode = "hold", button = "run"))),
    TouchButtonConfig(id = "btn_weapon", label = "Weapon", icon = "weapon", shape = BUTTON_SHAPE_CIRCLE,
        x = 0.8925f, y = 0.50555557f, size = 0.220f, alpha = 0.35f, visible = true,
        actions = listOf(TouchButtonAction(type = "control_action", mode = "tap", button = "weapon_toggle"))),
    TouchButtonConfig(id = "btn_jump", label = "Jump", icon = "jump", shape = BUTTON_SHAPE_CIRCLE,
        x = 0.83958334f, y = 0.62222224f, size = 0.220f, alpha = 0.35f, visible = true,
        actions = listOf(TouchButtonAction(type = "control_action", mode = "hold", button = "jump_button")))
)

fun defaultMenuButtons(): List<TouchButtonConfig> {
    val byId = defaultButtons().associateBy { it.id }
    return listOfNotNull(
        byId["btn_menu"]?.copy(
            label = "Menu", icon = "menu",
            x = 0.875f, y = 0.3888889f, size = 0.260f,
            alpha = 0.35f
        ),
        byId["btn_use"]?.copy(
            label = "OK", icon = "ok",
            x = 0.805f, y = 0.54444444f, size = 0.260f,
            alpha = 0.35f
        ),
        byId["btn_jump"]?.copy(
            label = "Use", icon = "use",
            x = 0.805f, y = 0.54444444f, size = 0.260f,
            alpha = 0.35f,
            actions = listOf(TouchButtonAction(type = "control_action", mode = "tap", button = "use"))
        ),
        byId["dpad"]?.copy(
            x = 0.035f, y = 0.3888889f, size = 0.405f,
            alpha = 0.30f
        )
    )
}
