package com.dahl404.penny937.data

import android.content.Context
import android.content.SharedPreferences
import com.dahl404.penny937.model.GridStyle
import com.dahl404.penny937.model.ShapeKind
import com.dahl404.penny937.model.Tool

class Prefs(context: Context) {
    private val sp: SharedPreferences? = runCatching {
        context.getSharedPreferences("penny-937", Context.MODE_PRIVATE)
    }.getOrNull()

    private fun write(block: SharedPreferences.Editor.() -> Unit) {
        val editor = sp?.edit() ?: return
        runCatching {
            editor.block()
            editor.apply()
        }
    }

    var lastProjectId: Long?
        get() = sp?.getLong("lastProject", -1L)?.takeIf { it > 0 }
        set(value) = write { putLong("lastProject", value ?: -1L) }

    fun viewportFor(projectId: Long): String? =
        sp?.getString("viewport_$projectId", null)

    fun setViewportFor(projectId: Long, encoded: String?) = write {
        if (encoded == null) remove("viewport_$projectId")
        else putString("viewport_$projectId", encoded)
    }

    var stylusOnly: Boolean
        get() = sp?.getBoolean("stylusOnly", true) ?: true
        set(value) = write { putBoolean("stylusOnly", value) }

    var onionStrength: Float
        get() = sp?.getFloat("onionStrength", -0.45f) ?: -0.45f
        set(value) = write { putFloat("onionStrength", value) }

    var hintSeen: Boolean
        get() = sp?.getBoolean("hintSeen", false) ?: false
        set(value) = write { putBoolean("hintSeen", value) }

    var pressureEnabled: Boolean
        get() = sp?.getBoolean("pressure", true) ?: true
        set(value) = write { putBoolean("pressure", value) }

    var gridEnabled: Boolean
        get() = sp?.getBoolean("grid", false) ?: false
        set(value) = write { putBoolean("grid", value) }

    var gridStyle: GridStyle
        get() = runCatching {
            GridStyle.valueOf(sp?.getString("gridStyle", GridStyle.GRID.name) ?: GridStyle.GRID.name)
        }.getOrDefault(GridStyle.GRID)
        set(value) = write { putString("gridStyle", value.name) }

    var shapeKind: ShapeKind
        get() = runCatching {
            ShapeKind.valueOf(sp?.getString("shapeKind", ShapeKind.LINE.name) ?: ShapeKind.LINE.name)
        }.getOrDefault(ShapeKind.LINE)
        set(value) = write { putString("shapeKind", value.name) }

    var lastTool: Tool
        get() = runCatching {
            Tool.valueOf(sp?.getString("tool", Tool.PEN.name) ?: Tool.PEN.name)
        }.getOrDefault(Tool.PEN)
        set(value) = write { putString("tool", value.name) }

    var lastColor: Int
        get() = sp?.getInt("color", 0xFF1D1D1F.toInt()) ?: 0xFF1D1D1F.toInt()
        set(value) = write { putInt("color", value) }

    fun sizeFor(tool: Tool): Float =
        sp?.getFloat("size_${tool.name}", tool.defaultWidth()) ?: tool.defaultWidth()

    fun setSizeFor(tool: Tool, size: Float) = write { putFloat("size_${tool.name}", size) }

    fun opacityFor(tool: Tool): Float =
        sp?.getFloat("opacity_${tool.name}", tool.defaultOpacity()) ?: tool.defaultOpacity()

    fun setOpacityFor(tool: Tool, opacity: Float) =
        write { putFloat("opacity_${tool.name}", opacity) }

    fun customSwatch(index: Int): Int? {
        val preferences = sp ?: return null
        val key = "swatch_$index"
        return if (preferences.contains(key)) preferences.getInt(key, 0) else null
    }

    fun setCustomSwatch(index: Int, color: Int?) = write {
        val key = "swatch_$index"
        if (color == null) remove(key) else putInt(key, color)
    }

    fun customSwatches(): List<Int?> = List(CUSTOM_SWATCH_COUNT) { customSwatch(it) }

    companion object {
        const val CUSTOM_SWATCH_COUNT = 6
    }
}
