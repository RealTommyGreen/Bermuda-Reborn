package com.bermuda.reborn.touch

import android.util.Log
import android.view.KeyEvent
import android.view.MotionEvent
import com.bermuda.reborn.BermudaActivity
import org.libsdl.app.SDLActivity

class TouchInputDispatcher {

    private val heldMouseButtons = mutableSetOf<Int>()
    private val heldKeyCodes = mutableSetOf<Int>()
    private val heldComboKeys = mutableMapOf<String, MutableList<Int>>()
    private val heldContextualButtonKeys = mutableMapOf<String, Int>()
    private var heldDpadDirection: String? = null

    fun performAction(action: TouchButtonAction, pressed: Boolean) {
        performButtonAction(null, action, pressed)
    }

    fun performButtonAction(buttonId: String?, action: TouchButtonAction, pressed: Boolean) {
        if (buttonId != null && action.type == "key") {
            val contextualKeyCode = contextualKeyCode(buttonId, pressed)
            if (contextualKeyCode != null) {
                performRawKeyCode(contextualKeyCode, pressed)
                return
            }
        }

        when (action.type) {
            "mouse_button" -> dispatchMouseAction(action, pressed)
            "key" -> dispatchKeyAction(action, pressed)
            "key_combo" -> dispatchKeyComboAction(action, pressed)
            "text" -> dispatchTextAction(action, pressed)
            "control_action" -> dispatchControlAction(action, pressed)
            "dpad" -> Unit
            else -> Log.w(TAG, "Unknown action type: ${action.type}")
        }
    }

    fun releaseAll() {
        heldMouseButtons.toList().forEach { button ->
            SDLActivity.onNativeMouse(button, ACTION_UP, 0f, 0f, true)
        }
        heldMouseButtons.clear()

        heldComboKeys.values.toList().forEach { codes ->
            codes.reversed().forEach { keyCode ->
                SDLActivity.onNativeKeyUp(keyCode)
                heldKeyCodes.remove(keyCode)
            }
        }
        heldComboKeys.clear()

        heldContextualButtonKeys.values.toList().forEach { keyCode ->
            SDLActivity.onNativeKeyUp(keyCode)
            heldKeyCodes.remove(keyCode)
        }
        heldContextualButtonKeys.clear()

        heldKeyCodes.toList().forEach { keyCode ->
            SDLActivity.onNativeKeyUp(keyCode)
        }
        heldKeyCodes.clear()
        heldDpadDirection = null
    }

    fun performDpadDirection(direction: String?) {
        if (heldDpadDirection == direction) return

        heldDpadDirection?.let { oldDirection ->
            val oldCode = keyNameToCode(oldDirection)
            if (oldCode != null) {
                SDLActivity.onNativeKeyUp(oldCode)
                heldKeyCodes.remove(oldCode)
            }
        }

        heldDpadDirection = null

        if (direction != null) {
            val newCode = keyNameToCode(direction)
            if (newCode != null) {
                SDLActivity.onNativeKeyDown(newCode)
                heldKeyCodes.add(newCode)
                heldDpadDirection = direction
            } else {
                Log.w(TAG, "Unknown dpad direction: $direction")
            }
        }
    }

    fun performKeyName(keyName: String, pressed: Boolean) {
        val keyCode = keyNameToCode(keyName) ?: run {
            Log.w(TAG, "Unknown key name: $keyName")
            return
        }
        performRawKeyCode(keyCode, pressed)
    }

    private fun performRawKeyCode(keyCode: Int, pressed: Boolean) {
        if (pressed) {
            if (!heldKeyCodes.contains(keyCode)) {
                SDLActivity.onNativeKeyDown(keyCode)
                heldKeyCodes.add(keyCode)
            }
        } else if (heldKeyCodes.contains(keyCode)) {
            SDLActivity.onNativeKeyUp(keyCode)
            heldKeyCodes.remove(keyCode)
        }
    }

    private fun contextualKeyCode(buttonId: String, pressed: Boolean): Int? {
        if (!pressed) {
            return heldContextualButtonKeys.remove(buttonId)
        }

        val context = try {
            BermudaActivity.nativeGetTouchInputContext()
        } catch (e: UnsatisfiedLinkError) {
            TOUCH_CONTEXT_GAMEPLAY
        }

        val keyCode = when {
            buttonId == "btn_jump" && (context == TOUCH_CONTEXT_BITMAP_CONFIRM || context == TOUCH_CONTEXT_MENU || context == TOUCH_CONTEXT_VIDEO) ->
                KeyEvent.KEYCODE_ENTER
            buttonId == "btn_weapon" && context == TOUCH_CONTEXT_MENU ->
                KeyEvent.KEYCODE_ESCAPE
            else -> null
        }

        if (keyCode != null) {
            heldContextualButtonKeys[buttonId] = keyCode
        }
        return keyCode
    }

    private fun dispatchMouseAction(action: TouchButtonAction, pressed: Boolean) {
        val mouseButton = toMouseButton(action.button)
        val actionCode = if (pressed) ACTION_DOWN else ACTION_UP
        SDLActivity.onNativeMouse(mouseButton, actionCode, 0f, 0f, true)
        if (pressed) heldMouseButtons.add(mouseButton)
        else heldMouseButtons.remove(mouseButton)
    }

    private fun dispatchKeyAction(action: TouchButtonAction, pressed: Boolean) {
        val keyCode = resolveKeyCode(action) ?: run {
            Log.w(TAG, "Cannot resolve keyCode for action: $action")
            return
        }
        val modifiers = action.modifiers
        if (pressed) {
            sendModifiersDown(modifiers)
            SDLActivity.onNativeKeyDown(keyCode)
            heldKeyCodes.add(keyCode)
        } else {
            SDLActivity.onNativeKeyUp(keyCode)
            heldKeyCodes.remove(keyCode)
            sendModifiersUp(modifiers)
        }
    }

    private fun dispatchKeyComboAction(action: TouchButtonAction, pressed: Boolean) {
        val comboKeys = resolveComboKeys(action) ?: run {
            Log.w(TAG, "Cannot resolve key combo for action: $action")
            return
        }
        if (comboKeys.isEmpty() || comboKeys.size > MAX_COMBO_KEYS) return
        val distinctKeys = comboKeys.distinct()
        val comboId = comboId(action)

        if (pressed) {
            if (heldComboKeys.containsKey(comboId)) releaseCombo(comboId)
            val held = mutableListOf<Int>()
            for (keyCode in distinctKeys) {
                SDLActivity.onNativeKeyDown(keyCode)
                heldKeyCodes.add(keyCode)
                held.add(keyCode)
            }
            heldComboKeys[comboId] = held
        } else {
            releaseCombo(comboId)
        }
    }

    private fun releaseCombo(comboId: String) {
        val held = heldComboKeys.remove(comboId) ?: return
        for (keyCode in held.reversed()) {
            SDLActivity.onNativeKeyUp(keyCode)
            heldKeyCodes.remove(keyCode)
        }
    }

    private fun resolveComboKeys(action: TouchButtonAction): List<Int>? {
        val codes = mutableListOf<Int>()
        for (kc in action.keyCodes) codes.add(kc)
        for (name in action.keyNames) {
            val code = keyNameToCode(name)
            if (code != null) codes.add(code)
            else { Log.w(TAG, "Unknown key_name in combo: $name"); return null }
        }
        if (codes.isNotEmpty()) return codes
        val mainKey = resolveKeyCode(action) ?: return null
        val modKeys = action.modifiers.mapNotNull { modifierKeyCode(it) }
        return modKeys + mainKey
    }

    private fun comboId(action: TouchButtonAction): String {
        val parts = mutableListOf<String>()
        parts.addAll(action.keyCodes.map { it.toString() })
        parts.addAll(action.keyNames)
        if (parts.isEmpty()) {
            action.keyCode?.let { parts.add(it.toString()) }
            action.keyName?.let { parts.add(it) }
        }
        return parts.joinToString("+")
    }

    private fun sendModifiersDown(modifiers: List<String>) {
        modifiers.forEach { mod ->
            val code = modifierKeyCode(mod)
            if (code != null) {
                SDLActivity.onNativeKeyDown(code)
                heldKeyCodes.add(code)
            }
        }
    }

    private fun sendModifiersUp(modifiers: List<String>) {
        modifiers.reversed().forEach { mod ->
            val code = modifierKeyCode(mod)
            if (code != null) {
                SDLActivity.onNativeKeyUp(code)
                heldKeyCodes.remove(code)
            }
        }
    }

    private fun dispatchControlAction(action: TouchButtonAction, pressed: Boolean) {
        val controlAction = action.button?.let { controlActionByName(it) } ?: run {
            Log.w(TAG, "Unknown control_action button: ${action.button}")
            return
        }
        try {
            BermudaActivity.nativePerformControlAction(controlAction, pressed)
        } catch (e: UnsatisfiedLinkError) {
            Log.w(TAG, "nativePerformControlAction not available: ${e.message}")
        }
    }

    private fun dispatchTextAction(action: TouchButtonAction, pressed: Boolean) {
        if (!pressed) return
        val text = action.text ?: return
        for (ch in text) {
            val code = charToKeyCode(ch) ?: continue
            SDLActivity.onNativeKeyDown(code)
            SDLActivity.onNativeKeyUp(code)
        }
    }

    private fun resolveKeyCode(action: TouchButtonAction): Int? {
        action.keyCode?.let { return it }
        action.keyName?.let { return keyNameToCode(it) }
        return null
    }

    companion object {
        private const val TAG = "TouchInputDispatcher"
        private const val MAX_COMBO_KEYS = 3
        private const val ACTION_DOWN = 0
        private const val ACTION_UP = 1
        private const val TOUCH_CONTEXT_GAMEPLAY = 0
        private const val TOUCH_CONTEXT_VIDEO = 1
        private const val TOUCH_CONTEXT_BITMAP_CONFIRM = 2
        private const val TOUCH_CONTEXT_MENU = 3

        fun toMouseButton(name: String?): Int = when (name?.lowercase()) {
            "right" -> MotionEvent.BUTTON_SECONDARY
            "middle" -> MotionEvent.BUTTON_TERTIARY
            else -> MotionEvent.BUTTON_PRIMARY
        }

        fun keyNameToCode(name: String): Int? = when (name.uppercase()) {
            "ESCAPE", "ESC" -> KeyEvent.KEYCODE_ESCAPE
            "ENTER", "RETURN" -> KeyEvent.KEYCODE_ENTER
            "SPACE" -> KeyEvent.KEYCODE_SPACE
            "TAB" -> KeyEvent.KEYCODE_TAB
            "BACKSPACE" -> KeyEvent.KEYCODE_DEL
            "DELETE" -> KeyEvent.KEYCODE_FORWARD_DEL
            "UP" -> KeyEvent.KEYCODE_DPAD_UP
            "DOWN" -> KeyEvent.KEYCODE_DPAD_DOWN
            "LEFT" -> KeyEvent.KEYCODE_DPAD_LEFT
            "RIGHT" -> KeyEvent.KEYCODE_DPAD_RIGHT
            "PAGE_UP" -> KeyEvent.KEYCODE_PAGE_UP
            "PAGE_DOWN" -> KeyEvent.KEYCODE_PAGE_DOWN
            "SHIFT" -> KeyEvent.KEYCODE_SHIFT_LEFT
            "CTRL" -> KeyEvent.KEYCODE_CTRL_LEFT
            "ALT" -> KeyEvent.KEYCODE_ALT_LEFT
            "A" -> KeyEvent.KEYCODE_A; "B" -> KeyEvent.KEYCODE_B; "C" -> KeyEvent.KEYCODE_C
            "D" -> KeyEvent.KEYCODE_D; "E" -> KeyEvent.KEYCODE_E; "F" -> KeyEvent.KEYCODE_F
            "G" -> KeyEvent.KEYCODE_G; "H" -> KeyEvent.KEYCODE_H; "I" -> KeyEvent.KEYCODE_I
            "J" -> KeyEvent.KEYCODE_J; "K" -> KeyEvent.KEYCODE_K; "L" -> KeyEvent.KEYCODE_L
            "M" -> KeyEvent.KEYCODE_M; "N" -> KeyEvent.KEYCODE_N; "O" -> KeyEvent.KEYCODE_O
            "P" -> KeyEvent.KEYCODE_P; "Q" -> KeyEvent.KEYCODE_Q; "R" -> KeyEvent.KEYCODE_R
            "S" -> KeyEvent.KEYCODE_S; "T" -> KeyEvent.KEYCODE_T; "U" -> KeyEvent.KEYCODE_U
            "V" -> KeyEvent.KEYCODE_V; "W" -> KeyEvent.KEYCODE_W; "X" -> KeyEvent.KEYCODE_X
            "Y" -> KeyEvent.KEYCODE_Y; "Z" -> KeyEvent.KEYCODE_Z
            "0" -> KeyEvent.KEYCODE_0; "1" -> KeyEvent.KEYCODE_1; "2" -> KeyEvent.KEYCODE_2
            "3" -> KeyEvent.KEYCODE_3; "4" -> KeyEvent.KEYCODE_4; "5" -> KeyEvent.KEYCODE_5
            "6" -> KeyEvent.KEYCODE_6; "7" -> KeyEvent.KEYCODE_7; "8" -> KeyEvent.KEYCODE_8
            "9" -> KeyEvent.KEYCODE_9
            "MINUS", "-" -> KeyEvent.KEYCODE_MINUS
            "EQUALS", "=" -> KeyEvent.KEYCODE_EQUALS
            "COMMA", "," -> KeyEvent.KEYCODE_COMMA
            "PERIOD", "." -> KeyEvent.KEYCODE_PERIOD
            "SLASH", "/" -> KeyEvent.KEYCODE_SLASH
            "SEMICOLON", ";" -> KeyEvent.KEYCODE_SEMICOLON
            "APOSTROPHE", "'" -> KeyEvent.KEYCODE_APOSTROPHE
            else -> null
        }

        fun modifierKeyCode(name: String): Int? = when (name.uppercase()) {
            "SHIFT" -> KeyEvent.KEYCODE_SHIFT_LEFT
            "CTRL" -> KeyEvent.KEYCODE_CTRL_LEFT
            "ALT" -> KeyEvent.KEYCODE_ALT_LEFT
            else -> null
        }

        fun controlActionByName(name: String): Int? = when (name.lowercase()) {
            "run" -> 0       // CONTROL_ACTION_RUN
            "jump_button" -> 1  // CONTROL_ACTION_JUMP_BUTTON
            "weapon_toggle" -> 2 // CONTROL_ACTION_WEAPON_TOGGLE
            "use" -> 3       // CONTROL_ACTION_USE
            "menu_back" -> 4  // CONTROL_ACTION_MENU_BACK
            "reload" -> 5    // CONTROL_ACTION_RELOAD
            else -> null
        }

        fun charToKeyCode(ch: Char): Int? {
            return try {
                KeyEvent::class.java.getDeclaredField("KEYCODE_${ch.uppercaseChar()}").getInt(null)
            } catch (_: Exception) {
                null
            }
        }
    }
}
