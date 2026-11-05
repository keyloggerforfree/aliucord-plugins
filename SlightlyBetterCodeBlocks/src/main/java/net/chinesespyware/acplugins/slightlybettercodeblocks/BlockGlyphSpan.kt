/*
 * Copyright (c) 2026 keyloggerforfree
 * Licensed under the Open Software License version 3.0
 */

package net.chinesespyware.acplugins.slightlybettercodeblocks

import android.graphics.Canvas
import android.graphics.Paint
import android.text.Spannable
import android.text.Spanned
import android.text.TextPaint
import android.text.style.CharacterStyle
import android.text.style.MetricAffectingSpan
import android.text.style.ReplacementSpan
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Draws block elements (U+2580–259F), sextants (U+1FB00–1FB3B) and the extra eighth blocks (U+1FB70–1FB8B) as
 * rectangles filling exactly one character cell, like terminals do. Fonts often lack these glyphs (they show up as
 * boxes), and fallback fonts draw them at a different width and height, so block art (e.g. fastfetch logos) ends up
 * misaligned with gaps between rows.
 *
 * One span covers a run of consecutive block characters. Android may measure and draw it in pieces (it splits at
 * every metric-affecting span, e.g. ANSI bold changing mid-run), so every method works on the [start, end) it's given.
 */
class BlockGlyphSpan private constructor(
    private val codePoints: IntArray,
    /** Char offset of each cell from the span start */
    private val offsets: IntArray,
) : ReplacementSpan() {
    /** First cell whose characters start at or after [start]. (Plain ints: Kotlin 1.5's IR backend crashes on IntRange here.) */
    private fun firstCell(text: CharSequence?, start: Int): Int {
        val spanStart = spanStartIn(text)
        if (spanStart < 0) return 0
        var i = 0
        while (i < offsets.size && spanStart + offsets[i] < start) i++
        return i
    }

    /** Index after the last cell whose characters start before [end]. */
    private fun endCell(text: CharSequence?, end: Int): Int {
        val spanStart = spanStartIn(text)
        if (spanStart < 0) return codePoints.size
        var i = 0
        while (i < offsets.size && spanStart + offsets[i] < end) i++
        return i
    }

    private fun spanStartIn(text: CharSequence?): Int = if (text is Spanned) text.getSpanStart(this) else -1

    override fun getSize(paint: Paint, text: CharSequence?, start: Int, end: Int, fm: Paint.FontMetricsInt?): Int {
        // Without this a line made only of block characters would get zero height
        if (fm != null) paint.getFontMetricsInt(fm)
        val count = endCell(text, end) - firstCell(text, start)
        return if (count <= 0) 0 else (cellWidth(paint) * count).roundToInt()
    }

    override fun draw(
        canvas: Canvas, text: CharSequence?, start: Int, end: Int,
        x: Float, top: Int, y: Int, bottom: Int, paint: Paint,
    ) {
        val cell = cellWidth(paint)
        // Fill the whole line so rows touch, but not extra padding some lines get (e.g. the codeblock's last line)
        val metrics = paint.fontMetrics
        val y0 = top.toFloat()
        val y1 = min(bottom.toFloat(), y0 + (metrics.descent - metrics.ascent) * 1.35f)

        val spanned = text as? Spanned
        val spanStart = spanStartIn(text)
        val cellPaint = TextPaint()
        var left = x
        val last = endCell(text, end)
        var i = firstCell(text, start)
        while (i < last) {
            // Replacement spans are drawn before character styles are applied, so look up colors per cell
            cellPaint.set(paint)
            cellPaint.bgColor = 0
            if (spanned != null && spanStart >= 0) {
                val charStart = spanStart + offsets[i]
                val charEnd = charStart + Character.charCount(codePoints[i])
                for (style in spanned.getSpans(charStart, charEnd, CharacterStyle::class.java))
                    if (style !is MetricAffectingSpan) style.updateDrawState(cellPaint)
            }
            cellPaint.style = Paint.Style.FILL

            val right = left + cell
            if (cellPaint.bgColor != 0) {
                val fg = cellPaint.color
                cellPaint.color = cellPaint.bgColor
                canvas.drawRect(left.roundToInt().toFloat(), y0, right.roundToInt().toFloat(), y1, cellPaint)
                cellPaint.color = fg
            }
            drawCell(canvas, cellPaint, codePoints[i], left, y0, right, y1)
            left = right
            i++
        }
    }

    private fun drawCell(c: Canvas, p: Paint, cp: Int, l: Float, t: Float, r: Float, b: Float) {
        // Rounded grid lines shared by neighbouring rectangles, so there are no hairline seams
        fun gx(f: Float) = (l + (r - l) * f).roundToInt().toFloat()
        fun gy(f: Float) = (t + (b - t) * f).roundToInt().toFloat()
        fun rect(fl: Float, ft: Float, fr: Float, fb: Float) = c.drawRect(gx(fl), gy(ft), gx(fr), gy(fb), p)
        fun upper(eighths: Int) = rect(0f, 0f, 1f, eighths / 8f)
        fun lower(eighths: Int) = rect(0f, 1f - eighths / 8f, 1f, 1f)
        fun left(eighths: Int) = rect(0f, 0f, eighths / 8f, 1f)
        fun right(eighths: Int) = rect(1f - eighths / 8f, 0f, 1f, 1f)

        when (cp) {
            0x2580 -> upper(4) // ▀
            in 0x2581..0x2588 -> lower(cp - 0x2580) // ▁..█
            in 0x2589..0x258F -> left(0x2590 - cp) // ▉..▏
            0x2590 -> right(4) // ▐
            in 0x2591..0x2593 -> { // ░ ▒ ▓
                val alpha = p.alpha
                p.alpha = alpha * (cp - 0x2590) / 4
                rect(0f, 0f, 1f, 1f)
                p.alpha = alpha
            }
            0x2594 -> upper(1) // ▔
            0x2595 -> right(1) // ▕
            in 0x2596..0x259F -> { // quadrants ▖..▟; bits: 1 top-left, 2 top-right, 4 bottom-left, 8 bottom-right
                val bits = QUADRANTS[cp - 0x2596]
                if (bits and 1 != 0) rect(0f, 0f, .5f, .5f)
                if (bits and 2 != 0) rect(.5f, 0f, 1f, .5f)
                if (bits and 4 != 0) rect(0f, .5f, .5f, 1f)
                if (bits and 8 != 0) rect(.5f, .5f, 1f, 1f)
            }
            in 0x1FB00..0x1FB3B -> { // sextants: 2 columns x 3 rows, bit 0 = top-left, row by row
                var bits = cp - 0x1FB00 + 1
                if (bits >= 21) bits++ // 21 (left column) is ▌
                if (bits >= 42) bits++ // 42 (right column) is ▐
                for (cell in 0 until 6) {
                    if (bits and (1 shl cell) == 0) continue
                    val col = cell % 2
                    val row = cell / 2
                    rect(col / 2f, row / 3f, (col + 1) / 2f, (row + 1) / 3f)
                }
            }
            in 0x1FB70..0x1FB75 -> { val n = cp - 0x1FB70 + 1; rect(n / 8f, 0f, (n + 1) / 8f, 1f) } // vertical eighth 2..7
            in 0x1FB76..0x1FB7B -> { val n = cp - 0x1FB76 + 1; rect(0f, n / 8f, 1f, (n + 1) / 8f) } // horizontal eighth 2..7
            0x1FB7C -> { left(1); lower(1) }
            0x1FB7D -> { left(1); upper(1) }
            0x1FB7E -> { right(1); upper(1) }
            0x1FB7F -> { right(1); lower(1) }
            0x1FB80 -> { upper(1); lower(1) }
            0x1FB81 -> for (n in intArrayOf(0, 2, 4, 7)) rect(0f, n / 8f, 1f, (n + 1) / 8f) // horizontal eighths 1,3,5,8
            in 0x1FB82..0x1FB86 -> upper(UPPER_RIGHT_EIGHTHS[cp - 0x1FB82]) // upper 1/4, 3/8, 5/8, 3/4, 7/8
            in 0x1FB87..0x1FB8B -> right(UPPER_RIGHT_EIGHTHS[cp - 0x1FB87]) // right 1/4, 3/8, 5/8, 3/4, 7/8
        }
    }

    companion object {
        private val QUADRANTS = intArrayOf(4, 8, 1, 13, 9, 7, 11, 2, 6, 14) // ▖ ▗ ▘ ▙ ▚ ▛ ▜ ▝ ▞ ▟
        private val UPPER_RIGHT_EIGHTHS = intArrayOf(2, 3, 5, 6, 7)

        @JvmStatic
        fun isBlock(cp: Int) = cp in 0x2580..0x259F || cp in 0x1FB00..0x1FB3B || cp in 0x1FB70..0x1FB8B

        private fun cellWidth(paint: Paint) = paint.measureText("0")

        /** Covers runs of block characters between [start] and [end] with [BlockGlyphSpan]s. */
        @JvmStatic
        fun apply(text: Spannable, start: Int, end: Int) {
            var i = start
            while (i < end) {
                val cp = Character.codePointAt(text, i)
                if (!isBlock(cp)) {
                    i += Character.charCount(cp)
                    continue
                }
                val runStart = i
                val codePoints = ArrayList<Int>()
                val offsets = ArrayList<Int>()
                while (i < end) {
                    val c = Character.codePointAt(text, i)
                    if (!isBlock(c)) break
                    codePoints.add(c)
                    offsets.add(i - runStart)
                    i += Character.charCount(c)
                }
                text.setSpan(
                    BlockGlyphSpan(codePoints.toIntArray(), offsets.toIntArray()),
                    runStart, i, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        }
    }
}
