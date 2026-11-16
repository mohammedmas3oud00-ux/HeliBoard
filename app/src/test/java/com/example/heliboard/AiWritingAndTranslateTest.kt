package com.example.heliboard

import android.content.Context
import androidx.activity.compose.LocalActivityResultRegistryOwner
import androidx.activity.compose.LocalOnBackPressedDispatcherOwner
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertIsEnabled
import androidx.compose.ui.test.assertIsNotEnabled
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import androidx.test.core.app.ApplicationProvider
import com.example.heliboard.ai.AiConfigResolver
import com.example.heliboard.ai.AiConnectionMode
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
import com.example.heliboard.data.HeliSettings
import com.example.heliboard.data.KeyActionType
import com.example.heliboard.data.KeySpec
import com.example.heliboard.data.KeyboardMode
import com.example.heliboard.data.PreferencesRepository
import com.example.heliboard.data.SuggestionEngine
import com.example.heliboard.data.ToolbarKey
import com.example.heliboard.ime.HapticFeedbackManager
import com.example.heliboard.ime.HeliKeyboardController
import com.example.heliboard.ime.HeliKeyboardView
import com.example.heliboard.ime.SandboxInputTarget
import com.example.heliboard.theme.HeliBoardTheme
import com.example.heliboard.ui.HeliBoardViewModel
import com.example.heliboard.util.LocaleHelper
import com.sun.net.httpserver.HttpServer
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
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
import java.net.InetSocketAddress
import java.util.concurrent.TimeUnit

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class AiWritingAndTranslateTest {

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
    }

    private class RecordingHapticManager(ctx: Context) : HapticFeedbackManager(ctx) {
        val pressEvents = mutableListOf<Pair<KeyActionType, Boolean>>()
        val longPressEvents = mutableListOf<Boolean>()

        override fun performKeyPress(actionType: KeyActionType, enabled: Boolean, view: android.view.View?) {
            pressEvents += actionType to enabled
            super.performKeyPress(actionType, enabled, view)
        }

        override fun performLongPress(enabled: Boolean, view: android.view.View?) {
            longPressEvents += enabled
            super.performLongPress(enabled, view)
        }
    }

    private class FakeAiProxyClient : AiProxyClient {
        val writingRequests = mutableListOf<Pair<AiProxyConfig, AiWritingRequest>>()
        val translateRequests = mutableListOf<Pair<AiProxyConfig, AiTranslateRequest>>()

        var writingHandler: suspend (AiProxyConfig, AiWritingRequest) -> AiWritingResult = { _, req ->
            when (req.operation) {
                AiWritingOperation.PROOFREAD -> AiWritingResult(req.operation, text = "Corrected: ${req.text}")
                AiWritingOperation.REWRITE -> AiWritingResult(req.operation, text = "Rewritten(${req.tone.apiValue}): ${req.text}")
                AiWritingOperation.COMPOSE_MESSAGE -> AiWritingResult(req.operation, text = "Draft message: ${req.text}")
                AiWritingOperation.COMPOSE_EMAIL -> AiWritingResult(
                    operation = req.operation,
                    subject = "Subject for ${req.text}",
                    body = "Dear [Recipient],\n\n${req.text}\n\nBest regards,\n[Your Name]",
                    text = "Subject for ${req.text}\n\nDear [Recipient],\n\n${req.text}"
                )
            }
        }

        var translateHandler: suspend (AiProxyConfig, AiTranslateRequest) -> AiTranslateResult = { _, req ->
            AiTranslateResult(
                translatedText = "[${req.targetLanguage.apiValue}] ${req.text}",
                detectedSourceLanguage = if (req.sourceLanguage == AiLanguageOption.AUTO) "en" else req.sourceLanguage.apiValue
            )
        }

        override suspend fun processWriting(config: AiProxyConfig, request: AiWritingRequest): AiWritingResult {
            writingRequests += config to request
            return writingHandler(config, request)
        }

        override suspend fun translate(config: AiProxyConfig, request: AiTranslateRequest): AiTranslateResult {
            translateRequests += config to request
            return translateHandler(config, request)
        }
    }

    @Test
    fun hapticFeedbackManager_dispatchesPressAndLongPressOnlyWhenEnabled() {
        val prefsRepo = PreferencesRepository(context)
        prefsRepo.updateSettings { it.copy(vibrateOnKeypress = true) }
        val sandbox = SandboxInputTarget()
        val haptics = RecordingHapticManager(context)
        val controller = HeliKeyboardController(
            context = context,
            prefsRepo = prefsRepo,
            assetRepo = AssetRepository(context),
            suggestionEngine = SuggestionEngine(),
            target = sandbox,
            hapticFeedbackManager = haptics
        )

        controller.onKeyPress(KeySpec("a"))
        controller.onKeyLongPressFeedback()
        assertEquals(listOf(KeyActionType.CHARACTER to true), haptics.pressEvents)
        assertEquals(listOf(true), haptics.longPressEvents)

        prefsRepo.updateSettings { it.copy(vibrateOnKeypress = false) }
        controller.onKeyPress(KeySpec("b"))
        assertEquals(KeyActionType.CHARACTER to false, haptics.pressEvents.last())
        controller.destroy()
    }

    @Test
    fun settingsMigration_v3ToV4_insertsAiWritingAndTranslateIntoToolbar() {
        val legacy = HeliSettings(
            schemaVersion = 3,
            toolbarKeys = listOf(ToolbarKey.VOICE.name, ToolbarKey.EMOJI.name, ToolbarKey.CLIPBOARD.name)
        )
        val json = Json { encodeDefaults = true }
        context.getSharedPreferences("heliboard_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("settings_json", json.encodeToString(legacy))
            .commit()

        val migrated = PreferencesRepository(context).settings.value
        assertEquals(6, migrated.schemaVersion)
        assertTrue(ToolbarKey.AI_WRITING.name in migrated.toolbarKeys)
        assertTrue(ToolbarKey.TRANSLATE.name in migrated.toolbarKeys)
        val voiceIdx = migrated.toolbarKeys.indexOf(ToolbarKey.VOICE.name)
        val aiIdx = migrated.toolbarKeys.indexOf(ToolbarKey.AI_WRITING.name)
        val trIdx = migrated.toolbarKeys.indexOf(ToolbarKey.TRANSLATE.name)
        assertEquals(voiceIdx + 1, aiIdx)
        assertEquals(aiIdx + 1, trIdx)
    }

    @Test
    fun aiWriting_cloudDisclosure_egyptianProofreadReplaceSelection_andSeparateEmailSubjectBody() {
        val prefsRepo = PreferencesRepository(context)
        prefsRepo.updateSettings {
            it.copy(
                appLanguage = "en",
                aiProxyUrl = "https://proxy.example.com",
                aiProxyAuthToken = "client-secret",
                aiCloudNoticeAccepted = false
            )
        }
        val sandbox = SandboxInputTarget()
        // Pre-select Egyptian Arabic phrase inside surrounding text
        sandbox.setExternalValue(
            TextFieldValue(
                text = "Prefix: ازيك يا احمد عامل ايه اليوم. Suffix",
                selection = TextRange(8, 35)
            )
        )

        val fakeClient = FakeAiProxyClient().apply {
            writingHandler = { _, req ->
                when (req.operation) {
                    AiWritingOperation.PROOFREAD -> AiWritingResult(
                        operation = req.operation,
                        text = "إزيك يا أحمد، عامل إيه النهاردة؟"
                    )
                    AiWritingOperation.REWRITE -> AiWritingResult(
                        operation = req.operation,
                        text = "Formal rewrite for ${req.text}"
                    )
                    AiWritingOperation.COMPOSE_EMAIL -> AiWritingResult(
                        operation = req.operation,
                        subject = "Meeting Tomorrow",
                        body = "Hi [Recipient],\nNotes: ${req.text}\nTime: [Time]",
                        text = "Meeting Tomorrow\n\nHi [Recipient],\nNotes: ${req.text}\nTime: [Time]"
                    )
                    else -> AiWritingResult(req.operation, text = req.text)
                }
            }
        }

        val controller = HeliKeyboardController(
            context = context,
            prefsRepo = prefsRepo,
            assetRepo = AssetRepository(context),
            suggestionEngine = SuggestionEngine(),
            target = sandbox,
            aiProxyClient = fakeClient
        )

        val assetRepo = AssetRepository(context)
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
                    emojiCategories = assetRepo.loadEmojiCategories(),
                    onDeleteClipboard = {},
                    onPinClipboard = {},
                    onClearUnpinnedClipboard = {}
                )
            }
        }

        // Open AI Writing from toolbar -> disclosure shown first, ZERO network calls triggered
        composeRule.onNodeWithTag("toolbar_key_ai_writing").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_disclosure_card").assertIsDisplayed()
        assertEquals(0, fakeClient.writingRequests.size)

        // Accept disclosure -> selection loaded, still ZERO automatic requests
        composeRule.onNodeWithTag("ai_disclosure_accept_button").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_selection_loaded_badge").assertIsDisplayed()
        assertEquals(0, fakeClient.writingRequests.size)

        // Submit Proofread -> result returned -> Replace Selection updates only selected range
        composeRule.onNodeWithTag("ai_writing_submit_button").performClick()
        composeRule.waitForIdle()
        assertEquals(1, fakeClient.writingRequests.size)
        assertEquals("https://proxy.example.com", fakeClient.writingRequests[0].first.baseUrl)
        assertEquals(AiWritingOperation.PROOFREAD, fakeClient.writingRequests[0].second.operation)
        composeRule.onNodeWithTag("ai_writing_result_preview")
            .assertTextContains("إزيك يا أحمد، عامل إيه النهاردة؟", substring = true)

        composeRule.onNodeWithTag("ai_writing_replace_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("Prefix: إزيك يا أحمد، عامل إيه النهاردة؟. Suffix", sandbox.value.value.text)

        // Switch to Compose Email, choose Arabic target + Formal tone, verify separate Subject/Body insert
        composeRule.onNodeWithTag("ai_op_compose_email").performScrollTo().performClick()
        composeRule.onNodeWithTag("ai_tone_formal").performScrollTo().performClick()
        composeRule.onNodeWithTag("ai_target_lang_ar").performScrollTo().performClick()
        controller.onAiWritingInputChanged("Confirm weekly sync")
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("ai_writing_submit_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        val lastReq = fakeClient.writingRequests.last().second
        assertEquals(AiWritingOperation.COMPOSE_EMAIL, lastReq.operation)
        assertEquals(AiTone.FORMAL, lastReq.tone)
        assertEquals(AiLanguageOption.AR, lastReq.targetLanguage)

        composeRule.onNodeWithTag("ai_email_subject_preview").assertTextContains("Meeting Tomorrow", substring = true)
        composeRule.onNodeWithTag("ai_email_body_preview").assertTextContains("Hi [Recipient]", substring = true)

        sandbox.setExternalValue(TextFieldValue(""))
        composeRule.onNodeWithTag("ai_email_insert_subject_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals("Meeting Tomorrow", sandbox.value.value.text)

        sandbox.commitText("\n")
        composeRule.onNodeWithTag("ai_email_insert_body_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(sandbox.value.value.text.contains("Hi [Recipient]"))

        controller.destroy()
    }

    @Test
    fun aiPrivacyGuards_noAutoClipboardRead_passwordBlocked_unconfiguredServer_andFieldChangeCopyOnly() {
        val prefsRepo = PreferencesRepository(context)
        prefsRepo.updateSettings {
            it.copy(
                appLanguage = "en",
                aiProxyUrl = "",
                aiCloudNoticeAccepted = true
            )
        }
        prefsRepo.addClipboardEntry("secret-clipboard-note")
        val sandbox = SandboxInputTarget()
        var aiSettingsOpened = 0
        val gate = CompletableDeferred<AiWritingResult>()

        val fakeClient = FakeAiProxyClient().apply {
            writingHandler = { _, _ -> gate.await() }
        }

        val controller = HeliKeyboardController(
            context = context,
            prefsRepo = prefsRepo,
            assetRepo = AssetRepository(context),
            suggestionEngine = SuggestionEngine(),
            target = sandbox,
            onOpenAiSettings = { aiSettingsOpened++ },
            aiProxyClient = fakeClient,
            defaultProxyUrlProvider = { "" },
            defaultProxyTokenProvider = { "" },
            defaultGeminiApiKeyProvider = { "" }
        )

        val assetRepo = AssetRepository(context)
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
                    emojiCategories = assetRepo.loadEmojiCategories(),
                    onDeleteClipboard = {},
                    onPinClipboard = {},
                    onClearUnpinnedClipboard = {}
                )
            }
        }

        // 1. Opening AI Writing never auto-reads clipboard; Paste reads only on explicit click
        composeRule.onNodeWithTag("toolbar_key_ai_writing").performClick()
        composeRule.waitForIdle()
        assertEquals("", controller.uiState.value.aiWriting.inputText)

        composeRule.onNodeWithTag("ai_writing_paste_button").performClick()
        composeRule.waitForIdle()
        assertEquals("secret-clipboard-note", controller.uiState.value.aiWriting.inputText)

        // 2. Unconfigured server URL surfaces clear error + Settings navigation button
        composeRule.onNodeWithTag("ai_writing_submit_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(AiProxyErrorType.UNCONFIGURED, controller.uiState.value.aiWriting.errorType)
        composeRule.onNodeWithTag("ai_configure_server_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertEquals(1, aiSettingsOpened)

        // 3. Field changed while request is in-flight -> switches to Copy-only and disables Insert
        prefsRepo.updateSettings { it.copy(aiProxyUrl = "https://proxy.example.com") }
        composeRule.onNodeWithTag("ai_writing_submit_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_writing_cancel_button").performScrollTo().assertIsDisplayed()

        // Switch field before response completes
        controller.onInputFieldChanged(restarting = false)
        gate.complete(AiWritingResult(AiWritingOperation.PROOFREAD, text = "Polished note"))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("ai_writing_field_changed_banner").performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag("ai_writing_insert_button").assertDoesNotExist()
        composeRule.onNodeWithTag("ai_writing_copy_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertTrue(controller.uiState.value.aiWriting.copiedNotice)

        // 4. Cancel in-flight request stops loading cleanly
        val slowGate = CompletableDeferred<AiWritingResult>()
        fakeClient.writingHandler = { _, _ -> slowGate.await() }
        controller.onAiWritingInputChanged("Try again")
        composeRule.onNodeWithTag("ai_writing_submit_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_writing_cancel_button").performScrollTo().performClick()
        composeRule.waitForIdle()
        assertFalse(controller.uiState.value.aiWriting.isLoading)

        // 5. Password field blocks AI Writing & Translation completely
        sandbox.setPasswordInput(true)
        controller.refreshLayoutAndSuggestions()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("ai_password_blocked_banner").assertIsDisplayed()
        composeRule.onNodeWithTag("ai_writing_input").assertDoesNotExist()

        controller.destroy()
    }

    @Test
    fun translatePanel_directionsSwap_autoTranslateDebounceSupersedesStaleAndStopsOnCloseOrFieldChange() {
        val prefsRepo = PreferencesRepository(context)
        prefsRepo.updateSettings {
            it.copy(
                appLanguage = "en",
                selectedSubtypeLocale = "en-US",
                aiProxyUrl = "https://proxy.example.com",
                aiCloudNoticeAccepted = true
            )
        }
        val sandbox = SandboxInputTarget()
        val firstGate = CompletableDeferred<AiTranslateResult>()
        val secondGate = CompletableDeferred<AiTranslateResult>()
        var callNum = 0

        val fakeClient = FakeAiProxyClient().apply {
            translateHandler = { _, req ->
                callNum++
                if (callNum == 1) firstGate.await() else {
                    secondGate.complete(
                        AiTranslateResult(
                            translatedText = "مرحبًا يا علي\nhttps://example.com #42",
                            detectedSourceLanguage = "en"
                        )
                    )
                    secondGate.await()
                }
            }
        }

        val controller = HeliKeyboardController(
            context = context,
            prefsRepo = prefsRepo,
            assetRepo = AssetRepository(context),
            suggestionEngine = SuggestionEngine(),
            target = sandbox,
            aiProxyClient = fakeClient,
            autoTranslateDebounceMs = 20L
        )

        val assetRepo = AssetRepository(context)
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
                    emojiCategories = assetRepo.loadEmojiCategories(),
                    onDeleteClipboard = {},
                    onPinClipboard = {},
                    onClearUnpinnedClipboard = {}
                )
            }
        }

        composeRule.onNodeWithTag("toolbar_key_translate").performClick()
        composeRule.waitForIdle()

        // Swap button disabled when source is Auto, enabled when explicit EN -> AR
        composeRule.onNodeWithTag("translate_swap_button").assertIsNotEnabled()
        composeRule.onNodeWithTag("translate_from_en").performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("translate_swap_button").assertIsEnabled()

        // Swap turns EN -> AR into AR -> EN
        composeRule.onNodeWithTag("translate_swap_button").performClick()
        composeRule.waitForIdle()
        assertEquals(AiLanguageOption.AR, controller.uiState.value.translate.sourceLanguage)
        assertEquals(AiLanguageOption.EN, controller.uiState.value.translate.targetLanguage)

        // Back to Auto -> AR and test Auto-Translate superseding stale responses
        composeRule.onNodeWithTag("translate_from_auto").performClick()
        composeRule.onNodeWithTag("translate_to_ar").performClick()
        composeRule.onNodeWithTag("translate_auto_switch").performClick()
        composeRule.waitForIdle()
        assertTrue(controller.uiState.value.translate.autoTranslateEnabled)

        // Rapid typing before debounce window elapses coalesces into 1 request
        controller.onTranslateInputChanged("He")
        controller.onTranslateInputChanged("Hello")
        ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
        composeRule.waitForIdle()
        assertEquals(1, fakeClient.translateRequests.size)
        assertEquals("Hello", fakeClient.translateRequests[0].second.text)

        // User edits text before first response returns -> triggers newer request
        controller.onTranslateInputChanged("Hello Ali\nhttps://example.com #42")
        ShadowLooper.idleMainLooper(100, TimeUnit.MILLISECONDS)
        composeRule.waitForIdle()
        assertEquals(2, fakeClient.translateRequests.size)

        // Now complete stale first response -> MUST be ignored in favor of newer result
        firstGate.complete(AiTranslateResult("STALE_OLD_RESULT", "en"))
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("translate_result_preview")
            .assertTextContains("مرحبًا يا علي", substring = true)

        // Closing panel turns Auto-Translate OFF immediately
        composeRule.onNodeWithTag("translate_back_button").performClick()
        composeRule.waitForIdle()
        assertEquals(KeyboardMode.ALPHA, controller.uiState.value.mode)
        assertFalse(controller.uiState.value.translate.autoTranslateEnabled)

        // Re-open, enable auto-translate, hide keyboard -> Auto-Translate turns OFF immediately
        composeRule.onNodeWithTag("toolbar_key_translate").performClick()
        composeRule.onNodeWithTag("translate_auto_switch").performClick()
        composeRule.waitForIdle()
        assertTrue(controller.uiState.value.translate.autoTranslateEnabled)

        controller.onKeyboardHidden()
        composeRule.waitForIdle()
        assertFalse(controller.uiState.value.translate.autoTranslateEnabled)

        controller.destroy()
    }

    @Test
    fun httpAiProxyClient_communicatesWithLocalProxyAndHandlesErrors() = runBlocking(Dispatchers.IO) {
        var lastAuthHeader: String? = null
        var nextStatus = 200
        var nextResponse = """{"task":"translate","action":"translate","translatedText":"أهلا بك","detectedSourceLanguage":"en"}"""

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1/ai/process") { exchange ->
            lastAuthHeader = exchange.requestHeaders.getFirst("Authorization")
            exchange.requestBody.readBytes()
            val bytes = nextResponse.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(nextStatus, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()

        try {
            val baseUrl = "http://127.0.0.1:${server.address.port}"
            val client = HttpAiProxyClient(context = null, ioDispatcher = Dispatchers.IO)
            val cfg = AiProxyConfig(baseUrl = baseUrl, authToken = "tok-123")

            val res = client.translate(
                cfg,
                AiTranslateRequest(AiLanguageOption.AUTO, AiLanguageOption.AR, "Welcome")
            )
            assertEquals("Bearer tok-123", lastAuthHeader)
            assertEquals("أهلا بك", res.translatedText)
            assertEquals("en", res.detectedSourceLanguage)

            nextStatus = 401
            nextResponse = """{"error":{"code":"UNAUTHORIZED","message":"Bad token"}}"""
            val authErr = runCatching {
                client.processWriting(cfg, AiWritingRequest(AiWritingOperation.PROOFREAD, AiTone.SAME, AiLanguageOption.AUTO, "hi"))
            }.exceptionOrNull() as? AiProxyException
            assertEquals(AiProxyErrorType.UNAUTHORIZED, authErr?.type)

            nextStatus = 429
            nextResponse = """{"error":{"code":"RATE_LIMITED","message":"Slow down"}}"""
            val rateErr = runCatching {
                client.processWriting(cfg, AiWritingRequest(AiWritingOperation.PROOFREAD, AiTone.SAME, AiLanguageOption.AUTO, "hi"))
            }.exceptionOrNull() as? AiProxyException
            assertEquals(AiProxyErrorType.RATE_LIMITED, rateErr?.type)
        } finally {
            server.stop(0)
        }
    }

    @Test
    fun aiSettingsScreen_persistsProxyUrlAndToken() {
        val app = ApplicationProvider.getApplicationContext<HeliBoardApp>()
        val vm = HeliBoardViewModel(app)
        vm.updateSettings { it.copy(appLanguage = "en") }

        composeRule.setContent {
            val settings by vm.settings.collectAsState()
            val baseCtx = LocalContext.current
            val (localizedCtx, localizedConfig) = remember(settings.appLanguage, baseCtx) {
                LocaleHelper.createLocalizedContext(baseCtx, settings.appLanguage)
            }
            val layoutDir = remember(settings.appLanguage) {
                LocaleHelper.resolveLayoutDirection(settings.appLanguage)
            }
            val registryOwner = LocalActivityResultRegistryOwner.current
            val backDispatcherOwner = LocalOnBackPressedDispatcherOwner.current
            val providers = listOfNotNull(
                LocalContext provides localizedCtx,
                LocalConfiguration provides localizedConfig,
                LocalLayoutDirection provides layoutDir,
                registryOwner?.let { LocalActivityResultRegistryOwner provides it },
                backDispatcherOwner?.let { LocalOnBackPressedDispatcherOwner provides it }
            ).toTypedArray()

            CompositionLocalProvider(*providers) {
                HeliBoardTheme {
                    HeliBoardRoot(viewModel = vm)
                }
            }
        }

        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.onNodeWithTag("settings_ai").performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag("ai_proxy_url_input").performTextInput("https://my-gemini-proxy.example.com")
        composeRule.onNodeWithTag("ai_proxy_token_input").performTextInput("token-xyz")
        composeRule.onNodeWithTag("ai_proxy_save_button").performClick()
        composeRule.waitForIdle()

        val reloaded = PreferencesRepository(context)
        assertEquals("https://my-gemini-proxy.example.com", reloaded.settings.value.aiProxyUrl)
        assertEquals("token-xyz", reloaded.settings.value.aiProxyAuthToken)
    }

    @Test
    fun aiConfigResolver_prioritizesProxyThenBuildProxyThenAutoGeminiSecretsKey() {
        // Placeholder defaults resolve to UNCONFIGURED
        assertEquals(
            AiConnectionMode.UNCONFIGURED,
            AiConfigResolver.resolveConnectionMode("", "", "GEMINI_API_KEY_DEFAULT_VALUE")
        )
        assertEquals(
            null,
            AiConfigResolver.resolveConfig("", "", "", "", "GEMINI_API_KEY_DEFAULT_VALUE")
        )

        // When only GEMINI_API_KEY is supplied via Secrets, resolves automatically without proxy URL
        val autoKeyConfig = AiConfigResolver.resolveConfig(
            settingsProxyUrl = "",
            settingsAuthToken = "",
            buildProxyUrl = "",
            buildProxyToken = "",
            buildGeminiApiKey = "test-secrets-key-123"
        )
        assertEquals("test-secrets-key-123", autoKeyConfig?.directGeminiApiKey)
        assertEquals("", autoKeyConfig?.baseUrl)
        assertEquals(
            AiConnectionMode.AUTO_GEMINI_KEY,
            AiConfigResolver.resolveConnectionMode("", "", "test-secrets-key-123")
        )

        // Build-time proxy overrides direct key mode
        val buildProxyCfg = AiConfigResolver.resolveConfig(
            settingsProxyUrl = "",
            settingsAuthToken = "",
            buildProxyUrl = "https://build-proxy.example.com",
            buildProxyToken = "build-tok",
            buildGeminiApiKey = "test-secrets-key-123"
        )
        assertEquals("https://build-proxy.example.com", buildProxyCfg?.baseUrl)
        assertEquals("build-tok", buildProxyCfg?.authToken)
        assertEquals("", buildProxyCfg?.directGeminiApiKey)

        // Explicit user settings proxy overrides everything
        val userProxyCfg = AiConfigResolver.resolveConfig(
            settingsProxyUrl = "https://user-proxy.example.com",
            settingsAuthToken = "user-tok",
            buildProxyUrl = "https://build-proxy.example.com",
            buildProxyToken = "build-tok",
            buildGeminiApiKey = "test-secrets-key-123"
        )
        assertEquals("https://user-proxy.example.com", userProxyCfg?.baseUrl)
        assertEquals("user-tok", userProxyCfg?.authToken)
    }

    @Test
    fun httpAiProxyClient_autoGeminiMode_callsGeminiEndpointAndParsesEmailAndTranslation() = runBlocking(Dispatchers.IO) {
        var seenApiKeyHeader: String? = null
        var lastRequestBody = ""
        var mode = "email"

        val server = HttpServer.create(InetSocketAddress("127.0.0.1", 0), 0)
        server.createContext("/v1beta/models/gemini-2.5-flash:generateContent") { exchange ->
            seenApiKeyHeader = exchange.requestHeaders.getFirst("x-goog-api-key")
            lastRequestBody = exchange.requestBody.readBytes().toString(Charsets.UTF_8)
            val innerJson = if (mode == "email") {
                """{"subject":"متابعة الاجتماع","body":"أهلاً [الاسم]، جاهز للاجتماع.","detectedLanguage":"ar"}"""
            } else {
                """{"translatedText":"Hello from Cairo","detectedSourceLanguage":"ar"}"""
            }
            val escapedInner = innerJson.replace("\\", "\\\\").replace("\"", "\\\"")
            val geminiEnvelope = """{"candidates":[{"content":{"parts":[{"text":"$escapedInner"}]}}]}"""
            val bytes = geminiEnvelope.toByteArray(Charsets.UTF_8)
            exchange.responseHeaders.add("Content-Type", "application/json")
            exchange.sendResponseHeaders(200, bytes.size.toLong())
            exchange.responseBody.use { it.write(bytes) }
        }
        server.start()

        try {
            val endpoint = "http://127.0.0.1:${server.address.port}/v1beta/models/gemini-2.5-flash:generateContent"
            val client = HttpAiProxyClient(
                context = null,
                ioDispatcher = Dispatchers.IO,
                directGeminiEndpoint = endpoint
            )
            val autoCfg = AiProxyConfig(baseUrl = "", directGeminiApiKey = "unit-test-key")

            val emailRes = client.processWriting(
                autoCfg,
                AiWritingRequest(AiWritingOperation.COMPOSE_EMAIL, AiTone.FORMAL, AiLanguageOption.AR, "تأكيد الاجتماع")
            )
            assertEquals("unit-test-key", seenApiKeyHeader)
            assertTrue(lastRequestBody.contains("application/json"))
            assertEquals("متابعة الاجتماع", emailRes.subject)
            assertTrue(emailRes.body.contains("[الاسم]"))

            mode = "translate"
            val trRes = client.translate(
                autoCfg,
                AiTranslateRequest(AiLanguageOption.AUTO, AiLanguageOption.EN, "أهلا من القاهرة")
            )
            assertEquals("Hello from Cairo", trRes.translatedText)
            assertEquals("ar", trRes.detectedSourceLanguage)
        } finally {
            server.stop(0)
        }
    }
}
