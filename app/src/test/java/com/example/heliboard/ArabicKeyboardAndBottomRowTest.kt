package com.example.heliboard

import android.content.Context
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.click
import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithContentDescription
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.test.core.app.ApplicationProvider
import com.example.heliboard.data.AssetRepository
import com.example.heliboard.data.HeliSettings
import com.example.heliboard.data.ImeActionType
import com.example.heliboard.data.KeyActionType
import com.example.heliboard.data.KeyboardMode
import com.example.heliboard.data.PopupSpec
import com.example.heliboard.data.PreferencesRepository
import com.example.heliboard.data.ShiftState
import com.example.heliboard.data.SuggestionEngine
import com.example.heliboard.ime.HeliKeyboardController
import com.example.heliboard.ime.HeliKeyboardView
import com.example.heliboard.ime.SandboxInputTarget
import com.example.heliboard.ime.calculateAnchoredOverlayOffset
import com.example.heliboard.ime.hitTestPopupIndex
import com.example.heliboard.theme.HeliBoardTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [35])
class ArabicKeyboardAndBottomRowTest {

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
    fun previewBubbleMath_centersAboveKeyAndClampsAtEdges() {
        val bubbleSize = IntSize(120, 100)
        val containerWidth = 1080

        val middleRect = Rect(490f, 180f, 590f, 280f)
        val middleOffset = calculateAnchoredOverlayOffset(
            keyRect = middleRect,
            overlaySize = bubbleSize,
            containerWidth = containerWidth,
            horizontalMarginPx = 8,
            verticalGapPx = 12,
            minTopPx = -80
        )
        assertEquals(IntOffset(480, 68), middleOffset)

        val leftEdgeRect = Rect(0f, 180f, 90f, 280f)
        val leftOffset = calculateAnchoredOverlayOffset(
            keyRect = leftEdgeRect,
            overlaySize = bubbleSize,
            containerWidth = containerWidth,
            horizontalMarginPx = 8,
            verticalGapPx = 12,
            minTopPx = -80
        )
        assertEquals(8, leftOffset.x)

        val rightEdgeRect = Rect(990f, 180f, 1080f, 280f)
        val rightOffset = calculateAnchoredOverlayOffset(
            keyRect = rightEdgeRect,
            overlaySize = bubbleSize,
            containerWidth = containerWidth,
            horizontalMarginPx = 8,
            verticalGapPx = 12,
            minTopPx = -80
        )
        assertEquals(952, rightOffset.x)
    }

    @Test
    fun popupHitTest_matchesExactVisualBoundsAndCancelsOutside() {
        val itemRects = mapOf(
            0 to Rect(200f, 50f, 280f, 130f),
            1 to Rect(116f, 50f, 196f, 130f)
        )
        assertEquals(0, hitTestPopupIndex(Offset(240f, 90f), itemRects))
        assertEquals(1, hitTestPopupIndex(Offset(150f, 90f), itemRects))
        assertNull(hitTestPopupIndex(Offset(50f, 90f), itemRects))
        assertNull(hitTestPopupIndex(Offset(240f, 220f), itemRects))
    }

    @Test
    fun arabicLayoutAndPopups_matchSpecOrderHintsNumberRowAndLamAlef() {
        assertTrue(HeliSettings().showNumberRow)
        val assetRepo = AssetRepository(context)
        val layout = assetRepo.loadMainKeyboardLayout(
            layoutId = "arabic",
            localeTag = "ar",
            customLayouts = emptyMap(),
            showNumberRow = true,
            showLanguageSwitch = true,
            showEmojiKey = false,
            shiftState = ShiftState.OFF
        )

        // Row 0: Arabic digits ١..٠
        assertEquals(
            listOf("١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩", "٠"),
            layout.rows[0].keys.map { it.label }
        )
        assertTrue(layout.rows[0].keys.all { it.hintLabel == null })

        // Row 1: exact L->R visual letters & symbol hints
        val row1Expected = listOf(
            "ض" to "%", "ص" to "\\", "ث" to "|", "ق" to "=", "ف" to "[",
            "غ" to "]", "ع" to "<", "ه" to ">", "خ" to "{", "ح" to "}", "ج" to "°"
        )
        assertEquals(row1Expected.map { it.first }, layout.rows[1].keys.map { it.label })
        assertEquals(row1Expected.map { it.second }, layout.rows[1].keys.map { it.hintLabel })

        // Row 2: exact L->R visual letters & symbol hints
        val row2Expected = listOf(
            "ش" to "@", "س" to "#", "ي" to "$", "ب" to "_", "ل" to "&",
            "ا" to "-", "ت" to "+", "ن" to "(", "م" to ")", "ك" to "/", "ط" to "~"
        )
        assertEquals(row2Expected.map { it.first }, layout.rows[2].keys.map { it.label })
        assertEquals(row2Expected.map { it.second }, layout.rows[2].keys.map { it.hintLabel })

        // Row 3: exact L->R letters + Delete on right, NO Shift
        val row3 = layout.rows[3].keys
        assertFalse(row3.any { it.actionType == KeyActionType.SHIFT })
        assertEquals(KeyActionType.DELETE, row3.last().actionType)
        val row3Expected = listOf(
            "ذ" to "`", "ء" to "*", "ؤ" to "\"", "ر" to "'", "ى" to ":",
            "ة" to "؛", "و" to "!", "ز" to "؟", "ظ" to "\\", "د" to "٪"
        )
        assertEquals(row3Expected.map { it.first }, row3.dropLast(1).map { it.label })
        assertEquals(row3Expected.map { it.second }, row3.dropLast(1).map { it.hintLabel })

        // Bottom row L->R 6 keys
        val bottomRow = layout.rows[4].keys
        assertEquals(
            listOf(
                KeyActionType.SWITCH_SYMBOLS,
                KeyActionType.CHARACTER,
                KeyActionType.LANGUAGE_SWITCH,
                KeyActionType.SPACE,
                KeyActionType.CHARACTER,
                KeyActionType.ENTER
            ),
            bottomRow.map { it.actionType }
        )
        assertEquals("؟١٢٣", bottomRow[0].label)
        assertEquals("،", bottomRow[1].label)
        assertEquals("☺", bottomRow[1].hintLabel)
        assertEquals("العربية", bottomRow[3].label)
        assertEquals(".", bottomRow[4].label)

        val allKeys = layout.rows.flatMap { it.keys }
        val allReachableOutputs = (allKeys.map { it.outputText } + allKeys.flatMap { k -> k.popups.map { it.outputText } }).toSet()

        // Core Hamzas / Ta Marbuta / Alif Maqsura / Arabic punctuation
        listOf("أ", "إ", "آ", "ء", "ؤ", "ئ", "ى", "ة", "،", "؟", "؛").forEach { expected ->
            assertTrue("Expected '$expected' to be reachable in Arabic layout", expected in allReachableOutputs)
        }

        // Alif hint must NOT contain !fixedColumnOrder! directive
        val alifKey = allKeys.first { it.label == "ا" }
        assertTrue(alifKey.popups.none { it.label.startsWith("!") || it.outputText.startsWith("!") })
        assertTrue("أ" in alifKey.popups.map { it.outputText })

        // Lam key has & hint AND Lam-Alef ligature outputting connected "\u0644\u0627"
        val lamKey = allKeys.first { it.label == "ل" }
        assertEquals("ل", lamKey.outputText)
        assertEquals("&", lamKey.hintLabel)
        assertTrue(lamKey.popups.any { it.outputText == "\u0644\u0627" })

        // Tashkeel diacritics on Arabic comma key + Emoji popup shortcut
        val commaKey = bottomRow[1]
        assertTrue(commaKey.popups.any { it.actionType == KeyActionType.EMOJI })
        assertTrue(commaKey.popups.contains(PopupSpec("َ◌", "\u064E")))
        assertTrue(commaKey.popups.contains(PopupSpec("ّ◌", "\u0651")))

        // Literal brackets & slashes output exact codepoints in Arabic controller
        val prefsRepo = PreferencesRepository(context)
        prefsRepo.updateSettings { it.copy(selectedSubtypeLocale = "ar") }
        val sandbox = SandboxInputTarget()
        val controller = HeliKeyboardController(context, prefsRepo, assetRepo, SuggestionEngine(), sandbox)
        listOf("ف" to "[", "غ" to "]", "ع" to "<", "ه" to ">", "خ" to "{", "ح" to "}", "ن" to "(", "م" to ")", "ص" to "\\", "ك" to "/", "ا" to "-").forEach { (letter, sym) ->
            val key = allKeys.first { it.label == letter }
            val popup = key.popups.first { it.label == sym }
            controller.onPopupSelected(key, popup, popup.outputText)
        }
        assertEquals("[]<> {}()\\/-".replace(" ", ""), sandbox.value.value.text)
    }

    @Test
    fun englishLayoutAndShift_preserveSymbolHintsAndNumberRow() {
        val assetRepo = AssetRepository(context)
        val lower = assetRepo.loadMainKeyboardLayout(
            layoutId = "qwerty",
            localeTag = "en-US",
            customLayouts = emptyMap(),
            showNumberRow = true,
            showLanguageSwitch = true,
            showEmojiKey = false,
            shiftState = ShiftState.OFF
        )
        val shifted = assetRepo.loadMainKeyboardLayout(
            layoutId = "qwerty",
            localeTag = "en-US",
            customLayouts = emptyMap(),
            showNumberRow = true,
            showLanguageSwitch = true,
            showEmojiKey = false,
            shiftState = ShiftState.SHIFTED
        )

        // Row 0: Western digits 1..0 regardless of Shift
        val expectedDigits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        assertEquals(expectedDigits, lower.rows[0].keys.map { it.label })
        assertEquals(expectedDigits, shifted.rows[0].keys.map { it.label })

        // Row 1 Q..P symbol hints
        val row1Hints = listOf("%", "\\", "|", "=", "[", "]", "<", ">", "{", "}")
        assertEquals(listOf("q", "w", "e", "r", "t", "y", "u", "i", "o", "p"), lower.rows[1].keys.map { it.label })
        assertEquals(row1Hints, lower.rows[1].keys.map { it.hintLabel })
        assertEquals(row1Hints, shifted.rows[1].keys.map { it.hintLabel })

        // Row 2 A..L symbol hints
        val row2Hints = listOf("@", "#", "$", "_", "&", "-", "+", "(", ")")
        assertEquals(listOf("a", "s", "d", "f", "g", "h", "j", "k", "l"), lower.rows[2].keys.map { it.label })
        assertEquals(row2Hints, lower.rows[2].keys.map { it.hintLabel })

        // Row 3 Shift + Z..M (* " ' : ; ! ?) + Delete
        val row3 = lower.rows[3].keys
        assertEquals(KeyActionType.SHIFT, row3.first().actionType)
        assertEquals(KeyActionType.DELETE, row3.last().actionType)
        assertEquals(listOf("z", "x", "c", "v", "b", "n", "m"), row3.drop(1).dropLast(1).map { it.label })
        assertEquals(listOf("*", "\"", "'", ":", ";", "!", "?"), row3.drop(1).dropLast(1).map { it.hintLabel })

        // Bottom row L->R 6 keys
        val bottomRow = lower.rows[4].keys
        assertEquals("?123", bottomRow[0].label)
        assertEquals(",", bottomRow[1].label)
        assertEquals("☺", bottomRow[1].hintLabel)
        assertEquals(KeyActionType.LANGUAGE_SWITCH, bottomRow[2].actionType)
        assertEquals("English", bottomRow[3].label)
        assertEquals(".", bottomRow[4].label)
        assertEquals(KeyActionType.ENTER, bottomRow[5].actionType)
    }

    @Test
    fun bottomRow_migratesEmojiToToolbarKeepsGlobeAndExpandsSpacebar() {
        val legacyJson = """
            {
              "showEmojiKey": true,
              "showLanguageSwitchKey": false,
              "selectedSubtypeLocale": "en-US",
              "subtypes": [
                {"localeTag": "en-US", "displayName": "English (US)", "layoutId": "qwerty", "isEnabled": true},
                {"localeTag": "de-DE", "displayName": "German", "layoutId": "qwertz", "isEnabled": true}
              ]
            }
        """.trimIndent()
        context.getSharedPreferences("heliboard_prefs", Context.MODE_PRIVATE)
            .edit()
            .putString("settings_json", legacyJson)
            .commit()

        val prefsRepo = PreferencesRepository(context)
        val settings = prefsRepo.settings.value
        assertFalse(settings.showEmojiKey)
        assertTrue(settings.showNumberRow)
        assertEquals("light", settings.themeName)
        assertTrue(settings.subtypes.any { it.localeTag == "ar" && it.isEnabled })

        // Even if showLanguageSwitchKey is false, Globe stays available when >1 language enabled
        prefsRepo.updateSettings { it.copy(showLanguageSwitchKey = false) }
        val controller = HeliKeyboardController(
            context = context,
            prefsRepo = prefsRepo,
            assetRepo = AssetRepository(context),
            suggestionEngine = SuggestionEngine(),
            target = SandboxInputTarget()
        )
        val bottomKeys = controller.uiState.value.layout.rows.last().keys
        assertEquals(
            listOf(
                KeyActionType.SWITCH_SYMBOLS,
                KeyActionType.CHARACTER,
                KeyActionType.LANGUAGE_SWITCH,
                KeyActionType.SPACE,
                KeyActionType.CHARACTER,
                KeyActionType.ENTER
            ),
            bottomKeys.map { it.actionType }
        )
        val spaceKey = bottomKeys.first { it.actionType == KeyActionType.SPACE }
        assertTrue(spaceKey.weight >= 4.5f)

        // Long-press Emoji shortcut inside Comma opens Emoji panel
        val commaKey = bottomKeys[1]
        val emojiPopup = commaKey.popups.first { it.actionType == KeyActionType.EMOJI }
        controller.onPopupSelected(commaKey, emojiPopup, "")
        assertEquals(KeyboardMode.EMOJI, controller.uiState.value.mode)

        // Globe switches from en-US to ar
        val globeKey = bottomKeys.first { it.actionType == KeyActionType.LANGUAGE_SWITCH }
        controller.onKeyPress(globeKey)
        assertEquals("ar", prefsRepo.activeSubtype().localeTag)
    }

    @Test
    fun uiInteraction_arabicLamTapAndPopupSelection_dynamicActionIcon_andCompactWidth() {
        val prefsRepo = PreferencesRepository(context)
        prefsRepo.updateSettings { it.copy(selectedSubtypeLocale = "ar", longPressTimeoutMs = 200) }
        val assetRepo = AssetRepository(context)
        val sandbox = SandboxInputTarget()
        sandbox.setImeAction(ImeActionType.SEARCH)
        val controller = HeliKeyboardController(
            context = context,
            prefsRepo = prefsRepo,
            assetRepo = assetRepo,
            suggestionEngine = SuggestionEngine(),
            target = sandbox
        )

        composeRule.setContent {
            val settings by prefsRepo.settings.collectAsState()
            val uiState by controller.uiState.collectAsState()
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                HeliBoardTheme(darkTheme = false) {
                    Box(modifier = Modifier.width(320.dp)) {
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
            }
        }

        composeRule.waitForIdle()
        // Action icon reflects IME_ACTION_SEARCH
        composeRule.onNodeWithContentDescription("SEARCH").assertIsDisplayed()
        sandbox.setImeAction(ImeActionType.SEND)
        controller.refreshLayoutAndSuggestions()
        composeRule.waitForIdle()
        composeRule.onNodeWithContentDescription("SEND").assertIsDisplayed()

        // Verify preview bubble shows centered above edge key and within narrow 320dp grid bounds
        val firstArabicKey = composeRule.onNodeWithTag("key_character_ض")
        firstArabicKey.performTouchInput { down(center) }
        composeRule.waitForIdle()
        val previewBounds = composeRule.onNodeWithTag("key_preview_bubble").fetchSemanticsNode().boundsInRoot
        val gridBounds = composeRule.onNodeWithTag("keyboard_key_grid").fetchSemanticsNode().boundsInRoot
        assertTrue(previewBounds.left >= gridBounds.left - 1f)
        assertTrue(previewBounds.right <= gridBounds.right + 1f)
        firstArabicKey.performTouchInput { up() }
        composeRule.waitForIdle()
        assertEquals("ض", sandbox.value.value.text)

        // Normal tap on 'ل' inserts single 'ل'
        composeRule.onNodeWithTag("key_character_ل").performTouchInput { click() }
        composeRule.waitForIdle()
        assertEquals("ضل", sandbox.value.value.text)

        // Long-press 'ل' then tap 'ﻻ' option commits connected "\u0644\u0627" ("لا") once
        composeRule.onNodeWithTag("key_character_ل").performTouchInput {
            down(center)
            advanceEventTime(300L)
            up()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("key_popup_menu").assertIsDisplayed()
        composeRule.onNodeWithTag("popup_option_ﻻ").performClick()
        composeRule.waitForIdle()
        assertEquals("ضل\u0644\u0627", sandbox.value.value.text)

        // Long-press 'ل' then close button cancels without inserting any character
        composeRule.onNodeWithTag("key_character_ل").performTouchInput {
            down(center)
            advanceEventTime(300L)
            up()
        }
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("popup_close_button").performClick()
        composeRule.waitForIdle()
        assertTrue(composeRule.onAllNodesWithTag("key_popup_menu").fetchSemanticsNodes().isEmpty())
        assertEquals("ضل\u0644\u0627", sandbox.value.value.text)

        // Spacebar tap always inserts space; long-press on spacebar does NOT switch language
        composeRule.onNodeWithTag("key_space_العربية").performTouchInput {
            down(center)
            advanceEventTime(350L)
            up()
        }
        composeRule.waitForIdle()
        assertEquals("ar", prefsRepo.activeSubtype().localeTag)
        composeRule.onNodeWithTag("key_space_العربية").performTouchInput { click() }
        composeRule.waitForIdle()
        assertEquals("ضل\u0644\u0627  ", sandbox.value.value.text)

        // One-Handed mode: preview bubble on edge key stays inside compressed key grid bounds
        prefsRepo.updateSettings { it.copy(oneHandedMode = true, oneHandedRight = false) }
        composeRule.waitForIdle()
        val oneHandedEdgeKey = composeRule.onNodeWithTag("key_character_ج")
        oneHandedEdgeKey.performTouchInput { down(center) }
        composeRule.waitForIdle()
        val ohPreview = composeRule.onNodeWithTag("key_preview_bubble").fetchSemanticsNode().boundsInRoot
        val ohGrid = composeRule.onNodeWithTag("keyboard_key_grid").fetchSemanticsNode().boundsInRoot
        assertTrue(ohPreview.left >= ohGrid.left - 1f)
        assertTrue(ohPreview.right <= ohGrid.right + 1f)
        oneHandedEdgeKey.performTouchInput { up() }
        composeRule.waitForIdle()
        prefsRepo.updateSettings { it.copy(oneHandedMode = false) }
        composeRule.waitForIdle()

        // Standalone Emoji key is NOT on bottom row by default; toolbar & Comma->☺ open Emoji
        assertTrue(composeRule.onAllNodesWithTag("key_emoji_☺").fetchSemanticsNodes().isEmpty())
        composeRule.onNodeWithTag("toolbar_key_emoji").performClick()
        composeRule.waitForIdle()
        assertEquals(KeyboardMode.EMOJI, controller.uiState.value.mode)

        // Switch back and tap Globe to cycle to English
        controller.setMode(KeyboardMode.ALPHA)
        composeRule.waitForIdle()
        composeRule.onNodeWithTag("key_language_switch_🌐").performTouchInput { click() }
        composeRule.waitForIdle()
        assertEquals("en-US", prefsRepo.activeSubtype().localeTag)

        // English key normal tap & preview sanity check
        val englishKeyT = composeRule.onNodeWithTag("key_character_t")
        englishKeyT.performTouchInput { down(center) }
        composeRule.waitForIdle()
        val engPreviewBounds = composeRule.onNodeWithTag("key_preview_bubble").fetchSemanticsNode().boundsInRoot
        val engKeyBounds = englishKeyT.fetchSemanticsNode().boundsInRoot
        val centerDiff = kotlin.math.abs(
            ((engPreviewBounds.left + engPreviewBounds.right) / 2f) -
                ((engKeyBounds.left + engKeyBounds.right) / 2f)
        )
        assertTrue("English key preview should center horizontally above key (diff=$centerDiff)", centerDiff < 6f)
        englishKeyT.performTouchInput { up() }
        composeRule.waitForIdle()
        assertTrue(sandbox.value.value.text.endsWith("T") || sandbox.value.value.text.endsWith("t"))
        assertNotNull(sandbox.value.value.text)
    }
}
