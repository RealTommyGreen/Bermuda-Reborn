package com.bermuda.reborn.touch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.drawable.GradientDrawable
import android.view.View

class TouchOverlaySettingsButtonView(context: Context) : View(context) {

    private val gearPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt()
        style = Paint.Style.STROKE
        strokeWidth = 2.2f * resources.displayMetrics.density
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

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val cx = width / 2f
        val cy = height / 2f
        val s = minOf(width, height) * 0.28f

        canvas.drawCircle(cx, cy, s * 0.55f, gearPaint)
        canvas.drawCircle(cx, cy, s * 0.18f, gearPaint)
        for (i in 0..7) {
            val a = i * Math.PI.toFloat() / 4f
            val x1 = cx + kotlin.math.cos(a) * s * 0.65f
            val y1 = cy + kotlin.math.sin(a) * s * 0.65f
            val x2 = cx + kotlin.math.cos(a) * s * 0.92f
            val y2 = cy + kotlin.math.sin(a) * s * 0.92f
            canvas.drawLine(x1, y1, x2, y2, gearPaint)
        }
    }

    private fun Int.dpToPx(): Int = (this * resources.displayMetrics.density).toInt()
}
