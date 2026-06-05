package com.bermuda.reborn.touch

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.util.Log
import com.bermuda.reborn.R
import com.caverock.androidsvg.SVG
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

object SvgIconManager {
    private const val TAG = "SvgIconManager"
    private const val ICON_BITMAP_SIZE = 512
    private const val ICON_PADDING_FRACTION = 0.08f
    private val json = Json { ignoreUnknownKeys = true }
    private var initialized = false
    private var appContext: Context? = null
    private val entries = mutableMapOf<String, IconSetEntry>()
    private val mappings = mutableMapOf<String, String>()
    private val bitmapCache = mutableMapOf<String, Bitmap>()

    fun init(context: Context) {
        if (initialized) return
        initialized = true
        appContext = context.applicationContext

        try {
            val iconsetJson = context.resources.openRawResource(R.raw.iconset)
                .bufferedReader().readText()
            val list: List<IconSetEntry> = json.decodeFromString(iconsetJson)
            for (entry in list) {
                entries[entry.name] = entry
                Log.d(TAG, "Loaded iconset entry: ${entry.name} -> ${entry.svg}")
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load iconset.json", e)
        }

        try {
            val mappingsJson = context.resources.openRawResource(R.raw.iconmappings)
                .bufferedReader().readText()
            val map: Map<String, String> = json.decodeFromString(mappingsJson)
            mappings.putAll(map)
            Log.d(TAG, "Loaded ${mappings.size} icon mappings: $mappings")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load iconmappings.json", e)
        }

        for ((_, svgName) in mappings) {
            val entry = entries[svgName] ?: continue
            val result = loadSvgBitmap(entry.svg)
            Log.d(TAG, "Pre-load SVG ${entry.svg}: ${if (result != null) "OK" else "FAILED"}")
        }
    }

    fun hasIcon(gameIconName: String): Boolean {
        val svgName = mappings[gameIconName] ?: return false
        return entries.containsKey(svgName)
    }

    fun getIconFill(gameIconName: String): Float? {
        val svgName = mappings[gameIconName] ?: return null
        return entries[svgName]?.iconFill
    }

    fun renderIcon(
        canvas: Canvas,
        context: Context,
        gameIconName: String,
        shapeBounds: RectF,
        fillPaint: Paint,
        iconFillOverride: Float = -1f
    ): Boolean {
        val svgName = mappings[gameIconName] ?: return false
        val entry = entries[svgName] ?: return false
        val bitmap = loadSvgBitmap(entry.svg) ?: return false

        val fill = if (iconFillOverride > 0f) iconFillOverride else entry.iconFill

        val sbW = shapeBounds.width()
        val sbH = shapeBounds.height()
        val iconW = sbW * fill
        val iconH = sbH * fill
        val scale = minOf(iconW / bitmap.width, iconH / bitmap.height)
        val destW = bitmap.width * scale * entry.iconScaleX
        val destH = bitmap.height * scale * entry.iconScaleY

        val offsetX = entry.iconOffsetX * sbW
        val offsetY = entry.iconOffsetY * sbH

        val dest = RectF(
            shapeBounds.centerX() + offsetX - destW / 2f,
            shapeBounds.centerY() + offsetY - destH / 2f,
            shapeBounds.centerX() + offsetX + destW / 2f,
            shapeBounds.centerY() + offsetY + destH / 2f
        )
        canvas.drawBitmap(bitmap, null, dest, fillPaint)
        return true
    }

    private fun loadSvgBitmap(name: String): Bitmap? {
        bitmapCache[name]?.let { return it }
        val ctx = appContext ?: return null

        return try {
            val resId = ctx.resources.getIdentifier(
                name.removeSuffix(".svg"),
                "raw",
                ctx.packageName
            )
            if (resId == 0) {
                Log.e(TAG, "SVG resource not found: $name")
                return null
            }

            val svg = ctx.resources.openRawResource(resId).use { input ->
                SVG.getFromInputStream(input)
            } ?: return null

            val iconArea = (ICON_BITMAP_SIZE * (1f - 2f * ICON_PADDING_FRACTION)).toInt().coerceAtLeast(1)
            val offset = ((ICON_BITMAP_SIZE - iconArea) / 2f)

            val iconBitmap = Bitmap.createBitmap(iconArea, iconArea, Bitmap.Config.ARGB_8888)
            val iconCanvas = Canvas(iconBitmap)
            svg.documentWidth = iconArea.toFloat()
            svg.documentHeight = iconArea.toFloat()
            svg.renderToCanvas(iconCanvas, targetRect(svg, iconArea))

            val outBitmap = Bitmap.createBitmap(ICON_BITMAP_SIZE, ICON_BITMAP_SIZE, Bitmap.Config.ARGB_8888)
            val outCanvas = Canvas(outBitmap)
            val whitePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                colorFilter = PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_IN)
            }
            outCanvas.drawBitmap(iconBitmap, offset, offset, whitePaint)
            iconBitmap.recycle()

            bitmapCache[name] = outBitmap
            outBitmap
        } catch (e: Exception) {
            Log.e(TAG, "Failed to load SVG: $name", e)
            null
        }
    }

    private fun targetRect(svg: SVG, iconArea: Int): RectF {
        val viewBox = svg.documentViewBox
        val aspect = if (viewBox != null && viewBox.width() > 0f && viewBox.height() > 0f) {
            viewBox.width() / viewBox.height()
        } else {
            val width = svg.documentWidth
            val height = svg.documentHeight
            if (width > 0f && height > 0f) width / height else 1f
        }

        return if (aspect > 1f) {
            val height = iconArea / aspect
            RectF(0f, (iconArea - height) / 2f, iconArea.toFloat(), (iconArea + height) / 2f)
        } else {
            val width = iconArea * aspect
            RectF((iconArea - width) / 2f, 0f, (iconArea + width) / 2f, iconArea.toFloat())
        }
    }
}

@Serializable
data class IconSetEntry(
    val name: String,
    val svg: String,
    val iconFill: Float,
    val iconOffsetX: Float = 0f,
    val iconOffsetY: Float = 0f,
    val iconScaleX: Float = 1f,
    val iconScaleY: Float = 1f
)
