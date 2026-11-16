package com.example.heliboard.ime

import android.content.Intent
import android.inputmethodservice.InputMethodService
import android.view.View
import android.view.inputmethod.EditorInfo
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.platform.ComposeView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.lifecycle.setViewTreeLifecycleOwner
import androidx.lifecycle.setViewTreeViewModelStoreOwner
import androidx.savedstate.SavedStateRegistry
import androidx.savedstate.SavedStateRegistryController
import androidx.savedstate.SavedStateRegistryOwner
import androidx.savedstate.setViewTreeSavedStateRegistryOwner
import com.example.heliboard.HeliBoardApp
import com.example.heliboard.MainActivity
import com.example.heliboard.theme.HeliBoardTheme

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
            }
        )
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

    override fun onStartInputView(editorInfo: EditorInfo?, restarting: Boolean) {
        super.onStartInputView(editorInfo, restarting)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_RESUME)
        (application as HeliBoardApp).preferencesRepository.captureSystemClipboard()
        controller.refreshLayoutAndSuggestions()
    }

    override fun onFinishInputView(finishingInput: Boolean) {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_PAUSE)
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        super.onFinishInputView(finishingInput)
    }

    override fun onDestroy() {
        lifecycleRegistry.handleLifecycleEvent(Lifecycle.Event.ON_DESTROY)
        store.clear()
        super.onDestroy()
    }
}
