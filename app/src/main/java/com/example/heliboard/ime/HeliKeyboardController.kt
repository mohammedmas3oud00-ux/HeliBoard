package com.example.heliboard.ime

import android.content.Context
import android.media.AudioManager
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import com.example.heliboard.data.AssetRepository
import com.example.heliboard.data.KeyActionType
import com.example.heliboard.data.KeySpec
import com.example.heliboard.data.KeyboardLayout
import com.example.heliboard.data.KeyboardMode
import com.example.heliboard.data.PreferencesRepository
import com.example.heliboard.data.ShiftState
import com.example.heliboard.data.SuggestionEngine
import com.example.heliboard.data.ToolbarKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

interface TextInputTarget {
    fun getTextBeforeCursor(maxChars: Int): String
    fun getTextAfterCursor(maxChars: Int): String
    fun commitText(text: String)
    fun deleteBeforeCursor(count: Int)
    fun moveCursor(delta: Int)
    fun moveCursorVertical(deltaLines: Int)
    fun moveToLineEdge(end: Boolean)
    fun performEnter()
    fun selectAll()
    fun copySelection(): String?
    fun cutSelection(): String?
    fun undo()
    fun redo()
}

class SandboxInputTarget : TextInputTarget {
    private val _value = MutableStateFlow(TextFieldValue(""))
    val value: StateFlow<TextFieldValue> = _value.asStateFlow()

    private val undoStack = ArrayDeque<TextFieldValue>()
    private val redoStack = ArrayDeque<TextFieldValue>()

    fun setExternalValue(newValue: TextFieldValue) {
        if (newValue.text != _value.value.text) {
            pushUndo(_value.value)
        }
        _value.value = newValue
    }

    private fun pushUndo(snapshot: TextFieldValue) {
        if (undoStack.lastOrNull()?.text == snapshot.text) return
        undoStack.addLast(snapshot)
        if (undoStack.size > 40) undoStack.removeFirst()
        redoStack.clear()
    }

    override fun getTextBeforeCursor(maxChars: Int): String {
        val v = _value.value
        val end = v.selection.start.coerceIn(0, v.text.length)
        val start = (end - maxChars).coerceAtLeast(0)
        return v.text.substring(start, end)
    }

    override fun getTextAfterCursor(maxChars: Int): String {
        val v = _value.value
        val start = v.selection.end.coerceIn(0, v.text.length)
        val end = (start + maxChars).coerceAtMost(v.text.length)
        return v.text.substring(start, end)
    }

    override fun commitText(text: String) {
        val v = _value.value
        pushUndo(v)
        val min = v.selection.min.coerceIn(0, v.text.length)
        val max = v.selection.max.coerceIn(0, v.text.length)
        val nextText = v.text.replaceRange(min, max, text)
        val cursor = min + text.length
        _value.value = TextFieldValue(nextText, TextRange(cursor))
    }

    override fun deleteBeforeCursor(count: Int) {
        val v = _value.value
        if (v.selection.length > 0) {
            pushUndo(v)
            val min = v.selection.min.coerceIn(0, v.text.length)
            val max = v.selection.max.coerceIn(0, v.text.length)
            _value.value = TextFieldValue(v.text.removeRange(min, max), TextRange(min))
            return
        }
        val end = v.selection.start.coerceIn(0, v.text.length)
        if (end == 0 || count <= 0) return
        pushUndo(v)
        val start = (end - count).coerceAtLeast(0)
        _value.value = TextFieldValue(v.text.removeRange(start, end), TextRange(start))
    }

    override fun moveCursor(delta: Int) {
        val v = _value.value
        val next = (v.selection.end + delta).coerceIn(0, v.text.length)
        _value.value = v.copy(selection = TextRange(next))
    }

    override fun moveCursorVertical(deltaLines: Int) {
        val v = _value.value
        val pos = v.selection.end.coerceIn(0, v.text.length)
        val lines = v.text.split("\n")
        var acc = 0
        var currentLine = 0
        var col = 0
        for (i in lines.indices) {
            val lineEnd = acc + lines[i].length
            if (pos <= lineEnd) {
                currentLine = i
                col = pos - acc
                break
            }
            acc = lineEnd + 1
        }
        val targetLine = (currentLine + deltaLines).coerceIn(0, lines.lastIndex)
        var targetPos = 0
        for (i in 0 until targetLine) {
            targetPos += lines[i].length + 1
        }
        targetPos += col.coerceAtMost(lines[targetLine].length)
        _value.value = v.copy(selection = TextRange(targetPos))
    }

    override fun moveToLineEdge(end: Boolean) {
        val v = _value.value
        val pos = v.selection.end.coerceIn(0, v.text.length)
        val target = if (end) {
            val idx = v.text.indexOf('\n', pos)
            if (idx == -1) v.text.length else idx
        } else {
            val idx = v.text.lastIndexOf('\n', (pos - 1).coerceAtLeast(0))
            if (idx == -1 || pos == 0) 0 else idx + 1
        }
        _value.value = v.copy(selection = TextRange(target))
    }

    override fun performEnter() = commitText("\n")

    override fun selectAll() {
        val v = _value.value
        _value.value = v.copy(selection = TextRange(0, v.text.length))
    }

    override fun copySelection(): String? {
        val v = _value.value
        if (v.selection.length <= 0) return null
        return v.text.substring(v.selection.min, v.selection.max)
    }

    override fun cutSelection(): String? {
        val v = _value.value
        if (v.selection.length <= 0) return null
        val selected = v.text.substring(v.selection.min, v.selection.max)
        pushUndo(v)
        _value.value = TextFieldValue(
            v.text.removeRange(v.selection.min, v.selection.max),
            TextRange(v.selection.min)
        )
        return selected
    }

    override fun undo() {
        val prev = undoStack.removeLastOrNull() ?: return
        redoStack.addLast(_value.value)
        _value.value = prev
    }

    override fun redo() {
        val next = redoStack.removeLastOrNull() ?: return
        undoStack.addLast(_value.value)
        _value.value = next
    }
}

class ImeInputTarget(
    private val connectionProvider: () -> InputConnection?,
    private val editorInfoProvider: () -> EditorInfo?
) : TextInputTarget {

    override fun getTextBeforeCursor(maxChars: Int): String =
        connectionProvider()?.getTextBeforeCursor(maxChars, 0)?.toString().orEmpty()

    override fun getTextAfterCursor(maxChars: Int): String =
        connectionProvider()?.getTextAfterCursor(maxChars, 0)?.toString().orEmpty()

    override fun commitText(text: String) {
        connectionProvider()?.commitText(text, 1)
    }

    override fun deleteBeforeCursor(count: Int) {
        val ic = connectionProvider() ?: return
        val selected = ic.getSelectedText(0)
        if (!selected.isNullOrEmpty()) {
            ic.commitText("", 1)
        } else {
            ic.deleteSurroundingText(count, 0)
        }
    }

    override fun moveCursor(delta: Int) {
        val ic = connectionProvider() ?: return
        val code = if (delta < 0) KeyEvent.KEYCODE_DPAD_LEFT else KeyEvent.KEYCODE_DPAD_RIGHT
        repeat(kotlin.math.abs(delta)) {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
    }

    override fun moveCursorVertical(deltaLines: Int) {
        val ic = connectionProvider() ?: return
        val code = if (deltaLines < 0) KeyEvent.KEYCODE_DPAD_UP else KeyEvent.KEYCODE_DPAD_DOWN
        repeat(kotlin.math.abs(deltaLines)) {
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
            ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
        }
    }

    override fun moveToLineEdge(end: Boolean) {
        val ic = connectionProvider() ?: return
        val code = if (end) KeyEvent.KEYCODE_MOVE_END else KeyEvent.KEYCODE_MOVE_HOME
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_DOWN, code))
        ic.sendKeyEvent(KeyEvent(KeyEvent.ACTION_UP, code))
    }

    override fun performEnter() {
        val ic = connectionProvider() ?: return
        val info = editorInfoProvider()
        val actionId = (info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        if (actionId != EditorInfo.IME_ACTION_NONE && actionId != EditorInfo.IME_ACTION_UNSPECIFIED &&
            (info?.imeOptions ?: 0) and EditorInfo.IME_FLAG_NO_ENTER_ACTION == 0
        ) {
            if (ic.performEditorAction(actionId)) return
        }
        ic.commitText("\n", 1)
    }

    override fun selectAll() {
        connectionProvider()?.performContextMenuAction(android.R.id.selectAll)
    }

    override fun copySelection(): String? {
        val ic = connectionProvider() ?: return null
        val selected = ic.getSelectedText(0)?.toString()
        ic.performContextMenuAction(android.R.id.copy)
        return selected
    }

    override fun cutSelection(): String? {
        val ic = connectionProvider() ?: return null
        val selected = ic.getSelectedText(0)?.toString()
        ic.performContextMenuAction(android.R.id.cut)
        return selected
    }

    override fun undo() {
        val ic = connectionProvider() ?: return
        if (!ic.performContextMenuAction(android.R.id.undo)) {
            ic.sendKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z, 0, KeyEvent.META_CTRL_ON))
            ic.sendKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_Z, 0, KeyEvent.META_CTRL_ON))
        }
    }

    override fun redo() {
        val ic = connectionProvider() ?: return
        if (!ic.performContextMenuAction(android.R.id.redo)) {
            ic.sendKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_DOWN, KeyEvent.KEYCODE_Z, 0, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON))
            ic.sendKeyEvent(KeyEvent(0, 0, KeyEvent.ACTION_UP, KeyEvent.KEYCODE_Z, 0, KeyEvent.META_CTRL_ON or KeyEvent.META_SHIFT_ON))
        }
    }
}

data class KeyboardUiState(
    val mode: KeyboardMode = KeyboardMode.ALPHA,
    val shiftState: ShiftState = ShiftState.OFF,
    val toolbarExpanded: Boolean = false,
    val suggestions: List<String> = emptyList(),
    val autocorrectCandidate: String? = null,
    val currentWord: String = "",
    val layout: KeyboardLayout = KeyboardLayout("qwerty", emptyList())
)

class HeliKeyboardController(
    private val context: Context,
    private val prefsRepo: PreferencesRepository,
    private val assetRepo: AssetRepository,
    private val suggestionEngine: SuggestionEngine,
    private val target: TextInputTarget,
    private val onOpenSettings: (() -> Unit)? = null
) {
    private val _uiState = MutableStateFlow(KeyboardUiState())
    val uiState: StateFlow<KeyboardUiState> = _uiState.asStateFlow()

    private var lastSpaceTime = 0L
    private var lastAutocorrectOriginal: String? = null
    private var lastAutocorrectReplaced: String? = null

    private val vibrator: Vibrator? by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val vm = context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            vm?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    }

    private val audioManager: AudioManager? by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    init {
        refreshLayoutAndSuggestions()
    }

    fun refreshLayoutAndSuggestions() {
        val settings = prefsRepo.settings.value
        val subtype = prefsRepo.activeSubtype()
        val customLayouts = prefsRepo.customLayouts.value
        val currentState = _uiState.value

        val autoShift = if (currentState.mode == KeyboardMode.ALPHA &&
            currentState.shiftState == ShiftState.OFF &&
            settings.autoCap &&
            shouldAutoCap()
        ) {
            ShiftState.SHIFTED
        } else {
            currentState.shiftState
        }

        val layout = when (currentState.mode) {
            KeyboardMode.ALPHA -> assetRepo.loadMainKeyboardLayout(
                layoutId = subtype.layoutId,
                localeTag = subtype.localeTag,
                customLayouts = customLayouts,
                showNumberRow = settings.showNumberRow,
                showLanguageSwitch = settings.showLanguageSwitchKey && settings.subtypes.count { it.isEnabled } > 1,
                showEmojiKey = settings.showEmojiKey,
                shiftState = autoShift
            )
            KeyboardMode.SYMBOLS -> assetRepo.loadSymbolsLayout(shifted = false)
            KeyboardMode.MORE_SYMBOLS -> assetRepo.loadSymbolsLayout(shifted = true)
            KeyboardMode.NUMPAD -> assetRepo.loadNumpadLayout()
            else -> currentState.layout
        }

        val (word, prevWord) = extractCurrentAndPreviousWord()
        val personal = prefsRepo.personalWords.value
        val suggestions = if (settings.showSuggestions) {
            suggestionEngine.suggest(
                currentWord = word,
                previousWord = prevWord,
                personalWords = personal,
                suggestEmojis = settings.suggestEmojis
            )
        } else {
            emptyList()
        }
        val autocorrect = if (settings.autoCorrection && word.length >= 2) {
            suggestionEngine.autocorrectCandidate(word, personal)
        } else {
            null
        }

        _uiState.update {
            it.copy(
                shiftState = autoShift,
                layout = layout,
                currentWord = word,
                suggestions = suggestions,
                autocorrectCandidate = autocorrect
            )
        }
    }

    fun onKeyPress(key: KeySpec, customOutput: String? = null) {
        triggerFeedback(key.actionType)
        val settings = prefsRepo.settings.value

        when (key.actionType) {
            KeyActionType.SHIFT -> {
                lastAutocorrectOriginal = null
                val nextShift = when (_uiState.value.shiftState) {
                    ShiftState.OFF -> ShiftState.SHIFTED
                    ShiftState.SHIFTED -> ShiftState.CAPS_LOCK
                    ShiftState.CAPS_LOCK -> ShiftState.OFF
                }
                _uiState.update { it.copy(shiftState = nextShift) }
                refreshLayoutAndSuggestions()
                return
            }
            KeyActionType.DELETE -> {
                handleBackspace()
                return
            }
            KeyActionType.SPACE -> {
                handleSpace(settings.doubleSpacePeriod, settings.autoCorrection, settings.incognitoMode)
                return
            }
            KeyActionType.ENTER -> {
                commitCurrentWordCorrection(settings.autoCorrection, settings.incognitoMode)
                target.performEnter()
                _uiState.update {
                    it.copy(
                        shiftState = if (settings.autoCap) ShiftState.SHIFTED else ShiftState.OFF
                    )
                }
                refreshLayoutAndSuggestions()
                return
            }
            KeyActionType.SWITCH_SYMBOLS -> {
                _uiState.update { it.copy(mode = KeyboardMode.SYMBOLS) }
                refreshLayoutAndSuggestions()
                return
            }
            KeyActionType.SWITCH_MORE_SYMBOLS -> {
                val next = if (_uiState.value.mode == KeyboardMode.SYMBOLS) KeyboardMode.MORE_SYMBOLS else KeyboardMode.SYMBOLS
                _uiState.update { it.copy(mode = next) }
                refreshLayoutAndSuggestions()
                return
            }
            KeyActionType.SWITCH_ALPHA -> {
                _uiState.update { it.copy(mode = KeyboardMode.ALPHA) }
                refreshLayoutAndSuggestions()
                return
            }
            KeyActionType.SWITCH_NUMPAD -> {
                _uiState.update { it.copy(mode = KeyboardMode.NUMPAD) }
                refreshLayoutAndSuggestions()
                return
            }
            KeyActionType.EMOJI -> {
                val next = if (_uiState.value.mode == KeyboardMode.EMOJI) KeyboardMode.ALPHA else KeyboardMode.EMOJI
                _uiState.update { it.copy(mode = next) }
                refreshLayoutAndSuggestions()
                return
            }
            KeyActionType.LANGUAGE_SWITCH -> {
                prefsRepo.cycleSubtype()
                refreshLayoutAndSuggestions()
                return
            }
            KeyActionType.PLACEHOLDER -> return
            KeyActionType.CHARACTER -> {
                val baseChar = customOutput ?: key.outputText
                val out = if (customOutput == null && _uiState.value.shiftState != ShiftState.OFF) {
                    key.shiftedOutput ?: baseChar.uppercase()
                } else {
                    baseChar
                }
                if (out in listOf(".", ",", "!", "?", ";", ":")) {
                    commitCurrentWordCorrection(settings.autoCorrection, settings.incognitoMode)
                } else {
                    lastAutocorrectOriginal = null
                }
                target.commitText(out)
                if (_uiState.value.shiftState == ShiftState.SHIFTED) {
                    _uiState.update { it.copy(shiftState = ShiftState.OFF) }
                }
                refreshLayoutAndSuggestions()
            }
        }
    }

    fun onSuggestionSelected(suggestion: String) {
        triggerFeedback(KeyActionType.CHARACTER)
        val word = _uiState.value.currentWord
        if (word.isNotEmpty()) {
            target.deleteBeforeCursor(word.length)
        }
        val isEmoji = suggestion.any { Character.getType(it) == Character.SURROGATE.toInt() || it.code > 0x2600 }
        val toCommit = if (isEmoji) suggestion else "$suggestion "
        target.commitText(toCommit)
        if (!prefsRepo.settings.value.incognitoMode && !isEmoji) {
            val (_, prev) = extractCurrentAndPreviousWord()
            suggestionEngine.learnWord(suggestion, prev)
        }
        lastAutocorrectOriginal = null
        refreshLayoutAndSuggestions()
    }

    fun onEmojiSelected(emoji: String) {
        triggerFeedback(KeyActionType.CHARACTER)
        val tone = prefsRepo.settings.value.emojiSkinTone
        val finalEmoji = if (tone.isNotEmpty() && supportsSkinTone(emoji)) emoji + tone else emoji
        target.commitText(finalEmoji)
        prefsRepo.addEmojiToRecents(emoji)
        refreshLayoutAndSuggestions()
    }

    fun onPasteClip(text: String) {
        triggerFeedback(KeyActionType.CHARACTER)
        target.commitText(text)
        prefsRepo.copyToSystemClipboard(text)
        refreshLayoutAndSuggestions()
    }

    fun onToolbarAction(action: ToolbarKey) {
        triggerFeedback(KeyActionType.SHIFT)
        when (action) {
            ToolbarKey.EMOJI -> toggleMode(KeyboardMode.EMOJI)
            ToolbarKey.CLIPBOARD -> {
                prefsRepo.captureSystemClipboard()
                toggleMode(KeyboardMode.CLIPBOARD)
            }
            ToolbarKey.NUMPAD -> toggleMode(KeyboardMode.NUMPAD)
            ToolbarKey.DPAD -> toggleMode(KeyboardMode.DPAD)
            ToolbarKey.UNDO -> {
                target.undo()
                refreshLayoutAndSuggestions()
            }
            ToolbarKey.REDO -> {
                target.redo()
                refreshLayoutAndSuggestions()
            }
            ToolbarKey.SELECT_ALL -> target.selectAll()
            ToolbarKey.COPY -> target.copySelection()?.let { prefsRepo.addClipboardEntry(it) }
            ToolbarKey.CUT -> {
                target.cutSelection()?.let { prefsRepo.addClipboardEntry(it) }
                refreshLayoutAndSuggestions()
            }
            ToolbarKey.PASTE -> {
                val clip = prefsRepo.clipboard.value.firstOrNull()?.text
                if (!clip.isNullOrEmpty()) {
                    target.commitText(clip)
                    refreshLayoutAndSuggestions()
                }
            }
            ToolbarKey.ONE_HANDED -> {
                prefsRepo.updateSettings { it.copy(oneHandedMode = !it.oneHandedMode) }
            }
            ToolbarKey.SPLIT -> {
                prefsRepo.updateSettings { it.copy(splitKeyboard = !it.splitKeyboard) }
            }
            ToolbarKey.INCOGNITO -> {
                prefsRepo.updateSettings { it.copy(incognitoMode = !it.incognitoMode) }
            }
            ToolbarKey.AUTOCORRECT -> {
                prefsRepo.updateSettings { it.copy(autoCorrection = !it.autoCorrection) }
                refreshLayoutAndSuggestions()
            }
            ToolbarKey.CLEAR_CLIPBOARD -> prefsRepo.clearUnpinnedClipboard()
            ToolbarKey.LEFT -> {
                target.moveCursor(-1)
                refreshLayoutAndSuggestions()
            }
            ToolbarKey.RIGHT -> {
                target.moveCursor(1)
                refreshLayoutAndSuggestions()
            }
            ToolbarKey.SETTINGS -> onOpenSettings?.invoke()
        }
    }

    fun toggleToolbarExpanded() {
        _uiState.update { it.copy(toolbarExpanded = !it.toolbarExpanded) }
    }

    fun setMode(mode: KeyboardMode) {
        _uiState.update { it.copy(mode = mode) }
        refreshLayoutAndSuggestions()
    }

    fun toggleOneHandedSide() {
        prefsRepo.updateSettings { it.copy(oneHandedRight = !it.oneHandedRight) }
    }

    fun exitOneHanded() {
        prefsRepo.updateSettings { it.copy(oneHandedMode = false) }
    }

    fun onSpacebarSwipe(steps: Int) {
        if (!prefsRepo.settings.value.spacebarSwipeCursor || steps == 0) return
        target.moveCursor(steps)
        refreshLayoutAndSuggestions()
    }

    fun onDpadAction(action: String) {
        triggerFeedback(KeyActionType.CHARACTER)
        when (action) {
            "LEFT" -> target.moveCursor(-1)
            "RIGHT" -> target.moveCursor(1)
            "UP" -> target.moveCursorVertical(-1)
            "DOWN" -> target.moveCursorVertical(1)
            "HOME" -> target.moveToLineEdge(false)
            "END" -> target.moveToLineEdge(true)
            "WORD_LEFT" -> {
                val before = target.getTextBeforeCursor(60)
                val trimmed = before.trimEnd()
                val idx = trimmed.lastIndexOfAny(charArrayOf(' ', '\n'))
                val steps = if (idx == -1) before.length else (before.length - idx - 1).coerceAtLeast(1)
                target.moveCursor(-steps)
            }
            "WORD_RIGHT" -> {
                val after = target.getTextAfterCursor(60)
                val nonSpace = after.indexOfFirst { !it.isWhitespace() }.coerceAtLeast(0)
                val nextSpace = after.indexOfAny(charArrayOf(' ', '\n'), nonSpace)
                val steps = if (nextSpace == -1) after.length else nextSpace + 1
                target.moveCursor(steps.coerceAtLeast(1))
            }
            "SELECT_ALL" -> target.selectAll()
            "COPY" -> target.copySelection()?.let { prefsRepo.addClipboardEntry(it) }
            "CUT" -> target.cutSelection()?.let { prefsRepo.addClipboardEntry(it) }
            "PASTE" -> prefsRepo.clipboard.value.firstOrNull()?.text?.let { target.commitText(it) }
        }
        refreshLayoutAndSuggestions()
    }

    private fun toggleMode(targetMode: KeyboardMode) {
        val next = if (_uiState.value.mode == targetMode) KeyboardMode.ALPHA else targetMode
        _uiState.update { it.copy(mode = next) }
        refreshLayoutAndSuggestions()
    }

    private fun handleBackspace() {
        val orig = lastAutocorrectOriginal
        val repl = lastAutocorrectReplaced
        if (orig != null && repl != null) {
            val before = target.getTextBeforeCursor(repl.length + 1)
            if (before == "$repl ") {
                target.deleteBeforeCursor(repl.length + 1)
                target.commitText(orig)
                lastAutocorrectOriginal = null
                lastAutocorrectReplaced = null
                refreshLayoutAndSuggestions()
                return
            }
        }
        lastAutocorrectOriginal = null
        lastAutocorrectReplaced = null
        target.deleteBeforeCursor(1)
        refreshLayoutAndSuggestions()
    }

    private fun handleSpace(doubleSpacePeriod: Boolean, autoCorrection: Boolean, incognito: Boolean) {
        val now = System.currentTimeMillis()
        val before = target.getTextBeforeCursor(4)
        if (doubleSpacePeriod && now - lastSpaceTime < 450 &&
            before.length >= 2 && before.endsWith(" ") && !before[before.length - 2].isWhitespace() &&
            before[before.length - 2] != '.'
        ) {
            target.deleteBeforeCursor(1)
            target.commitText(". ")
            lastSpaceTime = 0L
            if (prefsRepo.settings.value.autoCap) {
                _uiState.update { it.copy(shiftState = ShiftState.SHIFTED) }
            }
            refreshLayoutAndSuggestions()
            return
        }
        lastSpaceTime = now
        commitCurrentWordCorrection(autoCorrection, incognito)
        target.commitText(" ")
        refreshLayoutAndSuggestions()
    }

    private fun commitCurrentWordCorrection(autoCorrection: Boolean, incognito: Boolean) {
        val (word, prev) = extractCurrentAndPreviousWord()
        if (word.isEmpty()) return
        val candidate = if (autoCorrection) {
            suggestionEngine.autocorrectCandidate(word, prefsRepo.personalWords.value)
        } else null

        if (candidate != null && candidate != word) {
            target.deleteBeforeCursor(word.length)
            target.commitText(candidate)
            lastAutocorrectOriginal = word
            lastAutocorrectReplaced = candidate
            if (!incognito) suggestionEngine.learnWord(candidate, prev)
        } else {
            lastAutocorrectOriginal = null
            lastAutocorrectReplaced = null
            if (!incognito) suggestionEngine.learnWord(word, prev)
        }
    }

    private fun extractCurrentAndPreviousWord(): Pair<String, String?> {
        val before = target.getTextBeforeCursor(80)
        if (before.isEmpty()) return "" to null
        if (before.last().isWhitespace() || before.last() in listOf('.', ',', '!', '?', ':', ';')) {
            val prev = before.trimEnd { !it.isLetterOrDigit() && it != '\'' }
                .takeLastWhile { it.isLetterOrDigit() || it == '\'' }
                .takeIf { it.isNotEmpty() }
            return "" to prev
        }
        val current = before.takeLastWhile { it.isLetterOrDigit() || it == '\'' || it == '-' }
        val preceding = before.dropLast(current.length)
            .trimEnd { !it.isLetterOrDigit() && it != '\'' }
            .takeLastWhile { it.isLetterOrDigit() || it == '\'' }
            .takeIf { it.isNotEmpty() }
        return current to preceding
    }

    private fun shouldAutoCap(): Boolean {
        val before = target.getTextBeforeCursor(24)
        if (before.isEmpty()) return true
        val trimmed = before.trimEnd(' ')
        if (trimmed.isEmpty()) return true
        val last = trimmed.last()
        return last == '\n' || ((last == '.' || last == '!' || last == '?') && before.endsWith(" "))
    }

    private fun triggerFeedback(actionType: KeyActionType) {
        val settings = prefsRepo.settings.value
        if (settings.vibrateOnKeypress) {
            runCatching {
                val ms = if (actionType == KeyActionType.CHARACTER) 12L else 20L
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    vibrator?.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
                } else {
                    @Suppress("DEPRECATION")
                    vibrator?.vibrate(ms)
                }
            }
        }
        if (settings.soundOnKeypress) {
            val fx = when (actionType) {
                KeyActionType.DELETE -> AudioManager.FX_KEYPRESS_DELETE
                KeyActionType.SPACE -> AudioManager.FX_KEYPRESS_SPACEBAR
                KeyActionType.ENTER -> AudioManager.FX_KEYPRESS_RETURN
                else -> AudioManager.FX_KEYPRESS_STANDARD
            }
            runCatching { audioManager?.playSoundEffect(fx) }
        }
    }

    private fun supportsSkinTone(emoji: String): Boolean {
        val first = emoji.codePointAt(0)
        return first in 0x1F446..0x1F4AA || first in 0x1F91A..0x1F91F || first in 0x1F64B..0x1F64F
    }
}
