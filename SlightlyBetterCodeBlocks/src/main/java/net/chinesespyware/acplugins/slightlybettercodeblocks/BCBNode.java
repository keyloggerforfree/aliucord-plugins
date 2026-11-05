/*
 * Copyright (c) 2021 Juby210
 * Licensed under the Open Software License version 3.0
 */

package net.chinesespyware.acplugins.slightlybettercodeblocks;

import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.RelativeSizeSpan;

import com.discord.simpleast.core.node.Node;
import com.discord.utilities.textprocessing.node.BasicRenderContext;
import com.discord.utilities.textprocessing.node.Spoilerable;

public final class BCBNode<RC extends BasicRenderContext> extends Node<RC> {
    private final CharSequence content;
    private final String lang;
    private final String raw;
    private Spoilerable parent;

    public BCBNode(String lang, CharSequence content, String raw) {
        this.lang = lang;
        this.content = content;
        this.raw = raw;
    }

    public void setParent(Spoilerable parent) {
        this.parent = parent;
    }

    @Override
    public final void render(SpannableStringBuilder builder, RC rc) {
        if (builder != null) {
            Utils.ensureEndsWithNewline(builder);
            int a = builder.length();
            if (lang != null) LangNode.renderLang(builder, rc.getContext(), lang, a);

            int contentStart = builder.length();
            builder.append(content);
            CodeBlocks.decorate(builder, a, contentStart, rc.getContext(), lang, raw, parent);

            // The code's font is set by CodeBlocks.decorate (MonospaceSpan)
            builder.setSpan(new RelativeSizeSpan(0.85f), a, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        }
    }
}
