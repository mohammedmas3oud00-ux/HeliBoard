package com.example.heliboard.ime

import android.content.ClipDescription
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.text.InputType
import android.view.KeyEvent
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.core.view.inputmethod.EditorInfoCompat
import androidx.core.view.inputmethod.InputConnectionCompat
import androidx.core.view.inputmethod.InputContentInfoCompat
import com.example.heliboard.BuildConfig
import com.example.heliboard.R
import com.example.heliboard.ai.AiConfigResolver
import com.example.heliboard.ai.AiLanguageOption
import com.example.heliboard.ai.AiProxyClient
import com.example.heliboard.ai.AiProxyConfig
import com.example.heliboard.ai.AiProxyErrorType
import com.example.heliboard.ai.AiProxyException
import com.example.heliboard.ai.AiTone
import com.example.heliboard.ai.AiTranslateRequest
import com.example.heliboard.ai.AiTranslateResult
import com.example.heliboard.ai.AiWritingOperation
import com.example.heliboard.ai.AiWritingRequest
import com.example.heliboard.ai.AiWritingResult
import com.example.heliboard.ai.HttpAiProxyClient
import com.example.heliboard.data.AssetRepository
import com.example.heliboard.data.ClipboardEntry
import com.example.heliboard.data.ImeActionType
import com.example.heliboard.data.KeyActionType
import com.example.heliboard.data.KeySpec
import com.example.heliboard.data.KeyboardLayout
import com.example.heliboard.data.KeyboardMode
import com.example.heliboard.data.PopupSpec
import com.example.heliboard.data.PreferencesRepository
import com.example.heliboard.data.ShiftState
import com.example.heliboard.data.SuggestionEngine
import com.example.heliboard.data.ToolbarKey
import com.example.heliboard.theme.containsArabicScript
import com.example.heliboard.util.LocaleHelper
import com.example.heliboard.voice.AndroidVoiceDictationBackend
import com.example.heliboard.voice.VoiceDictationBackend
import com.example.heliboard.voice.VoiceErrorType
import com.example.heliboard.voice.VoiceRecognitionListener
import com.example.heliboard.voice.VoiceSessionStatus
import com.example.heliboard.voice.VoiceUiState
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

interface TextInputTarget {
    fun getTextBeforeCursor(maxChars: Int): String
    fun getTextAfterCursor(maxChars: Int): String
    fun getSelectedText(): String? = null
    fun commitText(text: String)
    fun replaceSelection(expectedOriginal: String, replacement: String): Boolean = false
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
    fun supportsImageMimeType(mimeType: String): Boolean = false
    fun commitImage(uri: Uri, mimeType: String): Boolean = false
    fun isSensitiveInput(): Boolean = false
    fun isPasswordInput(): Boolean = isSensitiveInput()
    fun getImeAction(): ImeActionType = ImeActionType.ENTER
}

class SandboxInputTarget : TextInputTarget {
    private val _value = MutableStateFlow(TextFieldValue(""))
    val value: StateFlow<TextFieldValue> = _value.asStateFlow()

    private val _acceptsImages = MutableStateFlow(false)
    val acceptsImages: StateFlow<Boolean> = _acceptsImages.asStateFlow()

    private val _pastedImages = MutableStateFlow<List<String>>(emptyList())
    val pastedImages: StateFlow<List<String>> = _pastedImages.asStateFlow()

    private val _imeAction = MutableStateFlow(ImeActionType.ENTER)
    private val _passwordInput = MutableStateFlow(false)

    fun setImeAction(action: ImeActionType) {
        _imeAction.value = action
    }

    fun setPasswordInput(enabled: Boolean) {
        _passwordInput.value = enabled
    }

    override fun getImeAction(): ImeActionType = _imeAction.value

    override fun isSensitiveInput(): Boolean = _passwordInput.value

    override fun isPasswordInput(): Boolean = _passwordInput.value

    fun setAcceptsImages(enabled: Boolean) {
        _acceptsImages.value = enabled
    }

    fun clearPastedImages() {
        _pastedImages.value = emptyList()
    }

    override fun supportsImageMimeType(mimeType: String): Boolean {
        return _acceptsImages.value && mimeType.startsWith("image/")
    }

    override fun commitImage(uri: Uri, mimeType: String): Boolean {
        if (!supportsImageMimeType(mimeType)) return false
        _pastedImages.update { it + uri.toString() }
        return true
    }

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

    override fun getSelectedText(): String? {
        val v = _value.value
        if (v.selection.length <= 0) return null
        val min = v.selection.min.coerceIn(0, v.text.length)
        val max = v.selection.max.coerceIn(0, v.text.length)
        return v.text.substring(min, max).takeIf { it.isNotEmpty() }
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

    override fun replaceSelection(expectedOriginal: String, replacement: String): Boolean {
        val v = _value.value
        if (v.selection.length <= 0) return false
        val min = v.selection.min.coerceIn(0, v.text.length)
        val max = v.selection.max.coerceIn(0, v.text.length)
        val selected = v.text.substring(min, max)
        if (selected != expectedOriginal) return false
        pushUndo(v)
        val nextText = v.text.replaceRange(min, max, replacement)
        val cursor = min + replacement.length
        _value.value = TextFieldValue(nextText, TextRange(cursor))
        return true
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

    override fun getSelectedText(): String? =
        connectionProvider()?.getSelectedText(0)?.toString()?.takeIf { it.isNotEmpty() }

    override fun commitText(text: String) {
        connectionProvider()?.commitText(text, 1)
    }

    override fun replaceSelection(expectedOriginal: String, replacement: String): Boolean {
        val ic = connectionProvider() ?: return false
        val current = ic.getSelectedText(0)?.toString() ?: return false
        if (current != expectedOriginal) return false
        return ic.commitText(replacement, 1)
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

    override fun isSensitiveInput(): Boolean {
        return isSensitiveEditorInfo(editorInfoProvider())
    }

    override fun isPasswordInput(): Boolean {
        return isPasswordEditorInfo(editorInfoProvider())
    }

    override fun getImeAction(): ImeActionType {
        val info = editorInfoProvider() ?: return ImeActionType.ENTER
        if ((info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0) {
            return ImeActionType.ENTER
        }
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_SEARCH -> ImeActionType.SEARCH
            EditorInfo.IME_ACTION_SEND -> ImeActionType.SEND
            EditorInfo.IME_ACTION_DONE -> ImeActionType.DONE
            EditorInfo.IME_ACTION_NEXT -> ImeActionType.NEXT
            EditorInfo.IME_ACTION_GO -> ImeActionType.GO
            EditorInfo.IME_ACTION_PREVIOUS -> ImeActionType.PREVIOUS
            else -> ImeActionType.ENTER
        }
    }

    override fun supportsImageMimeType(mimeType: String): Boolean {
        val info = editorInfoProvider() ?: return false
        val accepted = EditorInfoCompat.getContentMimeTypes(info)
        if (accepted.isEmpty()) return false
        return accepted.any { pattern -> ClipDescription.compareMimeTypes(mimeType, pattern) }
    }

    override fun commitImage(uri: Uri, mimeType: String): Boolean {
        val ic = connectionProvider() ?: return false
        val info = editorInfoProvider() ?: return false
        if (!supportsImageMimeType(mimeType)) return false
        val contentInfo = InputContentInfoCompat(
            uri,
            ClipDescription("HeliBoard image", arrayOf(mimeType)),
            null
        )
        return InputConnectionCompat.commitContent(
            ic,
            info,
            contentInfo,
            InputConnectionCompat.INPUT_CONTENT_GRANT_READ_URI_PERMISSION,
            null
        )
    }

    companion object {
        fun isPasswordEditorInfo(info: EditorInfo?): Boolean {
            if (info == null) return false
            val inputClass = info.inputType and InputType.TYPE_MASK_CLASS
            val variation = info.inputType and InputType.TYPE_MASK_VARIATION
            if (inputClass == InputType.TYPE_CLASS_TEXT) {
                if (variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                    variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
                ) {
                    return true
                }
            } else if (inputClass == InputType.TYPE_CLASS_NUMBER &&
                variation == InputType.TYPE_NUMBER_VARIATION_PASSWORD
            ) {
                return true
            }
            return false
        }

        fun isSensitiveEditorInfo(info: EditorInfo?): Boolean {
            if (info == null) return false
            if ((info.imeOptions and EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING) != 0) return true
            return isPasswordEditorInfo(info)
        }
    }
}

data class AiWritingUiState(
    val operation: AiWritingOperation = AiWritingOperation.PROOFREAD,
    val tone: AiTone = AiTone.SAME,
    val targetLanguage: AiLanguageOption = AiLanguageOption.AUTO,
    val inputText: String = "",
    val loadedFromSelection: Boolean = false,
    val originalSelectedText: String? = null,
    val originFieldGeneration: Long = 0L,
    val isLoading: Boolean = false,
    val errorType: AiProxyErrorType? = null,
    val errorMessage: String? = null,
    val result: AiWritingResult? = null,
    val copiedNotice: Boolean = false,
    val isFieldChanged: Boolean = false
) {
    val canReplaceSelection: Boolean
        get() = loadedFromSelection && !originalSelectedText.isNullOrEmpty() && !isFieldChanged
}

data class TranslateUiState(
    val sourceLanguage: AiLanguageOption = AiLanguageOption.AUTO,
    val targetLanguage: AiLanguageOption = AiLanguageOption.AR,
    val inputText: String = "",
    val loadedFromSelection: Boolean = false,
    val originalSelectedText: String? = null,
    val originFieldGeneration: Long = 0L,
    val autoTranslateEnabled: Boolean = false,
    val isLoading: Boolean = false,
    val errorType: AiProxyErrorType? = null,
    val errorMessage: String? = null,
    val result: AiTranslateResult? = null,
    val copiedNotice: Boolean = false,
    val isFieldChanged: Boolean = false
) {
    val canSwapLanguages: Boolean
        get() = sourceLanguage != AiLanguageOption.AUTO && sourceLanguage != targetLanguage

    val canReplaceSelection: Boolean
        get() = loadedFromSelection && !originalSelectedText.isNullOrEmpty() && !isFieldChanged
}

data class KeyboardUiState(
    val mode: KeyboardMode = KeyboardMode.ALPHA,
    val shiftState: ShiftState = ShiftState.OFF,
    val toolbarExpanded: Boolean = false,
    val suggestions: List<String> = emptyList(),
    val autocorrectCandidate: String? = null,
    val currentWord: String = "",
    val layout: KeyboardLayout = KeyboardLayout("qwerty", emptyList()),
    val isSensitiveInput: Boolean = false,
    val isPasswordInput: Boolean = false,
    val imeAction: ImeActionType = ImeActionType.ENTER,
    val clipboardFeedbackMessage: String? = null,
    val voice: VoiceUiState = VoiceUiState(),
    val aiWriting: AiWritingUiState = AiWritingUiState(),
    val translate: TranslateUiState = TranslateUiState()
)

class HeliKeyboardController(
    private val context: Context,
    private val prefsRepo: PreferencesRepository,
    private val assetRepo: AssetRepository,
    private val suggestionEngine: SuggestionEngine,
    private val target: TextInputTarget,
    private val onOpenSettings: (() -> Unit)? = null,
    private val onOpenAiSettings: (() -> Unit)? = null,
    private val voiceBackend: VoiceDictationBackend = AndroidVoiceDictationBackend(context),
    private val aiProxyClient: AiProxyClient = HttpAiProxyClient(context),
    private val hapticFeedbackManager: HapticFeedbackManager = HapticFeedbackManager(context),
    private val mainDispatcher: CoroutineDispatcher = Dispatchers.Main.immediate,
    private val autoTranslateDebounceMs: Long = 550L,
    private val voiceAutoSilenceTimeoutMs: Long = 6000L,
    private val defaultProxyUrlProvider: () -> String = { BuildConfig.AI_PROXY_URL },
    private val defaultProxyTokenProvider: () -> String = { BuildConfig.AI_PROXY_AUTH_TOKEN },
    private val defaultGeminiApiKeyProvider: () -> String = { BuildConfig.GEMINI_API_KEY },
    var onRequestAudioPermission: (() -> Unit)? = null,
    var onSystemVoiceFallback: (() -> Boolean)? = null
) {
    private val _uiState = MutableStateFlow(KeyboardUiState())
    val uiState: StateFlow<KeyboardUiState> = _uiState.asStateFlow()

    private val controllerScope = CoroutineScope(SupervisorJob() + mainDispatcher)

    private var lastSpaceTime = 0L
    private var lastAutocorrectOriginal: String? = null
    private var lastAutocorrectReplaced: String? = null

    private var fieldGeneration: Long = 0L
    private var logicalVoiceCounter: Long = 0L
    private var activeLogicalVoiceSessionId: Long = -1L
    private var voiceSessionCounter: Long = 0L
    private var activeVoiceSessionId: Long = -1L
    private var activeSessionFieldGeneration: Long = -1L
    private val committedSegmentIds = mutableSetOf<Long>()
    private var stopRequestedAfterSegment: Boolean = false
    private val sessionDictatedText = StringBuilder()
    private var silenceWatchdogJob: Job? = null
    private var stopFinalizeTimeoutJob: Job? = null

    private var writingRequestSeq: Long = 0L
    private var writingJob: Job? = null
    private var translateRequestSeq: Long = 0L
    private var translateJob: Job? = null
    private var autoTranslateDebounceJob: Job? = null

    private val audioManager: AudioManager? by lazy {
        context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    }

    init {
        refreshLayoutAndSuggestions()
    }

    private fun localizedContext(): Context {
        return LocaleHelper.createLocalizedContext(context, prefsRepo.settings.value.appLanguage).first
    }

    private fun str(resId: Int, vararg args: Any): String {
        val ctx = localizedContext()
        return if (args.isEmpty()) ctx.getString(resId) else ctx.getString(resId, *args)
    }

    fun onInputFieldChanged(restarting: Boolean = false) {
        if (!restarting) {
            fieldGeneration++
            cancelVoiceDictationInternal(clearState = true)
            stopAutoTranslateAndMarkFieldChanged()
        }
        refreshLayoutAndSuggestions()
    }

    fun onKeyboardHidden() {
        fieldGeneration++
        cancelVoiceDictationInternal(clearState = true)
        stopAutoTranslateAndMarkFieldChanged()
    }

    fun refreshLayoutAndSuggestions() {
        val settings = prefsRepo.settings.value
        val subtype = prefsRepo.activeSubtype()
        val customLayouts = prefsRepo.customLayouts.value
        val currentState = _uiState.value

        val casedLocale = isCasedLocale(subtype.localeTag)
        val autoShift = if (!casedLocale) {
            ShiftState.OFF
        } else if (currentState.mode == KeyboardMode.ALPHA &&
            currentState.shiftState == ShiftState.OFF &&
            settings.autoCap &&
            shouldAutoCap()
        ) {
            ShiftState.SHIFTED
        } else {
            currentState.shiftState
        }

        val enabledLanguagesCount = settings.subtypes.count { it.isEnabled }
        val layout = when (currentState.mode) {
            KeyboardMode.ALPHA -> assetRepo.loadMainKeyboardLayout(
                layoutId = subtype.layoutId,
                localeTag = subtype.localeTag,
                customLayouts = customLayouts,
                showNumberRow = settings.showNumberRow,
                showLanguageSwitch = enabledLanguagesCount > 1,
                showEmojiKey = settings.showEmojiKey,
                shiftState = autoShift
            )
            KeyboardMode.SYMBOLS -> assetRepo.loadSymbolsLayout(shifted = false, localeTag = subtype.localeTag)
            KeyboardMode.MORE_SYMBOLS -> assetRepo.loadSymbolsLayout(shifted = true, localeTag = subtype.localeTag)
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

        val sensitive = target.isSensitiveInput()
        val password = target.isPasswordInput()
        val imeAction = target.getImeAction()
        _uiState.update {
            it.copy(
                shiftState = autoShift,
                layout = layout,
                currentWord = word,
                suggestions = suggestions,
                autocorrectCandidate = autocorrect,
                isSensitiveInput = sensitive,
                isPasswordInput = password,
                imeAction = imeAction
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
                val casedLocale = isCasedLocale(prefsRepo.activeSubtype().localeTag)
                _uiState.update {
                    it.copy(
                        shiftState = if (settings.autoCap && casedLocale) ShiftState.SHIFTED else ShiftState.OFF
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
                if (out in listOf(".", ",", "!", "?", ";", ":", "،", "؟", "؛")) {
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

    fun onPopupSelected(
        key: KeySpec,
        popup: PopupSpec,
        resolvedOutput: String,
        replaceInstantPreviewChar: Boolean = false
    ) {
        if (popup.actionType == KeyActionType.EMOJI) {
            if (replaceInstantPreviewChar) {
                target.deleteBeforeCursor(1)
            }
            triggerFeedback(KeyActionType.EMOJI)
            toggleMode(KeyboardMode.EMOJI)
            return
        }
        if (replaceInstantPreviewChar && key.actionType == KeyActionType.CHARACTER) {
            target.deleteBeforeCursor(1)
        }
        onKeyPress(key, customOutput = resolvedOutput)
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
        if (!prefsRepo.settings.value.incognitoMode && !target.isSensitiveInput()) {
            prefsRepo.copyToSystemClipboard(text)
        }
        _uiState.update { it.copy(clipboardFeedbackMessage = null) }
        refreshLayoutAndSuggestions()
    }

    fun onPasteClipboardEntry(entry: ClipboardEntry) {
        triggerFeedback(KeyActionType.CHARACTER)
        if (!entry.isImage) {
            onPasteClip(entry.text)
            return
        }
        val resolved = prefsRepo.resolveImageForPaste(entry)
        if (resolved == null) {
            _uiState.update {
                it.copy(clipboardFeedbackMessage = str(R.string.clipboard_image_unavailable_hint))
            }
            return
        }
        val (uri, mimeType) = resolved
        if (!target.supportsImageMimeType(mimeType)) {
            _uiState.update {
                it.copy(clipboardFeedbackMessage = str(R.string.clipboard_image_not_supported))
            }
            return
        }
        val committed = target.commitImage(uri, mimeType)
        if (!committed) {
            _uiState.update {
                it.copy(clipboardFeedbackMessage = str(R.string.clipboard_image_not_supported))
            }
        } else {
            _uiState.update { it.copy(clipboardFeedbackMessage = null) }
            refreshLayoutAndSuggestions()
        }
    }

    fun clearClipboardFeedback() {
        _uiState.update { it.copy(clipboardFeedbackMessage = null) }
    }

    fun movePinnedClipboard(fromIndex: Int, toIndex: Int) {
        prefsRepo.movePinnedEntry(fromIndex, toIndex)
    }

    fun movePinnedClipboardById(id: Long, delta: Int) {
        prefsRepo.movePinnedEntryById(id, delta)
    }

    fun onToolbarAction(action: ToolbarKey) {
        triggerFeedback(KeyActionType.SHIFT)
        when (action) {
            ToolbarKey.VOICE -> startOrStopVoiceDictation()
            ToolbarKey.EMOJI -> toggleMode(KeyboardMode.EMOJI)
            ToolbarKey.CLIPBOARD -> {
                val sensitive = target.isSensitiveInput()
                prefsRepo.pruneExpiredClipboard()
                prefsRepo.captureSystemClipboard(isSensitiveField = sensitive)
                _uiState.update { it.copy(clipboardFeedbackMessage = null) }
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
            ToolbarKey.COPY -> {
                target.copySelection()?.let {
                    prefsRepo.addClipboardEntry(it, isSensitiveField = target.isSensitiveInput())
                }
            }
            ToolbarKey.CUT -> {
                target.cutSelection()?.let {
                    prefsRepo.addClipboardEntry(it, isSensitiveField = target.isSensitiveInput())
                }
                refreshLayoutAndSuggestions()
            }
            ToolbarKey.PASTE -> {
                val clip = prefsRepo.clipboard.value.firstOrNull()
                if (clip != null) {
                    onPasteClipboardEntry(clip)
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
            ToolbarKey.AI_WRITING -> openOrToggleAiWriting()
            ToolbarKey.TRANSLATE -> openOrToggleTranslate()
        }
    }

    fun ensureVoiceDictationStarted() {
        val currentStatus = _uiState.value.voice.status
        if (currentStatus == VoiceSessionStatus.LISTENING || currentStatus == VoiceSessionStatus.PROCESSING) {
            return
        }
        startOrStopVoiceDictation()
    }

    fun startOrStopVoiceDictation() {
        val currentStatus = _uiState.value.voice.status
        if (currentStatus == VoiceSessionStatus.LISTENING || currentStatus == VoiceSessionStatus.PROCESSING) {
            stopVoiceDictation()
            return
        }

        if (target.isPasswordInput()) {
            _uiState.update {
                it.copy(
                    voice = VoiceUiState(
                        status = VoiceSessionStatus.UNAVAILABLE,
                        message = str(R.string.voice_disabled_password),
                        canUseSystemVoiceInput = false
                    )
                )
            }
            return
        }

        if (!voiceBackend.hasAudioPermission()) {
            _uiState.update {
                it.copy(
                    voice = VoiceUiState(
                        status = VoiceSessionStatus.PERMISSION_REQUIRED,
                        message = str(R.string.voice_permission_required),
                        canUseSystemVoiceInput = false
                    )
                )
            }
            onRequestAudioPermission?.invoke()
            return
        }

        val settings = prefsRepo.settings.value
        val subtype = prefsRepo.activeSubtype()
        val (primaryLocale, fallbackLocale) = AndroidVoiceDictationBackend.resolveVoiceLocaleTags(
            writingLocaleTag = subtype.localeTag,
            arabicDialect = settings.arabicVoiceDialect
        )

        val localSupported = voiceBackend.isLocalRecognitionAvailable(primaryLocale) ||
            (fallbackLocale != null && voiceBackend.isLocalRecognitionAvailable(fallbackLocale))
        if (!localSupported) {
            val systemAvailable = voiceBackend.isSystemVoiceInputAvailable() || onSystemVoiceFallback != null
            _uiState.update {
                it.copy(
                    voice = VoiceUiState(
                        status = VoiceSessionStatus.UNAVAILABLE,
                        activeLocaleTag = primaryLocale,
                        message = str(R.string.voice_local_unavailable, subtype.displayName),
                        canUseSystemVoiceInput = systemAvailable,
                        canOpenOfflineVoiceSettings = true
                    )
                )
            }
            return
        }

        val logicalId = ++logicalVoiceCounter
        activeLogicalVoiceSessionId = logicalId
        activeSessionFieldGeneration = fieldGeneration
        committedSegmentIds.clear()
        stopRequestedAfterSegment = false
        sessionDictatedText.clear()

        val continuous = settings.continuousVoiceDictation
        val preferOffline = settings.preferOfflineVoice

        startNextVoiceSegment(
            logicalId = logicalId,
            primaryLocale = primaryLocale,
            fallbackLocale = fallbackLocale,
            subtypeDisplayName = subtype.displayName,
            continuous = continuous,
            preferOffline = preferOffline
        )
        if (continuous) {
            resetVoiceSilenceTimer(logicalId)
        }
    }

    private fun startNextVoiceSegment(
        logicalId: Long,
        primaryLocale: String,
        fallbackLocale: String?,
        subtypeDisplayName: String,
        continuous: Boolean,
        preferOffline: Boolean
    ) {
        if (activeLogicalVoiceSessionId != logicalId || activeSessionFieldGeneration != fieldGeneration) return
        val segmentId = ++voiceSessionCounter
        activeVoiceSessionId = segmentId

        _uiState.update {
            it.copy(
                voice = VoiceUiState(
                    status = VoiceSessionStatus.LISTENING,
                    activeLocaleTag = primaryLocale,
                    partialText = "",
                    message = if (continuous) str(R.string.voice_listening_continuous) else str(R.string.voice_listening),
                    canUseSystemVoiceInput = false,
                    canOpenOfflineVoiceSettings = false,
                    sessionId = segmentId,
                    isContinuous = continuous,
                    isOfflinePreferred = preferOffline
                )
            )
        }

        val listener = object : VoiceRecognitionListener {
            override fun onReady(sessionId: Long) {
                if (!isSessionValid(sessionId) || activeLogicalVoiceSessionId != logicalId) return
                _uiState.update {
                    if (it.voice.sessionId == sessionId) {
                        it.copy(voice = it.voice.copy(status = VoiceSessionStatus.LISTENING))
                    } else it
                }
            }

            override fun onSpeechStarted(sessionId: Long) {
                if (!isSessionValid(sessionId) || activeLogicalVoiceSessionId != logicalId) return
                if (continuous) {
                    resetVoiceSilenceTimer(logicalId)
                }
            }

            override fun onPartial(sessionId: Long, text: String) {
                if (!isSessionValid(sessionId) || activeLogicalVoiceSessionId != logicalId) return
                if (continuous && text.isNotBlank()) {
                    resetVoiceSilenceTimer(logicalId)
                }
                _uiState.update {
                    if (it.voice.sessionId == sessionId) {
                        it.copy(voice = it.voice.copy(partialText = text))
                    } else it
                }
            }

            override fun onFinal(sessionId: Long, text: String) {
                if (!isSessionValid(sessionId) || activeLogicalVoiceSessionId != logicalId) return
                if (!committedSegmentIds.add(sessionId)) return
                stopFinalizeTimeoutJob?.cancel()
                stopFinalizeTimeoutJob = null

                val clean = text.trim()
                if (clean.isNotEmpty() && !target.isPasswordInput()) {
                    val before = target.getTextBeforeCursor(2)
                    val toCommit = if (before.isNotEmpty() && !before.last().isWhitespace()) {
                        " $clean"
                    } else {
                        clean
                    }
                    target.commitText(toCommit)
                    if (sessionDictatedText.isNotEmpty()) {
                        sessionDictatedText.append(' ')
                    }
                    sessionDictatedText.append(clean)
                    refreshLayoutAndSuggestions()
                }

                if (!continuous || stopRequestedAfterSegment) {
                    finishVoiceSessionCleanly(logicalId)
                } else {
                    resetVoiceSilenceTimer(logicalId)
                    startNextVoiceSegment(
                        logicalId = logicalId,
                        primaryLocale = primaryLocale,
                        fallbackLocale = fallbackLocale,
                        subtypeDisplayName = subtypeDisplayName,
                        continuous = true,
                        preferOffline = preferOffline
                    )
                }
            }

            override fun onError(sessionId: Long, type: VoiceErrorType, detail: String?) {
                if (!isSessionValid(sessionId) || activeLogicalVoiceSessionId != logicalId) return
                stopFinalizeTimeoutJob?.cancel()
                stopFinalizeTimeoutJob = null

                if (type == VoiceErrorType.NO_MATCH && continuous) {
                    if (stopRequestedAfterSegment) {
                        finishVoiceSessionCleanly(logicalId)
                    } else {
                        // Brief pause or stutter: resume listening seamlessly until 6s continuous silence expires
                        startNextVoiceSegment(
                            logicalId = logicalId,
                            primaryLocale = primaryLocale,
                            fallbackLocale = fallbackLocale,
                            subtypeDisplayName = subtypeDisplayName,
                            continuous = true,
                            preferOffline = preferOffline
                        )
                    }
                    return
                }

                finishVoiceSessionWithError(logicalId, sessionId, type, subtypeDisplayName, primaryLocale, preferOffline)
            }
        }

        voiceBackend.startListening(
            sessionId = segmentId,
            localeTag = primaryLocale,
            fallbackLocaleTag = fallbackLocale,
            listener = listener,
            preferOffline = preferOffline
        )
    }

    private fun resetVoiceSilenceTimer(logicalId: Long) {
        silenceWatchdogJob?.cancel()
        if (voiceAutoSilenceTimeoutMs <= 0L) return
        silenceWatchdogJob = controllerScope.launch {
            delay(voiceAutoSilenceTimeoutMs)
            if (activeLogicalVoiceSessionId == logicalId && activeSessionFieldGeneration == fieldGeneration) {
                val sid = activeVoiceSessionId
                if (sid > 0L) {
                    commitPendingPartialIfAny(sid)
                }
                activeLogicalVoiceSessionId = -1L
                activeVoiceSessionId = -1L
                if (sid > 0L) {
                    voiceBackend.cancel(sid)
                }
                _uiState.update { it.copy(voice = VoiceUiState(status = VoiceSessionStatus.IDLE)) }
            }
        }
    }

    private fun commitPendingPartialIfAny(sessionId: Long) {
        val voiceState = _uiState.value.voice
        if (voiceState.sessionId != sessionId) return
        val clean = voiceState.partialText.trim()
        if (clean.isNotEmpty() && !target.isPasswordInput() && committedSegmentIds.add(sessionId)) {
            val before = target.getTextBeforeCursor(2)
            val toCommit = if (before.isNotEmpty() && !before.last().isWhitespace()) {
                " $clean"
            } else {
                clean
            }
            target.commitText(toCommit)
            if (sessionDictatedText.isNotEmpty()) {
                sessionDictatedText.append(' ')
            }
            sessionDictatedText.append(clean)
            refreshLayoutAndSuggestions()
        }
    }

    private fun finishVoiceSessionCleanly(logicalId: Long) {
        if (activeLogicalVoiceSessionId != logicalId) return
        silenceWatchdogJob?.cancel()
        silenceWatchdogJob = null
        stopFinalizeTimeoutJob?.cancel()
        stopFinalizeTimeoutJob = null
        activeLogicalVoiceSessionId = -1L
        activeVoiceSessionId = -1L
        stopRequestedAfterSegment = false
        _uiState.update { it.copy(voice = VoiceUiState(status = VoiceSessionStatus.IDLE)) }
    }

    private fun finishVoiceSessionWithError(
        logicalId: Long,
        sessionId: Long,
        type: VoiceErrorType,
        subtypeDisplayName: String,
        primaryLocale: String,
        preferOffline: Boolean
    ) {
        if (activeLogicalVoiceSessionId != logicalId) return
        silenceWatchdogJob?.cancel()
        silenceWatchdogJob = null
        stopFinalizeTimeoutJob?.cancel()
        stopFinalizeTimeoutJob = null
        activeLogicalVoiceSessionId = -1L
        activeVoiceSessionId = -1L
        stopRequestedAfterSegment = false

        val canFallback = (type == VoiceErrorType.ENGINE_UNAVAILABLE ||
            type == VoiceErrorType.LANGUAGE_UNSUPPORTED) &&
            (voiceBackend.isSystemVoiceInputAvailable() || onSystemVoiceFallback != null)
        val canOpenOfflineSettings = type == VoiceErrorType.ENGINE_UNAVAILABLE ||
            type == VoiceErrorType.LANGUAGE_UNSUPPORTED
        val nextStatus = when (type) {
            VoiceErrorType.PERMISSION_DENIED -> VoiceSessionStatus.PERMISSION_REQUIRED
            VoiceErrorType.ENGINE_UNAVAILABLE,
            VoiceErrorType.LANGUAGE_UNSUPPORTED -> VoiceSessionStatus.UNAVAILABLE
            else -> VoiceSessionStatus.ERROR
        }
        val msg = when (type) {
            VoiceErrorType.PERMISSION_DENIED -> str(R.string.voice_permission_denied)
            VoiceErrorType.LANGUAGE_UNSUPPORTED -> {
                if (preferOffline) {
                    str(R.string.voice_offline_unavailable_hint, subtypeDisplayName)
                } else {
                    str(R.string.voice_local_unavailable, subtypeDisplayName)
                }
            }
            VoiceErrorType.ENGINE_UNAVAILABLE -> str(R.string.voice_local_unavailable, subtypeDisplayName)
            VoiceErrorType.NO_MATCH -> str(R.string.voice_error_no_match)
            VoiceErrorType.AUDIO -> str(R.string.voice_error_audio)
            VoiceErrorType.BUSY -> str(R.string.voice_error_busy)
        }
        _uiState.update {
            it.copy(
                voice = VoiceUiState(
                    status = nextStatus,
                    activeLocaleTag = primaryLocale,
                    message = msg,
                    canUseSystemVoiceInput = canFallback,
                    canOpenOfflineVoiceSettings = canOpenOfflineSettings,
                    sessionId = sessionId
                )
            )
        }
    }

    fun stopVoiceDictation() {
        val sid = activeVoiceSessionId
        val logicalId = activeLogicalVoiceSessionId
        if (sid > 0 && _uiState.value.voice.status == VoiceSessionStatus.LISTENING) {
            silenceWatchdogJob?.cancel()
            silenceWatchdogJob = null
            stopRequestedAfterSegment = true
            _uiState.update {
                it.copy(
                    voice = it.voice.copy(
                        status = VoiceSessionStatus.PROCESSING,
                        message = str(R.string.voice_processing)
                    )
                )
            }
            voiceBackend.stopListening(sid)
            stopFinalizeTimeoutJob?.cancel()
            stopFinalizeTimeoutJob = controllerScope.launch {
                delay(1800L)
                if (activeLogicalVoiceSessionId == logicalId && activeVoiceSessionId == sid) {
                    commitPendingPartialIfAny(sid)
                    voiceBackend.cancel(sid)
                    finishVoiceSessionCleanly(logicalId)
                }
            }
        }
    }

    fun openOfflineVoiceSettings() {
        voiceBackend.openOfflineVoiceSettings()
    }

    fun translateCurrentVoiceDictation() {
        val captured = sessionDictatedText.toString().trim()
            .ifEmpty { _uiState.value.voice.partialText.trim() }
        cancelVoiceDictationInternal(clearState = true)
        if (target.isPasswordInput()) {
            _uiState.update { it.copy(mode = KeyboardMode.AI_TRANSLATE, isPasswordInput = true) }
            return
        }
        val textToTranslate = captured.ifEmpty { target.getTextBeforeCursor(500).trim() }
        val activeLocale = prefsRepo.activeSubtype().localeTag
        val defaultTarget = when {
            textToTranslate.isNotEmpty() && containsArabicScript(textToTranslate) -> AiLanguageOption.EN
            textToTranslate.isNotEmpty() -> AiLanguageOption.AR
            activeLocale.startsWith("ar", ignoreCase = true) -> AiLanguageOption.EN
            else -> AiLanguageOption.AR
        }
        _uiState.update {
            it.copy(
                mode = KeyboardMode.AI_TRANSLATE,
                isPasswordInput = false,
                translate = TranslateUiState(
                    sourceLanguage = AiLanguageOption.AUTO,
                    targetLanguage = defaultTarget,
                    inputText = textToTranslate,
                    loadedFromSelection = false,
                    originalSelectedText = null,
                    originFieldGeneration = fieldGeneration,
                    autoTranslateEnabled = false,
                    isFieldChanged = false
                )
            )
        }
        refreshLayoutAndSuggestions()
        if (prefsRepo.settings.value.aiCloudNoticeAccepted && textToTranslate.isNotBlank()) {
            submitTranslation(isAuto = false)
        }
    }

    fun cancelVoiceDictation() {
        cancelVoiceDictationInternal(clearState = true)
    }

    fun dismissVoiceBanner() {
        cancelVoiceDictationInternal(clearState = true)
    }

    fun onVoicePermissionResult(granted: Boolean) {
        if (granted) {
            if (_uiState.value.voice.status == VoiceSessionStatus.PERMISSION_REQUIRED) {
                ensureVoiceDictationStarted()
            }
        } else {
            _uiState.update {
                it.copy(
                    voice = VoiceUiState(
                        status = VoiceSessionStatus.PERMISSION_REQUIRED,
                        message = str(R.string.voice_permission_denied),
                        canUseSystemVoiceInput = false
                    )
                )
            }
        }
    }

    fun useSystemVoiceInput() {
        cancelVoiceDictationInternal(clearState = false)
        val launched = (onSystemVoiceFallback?.invoke() == true) || voiceBackend.launchSystemVoiceInput()
        if (!launched) {
            _uiState.update {
                it.copy(
                    voice = VoiceUiState(
                        status = VoiceSessionStatus.ERROR,
                        message = str(R.string.voice_system_unavailable),
                        canUseSystemVoiceInput = false
                    )
                )
            }
        } else {
            _uiState.update { it.copy(voice = VoiceUiState(status = VoiceSessionStatus.IDLE)) }
        }
    }

    fun destroy() {
        cancelVoiceDictationInternal(clearState = true)
        cancelAiWriting()
        cancelTranslation()
        controllerScope.cancel()
        voiceBackend.destroy()
    }

    fun onKeyLongPressFeedback() {
        hapticFeedbackManager.performLongPress(enabled = prefsRepo.settings.value.vibrateOnKeypress)
    }

    // region AI Writing & Translate

    fun openAiSettingsScreen() {
        val opener = onOpenAiSettings ?: onOpenSettings
        opener?.invoke()
    }

    fun acceptAiCloudNotice() {
        prefsRepo.updateSettings { it.copy(aiCloudNoticeAccepted = true) }
    }

    private fun openOrToggleAiWriting() {
        if (_uiState.value.mode == KeyboardMode.AI_WRITING) {
            setMode(KeyboardMode.ALPHA)
            return
        }
        disableAutoTranslateOnClose()
        val password = target.isPasswordInput()
        val selected = if (password) null else target.getSelectedText()?.trim()?.takeIf { it.isNotEmpty() }
        val gen = fieldGeneration
        val prev = _uiState.value.aiWriting
        _uiState.update {
            it.copy(
                mode = KeyboardMode.AI_WRITING,
                isPasswordInput = password,
                aiWriting = AiWritingUiState(
                    operation = prev.operation,
                    tone = prev.tone,
                    targetLanguage = prev.targetLanguage,
                    inputText = selected.orEmpty(),
                    loadedFromSelection = selected != null,
                    originalSelectedText = selected,
                    originFieldGeneration = gen,
                    isFieldChanged = false
                )
            )
        }
        refreshLayoutAndSuggestions()
    }

    private fun openOrToggleTranslate() {
        if (_uiState.value.mode == KeyboardMode.AI_TRANSLATE) {
            setMode(KeyboardMode.ALPHA)
            return
        }
        val password = target.isPasswordInput()
        val selected = if (password) null else target.getSelectedText()?.trim()?.takeIf { it.isNotEmpty() }
        val gen = fieldGeneration
        val activeLocale = prefsRepo.activeSubtype().localeTag
        val defaultTarget = when {
            selected != null && containsArabicScript(selected) -> AiLanguageOption.EN
            selected != null -> AiLanguageOption.AR
            activeLocale.startsWith("ar", ignoreCase = true) -> AiLanguageOption.EN
            else -> AiLanguageOption.AR
        }
        _uiState.update {
            it.copy(
                mode = KeyboardMode.AI_TRANSLATE,
                isPasswordInput = password,
                translate = TranslateUiState(
                    sourceLanguage = AiLanguageOption.AUTO,
                    targetLanguage = defaultTarget,
                    inputText = selected.orEmpty(),
                    loadedFromSelection = selected != null,
                    originalSelectedText = selected,
                    originFieldGeneration = gen,
                    autoTranslateEnabled = false,
                    isFieldChanged = false
                )
            )
        }
        refreshLayoutAndSuggestions()
    }

    fun setAiWritingOperation(operation: AiWritingOperation) {
        _uiState.update {
            it.copy(
                aiWriting = it.aiWriting.copy(
                    operation = operation,
                    errorType = null,
                    errorMessage = null,
                    copiedNotice = false
                )
            )
        }
    }

    fun setAiWritingTone(tone: AiTone) {
        _uiState.update {
            it.copy(aiWriting = it.aiWriting.copy(tone = tone, copiedNotice = false))
        }
    }

    fun setAiWritingTargetLanguage(lang: AiLanguageOption) {
        _uiState.update {
            it.copy(aiWriting = it.aiWriting.copy(targetLanguage = lang, copiedNotice = false))
        }
    }

    fun onAiWritingInputChanged(text: String) {
        _uiState.update {
            val stillMatchesSelection = it.aiWriting.loadedFromSelection &&
                it.aiWriting.originalSelectedText == text.trim()
            it.copy(
                aiWriting = it.aiWriting.copy(
                    inputText = text,
                    loadedFromSelection = stillMatchesSelection,
                    errorType = null,
                    errorMessage = null,
                    copiedNotice = false
                )
            )
        }
    }

    fun pasteExplicitlyToAiWriting() {
        if (target.isPasswordInput()) return
        val clip = prefsRepo.readSystemClipboardTextExplicitly()?.trim().orEmpty()
        if (clip.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    aiWriting = it.aiWriting.copy(
                        inputText = clip,
                        loadedFromSelection = false,
                        originalSelectedText = null,
                        originFieldGeneration = fieldGeneration,
                        isFieldChanged = false,
                        errorType = null,
                        errorMessage = null,
                        copiedNotice = false
                    )
                )
            }
        }
    }

    fun clearAiWriting() {
        cancelAiWriting()
        _uiState.update {
            it.copy(
                aiWriting = it.aiWriting.copy(
                    inputText = "",
                    loadedFromSelection = false,
                    originalSelectedText = null,
                    errorType = null,
                    errorMessage = null,
                    result = null,
                    copiedNotice = false
                )
            )
        }
    }

    fun submitAiWriting() {
        if (target.isPasswordInput()) {
            _uiState.update { it.copy(isPasswordInput = true) }
            return
        }
        val settings = prefsRepo.settings.value
        if (!settings.aiCloudNoticeAccepted) return

        val state = _uiState.value.aiWriting
        val cleanInput = state.inputText.trim()
        if (cleanInput.isEmpty()) {
            _uiState.update {
                it.copy(
                    aiWriting = it.aiWriting.copy(
                        errorType = AiProxyErrorType.EMPTY_INPUT,
                        errorMessage = str(R.string.ai_error_empty_input)
                    )
                )
            }
            return
        }

        val config = AiConfigResolver.resolveConfig(
            settingsProxyUrl = settings.aiProxyUrl,
            settingsAuthToken = settings.aiProxyAuthToken,
            buildProxyUrl = defaultProxyUrlProvider(),
            buildProxyToken = defaultProxyTokenProvider(),
            buildGeminiApiKey = defaultGeminiApiKeyProvider()
        )
        if (config == null) {
            _uiState.update {
                it.copy(
                    aiWriting = it.aiWriting.copy(
                        errorType = AiProxyErrorType.UNCONFIGURED,
                        errorMessage = str(R.string.ai_error_unconfigured)
                    )
                )
            }
            return
        }

        writingJob?.cancel()
        val reqId = ++writingRequestSeq
        val originGen = fieldGeneration
        val request = AiWritingRequest(
            operation = state.operation,
            tone = state.tone,
            targetLanguage = state.targetLanguage,
            text = cleanInput
        )

        _uiState.update {
            it.copy(
                aiWriting = it.aiWriting.copy(
                    isLoading = true,
                    errorType = null,
                    errorMessage = null,
                    copiedNotice = false,
                    originFieldGeneration = originGen,
                    isFieldChanged = false
                )
            )
        }

        writingJob = controllerScope.launch {
            try {
                val res = aiProxyClient.processWriting(config, request)
                if (reqId != writingRequestSeq) return@launch
                val changedSinceStart = originGen != fieldGeneration
                _uiState.update {
                    it.copy(
                        aiWriting = it.aiWriting.copy(
                            isLoading = false,
                            result = res,
                            errorType = null,
                            errorMessage = null,
                            isFieldChanged = changedSinceStart
                        )
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: AiProxyException) {
                if (reqId != writingRequestSeq) return@launch
                _uiState.update {
                    it.copy(
                        aiWriting = it.aiWriting.copy(
                            isLoading = false,
                            errorType = e.type,
                            errorMessage = mapAiErrorMessage(e)
                        )
                    )
                }
            } catch (t: Throwable) {
                if (reqId != writingRequestSeq) return@launch
                _uiState.update {
                    it.copy(
                        aiWriting = it.aiWriting.copy(
                            isLoading = false,
                            errorType = AiProxyErrorType.SERVER_ERROR,
                            errorMessage = str(R.string.ai_error_server, t.javaClass.simpleName)
                        )
                    )
                }
            }
        }
    }

    fun cancelAiWriting() {
        writingRequestSeq++
        writingJob?.cancel()
        writingJob = null
        _uiState.update {
            it.copy(aiWriting = it.aiWriting.copy(isLoading = false))
        }
    }

    fun insertAiWritingText() {
        val text = _uiState.value.aiWriting.result?.text?.takeIf { it.isNotBlank() } ?: return
        insertTextSafelyFromAi(text, isWritingPanel = true)
    }

    fun insertAiEmailSubject() {
        val subject = _uiState.value.aiWriting.result?.subject?.takeIf { it.isNotBlank() } ?: return
        insertTextSafelyFromAi(subject, isWritingPanel = true)
    }

    fun insertAiEmailBody() {
        val body = _uiState.value.aiWriting.result?.body?.takeIf { it.isNotBlank() } ?: return
        insertTextSafelyFromAi(body, isWritingPanel = true)
    }

    fun replaceSelectionWithAiWriting() {
        val st = _uiState.value.aiWriting
        val replacement = st.result?.text?.takeIf { it.isNotBlank() } ?: return
        replaceSelectionSafelyFromAi(
            original = st.originalSelectedText,
            replacement = replacement,
            originGen = st.originFieldGeneration,
            isWritingPanel = true
        )
    }

    fun copyAiWritingText(customText: String? = null) {
        val text = customText ?: _uiState.value.aiWriting.result?.text
        if (text.isNullOrBlank()) return
        if (!prefsRepo.settings.value.incognitoMode && !target.isSensitiveInput()) {
            prefsRepo.copyToSystemClipboard(text)
            prefsRepo.addClipboardEntry(text, isSensitiveField = target.isSensitiveInput())
        } else {
            prefsRepo.copyToSystemClipboard(text)
        }
        _uiState.update { it.copy(aiWriting = it.aiWriting.copy(copiedNotice = true)) }
    }

    fun setTranslateSourceLanguage(lang: AiLanguageOption) {
        _uiState.update { current ->
            val t = current.translate
            val adjustedTarget = if (lang != AiLanguageOption.AUTO && lang == t.targetLanguage) {
                if (lang == AiLanguageOption.AR) AiLanguageOption.EN else AiLanguageOption.AR
            } else t.targetLanguage
            current.copy(
                translate = t.copy(
                    sourceLanguage = lang,
                    targetLanguage = adjustedTarget,
                    errorType = null,
                    errorMessage = null,
                    copiedNotice = false
                )
            )
        }
        triggerAutoTranslateIfActive()
    }

    fun setTranslateTargetLanguage(lang: AiLanguageOption) {
        if (lang == AiLanguageOption.AUTO) return
        _uiState.update { current ->
            val t = current.translate
            val adjustedSource = if (t.sourceLanguage == lang) {
                if (lang == AiLanguageOption.AR) AiLanguageOption.EN else AiLanguageOption.AR
            } else t.sourceLanguage
            current.copy(
                translate = t.copy(
                    sourceLanguage = adjustedSource,
                    targetLanguage = lang,
                    errorType = null,
                    errorMessage = null,
                    copiedNotice = false
                )
            )
        }
        triggerAutoTranslateIfActive()
    }

    fun swapTranslateLanguages() {
        val t = _uiState.value.translate
        if (!t.canSwapLanguages) return
        val newSource = t.targetLanguage
        val newTarget = t.sourceLanguage
        _uiState.update {
            it.copy(
                translate = it.translate.copy(
                    sourceLanguage = newSource,
                    targetLanguage = newTarget,
                    errorType = null,
                    errorMessage = null,
                    copiedNotice = false
                )
            )
        }
        triggerAutoTranslateIfActive()
    }

    fun onTranslateInputChanged(text: String) {
        autoTranslateDebounceJob?.cancel()
        val prev = _uiState.value.translate
        val stillMatchesSelection = prev.loadedFromSelection && prev.originalSelectedText == text.trim()
        _uiState.update {
            it.copy(
                translate = it.translate.copy(
                    inputText = text,
                    loadedFromSelection = stillMatchesSelection,
                    errorType = null,
                    errorMessage = null,
                    copiedNotice = false
                )
            )
        }
        if (text.trim().isEmpty()) {
            translateRequestSeq++
            translateJob?.cancel()
            _uiState.update { it.copy(translate = it.translate.copy(isLoading = false, result = null)) }
            return
        }
        triggerAutoTranslateIfActive()
    }

    fun pasteExplicitlyToTranslate() {
        if (target.isPasswordInput()) return
        val clip = prefsRepo.readSystemClipboardTextExplicitly()?.trim().orEmpty()
        if (clip.isNotEmpty()) {
            _uiState.update {
                it.copy(
                    translate = it.translate.copy(
                        inputText = clip,
                        loadedFromSelection = false,
                        originalSelectedText = null,
                        originFieldGeneration = fieldGeneration,
                        isFieldChanged = false,
                        errorType = null,
                        errorMessage = null,
                        copiedNotice = false
                    )
                )
            }
            triggerAutoTranslateIfActive()
        }
    }

    fun clearTranslateInput() {
        cancelTranslation()
        _uiState.update {
            it.copy(
                translate = it.translate.copy(
                    inputText = "",
                    loadedFromSelection = false,
                    originalSelectedText = null,
                    errorType = null,
                    errorMessage = null,
                    result = null,
                    copiedNotice = false
                )
            )
        }
    }

    fun setAutoTranslateEnabled(enabled: Boolean) {
        if (!enabled) {
            disableAutoTranslateOnClose()
            return
        }
        if (target.isPasswordInput() || !prefsRepo.settings.value.aiCloudNoticeAccepted) return
        _uiState.update {
            it.copy(translate = it.translate.copy(autoTranslateEnabled = true, copiedNotice = false))
        }
        if (_uiState.value.translate.inputText.trim().isNotEmpty()) {
            triggerAutoTranslateIfActive(immediate = true)
        }
    }

    fun submitTranslation(isAuto: Boolean = false) {
        if (target.isPasswordInput()) {
            _uiState.update { it.copy(isPasswordInput = true) }
            return
        }
        val settings = prefsRepo.settings.value
        if (!settings.aiCloudNoticeAccepted) return

        val state = _uiState.value.translate
        val cleanInput = state.inputText.trim()
        if (cleanInput.isEmpty()) {
            if (!isAuto) {
                _uiState.update {
                    it.copy(
                        translate = it.translate.copy(
                            errorType = AiProxyErrorType.EMPTY_INPUT,
                            errorMessage = str(R.string.ai_error_empty_input)
                        )
                    )
                }
            }
            return
        }

        val config = AiConfigResolver.resolveConfig(
            settingsProxyUrl = settings.aiProxyUrl,
            settingsAuthToken = settings.aiProxyAuthToken,
            buildProxyUrl = defaultProxyUrlProvider(),
            buildProxyToken = defaultProxyTokenProvider(),
            buildGeminiApiKey = defaultGeminiApiKeyProvider()
        )
        if (config == null) {
            _uiState.update {
                it.copy(
                    translate = it.translate.copy(
                        errorType = AiProxyErrorType.UNCONFIGURED,
                        errorMessage = str(R.string.ai_error_unconfigured)
                    )
                )
            }
            return
        }

        translateJob?.cancel()
        val reqId = ++translateRequestSeq
        val originGen = fieldGeneration
        val request = AiTranslateRequest(
            sourceLanguage = state.sourceLanguage,
            targetLanguage = state.targetLanguage,
            text = cleanInput
        )

        _uiState.update {
            it.copy(
                translate = it.translate.copy(
                    isLoading = true,
                    errorType = null,
                    errorMessage = null,
                    copiedNotice = false,
                    originFieldGeneration = originGen,
                    isFieldChanged = false
                )
            )
        }

        translateJob = controllerScope.launch {
            try {
                val res = aiProxyClient.translate(config, request)
                if (reqId != translateRequestSeq) return@launch
                val changedSinceStart = originGen != fieldGeneration
                _uiState.update {
                    it.copy(
                        translate = it.translate.copy(
                            isLoading = false,
                            result = res,
                            errorType = null,
                            errorMessage = null,
                            isFieldChanged = changedSinceStart
                        )
                    )
                }
            } catch (ce: CancellationException) {
                throw ce
            } catch (e: AiProxyException) {
                if (reqId != translateRequestSeq) return@launch
                _uiState.update {
                    it.copy(
                        translate = it.translate.copy(
                            isLoading = false,
                            errorType = e.type,
                            errorMessage = mapAiErrorMessage(e)
                        )
                    )
                }
            } catch (t: Throwable) {
                if (reqId != translateRequestSeq) return@launch
                _uiState.update {
                    it.copy(
                        translate = it.translate.copy(
                            isLoading = false,
                            errorType = AiProxyErrorType.SERVER_ERROR,
                            errorMessage = str(R.string.ai_error_server, t.javaClass.simpleName)
                        )
                    )
                }
            }
        }
    }

    fun cancelTranslation() {
        autoTranslateDebounceJob?.cancel()
        autoTranslateDebounceJob = null
        translateRequestSeq++
        translateJob?.cancel()
        translateJob = null
        _uiState.update {
            it.copy(translate = it.translate.copy(isLoading = false))
        }
    }

    fun insertTranslatedText() {
        val text = _uiState.value.translate.result?.translatedText?.takeIf { it.isNotBlank() } ?: return
        insertTextSafelyFromAi(text, isWritingPanel = false)
    }

    fun replaceSelectionWithTranslation() {
        val st = _uiState.value.translate
        val replacement = st.result?.translatedText?.takeIf { it.isNotBlank() } ?: return
        replaceSelectionSafelyFromAi(
            original = st.originalSelectedText,
            replacement = replacement,
            originGen = st.originFieldGeneration,
            isWritingPanel = false
        )
    }

    fun copyTranslatedText() {
        val text = _uiState.value.translate.result?.translatedText
        if (text.isNullOrBlank()) return
        if (!prefsRepo.settings.value.incognitoMode && !target.isSensitiveInput()) {
            prefsRepo.copyToSystemClipboard(text)
            prefsRepo.addClipboardEntry(text, isSensitiveField = target.isSensitiveInput())
        } else {
            prefsRepo.copyToSystemClipboard(text)
        }
        _uiState.update { it.copy(translate = it.translate.copy(copiedNotice = true)) }
    }

    private fun triggerAutoTranslateIfActive(immediate: Boolean = false) {
        autoTranslateDebounceJob?.cancel()
        val st = _uiState.value.translate
        if (!st.autoTranslateEnabled || st.isFieldChanged || target.isPasswordInput()) return
        if (st.inputText.trim().isEmpty()) return

        autoTranslateDebounceJob = controllerScope.launch {
            if (!immediate && autoTranslateDebounceMs > 0L) {
                delay(autoTranslateDebounceMs)
            }
            val latest = _uiState.value.translate
            if (latest.autoTranslateEnabled && !latest.isFieldChanged && latest.inputText.trim().isNotEmpty()) {
                submitTranslation(isAuto = true)
            }
        }
    }

    private fun disableAutoTranslateOnClose() {
        autoTranslateDebounceJob?.cancel()
        autoTranslateDebounceJob = null
        if (_uiState.value.translate.autoTranslateEnabled) {
            translateRequestSeq++
            translateJob?.cancel()
            translateJob = null
            _uiState.update {
                it.copy(translate = it.translate.copy(autoTranslateEnabled = false, isLoading = false))
            }
        }
    }

    private fun stopAutoTranslateAndMarkFieldChanged() {
        autoTranslateDebounceJob?.cancel()
        autoTranslateDebounceJob = null
        _uiState.update { current ->
            val cancelAuto = current.translate.autoTranslateEnabled
            if (cancelAuto) {
                translateRequestSeq++
                translateJob?.cancel()
                translateJob = null
            }
            val writingTouched = current.aiWriting.result != null ||
                current.aiWriting.isLoading ||
                current.aiWriting.loadedFromSelection
            val translateTouched = current.translate.result != null ||
                current.translate.isLoading ||
                current.translate.loadedFromSelection ||
                current.translate.autoTranslateEnabled
            current.copy(
                aiWriting = if (writingTouched) {
                    current.aiWriting.copy(isFieldChanged = true)
                } else current.aiWriting,
                translate = if (translateTouched) {
                    current.translate.copy(
                        autoTranslateEnabled = false,
                        isLoading = if (cancelAuto) false else current.translate.isLoading,
                        isFieldChanged = true
                    )
                } else current.translate
            )
        }
    }

    private fun insertTextSafelyFromAi(text: String, isWritingPanel: Boolean) {
        val originGen = if (isWritingPanel) {
            _uiState.value.aiWriting.originFieldGeneration
        } else {
            _uiState.value.translate.originFieldGeneration
        }
        if (originGen != fieldGeneration || target.isPasswordInput()) {
            markFieldChangedOnPanel(isWritingPanel)
            return
        }
        triggerFeedback(KeyActionType.CHARACTER)
        target.commitText(text)
        refreshLayoutAndSuggestions()
    }

    private fun replaceSelectionSafelyFromAi(
        original: String?,
        replacement: String,
        originGen: Long,
        isWritingPanel: Boolean
    ) {
        if (originGen != fieldGeneration || target.isPasswordInput() || original.isNullOrEmpty()) {
            markFieldChangedOnPanel(isWritingPanel)
            return
        }
        val replaced = target.replaceSelection(original, replacement)
        if (!replaced) {
            // Selection no longer intact — commit once cleanly or update state
            target.commitText(replacement)
        }
        triggerFeedback(KeyActionType.CHARACTER)
        if (isWritingPanel) {
            _uiState.update {
                it.copy(aiWriting = it.aiWriting.copy(loadedFromSelection = false, originalSelectedText = null))
            }
        } else {
            _uiState.update {
                it.copy(translate = it.translate.copy(loadedFromSelection = false, originalSelectedText = null))
            }
        }
        refreshLayoutAndSuggestions()
    }

    private fun markFieldChangedOnPanel(isWritingPanel: Boolean) {
        if (isWritingPanel) {
            _uiState.update { it.copy(aiWriting = it.aiWriting.copy(isFieldChanged = true)) }
        } else {
            _uiState.update { it.copy(translate = it.translate.copy(isFieldChanged = true, autoTranslateEnabled = false)) }
        }
    }

    private fun mapAiErrorMessage(ex: AiProxyException): String = when (ex.type) {
        AiProxyErrorType.EMPTY_INPUT -> str(R.string.ai_error_empty_input)
        AiProxyErrorType.UNCONFIGURED -> str(R.string.ai_error_unconfigured)
        AiProxyErrorType.INVALID_URL -> str(R.string.ai_error_invalid_url)
        AiProxyErrorType.OFFLINE -> str(R.string.ai_error_offline)
        AiProxyErrorType.UNAUTHORIZED -> str(R.string.ai_error_unauthorized)
        AiProxyErrorType.RATE_LIMITED -> str(R.string.ai_error_rate_limited)
        AiProxyErrorType.TIMEOUT -> str(R.string.ai_error_timeout)
        AiProxyErrorType.NETWORK -> str(R.string.ai_error_network)
        AiProxyErrorType.SERVER_ERROR -> str(R.string.ai_error_server, ex.statusCode?.toString() ?: "500")
    }

    // endregion

    private fun isSessionValid(sessionId: Long): Boolean {
        return sessionId > 0L &&
            sessionId == activeVoiceSessionId &&
            activeSessionFieldGeneration == fieldGeneration
    }

    private fun cancelVoiceDictationInternal(clearState: Boolean) {
        silenceWatchdogJob?.cancel()
        silenceWatchdogJob = null
        stopFinalizeTimeoutJob?.cancel()
        stopFinalizeTimeoutJob = null
        val sid = activeVoiceSessionId
        activeLogicalVoiceSessionId = -1L
        activeVoiceSessionId = -1L
        activeSessionFieldGeneration = -1L
        stopRequestedAfterSegment = false
        if (sid > 0L) {
            voiceBackend.cancel(sid)
        }
        if (clearState) {
            _uiState.update { it.copy(voice = VoiceUiState(status = VoiceSessionStatus.IDLE)) }
        }
    }

    fun toggleToolbarExpanded() {
        _uiState.update { it.copy(toolbarExpanded = !it.toolbarExpanded) }
    }

    fun toggleToolbarCustomizePanel() {
        toggleMode(KeyboardMode.TOOLBAR_CUSTOMIZE)
    }

    fun toggleToolbarKey(key: ToolbarKey) {
        prefsRepo.toggleToolbarKey(key)
    }

    fun moveToolbarKey(fromIndex: Int, toIndex: Int) {
        prefsRepo.moveToolbarKey(fromIndex, toIndex)
    }

    fun moveToolbarKeyByName(keyName: String, delta: Int) {
        prefsRepo.moveToolbarKey(keyName, delta)
    }

    fun resetToolbarKeys() {
        prefsRepo.resetToolbarKeys()
    }

    fun setMode(mode: KeyboardMode) {
        if (_uiState.value.mode == KeyboardMode.AI_TRANSLATE && mode != KeyboardMode.AI_TRANSLATE) {
            disableAutoTranslateOnClose()
        }
        _uiState.update { it.copy(mode = mode, clipboardFeedbackMessage = null) }
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
            "COPY" -> target.copySelection()?.let {
                prefsRepo.addClipboardEntry(it, isSensitiveField = target.isSensitiveInput())
            }
            "CUT" -> target.cutSelection()?.let {
                prefsRepo.addClipboardEntry(it, isSensitiveField = target.isSensitiveInput())
            }
            "PASTE" -> prefsRepo.clipboard.value.firstOrNull()?.let { onPasteClipboardEntry(it) }
        }
        refreshLayoutAndSuggestions()
    }

    private fun toggleMode(targetMode: KeyboardMode) {
        val next = if (_uiState.value.mode == targetMode) KeyboardMode.ALPHA else targetMode
        setMode(next)
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
            if (prefsRepo.settings.value.autoCap && isCasedLocale(prefsRepo.activeSubtype().localeTag)) {
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
        if (before.last().isWhitespace() || before.last() in listOf('.', ',', '!', '?', ':', ';', '،', '؟', '؛')) {
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

    private fun isCasedLocale(localeTag: String): Boolean {
        val lang = localeTag.substringBefore("-").lowercase()
        return lang !in setOf("ar", "fa", "he", "ur", "yi", "th", "hi", "bn", "ko")
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
        hapticFeedbackManager.performKeyPress(
            actionType = actionType,
            enabled = settings.vibrateOnKeypress
        )
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
