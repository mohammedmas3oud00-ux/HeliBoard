package com.example.heliboard.ime

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.heliboard.HeliBoardApp
import com.example.heliboard.MainActivity
import com.example.heliboard.theme.HeliBoardTheme
import com.example.heliboard.util.LocaleHelper
import com.example.heliboard.voice.AndroidVoiceDictationBackend
import kotlinx.coroutines.launch

class HeliBoardIMEService :
    InputMethodService(),
    LifecycleOwner,
    ViewModelStoreOwner,
    SavedStateRegistryOwner {

    private val lifecycleRegistry = LifecycleRegistry(this)
    private val store = ViewModelStore()
    private val savedStateController = SavedStateRegistryController.create(this)

    override val lifecycle: Lifecycle get() = lifecycleRegistry
    override val viewModelStore: ViewModelStore get() = store
    override val savedStateRegistry: SavedStateRegistry get() = savedStateController.savedStateRegistry

    private lateinit var controller: HeliKeyboardController

    override fun onCreate() {
        super.onCreate()
        savedStateController.performRestore(null)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_CREATE)

        val app = application as HeliBoardApp
        controller = HeliKeyboardController(
            context = this,
            prefsRepo = app.preferencesRepository,
            assetRepo = app.assetRepository,
            suggestionEngine = app.suggestionEngine,
            target = ImeInputTarget(
                connectionProvider = { currentInputConnection },
                editorInfoProvider = { currentInputEditorInfo }
            ),
            onOpenSettings = {
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
                startActivity(intent)
            },
            onOpenAiSettings = {
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(MainActivity.EXTRA_OPEN_AI_SETTINGS, true)
                }
                startActivity(intent)
            },
            voiceBackend = AndroidVoiceDictationBackend(this),
            onRequestAudioPermission = {
                val intent = Intent(this, MainActivity::class.java).apply {
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    putExtra(MainActivity.EXTRA_REQUEST_AUDIO_PERMISSION, true)
                }
                startActivity(intent)
            },
            onSystemVoiceFallback = {
                runCatching {
                    val imm = getSystemService(INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
                    val hasVoiceIme = imm.enabledInputMethodList.any { info ->
                        info.packageName != packageName &&
                            (0 until info.subtypeCount).any { idx ->
                                info.getSubtypeAt(idx).mode.equals("voice", ignoreCase = true)
                            }
                    }
                    if (hasVoiceIme) {
                        imm.showInputMethodPicker()
                        true
                    } else {
                        false
                    }
                }.getOrDefault(false)
            }
        )
        lifecycleScope.launch {
            app.voicePermissionEvents.collect { granted ->
                if (granted) {
                    app.consumePendingVoiceAutoStart()
                }
                controller.onVoicePermissionResult(granted)
            }
        }
    }

    override fun onCreateInputView(): View {
        val decorView = window?.window?.decorView
        decorView?.let {
            it.setViewTreeLifecycleOwner(this)
            it.setViewTreeViewModelStoreOwner(this)
            it.setViewTreeSavedStateRegistryOwner(this)
        }

        val app = application as HeliBoardApp
        return ComposeView(this).apply {
            setViewTreeLifecycleOwner(this@HeliBoardIMEService)
            setViewTreeViewModelStoreOwner(this@HeliBoardIMEService)
            setViewTreeSavedStateRegistryOwner(this@HeliBoardIMEService)
            setContent {
                val settings by app.preferencesRepository.settings.collectAsState()
                val customThemes by app.preferencesRepository.customThemes.collectAsState()
                val clipboard by app.preferencesRepository.clipboard.collectAsState()
                val uiState by controller.uiState.collectAsState()
                val systemDark = isSystemInDarkTheme()
                val baseContext = LocalContext.current
                val (localizedCtx, localizedConfig) = remember(settings.appLanguage, baseContext) {
                    LocaleHelper.createLocalizedContext(baseContext, settings.appLanguage)
                }
                val layoutDir = remember(settings.appLanguage) {
                    LocaleHelper.resolveLayoutDirection(settings.appLanguage)
                }

                CompositionLocalProvider(
                    LocalContext provides localizedCtx,
                    LocalConfiguration provides localizedConfig,
                    LocalLayoutDirection provides layoutDir
                ) {
                    HeliBoardTheme(darkTheme = systemDark) {
                        HeliKeyboardView(
                            controller = controller,
                            uiState = uiState,
                            settings = settings,
                            customThemes = customThemes,
                            clipboardEntries = clipboard,
                            emojiCategories = app.assetRepository.loadEmojiCategories(),
                            onDeleteClipboard = { app.preferencesRepository.deleteClipboardEntry(it) },
                            onPinClipboard = { app.preferencesRepository.togglePinClipboard(it) },
                            onClearUnpinnedClipboard = { app.preferencesRepository.clearUnpinnedClipboard() },
                            applyNavigationBarPadding = true
                        )
                    }
                }
            }
        }
    }

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        val sensitive = ImeInputTarget.isSensitiveEditorInfo(editorInfo)
        val app = application as HeliBoardApp
        val prefs = app.preferencesRepository
        prefs.pruneExpiredClipboard()
        prefs.captureSystemClipboard(isSensitiveField = sensitive)
        controller.onInputFieldChanged(restarting = restarting)
        if (app.consumePendingVoiceAutoStart()) {
            controller.ensureVoiceDictationStarted()
        }
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        controller.onKeyboardHidden()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        controller.destroy()
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        super.onDestroy()
    }
}
