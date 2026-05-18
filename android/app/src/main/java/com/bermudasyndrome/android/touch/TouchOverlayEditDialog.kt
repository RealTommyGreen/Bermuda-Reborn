package com.bermudasyndrome.android.touch

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.AdapterView
import android.widget.ArrayAdapter
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Spinner
import android.widget.TextView

class TouchOverlayEditDialog(
    private val context: Context,
    private val buttonConfig: TouchButtonConfig,
    private val onSave: (TouchButtonConfig) -> Unit,
    private val onDelete: (String) -> Unit
) {
    private val presetOptions = TOUCH_BUTTON_PRESETS
    private val presetEntries = buildPresetEntries(presetOptions)
    private fun shapeOptions() = listOf(
        Option(BUTTON_SHAPE_CIRCLE, "Circle"),
        Option(BUTTON_SHAPE_SQUARE, "Square"),
        Option(BUTTON_SHAPE_RECTANGLE, "Rectangle")
    )
    private val sizeValues = floatArrayOf(0.030f, 0.040f, 0.055f, 0.065f, 0.075f, 0.090f, 0.105f, 0.120f, 0.140f, 0.160f, 0.180f, 0.220f, 0.260f, 0.300f, 0.350f, 0.400f, 0.450f)
    private val alphaValues = floatArrayOf(0.15f, 0.25f, 0.35f, 0.45f, 0.55f, 0.65f, 0.75f, 0.85f, 1.00f)

    fun show() {
        val selectedPreset = touchButtonPresetFor(buttonConfig) ?: presetOptions.first()
        val shapes = shapeOptions()

        val scrollView = ScrollView(context).apply { isFillViewport = false }
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22.dp, 18.dp, 22.dp, 16.dp)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = 10.dp.toFloat()
                setColor(0xEE111820.toInt()); setStroke(1.dp, SURFACE_STROKE)
            }
        }

        val presetSpinner = groupedPresetSpinner(
            presetEntries.indexOfFirst { it.preset?.id == selectedPreset.id }.takeIf { it >= 0 } ?: 1)
        val shapeSpinner = spinner(shapes.map { it.label }, shapes.indexOfFirst { it.value == buttonConfig.shape }.takeIf { it >= 0 } ?: 0)
        val dpadRunCheckBox = CheckBox(context).apply {
            text = "Double tap left/right to run"
            textSize = 14f
            setTextColor(TEXT)
            buttonTintList = tint(ACCENT)
            isChecked = buttonConfig.dpadDoubleTapRun
            isEnabled = isDpadPreset(presetSpinner.selectedItemPosition)
        }
        presetSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                dpadRunCheckBox.isEnabled = isDpadPreset(position)
            }
            override fun onNothingSelected(parent: AdapterView<*>?) = Unit
        }

        val sizeLabel = valueText()
        val sizeSeekBar = SeekBar(context).apply {
            max = sizeValues.size - 1; progress = findClosestIndex(sizeValues, buttonConfig.size)
            thumbTintList = tint(ACCENT); progressTintList = tint(ACCENT); progressBackgroundTintList = tint(SURFACE_STROKE)
            setOnSeekBarChangeListener(snapListener(sizeValues, sizeLabel, sizeFormat))
        }

        val alphaLabel = valueText()
        val alphaSeekBar = SeekBar(context).apply {
            max = alphaValues.size - 1; progress = findClosestIndex(alphaValues, buttonConfig.alpha)
            thumbTintList = tint(ACCENT); progressTintList = tint(ACCENT); progressBackgroundTintList = tint(SURFACE_STROKE)
            setOnSeekBarChangeListener(snapListener(alphaValues, alphaLabel, alphaFormat))
        }

        container.addView(title("Edit Button"))
        container.addView(labeledField("Action", presetSpinner))
        container.addView(labeledField("Size", sliderRow(sizeSeekBar, sizeLabel)))
        container.addView(labeledField("Shape", shapeSpinner))
        container.addView(labeledField("Opacity", sliderRow(alphaSeekBar, alphaLabel)))
        container.addView(labeledField("D-Pad", dpadRunCheckBox))

        sizeLabel.text = sizeFormat(sizeValues[sizeSeekBar.progress])
        alphaLabel.text = alphaFormat(alphaValues[alphaSeekBar.progress])
        scrollView.addView(container)

        val dialog = AlertDialog.Builder(context)
            .setView(scrollView)
            .setPositiveButton("Save") { _, _ ->
                val shapes = shapeOptions()
                val preset = presetEntries[presetSpinner.selectedItemPosition.coerceIn(0, presetEntries.size - 1)].preset ?: selectedPreset
                val updated = preset.applyTo(buttonConfig).copy(
                    shape = shapes[shapeSpinner.selectedItemPosition.coerceIn(0, shapes.size - 1)].value ?: BUTTON_SHAPE_CIRCLE,
                    size = sizeValues[sizeSeekBar.progress],
                    alpha = alphaValues[alphaSeekBar.progress],
                    dpadDoubleTapRun = preset.action.type == "dpad" && dpadRunCheckBox.isChecked
                )
                onSave(updated)
            }
            .setNegativeButton("Cancel", null)
            .setNeutralButton("Delete") { _, _ -> onDelete(buttonConfig.id) }
            .create()

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
        dialog.show()
    }

    private fun title(text: String) = TextView(context).apply {
        this.text = text; textSize = 18f; setTypeface(typeface, Typeface.BOLD)
        setTextColor(TEXT); setPadding(0, 0, 0, 12.dp)
    }

    private fun labeledField(label: String, child: View) = LinearLayout(context).apply {
        orientation = LinearLayout.VERTICAL; setPadding(0, 4.dp, 0, 10.dp)
        addView(TextView(context).apply {
            text = label; textSize = 12f; setTypeface(typeface, Typeface.BOLD)
            setTextColor(TEXT_MUTED); setPadding(0, 0, 0, 5.dp)
        })
        addView(child)
    }

    private fun sliderRow(seekBar: SeekBar, valueLabel: TextView) = LinearLayout(context).apply {
        orientation = LinearLayout.HORIZONTAL; gravity = Gravity.CENTER_VERTICAL
        addView(seekBar, LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f))
        addView(valueLabel, LinearLayout.LayoutParams(64.dp, ViewGroup.LayoutParams.WRAP_CONTENT).apply { leftMargin = 12.dp })
    }

    private fun valueText() = TextView(context).apply {
        gravity = Gravity.END; textSize = 12f; setTextColor(TEXT)
    }

    private fun spinner(labels: List<String>, selected: Int) = Spinner(context).apply {
        adapter = object : ArrayAdapter<String>(context, android.R.layout.simple_spinner_item, labels) {
            override fun getView(pos: Int, cv: View?, parent: ViewGroup) =
                (super.getView(pos, cv, parent) as TextView).apply { setTextColor(TEXT); textSize = 14f }
            override fun getDropDownView(pos: Int, cv: View?, parent: ViewGroup) =
                (super.getDropDownView(pos, cv, parent) as TextView).apply { setTextColor(TEXT); textSize = 14f; setBackgroundColor(0xFF111820.toInt()) }
        }
        setSelection(selected.coerceIn(0, labels.size - 1))
        background = fieldBackground()
        setPopupBackgroundDrawable(GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(0xFF111820.toInt()); setStroke(1.dp, SURFACE_STROKE) })
    }

    private fun groupedPresetSpinner(selected: Int) = Spinner(context).apply {
        adapter = object : ArrayAdapter<PresetEntry>(context, android.R.layout.simple_spinner_item, presetEntries) {
            override fun areAllItemsEnabled() = false
            override fun isEnabled(pos: Int) = getItem(pos)?.preset != null
            override fun getView(pos: Int, cv: View?, parent: ViewGroup) =
                (super.getView(pos, cv, parent) as TextView).apply { text = getItem(pos)?.label; setTextColor(TEXT); textSize = 14f }
            override fun getDropDownView(pos: Int, cv: View?, parent: ViewGroup): View {
                val entry = getItem(pos) ?: presetEntries.first()
                return (super.getDropDownView(pos, cv, parent) as TextView).apply {
                    text = entry.label
                    textSize = if (entry.preset == null) 12f else 14f
                    setTypeface(typeface, if (entry.preset == null) Typeface.BOLD else Typeface.NORMAL)
                    setTextColor(if (entry.preset == null) ACCENT else TEXT)
                    setBackgroundColor(0xFF111820.toInt())
                }
            }
        }
        setSelection(selected.coerceIn(0, presetEntries.size - 1))
        background = fieldBackground()
        setPopupBackgroundDrawable(GradientDrawable().apply { shape = GradientDrawable.RECTANGLE; setColor(0xFF111820.toInt()); setStroke(1.dp, SURFACE_STROKE) })
    }

    private fun snapListener(values: FloatArray, label: TextView, format: (Float) -> String) =
        object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(sb: SeekBar?, p: Int, fu: Boolean) { label.text = format(values[p.coerceIn(0, values.size - 1)]) }
            override fun onStartTrackingTouch(sb: SeekBar?) = Unit
            override fun onStopTrackingTouch(sb: SeekBar?) = Unit
        }

    private fun fieldBackground() = GradientDrawable().apply {
        shape = GradientDrawable.RECTANGLE; cornerRadius = 7.dp.toFloat()
        setColor(0xAA1D2A36.toInt()); setStroke(1.dp, SURFACE_STROKE)
    }

    private fun tint(color: Int) = android.content.res.ColorStateList.valueOf(color)
    private fun findClosestIndex(values: FloatArray, target: Float): Int {
        var best = 0; var bestDist = Float.MAX_VALUE
        for (i in values.indices) { val dist = kotlin.math.abs(values[i] - target); if (dist < bestDist) { bestDist = dist; best = i } }
        return best
    }

    private val sizeFormat: (Float) -> String = { v -> String.format("%.3f", v) }
    private val alphaFormat: (Float) -> String = { v -> "${(v * 100).toInt()}%" }
    private val Int.dp: Int get() = (this * context.resources.displayMetrics.density).toInt()
    private val typeface get() = Typeface.DEFAULT

    private data class Option(val value: String?, val label: String)
    private data class PresetEntry(val label: String, val preset: TouchButtonPreset?)

    private fun buildPresetEntries(presets: List<TouchButtonPreset>): List<PresetEntry> {
        val result = mutableListOf<PresetEntry>()
        for ((cat, catPresets) in presets.groupBy { it.category }) {
            result.add(PresetEntry(cat.uppercase(), null))
            catPresets.forEach { result.add(PresetEntry(it.label, it)) }
        }
        return result
    }

    private fun isDpadPreset(position: Int): Boolean =
        presetEntries.getOrNull(position)?.preset?.action?.type == "dpad"

    companion object {
        private const val TEXT = 0xFFFFFFFF.toInt()
        private const val TEXT_MUTED = 0xFFB5C0CC.toInt()
        private const val ACCENT = 0xFFFFC17A.toInt()
        private const val SURFACE_STROKE = 0x667D8DA0
    }
}
