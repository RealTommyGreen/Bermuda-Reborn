package com.bermudasyndrome.android.touch

import android.app.Activity
import android.graphics.drawable.GradientDrawable
import android.util.Log
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.TextView

class TouchOverlayController(
    private val filesDir: java.io.File,
    private val activity: Activity,
    private val root: ViewGroup
) {
    private val store = TouchButtonStore(filesDir)
    private val dispatcher = TouchInputDispatcher()
    private var config: TouchOverlayConfig? = null
    private val buttonViews = mutableListOf<TouchOverlayButtonView>()
    private var overlayContainer: FrameLayout? = null
    private var attached = false
    private var containerWidth = 0
    private var containerHeight = 0
    private var saveDebounceRunnable: Runnable? = null
    private var schlossButton: TouchOverlayLockButtonView? = null
    private var plusButton: TextView? = null
    private var gearButton: TouchOverlaySettingsButtonView? = null
    private var gridView: TouchOverlayGridView? = null

    fun attach() {
        if (attached) return
        attached = true

        config = store.loadOrDefault()
        config = config!!.copy(layoutLocked = true)

        val container = FrameLayout(activity).apply {
            isClickable = false; isFocusable = false
            clipChildren = false; clipToPadding = false
        }
        root.clipChildren = false; root.clipToPadding = false
        root.addView(container, createMatchParentLayoutParams())
        root.bringChildToFront(container)
        overlayContainer = container

        root.addOnLayoutChangeListener(layoutChangeListener)
        root.post {
            captureContainerSize()
            createGridView()
            createSystemButtons()
            createButtonViews()
            updateSchlossButtonState()
        }
    }

    fun detach() {
        releasePressedInputs()
        removeAllButtonViews()
        removeSystemButtons()
        removeGridView()
        root.removeOnLayoutChangeListener(layoutChangeListener)
        overlayContainer?.let { (it.parent as? ViewGroup)?.removeView(it) }
        overlayContainer = null
        saveDebounceRunnable?.let { root.removeCallbacks(it) }
        saveDebounceRunnable = null
        attached = false
    }

    fun releasePressedInputs() {
        dispatcher.releaseAll()
        buttonViews.forEach { it.releaseIfHeld() }
    }

    fun reloadFromStore() {
        config = store.loadOrDefault()
        config = config!!.copy(layoutLocked = true)
        root.post {
            removeAllButtonViews()
            captureContainerSize()
            updateGridViewState()
            createButtonViews()
            updateButtonDraggable()
            updateSchlossButtonState()
        }
    }

    fun resetToDefaults() {
        config = store.defaultConfig()
        store.save(config!!)
        root.post {
            removeAllButtonViews()
            captureContainerSize()
            updateGridViewState()
            createButtonViews()
            updateButtonDraggable()
            updateSchlossButtonState()
        }
        Log.i(TAG, "Reset to default config")
    }

    fun deleteAllButtons() {
        config = TouchOverlayConfig().copy(buttons = emptyList())
        store.save(config!!)
        root.post {
            removeAllButtonViews()
            updateGridViewState()
            updateButtonDraggable()
            updateSchlossButtonState()
        }
        Log.i(TAG, "Deleted all touch overlay buttons")
    }

    private fun captureContainerSize() {
        containerWidth = root.width; containerHeight = root.height
    }

    private val layoutChangeListener = View.OnLayoutChangeListener { _, _, _, _, _, _, _, _, _ ->
        root.post {
            val nw = root.width; val nh = root.height
            if (nw > 0 && nh > 0 && (nw != containerWidth || nh != containerHeight)) {
                containerWidth = nw; containerHeight = nh
                updateGridViewState()
                repositionAllButtons()
                repositionSystemButtons()
            }
        }
    }

    private fun createGridView() {
        val container = overlayContainer ?: return
        if (gridView != null) return
        val view = TouchOverlayGridView(activity).apply {
            isClickable = false; isFocusable = false
            gridSizePx = gridSizePx()
            visibility = if (config?.layoutLocked == false) View.VISIBLE else View.GONE
        }
        gridView = view
        container.addView(view, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.MATCH_PARENT))
    }

    private fun updateGridViewState() {
        gridView?.let {
            it.gridSizePx = gridSizePx()
            it.visibility = if (config?.layoutLocked == false) View.VISIBLE else View.GONE
            it.invalidate()
        }
    }

    private fun removeGridView() {
        gridView?.let { (it.parent as? ViewGroup)?.removeView(it) }
        gridView = null
    }

    // ---- System buttons ----
    private fun createSystemButtons() {
        val container = overlayContainer ?: return
        val cfg = config ?: return

        schlossButton = TouchOverlayLockButtonView(activity).apply {
            setLocked(cfg.layoutLocked)
            setOnClickListener { onSchlossTapped() }
            isClickable = true; isFocusable = true
        }
        container.addView(schlossButton, bottomStartLayoutParams(0, SYSTEM_BUTTON_SIZE_DP.dpToPx()))

        plusButton = createSystemButtonView("+").apply {
            setOnClickListener { onPlusTapped() }
            setOnLongClickListener { resetToDefaults(); true }
            visibility = if (cfg.layoutLocked) View.GONE else View.VISIBLE
        }
        container.addView(plusButton, bottomStartLayoutParams(1, SYSTEM_BUTTON_SIZE_DP.dpToPx()))

        gearButton = TouchOverlaySettingsButtonView(activity).apply {
            setOnClickListener { onGearTapped() }
            isClickable = true; isFocusable = true
            visibility = if (cfg.layoutLocked) View.GONE else View.VISIBLE
        }
        container.addView(gearButton, bottomStartLayoutParams(2, SYSTEM_BUTTON_SIZE_DP.dpToPx()))

        ensureSystemButtonsOnTop()
    }

    private fun createSystemButtonView(text: String): TextView =
        TextView(activity).apply {
            this.text = text; textSize = 18f; gravity = Gravity.CENTER
            setTextColor(0xFFFFFFFF.toInt())
            background = GradientDrawable().apply {
                shape = GradientDrawable.RECTANGLE; cornerRadius = 12.dpToPx().toFloat()
                setColor(0xAA111820.toInt()); setStroke(1.dpToPx(), 0x66FFFFFF)
            }
            isClickable = true; isFocusable = true
        }

    private fun repositionSystemButtons() {
        schlossButton?.layoutParams = bottomStartLayoutParams(0, SYSTEM_BUTTON_SIZE_DP.dpToPx())
        plusButton?.layoutParams = bottomStartLayoutParams(1, SYSTEM_BUTTON_SIZE_DP.dpToPx())
        gearButton?.layoutParams = bottomStartLayoutParams(2, SYSTEM_BUTTON_SIZE_DP.dpToPx())
    }

    private fun removeSystemButtons() {
        schlossButton?.let { (it.parent as? ViewGroup)?.removeView(it) }
        plusButton?.let { (it.parent as? ViewGroup)?.removeView(it) }
        gearButton?.let { (it.parent as? ViewGroup)?.removeView(it) }
        schlossButton = null; plusButton = null; gearButton = null
    }

    private fun updateSchlossButtonState() {
        val cfg = config ?: return
        schlossButton?.setLocked(cfg.layoutLocked)
        plusButton?.visibility = if (cfg.layoutLocked) View.GONE else View.VISIBLE
        gearButton?.visibility = if (cfg.layoutLocked) View.GONE else View.VISIBLE
    }

    private fun onPlusTapped() {
        val cfg = config ?: return; val container = overlayContainer ?: return
        if (containerWidth <= 0 || containerHeight <= 0) {
            captureContainerSize()
            if (containerWidth <= 0 || containerHeight <= 0) { Log.w(TAG, "Cannot create button: zero size"); return }
        }
        val newId = "button_${System.currentTimeMillis()}"
        val baseConfig = TouchButtonConfig(id = newId, label = "", icon = null, shape = BUTTON_SHAPE_CIRCLE,
            x = 0.45f, y = 0.75f, size = 0.090f, alpha = 0.45f, visible = true, actions = emptyList())
        val tempConfig = TOUCH_BUTTON_PRESETS.first().applyTo(baseConfig)
        val dialog = TouchOverlayEditDialog(context = activity, buttonConfig = tempConfig,
            onSave = { addNewButton(it) }, onDelete = {})
        dialog.show()
    }

    private fun addNewButton(buttonConfig: TouchButtonConfig) {
        val cfg = config ?: return; val container = overlayContainer ?: return
        val minDim = minOf(containerWidth, containerHeight)
        val (bw, bh) = dimensionsFor(buttonConfig, minDim)
        val leftPx = (buttonConfig.x * containerWidth).toInt().coerceIn(0, (containerWidth - bw).coerceAtLeast(0))
        val topPx = (buttonConfig.y * containerHeight).toInt().coerceIn(0, (containerHeight - bh).coerceAtLeast(0))
        val buttonView = TouchOverlayButtonView(container.context, buttonConfig, dispatcher,
            { onButtonPositionChanged(it) }, { onButtonLongPress(it) }, draggable = !cfg.layoutLocked)
        buttonView.alpha = buttonConfig.alpha
        buttonView.setSnapGridSize(if (cfg.layoutLocked) 0 else gridSizePx())
        buttonView.layoutParams = FrameLayout.LayoutParams(bw, bh).apply { leftMargin = leftPx; topMargin = topPx }
        container.addView(buttonView); buttonViews.add(buttonView)
        ensureSystemButtonsOnTop()
        config = cfg.copy(buttons = cfg.buttons + buttonConfig)
        saveConfig()
        Log.i(TAG, "Created new button: ${buttonConfig.id}")
    }

    private fun onGearTapped() {
        val dialog = TouchOverlaySettingsDialog(context = activity,
            onResetAll = { resetToDefaults() }, onDeleteAll = { deleteAllButtons() })
        dialog.show()
    }

    private fun ensureSystemButtonsOnTop() {
        schlossButton?.let { overlayContainer?.bringChildToFront(it) }
        plusButton?.let { overlayContainer?.bringChildToFront(it) }
        gearButton?.let { overlayContainer?.bringChildToFront(it) }
    }

    private fun updateButtonDraggable() {
        val locked = config?.layoutLocked ?: true
        buttonViews.forEach {
            it.setDraggable(!locked)
            it.setSnapGridSize(if (locked) 0 else gridSizePx())
        }
    }

    private fun createButtonViews() {
        val cfg = config ?: return; val container = overlayContainer ?: return
        if (cfg.buttons.isEmpty()) { Log.w(TAG, "No buttons in config"); return }
        if (containerWidth <= 0 || containerHeight <= 0) {
            Log.w(TAG, "Container has zero size, deferring")
            container.post { captureContainerSize(); createButtonViews() }
            return
        }
        val minDim = minOf(containerWidth, containerHeight)
        for (btnConfig in cfg.buttons) {
            if (!btnConfig.visible) continue
            val buttonView = TouchOverlayButtonView(container.context, btnConfig, dispatcher,
                { onButtonPositionChanged(it) }, { onButtonLongPress(it) }, draggable = !cfg.layoutLocked)
            buttonView.alpha = btnConfig.alpha
            buttonView.setSnapGridSize(if (cfg.layoutLocked) 0 else gridSizePx())
            val (bw, bh) = dimensionsFor(btnConfig, minDim)
            val leftPx = (btnConfig.x * containerWidth).toInt().coerceIn(0, (containerWidth - bw).coerceAtLeast(0))
            val topPx = (btnConfig.y * containerHeight).toInt().coerceIn(0, (containerHeight - bh).coerceAtLeast(0))
            buttonView.layoutParams = FrameLayout.LayoutParams(bw, bh).apply { leftMargin = leftPx; topMargin = topPx }
            container.addView(buttonView); buttonViews.add(buttonView)
        }
        Log.i(TAG, "Created ${buttonViews.size} button views")
        ensureSystemButtonsOnTop()
    }

    private fun repositionAllButtons() {
        val cfg = config ?: return; val minDim = minOf(containerWidth, containerHeight)
        for (view in buttonViews) {
            val btnConfig = cfg.buttons.firstOrNull { it.id == view.config.id } ?: continue
            val (bw, bh) = dimensionsFor(btnConfig, minDim)
            val leftPx = (btnConfig.x * containerWidth).toInt().coerceIn(0, (containerWidth - bw).coerceAtLeast(0))
            val topPx = (btnConfig.y * containerHeight).toInt().coerceIn(0, (containerHeight - bh).coerceAtLeast(0))
            val lp = view.layoutParams as? FrameLayout.LayoutParams ?: continue
            lp.width = bw; lp.height = bh; lp.leftMargin = leftPx; lp.topMargin = topPx
            view.layoutParams = lp
            view.updateAppearance(bw, bh, btnConfig.alpha)
        }
        ensureSystemButtonsOnTop()
    }

    private fun onButtonPositionChanged(buttonId: String) {
        saveDebounceRunnable?.let { root.removeCallbacks(it) }
        saveDebounceRunnable = Runnable { saveCurrentPositions() }
        root.postDelayed(saveDebounceRunnable, SAVE_DEBOUNCE_MS)
    }

    private fun onButtonLongPress(btnConfig: TouchButtonConfig) {
        val currentView = buttonViews.firstOrNull { it.config.id == btnConfig.id }
        val captured = currentView?.getNormalizedPosition(containerWidth, containerHeight)
        val dialog = TouchOverlayEditDialog(context = activity, buttonConfig = btnConfig,
            onSave = { updated ->
                val preserved = if (captured != null) updated.copy(x = captured.first, y = captured.second) else updated
                onButtonEditSaved(preserved)
            },
            onDelete = { onButtonDeleted(it) })
        dialog.show()
    }

    private fun onButtonEditSaved(updatedConfig: TouchButtonConfig) {
        val cfg = config ?: return
        config = cfg.copy(buttons = cfg.buttons.map { if (it.id == updatedConfig.id) updatedConfig else it })
        saveConfig()
        val view = buttonViews.firstOrNull { it.config.id == updatedConfig.id } ?: return
        view.updateConfig(updatedConfig)
        val minDim = minOf(containerWidth, containerHeight)
        val (bw, bh) = dimensionsFor(updatedConfig, minDim)
        val leftPx = (updatedConfig.x * containerWidth).toInt().coerceIn(0, (containerWidth - bw).coerceAtLeast(0))
        val topPx = (updatedConfig.y * containerHeight).toInt().coerceIn(0, (containerHeight - bh).coerceAtLeast(0))
        val lp = view.layoutParams as? FrameLayout.LayoutParams ?: return
        lp.width = bw; lp.height = bh; lp.leftMargin = leftPx; lp.topMargin = topPx
        view.layoutParams = lp
        view.updateAppearance(bw, bh, updatedConfig.alpha)
        ensureSystemButtonsOnTop()
    }

    private fun onButtonDeleted(buttonId: String) {
        val cfg = config ?: return; val container = overlayContainer ?: return
        val view = buttonViews.firstOrNull { it.config.id == buttonId } ?: return
        view.releaseIfHeld(); container.removeView(view); buttonViews.remove(view)
        config = cfg.copy(buttons = cfg.buttons.filter { it.id != buttonId })
        saveConfig()
        Log.i(TAG, "Deleted button: $buttonId")
    }

    private fun saveCurrentPositions() {
        val cfg = config ?: return
        if (containerWidth <= 0 || containerHeight <= 0) return
        val updatedButtons = cfg.buttons.map { btnConfig ->
            val view = buttonViews.firstOrNull { it.config.id == btnConfig.id }
            if (view != null) { val (nx, ny) = view.getNormalizedPosition(containerWidth, containerHeight); btnConfig.copy(x = nx, y = ny) }
            else btnConfig
        }
        config = cfg.copy(buttons = updatedButtons)
        saveConfig()
    }

    private fun removeAllButtonViews() {
        val container = overlayContainer ?: return
        buttonViews.forEach { container.removeView(it) }
        buttonViews.clear()
    }

    private fun onSchlossTapped() {
        val cfg = config ?: return
        val newLocked = !cfg.layoutLocked
        config = cfg.copy(layoutLocked = newLocked)
        updateSchlossButtonState()
        updateButtonDraggable()
        updateGridViewState()
        if (newLocked) saveCurrentPositions()
        Log.i(TAG, "Layout lock toggled: locked=$newLocked")
    }

    private fun saveConfig() { config?.let { store.save(it) } }

    private fun createMatchParentLayoutParams(): ViewGroup.LayoutParams =
        ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)

    private fun bottomStartLayoutParams(index: Int, sizePx: Int): FrameLayout.LayoutParams {
        val margin = 8.dpToPx(); val gap = 6.dpToPx()
        return FrameLayout.LayoutParams(sizePx, sizePx).apply {
            gravity = Gravity.BOTTOM or Gravity.START; bottomMargin = margin; leftMargin = margin + index * (sizePx + gap)
        }
    }

    private fun dimensionsFor(btnConfig: TouchButtonConfig, minDim: Int): Pair<Int, Int> {
        val minPx = MIN_BUTTON_SIZE_DP.dpToPx()
        val height = (btnConfig.size * minDim).toInt().coerceAtLeast(minPx)
        val width = if (btnConfig.shape == BUTTON_SHAPE_RECTANGLE) (height * RECTANGLE_WIDTH_FACTOR).toInt() else height
        return Pair(width, height)
    }

    private fun gridSizePx(): Int = GRID_SIZE_DP.dpToPx().coerceAtLeast(12)
    private fun Int.dpToPx(): Int = (this * activity.resources.displayMetrics.density).toInt()

    companion object {
        private const val TAG = "TouchOverlayController"
        private const val SAVE_DEBOUNCE_MS = 250L
        private const val MIN_BUTTON_SIZE_DP = 28
        private const val SYSTEM_BUTTON_SIZE_DP = 44
        private const val RECTANGLE_WIDTH_FACTOR = 1.55f
        private const val GRID_SIZE_DP = 16
    }
}
