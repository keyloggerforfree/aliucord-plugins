/*
 * Copyright (c) 2026 keyloggerforfree
 * Licensed under the Open Software License version 3.0
 */

package net.chinesespyware.acplugins.slightlybettercodeblocks

import android.content.Context
import android.graphics.Paint
import android.graphics.Typeface
import android.text.TextPaint
import android.text.style.MetricAffectingSpan
import androidx.core.content.res.ResourcesCompat
import com.aliucord.Constants

/**
 * Sets Discord's bundled code font. TypefaceSpan("monospace") and Typeface.MONOSPACE resolve through the system
 * font config, which custom system fonts (e.g. Samsung's FlipFont "Font style") replace with a proportional font.
 * Fonts loaded from app resources aren't replaced.
 */
class MonospaceSpan(private val typeface: Typeface) : MetricAffectingSpan() {
    override fun updateDrawState(paint: TextPaint) = apply(paint)
    override fun updateMeasureState(paint: TextPaint) = apply(paint)

    private fun apply(paint: Paint) {
        // Keep the style set by earlier spans (e.g. ANSI bold), faking what the font can't do
        val oldStyle = paint.typeface?.style ?: Typeface.NORMAL
        val styled = Typeface.create(typeface, oldStyle)
        val fake = oldStyle and styled.style.inv()
        if (fake and Typeface.BOLD != 0) paint.isFakeBoldText = true
        if (fake and Typeface.ITALIC != 0) paint.textSkewX = -0.25f
        paint.typeface = styled
    }

    companion object {
        private var cached: Typeface? = null

        @JvmStatic
        fun typeface(ctx: Context): Typeface = cached ?: run {
            val tf = try {
                ResourcesCompat.getFont(ctx, Constants.Fonts.sourcecodepro_semibold)
            } catch (e: Throwable) {
                null
            } ?: Typeface.MONOSPACE
            cached = tf
            tf
        }
    }
}
