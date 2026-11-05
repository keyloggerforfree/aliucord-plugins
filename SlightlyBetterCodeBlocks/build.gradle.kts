version = "1.3.0"
description = "BetterCodeBlocks, but slightly better: ANSI colors, long-press copy, a code viewer and line numbers."

// Original author of BetterCodeBlocks, which this is forked from.
// The root build.gradle.kts adds keyloggerforfree to every plugin first.
aliucord.author("Juby210", 324622488644616195L)

aliucord.changelog.set("""
    1.3.0
    ======================

    Added {added}
    ======================

    * terminal colors for ANSI codeblocks (on by default): standard palette, bright, 256 and true colors. Turn off in settings for Discord's own palette
    * block characters (▀▄█▟, sextants) are drawn as solid cells, so ANSI art and fastfetch logos line up

    Fixed {fixed}
    ======================

    * tapping or long-pressing empty space in a codeblock now opens the viewer or copies it too
    * ANSI white (37) was ignored

    1.2.0
    ======================

    Added {added}
    ======================

    * added ANSI codeblock rendering (colors, bold, underline)
    * long-press a codeblock to copy its source
    * tap a codeblock to open it in a viewer (line numbers, sideways scrolling, copy)
    * optional line numbers in chat

    Fixed {fixed}
    ======================

    * codeblocks stay monospaced with custom system fonts (e.g. Samsung font styles)
    * settings apply to messages already on screen, no restart needed

    1.1.2
    ======================

    * updated to **115.4**

    1.1.1
    ======================

    * updated to **112.4**

    1.1.0
    ======================

    Added {added}
    ======================

    * added basic settings
    * added Go SimpleAST highlighting
    * added JSON (json, jsonp) SimpleAST highlighting
""".trimIndent())

tasks.withType<JavaCompile> {
    // src/main/java/b/a/t/a/a.java replaces a class with a broken generic signature in the Discord jar.
    // javac defaults to the newer of the two, which is usually the jar, so force the source file.
    // The sourcepath keeps it visible on incremental builds, where only changed files are passed to javac.
    options.sourcepath = files("src/main/java")
    options.compilerArgs.add("-Xprefer:source")
}
