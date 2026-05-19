package com.bermudasyndrome.android.touch

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Spinner
import android.widget.TextView
import com.bermudasyndrome.android.BermudaActivity

class TouchOverlaySettingsDialog(
    private val context: Context,
    private val config: TouchOverlayConfig,
    private val onConfigChanged: (TouchOverlayConfig) -> Unit,
    private val onResetAll: () -> Unit,
    private val onDeleteAll: () -> Unit
) {
    fun show() {
        var currentConfig = config

        val scrollView = ScrollView(context).apply { isFillViewport = false }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22.dp, 18.dp, 22.dp, 16.dp)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = 10.dp.toFloat()
                setColor(0xEE111820.toInt()); setStroke(1.dp, SURFACE_STROKE)
            }
        }

        container.addView(title("Touch Overlay Settings"))

        // --- D-Pad ---
        val dpadRunCheckBox = checkBox("Double tap left/right to run", currentConfig.dpadDoubleTapRunEnabled) {
            currentConfig = currentConfig.copy(dpadDoubleTapRunEnabled = it)
            onConfigChanged(currentConfig)
        }
        container.addView(sectionLabel("D-Pad"))
        container.addView(dpadRunCheckBox)

        // --- Cheats ---
        val godModeCheckBox = checkBox("God Mode / No Hit", currentConfig.cheatGodMode) {
            currentConfig = currentConfig.copy(cheatGodMode = it)
            onConfigChanged(currentConfig)
            BermudaActivity.nativeSetCheat(0, it)
        }
        val infiniteAmmoCheckBox = checkBox("Infinite Ammo", currentConfig.cheatInfiniteAmmo) {
            currentConfig = currentConfig.copy(cheatInfiniteAmmo = it)
            onConfigChanged(currentConfig)
            BermudaActivity.nativeSetCheat(1, it)
        }
        val allWeaponsCheckBox = checkBox("All Weapons", currentConfig.cheatAllWeapons) {
            currentConfig = currentConfig.copy(cheatAllWeapons = it)
            onConfigChanged(currentConfig)
            BermudaActivity.nativeSetCheat(2, it)
        }
        container.addView(sectionLabel("Cheats (v1)"))
        container.addView(godModeCheckBox)
        container.addView(infiniteAmmoCheckBox)
        container.addView(allWeaponsCheckBox)

        // --- Screen Mode ---
        val screenModes = listOf("4:3 Aspect Correct", "16:9 Stretched (Gameplay)")
        val screenModeSpinner = Spinner(context).apply {
            adapter = object : ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, screenModes) {
                override fun getView(pos: Int, cv: View?, parent: ViewGroup) =
                    (super.getView(pos, cv, parent) as TextView).apply { setTextColor(TEXT); textSize = 14f }
                override fun getDropDownView(pos: Int, cv: View?, parent: ViewGroup) =
                    (super.getDropDownView(pos, cv, parent) as TextView).apply { setTextColor(TEXT); textSize = 14f; setBackgroundColor(0xFF111820.toInt()) }
            }
            setSelection(currentConfig.screenMode.coerceIn(0, 1))
            background = fieldBackground()
            setPopupBackgroundDrawable(GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(0xFF111820.toInt()); setStroke(1.dp, SURFACE_STROKE) })
        }
        container.addView(sectionLabel("Screen Mode"))
        container.addView(screenModeSpinner)

        // --- Separator ---
        container.addView(separator())

        // --- Danger zone ---
        container.addView(sectionLabel("Layout"))
        container.addView(dialogButton("Reset to Defaults", 0xFF1A3240.toInt(), 0xFF4A7A9A.toInt()) { onResetAll() })
        container.addView(dialogButton("Delete All Buttons", 0xFF3A1A1A.toInt(), 0xFF8A4A4A.toInt()) { onDeleteAll() })

        scrollView.addView(container)

        val dialog = AlertDialog.Builder(context)
            .setView(scrollView)
            .setPositiveButton("Close") { _, _ ->
                val selectedMode = screenModeSpinner.selectedItemPosition.coerceIn(0, 1)
                currentConfig = currentConfig.copy(screenMode = selectedMode)
                onConfigChanged(currentConfig)
                BermudaActivity.nativeSetScreenMode(selectedMode)
            }
            .create()

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
        dialog.show()
    }

    private fun title(text: String) = TextView(context).apply {
        this.text = text; textSize = 18f
        setTypeface(Typeface.DEFAULT, Typeface.BOLD); setTextColor(TEXT)
        setPadding(0, 0, 0, 18.dp)
    }

    private fun sectionLabel(text: String) = TextView(context).apply {
        this.text = text; textSize = 12f
        setTypeface(Typeface.DEFAULT, Typeface.BOLD)
        setTextColor(ACCENT); setPadding(0, 14.dp, 0, 6.dp)
    }

    private fun checkBox(text: String, checked: Boolean, onChange: (Boolean) -> Unit) =
        CheckBox(context).apply {
            this.text = text; textSize = 14f
            setTextColor(TEXT)
            buttonTintList = tint(ACCENT)
            isChecked = checked
            setOnCheckedChangeListener { _, isChecked -> onChange(isChecked) }
        }

    private fun dialogButton(text: String, fill: Int, stroke: Int, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            this.text = text; textSize = 14f; setTextColor(TEXT)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            gravity = Gravity.CENTER
            setPadding(16.dp, 12.dp, 16.dp, 12.dp)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = 8.dp.toFloat()
                setColor(fill); setStroke(1.dp, stroke)
            }
            setOnClickListener { onClick() }
        }.also {
            val lp = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
            lp.bottomMargin = 10.dp; it.layoutParams = lp
        }
    }

    private fun separator() = View(context).apply {
        layoutParams = LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1.dp).apply {
            bottomMargin = 4.dp; topMargin = 8.dp
        }
        setBackgroundColor(SURFACE_STROKE)
    }

    private fun fieldBackground() = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; cornerRadius = 7.dp.toFloat()
        setColor(0xAA1D2A36.toInt()); setStroke(1.dp, SURFACE_STROKE)
    }

    private fun tint(color: Int) = android.content.res.ColorStateList.valueOf(color)
    private val Int.dp: Int get() = (this * context.resources.displayMetrics.density).toInt()

    companion object {
        private const val TEXT = 0xFFFFFFFF.toInt()
        private const val ACCENT = 0xFFFFC17A.toInt()
        private const val SURFACE_STROKE = 0x667D8DA0
    }
}
