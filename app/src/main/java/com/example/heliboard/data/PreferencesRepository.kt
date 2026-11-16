package com.example.heliboard.data

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.core.content.edit
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

class PreferencesRepository(private val context: Context) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<HeliSettings> = _settings.asStateFlow()

    private val _clipboard = MutableStateFlow(loadClipboard())
    val clipboard: StateFlow<List<ClipboardEntry>> = _clipboard.asStateFlow()

    private val _personalWords = MutableStateFlow(loadPersonalWords())
    val personalWords: StateFlow<List<PersonalWordEntry>> = _personalWords.asStateFlow()

    private val _customThemes = MutableStateFlow(loadCustomThemes())
    val customThemes: StateFlow<List<CustomThemeSpec>> = _customThemes.asStateFlow()

    private val _customLayouts = MutableStateFlow(loadCustomLayouts())
    val customLayouts: StateFlow<Map<String, String>> = _customLayouts.asStateFlow()

    private val clipboardManager by lazy {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    private var lastCapturedClip: String? = null

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        captureSystemClipboard()
    }

    init {
        runCatching {
            clipboardManager.addPrimaryClipChangedListener(clipListener)
            captureSystemClipboard()
        }
    }

    fun updateSettings(transform: (HeliSettings) -> HeliSettings) {
        val updated = transform(_settings.value)
        _settings.value = updated
        prefs.edit { putString(KEY_SETTINGS, json.encodeToString(updated)) }
    }

    fun cycleSubtype() {
        val current = _settings.value
        val enabled = current.subtypes.filter { it.isEnabled }
        if (enabled.size <= 1) return
        val idx = enabled.indexOfFirst { it.localeTag == current.selectedSubtypeLocale }
        val next = enabled[(idx + 1) % enabled.size]
        updateSettings { it.copy(selectedSubtypeLocale = next.localeTag) }
    }

    fun activeSubtype(): SubtypeConfig {
        val current = _settings.value
        return current.subtypes.firstOrNull { it.localeTag == current.selectedSubtypeLocale && it.isEnabled }
            ?: current.subtypes.firstOrNull { it.isEnabled }
            ?: SubtypeConfig("en-US", "English (US)", "qwerty", true)
    }

    fun addEmojiToRecents(emoji: String) {
        updateSettings { s ->
            val updated = (listOf(emoji) + s.recentEmojis.filterNot { it == emoji }).take(24)
            s.copy(recentEmojis = updated)
        }
    }

    fun captureSystemClipboard() {
        if (!_settings.value.clipboardHistoryEnabled) return
        val clip = runCatching { clipboardManager.primaryClip }.getOrNull() ?: return
        if (clip.itemCount == 0) return
        val text = clip.getItemAt(0).coerceToText(context)?.toString()?.trim().orEmpty()
        if (text.isEmpty() || text == lastCapturedClip) return
        lastCapturedClip = text
        addClipboardEntry(text)
    }

    fun addClipboardEntry(text: String) {
        val clean = text.trim()
        if (clean.isEmpty()) return
        val existing = _clipboard.value.firstOrNull { it.text == clean }
        val newEntry = if (existing != null) {
            existing.copy(timestamp = System.currentTimeMillis())
        } else {
            ClipboardEntry(
                id = System.currentTimeMillis(),
                text = clean,
                timestamp = System.currentTimeMillis(),
                isPinned = false
            )
        }
        val list = (listOf(newEntry) + _clipboard.value.filterNot { it.text == clean }).take(40)
        saveClipboard(sortClipboard(list))
    }

    fun copyToSystemClipboard(text: String) {
        lastCapturedClip = text
        runCatching {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("HeliBoard", text))
        }
    }

    fun togglePinClipboard(id: Long) {
        val updated = _clipboard.value.map {
            if (it.id == id) it.copy(isPinned = !it.isPinned) else it
        }
        saveClipboard(sortClipboard(updated))
    }

    fun deleteClipboardEntry(id: Long) {
        saveClipboard(_clipboard.value.filterNot { it.id == id })
    }

    fun clearUnpinnedClipboard() {
        saveClipboard(_clipboard.value.filter { it.isPinned })
    }

    private fun sortClipboard(items: List<ClipboardEntry>): List<ClipboardEntry> {
        return if (_settings.value.clipboardPinnedFirst) {
            items.sortedWith(compareByDescending<ClipboardEntry> { it.isPinned }.thenByDescending { it.timestamp })
        } else {
            items.sortedByDescending { it.timestamp }
        }
    }

    fun upsertPersonalWord(entry: PersonalWordEntry) {
        val list = _personalWords.value.toMutableList()
        val existingIdx = list.indexOfFirst { it.id == entry.id }
        if (existingIdx >= 0) {
            list[existingIdx] = entry
        } else {
            list.add(0, entry.copy(id = System.currentTimeMillis()))
        }
        savePersonalWords(list.sortedBy { it.word.lowercase() })
    }

    fun deletePersonalWord(id: Long) {
        savePersonalWords(_personalWords.value.filterNot { it.id == id })
    }

    fun saveCustomTheme(spec: CustomThemeSpec) {
        val list = (_customThemes.value.filterNot { it.name == spec.name } + spec)
            .sortedBy { it.name.lowercase() }
        _customThemes.value = list
        prefs.edit { putString(KEY_CUSTOM_THEMES, json.encodeToString(list)) }
    }

    fun deleteCustomTheme(name: String) {
        val list = _customThemes.value.filterNot { it.name == name }
        _customThemes.value = list
        prefs.edit { putString(KEY_CUSTOM_THEMES, json.encodeToString(list)) }
        updateSettings { s ->
            s.copy(
                themeName = if (s.themeName == name) "light" else s.themeName,
                nightThemeName = if (s.nightThemeName == name) "dark" else s.nightThemeName
            )
        }
    }

    fun saveCustomLayout(layoutId: String, source: String) {
        val map = _customLayouts.value.toMutableMap()
        map[layoutId] = source
        _customLayouts.value = map
        prefs.edit { putString(KEY_CUSTOM_LAYOUTS, json.encodeToString(map)) }
    }

    fun resetCustomLayout(layoutId: String) {
        val map = _customLayouts.value.toMutableMap()
        map.remove(layoutId)
        _customLayouts.value = map
        prefs.edit { putString(KEY_CUSTOM_LAYOUTS, json.encodeToString(map)) }
    }

    fun exportBackupJson(): String {
        val bundle = HeliBackupBundle(
            settings = _settings.value,
            personalWords = _personalWords.value,
            customThemes = _customThemes.value,
            customLayouts = _customLayouts.value,
            pinnedClips = _clipboard.value.filter { it.isPinned }
        )
        return json.encodeToString(bundle)
    }

    fun importBackupJson(raw: String): Result<String> = runCatching {
        val bundle = json.decodeFromString<HeliBackupBundle>(raw)
        _settings.value = bundle.settings
        _personalWords.value = bundle.personalWords
        _customThemes.value = bundle.customThemes
        _customLayouts.value = bundle.customLayouts
        val mergedClips = (bundle.pinnedClips + _clipboard.value).distinctBy { it.text }
        _clipboard.value = sortClipboard(mergedClips)

        prefs.edit {
            putString(KEY_SETTINGS, json.encodeToString(bundle.settings))
            putString(KEY_PERSONAL_WORDS, json.encodeToString(bundle.personalWords))
            putString(KEY_CUSTOM_THEMES, json.encodeToString(bundle.customThemes))
            putString(KEY_CUSTOM_LAYOUTS, json.encodeToString(bundle.customLayouts))
            putString(KEY_CLIPBOARD, json.encodeToString(_clipboard.value))
        }
        "Restored ${bundle.personalWords.size} words, ${bundle.customThemes.size} custom themes, and all preferences."
    }

    private fun loadSettings(): HeliSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return HeliSettings()
        return runCatching { json.decodeFromString<HeliSettings>(raw) }.getOrDefault(HeliSettings())
    }

    private fun loadClipboard(): List<ClipboardEntry> {
        val raw = prefs.getString(KEY_CLIPBOARD, null) ?: return listOf(
            ClipboardEntry(1L, "HeliBoard — 100% offline privacy keyboard", System.currentTimeMillis(), isPinned = true)
        )
        return runCatching { json.decodeFromString<List<ClipboardEntry>>(raw) }.getOrDefault(emptyList())
    }

    private fun saveClipboard(items: List<ClipboardEntry>) {
        _clipboard.value = items
        prefs.edit { putString(KEY_CLIPBOARD, json.encodeToString(items)) }
    }

    private fun loadPersonalWords(): List<PersonalWordEntry> {
        val raw = prefs.getString(KEY_PERSONAL_WORDS, null) ?: return listOf(
            PersonalWordEntry(1L, "HeliBoard", "hb", 250, ""),
            PersonalWordEntry(2L, "On my way!", "omw", 240, "en-US")
        )
        return runCatching { json.decodeFromString<List<PersonalWordEntry>>(raw) }.getOrDefault(emptyList())
    }

    private fun savePersonalWords(items: List<PersonalWordEntry>) {
        _personalWords.value = items
        prefs.edit { putString(KEY_PERSONAL_WORDS, json.encodeToString(items)) }
    }

    private fun loadCustomThemes(): List<CustomThemeSpec> {
        val raw = prefs.getString(KEY_CUSTOM_THEMES, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<CustomThemeSpec>>(raw) }.getOrDefault(emptyList())
    }

    private fun loadCustomLayouts(): Map<String, String> {
        val raw = prefs.getString(KEY_CUSTOM_LAYOUTS, null) ?: return emptyMap()
        return runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())
    }

    companion object {
        private const val PREFS_NAME = "heliboard_prefs"
        private const val KEY_SETTINGS = "settings_json"
        private const val KEY_CLIPBOARD = "clipboard_json"
        private const val KEY_PERSONAL_WORDS = "personal_words_json"
        private const val KEY_CUSTOM_THEMES = "custom_themes_json"
        private const val KEY_CUSTOM_LAYOUTS = "custom_layouts_json"
    }
}
