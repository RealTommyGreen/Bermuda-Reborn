package com.bermudasyndrome.android.touch

import android.app.AlertDialog
import android.content.Context
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView

class TouchOverlaySettingsDialog(
    private val context: Context,
    private val onResetAll: () -> Unit,
    private val onDeleteAll: () -> Unit
) {

    fun show() {
        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(22.dp, 18.dp, 22.dp, 16.dp)
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = 10.dp.toFloat()
                setColor(0xEE111820.toInt()); setStroke(1.dp, SURFACE_STROKE)
            }
        }

        container.addView(TextView(context).apply {
            text = "Touch Overlay Settings"; textSize = 18f
            setTypeface(Typeface.DEFAULT, Typeface.BOLD); setTextColor(TEXT)
            setPadding(0, 0, 0, 18.dp)
        })

        container.addView(dialogButton("Reset to Defaults", 0xFF1A3240.toInt(), 0xFF4A7A9A.toInt()) { onResetAll() })
        container.addView(dialogButton("Delete All Buttons", 0xFF3A1A1A.toInt(), 0xFF8A4A4A.toInt()) { onDeleteAll() })

        val dialog = AlertDialog.Builder(context)
            .setView(container)
            .setPositiveButton("Close", null)
            .create()

        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(android.R.color.transparent)
        }
        dialog.show()
    }

    private fun dialogButton(text: String, fill: Int, stroke: Int, onClick: () -> Unit): TextView {
        return TextView(context).apply {
            this.text = text; textSize = 14f; setTextColor(TEXT)
            setTypeface(Typeface.DEFAULT, Typeface.BOLD)
            gravity = android.view.Gravity.CENTER
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

    private val Int.dp: Int get() = (this * context.resources.displayMetrics.density).toInt()

    companion object {
        private const val TEXT = 0xFFFFFFFF.toInt()
        private const val SURFACE_STROKE = 0x667D8DA0
    }
}
