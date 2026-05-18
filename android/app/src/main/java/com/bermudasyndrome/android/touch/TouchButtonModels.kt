package com.bermudasyndrome.android.touch

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

const val TOUCH_OVERLAY_CONFIG_VERSION = 3

@Serializable
data class TouchOverlayConfig(
    @SerialName("schema_version") val schemaVersion: Int = TOUCH_OVERLAY_CONFIG_VERSION,
    val enabled: Boolean = true,
    @SerialName("layout_locked") val layoutLocked: Boolean = true,
    val buttons: List<TouchButtonConfig> = emptyList()
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

const val BUTTON_SHAPE_CIRCLE = "circle"
const val BUTTON_SHAPE_SQUARE = "square"
const val BUTTON_SHAPE_RECTANGLE = "rectangle"

// Bermuda default touch overlay layout
fun defaultButtons(): List<TouchButtonConfig> = listOf(
    // D-Pad (bottom-left)
    TouchButtonConfig(id = "dpad", label = "", icon = "dpad_map", shape = BUTTON_SHAPE_SQUARE,
        x = 0.14f, y = 0.68f, size = 0.22f, alpha = 0.40f, visible = true,
        actions = listOf(TouchButtonAction(type = "dpad", mode = "hold"))),
    // Action buttons (right side, top to bottom)
    TouchButtonConfig(id = "btn_jump", label = "Jump", icon = "jump", shape = BUTTON_SHAPE_CIRCLE,
        x = 0.80f, y = 0.10f, size = 0.085f, alpha = 0.45f, visible = true,
        actions = listOf(TouchButtonAction(type = "key", mode = "hold", keyName = "UP"))),
    TouchButtonConfig(id = "btn_use", label = "Use", icon = "use", shape = BUTTON_SHAPE_CIRCLE,
        x = 0.80f, y = 0.25f, size = 0.085f, alpha = 0.45f, visible = true,
        actions = listOf(TouchButtonAction(type = "key", mode = "tap", keyName = "ENTER"))),
    TouchButtonConfig(id = "btn_weapon", label = "Weapon", icon = "weapon", shape = BUTTON_SHAPE_CIRCLE,
        x = 0.80f, y = 0.40f, size = 0.085f, alpha = 0.45f, visible = true,
        actions = listOf(TouchButtonAction(type = "key", mode = "tap", keyName = "SPACE"))),
    TouchButtonConfig(id = "btn_run", label = "Run", icon = "run", shape = BUTTON_SHAPE_CIRCLE,
        x = 0.92f, y = 0.25f, size = 0.080f, alpha = 0.40f, visible = true,
        actions = listOf(TouchButtonAction(type = "key", mode = "hold", keyName = "SHIFT"))),
    TouchButtonConfig(id = "btn_inv", label = "Inventory", icon = "inventory", shape = BUTTON_SHAPE_CIRCLE,
        x = 0.80f, y = 0.55f, size = 0.080f, alpha = 0.40f, visible = true,
        actions = listOf(TouchButtonAction(type = "key", mode = "tap", keyName = "TAB"))),
    TouchButtonConfig(id = "btn_status", label = "Status", icon = "status", shape = BUTTON_SHAPE_CIRCLE,
        x = 0.92f, y = 0.40f, size = 0.075f, alpha = 0.38f, visible = true,
        actions = listOf(TouchButtonAction(type = "key", mode = "hold", keyName = "CTRL"))),
    TouchButtonConfig(id = "btn_menu", label = "Menu", icon = "menu", shape = BUTTON_SHAPE_CIRCLE,
        x = 0.92f, y = 0.55f, size = 0.075f, alpha = 0.38f, visible = true,
        actions = listOf(TouchButtonAction(type = "key", mode = "tap", keyName = "ESCAPE"))),
    // Mouse buttons (bottom-center)
    TouchButtonConfig(id = "mouse_left", label = "L", icon = "mouse_left", shape = BUTTON_SHAPE_CIRCLE,
        x = 0.42f, y = 0.82f, size = 0.085f, alpha = 0.45f, visible = true,
        actions = listOf(TouchButtonAction(type = "mouse_button", button = "left", mode = "hold"))),
    TouchButtonConfig(id = "mouse_right", label = "R", icon = "mouse_right", shape = BUTTON_SHAPE_CIRCLE,
        x = 0.58f, y = 0.82f, size = 0.085f, alpha = 0.45f, visible = true,
        actions = listOf(TouchButtonAction(type = "mouse_button", button = "right", mode = "hold")))
)
