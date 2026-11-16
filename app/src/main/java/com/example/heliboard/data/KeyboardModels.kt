package com.example.heliboard.data

import kotlinx.serialization.Serializable

enum class KeyboardMode {
    ALPHA,
    SYMBOLS,
    MORE_SYMBOLS,
    NUMPAD,
    EMOJI,
    CLIPBOARD,
    DPAD
}

enum class ShiftState {
    OFF,
    SHIFTED,
    CAPS_LOCK
}

enum class ThemeStyle(val id: String, val displayName: String) {
    MATERIAL("Material", "Material"),
    ROUNDED("Rounded", "Rounded"),
    HOLO("Holo", "Holo")
}

enum class ToolbarMode(val id: String, val displayName: String) {
    EXPANDABLE("EXPANDABLE", "Expandable"),
    TOOLBAR_KEYS("TOOLBAR_KEYS", "Always Show Toolbar"),
    SUGGESTION_STRIP("SUGGESTION_STRIP", "Suggestions Only"),
    HIDDEN("HIDDEN", "Hidden")
}

enum class ToolbarKey(val label: String) {
    EMOJI("Emoji"),
    CLIPBOARD("Clipboard"),
    NUMPAD("Numpad"),
    DPAD("D-Pad"),
    UNDO("Undo"),
    REDO("Redo"),
    SELECT_ALL("Select All"),
    COPY("Copy"),
    CUT("Cut"),
    PASTE("Paste"),
    ONE_HANDED("One-Handed"),
    SPLIT("Split"),
    INCOGNITO("Incognito"),
    AUTOCORRECT("Autocorrect"),
    CLEAR_CLIPBOARD("Clear Clipboard"),
    LEFT("Cursor Left"),
    RIGHT("Cursor Right"),
    SETTINGS("Settings")
}

enum class KeyActionType {
    CHARACTER,
    SHIFT,
    DELETE,
    SPACE,
    ENTER,
    SWITCH_SYMBOLS,
    SWITCH_ALPHA,
    SWITCH_MORE_SYMBOLS,
    SWITCH_NUMPAD,
    EMOJI,
    LANGUAGE_SWITCH,
    PLACEHOLDER
}

data class KeySpec(
    val label: String,
    val outputText: String = label,
    val shiftedLabel: String? = null,
    val shiftedOutput: String? = null,
    val hintLabel: String? = null,
    val popups: List<String> = emptyList(),
    val actionType: KeyActionType = KeyActionType.CHARACTER,
    val isFunctional: Boolean = false,
    val weight: Float = 1f
)

data class KeyRow(
    val keys: List<KeySpec>
)

data class KeyboardLayout(
    val name: String,
    val rows: List<KeyRow>
)

data class LayoutInfo(
    val id: String,
    val displayName: String,
    val assetPath: String,
    val isJson: Boolean
)

@Serializable
data class SubtypeConfig(
    val localeTag: String,
    val displayName: String,
    val layoutId: String,
    val isEnabled: Boolean = true
)

data class EmojiCategoryData(
    val id: String,
    val title: String,
    val iconName: String,
    val emojis: List<String>
)

@Serializable
data class ClipboardEntry(
    val id: Long,
    val text: String,
    val timestamp: Long,
    val isPinned: Boolean = false
)

@Serializable
data class PersonalWordEntry(
    val id: Long,
    val word: String,
    val shortcut: String = "",
    val weight: Int = 250,
    val localeTag: String = ""
)

@Serializable
data class CustomThemeSpec(
    val name: String,
    val isDark: Boolean = false,
    val accentColor: Int,
    val backgroundColor: Int,
    val keyColor: Int,
    val functionalKeyColor: Int,
    val spaceBarColor: Int,
    val textColor: Int,
    val hintTextColor: Int
)

@Serializable
data class HeliSettings(
    val themeStyle: String = ThemeStyle.MATERIAL.id,
    val dayNightAuto: Boolean = true,
    val themeName: String = "light",
    val nightThemeName: String = "dark",
    val keyBorders: Boolean = true,
    val keyboardHeightScale: Float = 1.0f,
    val keyGapScale: Float = 1.0f,
    val fontScale: Float = 1.0f,
    val showNumberRow: Boolean = false,
    val showHints: Boolean = true,
    val keyPopupEnabled: Boolean = true,
    val vibrateOnKeypress: Boolean = true,
    val soundOnKeypress: Boolean = false,
    val longPressTimeoutMs: Int = 300,
    val autoCap: Boolean = true,
    val doubleSpacePeriod: Boolean = true,
    val autoCorrection: Boolean = true,
    val showSuggestions: Boolean = true,
    val suggestEmojis: Boolean = true,
    val spacebarSwipeCursor: Boolean = true,
    val oneHandedMode: Boolean = false,
    val oneHandedRight: Boolean = false,
    val splitKeyboard: Boolean = false,
    val incognitoMode: Boolean = false,
    val showLanguageSwitchKey: Boolean = true,
    val showEmojiKey: Boolean = true,
    val toolbarMode: String = ToolbarMode.EXPANDABLE.id,
    val toolbarKeys: List<String> = listOf(
        ToolbarKey.EMOJI.name,
        ToolbarKey.CLIPBOARD.name,
        ToolbarKey.NUMPAD.name,
        ToolbarKey.DPAD.name,
        ToolbarKey.UNDO.name,
        ToolbarKey.REDO.name,
        ToolbarKey.ONE_HANDED.name,
        ToolbarKey.SPLIT.name,
        ToolbarKey.PASTE.name,
        ToolbarKey.INCOGNITO.name,
        ToolbarKey.SETTINGS.name
    ),
    val emojiSkinTone: String = "",
    val recentEmojis: List<String> = listOf("😀", "😂", "❤️", "👍", "🙏", "🔥", "✨", "🎉"),
    val clipboardHistoryEnabled: Boolean = true,
    val clipboardPinnedFirst: Boolean = true,
    val selectedSubtypeLocale: String = "en-US",
    val subtypes: List<SubtypeConfig> = listOf(
        SubtypeConfig("en-US", "English (US)", "qwerty", true),
        SubtypeConfig("en-GB", "English (UK)", "qwerty", true),
        SubtypeConfig("de-DE", "German", "qwertz", true),
        SubtypeConfig("fr-FR", "French", "azerty", true),
        SubtypeConfig("es-ES", "Spanish", "qwerty", false)
    )
)

@Serializable
data class HeliBackupBundle(
    val version: Int = 1,
    val settings: HeliSettings,
    val personalWords: List<PersonalWordEntry> = emptyList(),
    val customThemes: List<CustomThemeSpec> = emptyList(),
    val customLayouts: Map<String, String> = emptyMap(),
    val pinnedClips: List<ClipboardEntry> = emptyList()
)
