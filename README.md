# HeliBoard (Kotlin & Jetpack Compose Edition)

HeliBoard is a 100% offline, privacy-conscious, and highly customizable Android keyboard rewritten cleanly in **Kotlin** and **Jetpack Compose** with Material Design 3.

It declares **no `INTERNET` permission**—all typing, suggestions, clipboard history, layout parsing, themes, and backup/restore run entirely on-device.

## Key Features

- **Real Android IME + Live Interactive Sandbox**: Includes `HeliBoardIMEService` (system Input Method Service) plus an in-app Keyboard Playground so you can test layouts, themes, popups, emoji, clipboard, and D-pad controls immediately.
- **80+ Bundled Keyboard Layouts**: Parses HeliBoard's `.txt` and `.json` layout assets (`QWERTY`, `QWERTZ`, `AZERTY`, `Dvorak`, `Colemak`, `Workman`, `BÉPO`, Arabic, Bengali, Cyrillic, Greek, Hebrew, Hindi, Korean, Tamil, Thai, Urdu, and many more), plus dedicated symbol, number row, and numpad layers.
- **Locale-Aware Long-Press Popups**: Loads diacritic and alternate character popups from `assets/locale_key_texts/` and supports slide-to-select popup characters.
- **10 Emoji Categories & Search**: Browse bundled emoji sets with recents history, skin-tone support, and instant search.
- **Clipboard History**: Syncs with Android `ClipboardManager`, supports pinning items, pasting directly, and clearing unpinned entries.
- **Text Editing & Ergonomic Modes**: D-pad cursor navigation & selection controls, One-Handed mode, Split keyboard mode, Incognito mode, and customizable expandable toolbar.
- **Themes & Geometry Customization**: 16 built-in HeliBoard palettes (including Material You dynamic colors, Light/Dark, Nord, Catppuccin, Dracula, Solarized, AMOLED, Holo), custom theme editor, Material/Rounded/Holo key styles, key borders, height/width/radius/bottom-padding sliders.
- **Offline Suggestions, Personal Dictionary & Backup**: Pure-Kotlin suggestion & auto-correction engine with bigram next-word prediction, learned words, personal dictionary editor, custom layout editor, and full JSON settings backup/restore.

## Building

Built with Kotlin, Jetpack Compose, Material 3, Android Gradle Plugin 9.1.1, and Gradle 9.3.1 targeting Android SDK 36 (`minSdk = 24`).

```bash
gradle :app:assembleDebug
```

## License

HeliBoard is licensed under the GNU General Public License v3.0. See [LICENSE](LICENSE), [LICENSE-Apache-2.0](LICENSE-Apache-2.0), and [LICENSE-CC-BY-SA-4.0](LICENSE-CC-BY-SA-4.0) for details.
