/*
 * Copyright (c) 2021 Juby210
 * Licensed under the Open Software License version 3.0
 */

package net.chinesespyware.acplugins.slightlybettercodeblocks

import android.annotation.SuppressLint
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.LeadingMarginSpan
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.TextView
import com.aliucord.Utils
import com.aliucord.fragments.SettingsPage
import com.aliucord.utils.DimenUtils
import com.discord.utilities.color.ColorCompat
import com.lytefast.flexinput.R

/** Full screen codeblock viewer: optional horizontal scrolling, line numbers and a copy button. */
class CodeViewer(
    private val lang: String?,
    private val raw: String,
    private val rendered: CharSequence,
    private val wrap: Boolean,
) : SettingsPage() {
    @SuppressLint("SetTextI18n")
    override fun onViewBound(view: View) {
        super.onViewBound(view)
        val ctx = view.context

        val lineStarts = CodeBlocks.lineStarts(rendered, 0)
        setActionBarTitle(lang ?: "Codeblock")
        setActionBarSubtitle(if (lineStarts.size == 1) "1 line" else "${lineStarts.size} lines")

        ctx.getDrawable(R.e.ic_copy_24dp)?.mutate()?.let { icon ->
            Utils.tintToTheme(icon)
            addHeaderButton("Copy", icon) {
                CodeBlocks.copy(raw)
                true
            }
        }

        val padding = DimenUtils.defaultPadding
        val code = TextView(ctx).apply {
            typeface = Typeface.MONOSPACE
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(ColorCompat.getThemedColor(ctx, R.b.colorTextNormal))
            setTextIsSelectable(true)
            setPadding(padding / 2, padding / 2, padding, padding / 2)
        }
        code.text = withLineNumbers(rendered, lineStarts, code.paint, ColorCompat.getThemedColor(ctx, R.b.colorTextMuted))

        val content: View = if (wrap) code else HorizontalScrollView(ctx).apply {
            isHorizontalScrollBarEnabled = true
            addView(code, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
        }
        content.setBackgroundColor(ColorCompat.getThemedColor(ctx, R.b.theme_chat_code))
        addView(content)
    }

    private fun withLineNumbers(text: CharSequence, starts: IntArray, paint: Paint, color: Int): CharSequence {
        val out = SpannableStringBuilder(text)
        val digits = starts.size.toString().length
        val gap = paint.measureText("  ")
        val width = (paint.measureText("0".repeat(digits)) + gap).toInt()
        for (i in starts.indices) {
            val start = starts[i]
            // Paragraph range including its trailing newline
            val end = if (i + 1 < starts.size) starts[i + 1] else out.length
            if (start >= end) continue
            out.setSpan(LineNumberSpan((i + 1).toString(), width, gap, color), start, end, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
        return out
    }

    /** Draws the line number in the leading margin, so it's not part of the selectable text. */
    private class LineNumberSpan(
        private val number: String,
        private val width: Int,
        private val gap: Float,
        private val color: Int,
    ) : LeadingMarginSpan {
        override fun getLeadingMargin(first: Boolean) = width

        override fun drawLeadingMargin(
            c: Canvas, p: Paint, x: Int, dir: Int, top: Int, baseline: Int, bottom: Int,
            text: CharSequence, start: Int, end: Int, first: Boolean, layout: Layout?,
        ) {
            if (!first) return
            val oldColor = p.color
            val oldAlign = p.textAlign
            p.color = color
            p.textAlign = Paint.Align.RIGHT
            val right = if (dir > 0) x + width - gap else x - gap
            c.drawText(number, right, baseline.toFloat(), p)
            p.color = oldColor
            p.textAlign = oldAlign
        }
    }
}
