package com.masen.penny937.data

import android.content.Context
import android.content.SharedPreferences
import com.masen.penny937.model.Tool

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

    var stylusOnly: Boolean
        get() = sp?.getBoolean("stylusOnly", true) ?: true
        set(value) = write { putBoolean("stylusOnly", value) }

    var onionEnabled: Boolean
        get() = sp?.getBoolean("onion", true) ?: true
        set(value) = write { putBoolean("onion", value) }

    var hintSeen: Boolean
        get() = sp?.getBoolean("hintSeen", false) ?: false
        set(value) = write { putBoolean("hintSeen", value) }

    var pressureEnabled: Boolean
        get() = sp?.getBoolean("pressure", true) ?: true
        set(value) = write { putBoolean("pressure", value) }

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
}
