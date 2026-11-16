package com.example.heliboard

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.test.core.app.ApplicationProvider
import com.example.heliboard.data.AssetRepository
import com.example.heliboard.data.PreferencesRepository
import com.example.heliboard.data.SuggestionEngine
import com.example.heliboard.ime.HeliKeyboardController
import com.example.heliboard.ime.HeliKeyboardView
import com.example.heliboard.ime.SandboxInputTarget
import com.example.heliboard.theme.HeliBoardTheme
import com.example.heliboard.ui.HeliBoardViewModel
import com.example.heliboard.ui.screens.PreferencesScreen
import com.example.heliboard.voice.AndroidVoiceDictationBackend
import com.example.heliboard.voice.PlatformSpeechRecognizer
import com.example.heliboard.voice.VoiceDictationBackend
import com.example.heliboard.voice.VoiceErrorType
import com.example.heliboard.voice.VoiceRecognitionListener
import com.example.heliboard.voice.VoiceSessionStatus
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
class OfflineVoiceFixVerificationTest {

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

    private class FakePlatformRecognizer(val isOnDevice: Boolean) : PlatformSpeechRecognizer {
        var listener: RecognitionListener? = null
        val startedIntents = mutableListOf<Intent>()
        val modelDownloadLocales = mutableListOf<String>()
        var destroyed = false
        var cancelled = false

        override fun setRecognitionListener(listener: RecognitionListener) {
            this.listener = listener
        }

        override fun startListening(recognizerIntent: Intent) {
            startedIntents += recognizerIntent
        }

        override fun stopListening() = Unit

        override fun cancel() {
            cancelled = true
        }

        override fun destroy() {
            destroyed = true
        }

        override fun triggerModelDownload(recognizerIntent: Intent) {
            recognizerIntent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE)?.let {
                modelDownloadLocales += it
            }
        }
    }

    private class ScriptedVoiceBackend : VoiceDictationBackend {
        var permissionGranted = true
        var openedOfflineSettingsCount = 0
        var lastSessionId: Long = -1L
        var lastListener: VoiceRecognitionListener? = null

        override fun hasAudioPermission(): Boolean = permissionGranted
        override fun isLocalRecognitionAvailable(localeTag: String): Boolean = true
        override fun isOnDeviceOfflineAvailable(): Boolean = true
        override fun isSystemVoiceInputAvailable(): Boolean = true
        override fun openOfflineVoiceSettings(): Boolean {
            openedOfflineSettingsCount++
            return true
        }

        override fun startListening(
            sessionId: Long,
            localeTag: String,
            fallbackLocaleTag: String?,
            listener: VoiceRecognitionListener,
            preferOffline: Boolean
        ) {
            lastSessionId = sessionId
            lastListener = listener
        }

        override fun stopListening(sessionId: Long) = Unit
        override fun cancel(sessionId: Long) = Unit
        override fun launchSystemVoiceInput(): Boolean = true
        override fun destroy() = Unit
    }

    @Test
    fun buildRecognitionIntent_removes15sMinLengthBlocker_andSetsOfflineAndSilenceExtras() {
        val backend = AndroidVoiceDictationBackend(context)

        val offlineIntent = backend.buildRecognitionIntent(
            AndroidVoiceDictationBackend.RecognitionAttempt(
                localeTag = "ar-EG",
                useOnDeviceRecognizer = true,
                forceOfflineExtra = true
            )
        )
        assertFalse(offlineIntent.hasExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS))
        assertEquals(
            1800L,
            offlineIntent.getLongExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 0L)
        )
        assertEquals(
            1300L,
            offlineIntent.getLongExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 0L)
        )
        assertTrue(offlineIntent.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false))
        assertEquals("ar-EG", offlineIntent.getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))

        val onlineFallbackIntent = backend.buildRecognitionIntent(
            AndroidVoiceDictationBackend.RecognitionAttempt(
                localeTag = "ar",
                useOnDeviceRecognizer = false,
                forceOfflineExtra = false
            )
        )
        assertFalse(onlineFallbackIntent.hasExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS))
        assertFalse(onlineFallbackIntent.getBooleanExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, false))
    }

    @Test
    fun androidVoiceBackend_triggersModelDownload_andRecoversFromServerDisconnectedBusyAndLanguageUnavailable() {
        val backend = AndroidVoiceDictationBackend(context).apply {
            testCooldownMs = 10L
            testStandardAvailableOverride = true
            testOnDeviceAvailableOverride = true
        }
        val createdRecognizers = mutableListOf<FakePlatformRecognizer>()
        backend.testRecognizerFactory = { useOnDevice ->
            val rec = FakePlatformRecognizer(useOnDevice)
            createdRecognizers += rec
            rec to useOnDevice
        }

        val finals = mutableListOf<String>()
        val errors = mutableListOf<VoiceErrorType>()
        val listener = object : VoiceRecognitionListener {
            override fun onReady(sessionId: Long) = Unit
            override fun onPartial(sessionId: Long, text: String) = Unit
            override fun onFinal(sessionId: Long, text: String) {
                finals += text
            }
            override fun onError(sessionId: Long, type: VoiceErrorType, detail: String?) {
                errors += type
            }
        }

        // Start preferOffline = true for Arabic: tries on-device ar-EG -> ar-SA -> ar, then standard
        backend.startListening(
            sessionId = 1L,
            localeTag = "ar-EG",
            fallbackLocaleTag = "ar",
            listener = listener,
            preferOffline = true
        )
        ShadowLooper.idleMainLooper(20L, TimeUnit.MILLISECONDS)

        val firstRec = createdRecognizers.first()
        assertTrue(firstRec.isOnDevice)
        assertEquals("ar-EG", firstRec.startedIntents.last().getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))

        // 1. Missing Arabic Egypt pack -> triggers model download + schedules fallback to ar-SA
        firstRec.listener?.onError(SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE)
        assertEquals(listOf("ar-EG"), firstRec.modelDownloadLocales)
        ShadowLooper.idleMainLooper(20L, TimeUnit.MILLISECONDS)

        val secondAttemptLocale = firstRec.startedIntents.last().getStringExtra(RecognizerIntent.EXTRA_LANGUAGE)
        assertEquals("ar-SA", secondAttemptLocale)

        // 2. ERROR_RECOGNIZER_BUSY before ready -> 1-shot destroy + 200ms backoff retry on SAME ar-SA attempt
        firstRec.listener?.onError(SpeechRecognizer.ERROR_RECOGNIZER_BUSY)
        assertTrue(firstRec.destroyed)
        ShadowLooper.idleMainLooper(220L, TimeUnit.MILLISECONDS)
        val afterBusyRec = createdRecognizers.last()
        assertEquals("ar-SA", afterBusyRec.startedIntents.last().getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))

        // 3. ERROR_SERVER_DISCONNECTED -> destroys recognizer & retries next attempt (ar)
        afterBusyRec.listener?.onError(SpeechRecognizer.ERROR_SERVER_DISCONNECTED)
        assertTrue(afterBusyRec.destroyed)
        ShadowLooper.idleMainLooper(20L, TimeUnit.MILLISECONDS)
        val thirdRec = createdRecognizers.last()
        assertEquals("ar", thirdRec.startedIntents.last().getStringExtra(RecognizerIntent.EXTRA_LANGUAGE))

        // 4. Success delivers final result immediately without failing session
        val bundle = Bundle().apply {
            putStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION, arrayListOf("السلام عليكم"))
        }
        thirdRec.listener?.onReadyForSpeech(null)
        thirdRec.listener?.onResults(bundle)
        assertEquals(listOf("السلام عليكم"), finals)
        assertTrue(errors.isEmpty())

        // 5. After onReadyForSpeech succeeds, natural silence NO_MATCH reports NO_MATCH immediately (no bogus loop)
        backend.startListening(2L, "ar", "ar", listener, preferOffline = true)
        ShadowLooper.idleMainLooper(30L, TimeUnit.MILLISECONDS)
        val readyRec = createdRecognizers.last()
        readyRec.listener?.onReadyForSpeech(null)
        readyRec.listener?.onError(SpeechRecognizer.ERROR_NO_MATCH)
        assertEquals(listOf(VoiceErrorType.NO_MATCH), errors)

        backend.destroy()
    }

    @Test
    fun controller_onSpeechStartedResetsWatchdog_andPartialTextCommitsOnWatchdogTimeout() {
        val prefsRepo = PreferencesRepository(context)
        prefsRepo.updateSettings {
            it.copy(
                selectedSubtypeLocale = "ar",
                appLanguage = "ar",
                preferOfflineVoice = true,
                continuousVoiceDictation = true
            )
        }
        val backend = ScriptedVoiceBackend()
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

        controller.startOrStopVoiceDictation()
        val sid = backend.lastSessionId
        assertEquals(VoiceSessionStatus.LISTENING, controller.uiState.value.voice.status)

        // Advance 180ms (< 250ms), then fire onSpeechStarted -> watchdog resets!
        ShadowLooper.idleMainLooper(180L, TimeUnit.MILLISECONDS)
        backend.lastListener?.onSpeechStarted(sid)
        ShadowLooper.idleMainLooper(150L, TimeUnit.MILLISECONDS) // Total elapsed = 330ms > 250ms
        assertEquals(VoiceSessionStatus.LISTENING, controller.uiState.value.voice.status)

        // Emit partial result, then wait past watchdog timeout -> uncommitted partial must commit cleanly ONCE
        backend.lastListener?.onPartial(sid, "نص محلي تجريبي")
        ShadowLooper.idleMainLooper(300L, TimeUnit.MILLISECONDS)
        assertEquals(VoiceSessionStatus.IDLE, controller.uiState.value.voice.status)
        assertEquals("نص محلي تجريبي", sandbox.value.value.text)

        controller.destroy()
    }

    @Test
    fun ensureVoiceDictationStarted_doesNotDoubleToggleOff_andOfflineSettingsActionWorksInBannerAndSettings() {
        val app = ApplicationProvider.getApplicationContext<HeliBoardApp>()
        val vm = HeliBoardViewModel(app)
        vm.updateSettings {
            it.copy(
                selectedSubtypeLocale = "ar",
                appLanguage = "en",
                preferOfflineVoice = true,
                continuousVoiceDictation = false
            )
        }
        val backend = ScriptedVoiceBackend().apply { permissionGranted = false }
        val controller = HeliKeyboardController(
            context = context,
            prefsRepo = vm.prefsRepo,
            assetRepo = AssetRepository(context),
            suggestionEngine = SuggestionEngine(),
            target = SandboxInputTarget(),
            voiceBackend = backend,
            onRequestAudioPermission = {}
        )

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

        // Request permission -> grant permission -> simulate IME resume calling ensureVoiceDictationStarted
        controller.startOrStopVoiceDictation()
        assertEquals(VoiceSessionStatus.PERMISSION_REQUIRED, controller.uiState.value.voice.status)

        backend.permissionGranted = true
        controller.onVoicePermissionResult(true)
        assertEquals(VoiceSessionStatus.LISTENING, controller.uiState.value.voice.status)

        // Calling ensureVoiceDictationStarted again MUST NOT stop listening!
        controller.ensureVoiceDictationStarted()
        assertEquals(VoiceSessionStatus.LISTENING, controller.uiState.value.voice.status)

        // When offline language pack is missing, banner exposes Offline Voice Packs button and calls backend
        backend.lastListener?.onError(backend.lastSessionId, VoiceErrorType.LANGUAGE_UNSUPPORTED)
        composeRule.waitForIdle()
        assertEquals(VoiceSessionStatus.UNAVAILABLE, controller.uiState.value.voice.status)
        assertTrue(controller.uiState.value.voice.canOpenOfflineVoiceSettings)
        composeRule.onNodeWithTag("voice_offline_settings_button").assertIsDisplayed().performClick()
        assertEquals(1, backend.openedOfflineSettingsCount)

        controller.destroy()
    }

    @Test
    fun preferencesScreen_displaysOfflineVoicePacksButton() {
        val app = ApplicationProvider.getApplicationContext<HeliBoardApp>()
        val vm = HeliBoardViewModel(app)
        vm.updateSettings { it.copy(appLanguage = "en") }

        composeRule.setContent {
            HeliBoardTheme(darkTheme = false) {
                PreferencesScreen(viewModel = vm, onBack = {})
            }
        }

        composeRule.onNodeWithTag("pref_voice_offline_settings_btn")
            .performScrollTo()
            .assertIsDisplayed()
    }
}
