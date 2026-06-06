package com.bermuda.reborn.touch

data class TouchButtonPreset(
    val id: String,
    val label: String,
    val icon: String,
    val action: TouchButtonAction,
    val category: String
) {
    fun applyTo(config: TouchButtonConfig): TouchButtonConfig =
        config.copy(label = label, icon = icon, actions = listOf(action))
}

val TOUCH_BUTTON_PRESETS: List<TouchButtonPreset> = listOf(
    // D-Pad
    TouchButtonPreset("dpad", "D-Pad", "dpad_map",
        TouchButtonAction(type = "dpad", mode = "hold"), "Movement"),
    controlActionPreset("jump", "Jump", "jump", "jump_button", "Movement", mode = "hold"),

    // Mouse
    mousePreset("mouse_left", "Left Click", "mouse_left", "left"),
    mousePreset("mouse_right", "Right Click", "mouse_right", "right"),

    // Bermuda — control_action based for core gameplay
    controlActionPreset("use", "Use", "use", "use", "Actions"),
    controlActionPreset("weapon", "Weapon", "weapon", "weapon_toggle", "Actions"),
    controlActionPreset("run", "Run", "run", "run", "Actions", mode = "hold"),
    keyPreset("inventory", "Inventory", "inventory", "TAB", "UI"),
    keyPreset("status", "Status", "status", "CTRL", "UI", mode = "hold"),
    controlActionPreset("menu", "Menu", "menu", "menu_back", "UI"),
    keyPreset("save", "Save", "quick_save", "S", "UI"),
    keyPreset("load", "Load", "quick_load", "L", "UI"),
    keyPreset("slot_next", "Slot +", "arrow_right", "PAGE_UP", "UI"),
    keyPreset("slot_prev", "Slot -", "arrow_left", "PAGE_DOWN", "UI"),

    // Combo
    comboPreset("quick_save_combo", "Quick Save", "quick_save", listOf("ALT", "S"), "UI"),
    comboPreset("quick_load_combo", "Quick Load", "quick_load", listOf("ALT", "L"), "UI")
)

fun touchButtonPresetById(id: String?): TouchButtonPreset? =
    TOUCH_BUTTON_PRESETS.firstOrNull { it.id == id }

fun touchButtonPresetFor(config: TouchButtonConfig): TouchButtonPreset? =
    touchButtonPresetById(config.icon)
        ?: TOUCH_BUTTON_PRESETS.firstOrNull { preset ->
            config.actions.firstOrNull()?.let { actionMatches(preset.action, it) } == true
        }

private fun mousePreset(id: String, label: String, icon: String, button: String): TouchButtonPreset =
    TouchButtonPreset(id, label, icon,
        TouchButtonAction(type = "mouse_button", button = button, mode = "hold"), "Mouse")

private fun keyPreset(id: String, label: String, icon: String, keyName: String,
                       category: String, mode: String = "tap"): TouchButtonPreset =
    TouchButtonPreset(id, label, icon,
        TouchButtonAction(type = "key", mode = mode, keyName = keyName), category)

private fun controlActionPreset(id: String, label: String, icon: String, button: String,
                                 category: String, mode: String = "tap"): TouchButtonPreset =
    TouchButtonPreset(id, label, icon,
        TouchButtonAction(type = "control_action", mode = mode, button = button), category)

private fun comboPreset(id: String, label: String, icon: String,
                         keyNames: List<String>, category: String): TouchButtonPreset =
    TouchButtonPreset(id, label, icon,
        TouchButtonAction(type = "key_combo", mode = "tap", keyNames = keyNames), category)

private fun actionMatches(expected: TouchButtonAction, actual: TouchButtonAction): Boolean {
    if (expected.type != actual.type) return false
    if (expected.mode != actual.mode) return false
    return when (expected.type) {
        "mouse_button" -> expected.button == actual.button
        "key" -> expected.keyName?.uppercase() == actual.keyName?.uppercase()
                && expected.keyCode == actual.keyCode
        "key_combo" -> expected.keyCodes == actual.keyCodes
                && expected.keyNames.map { it.uppercase() } == actual.keyNames.map { it.uppercase() }
        "dpad" -> true
        "control_action" -> expected.button?.lowercase() == actual.button?.lowercase()
        else -> false
    }
}