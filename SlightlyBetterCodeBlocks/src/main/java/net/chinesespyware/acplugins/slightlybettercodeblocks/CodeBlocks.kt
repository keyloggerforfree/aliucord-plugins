/*
 * Copyright (c) 2021 Juby210
 * Licensed under the Open Software License version 3.0
 */

package net.chinesespyware.acplugins.slightlybettercodeblocks

import android.content.Context
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import com.aliucord.Utils
import com.aliucord.api.SettingsAPI
import com.discord.utilities.color.ColorCompat
import com.discord.utilities.spans.ClickableSpan
import com.discord.utilities.textprocessing.node.Spoilerable
import com.lytefast.flexinput.R

/** Line numbers, long-press copy and tap-to-open-viewer for rendered codeblocks. */
object CodeBlocks {
    const val KEY_LINE_NUMBERS = "lineNumbers"
    const val KEY_VIEWER_WRAP = "viewerWrap"

    // Discord's ClickableSpan always calls setUnderlineText(false). A high priority makes it come first in
    // getSpans(), so it's drawn first (ANSI underline still wins) and LinkifiedTextView picks it as the touched span.
    private const val CLICK_FLAGS = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE or (200 shl Spanned.SPAN_PRIORITY_SHIFT)

    private lateinit var settings: SettingsAPI

    @JvmStatic
    fun init(settings: SettingsAPI) {
        this.settings = settings
    }

    /**
     * Call after a codeblock was appended to [builder].
     * @param blockStart start of the block including the language label
     * @param contentStart start of the code itself; the code runs until the end of [builder]
     * @param raw source text of the block, copied on long-press. null disables copy and the viewer.
     * @param parent the BlockBackgroundNode around the block, used to skip unrevealed spoilers
     */
    @JvmStatic
    fun decorate(
        builder: SpannableStringBuilder,
        blockStart: Int,
        contentStart: Int,
        ctx: Context,
        lang: String?,
        raw: String?,
        parent: Spoilerable?,
    ) {
        if (contentStart > builder.length) return
        // Snapshot before line numbers are inserted, the viewer draws its own
        val rendered = builder.subSequence(contentStart, builder.length)

        if (settings.getBool(KEY_LINE_NUMBERS, false)) addLineNumbers(builder, contentStart, ctx)

        if (raw != null && (parent == null || parent.isRevealed)) {
            val span = ClickableSpan(
                null,
                false,
                { copy(raw) },
                { view -> openViewer(view.context, lang, raw, rendered) },
            )
            builder.setSpan(span, blockStart, builder.length, CLICK_FLAGS)
        }
    }

    @JvmStatic
    fun copy(raw: String) {
        Utils.setClipboard("Codeblock", raw)
        Utils.showToast("Copied codeblock")
    }

    private fun openViewer(ctx: Context, lang: String?, raw: String, rendered: CharSequence) {
        Utils.openPageWithProxy(ctx, CodeViewer(lang, raw, rendered, settings.getBool(KEY_VIEWER_WRAP, false)))
    }

    /** Line start offsets of [text] from [start], ignoring trailing newlines. */
    @JvmStatic
    fun lineStarts(text: CharSequence, start: Int): IntArray {
        var end = text.length
        while (end > start && text[end - 1] == '\n') end--
        val starts = ArrayList<Int>()
        starts.add(start)
        for (i in start until end) if (text[i] == '\n') starts.add(i + 1)
        return starts.toIntArray()
    }

    /**
     * Inserts "  1  " style prefixes in front of every line from [start] to the end of [builder].
     * Inserted in place, back to front, so existing spans keep their objects and ranges.
     */
    private fun addLineNumbers(builder: SpannableStringBuilder, start: Int, ctx: Context) {
        val starts = lineStarts(builder, start)
        val digits = starts.size.toString().length
        val color = ColorCompat.getThemedColor(ctx, R.b.colorTextMuted)
        for (i in starts.indices.reversed()) {
            val pos = starts[i]
            val prefix = (i + 1).toString().padStart(digits) + "  "
            builder.insert(pos, prefix)
            builder.setSpan(ForegroundColorSpan(color), pos, pos + prefix.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        }
    }
}
