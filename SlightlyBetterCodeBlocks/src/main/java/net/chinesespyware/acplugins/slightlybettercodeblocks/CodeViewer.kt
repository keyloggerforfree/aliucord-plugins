/*
 * Copyright (c) 2026 keyloggerforfree
 * Licensed under the Open Software License version 3.0
 */

package net.chinesespyware.acplugins.slightlybettercodeblocks

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.os.Build
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.GradientDrawable
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.TextPaint
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import androidx.core.content.res.ResourcesCompat
import com.aliucord.Constants
import com.aliucord.Utils
import com.aliucord.utils.DimenUtils
import com.discord.utilities.color.ColorCompat
import com.lytefast.flexinput.R
import kotlin.math.ceil

/**
 * Floating codeblock viewer: a card sized to the code (up to the screen minus a margin, so there's always room to
 * tap outside and dismiss), with line numbers, selectable text, optional horizontal scrolling and a copy button.
 */
object CodeViewer {
    private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT
    private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT

    /** [ctx] must be a themed (Activity) context, it's used for Discord's colors and fonts. */
    @JvmStatic
    fun show(ctx: Context, lang: String?, raw: String, rendered: CharSequence, wrap: Boolean) {
        // Full screen translucent window holding a centered card. A regular dialog window has a minimum width
        // from the theme, which would stop the card from shrinking to small codeblocks.
        val dialog = Dialog(ctx, android.R.style.Theme_Translucent_NoTitleBar)
        val lineCount = CodeBlocks.lineStarts(rendered, 0).size

        val card = LinearLayout(ctx).apply {
            orientation = LinearLayout.VERTICAL
            isClickable = true // taps on the card must not reach the dismiss area behind it
            background = GradientDrawable().apply {
                setColor(ColorCompat.getThemedColor(ctx, R.b.colorBackgroundSecondary))
                cornerRadius = DimenUtils.defaultCardRadius.toFloat()
            }
            clipToOutline = true
            // Header first: LinearLayout gives the scroll view only the height that's left
            addView(header(ctx, lang, lineCount, raw, dialog::dismiss), LinearLayout.LayoutParams(MATCH, WRAP))
            addView(ScrollView(ctx).apply {
                addView(codeView(ctx, rendered, lineCount, wrap), FrameLayout.LayoutParams(WRAP, WRAP))
            }, LinearLayout.LayoutParams(WRAP, WRAP))
        }

        val root = FrameLayout(ctx).apply {
            setOnClickListener { dialog.dismiss() }
            addView(card, FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER).apply {
                val h = DimenUtils.dpToPx(8)
                val v = DimenUtils.dpToPx(8)
                setMargins(h, v, h, v)
            })
        }

        dialog.setContentView(root)
        dialog.setCanceledOnTouchOutside(true)
        dialog.window?.run {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setLayout(MATCH, MATCH)
            setWindowAnimations(android.R.style.Animation_Dialog)
            // Leave the system bars to Discord: the translucent theme gives this window its own black status bar,
            // which replaced Discord's while the viewer was open (and hid dark icons in light theme).
            // Transparent bars + the system dim (which also dims Discord's bar backgrounds) like a regular dialog.
            addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND or WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
            setDimAmount(0.6f)
            statusBarColor = Color.TRANSPARENT
            navigationBarColor = Color.TRANSPARENT
            ctx.findActivity()?.window?.let { copyBarIconColors(it, this) }
        }
        dialog.show()
    }

    private fun Context.findActivity(): Activity? {
        var c: Context? = this
        while (c is ContextWrapper) {
            if (c is Activity) return c
            c = c.baseContext
        }
        return null
    }

    /** Light or dark status/navigation bar icons, same as the activity. The focused window decides them. */
    @Suppress("DEPRECATION")
    private fun copyBarIconColors(from: Window, to: Window) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val mask = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS or WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS
            val appearance = from.insetsController?.systemBarsAppearance ?: return
            to.insetsController?.setSystemBarsAppearance(appearance and mask, mask)
        } else {
            val mask = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR or View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR
            to.decorView.systemUiVisibility = (to.decorView.systemUiVisibility and mask.inv()) or
                (from.decorView.systemUiVisibility and mask)
        }
    }

    private fun header(ctx: Context, lang: String?, lineCount: Int, raw: String, dismiss: () -> Unit): View {
        val padding = DimenUtils.defaultPadding
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(padding, padding / 2, padding / 4, padding / 2)

            addView(LinearLayout(ctx).apply {
                orientation = LinearLayout.VERTICAL
                addView(TextView(ctx).apply {
                    text = lang ?: "Codeblock"
                    typeface = ResourcesCompat.getFont(ctx, Constants.Fonts.whitney_semibold)
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
                    setTextColor(ColorCompat.getThemedColor(ctx, R.b.colorHeaderPrimary))
                })
                addView(TextView(ctx).apply {
                    text = if (lineCount == 1) "1 line" else "$lineCount lines"
                    setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
                    setTextColor(ColorCompat.getThemedColor(ctx, R.b.colorTextMuted))
                })
            }, LinearLayout.LayoutParams(0, WRAP, 1f).apply { marginEnd = padding })

            addView(iconButton(ctx, R.e.ic_copy_24dp, "Copy") { CodeBlocks.copy(raw) })
            addView(iconButton(ctx, R.e.ic_close_white_24dp, "Close") { dismiss() })
        }
    }

    private fun iconButton(ctx: Context, icon: Int, description: String, onClick: () -> Unit) = ImageView(ctx).apply {
        val size = DimenUtils.dpToPx(40)
        val inset = DimenUtils.dpToPx(8)
        layoutParams = LinearLayout.LayoutParams(size, size)
        setPadding(inset, inset, inset, inset)
        contentDescription = description
        ctx.getDrawable(icon)?.mutate()?.let { setImageDrawable(Utils.tintToTheme(it)) }
        val ripple = TypedValue()
        if (ctx.theme.resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true))
            setBackgroundResource(ripple.resourceId)
        setOnClickListener { onClick() }
    }

    /** Line number gutter + code, with the code scrolling sideways unless [wrap]. */
    private fun codeView(ctx: Context, rendered: CharSequence, lineCount: Int, wrap: Boolean): View {
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
        return LinearLayout(ctx).apply {
            orientation = LinearLayout.HORIZONTAL
            setBackgroundColor(ColorCompat.getThemedColor(ctx, R.b.theme_chat_code))
            addView(gutter, LinearLayout.LayoutParams(WRAP, MATCH))
            // WRAP_CONTENT (not weighted) so the card can shrink to short code; the parent's AT_MOST width
            // still makes wrapped text wrap and the scroll view scroll
            if (wrap) addView(code, LinearLayout.LayoutParams(WRAP, WRAP))
            else addView(HorizontalScrollView(ctx).apply {
                addView(code, FrameLayout.LayoutParams(WRAP, WRAP))
            }, LinearLayout.LayoutParams(WRAP, WRAP))
        }
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
