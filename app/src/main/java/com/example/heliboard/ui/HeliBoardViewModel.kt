package com.example.heliboard.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.provider.Settings
import android.view.inputmethod.InputMethodManager
import androidx.compose.ui.text.input.TextFieldValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.heliboard.HeliBoardApp
import com.example.heliboard.data.CustomThemeSpec
import com.example.heliboard.data.HeliSettings
import com.example.heliboard.data.PersonalWordEntry
import com.example.heliboard.data.SubtypeConfig
import com.example.heliboard.data.ToolbarKey
import com.example.heliboard.data.room.ClipboardSnippetEntity
import com.example.heliboard.ime.HeliKeyboardController
import com.example.heliboard.ime.SandboxInputTarget
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ImeStatus(
    val isEnabled: Boolean = false,
    val isSelected: Boolean = false
)

class HeliBoardViewModel(application: Application) : AndroidViewModel(application) {

    private val app = application as HeliBoardApp
    val prefsRepo = app.preferencesRepository
    val assetRepo = app.assetRepository
    val suggestionEngine = app.suggestionEngine

    val settings = prefsRepo.settings
    val clipboard = prefsRepo.clipboard
    val roomClipboardSnippets: StateFlow<List<ClipboardSnippetEntity>> =
        app.clipboardRoomRepository.snippets.stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )
    val personalWords = prefsRepo.personalWords
    val customThemes = prefsRepo.customThemes
    val customLayouts = prefsRepo.customLayouts

    val availableLayouts by lazy { assetRepo.getAvailableMainLayouts() }
    val emojiCategories by lazy { assetRepo.loadEmojiCategories() }

    val sandboxTarget = SandboxInputTarget()
    val sandboxText: StateFlow<TextFieldValue> = sandboxTarget.value

    var onNavigateToSettings: (() -> Unit)? = null
    var onNavigateToAiSettings: (() -> Unit)? = null

    val sandboxController = HeliKeyboardController(
        context = application,
        prefsRepo = prefsRepo,
        assetRepo = assetRepo,
        suggestionEngine = suggestionEngine,
        target = sandboxTarget,
        onOpenSettings = { onNavigateToSettings?.invoke() },
        onOpenAiSettings = { onNavigateToAiSettings?.invoke() }
    )

    val keyboardUiState = sandboxController.uiState

    private val _imeStatus = MutableStateFlow(ImeStatus())
    val imeStatus: StateFlow<ImeStatus> = _imeStatus.asStateFlow()

    private val _learnedCount = MutableStateFlow(suggestionEngine.getLearnedWordsCount())
    val learnedCount: StateFlow<Int> = _learnedCount.asStateFlow()

    private val _customDictCount = MutableStateFlow(suggestionEngine.getCustomDictionaryCount())
    val customDictCount: StateFlow<Int> = _customDictCount.asStateFlow()

    init {
        refreshImeStatus()
        viewModelScope.launch {
            combine(settings, customLayouts, personalWords) { _, _, _ -> Unit }
                .collect {
                    sandboxController.refreshLayoutAndSuggestions()
                    _learnedCount.value = suggestionEngine.getLearnedWordsCount()
                }
        }
    }

    fun refreshImeStatus() {
        val imm = app.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        val pkg = app.packageName
        val enabled = runCatching {
            imm.enabledInputMethodList.any { it.packageName == pkg }
        }.getOrDefault(false)
        val defaultIme = runCatching {
            Settings.Secure.getString(app.contentResolver, Settings.Secure.DEFAULT_INPUT_METHOD)
        }.getOrNull().orEmpty()
        val selected = defaultIme.startsWith("$pkg/")
        _imeStatus.value = ImeStatus(isEnabled = enabled, isSelected = selected)
    }

    fun openSystemImeSettings(context: Context) {
        val intent = Intent(Settings.ACTION_INPUT_METHOD_SETTINGS).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
    }

    fun showInputMethodPicker(context: Context) {
        val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.showInputMethodPicker()
    }

    fun updateSettings(transform: (HeliSettings) -> HeliSettings) {
        prefsRepo.updateSettings(transform)
    }

    fun toggleSubtypeEnabled(localeTag: String) {
        updateSettings { s ->
            val enabledCount = s.subtypes.count { it.isEnabled }
            val updated = s.subtypes.map { sub ->
                if (sub.localeTag == localeTag) {
                    if (sub.isEnabled && enabledCount <= 1) sub
                    else sub.copy(isEnabled = !sub.isEnabled)
                } else sub
            }
            val activeStillEnabled = updated.any { it.localeTag == s.selectedSubtypeLocale && it.isEnabled }
            val nextSelected = if (activeStillEnabled) s.selectedSubtypeLocale
            else updated.firstOrNull { it.isEnabled }?.localeTag ?: s.selectedSubtypeLocale
            s.copy(subtypes = updated, selectedSubtypeLocale = nextSelected)
        }
    }

    fun setSubtypeLayout(localeTag: String, layoutId: String) {
        updateSettings { s ->
            s.copy(subtypes = s.subtypes.map {
                if (it.localeTag == localeTag) it.copy(layoutId = layoutId) else it
            })
        }
    }

    fun selectActiveSubtype(localeTag: String) {
        updateSettings { s -> s.copy(selectedSubtypeLocale = localeTag) }
    }

    fun addOrUpdateSubtype(subtype: SubtypeConfig) {
        updateSettings { s ->
            val exists = s.subtypes.any { it.localeTag == subtype.localeTag }
            val list = if (exists) {
                s.subtypes.map { if (it.localeTag == subtype.localeTag) subtype else it }
            } else {
                s.subtypes + subtype
            }
            s.copy(subtypes = list, selectedSubtypeLocale = subtype.localeTag)
        }
    }

    fun removeSubtype(localeTag: String) {
        updateSettings { s ->
            if (s.subtypes.size <= 1) return@updateSettings s
            val filtered = s.subtypes.filterNot { it.localeTag == localeTag }
            val ensured = if (filtered.none { it.isEnabled }) {
                filtered.mapIndexed { idx, sub -> if (idx == 0) sub.copy(isEnabled = true) else sub }
            } else filtered
            val nextSelected = if (s.selectedSubtypeLocale == localeTag) {
                ensured.first { it.isEnabled }.localeTag
            } else s.selectedSubtypeLocale
            s.copy(subtypes = ensured, selectedSubtypeLocale = nextSelected)
        }
    }

    fun toggleToolbarKey(key: ToolbarKey) {
        prefsRepo.toggleToolbarKey(key)
    }

    fun moveToolbarKey(fromIndex: Int, toIndex: Int) {
        prefsRepo.moveToolbarKey(fromIndex, toIndex)
    }

    fun moveToolbarKey(keyName: String, delta: Int) {
        prefsRepo.moveToolbarKey(keyName, delta)
    }

    fun resetToolbarKeys() {
        prefsRepo.resetToolbarKeys()
    }

    fun saveCustomTheme(spec: CustomThemeSpec) {
        prefsRepo.saveCustomTheme(spec)
    }

    fun deleteCustomTheme(name: String) {
        prefsRepo.deleteCustomTheme(name)
    }

    fun saveCustomLayout(layoutId: String, source: String) {
        prefsRepo.saveCustomLayout(layoutId, source)
    }

    fun resetCustomLayout(layoutId: String) {
        prefsRepo.resetCustomLayout(layoutId)
    }

    fun upsertPersonalWord(entry: PersonalWordEntry) {
        prefsRepo.upsertPersonalWord(entry)
    }

    fun deletePersonalWord(id: Long) {
        prefsRepo.deletePersonalWord(id)
    }

    fun clearLearnedWords() {
        suggestionEngine.clearLearnedWords()
        _learnedCount.value = 0
        sandboxController.refreshLayoutAndSuggestions()
    }

    fun importWordlist(content: String): Int {
        val count = suggestionEngine.importWordlist(content)
        _customDictCount.value = suggestionEngine.getCustomDictionaryCount()
        sandboxController.refreshLayoutAndSuggestions()
        return count
    }
}
