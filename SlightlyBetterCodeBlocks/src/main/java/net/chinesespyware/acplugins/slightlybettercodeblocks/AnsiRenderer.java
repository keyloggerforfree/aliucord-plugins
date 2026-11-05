/*
 * Copyright (c) 2026 keyloggerforfree
 * Licensed under the Open Software License version 3.0
 */

package net.chinesespyware.acplugins.slightlybettercodeblocks;

import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.BackgroundColorSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.style.UnderlineSpan;

/**
 * Renders ```ansi code blocks.
 *
 * Discord mode (default) matches Discord desktop/web: 0 reset, 1 bold, 4 underline, 30-37 foreground and
 * 40-47 background with Discord's palette. Terminal mode uses a standard terminal palette instead and adds
 * bright colors (90-97, 100-107), 256 colors (38;5;n) and true color (38;2;r;g;b).
 * In both modes 22/24/39/49 clear their attribute. Every other escape sequence is stripped.
 */
public final class AnsiRenderer {
    private static final char ESC = '\u001b';

    // Discord's palette is based on Solarized. Its backgrounds come from Solarized's "bright" slots,
    // which Solarized uses for grey tones, so they look washed out compared to a terminal.
    private static final int[] DISCORD_FOREGROUND = {
        0xFF4F545C, // 30 gray
        0xFFDC322F, // 31 red
        0xFF859900, // 32 green
        0xFFB58900, // 33 yellow
        0xFF268BD2, // 34 blue
        0xFFD33682, // 35 pink
        0xFF2AA198, // 36 cyan
        0xFFFFFFFF, // 37 white
    };
    private static final int[] DISCORD_BACKGROUND = {
        0xFF002B36, // 40 firefly dark blue
        0xFFCB4B16, // 41 orange
        0xFF586E75, // 42 marble blue
        0xFF657B83, // 43 greyish turquoise
        0xFF839496, // 44 gray
        0xFF6C71C4, // 45 indigo
        0xFF93A1A1, // 46 light gray
        0xFFFDF6E3, // 47 cream white
    };

    // Standard 16 terminal colors (VS Code's dark terminal palette): 0-7 normal, 8-15 bright
    private static final int[] TERMINAL = {
        0xFF000000, 0xFFCD3131, 0xFF0DBC79, 0xFFE5E510, 0xFF2472C8, 0xFFBC3FBC, 0xFF11A8CD, 0xFFE5E5E5,
        0xFF666666, 0xFFF14C4C, 0xFF23D18B, 0xFFF5F543, 0xFF3B8EEA, 0xFFD670D6, 0xFF29B8DB, 0xFFFFFFFF,
    };

    /** No color set. 0 is fully transparent, which no palette color is (unlike -1, which is white). */
    private static final int NONE = 0;
    private static final int INVALID = -1;

    public static boolean isAnsi(String lang) {
        return "ansi".equalsIgnoreCase(lang);
    }

    public static CharSequence render(String content, boolean terminalColors) {
        var out = new SpannableStringBuilder();
        if (content == null) return out;

        var state = new State(terminalColors);
        int segmentStart = 0;
        int n = content.length();
        int i = 0;
        while (i < n) {
            char c = content.charAt(i);
            if (c != ESC) {
                out.append(c);
                i++;
                continue;
            }

            // Lone ESC or non-CSI escape: drop the ESC itself
            if (i + 1 >= n || content.charAt(i + 1) != '[') {
                i++;
                continue;
            }

            // CSI: ESC [ <params 0x30-0x3F>* <intermediates 0x20-0x2F>* <final 0x40-0x7E>
            int j = i + 2;
            while (j < n && content.charAt(j) >= 0x30 && content.charAt(j) <= 0x3F) j++;
            int paramsEnd = j;
            while (j < n && content.charAt(j) >= 0x20 && content.charAt(j) <= 0x2F) j++;
            if (j >= n || content.charAt(j) < 0x40 || content.charAt(j) > 0x7E) {
                // Unterminated sequence: drop the ESC, keep the rest as text
                i++;
                continue;
            }

            if (content.charAt(j) == 'm' && paramsEnd == j) {
                state.applySpans(out, segmentStart, out.length());
                segmentStart = out.length();
                state.applySgr(content.substring(i + 2, paramsEnd));
            }
            i = j + 1;
        }
        state.applySpans(out, segmentStart, out.length());
        return out;
    }

    /** xterm 256 color palette: 0-15 base colors, 16-231 6x6x6 cube, 232-255 grays. */
    private static int xterm256(int n) {
        if (n < 16) return TERMINAL[n];
        if (n < 232) {
            n -= 16;
            return rgb(cubeLevel(n / 36), cubeLevel(n / 6 % 6), cubeLevel(n % 6));
        }
        int gray = 8 + (n - 232) * 10;
        return rgb(gray, gray, gray);
    }

    private static int cubeLevel(int i) {
        return i == 0 ? 0 : 55 + i * 40;
    }

    private static int rgb(int r, int g, int b) {
        return 0xFF000000 | (r & 0xFF) << 16 | (g & 0xFF) << 8 | (b & 0xFF);
    }

    private static final class State {
        final boolean terminal;
        int fg = NONE;
        int bg = NONE;
        boolean bold;
        boolean underline;

        State(boolean terminal) {
            this.terminal = terminal;
        }

        void reset() {
            fg = NONE;
            bg = NONE;
            bold = false;
            underline = false;
        }

        void applySgr(String params) {
            if (params.isEmpty()) {
                reset();
                return;
            }
            var codes = params.split(";", -1);
            for (int k = 0; k < codes.length; k++) {
                int code = parse(codes[k]);
                if (code == 0) reset();
                else if (code == 1) bold = true;
                else if (code == 4) underline = true;
                else if (code == 22) bold = false;
                else if (code == 24) underline = false;
                else if (code >= 30 && code <= 37) fg = terminal ? TERMINAL[code - 30] : DISCORD_FOREGROUND[code - 30];
                else if (code == 39) fg = NONE;
                else if (code >= 40 && code <= 47) bg = terminal ? TERMINAL[code - 40] : DISCORD_BACKGROUND[code - 40];
                else if (code == 49) bg = NONE;
                else if (terminal && code >= 90 && code <= 97) fg = TERMINAL[8 + code - 90];
                else if (terminal && code >= 100 && code <= 107) bg = TERMINAL[8 + code - 100];
                else if ((code == 38 || code == 48) && k + 1 < codes.length) {
                    // 38;5;n / 38;2;r;g;b. Always consume the arguments so they aren't misread as separate codes,
                    // but only use the color in terminal mode (Discord doesn't support them).
                    int mode = parse(codes[k + 1]);
                    int color = NONE;
                    if (mode == 5 && k + 2 < codes.length) {
                        int index = parse(codes[k + 2]);
                        if (index >= 0 && index <= 255) color = xterm256(index);
                        k += 2;
                    } else if (mode == 2 && k + 4 < codes.length) {
                        int r = parse(codes[k + 2]), g = parse(codes[k + 3]), b = parse(codes[k + 4]);
                        if (r >= 0 && g >= 0 && b >= 0) color = rgb(r, g, b);
                        k += 4;
                    } else {
                        k = codes.length; // malformed, ignore the rest
                    }
                    if (terminal && color != NONE) {
                        if (code == 38) fg = color;
                        else bg = color;
                    }
                }
            }
        }

        // Split at newlines so no style crosses a line start; line numbers are inserted there and would inherit it
        void applySpans(SpannableStringBuilder out, int start, int end) {
            int lineStart = start;
            for (int i = start; i < end; i++) {
                if (out.charAt(i) == '\n') {
                    applyLine(out, lineStart, i);
                    lineStart = i + 1;
                }
            }
            applyLine(out, lineStart, end);
        }

        private void applyLine(SpannableStringBuilder out, int start, int end) {
            if (start >= end) return;
            int flags = Spanned.SPAN_EXCLUSIVE_EXCLUSIVE;
            if (fg != NONE) out.setSpan(new ForegroundColorSpan(fg), start, end, flags);
            if (bg != NONE) out.setSpan(new BackgroundColorSpan(bg), start, end, flags);
            if (bold) out.setSpan(new StyleSpan(Typeface.BOLD), start, end, flags);
            if (underline) out.setSpan(new UnderlineSpan(), start, end, flags);
        }

        private static int parse(String s) {
            if (s.isEmpty()) return 0;
            try {
                return Integer.parseInt(s);
            } catch (NumberFormatException e) {
                return INVALID;
            }
        }
    }
}
