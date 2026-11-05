/*
 * Copyright (c) 2026 keyloggerforfree
 * Licensed under the Open Software License version 3.0
 */

package net.chinesespyware.acplugins.slightlybettercodeblocks

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.util.TypedValue
import android.view.View
import android.view.ViewGroup
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.TextView
import com.aliucord.Utils
import com.aliucord.fragments.SettingsPage
import com.aliucord.utils.DimenUtils
import com.discord.utilities.color.ColorCompat
import com.lytefast.flexinput.R
import kotlin.math.ceil

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

        val lineCount = CodeBlocks.lineStarts(rendered, 0).size
        setActionBarTitle(lang ?: "Codeblock")
        setActionBarSubtitle(if (lineCount == 1) "1 line" else "$lineCount lines")

        ctx.getDrawable(R.e.ic_copy_24dp)?.mutate()?.let { icon ->
            Utils.tintToTheme(icon)
            addHeaderButton("Copy", icon) {
                CodeBlocks.copy(raw)
                true
            }
        }

        val padding = DimenUtils.defaultPadding
        val font = MonospaceSpan.typeface(ctx)
        val code = TextView(ctx).apply {
            typeface = font
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
            setTextColor(ColorCompat.getThemedColor(ctx, R.b.colorTextNormal))
            setTextIsSelectable(true)
            setPadding(padding / 4, padding / 2, padding, padding / 2)
        }
        val text = SpannableStringBuilder(rendered)
        // Overrides TypefaceSpan("monospace") from Discord's own code rendering, which custom system fonts replace
        text.setSpan(MonospaceSpan(font), 0, text.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        BlockGlyphSpan.apply(text, 0, text.length)
        code.text = text

        val gutter = LineNumberGutter(ctx, code, lineCount, ColorCompat.getThemedColor(ctx, R.b.colorTextMuted))
        val row = LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(ColorCompat.getThemedColor(ctx, R.b.theme_chat_code))
            addView(gutter, LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT))
            val weighted = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
            if (wrap) addView(code, weighted)
            else addView(HorizontalScrollView(ctx).apply {
                isHorizontalScrollBarEnabled = true
                addView(code, ViewGroup.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT))
            }, weighted)
        }
        addView(row)
    }

    /**
     * Line numbers in their own view, next to the code. Inside the TextView they'd get covered by the selection
     * highlight, which spans the full width for every line between the first and last selected one.
     * Numbers are drawn at the baseline of each line's first row, so they stay aligned when lines wrap,
     * and they stay in place while the code scrolls sideways.
     */
    private class LineNumberGutter(
        ctx: Context,
        private val code: TextView,
        private val lineCount: Int,
        color: Int,
    ) : View(ctx) {
        private val paint = TextPaint(code.paint).apply {
            this.color = color
            textAlign = Paint.Align.RIGHT
        }
        private val gap = paint.measureText(" ")
        private val numbersWidth = paint.measureText("0".repeat(lineCount.toString().length))

        init {
            code.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> invalidate() }
        }

        override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
            val width = ceil(gap * 2 + numbersWidth).toInt()
            setMeasuredDimension(width, getDefaultSize(suggestedMinimumHeight, heightMeasureSpec))
        }

        override fun onDraw(canvas: Canvas) {
            val layout = code.layout ?: return
            val text = code.text
            val x = width - gap
            val top = code.totalPaddingTop.toFloat()
            var number = 1
            for (line in 0 until layout.lineCount) {
                if (number > lineCount) break // trailing empty lines
                val start = layout.getLineStart(line)
                if (line == 0 || text[start - 1] == '\n') {
                    canvas.drawText(number.toString(), x, top + layout.getLineBaseline(line), paint)
                    number++
                }
            }
        }
    }
}
