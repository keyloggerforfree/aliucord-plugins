# aliucord-plugins

Plugins for [Aliucord](https://github.com/Aliucord/Aliucord).

## SlightlyBetterCodeBlocks

A fork of [BetterCodeBlocks](https://github.com/Juby210/Aliucord-plugins/tree/main/BetterCodeBlocks) by [Juby210](https://github.com/Juby210). All the syntax highlighting comes from the original plugin. This fork adds:

- **ANSI colors:** ` ```ansi ` blocks render with colors, bold and underline. By default it uses a standard terminal palette and supports bright, 256 and true colors. Turn off "Terminal colors" to get Discord's own palette, the same as on desktop.
- **Block art:** block characters (`▀ ▄ █ ▟`, sextants like `🬋`) are drawn as solid cells, so ANSI art and fastfetch logos line up even when the font lacks them.
- **Long-press to copy:** long-press anywhere in a block, including empty space, to copy its source without the language label. ANSI escape codes are kept, so a copied colored block can be sent again as is.
- **Code viewer:** tap a code block to open it full screen, with line numbers, text you can select, a copy button and sideways scrolling for long lines.
- **Line numbers in chat:** optional, off by default.

All of these can be turned on or off in the plugin settings. You don't need [CopyCodeBlocks](https://github.com/RazerTexz/my-plugins/tree/main/CopyCodeBlocks) alongside this plugin.

If you have the original BetterCodeBlocks installed, uninstall it first. Both plugins hook the same code block rendering.

### Install

Aliucord's built-in installer only works for GitHub links posted in the Aliucord server, so install it by hand:

1. Download [SlightlyBetterCodeBlocks.zip](https://git.chinesespyware.net/keyloggerforfree/aliucord-plugins/raw/branch/builds/SlightlyBetterCodeBlocks.zip).
2. Move it to `Aliucord/plugins/` in your phone's internal storage (`/storage/emulated/0/Aliucord/plugins/`). Don't unzip it.
3. Restart Aliucord.

After that, the plugin updates itself through Aliucord's updater.

## Building

You need JDK 17 (Gradle 7.6 doesn't run on newer versions) and an Android SDK with the `android-34` platform. Put the SDK path in `local.properties`:

```properties
sdk.dir=/path/to/Android/Sdk
```

Then:

```sh
./gradlew :SlightlyBetterCodeBlocks:make           # -> SlightlyBetterCodeBlocks/build/SlightlyBetterCodeBlocks.zip
./gradlew :SlightlyBetterCodeBlocks:deployWithAdb  # installs it on a device connected over adb
```

### Publishing

The Aliucord updater reads `updater.json` and the plugin zips from the `builds` branch. To build everything and push it there:

```sh
./gradlew publishBuilds              # or double-click it under "aliucord" in Android Studio's Gradle panel
./gradlew publishBuilds -PdryRun     # build and show what would be published, don't push
./publish.sh                         # same thing without going through Gradle
```

It refuses to publish uncommitted changes (`-PallowDirty` or `--allow-dirty` overrides this). It keeps files that are already on the `builds` branch and doesn't touch your checkout. Bump a plugin's `version` before publishing, otherwise the updater won't offer the new build. Pushing from Android Studio needs your SSH key loaded in ssh-agent, because it can't ask for a passphrase.

## License

[Open Software License 3.0](LICENSE), the same license as the original. Source files keep Juby210's copyright headers.

SlightlyBetterCodeBlocks includes copies of [Prism4j](https://github.com/noties/Prism4j) and part of [Markwon](https://github.com/noties/Markwon) by Dimitry Ivanov (Apache 2.0), carried over from BetterCodeBlocks.
