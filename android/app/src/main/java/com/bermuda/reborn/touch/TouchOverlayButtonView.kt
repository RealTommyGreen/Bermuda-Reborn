package com.bermuda.reborn.touch

import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.os.Build
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout

class TouchOverlayButtonView(
    context: Context,
    initialConfig: TouchButtonConfig,
    private val dispatcher: TouchInputDispatcher,
    private val dragCallback: (String) -> Unit,
    private val longPressCallback: (TouchButtonConfig) -> Unit,
    draggable: Boolean = false
) : View(context) {

    private var buttonConfig: TouchButtonConfig = initialConfig
    internal val config: TouchButtonConfig get() = buttonConfig

    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xAA111820.toInt(); style = Paint.Style.FILL
    }
    private val activePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xDD2F5F86.toInt(); style = Paint.Style.FILL
    }
    private val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0x80FFFFFF.toInt(); style = Paint.Style.STROKE
        strokeWidth = 1.5f * resources.displayMetrics.density
    }
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt(); textAlign = Paint.Align.CENTER; isFakeBoldText = true
    }
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt(); style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND
        strokeWidth = 3f * resources.displayMetrics.density
    }
    private val iconFillPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = 0xFFFFFFFF.toInt(); style = Paint.Style.FILL
    }

    private val rect = RectF()
    private var activePointerId = -1
    private var isPressed = false
    private var isDragging = false
    private var isLongPress = false
    private var initialTouchTime = 0L
    private var downX = 0f; private var downY = 0f
    private var viewStartLeft = 0; private var viewStartTop = 0
    private var hasMovedPastThreshold = false
    private var isDraggable = draggable
    private var isHoldMode: Boolean = false
    private var isDpad: Boolean = false
    private var currentDpadDirection: String? = null
    private var lastHorizontalDpadTapDirection: String? = null
    private var lastHorizontalDpadTapTime = 0L
    private var dpadRunActive = false
    private var snapGridSizePx: Int = 0
    var globalDpadDoubleTapRunEnabled: Boolean = true

    init { updateHoldMode() }

    fun updateConfig(newConfig: TouchButtonConfig) {
        buttonConfig = newConfig; updateHoldMode(); invalidate()
    }

    private fun updateHoldMode() {
        val action = buttonConfig.actions.firstOrNull() ?: TouchButtonAction(type = "", mode = "hold")
        isHoldMode = action.mode == "hold"
        isDpad = buttonConfig.actions.any { it.type == "dpad" }
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        val action = event.actionMasked
        when (action) {
            MotionEvent.ACTION_DOWN -> {
                if (activePointerId != -1) return false
                activePointerId = event.getPointerId(0)
                setPressedState(true)
                initialTouchTime = System.currentTimeMillis()
                downX = getRawX(event, 0); downY = getRawY(event, 0)
                viewStartLeft = left; viewStartTop = top
                hasMovedPastThreshold = false; isDragging = false; isLongPress = false
                if (canDispatchInput() && isDpad && isHoldMode) updateDpadDirection(event.x, event.y)
                else if (canDispatchInput() && isHoldMode) dispatchActions(true)
                else if (canDispatchInput()) dispatchActions(true)
                return true
            }
            MotionEvent.ACTION_MOVE -> {
                if (activePointerId == -1) return true
                val pi = event.findPointerIndex(activePointerId)
                if (pi < 0) return true
                val dx = getRawX(event, pi) - downX; val dy = getRawY(event, pi) - downY
                val dist = Math.sqrt((dx * dx + dy * dy).toDouble())
                if (!hasMovedPastThreshold && dist > TOUCH_SLOP) hasMovedPastThreshold = true
                if (!isDragging) {
                    if (System.currentTimeMillis() - initialTouchTime > LONG_PRESS_MS) isLongPress = true
                    if (hasMovedPastThreshold && isDraggable) {
                        isDragging = true
                        if (canDispatchInput() && isHoldMode) { dispatchActions(false); setPressedState(false) }
                    }
                }
                if (isDragging) {
                    moveWithinParent(viewStartLeft + dx.toInt(), viewStartTop + dy.toInt())
                    dragCallback(config.id)
                } else if (canDispatchInput() && isDpad && isHoldMode) {
                    updateDpadDirection(event.getX(pi), event.getY(pi))
                }
                return true
            }
            MotionEvent.ACTION_UP -> {
                val pi = event.findPointerIndex(activePointerId)
                if (pi < 0) return true
                activePointerId = -1
                when {
                    isDragging -> { setPressedState(false); notifyPositionChanged() }
                    isLongPress && !hasMovedPastThreshold && isDraggable -> { setPressedState(false); longPressCallback(config) }
                    canDispatchInput() && isDpad && isHoldMode -> { releaseDpadDirection(); setPressedState(false) }
                    canDispatchInput() && isHoldMode -> { dispatchActions(false); setPressedState(false) }
                    canDispatchInput() -> { releaseTapActionsDelayed(); setPressedState(false) }
                    else -> setPressedState(false)
                }
                isDragging = false; isLongPress = false
                return true
            }
            MotionEvent.ACTION_POINTER_DOWN -> {
                if (activePointerId != -1) return false
                val index = event.actionIndex
                activePointerId = event.getPointerId(index)
                setPressedState(true)
                initialTouchTime = System.currentTimeMillis()
                downX = getRawX(event, index); downY = getRawY(event, index)
                viewStartLeft = left; viewStartTop = top
                hasMovedPastThreshold = false; isDragging = false; isLongPress = false
                if (canDispatchInput() && isDpad && isHoldMode) updateDpadDirection(event.getX(index), event.getY(index))
                else if (canDispatchInput() && isHoldMode) dispatchActions(true)
                else if (canDispatchInput()) dispatchActions(true)
                return true
            }
            MotionEvent.ACTION_POINTER_UP -> {
                val pointerIndex = event.actionIndex
                if (event.getPointerId(pointerIndex) == activePointerId) {
                    if (isDragging) { setPressedState(false); notifyPositionChanged() }
                    else if (isLongPress && !hasMovedPastThreshold && isDraggable) longPressCallback(config)
                    else if (canDispatchInput() && isDpad && isHoldMode) releaseDpadDirection()
                    else if (canDispatchInput() && isHoldMode) dispatchActions(false)
                    else if (canDispatchInput()) releaseTapActionsDelayed()
                    setPressedState(false); activePointerId = -1
                    isDragging = false; isLongPress = false
                }
                return true
            }
            MotionEvent.ACTION_CANCEL -> {
                if (activePointerId != -1) {
                    if (isDragging) notifyPositionChanged()
                    else if (canDispatchInput() && isDpad && isHoldMode) releaseDpadDirection()
                    else if (canDispatchInput() && isHoldMode) dispatchActions(false)
                    else if (canDispatchInput()) dispatchActions(false)
                }
                setPressedState(false); activePointerId = -1
                isDragging = false; isLongPress = false
                return true
            }
        }
        return false
    }

    private fun notifyPositionChanged() { dragCallback(config.id) }

    fun getNormalizedPosition(parentWidth: Int, parentHeight: Int): Pair<Float, Float> {
        val lp = layoutParams as? FrameLayout.LayoutParams
        val currentLeft = lp?.leftMargin ?: left
        val currentTop = lp?.topMargin ?: top
        return Pair(
            if (parentWidth > 0) currentLeft.toFloat() / parentWidth else config.x,
            if (parentHeight > 0) currentTop.toFloat() / parentHeight else config.y
        )
    }

    fun releaseIfHeld() {
        if (isPressed && isDpad && isHoldMode && !isDragging) { releaseDpadDirection(); setPressedState(false); activePointerId = -1 }
        else if (isPressed && isHoldMode && !isDragging) { dispatchActions(false); setPressedState(false); activePointerId = -1 }
        else if (isPressed) { setPressedState(false); activePointerId = -1 }
    }

    fun updateAppearance(widthPx: Int, heightPx: Int, alpha: Float) {
        layoutParams?.let { it.width = widthPx; it.height = heightPx }
        this.alpha = if (!isPressed) alpha else (alpha * 1.6f).coerceAtMost(1.0f)
        invalidate()
    }

    fun setDraggable(enabled: Boolean) { isDraggable = enabled }
    fun setSnapGridSize(gridSizePx: Int) { snapGridSizePx = gridSizePx.coerceAtLeast(0) }

    private fun setPressedState(pressed: Boolean) {
        if (isPressed != pressed) {
            isPressed = pressed
            alpha = if (pressed) (buttonConfig.alpha * 1.6f).coerceAtMost(1.0f) else buttonConfig.alpha
            invalidate()
        }
    }

    private fun dispatchActions(pressed: Boolean) {
        for (action in buttonConfig.actions) dispatcher.performButtonAction(buttonConfig.id, action, pressed)
    }

    private fun releaseTapActionsDelayed() {
        postDelayed({ dispatchActions(false) }, TAP_RELEASE_DELAY_MS)
    }

    private fun canDispatchInput(): Boolean = !isDraggable

    private fun updateDpadDirection(x: Float, y: Float) {
        val direction = dpadDirection(x, y) ?: return
        if (direction != currentDpadDirection) {
            releaseDpadRun()
            maybeActivateDpadRun(direction)
            dispatcher.performDpadDirection(direction)
            currentDpadDirection = direction
        }
    }

    private fun releaseDpadDirection() {
        releaseDpadRun()
        dispatcher.performDpadDirection(null)
        currentDpadDirection = null
    }

    private fun dpadDirection(x: Float, y: Float): String? {
        val cx = width / 2f; val cy = height / 2f
        val dx = x - cx; val dy = y - cy
        val deadZone = minOf(width, height) * 0.15f
        if (kotlin.math.abs(dx) < deadZone && kotlin.math.abs(dy) < deadZone) return null
        return if (kotlin.math.abs(dx) > kotlin.math.abs(dy))
            (if (dx > 0) "RIGHT" else "LEFT")
        else (if (dy > 0) "DOWN" else "UP")
    }

    private fun maybeActivateDpadRun(direction: String) {
        if (!globalDpadDoubleTapRunEnabled || (direction != "LEFT" && direction != "RIGHT")) return

        val now = System.currentTimeMillis()
        if (lastHorizontalDpadTapDirection == direction && now - lastHorizontalDpadTapTime <= DPAD_DOUBLE_TAP_RUN_MS) {
            dispatcher.performKeyName("SHIFT", true)
            dpadRunActive = true
        }
        lastHorizontalDpadTapDirection = direction
        lastHorizontalDpadTapTime = now
    }

    private fun releaseDpadRun() {
        if (dpadRunActive) {
            dispatcher.performKeyName("SHIFT", false)
            dpadRunActive = false
        }
    }

    private fun moveWithinParent(requestedLeft: Int, requestedTop: Int) {
        val parentView = parent as? View
        val parentWidth = parentView?.width ?: 0; val parentHeight = parentView?.height ?: 0
        val snappedLeft = if (isDraggable && snapGridSizePx > 1) ((requestedLeft + snapGridSizePx / 2) / snapGridSizePx) * snapGridSizePx else requestedLeft
        val snappedTop = if (isDraggable && snapGridSizePx > 1) ((requestedTop + snapGridSizePx / 2) / snapGridSizePx) * snapGridSizePx else requestedTop
        val clampedLeft = if (parentWidth > 0) snappedLeft.coerceIn(0, (parentWidth - width).coerceAtLeast(0)) else snappedLeft
        val clampedTop = if (parentHeight > 0) snappedTop.coerceIn(0, (parentHeight - height).coerceAtLeast(0)) else snappedTop
        val lp = layoutParams as? FrameLayout.LayoutParams
        if (lp != null) { lp.leftMargin = clampedLeft; lp.topMargin = clampedTop; layoutParams = lp }
        else layout(clampedLeft, clampedTop, clampedLeft + width, clampedTop + height)
    }

    override fun onDraw(canvas: Canvas) {
        val fillPaint = if (isPressed) activePaint else backgroundPaint
        rect.set(1f, 1f, width - 1f, height - 1f)
        when (buttonConfig.shape.lowercase()) {
            BUTTON_SHAPE_SQUARE -> canvas.drawRoundRect(rect, cornerRadius(0.14f), cornerRadius(0.14f), fillPaint)
            BUTTON_SHAPE_RECTANGLE -> canvas.drawRoundRect(rect, cornerRadius(0.18f), cornerRadius(0.18f), fillPaint)
            else -> canvas.drawOval(rect, fillPaint)
        }
        when (buttonConfig.shape.lowercase()) {
            BUTTON_SHAPE_SQUARE -> canvas.drawRoundRect(rect, cornerRadius(0.14f), cornerRadius(0.14f), borderPaint)
            BUTTON_SHAPE_RECTANGLE -> canvas.drawRoundRect(rect, cornerRadius(0.18f), cornerRadius(0.18f), borderPaint)
            else -> canvas.drawOval(rect, borderPaint)
        }
        val icon = buttonConfig.icon
        if (icon != null) drawIcon(canvas, icon)
        else drawCenteredText(canvas, buttonConfig.label.ifEmpty { actionDisplayName() })
    }

    private fun actionDisplayName(): String {
        val a = buttonConfig.actions.firstOrNull() ?: return ""
        return when (a.type) {
            "mouse_button" -> when (a.button) { "right" -> "R" else -> "L" }
            "key" -> a.keyName ?: ""
            "dpad" -> "D-Pad"
            else -> a.type
        }
    }

    private fun cornerRadius(factor: Float): Float = minOf(width, height) * factor

    private fun drawCenteredText(canvas: Canvas, text: String) {
        textPaint.textSize = minOf(height * 0.42f, width * 0.34f).coerceAtLeast(9f * resources.displayMetrics.density)
        while (textPaint.textSize > 8f * resources.displayMetrics.density &&
            (textPaint.measureText(text) > width * 0.78f || textPaint.fontSpacing > height * 0.52f))
            textPaint.textSize -= resources.displayMetrics.density
        canvas.drawText(text, width / 2f, height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f, textPaint)
    }

    private fun drawIcon(canvas: Canvas, icon: String) {
        iconPaint.strokeWidth = minOf(width, height) * 0.07f
        val cx = width / 2f; val cy = height / 2f; val s = minOf(width, height) * 0.28f
        when (icon) {
            "dpad_map" -> drawDpad(canvas, cx, cy, s)
            "jump" -> drawJump(canvas, cx, cy, s)
            "arrow_up" -> drawArrow(canvas, cx, cy + s, cx, cy - s)
            "arrow_down" -> drawArrow(canvas, cx, cy - s, cx, cy + s)
            "arrow_left" -> drawArrow(canvas, cx + s, cy, cx - s, cy)
            "arrow_right" -> drawArrow(canvas, cx - s, cy, cx + s, cy)
            "mouse_left" -> drawMouse(canvas, cx, cy, s, -1)
            "mouse_right" -> drawMouse(canvas, cx, cy, s, 1)
            "use", "enter" -> drawUse(canvas, cx, cy, s)
            "weapon", "space" -> drawWeapon(canvas, cx, cy, s)
            "run", "run_toggle" -> drawRun(canvas, cx, cy, s)
            "inventory", "tab" -> drawInventory(canvas, cx, cy, s)
            "status", "info" -> drawStatus(canvas, cx, cy, s)
            "menu", "escape" -> drawMenu(canvas, cx, cy, s)
            "quick_save" -> drawSave(canvas, cx, cy, s)
            "quick_load" -> drawLoad(canvas, cx, cy, s)
            "cancel_action" -> drawCancel(canvas, cx, cy, s)
            else -> drawCenteredText(canvas, icon)
        }
    }

    private fun drawArrow(canvas: Canvas, x1: Float, y1: Float, x2: Float, y2: Float) {
        canvas.drawLine(x1, y1, x2, y2, iconPaint)
        val angle = Math.atan2((y2 - y1).toDouble(), (x2 - x1).toDouble()).toFloat()
        val head = minOf(width, height) * 0.16f
        canvas.drawLine(x2, y2, x2 + kotlin.math.cos(angle + 2.53f) * head, y2 + kotlin.math.sin(angle + 2.53f) * head, iconPaint)
        canvas.drawLine(x2, y2, x2 + kotlin.math.cos(angle - 2.53f) * head, y2 + kotlin.math.sin(angle - 2.53f) * head, iconPaint)
    }

    private fun drawMouse(canvas: Canvas, cx: Float, cy: Float, s: Float, activeSide: Int) {
        val mouse = RectF(cx - s * 0.72f, cy - s * 1.05f, cx + s * 0.72f, cy + s * 1.05f)
        canvas.drawRoundRect(mouse, s * 0.55f, s * 0.55f, iconPaint)
        canvas.drawLine(cx, mouse.top + s * 0.1f, cx, cy - s * 0.2f, iconPaint)
        canvas.drawLine(cx - s * 0.72f, cy - s * 0.2f, cx + s * 0.72f, cy - s * 0.2f, iconPaint)
        val dotX = cx + activeSide * s * 0.34f
        canvas.drawCircle(dotX, cy - s * 0.58f, s * 0.16f, iconFillPaint)
    }

    private fun drawJump(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val boot = Path().apply {
            moveTo(cx - s * 0.78f, cy + s * 0.38f)
            lineTo(cx - s * 0.36f, cy + s * 0.16f)
            lineTo(cx - s * 0.2f, cy - s * 0.78f)
            cubicTo(cx - s * 0.02f, cy - s * 0.86f, cx + s * 0.22f, cy - s * 0.84f, cx + s * 0.36f, cy - s * 0.68f)
            lineTo(cx + s * 0.18f, cy + s * 0.04f)
            lineTo(cx + s * 0.72f, cy + s * 0.08f)
            cubicTo(cx + s * 1.02f, cy + s * 0.1f, cx + s * 1.12f, cy + s * 0.36f, cx + s * 0.88f, cy + s * 0.52f)
            lineTo(cx + s * 0.18f, cy + s * 0.76f)
            lineTo(cx - s * 0.64f, cy + s * 0.72f)
            close()
        }
        canvas.drawPath(boot, iconPaint)
        canvas.drawLine(cx - s * 0.34f, cy + s * 0.22f, cx + s * 0.26f, cy + s * 0.72f, iconPaint)
        canvas.drawLine(cx - s * 0.16f, cy - s * 0.48f, cx + s * 0.28f, cy - s * 0.42f, iconPaint)
        canvas.drawLine(cx - s * 0.22f, cy - s * 0.2f, cx + s * 0.22f, cy - s * 0.14f, iconPaint)
    }

    private fun drawUse(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val palm = Path().apply {
            moveTo(cx - s * 0.58f, cy - s * 0.12f)
            cubicTo(cx - s * 0.74f, cy + s * 0.18f, cx - s * 0.68f, cy + s * 0.78f, cx - s * 0.28f, cy + s * 0.94f)
            cubicTo(cx + s * 0.1f, cy + s * 1.08f, cx + s * 0.62f, cy + s * 0.78f, cx + s * 0.64f, cy + s * 0.26f)
            cubicTo(cx + s * 0.66f, cy - s * 0.04f, cx + s * 0.5f, cy - s * 0.18f, cx + s * 0.32f, cy - s * 0.08f)
            lineTo(cx + s * 0.12f, cy + s * 0.18f)
            lineTo(cx - s * 0.1f, cy - s * 0.02f)
            lineTo(cx - s * 0.36f, cy + s * 0.18f)
            close()
        }
        val thumb = Path().apply {
            moveTo(cx - s * 0.5f, cy + s * 0.08f)
            lineTo(cx - s * 1.0f, cy - s * 0.22f)
            cubicTo(cx - s * 1.14f, cy - s * 0.32f, cx - s * 1.02f, cy - s * 0.62f, cx - s * 0.78f, cy - s * 0.5f)
            lineTo(cx - s * 0.42f, cy - s * 0.22f)
        }
        canvas.drawPath(palm, iconPaint)
        canvas.drawPath(thumb, iconPaint)
        val fingerRadius = s * 0.16f
        val fingerTop = cy - s * 0.9f
        val fingerBottom = cy - s * 0.02f
        canvas.drawRoundRect(RectF(cx - s * 0.72f, cy - s * 0.66f, cx - s * 0.44f, cy + s * 0.16f), fingerRadius, fingerRadius, iconPaint)
        canvas.drawRoundRect(RectF(cx - s * 0.36f, fingerTop, cx - s * 0.08f, fingerBottom), fingerRadius, fingerRadius, iconPaint)
        canvas.drawRoundRect(RectF(cx, cy - s * 0.82f, cx + s * 0.28f, cy + s * 0.02f), fingerRadius, fingerRadius, iconPaint)
        canvas.drawRoundRect(RectF(cx + s * 0.36f, cy - s * 0.58f, cx + s * 0.62f, cy + s * 0.12f), fingerRadius, fingerRadius, iconPaint)
        canvas.drawLine(cx - s * 0.32f, cy + s * 0.42f, cx + s * 0.36f, cy + s * 0.42f, iconPaint)
        canvas.drawLine(cx - s * 0.22f, cy + s * 0.62f, cx + s * 0.26f, cy + s * 0.62f, iconPaint)
    }

    private fun drawWeapon(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        canvas.save()
        canvas.rotate(-18f, cx, cy)
        canvas.drawRoundRect(RectF(cx - s * 0.24f, cy - s * 0.18f, cx + s * 0.74f, cy + s * 0.08f), s * 0.06f, s * 0.06f, iconPaint)
        canvas.drawLine(cx + s * 0.72f, cy - s * 0.06f, cx + s * 1.14f, cy - s * 0.06f, iconPaint)
        canvas.drawLine(cx + s * 0.72f, cy + s * 0.04f, cx + s * 1.14f, cy + s * 0.04f, iconPaint)
        canvas.drawLine(cx + s * 1.14f, cy - s * 0.11f, cx + s * 1.14f, cy + s * 0.09f, iconPaint)
        val stock = Path().apply {
            moveTo(cx - s * 0.28f, cy - s * 0.16f)
            lineTo(cx - s * 0.92f, cy - s * 0.4f)
            cubicTo(cx - s * 1.08f, cy - s * 0.2f, cx - s * 1.06f, cy + s * 0.18f, cx - s * 0.86f, cy + s * 0.32f)
            lineTo(cx - s * 0.28f, cy + s * 0.06f)
            close()
        }
        canvas.drawPath(stock, iconPaint)
        canvas.drawRoundRect(RectF(cx - s * 0.04f, cy + s * 0.08f, cx + s * 0.24f, cy + s * 0.78f), s * 0.08f, s * 0.08f, iconPaint)
        canvas.drawArc(RectF(cx + s * 0.2f, cy + s * 0.02f, cx + s * 0.62f, cy + s * 0.48f), 190f, 160f, false, iconPaint)
        canvas.drawLine(cx + s * 0.08f, cy - s * 0.36f, cx + s * 0.46f, cy - s * 0.36f, iconPaint)
        canvas.drawLine(cx + s * 0.18f, cy - s * 0.5f, cx + s * 0.34f, cy - s * 0.22f, iconPaint)
        canvas.restore()
    }

    private fun drawRun(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val p = Paint(iconPaint).apply {
            strokeWidth = minOf(width, height) * 0.09f
            strokeCap = Paint.Cap.ROUND
        }
        canvas.drawLine(cx - s * 0.95f, cy - s * 0.52f, cx + s * 0.58f, cy - s * 0.52f, p)
        canvas.drawLine(cx - s * 0.58f, cy, cx + s * 0.94f, cy, p)
        canvas.drawLine(cx - s * 0.96f, cy + s * 0.52f, cx + s * 0.46f, cy + s * 0.52f, p)
        canvas.drawLine(cx + s * 0.62f, cy - s * 0.52f, cx + s * 0.96f, cy - s * 0.34f, p)
        canvas.drawLine(cx + s * 0.96f, cy, cx + s * 1.16f, cy + s * 0.14f, p)
        canvas.drawLine(cx + s * 0.5f, cy + s * 0.52f, cx + s * 0.84f, cy + s * 0.72f, p)
    }

    private fun drawInventory(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val bag = RectF(cx - s * 0.82f, cy - s * 0.28f, cx + s * 0.82f, cy + s * 0.86f)
        canvas.drawRoundRect(bag, s * 0.16f, s * 0.16f, iconPaint)
        canvas.drawArc(RectF(cx - s * 0.46f, cy - s * 0.88f, cx + s * 0.46f, cy - s * 0.02f), 205f, 130f, false, iconPaint)
        canvas.drawLine(cx - s * 0.42f, cy + s * 0.2f, cx + s * 0.42f, cy + s * 0.2f, iconPaint)
    }

    private fun drawStatus(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val heart = Path().apply {
            moveTo(cx, cy + s * 0.78f)
            cubicTo(cx - s * 1.08f, cy, cx - s * 0.72f, cy - s * 0.82f, cx, cy - s * 0.28f)
            cubicTo(cx + s * 0.72f, cy - s * 0.82f, cx + s * 1.08f, cy, cx, cy + s * 0.78f)
        }
        canvas.drawPath(heart, iconPaint)
        canvas.drawLine(cx - s * 0.84f, cy + s * 0.05f, cx - s * 0.32f, cy + s * 0.05f, iconPaint)
        canvas.drawLine(cx + s * 0.32f, cy + s * 0.05f, cx + s * 0.84f, cy + s * 0.05f, iconPaint)
    }

    private fun drawMenu(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        for (i in -1..1) {
            val y = cy + i * s * 0.52f
            canvas.drawLine(cx - s * 0.78f, y, cx + s * 0.78f, y, iconPaint)
        }
    }

    private fun drawSave(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val disk = RectF(cx - s * 0.78f, cy - s * 0.88f, cx + s * 0.78f, cy + s * 0.88f)
        canvas.drawRoundRect(disk, s * 0.12f, s * 0.12f, iconPaint)
        canvas.drawRect(cx - s * 0.42f, cy - s * 0.72f, cx + s * 0.38f, cy - s * 0.2f, iconPaint)
        canvas.drawLine(cx - s * 0.42f, cy + s * 0.3f, cx + s * 0.42f, cy + s * 0.3f, iconPaint)
        drawArrow(canvas, cx, cy + s * 0.05f, cx, cy + s * 0.62f)
    }

    private fun drawLoad(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val folder = Path().apply {
            moveTo(cx - s * 0.9f, cy - s * 0.38f)
            lineTo(cx - s * 0.28f, cy - s * 0.38f)
            lineTo(cx - s * 0.08f, cy - s * 0.12f)
            lineTo(cx + s * 0.88f, cy - s * 0.12f)
            lineTo(cx + s * 0.72f, cy + s * 0.82f)
            lineTo(cx - s * 0.82f, cy + s * 0.82f)
            close()
        }
        canvas.drawPath(folder, iconPaint)
        drawArrow(canvas, cx, cy - s * 0.92f, cx, cy + s * 0.28f)
    }

    private fun drawCancel(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        canvas.drawLine(cx - s * 0.7f, cy - s * 0.7f, cx + s * 0.7f, cy + s * 0.7f, iconPaint)
        canvas.drawLine(cx + s * 0.7f, cy - s * 0.7f, cx - s * 0.7f, cy + s * 0.7f, iconPaint)
    }

    private fun drawDpad(canvas: Canvas, cx: Float, cy: Float, s: Float) {
        val arm = s * 0.34f; val reach = s * 1.18f; val notch = s * 0.28f
        val outline = Path().apply {
            moveTo(cx - arm, cy - reach); lineTo(cx + arm, cy - reach); lineTo(cx + arm, cy - arm)
            lineTo(cx + reach, cy - arm); lineTo(cx + reach, cy + arm); lineTo(cx + arm, cy + arm)
            lineTo(cx + arm, cy + reach); lineTo(cx - arm, cy + reach); lineTo(cx - arm, cy + arm)
            lineTo(cx - reach, cy + arm); lineTo(cx - reach, cy - arm); lineTo(cx - arm, cy - arm); close()
        }
        val fillP = Paint(iconFillPaint).apply { alpha = 22 }
        val outlineP = Paint(iconPaint).apply { alpha = 150; strokeWidth = minOf(width, height) * 0.032f; style = Paint.Style.STROKE }
        val activeP = Paint(iconPaint).apply { alpha = 230; strokeWidth = minOf(width, height) * 0.045f; style = Paint.Style.STROKE }
        canvas.drawPath(outline, fillP); canvas.drawPath(outline, outlineP)
        canvas.drawLine(cx - notch, cy, cx + notch, cy, outlineP); canvas.drawLine(cx, cy - notch, cx, cy + notch, outlineP)
        when (currentDpadDirection) {
            "UP" -> canvas.drawLine(cx, cy - reach * 0.78f, cx, cy - arm * 1.15f, activeP)
            "DOWN" -> canvas.drawLine(cx, cy + arm * 1.15f, cx, cy + reach * 0.78f, activeP)
            "LEFT" -> canvas.drawLine(cx - reach * 0.78f, cy, cx - arm * 1.15f, cy, activeP)
            "RIGHT" -> canvas.drawLine(cx + arm * 1.15f, cy, cx + reach * 0.78f, cy, activeP)
        }
    }

    private fun getRawX(event: MotionEvent, pi: Int): Float =
        if (Build.VERSION.SDK_INT >= 29) event.getRawX(pi) else { val loc = IntArray(2); getLocationOnScreen(loc); event.getX(pi) + loc[0] }
    private fun getRawY(event: MotionEvent, pi: Int): Float =
        if (Build.VERSION.SDK_INT >= 29) event.getRawY(pi) else { val loc = IntArray(2); getLocationOnScreen(loc); event.getY(pi) + loc[1] }

    companion object {
        private const val LONG_PRESS_MS = 400L
        private const val TAP_RELEASE_DELAY_MS = 120L
        private const val DPAD_DOUBLE_TAP_RUN_MS = 280L
        private const val TOUCH_SLOP = 16.0
    }
}
