package com.bermuda.reborn.touch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.drawable.GradientDrawable
import android.view.View

class TouchOverlayLockButtonView(context: Context) : View(context) {

    private var locked = true

    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
    }

    init {
        background = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = 12.dpToPx().toFloat()
            setColor(0xAA111820.toInt())
            setStroke(1.dpToPx(), 0x66FFFFFF)
        }
    }

    fun setLocked(locked: Boolean) {
        this.locked = locked
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val inset = 9.dpToPx().toFloat()
        val bounds = RectF(inset, inset, width - inset, height - inset)
        iconPaint.alpha = if (locked) 255 else 155
        SvgIconManager.renderIcon(canvas, context, "lock", bounds, iconPaint)
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
