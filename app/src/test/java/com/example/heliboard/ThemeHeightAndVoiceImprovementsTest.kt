package com.example.heliboard

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.core.app.ApplicationProvider
import com.example.heliboard.data.HeliSettings
import com.example.heliboard.data.PreferencesRepository
import com.example.heliboard.theme.HeliBoardTheme
import com.example.heliboard.theme.KeyboardThemes
import com.example.heliboard.ui.HeliBoardViewModel
import com.example.heliboard.ui.screens.PreferencesScreen
import com.example.heliboard.voice.AndroidVoiceDictationBackend
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

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ThemeHeightAndVoiceImprovementsTest {

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

    @Test
    fun gboardLightAndDarkBlackPalettes_andDayNightAutoDefault_matchSpec() {
        val defaults = HeliSettings()
        assertTrue(defaults.dayNightAuto)
        assertEquals("light", defaults.themeName)
        assertEquals("dark", defaults.nightThemeName)

        val light = KeyboardThemes.resolvePalette("light", context, systemDark = false)
        assertFalse(light.isDark)
        assertEquals(Color(0xFFEEF0F5), light.background)
        assertEquals(Color(0xFFFFFFFF), light.keyBackground)
        assertEquals(Color(0xFFDCE1EA), light.functionalKeyBackground)
        assertEquals(Color(0xFF0B57D0), light.accent)
        assertEquals(Color.White, light.actionKeyIcon)

        val dark = KeyboardThemes.resolvePalette("dark", context, systemDark = true)
        assertTrue(dark.isDark)
        assertEquals(Color(0xFF131314), dark.background)
        assertEquals(Color(0xFF2C2D30), dark.keyBackground)
        assertEquals(Color(0xFF1E1F22), dark.functionalKeyBackground)
        assertEquals(Color(0xFFA8C7FA), dark.accent)
        assertEquals(Color(0xFF062E6F), dark.actionKeyIcon)
    }

    @Test
    fun settingsMigrationV4ToV5_enablesAutomaticDayNightDarkTheme() {
        val legacyV4 = HeliSettings(dayNightAuto = false, schemaVersion = 4)
        val raw = Json { encodeDefaults = true }.encodeToString(legacyV4)
        context.getSharedPreferences("heliboard_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("settings_json", raw)
            .commit()

        val repo = PreferencesRepository(context)
        assertTrue(repo.settings.value.dayNightAuto)
        assertEquals("dark", repo.settings.value.nightThemeName)
        assertEquals(6, repo.settings.value.schemaVersion)
        assertTrue(repo.settings.value.preferOfflineVoice)
        assertTrue(repo.settings.value.continuousVoiceDictation)
    }

    @Test
    fun arabicVoiceAttempts_includeStandardRecognizerAndMultiDialectFallback() {
        val backend = AndroidVoiceDictationBackend(context)
        val attempts = backend.buildRecognitionAttempts(
            localeTag = "ar-EG",
            fallbackLocaleTag = "ar",
            standardAvailable = true,
            onDeviceAvailable = true,
            preferOffline = false
        )
        assertTrue(attempts.isNotEmpty())
        assertFalse(attempts.first().useOnDeviceRecognizer)
        assertTrue(attempts.last().useOnDeviceRecognizer)
        val locales = attempts.map { it.localeTag }
        assertTrue(locales.indexOf("ar-EG") >= 0)
        assertTrue(locales.indexOf("ar-SA") > locales.indexOf("ar-EG"))
        assertTrue(locales.indexOf("ar") > locales.indexOf("ar-SA"))

        val offlineFirst = backend.buildRecognitionAttempts(
            localeTag = "ar-EG",
            fallbackLocaleTag = "ar",
            standardAvailable = true,
            onDeviceAvailable = true,
            preferOffline = true
        )
        assertTrue(offlineFirst.isNotEmpty())
        assertTrue(offlineFirst.first().useOnDeviceRecognizer)
    }

    @Test
    fun heliBoardApp_voicePermissionHandoff_setsPendingAutoStartOnce() {
        val app = ApplicationProvider.getApplicationContext<HeliBoardApp>()
        assertFalse(app.consumePendingVoiceAutoStart())
        app.notifyVoicePermissionResult(granted = true, fromImeRequest = true)
        assertTrue(app.consumePendingVoiceAutoStart())
        assertFalse(app.consumePendingVoiceAutoStart())
    }

    @Test
    fun preferencesScreen_heightPresets_updateAndPersistKeyboardHeightScale() {
        val app = ApplicationProvider.getApplicationContext<HeliBoardApp>()
        val vm = HeliBoardViewModel(app)
        vm.updateSettings { it.copy(appLanguage = "en", keyboardHeightScale = 1.0f) }

        composeRule.setContent {
            HeliBoardTheme(darkTheme = false) {
                PreferencesScreen(viewModel = vm, onBack = {})
            }
        }

        composeRule.onNodeWithTag("pref_keyboard_height_card").assertIsDisplayed()
        composeRule.onNodeWithTag("pref_height_preset_extra_tall").performClick()
        composeRule.waitForIdle()
        assertEquals(1.35f, vm.settings.value.keyboardHeightScale, 0.01f)

        val reloaded = PreferencesRepository(context)
        assertEquals(1.35f, reloaded.settings.value.keyboardHeightScale, 0.01f)
    }
}
