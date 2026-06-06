package com.bermuda.reborn.touch

import android.app.Activity
import android.os.Build
import android.util.Log
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import com.bermuda.reborn.BermudaActivity
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.libsdl.app.SDLActivity

class TouchOverlayController(
    private val filesDir: java.io.File,
    private val activity: Activity,
    private val root: ViewGroup,
    private val controllerEnabled: Boolean = false,
    private var controllerConfig: ControllerConfig? = null
) {
    private val store = TouchButtonStore(filesDir)
    private val controllerStore = ControllerConfigStore(filesDir)
    private val dispatcher = TouchInputDispatcher()
    private var config: TouchOverlayConfig? = null
    private val buttonViews = mutableListOf<TouchOverlayButtonView>()
    private var overlayContainer: FrameLayout? = null
    private var attached = false
    private var containerWidth = 0
    private var containerHeight = 0
    private var saveDebounceRunnable: Runnable? = null
    private var schlossButton: TouchOverlayLockButtonView? = null
    private var gearButton: TouchOverlaySettingsButtonView? = null
    private var gridView: TouchOverlayGridView? = null
    private var contextSyncRunnable: Runnable? = null
    private var lastSyncContext = -1
    private var lastSyncGunDrawn = false
    private var lastSyncSwordDrawn = false
    private var contextSyncRunning = false

    fun attach() {
        if (attached) return
        attached = true

        SvgIconManager.init(activity)

        config = store.loadOrDefault()
        config = config!!.copy(layoutLocked = true)

        // Sync persisted cheats and screen mode to native on startup
        BermudaActivity.nativeSetCheat(0, config!!.cheatGodMode)
        BermudaActivity.nativeSetCheat(1, config!!.cheatInfiniteAmmo)
        BermudaActivity.nativeSetCheat(2, config!!.cheatAllWeapons)
        BermudaActivity.nativeSetScreenMode(config!!.screenMode)

        val container = FrameLayout(activity).apply {
            isClickable = false; isFocusable = false
            clipChildren = false; clipToPadding = false
        }
        root.clipChildren = false; root.clipToPadding = false
        root.addView(container, createMatchParentLayoutParams())
        root.bringChildToFront(container)
        overlayContainer = container

        container.setOnTouchListener { _, event -> handleGameCanvasTouch(event) }

        root.addOnLayoutChangeListener(layoutChangeListener)
        root.post {
            captureContainerSize()
            createGridView()
            createSystemButtons()
            createButtonViews()
            syncGlobalConfigToButtonViews()
            updateSchlossButtonState()
            startContextSync()
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
        stopContextSync()
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
            syncGlobalConfigToButtonViews()
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
            syncGlobalConfigToButtonViews()
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

        gearButton = TouchOverlaySettingsButtonView(activity).apply {
            setOnClickListener { onGearTapped() }
            isClickable = true; isFocusable = true
            visibility = if (cfg.layoutLocked) View.GONE else View.VISIBLE
        }
        container.addView(gearButton, bottomStartLayoutParams(1, SYSTEM_BUTTON_SIZE_DP.dpToPx()))

        ensureSystemButtonsOnTop()
    }

    private fun repositionSystemButtons() {
        schlossButton?.layoutParams = bottomStartLayoutParams(0, SYSTEM_BUTTON_SIZE_DP.dpToPx())
        gearButton?.layoutParams = bottomStartLayoutParams(1, SYSTEM_BUTTON_SIZE_DP.dpToPx())
    }

    private fun removeSystemButtons() {
        schlossButton?.let { (it.parent as? ViewGroup)?.removeView(it) }
        gearButton?.let { (it.parent as? ViewGroup)?.removeView(it) }
        schlossButton = null; gearButton = null
    }

    private fun updateSchlossButtonState() {
        val cfg = config ?: return
        schlossButton?.setLocked(cfg.layoutLocked)
        gearButton?.visibility = if (cfg.layoutLocked) View.GONE else View.VISIBLE
    }

    private fun onGearTapped() {
        val cfg = config ?: return
        val cc = controllerConfig
        val dialog = TouchOverlaySettingsDialog(
            context = activity,
            config = cfg,
            controllerEnabled = controllerEnabled,
            controllerConfig = cc,
            onConfigChanged = { updated -> onConfigUpdated(updated) },
            onResetAll = { resetToDefaults() },
            onControllerConfigChanged = { updated ->
                controllerConfig = updated
                controllerStore.save(updated)
                val mappingJson = Json.encodeToString(updated.mapping)
                val dpadRun = config?.dpadDoubleTapRunEnabled ?: true
                BermudaActivity.nativeSetControllerConfig(controllerEnabled, mappingJson, dpadRun)
            },
            onOpenControllerMapping = {
                val currentCc = controllerConfig ?: ControllerConfig()
                openControllerMappingDialog(currentCc)
            }
        )
        dialog.show()
    }

    private fun openControllerMappingDialog(config: ControllerConfig) {
        ControllerMappingDialog(
            context = activity,
            controllerConfig = config,
            onConfigChanged = { updated ->
                controllerConfig = updated
                controllerStore.save(updated)
                val mappingJson = Json.encodeToString(updated.mapping)
                val dpadRun = this.config?.dpadDoubleTapRunEnabled ?: true
                BermudaActivity.nativeSetControllerConfig(controllerEnabled, mappingJson, dpadRun)
            }
        ).show()
    }

    private fun onConfigUpdated(updated: TouchOverlayConfig) {
        config = updated
        saveConfig()
        BermudaActivity.nativeSetTouchInventoryEnabled(updated.touchInventoryEnabled)
        syncGlobalConfigToButtonViews()
        if (controllerEnabled && controllerConfig != null) {
            val mappingJson = Json.encodeToString(controllerConfig!!.mapping)
            BermudaActivity.nativeSetControllerConfig(controllerEnabled, mappingJson, updated.dpadDoubleTapRunEnabled)
        }
    }

    private fun syncGlobalConfigToButtonViews() {
        val cfg = config ?: return
        for (view in buttonViews) {
            view.globalDpadDoubleTapRunEnabled = cfg.dpadDoubleTapRunEnabled
        }
    }

    private fun handleGameCanvasTouch(event: MotionEvent): Boolean {
        if (event.action == MotionEvent.ACTION_DOWN || event.action == MotionEvent.ACTION_UP) {
            val x = event.x
            val y = event.y
            if (!isTouchOnAnyButton(x, y)) {
                val action = if (event.action == MotionEvent.ACTION_DOWN) 0 else 1
                SDLActivity.onNativeMouse(android.view.MotionEvent.BUTTON_PRIMARY, action, x, y, false)
            }
        }
        return false
    }

    private fun isTouchOnAnyButton(x: Float, y: Float): Boolean {
        for (view in buttonViews) {
            if (x >= view.left.toFloat() && x <= view.right.toFloat() &&
                y >= view.top.toFloat() && y <= view.bottom.toFloat()) {
                return true
            }
        }
        if (schlossButton != null) {
            val v = schlossButton!!
            if (x >= v.left.toFloat() && x <= v.right.toFloat() &&
                y >= v.top.toFloat() && y <= v.bottom.toFloat()) return true
        }
        if (gearButton != null && gearButton!!.visibility == View.VISIBLE) {
            val v = gearButton!!
            if (x >= v.left.toFloat() && x <= v.right.toFloat() &&
                y >= v.top.toFloat() && y <= v.bottom.toFloat()) return true
        }
        return false
    }

    private fun ensureSystemButtonsOnTop() {
        schlossButton?.let { overlayContainer?.bringChildToFront(it) }
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
        if (controllerEnabled) {
            Log.i(TAG, "Controller mode active, skipping gameplay touch buttons")
            return
        }
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
            val (leftPx, topPx) = positionFor(btnConfig, bw, bh, minDim)
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
            val (leftPx, topPx) = positionFor(btnConfig, bw, bh, minDim)
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
        val (leftPx, topPx) = positionFor(updatedConfig, bw, bh, minDim)
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
            if (view != null) {
                val (nx, ny) = view.getNormalizedPosition(containerWidth, containerHeight)
                btnConfig.copy(x = nx, y = ny, anchorX = null, anchorY = null, offsetX = null, offsetY = null)
            }
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

    // ---- Context-sensitive visibility & icon switching ----

    fun startContextSync() {
        if (contextSyncRunning) return
        contextSyncRunning = true
        lastSyncContext = -1
        lastSyncGunDrawn = false
        lastSyncSwordDrawn = false
        scheduleContextSync()
    }

    fun stopContextSync() {
        contextSyncRunning = false
        contextSyncRunnable?.let { root.removeCallbacks(it) }
        contextSyncRunnable = null
    }

    private fun scheduleContextSync() {
        if (!contextSyncRunning) return
        contextSyncRunnable = Runnable {
            syncContextSensitiveState()
            if (contextSyncRunning) root.postDelayed(contextSyncRunnable, CONTEXT_SYNC_INTERVAL_MS)
        }
        root.postDelayed(contextSyncRunnable, CONTEXT_SYNC_INTERVAL_MS)
    }

    private fun syncContextSensitiveState() {
        if (controllerEnabled) return
        val context = try {
            BermudaActivity.nativeGetTouchInputContext()
        } catch (e: UnsatisfiedLinkError) { 0 }

        val controlState = try {
            BermudaActivity.nativeGetControlState()
        } catch (e: UnsatisfiedLinkError) { 0 }
        val gunDrawn = (controlState and 1) != 0     // CONTROL_STATE_GUN_DRAWN
        val swordDrawn = (controlState and 2) != 0    // CONTROL_STATE_SWORD_DRAWN

        if (context == lastSyncContext && gunDrawn == lastSyncGunDrawn && swordDrawn == lastSyncSwordDrawn) return
        lastSyncContext = context
        lastSyncGunDrawn = gunDrawn
        lastSyncSwordDrawn = swordDrawn

        val armed = gunDrawn || swordDrawn

        for (view in buttonViews) {
            val btnId = view.config.id
            val visibility = visibilityForContext(btnId, context)
            view.visibility = if (visibility) View.VISIBLE else View.GONE

            // Icon switching for armed state
            if (btnId == "btn_run" && armed) {
                val newIcon = if (gunDrawn) "fire" else if (swordDrawn) "sword" else null
                if (newIcon != null) view.updateConfig(view.config.copy(icon = newIcon))
                else view.updateConfig(view.config.copy(icon = "run"))
            } else if (btnId == "btn_run" && !armed) {
                view.updateConfig(view.config.copy(icon = "run"))
            }

            if (btnId == "btn_jump" && gunDrawn) {
                view.updateConfig(view.config.copy(icon = "reload"))
            } else if (btnId == "btn_jump" && !gunDrawn) {
                view.updateConfig(view.config.copy(icon = "jump"))
            }
        }
    }

    private fun visibilityForContext(buttonId: String, context: Int): Boolean {
        // context values: 0=GAMEPLAY, 1=VIDEO, 2=BITMAP_CONFIRM, 3=MENU
        return when (context) {
            1 -> { // VIDEO: only dpad (for skip via directionals) and cancel-type buttons
                buttonId == "btn_jump" // acts as skip in video
            }
            3, 2 -> { // MENU / BITMAP_CONFIRM: dpad, OK/Cancel
                buttonId == "dpad" || buttonId == "btn_use" || buttonId == "btn_menu" || buttonId == "btn_jump"
            }
            else -> { // GAMEPLAY: all buttons visible
                true
            }
        }
    }

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

    private fun positionFor(btnConfig: TouchButtonConfig, bw: Int, bh: Int, minDim: Int): Pair<Int, Int> {
        val offsetXPx = ((btnConfig.offsetX ?: 0f) * minDim).toInt()
        val offsetYPx = ((btnConfig.offsetY ?: 0f) * minDim).toInt()
        val left = when (btnConfig.anchorX) {
            BUTTON_ANCHOR_START -> offsetXPx
            BUTTON_ANCHOR_END -> containerWidth - bw - offsetXPx
            else -> (btnConfig.x * containerWidth).toInt()
        }.coerceIn(0, (containerWidth - bw).coerceAtLeast(0))
        val top = when (btnConfig.anchorY) {
            BUTTON_ANCHOR_TOP -> offsetYPx
            BUTTON_ANCHOR_BOTTOM -> containerHeight - bh - offsetYPx
            else -> (btnConfig.y * containerHeight).toInt()
        }.coerceIn(0, (containerHeight - bh).coerceAtLeast(0))
        return Pair(left, top)
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
        private const val CONTEXT_SYNC_INTERVAL_MS = 250L
    }
}
