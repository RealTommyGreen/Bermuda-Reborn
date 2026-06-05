package com.bermuda.reborn.touch

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.KeyEvent
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import com.bermuda.reborn.ControllerDeviceDetector

class ControllerMappingDialog(
    private val context: Context,
    private val controllerConfig: ControllerConfig,
    private val onConfigChanged: (ControllerConfig) -> Unit
) {
    fun show() {
        var currentMapping = controllerConfig.mapping.toMutableMap()
        var listeningAction: String? = null
        lateinit var statusText: TextView
        val rowViews = mutableMapOf<String, TextView>()

        fun buttonForAction(action: String): String =
            currentMapping.entries.firstOrNull { it.value == action }?.key ?: "-"

        fun refreshRows() {
            rowViews.forEach { (act, tv) -> tv.text = buttonForAction(act) }
        }

        fun remap(action: String, pickedButton: String) {
            val currentButton = buttonForAction(action)
            val swappedAction = currentMapping[pickedButton]
            currentMapping[pickedButton] = action
            if (swappedAction != null && currentButton != "-") {
                currentMapping[currentButton] = swappedAction
            } else if (currentButton != "-") {
                currentMapping.remove(currentButton)
            }
            listeningAction = null
            statusText.text = "Tap an action row to remap its button."
            refreshRows()
            onConfigChanged(controllerConfig.copy(mapping = currentMapping.toMap()))
        }

        fun startListening(action: String) {
            listeningAction = action
            val actionLabel = ControllerConfig.actionLabels[action] ?: action
            statusText.text = "Press a controller button for $actionLabel."
        }

        val scrollView = ScrollView(context).apply { isFillViewport = false }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22.dp, 18.dp, 22.dp, 16.dp)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 10.dp.toFloat()
                setColor(0xEE111820.toInt())
                setStroke(1.dp, SURFACE_STROKE)
            }
        }

        container.addView(title("Controller Mapping"))

        statusText = TextView(context).apply {
            text = "Tap an action row to remap its button."
            textSize = 12f
            setTextColor(ACCENT)
            setPadding(0, 0, 0, 10.dp)
        }
        container.addView(statusText)

        for (action in ControllerConfig.actions) {
            val row = mappingRow(action, buttonForAction(action), { startListening(action) }) { pickedButton ->
                remap(action, pickedButton)
            }
            rowViews[action] = row.second
            container.addView(row.first)
        }

        container.addView(separator())

        container.addView(dialogButton("Reset to Defaults", 0xFF1A3240.toInt(), 0xFF4A7A9A.toInt()) {
            currentMapping.clear()
            currentMapping.putAll(ControllerConfig.defaultControllerMapping())
            listeningAction = null
            statusText.text = "Tap an action row to remap its button."
            refreshRows()
            onConfigChanged(controllerConfig.copy(mapping = currentMapping.toMap()))
        })

        scrollView.addView(container)

        val dialog = AlertDialog.Builder(context)
            .setView(scrollView)
            .setPositiveButton("Close") { _, _ -> }
            .create()

        dialog.setOnKeyListener { _, keyCode, event ->
            val action = listeningAction ?: return@setOnKeyListener false
            if (event.action != KeyEvent.ACTION_DOWN) return@setOnKeyListener true
            val pickedButton = controllerButtonName(keyCode, event.source) ?: return@setOnKeyListener true
            remap(action, pickedButton)
            true
        }
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
        dialog.show()
    }

    private fun mappingRow(
        action: String,
        currentButton: String,
        onListen: () -> Unit,
        onPick: (String) -> Unit
    ): Pair<View, TextView> {
        val label = ControllerConfig.actionLabels[action] ?: action
        val row = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(0, 6.dp, 0, 6.dp)
        }

        val actionLabel = TextView(context).apply {
            text = label
            textSize = 14f
            setTextColor(TEXT)
            layoutParams = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
        }
        row.addView(actionLabel)

        val buttonBadge = TextView(context).apply {
            text = currentButton
            textSize = 14f
            setTextColor(TEXT)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(16.dp, 6.dp, 16.dp, 6.dp)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 6.dp.toFloat()
                setColor(0xFF2A4A3A.toInt())
                setStroke(1.dp, 0xFF4A9A5A.toInt())
            }
        }
        row.addView(buttonBadge)

        row.setOnClickListener { onListen() }
        row.setOnLongClickListener {
            showButtonPicker(currentButton, action, onPick)
            true
        }

        row.background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 6.dp.toFloat()
            setColor(0x00000000)
        }
        row.isClickable = true
        row.isFocusable = true
        row.layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply {
            bottomMargin = 4.dp
        }

        return Pair(row, buttonBadge)
    }

    private fun showButtonPicker(currentButton: String, action: String, onPick: (String) -> Unit) {
        val buttonItems = ControllerConfig.buttons.toTypedArray()
        val actionLabel = ControllerConfig.actionLabels[action] ?: action

        AlertDialog.Builder(context)
            .setTitle("Button for $actionLabel")
            .setItems(buttonItems) { _, which -> onPick(buttonItems[which]) }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun controllerButtonName(keyCode: Int, source: Int): String? {
        if (!ControllerDeviceDetector.isControllerSource(source)) return null
        return when (keyCode) {
            KeyEvent.KEYCODE_BUTTON_A -> "A"
            KeyEvent.KEYCODE_BUTTON_B -> "B"
            KeyEvent.KEYCODE_BUTTON_X -> "X"
            KeyEvent.KEYCODE_BUTTON_Y -> "Y"
            KeyEvent.KEYCODE_BUTTON_START -> "START"
            KeyEvent.KEYCODE_BUTTON_SELECT -> "SELECT"
            KeyEvent.KEYCODE_BUTTON_L1 -> "L1"
            KeyEvent.KEYCODE_BUTTON_R1 -> "R1"
            KeyEvent.KEYCODE_BUTTON_THUMBL -> "L3"
            else -> null
        }
    }

    private fun title(text: String) = TextView(context).apply {
        this.text = text
        textSize = 18f
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        setTextColor(TEXT)
        setPadding(0, 0, 0, 18.dp)
    }

    private fun separator() = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1.dp).apply {
            bottomMargin = 4.dp
            topMargin = 8.dp
        }
        setBackgroundColor(SURFACE_STROKE)
    }

    private fun dialogButton(text: String, fill: Int, stroke: Int, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            this.text = text
            textSize = 14f
            setTextColor(TEXT)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(16.dp, 12.dp, 16.dp, 12.dp)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE
                cornerRadius = 8.dp.toFloat()
                setColor(fill)
                setStroke(1.dp, stroke)
            }
            setOnClickListener { onClick() }
        }.also {
            it.layoutParams = LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            ).apply {
                bottomMargin = 10.dp
            }
        }
    }

    private val Int.dp: Int get() = (this * context.resources.displayMetrics.density).toInt()

    companion object {
        private const val TEXT = 0xFFFFFFFF.toInt()
        private const val ACCENT = 0xFFFFC17A.toInt()
        private const val SURFACE_STROKE = 0x667D8DA0
    }
}
