package com.bermudasyndrome.android.touch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.view.View

class TouchOverlayGridView(context: Context) : View(context) {

    var gridSizePx: Int = 16
        set(value) { field = value.coerceAtLeast(8); invalidate() }

    private val gridPaint = Paint().apply {
        color = 0x20FFFFFF
        strokeWidth = 1f
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        if (gridSizePx <= 0) return
        var x = 0
        while (x < width) {
            canvas.drawLine(x.toFloat(), 0f, x.toFloat(), height.toFloat(), gridPaint)
            x += gridSizePx
        }
        var y = 0
        while (y < height) {
            canvas.drawLine(0f, y.toFloat(), width.toFloat(), y.toFloat(), gridPaint)
            y += gridSizePx
        }
    }
}
