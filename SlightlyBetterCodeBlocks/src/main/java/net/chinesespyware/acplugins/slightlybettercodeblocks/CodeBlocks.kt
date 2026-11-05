/*
 * Copyright (c) 2026 keyloggerforfree
 * Licensed under the Open Software License version 3.0
 */

package net.chinesespyware.acplugins.slightlybettercodeblocks

import android.content.Context
import android.os.SystemClock
import androidx.recyclerview.widget.RecyclerView
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.view.MotionEvent
import android.widget.TextView
import com.aliucord.Utils
import com.aliucord.api.SettingsAPI
import com.discord.utilities.color.ColorCompat
import com.discord.utilities.spans.ClickableSpan
import com.discord.utilities.textprocessing.node.Spoilerable
import com.lytefast.flexinput.R
import java.util.Collections
import java.util.WeakHashMap

/** Line numbers, long-press copy and tap-to-open-viewer for rendered codeblocks. */
object CodeBlocks {
    const val KEY_LINE_NUMBERS = "lineNumbers"
    const val KEY_VIEWER_WRAP = "viewerWrap"
    const val KEY_TERMINAL_COLORS = "ansiTerminalColors"

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
        // Set after Discord's/Prism4j's spans so it wins over any TypefaceSpan("monospace") they add
        builder.setSpan(MonospaceSpan(MonospaceSpan.typeface(ctx)), contentStart, builder.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
        BlockGlyphSpan.apply(builder, contentStart, builder.length)

        if (raw != null && (parent == null || parent.isRevealed)) {
            val span = ClickableSpan(
                null,
                false,
                { copy(raw) },
                { view -> openViewer(view.context, lang, raw, rendered) },
            )
            builder.setSpan(span, blockStart, builder.length, CLICK_FLAGS)
            blockSpans.add(span)
        }
    }

    private val blockSpans: MutableSet<ClickableSpan> = Collections.newSetFromMap(WeakHashMap())

    /**
     * Our codeblock span on the touched line, wherever on the line the touch is. LinkifiedTextView only finds spans
     * under actual characters, so taps on empty space in a block (right of short lines, empty lines) would miss it.
     */
    @JvmStatic
    fun blockSpansAt(textView: TextView, event: MotionEvent): Array<android.text.style.ClickableSpan>? {
        val text = textView.text as? Spanned ?: return null
        val layout = textView.layout ?: return null
        val y = textView.scrollY + event.y.toInt() - textView.totalPaddingTop
        if (y < 0 || y >= layout.height || event.x < 0 || event.x >= textView.width) return null
        val line = layout.getLineForVertical(y)
        val spans = text.getSpans(layout.getLineStart(line), layout.getLineEnd(line), ClickableSpan::class.java)
            .filter { it in blockSpans }
        return if (spans.isEmpty()) null else spans.toTypedArray<android.text.style.ClickableSpan>()
    }

    private val chatAdapters: MutableSet<RecyclerView.Adapter<*>> = Collections.newSetFromMap(WeakHashMap())

    /** Called for every WidgetChatListAdapter created, so settings changes can re-render visible messages. */
    @JvmStatic
    fun trackChatAdapter(adapter: RecyclerView.Adapter<*>) {
        chatAdapters.add(adapter)
    }

    /** Re-binds all chat messages. Discord re-parses a message on every bind, so this applies new settings. */
    @JvmStatic
    fun rerenderChat() {
        Utils.mainThread.post { chatAdapters.toList().forEach { it.notifyDataSetChanged() } }
    }

    @JvmStatic
    fun copy(raw: String) {
        Utils.setClipboard("Codeblock", raw)
        Utils.showToast("Copied codeblock")
    }

    private var lastViewerOpen = 0L

    private fun openViewer(ctx: Context, lang: String?, raw: String, rendered: CharSequence) {
        // A double tap (e.g. out of habit with TapTap) would otherwise open two viewers before the first one shows up
        val now = SystemClock.uptimeMillis()
        if (now - lastViewerOpen < 700) return
        lastViewerOpen = now
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
