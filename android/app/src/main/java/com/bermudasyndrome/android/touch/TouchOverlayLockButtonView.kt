package com.bermudasyndrome.android.touch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.view.View

class TouchOverlayLockButtonView(context: Context) : View(context) {

    private var locked = true

    private val padlockPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * resources.displayMetrics.density
        strokeCap = Paint.Cap.ROUND
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
        val cx = width / 2f
        val cy = height / 2f
        val s = minOf(width, height) * 0.30f

        if (locked) {
            canvas.drawCircle(cx, cy + s * 0.2f, s * 0.78f, padlockPaint)
            canvas.drawRect(cx - s * 0.38f, cy - s * 0.05f, cx + s * 0.38f, cy + s * 0.65f, padlockPaint)
        } else {
            canvas.drawCircle(cx, cy - s * 0.35f, s * 0.78f, padlockPaint)
            canvas.drawLine(cx - s * 0.15f, cy + s * 0.3f, cx + s * 0.4f, cy - s * 0.2f, padlockPaint)
            canvas.drawRect(cx - s * 0.38f, cy + s * 0.15f, cx + s * 0.38f, cy + s * 0.7f, padlockPaint)
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
