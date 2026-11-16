package com.example.heliboard

import android.content.Context
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.down
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.up
import androidx.test.core.app.ApplicationProvider
import com.example.heliboard.data.AssetRepository
import com.example.heliboard.data.KeyActionType
import com.example.heliboard.data.KeySpec
import com.example.heliboard.data.KeyboardMode
import com.example.heliboard.data.PreferencesRepository
import com.example.heliboard.data.SuggestionEngine
import com.example.heliboard.data.ToolbarKey
import com.example.heliboard.data.ToolbarMode
import com.example.heliboard.data.room.ClipboardRoomRepository
import com.example.heliboard.data.room.HeliBoardDatabase
import com.example.heliboard.ime.HeliKeyboardController
import com.example.heliboard.ime.HeliKeyboardView
import com.example.heliboard.ime.SandboxInputTarget
import com.example.heliboard.theme.HeliBoardTheme
import com.example.heliboard.ui.HeliBoardViewModel
import com.example.heliboard.ui.screens.PreferencesScreen
import com.example.heliboard.voice.VoiceDictationBackend
import com.example.heliboard.voice.VoiceErrorType
import com.example.heliboard.voice.VoiceRecognitionListener
import com.example.heliboard.voice.VoiceSessionStatus
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.shadows.ShadowLooper
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class NewFeaturesVerificationTest {

    @get:Rule
    val composeRule = createComposeRule()

    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.getSharedPreferences("heliboard_prefs", Context.MODE_PRIVATE)
            .edit()
            .clear()
            .commit()
        runBlocking(Dispatchers.IO) {
            HeliBoardDatabase.getInstance(context).clearAllTables()
        }
    }

    private class ContinuousFakeVoiceBackend : VoiceDictationBackend {
        var permissionGranted = true
        var localAvailable = true
        var onDeviceOfflineAvailable = true
        val startedSessions = mutableListOf<Long>()
        val startedPreferOfflineFlags = mutableListOf<Boolean>()
        var lastListener: VoiceRecognitionListener? = null
        var stoppedSessionId: Long = -1L
        var cancelledSessionId: Long = -1L

        override fun hasAudioPermission(): Boolean = permissionGranted
        override fun isLocalRecognitionAvailable(localeTag: String): Boolean = localAvailable
        override fun isOnDeviceOfflineAvailable(): Boolean = onDeviceOfflineAvailable
        override fun isSystemVoiceInputAvailable(): Boolean = true

        override fun startListening(
            sessionId: Long,
            localeTag: String,
            fallbackLocaleTag: String?,
            listener: VoiceRecognitionListener,
            preferOffline: Boolean
        ) {
            startedSessions += sessionId
            startedPreferOfflineFlags += preferOffline
            lastListener = listener
        }

        override fun stopListening(sessionId: Long) {
            stoppedSessionId = sessionId
        }

        override fun cancel(sessionId: Long) {
            cancelledSessionId = sessionId
        }

        override fun launchSystemVoiceInput(): Boolean = true
        override fun destroy() = Unit
    }

    @Test
    fun roomClipboardPersistence_savesAndSyncsSnippetsReactsViaFlowAndRendersInPanel() {
        val db = HeliBoardDatabase.getInstance(context)
        val roomRepo = ClipboardRoomRepository(db)
        val prefsRepo = PreferencesRepository(context, roomRepo = roomRepo)

        prefsRepo.addClipboardEntry("Room persistent Arabic نص تجريبي")
        prefsRepo.addClipboardEntry("Room snippet two")

        val addedId = prefsRepo.clipboard.value.first { it.text == "Room persistent Arabic نص تجريبي" }.id
        prefsRepo.togglePinClipboard(addedId)

        // Wait up to 3s for background Room sync coroutine to persist to SQLite and emit via Flow
        val persisted = runBlocking {
            withTimeout(3000L) {
                roomRepo.snippets.first { list ->
                    list.any { it.text == "Room persistent Arabic نص تجريبي" && it.isPinned } &&
                        list.any { it.text == "Room snippet two" && !it.isPinned }
                }
            }
        }
        assertTrue(persisted.first { it.text == "Room persistent Arabic نص تجريبي" }.isPinned)

        // Verify ClipboardPanel displays the persisted snippet
        val assetRepo = AssetRepository(context)
        val sandbox = SandboxInputTarget()
        val controller = HeliKeyboardController(
            context = context,
            prefsRepo = prefsRepo,
            assetRepo = assetRepo,
            suggestionEngine = SuggestionEngine(),
            target = sandbox
        )
        controller.setMode(KeyboardMode.CLIPBOARD)

        composeRule.setContent {
            val settings by prefsRepo.settings.collectAsState()
            val uiState by controller.uiState.collectAsState()
            val clips by prefsRepo.clipboard.collectAsState()
            HeliBoardTheme(darkTheme = false) {
                HeliKeyboardView(
                    controller = controller,
                    uiState = uiState,
                    settings = settings,
                    customThemes = emptyList(),
                    clipboardEntries = clips,
                    emojiCategories = emptyList(),
                    onDeleteClipboard = { prefsRepo.deleteClipboardEntry(it) },
                    onPinClipboard = { prefsRepo.togglePinClipboard(it) },
                    onClearUnpinnedClipboard = { prefsRepo.clearUnpinnedClipboard() }
                )
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Room persistent Arabic نص تجريبي").assertIsDisplayed()
        controller.destroy()
    }

    @Test
    fun offlineContinuousVoice_survivesPausesAndStutters_stopsAfterSilenceOrTranslatesDirectly() {
        val prefsRepo = PreferencesRepository(context)
        prefsRepo.updateSettings {
            it.copy(
                selectedSubtypeLocale = "ar",
                appLanguage = "en",
                preferOfflineVoice = true,
                continuousVoiceDictation = true
            )
        }
        val backend = ContinuousFakeVoiceBackend()
        val sandbox = SandboxInputTarget()
        val controller = HeliKeyboardController(
            context = context,
            prefsRepo = prefsRepo,
            assetRepo = AssetRepository(context),
            suggestionEngine = SuggestionEngine(),
            target = sandbox,
            voiceBackend = backend,
            voiceAutoSilenceTimeoutMs = 250L
        )

        composeRule.setContent {
            val settings by prefsRepo.settings.collectAsState()
            val uiState by controller.uiState.collectAsState()
            HeliBoardTheme(darkTheme = false) {
                HeliKeyboardView(
                    controller = controller,
                    uiState = uiState,
                    settings = settings,
                    customThemes = emptyList(),
                    clipboardEntries = emptyList(),
                    emojiCategories = emptyList(),
                    onDeleteClipboard = {},
                    onPinClipboard = {},
                    onClearUnpinnedClipboard = {}
                )
            }
        }

        // Start continuous offline voice dictation
        composeRule.onNodeWithTag("toolbar_key_voice").performClick()
        composeRule.waitForIdle()
        assertEquals(VoiceSessionStatus.LISTENING, controller.uiState.value.voice.status)
        assertTrue(controller.uiState.value.voice.isContinuous)
        assertTrue(controller.uiState.value.voice.isOfflinePreferred)
        assertEquals(listOf(true), backend.startedPreferOfflineFlags)

        // Segment 1 finishes -> inserts text and seamlessly restarts listening for segment 2
        val sid1 = backend.startedSessions.last()
        backend.lastListener?.onFinal(sid1, "أهلا بكم")
        composeRule.waitForIdle()
        assertEquals("أهلا بكم", sandbox.value.value.text)
        assertEquals(VoiceSessionStatus.LISTENING, controller.uiState.value.voice.status)
        assertEquals(2, backend.startedSessions.size)

        // Brief pause/stutter triggers NO_MATCH -> continuous loop resumes listening automatically (sid3)
        val sid2 = backend.startedSessions.last()
        backend.lastListener?.onError(sid2, VoiceErrorType.NO_MATCH)
        composeRule.waitForIdle()
        assertEquals(VoiceSessionStatus.LISTENING, controller.uiState.value.voice.status)
        assertEquals(3, backend.startedSessions.size)

        // Segment 3 finishes -> cleanly appends with spacing
        val sid3 = backend.startedSessions.last()
        backend.lastListener?.onFinal(sid3, "في الكيبورد")
        composeRule.waitForIdle()
        assertEquals("أهلا بكم في الكيبورد", sandbox.value.value.text)
        assertEquals(VoiceSessionStatus.LISTENING, controller.uiState.value.voice.status)

        // Tapping Translate inside VoiceDictationBanner stops voice and opens Translate pre-filled!
        composeRule.onNodeWithTag("voice_translate_button").performClick()
        composeRule.waitForIdle()
        assertEquals(VoiceSessionStatus.IDLE, controller.uiState.value.voice.status)
        assertEquals(KeyboardMode.AI_TRANSLATE, controller.uiState.value.mode)
        assertEquals("أهلا بكم في الكيبورد", controller.uiState.value.translate.inputText)

        // Now verify automatic silence timeout stops continuous listening
        controller.setMode(KeyboardMode.ALPHA)
        controller.startOrStopVoiceDictation()
        assertEquals(VoiceSessionStatus.LISTENING, controller.uiState.value.voice.status)
        ShadowLooper.idleMainLooper(350L, TimeUnit.MILLISECONDS)
        assertEquals(VoiceSessionStatus.IDLE, controller.uiState.value.voice.status)

        controller.destroy()
    }

    @Test
    fun topStripCustomizePanel_andPersistentBackToKeyboardButton_workInRtlAndLtrAndSyncWithSettings() {
        val prefsRepo = PreferencesRepository(context)
        prefsRepo.updateSettings {
            it.copy(
                selectedSubtypeLocale = "ar",
                appLanguage = "en",
                toolbarMode = ToolbarMode.TOOLBAR_KEYS.id
            )
        }
        val controller = HeliKeyboardController(
            context = context,
            prefsRepo = prefsRepo,
            assetRepo = AssetRepository(context),
            suggestionEngine = SuggestionEngine(),
            target = SandboxInputTarget()
        )

        composeRule.setContent {
            val settings by prefsRepo.settings.collectAsState()
            val uiState by controller.uiState.collectAsState()
            HeliBoardTheme(darkTheme = false) {
                HeliKeyboardView(
                    controller = controller,
                    uiState = uiState,
                    settings = settings,
                    customThemes = emptyList(),
                    clipboardEntries = emptyList(),
                    emojiCategories = emptyList(),
                    onDeleteClipboard = {},
                    onPinClipboard = {},
                    onClearUnpinnedClipboard = {}
                )
            }
        }
        composeRule.waitForIdle()

        // In ALPHA mode, top_strip_back_to_keyboard is hidden while toolbar_customize_button is visible
        assertEquals(0, composeRule.onAllNodesWithTag("top_strip_back_to_keyboard").fetchSemanticsNodes().size)
        composeRule.onNodeWithTag("toolbar_customize_button").assertIsDisplayed().performClick()
        composeRule.waitForIdle()

        // Clicking toolbar_customize_button opens TOOLBAR_CUSTOMIZE and reveals top_strip_back_to_keyboard
        assertEquals(KeyboardMode.TOOLBAR_CUSTOMIZE, controller.uiState.value.mode)
        composeRule.onNodeWithTag("toolbar_customize_list").assertIsDisplayed()
        composeRule.onNodeWithTag("top_strip_back_to_keyboard").assertIsDisplayed()

        // Toggle EMOJI off -> moves to Available Buttons at bottom of LazyColumn -> scroll to it and toggle back on
        composeRule.onNodeWithTag("toolbar_customize_list").performScrollToNode(hasTestTag("toolbar_toggle_emoji"))
        composeRule.onNodeWithTag("toolbar_toggle_emoji").performClick()
        composeRule.waitForIdle()
        assertFalse(ToolbarKey.EMOJI.name in prefsRepo.settings.value.toolbarKeys)

        composeRule.onNodeWithTag("toolbar_customize_list").performScrollToNode(hasTestTag("toolbar_toggle_emoji"))
        composeRule.onNodeWithTag("toolbar_toggle_emoji").performClick()
        composeRule.waitForIdle()
        assertTrue(ToolbarKey.EMOJI.name in prefsRepo.settings.value.toolbarKeys)

        // Move second item up to index 0 using the customize control
        val secondBefore = prefsRepo.settings.value.toolbarKeys[1]
        val moveUpTag = "toolbar_move_up_${secondBefore.lowercase()}"
        composeRule.onNodeWithTag("toolbar_customize_list").performScrollToNode(hasTestTag(moveUpTag))
        composeRule.onNodeWithTag(moveUpTag).performClick()
        composeRule.waitForIdle()
        assertEquals(secondBefore, prefsRepo.settings.value.toolbarKeys[0])

        // Return to ALPHA via persistent top-strip Back-to-Keyboard button
        // First verify RTL side-edge mirroring in Arabic: Back button is on the RIGHT, Customize is on the LEFT
        val backBoundsRtl = composeRule.onNodeWithTag("top_strip_back_to_keyboard").fetchSemanticsNode().boundsInRoot
        val customizeBoundsRtl = composeRule.onNodeWithTag("toolbar_customize_button").fetchSemanticsNode().boundsInRoot
        assertTrue(backBoundsRtl.left > customizeBoundsRtl.left)

        composeRule.onNodeWithTag("top_strip_back_to_keyboard").performClick()
        composeRule.waitForIdle()
        assertEquals(KeyboardMode.ALPHA, controller.uiState.value.mode)

        // Switch typing language to English LTR + open AI_TRANSLATE even with HIDDEN toolbarMode
        prefsRepo.updateSettings { it.copy(selectedSubtypeLocale = "en-US", toolbarMode = ToolbarMode.HIDDEN.id) }
        controller.setMode(KeyboardMode.AI_TRANSLATE)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("top_strip_back_to_keyboard").assertIsDisplayed().performClick()
        composeRule.waitForIdle()
        assertEquals(KeyboardMode.ALPHA, controller.uiState.value.mode)

        // Verify LTR placement when toolbar is visible in English: Back is on LEFT, Customize is on RIGHT
        prefsRepo.updateSettings { it.copy(toolbarMode = ToolbarMode.TOOLBAR_KEYS.id) }
        controller.setMode(KeyboardMode.EMOJI)
        composeRule.waitForIdle()
        val backBoundsLtr = composeRule.onNodeWithTag("top_strip_back_to_keyboard").fetchSemanticsNode().boundsInRoot
        val customizeBoundsLtr = composeRule.onNodeWithTag("toolbar_customize_button").fetchSemanticsNode().boundsInRoot
        assertTrue(backBoundsLtr.left < customizeBoundsLtr.left)

        // Reset restores default order
        controller.resetToolbarKeys()
        assertEquals(ToolbarKey.VOICE.name, prefsRepo.settings.value.toolbarKeys.first())
        controller.destroy()
    }

    @Test
    fun fastRolloverTypingAcrossShiftFlip_neverDropsChars_andSensitivitySettingsPersist() {
        val app = ApplicationProvider.getApplicationContext<HeliBoardApp>()
        val vm = HeliBoardViewModel(app)
        vm.updateSettings {
            it.copy(
                selectedSubtypeLocale = "en-US",
                appLanguage = "en",
                autoCap = true,
                keyPressSensitivity = 0.65f,
                instantKeypressCommit = false
            )
        }
        val sandbox = SandboxInputTarget()
        val controller = HeliKeyboardController(
            context = context,
            prefsRepo = vm.prefsRepo,
            assetRepo = AssetRepository(context),
            suggestionEngine = SuggestionEngine(),
            target = sandbox
        )
        controller.onInputFieldChanged(restarting = false)

        composeRule.setContent {
            val settings by vm.prefsRepo.settings.collectAsState()
            val uiState by controller.uiState.collectAsState()
            HeliBoardTheme(darkTheme = false) {
                HeliKeyboardView(
                    controller = controller,
                    uiState = uiState,
                    settings = settings,
                    customThemes = emptyList(),
                    clipboardEntries = emptyList(),
                    emojiCategories = emptyList(),
                    onDeleteClipboard = {},
                    onPinClipboard = {},
                    onClearUnpinnedClipboard = {}
                )
            }
        }
        composeRule.waitForIdle()

        // Press 'h' down (pointer 0) while shifted, tap 'i' (pointer 1) rapidly while 'h' is held and shiftState flips SHIFTED -> OFF
        composeRule.onNodeWithTag("key_character_h").performTouchInput { down(pointerId = 0, position = center) }
        composeRule.onNodeWithTag("key_character_i").performTouchInput {
            down(pointerId = 1, position = center)
            up(pointerId = 1)
        }
        composeRule.onNodeWithTag("key_character_h").performTouchInput { up(pointerId = 0) }
        composeRule.waitForIdle()
        // Both characters must be committed — zero dropped letters across shift transition!
        assertEquals(2, sandbox.value.value.text.length)

        // Enable instantKeypressCommit -> commits immediately on finger down!
        vm.updateSettings { it.copy(instantKeypressCommit = true, keyPressSensitivity = 0.90f) }
        composeRule.waitForIdle()
        val beforeInstant = sandbox.value.value.text
        composeRule.onNodeWithTag("key_character_o").performTouchInput { down(center) }
        composeRule.waitForIdle()
        assertEquals(beforeInstant + "o", sandbox.value.value.text)
        composeRule.onNodeWithTag("key_character_o").performTouchInput { up() }
        composeRule.waitForIdle()
        // Lifting finger must NOT commit 'o' a second time
        assertEquals(beforeInstant + "o", sandbox.value.value.text)

        controller.destroy()
    }

    @Test
    fun preferencesScreen_keySensitivityControls_updateAndPersist() {
        val app = ApplicationProvider.getApplicationContext<HeliBoardApp>()
        val vm = HeliBoardViewModel(app)
        vm.updateSettings { it.copy(appLanguage = "en", keyPressSensitivity = 0.65f, instantKeypressCommit = false) }

        composeRule.setContent {
            HeliBoardTheme(darkTheme = false) {
                PreferencesScreen(viewModel = vm, onBack = {})
            }
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("pref_key_sensitivity_card").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("pref_sensitivity_ultra").performScrollTo().performClick()
        composeRule.waitForIdle()

        assertTrue(vm.settings.value.keyPressSensitivity >= 0.90f)
        assertTrue(vm.settings.value.instantKeypressCommit)

        val reloaded = PreferencesRepository(context)
        assertTrue(reloaded.settings.value.keyPressSensitivity >= 0.90f)
        assertTrue(reloaded.settings.value.instantKeypressCommit)
    }
}
