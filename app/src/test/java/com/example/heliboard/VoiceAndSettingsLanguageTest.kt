package com.example.heliboard

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
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
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.unit.LayoutDirection
import androidx.test.core.app.ApplicationProvider
import com.example.heliboard.data.AssetRepository
import com.example.heliboard.data.PreferencesRepository
import com.example.heliboard.data.SuggestionEngine
import com.example.heliboard.ime.HeliKeyboardController
import com.example.heliboard.ime.HeliKeyboardView
import com.example.heliboard.ime.SandboxInputTarget
import com.example.heliboard.theme.HeliBoardTheme
import com.example.heliboard.ui.HeliBoardViewModel
import com.example.heliboard.util.LocaleHelper
import com.example.heliboard.voice.AndroidVoiceDictationBackend
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class VoiceAndSettingsLanguageTest {

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

    private class FakeVoiceBackend : VoiceDictationBackend {
        var permissionGranted = true
        var localAvailable = true
        var systemVoiceAvailable = true
        var systemFallbackLaunched = false
        var lastSessionId: Long = -1L
        var lastLocale: String? = null
        var lastFallback: String? = null
        var lastListener: VoiceRecognitionListener? = null
        var cancelledSessionId: Long = -1L
        var stoppedSessionId: Long = -1L

        override fun hasAudioPermission(): Boolean = permissionGranted
        override fun isLocalRecognitionAvailable(localeTag: String): Boolean = localAvailable
        override fun isSystemVoiceInputAvailable(): Boolean = systemVoiceAvailable

        var lastPreferOffline: Boolean = false

        override fun startListening(
            sessionId: Long,
            localeTag: String,
            fallbackLocaleTag: String?,
            listener: VoiceRecognitionListener,
            preferOffline: Boolean
        ) {
            lastSessionId = sessionId
            lastLocale = localeTag
            lastFallback = fallbackLocaleTag
            lastListener = listener
            lastPreferOffline = preferOffline
        }

        override fun stopListening(sessionId: Long) {
            stoppedSessionId = sessionId
        }

        override fun cancel(sessionId: Long) {
            cancelledSessionId = sessionId
        }

        override fun launchSystemVoiceInput(): Boolean {
            systemFallbackLaunched = true
            return true
        }

        override fun destroy() = Unit
    }

    @Test
    fun manifestPermissions_declareAudioVibrateAndInternetForAiProxy() {
        val pkgInfo = context.packageManager.getPackageInfo(
            context.packageName,
            PackageManager.GET_PERMISSIONS
        )
        val perms = pkgInfo.requestedPermissions.orEmpty().toSet()
        assertTrue(Manifest.permission.RECORD_AUDIO in perms)
        assertTrue(Manifest.permission.VIBRATE in perms)
        assertTrue(Manifest.permission.INTERNET in perms)
        assertTrue(Manifest.permission.ACCESS_NETWORK_STATE in perms)
    }

    @Test
    fun voiceLocaleResolution_prefersEgyptianArabicWithGeneralArabicFallback() {
        assertEquals("ar-EG" to "ar", AndroidVoiceDictationBackend.resolveVoiceLocaleTags("ar", "ar-EG"))
        assertEquals("ar-SA" to "ar", AndroidVoiceDictationBackend.resolveVoiceLocaleTags("ar", "ar-SA"))
        assertEquals("en-US" to "en", AndroidVoiceDictationBackend.resolveVoiceLocaleTags("en-US", "ar-EG"))
    }

    @Test
    fun voiceDictation_permissionFlow_singleInsert_staleCallbackIgnored_passwordDisabled_andFallback() {
        val prefsRepo = PreferencesRepository(context)
        prefsRepo.updateSettings {
            it.copy(
                selectedSubtypeLocale = "ar",
                appLanguage = "en",
                continuousVoiceDictation = false
            )
        }
        val assetRepo = AssetRepository(context)
        val sandbox = SandboxInputTarget()
        val backend = FakeVoiceBackend().apply { permissionGranted = false }
        var permissionRequested = 0

        val controller = HeliKeyboardController(
            context = context,
            prefsRepo = prefsRepo,
            assetRepo = assetRepo,
            suggestionEngine = SuggestionEngine(),
            target = sandbox,
            voiceBackend = backend,
            onRequestAudioPermission = { permissionRequested++ }
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
                    emojiCategories = assetRepo.loadEmojiCategories(),
                    onDeleteClipboard = {},
                    onPinClipboard = {},
                    onClearUnpinnedClipboard = {}
                )
            }
        }

        // 1. Missing RECORD_AUDIO permission triggers prompt and resumes on grant
        composeRule.onNodeWithTag("toolbar_key_voice").performClick()
        composeRule.waitForIdle()
        assertEquals(1, permissionRequested)
        assertEquals(VoiceSessionStatus.PERMISSION_REQUIRED, controller.uiState.value.voice.status)
        composeRule.onNodeWithTag("voice_grant_permission_button").assertIsDisplayed()

        backend.permissionGranted = true
        controller.onVoicePermissionResult(true)
        composeRule.waitForIdle()
        assertEquals(VoiceSessionStatus.LISTENING, controller.uiState.value.voice.status)
        assertEquals("ar-EG", backend.lastLocale)
        assertEquals("ar", backend.lastFallback)
        composeRule.onNodeWithTag("voice_banner_message").assertTextContains("Listening", substring = true)

        // 2. Partial update + final result inserts ONCE at cursor without auto-sending
        val sid1 = backend.lastSessionId
        backend.lastListener?.onPartial(sid1, "أهلا")
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("voice_banner_message").assertTextContains("أهلا", substring = true)

        backend.lastListener?.onFinal(sid1, "أهلا وسهلا")
        // Duplicate late final on same session must be ignored
        backend.lastListener?.onFinal(sid1, "تكرار")
        composeRule.waitForIdle()
        assertEquals("أهلا وسهلا", sandbox.value.value.text)
        assertEquals(VoiceSessionStatus.IDLE, controller.uiState.value.voice.status)

        // 3. Cancel stops session and ignores any delayed onFinal callback
        controller.startOrStopVoiceDictation()
        val sid2 = backend.lastSessionId
        controller.cancelVoiceDictation()
        backend.lastListener?.onFinal(sid2, "لا يجب إدخاله")
        assertEquals("أهلا وسهلا", sandbox.value.value.text)

        // 4. Field change / keyboard hide invalidates active dictation session
        controller.startOrStopVoiceDictation()
        val sid3 = backend.lastSessionId
        controller.onKeyboardHidden()
        backend.lastListener?.onFinal(sid3, "بعد الإخفاء")
        assertEquals("أهلا وسهلا", sandbox.value.value.text)

        // 5. Switch writing language to English -> uses en-US while typing Arabic was previous
        prefsRepo.updateSettings { it.copy(selectedSubtypeLocale = "en-US") }
        controller.startOrStopVoiceDictation()
        assertEquals("en-US", backend.lastLocale)
        backend.lastListener?.onFinal(backend.lastSessionId, "hello")
        assertEquals("أهلا وسهلا hello", sandbox.value.value.text)

        // 6. Password field disables voice dictation
        sandbox.setPasswordInput(true)
        controller.refreshLayoutAndSuggestions()
        controller.startOrStopVoiceDictation()
        assertEquals(VoiceSessionStatus.UNAVAILABLE, controller.uiState.value.voice.status)
        assertTrue(controller.uiState.value.voice.message?.contains("password", ignoreCase = true) == true)

        // 7. Unavailable local recognition exposes System Voice fallback button cleanly
        sandbox.setPasswordInput(false)
        controller.refreshLayoutAndSuggestions()
        backend.localAvailable = false
        controller.startOrStopVoiceDictation()
        composeRule.waitForIdle()
        assertEquals(VoiceSessionStatus.UNAVAILABLE, controller.uiState.value.voice.status)
        composeRule.onNodeWithTag("voice_system_ime_button").performClick()
        composeRule.waitForIdle()
        assertTrue(backend.systemFallbackLaunched)
    }

    @Test
    fun settingsLanguage_switchesLiveBetweenArabicAndEnglish_independentOfKeyboardSubtype() {
        val app = ApplicationProvider.getApplicationContext<HeliBoardApp>()
        val vm = HeliBoardViewModel(app)
        // Start with English keyboard and English Settings UI
        vm.updateSettings { it.copy(selectedSubtypeLocale = "en-US", appLanguage = "en") }

        var observedDirection: LayoutDirection? = null
        composeRule.setContent {
            val settings by vm.settings.collectAsState()
            val baseCtx = LocalContext.current
            val (localizedCtx, localizedConfig) = remember(settings.appLanguage, baseCtx) {
                LocaleHelper.createLocalizedContext(baseCtx, settings.appLanguage)
            }
            val layoutDir = remember(settings.appLanguage) {
                LocaleHelper.resolveLayoutDirection(settings.appLanguage)
            }
            observedDirection = layoutDir

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

        // Open Settings tab
        composeRule.onNodeWithTag("nav_settings").performClick()
        composeRule.waitForIdle()
        assertEquals(LayoutDirection.Ltr, observedDirection)
        composeRule.onNodeWithText("Settings language").assertIsDisplayed()
        assertEquals("en-US", vm.prefsRepo.activeSubtype().localeTag)

        // Switch Settings language to Arabic -> UI updates to Arabic & RTL while keyboard stays en-US
        composeRule.onNodeWithTag("settings_lang_ar").performClick()
        composeRule.waitForIdle()
        assertEquals("ar", vm.settings.value.appLanguage)
        assertEquals(LayoutDirection.Rtl, observedDirection)
        composeRule.onNodeWithText("لغة واجهة الإعدادات").assertIsDisplayed()
        assertEquals("en-US", vm.prefsRepo.activeSubtype().localeTag)

        // Verify persistence across new PreferencesRepository instance
        val reloaded = PreferencesRepository(context)
        assertEquals("ar", reloaded.settings.value.appLanguage)
        assertEquals("en-US", reloaded.activeSubtype().localeTag)

        // Switch keyboard writing language to Arabic, then switch Settings back to English
        vm.selectActiveSubtype("ar")
        composeRule.onNodeWithTag("settings_lang_en").performClick()
        composeRule.waitForIdle()
        assertEquals("en", vm.settings.value.appLanguage)
        assertEquals(LayoutDirection.Ltr, observedDirection)
        composeRule.onNodeWithText("Settings language").assertIsDisplayed()
        assertEquals("ar", vm.prefsRepo.activeSubtype().localeTag)
    }
}
