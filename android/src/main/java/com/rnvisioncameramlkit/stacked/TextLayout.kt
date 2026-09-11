package com.rnvisioncameramlkit.stacked

import com.google.mlkit.vision.text.Text
import com.rnvisioncameramlkit.utils.Logger

enum class TextLayout {
    HORIZONTAL,
    STACKED,
    AUTO;

    fun shouldReadStacked(horizontalPass: Text?): Boolean = when (this) {
        HORIZONTAL -> false
        STACKED -> true
        AUTO -> horizontalPass == null || horizontalPass.textBlocks.none { it.lines.isNotEmpty() }
    }

    companion object {
        fun from(value: String?): TextLayout {
            if (value == null) return HORIZONTAL
            return when (value.lowercase()) {
                "stacked" -> STACKED
                "auto" -> AUTO
                "horizontal" -> HORIZONTAL
                else -> {
                    Logger.warn("Unknown textLayout '$value', defaulting to horizontal")
                    HORIZONTAL
                }
            }
        }
    }
}
