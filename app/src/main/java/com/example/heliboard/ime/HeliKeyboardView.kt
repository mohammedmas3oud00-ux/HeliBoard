package com.example.heliboard.ime

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Dialpad
import androidx.compose.material.icons.filled.EmojiEmotions
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Gamepad
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.changedToUpIgnoreConsumed
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.layout
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.platform.LocalViewConfiguration
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.heliboard.R
import com.example.heliboard.data.ClipboardEntry
import com.example.heliboard.data.CustomThemeSpec
import com.example.heliboard.data.EmojiCategoryData
import com.example.heliboard.data.HeliSettings
import com.example.heliboard.data.ImeActionType
import com.example.heliboard.data.KeyActionType
import com.example.heliboard.data.KeyRow
import com.example.heliboard.data.KeySpec
import com.example.heliboard.data.KeyboardMode
import com.example.heliboard.data.PopupSpec
import com.example.heliboard.data.ShiftState
import com.example.heliboard.data.ThemeStyle
import com.example.heliboard.data.ToolbarKey
import com.example.heliboard.data.ToolbarMode
import com.example.heliboard.theme.KeyboardColorPalette
import com.example.heliboard.theme.KeyboardThemes
import com.example.heliboard.theme.NotoSansArabicFamily
import com.example.heliboard.theme.containsArabicScript
import com.example.heliboard.voice.VoiceSessionStatus
import com.example.heliboard.voice.VoiceUiState
import kotlinx.coroutines.withTimeoutOrNull
import kotlin.math.abs
import kotlin.math.roundToInt

internal data class KeyPreviewState(
    val keyId: String,
    val label: String,
    val keyRect: Rect
)

internal data class ActivePopupState(
    val key: KeySpec,
    val keyRect: Rect,
    val selectedIndex: Int? = null,
    val isTapStickyMode: Boolean = false,
    val instantCommittedOnDown: Boolean = false
)

/**
 * Computes top-left [IntOffset] for an overlay (preview bubble or popup alternatives panel)
 * horizontally centered above [keyRect] and clamped inside [0, containerWidth].
 */
internal fun calculateAnchoredOverlayOffset(
    keyRect: Rect,
    overlaySize: IntSize,
    containerWidth: Int,
    horizontalMarginPx: Int,
    verticalGapPx: Int,
    minTopPx: Int
): IntOffset {
    val desiredX = (keyRect.center.x - overlaySize.width / 2f).roundToInt()
    val maxX = (containerWidth - overlaySize.width - horizontalMarginPx).coerceAtLeast(horizontalMarginPx)
    val clampedX = desiredX.coerceIn(horizontalMarginPx, maxX)
    val desiredY = (keyRect.top - overlaySize.height - verticalGapPx).roundToInt()
    val clampedY = desiredY.coerceAtLeast(minTopPx)
    return IntOffset(clampedX, clampedY)
}

/**
 * Hit-tests pointer coordinates in grid space against rendered popup option rectangles.
 * Checks exact bounds first, then allows a minimal border tolerance to avoid dead seams
 * between neighboring popup cells. Returns null if pointer is outside all options.
 */
internal fun hitTestPopupIndex(
    pointerInGrid: Offset,
    itemRects: Map<Int, Rect>,
    seamTolerancePx: Float = 4f
): Int? {
    if (itemRects.isEmpty()) return null
    for ((idx, rect) in itemRects) {
        if (rect.contains(pointerInGrid)) return idx
    }
    var bestIdx: Int? = null
    var bestDistSq = Float.MAX_VALUE
    for ((idx, rect) in itemRects) {
        val inflated = rect.inflate(seamTolerancePx)
        if (inflated.contains(pointerInGrid)) {
            val dx = pointerInGrid.x - rect.center.x
            val dy = pointerInGrid.y - rect.center.y
            val distSq = dx * dx + dy * dy
            if (distSq < bestDistSq) {
                bestDistSq = distSq
                bestIdx = idx
            }
        }
    }
    return bestIdx
}

internal fun resolvePopupDisplayAndOutput(
    popup: PopupSpec,
    shiftState: ShiftState
): Pair<String, String> {
    val shouldUpper = shiftState != ShiftState.OFF &&
        popup.label.length == 1 &&
        popup.label[0].isLetter() &&
        !containsArabicScript(popup.label)
    val display = if (shouldUpper) popup.label.uppercase() else popup.label
    val output = if (shouldUpper && popup.outputText == popup.label) {
        popup.outputText.uppercase()
    } else {
        popup.outputText
    }
    return display to output
}

@Composable
fun HeliKeyboardView(
    controller: HeliKeyboardController,
    uiState: KeyboardUiState,
    settings: HeliSettings,
    customThemes: List<CustomThemeSpec>,
    clipboardEntries: List<ClipboardEntry>,
    emojiCategories: List<EmojiCategoryData>,
    onDeleteClipboard: (Long) -> Unit,
    onPinClipboard: (Long) -> Unit,
    onClearUnpinnedClipboard: () -> Unit,
    applyNavigationBarPadding: Boolean = false,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val systemDark = isSystemInDarkTheme()
    val useNightTheme = settings.dayNightAuto && systemDark
    val activeThemeId = if (useNightTheme) settings.nightThemeName else settings.themeName
    val palette = remember(activeThemeId, systemDark, customThemes) {
        KeyboardThemes.resolvePalette(activeThemeId, context, systemDark, customThemes)
    }

    val baseModifier = modifier
        .fillMaxWidth()
        .background(palette.background)
        .let { if (applyNavigationBarPadding) it.navigationBarsPadding() else it }

    val toolbarVisible = settings.toolbarMode != ToolbarMode.HIDDEN.id
    val showTopStrip = toolbarVisible || uiState.mode != KeyboardMode.ALPHA

    Column(modifier = baseModifier) {
        // Top Suggestion & Toolbar Strip
        if (showTopStrip) {
            TopKeyboardStrip(
                palette = palette,
                settings = settings,
                uiState = uiState,
                toolbarVisible = toolbarVisible,
                onSuggestionClick = { controller.onSuggestionSelected(it) },
                onToggleExpand = { controller.toggleToolbarExpanded() },
                onToolbarAction = { controller.onToolbarAction(it) },
                onBackToKeyboard = { controller.setMode(KeyboardMode.ALPHA) },
                onOpenToolbarCustomize = { controller.toggleToolbarCustomizePanel() }
            )
        }

        // Voice Dictation Status / Error Banner
        if (uiState.voice.status != VoiceSessionStatus.IDLE) {
            VoiceDictationBanner(
                palette = palette,
                voice = uiState.voice,
                onStop = { controller.stopVoiceDictation() },
                onTranslate = { controller.translateCurrentVoiceDictation() },
                onCancel = { controller.cancelVoiceDictation() },
                onGrantPermission = { controller.onRequestAudioPermission?.invoke() },
                onUseSystemVoice = { controller.useSystemVoiceInput() },
                onOpenOfflineSettings = { controller.openOfflineVoiceSettings() },
                onDismiss = { controller.dismissVoiceBanner() }
            )
        }

        // Sub-panels or Main Keyboard Grid
        when (uiState.mode) {
            KeyboardMode.EMOJI -> {
                EmojiPanel(
                    palette = palette,
                    settings = settings,
                    categories = emojiCategories,
                    onSelectEmoji = { controller.onEmojiSelected(it) },
                    onBackToAlpha = { controller.setMode(KeyboardMode.ALPHA) },
                    onBackspace = {
                        controller.onKeyPress(KeySpec("⌫", actionType = KeyActionType.DELETE))
                    },
                    keyboardHeightScale = settings.keyboardHeightScale
                )
            }
            KeyboardMode.CLIPBOARD -> {
                ClipboardPanel(
                    palette = palette,
                    entries = clipboardEntries,
                    isIncognito = settings.incognitoMode,
                    isSensitiveInput = uiState.isSensitiveInput,
                    feedbackMessage = uiState.clipboardFeedbackMessage,
                    onDismissFeedback = { controller.clearClipboardFeedback() },
                    onPasteEntry = { controller.onPasteClipboardEntry(it) },
                    onTogglePin = onPinClipboard,
                    onMovePinned = { from, to -> controller.movePinnedClipboard(from, to) },
                    onMovePinnedById = { id, delta -> controller.movePinnedClipboardById(id, delta) },
                    onDelete = onDeleteClipboard,
                    onClearUnpinned = onClearUnpinnedClipboard,
                    onBackToAlpha = { controller.setMode(KeyboardMode.ALPHA) },
                    keyboardHeightScale = settings.keyboardHeightScale
                )
            }
            KeyboardMode.DPAD -> {
                DpadPanel(
                    palette = palette,
                    onAction = { controller.onDpadAction(it) },
                    onUndo = { controller.onToolbarAction(ToolbarKey.UNDO) },
                    onRedo = { controller.onToolbarAction(ToolbarKey.REDO) },
                    onBackToAlpha = { controller.setMode(KeyboardMode.ALPHA) },
                    keyboardHeightScale = settings.keyboardHeightScale
                )
            }
            KeyboardMode.TOOLBAR_CUSTOMIZE -> {
                ToolbarCustomizePanel(
                    palette = palette,
                    enabledKeyNames = settings.toolbarKeys,
                    onToggleKey = { controller.toggleToolbarKey(it) },
                    onMoveKey = { from, to -> controller.moveToolbarKey(from, to) },
                    onMoveKeyByName = { keyName, delta -> controller.moveToolbarKeyByName(keyName, delta) },
                    onReset = { controller.resetToolbarKeys() },
                    onBackToAlpha = { controller.setMode(KeyboardMode.ALPHA) },
                    keyboardHeightScale = settings.keyboardHeightScale
                )
            }
            KeyboardMode.AI_WRITING -> {
                AiWritingPanel(
                    palette = palette,
                    state = uiState.aiWriting,
                    isPasswordInput = uiState.isPasswordInput,
                    cloudNoticeAccepted = settings.aiCloudNoticeAccepted,
                    onAcceptCloudNotice = { controller.acceptAiCloudNotice() },
                    onSelectOperation = { controller.setAiWritingOperation(it) },
                    onSelectTone = { controller.setAiWritingTone(it) },
                    onSelectTargetLanguage = { controller.setAiWritingTargetLanguage(it) },
                    onInputChanged = { controller.onAiWritingInputChanged(it) },
                    onPaste = { controller.pasteExplicitlyToAiWriting() },
                    onClear = { controller.clearAiWriting() },
                    onSubmit = { controller.submitAiWriting() },
                    onCancel = { controller.cancelAiWriting() },
                    onInsert = { controller.insertAiWritingText() },
                    onReplaceSelection = { controller.replaceSelectionWithAiWriting() },
                    onInsertEmailSubject = { controller.insertAiEmailSubject() },
                    onInsertEmailBody = { controller.insertAiEmailBody() },
                    onCopy = { controller.copyAiWritingText(it) },
                    onConfigureServer = { controller.openAiSettingsScreen() },
                    onBackToAlpha = { controller.setMode(KeyboardMode.ALPHA) },
                    keyboardHeightScale = settings.keyboardHeightScale
                )
            }
            KeyboardMode.AI_TRANSLATE -> {
                TranslatePanel(
                    palette = palette,
                    state = uiState.translate,
                    isPasswordInput = uiState.isPasswordInput,
                    cloudNoticeAccepted = settings.aiCloudNoticeAccepted,
                    onAcceptCloudNotice = { controller.acceptAiCloudNotice() },
                    onSelectSource = { controller.setTranslateSourceLanguage(it) },
                    onSelectTarget = { controller.setTranslateTargetLanguage(it) },
                    onSwapLanguages = { controller.swapTranslateLanguages() },
                    onInputChanged = { controller.onTranslateInputChanged(it) },
                    onPaste = { controller.pasteExplicitlyToTranslate() },
                    onClear = { controller.clearTranslateInput() },
                    onToggleAutoTranslate = { controller.setAutoTranslateEnabled(it) },
                    onSubmit = { controller.submitTranslation(isAuto = false) },
                    onCancel = { controller.cancelTranslation() },
                    onInsert = { controller.insertTranslatedText() },
                    onReplaceSelection = { controller.replaceSelectionWithTranslation() },
                    onCopy = { controller.copyTranslatedText() },
                    onConfigureServer = { controller.openAiSettingsScreen() },
                    onBackToAlpha = { controller.setMode(KeyboardMode.ALPHA) },
                    keyboardHeightScale = settings.keyboardHeightScale
                )
            }
            else -> {
                val content = @Composable {
                    KeyboardKeyGrid(
                        palette = palette,
                        settings = settings,
                        uiState = uiState,
                        toolbarVisible = showTopStrip,
                        onKeyPress = { key -> controller.onKeyPress(key) },
                        onPopupSelected = { key, popup, outputText, replaceInstant ->
                            controller.onPopupSelected(key, popup, outputText, replaceInstantPreviewChar = replaceInstant)
                        },
                        onSpaceSwipe = { steps -> controller.onSpacebarSwipe(steps) },
                        onLongPressFeedback = { controller.onKeyLongPressFeedback() }
                    )
                }

                if (settings.oneHandedMode) {
                    // Force physical LTR placement for left/right handed offset bar so
                    // oneHandedRight consistently docks the sidebar to the opposite side.
                    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (settings.oneHandedRight) {
                                OneHandedSideBar(
                                    palette = palette,
                                    isRight = true,
                                    onSwapSide = { controller.toggleOneHandedSide() },
                                    onExit = { controller.exitOneHanded() }
                                )
                            }
                            Box(modifier = Modifier.weight(1f)) {
                                content()
                            }
                            if (!settings.oneHandedRight) {
                                OneHandedSideBar(
                                    palette = palette,
                                    isRight = false,
                                    onSwapSide = { controller.toggleOneHandedSide() },
                                    onExit = { controller.exitOneHanded() }
                                )
                            }
                        }
                    }
                } else {
                    content()
                }
            }
        }
    }
}

@Composable
private fun OneHandedSideBar(
    palette: KeyboardColorPalette,
    isRight: Boolean,
    onSwapSide: () -> Unit,
    onExit: () -> Unit
) {
    Column(
        modifier = Modifier
            .width(44.dp)
            .padding(vertical = 12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(18.dp)
    ) {
        IconButton(onClick = onExit, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = Icons.Default.OpenWith,
                contentDescription = "Exit one-handed mode",
                tint = palette.keyText
            )
        }
        IconButton(onClick = onSwapSide, modifier = Modifier.size(48.dp)) {
            Icon(
                imageVector = if (isRight) Icons.Default.ChevronLeft else Icons.Default.ChevronRight,
                contentDescription = "Switch keyboard side",
                tint = palette.keyText
            )
        }
    }
}

@Composable
private fun TopKeyboardStrip(
    palette: KeyboardColorPalette,
    settings: HeliSettings,
    uiState: KeyboardUiState,
    toolbarVisible: Boolean,
    onSuggestionClick: (String) -> Unit,
    onToggleExpand: () -> Unit,
    onToolbarAction: (ToolbarKey) -> Unit,
    onBackToKeyboard: () -> Unit,
    onOpenToolbarCustomize: () -> Unit
) {
    val showToolbarOnly = settings.toolbarMode == ToolbarMode.TOOLBAR_KEYS.id ||
        (settings.toolbarMode == ToolbarMode.EXPANDABLE.id &&
            (uiState.toolbarExpanded || (uiState.currentWord.isEmpty() && uiState.suggestions.isEmpty())))

    // Side edges (Back to Keyboard & Customize Toolbar) mirror cleanly by typing language direction (RTL for Arabic, LTR otherwise)
    val stripDirection = if (settings.selectedSubtypeLocale.startsWith("ar", ignoreCase = true)) {
        LayoutDirection.Rtl
    } else {
        LayoutDirection.Ltr
    }

    CompositionLocalProvider(LocalLayoutDirection provides stripDirection) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(42.dp)
                .background(palette.stripBackground)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            if (uiState.mode != KeyboardMode.ALPHA) {
                Surface(
                    shape = CircleShape,
                    color = palette.accent,
                    modifier = Modifier
                        .size(34.dp)
                        .clickable { onBackToKeyboard() }
                        .testTag("top_strip_back_to_keyboard")
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Keyboard,
                            contentDescription = stringResource(R.string.toolbar_back_to_keyboard),
                            tint = palette.actionKeyIcon,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
                Spacer(Modifier.width(4.dp))
            }

            if (toolbarVisible && settings.toolbarMode == ToolbarMode.EXPANDABLE.id) {
                IconButton(
                    onClick = onToggleExpand,
                    modifier = Modifier
                        .size(36.dp)
                        .testTag("toolbar_expand_button")
                ) {
                    Icon(
                        imageVector = if (showToolbarOnly) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = "Toggle toolbar",
                        tint = palette.suggestionText,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (!toolbarVisible) {
                Spacer(modifier = Modifier.weight(1f))
            } else if (showToolbarOnly) {
                Row(
                    modifier = Modifier
                        .weight(1f)
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(2.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    settings.toolbarKeys.forEach { keyName ->
                        val action = runCatching { ToolbarKey.valueOf(keyName) }.getOrNull() ?: return@forEach
                        val isVoiceActive = action == ToolbarKey.VOICE &&
                            (uiState.voice.status == VoiceSessionStatus.LISTENING || uiState.voice.status == VoiceSessionStatus.PROCESSING)
                        val isActive = when (action) {
                            ToolbarKey.VOICE -> isVoiceActive
                            ToolbarKey.ONE_HANDED -> settings.oneHandedMode
                            ToolbarKey.SPLIT -> settings.splitKeyboard
                            ToolbarKey.INCOGNITO -> settings.incognitoMode
                            ToolbarKey.AUTOCORRECT -> settings.autoCorrection
                            ToolbarKey.EMOJI -> uiState.mode == KeyboardMode.EMOJI
                            ToolbarKey.CLIPBOARD -> uiState.mode == KeyboardMode.CLIPBOARD
                            ToolbarKey.DPAD -> uiState.mode == KeyboardMode.DPAD
                            ToolbarKey.NUMPAD -> uiState.mode == KeyboardMode.NUMPAD
                            ToolbarKey.AI_WRITING -> uiState.mode == KeyboardMode.AI_WRITING
                            ToolbarKey.TRANSLATE -> uiState.mode == KeyboardMode.AI_TRANSLATE
                            else -> false
                        }
                        val disabledForPassword = (action == ToolbarKey.VOICE ||
                            action == ToolbarKey.AI_WRITING ||
                            action == ToolbarKey.TRANSLATE) && uiState.isPasswordInput
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(if (isActive) palette.accent.copy(alpha = 0.22f) else Color.Transparent)
                                .clickable { onToolbarAction(action) }
                                .testTag("toolbar_key_${action.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = iconForToolbarKey(action),
                                contentDescription = localizedToolbarKeyLabel(action),
                                tint = when {
                                    disabledForPassword -> palette.keyHintText.copy(alpha = 0.38f)
                                    isActive -> palette.accent
                                    else -> palette.suggestionText
                                },
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            } else {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val list = uiState.suggestions
                    if (list.isEmpty()) {
                        Text(
                            text = if (settings.incognitoMode) stringResource(R.string.incognito_mode_label) else stringResource(R.string.app_name),
                            color = palette.keyHintText,
                            fontSize = 12.sp,
                            modifier = Modifier
                                .weight(1f)
                                .padding(horizontal = 12.dp)
                        )
                    } else {
                        Row(modifier = Modifier.weight(1f)) {
                            list.forEachIndexed { idx, word ->
                                val isAutocorrect = word == uiState.autocorrectCandidate ||
                                    (idx == 0 && uiState.autocorrectCandidate != null)
                                val useArabicFont = containsArabicScript(word)
                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(36.dp)
                                        .clip(RoundedCornerShape(8.dp))
                                        .clickable { onSuggestionClick(word) }
                                        .testTag("suggestion_chip_$idx"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Text(
                                        text = word,
                                        color = if (isAutocorrect) palette.accent else palette.suggestionText,
                                        fontFamily = if (useArabicFont) NotoSansArabicFamily else null,
                                        fontWeight = if (isAutocorrect) FontWeight.Bold else FontWeight.Medium,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }

                    // Keep AI Writing, Translate, Emoji and Mic quickly reachable even while suggestions are visible
                    if (ToolbarKey.AI_WRITING.name in settings.toolbarKeys) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    if (uiState.mode == KeyboardMode.AI_WRITING) palette.accent.copy(alpha = 0.22f)
                                    else Color.Transparent
                                )
                                .clickable { onToolbarAction(ToolbarKey.AI_WRITING) }
                                .testTag("toolbar_key_ai_writing"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AutoFixHigh,
                                contentDescription = localizedToolbarKeyLabel(ToolbarKey.AI_WRITING),
                                tint = when {
                                    uiState.isPasswordInput -> palette.keyHintText.copy(alpha = 0.38f)
                                    uiState.mode == KeyboardMode.AI_WRITING -> palette.accent
                                    else -> palette.suggestionText
                                },
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (ToolbarKey.TRANSLATE.name in settings.toolbarKeys) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(
                                    if (uiState.mode == KeyboardMode.AI_TRANSLATE) palette.accent.copy(alpha = 0.22f)
                                    else Color.Transparent
                                )
                                .clickable { onToolbarAction(ToolbarKey.TRANSLATE) }
                                .testTag("toolbar_key_translate"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = localizedToolbarKeyLabel(ToolbarKey.TRANSLATE),
                                tint = when {
                                    uiState.isPasswordInput -> palette.keyHintText.copy(alpha = 0.38f)
                                    uiState.mode == KeyboardMode.AI_TRANSLATE -> palette.accent
                                    else -> palette.suggestionText
                                },
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }
                    if (ToolbarKey.EMOJI.name in settings.toolbarKeys) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (uiState.mode == KeyboardMode.EMOJI) palette.accent.copy(alpha = 0.22f)
                                    else Color.Transparent
                                )
                                .clickable { onToolbarAction(ToolbarKey.EMOJI) }
                                .testTag("toolbar_key_emoji"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.EmojiEmotions,
                                contentDescription = localizedToolbarKeyLabel(ToolbarKey.EMOJI),
                                tint = if (uiState.mode == KeyboardMode.EMOJI) palette.accent else palette.suggestionText,
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                    if (ToolbarKey.VOICE.name in settings.toolbarKeys) {
                        val isListening = uiState.voice.status == VoiceSessionStatus.LISTENING ||
                            uiState.voice.status == VoiceSessionStatus.PROCESSING
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(CircleShape)
                                .background(
                                    if (isListening) palette.accent.copy(alpha = 0.22f)
                                    else Color.Transparent
                                )
                                .clickable { onToolbarAction(ToolbarKey.VOICE) }
                                .testTag("toolbar_key_voice"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Mic,
                                contentDescription = stringResource(R.string.voice_mic_content_desc),
                                tint = when {
                                    uiState.isPasswordInput -> palette.keyHintText.copy(alpha = 0.38f)
                                    isListening -> palette.accent
                                    else -> palette.suggestionText
                                },
                                modifier = Modifier.size(19.dp)
                            )
                        }
                    }
                }
            }

            if (toolbarVisible) {
                Spacer(Modifier.width(2.dp))
                val customizeActive = uiState.mode == KeyboardMode.TOOLBAR_CUSTOMIZE
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(if (customizeActive) palette.accent.copy(alpha = 0.22f) else Color.Transparent)
                        .clickable { onOpenToolbarCustomize() }
                        .testTag("toolbar_customize_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = stringResource(R.string.toolbar_customize_desc),
                        tint = if (customizeActive) palette.accent else palette.suggestionText,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun VoiceDictationBanner(
    palette: KeyboardColorPalette,
    voice: VoiceUiState,
    onStop: () -> Unit,
    onTranslate: () -> Unit,
    onCancel: () -> Unit,
    onGrantPermission: () -> Unit,
    onUseSystemVoice: () -> Unit,
    onOpenOfflineSettings: () -> Unit = {},
    onDismiss: () -> Unit
) {
    val isListening = voice.status == VoiceSessionStatus.LISTENING ||
        voice.status == VoiceSessionStatus.PROCESSING
    val bg = if (isListening) {
        palette.accent.copy(alpha = 0.14f)
    } else {
        palette.functionalKeyBackground
    }

    Surface(
        color = bg,
        border = BorderStroke(0.75.dp, palette.accent.copy(alpha = if (isListening) 0.45f else 0.25f)),
        shape = RoundedCornerShape(10.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 6.dp, vertical = 4.dp)
            .testTag("voice_status_banner")
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = if (isListening) Icons.Default.Mic else Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(18.dp)
                )

                Column(modifier = Modifier.weight(1f)) {
                    val primaryText = when {
                        isListening && voice.partialText.isNotBlank() -> voice.partialText
                        !voice.message.isNullOrBlank() -> voice.message
                        else -> stringResource(R.string.voice_listening)
                    }
                    Text(
                        text = primaryText,
                        color = palette.keyText,
                        fontFamily = if (containsArabicScript(primaryText)) NotoSansArabicFamily else null,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("voice_banner_message")
                    )
                    if (isListening) {
                        val sub = buildString {
                            if (voice.isOfflinePreferred) {
                                append(stringResource(R.string.voice_offline_badge))
                                if (voice.isContinuous || voice.partialText.isNotBlank()) append(" • ")
                            }
                            if (voice.partialText.isNotBlank()) {
                                append(
                                    if (voice.isContinuous) stringResource(R.string.voice_listening_continuous)
                                    else stringResource(R.string.voice_listening)
                                )
                            }
                        }
                        if (sub.isNotBlank()) {
                            Text(
                                text = sub,
                                color = palette.keyHintText,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }

                if (isListening) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = palette.keyBackground,
                        modifier = Modifier
                            .height(30.dp)
                            .clickable { onTranslate() }
                            .testTag("voice_translate_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(3.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Translate,
                                contentDescription = stringResource(R.string.voice_translate),
                                tint = palette.accent,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = stringResource(R.string.voice_translate),
                                color = palette.keyText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = palette.accent,
                        modifier = Modifier
                            .height(30.dp)
                            .clickable { onStop() }
                            .testTag("voice_stop_button")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 9.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.voice_stop),
                                color = palette.actionKeyIcon,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = palette.keyBackground,
                        modifier = Modifier
                            .height(30.dp)
                            .clickable { onCancel() }
                            .testTag("voice_cancel_button")
                    ) {
                        Box(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.voice_cancel),
                                color = palette.keyText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                } else {
                    IconButton(
                        onClick = onDismiss,
                        modifier = Modifier
                            .size(28.dp)
                            .testTag("voice_dismiss_button")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = stringResource(R.string.action_dismiss),
                            tint = palette.keyHintText,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            if (!isListening &&
                (voice.status == VoiceSessionStatus.PERMISSION_REQUIRED ||
                    voice.canOpenOfflineVoiceSettings ||
                    voice.canUseSystemVoiceInput)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (voice.status == VoiceSessionStatus.PERMISSION_REQUIRED) {
                        TextButton(
                            onClick = onGrantPermission,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = palette.accent),
                            modifier = Modifier.testTag("voice_grant_permission_button")
                        ) {
                            Text(
                                text = stringResource(R.string.voice_grant_permission),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    if (voice.canOpenOfflineVoiceSettings) {
                        TextButton(
                            onClick = onOpenOfflineSettings,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = palette.accent),
                            modifier = Modifier.testTag("voice_offline_settings_button")
                        ) {
                            Text(
                                text = stringResource(R.string.voice_offline_settings),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                    if (voice.canUseSystemVoiceInput) {
                        TextButton(
                            onClick = onUseSystemVoice,
                            contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                            colors = ButtonDefaults.textButtonColors(contentColor = palette.accent),
                            modifier = Modifier.testTag("voice_system_ime_button")
                        ) {
                            Text(
                                text = stringResource(R.string.voice_use_system),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyboardKeyGrid(
    palette: KeyboardColorPalette,
    settings: HeliSettings,
    uiState: KeyboardUiState,
    toolbarVisible: Boolean,
    onKeyPress: (KeySpec) -> Unit,
    onPopupSelected: (KeySpec, PopupSpec, String, Boolean) -> Unit,
    onSpaceSwipe: (Int) -> Unit,
    onLongPressFeedback: () -> Unit = {}
) {
    val heightScale = settings.keyboardHeightScale.coerceIn(0.70f, 1.50f)
    val rowHeight: Dp = (48f * heightScale).coerceIn(32f, 76f).dp
    val gap: Dp = (4.5f * settings.keyGapScale).coerceIn(1.5f, 9f).dp
    val density = LocalDensity.current

    val cornerRadius = when (settings.themeStyle) {
        ThemeStyle.ROUNDED.id -> 14.dp
        ThemeStyle.HOLO.id -> 3.dp
        else -> 8.dp
    }

    var gridCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var gridWidthPx by remember { mutableIntStateOf(0) }
    val keyRects = remember { mutableStateMapOf<String, Rect>() }
    val popupItemRects = remember { mutableStateMapOf<Int, Rect>() }

    var activePreview by remember { mutableStateOf<KeyPreviewState?>(null) }
    var activePopup by remember { mutableStateOf<ActivePopupState?>(null) }

    // Clear stale popup/preview states when active layout changes (e.g. switching language)
    LaunchedEffect(uiState.layout) {
        keyRects.clear()
        popupItemRects.clear()
        activePreview = null
        activePopup = null
    }

    LaunchedEffect(activePopup?.key?.keyId) {
        popupItemRects.clear()
    }

    val horizontalMarginPx = remember(density) { with(density) { 4.dp.roundToPx() } }
    val verticalGapPx = remember(density) { with(density) { 6.dp.roundToPx() } }
    val minTopPx = remember(density, toolbarVisible) {
        if (toolbarVisible) -with(density) { 40.dp.roundToPx() } else with(density) { 2.dp.roundToPx() }
    }

    // Physical key grid and overlays share LTR coordinate space so key rects and overlay
    // placement stay 1:1 synchronized across Arabic RTL system locales and one-handed mode.
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .zIndex(10f)
                .onGloballyPositioned { coords ->
                    gridCoords = coords
                    gridWidthPx = coords.size.width
                }
                .testTag("keyboard_key_grid")
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalArrangement = Arrangement.spacedBy(gap + 2.dp)
            ) {
                uiState.layout.rows.forEach { row ->
                    KeyboardRowView(
                        row = row,
                        palette = palette,
                        settings = settings,
                        shiftState = uiState.shiftState,
                        imeAction = uiState.imeAction,
                        rowHeight = rowHeight,
                        gap = gap,
                        cornerRadius = cornerRadius,
                        gridCoords = gridCoords,
                        keyRects = keyRects,
                        popupItemRects = popupItemRects,
                        onShowPreview = { keyId, label, rect ->
                            activePreview = KeyPreviewState(keyId, label, rect)
                        },
                        onHidePreview = { keyId ->
                            if (activePreview?.keyId == keyId) {
                                activePreview = null
                            }
                        },
                        onDismissActivePopup = { activePopup = null },
                        onOpenPopup = { key, rect, instantCommitted ->
                            activePreview = null
                            onLongPressFeedback()
                            activePopup = ActivePopupState(
                                key = key,
                                keyRect = rect,
                                selectedIndex = null,
                                isTapStickyMode = false,
                                instantCommittedOnDown = instantCommitted
                            )
                        },
                        onUpdatePopupHover = { selectedIdx ->
                            activePopup = activePopup?.copy(selectedIndex = selectedIdx)
                        },
                        onFinishPopupHold = { key, selectedIdx, commitHover, keepSticky ->
                            val current = activePopup
                            if (current == null || current.key.keyId != key.keyId) {
                                activePopup = null
                            } else if (commitHover && selectedIdx != null && selectedIdx in key.popups.indices) {
                                val popup = key.popups[selectedIdx]
                                val (_, output) = resolvePopupDisplayAndOutput(popup, uiState.shiftState)
                                val replaceInstant = current.instantCommittedOnDown
                                activePopup = null
                                onPopupSelected(key, popup, output, replaceInstant)
                            } else if (keepSticky) {
                                activePopup = current.copy(selectedIndex = null, isTapStickyMode = true)
                            } else {
                                activePopup = null
                            }
                        },
                        onKeyPress = { key ->
                            activePopup = null
                            onKeyPress(key)
                        },
                        onSpaceSwipe = onSpaceSwipe
                    )
                }
            }

            // Dismiss backdrop when popup is open in tap-sticky mode
            val popupState = activePopup
            if (popupState != null && popupState.isTapStickyMode) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .zIndex(20f)
                        .pointerInput(popupState.key.keyId) {
                            detectTapGestures {
                                activePopup = null
                            }
                        }
                        .testTag("popup_dismiss_backdrop")
                )
            }

            // Floating Keypress Preview Bubble anchored directly above the pressed key
            val preview = activePreview
            if (settings.keyPopupEnabled && preview != null && popupState == null && gridWidthPx > 0) {
                val isArabicPreview = containsArabicScript(preview.label)
                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = palette.popupBackground,
                    border = BorderStroke(1.dp, palette.keyHintText.copy(alpha = 0.2f)),
                    shadowElevation = 8.dp,
                    modifier = Modifier
                        .zIndex(25f)
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val pos = calculateAnchoredOverlayOffset(
                                keyRect = preview.keyRect,
                                overlaySize = IntSize(placeable.width, placeable.height),
                                containerWidth = gridWidthPx,
                                horizontalMarginPx = horizontalMarginPx,
                                verticalGapPx = verticalGapPx,
                                minTopPx = minTopPx
                            )
                            layout(placeable.width, placeable.height) {
                                placeable.place(pos.x, pos.y)
                            }
                        }
                        .testTag("key_preview_bubble")
                ) {
                    Box(
                        modifier = Modifier
                            .defaultMinSize(minWidth = 44.dp, minHeight = 46.dp)
                            .padding(horizontal = 14.dp, vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = preview.label,
                            color = palette.keyText,
                            fontFamily = if (isArabicPreview) NotoSansArabicFamily else null,
                            fontSize = (24f * settings.fontScale).coerceIn(20f, 30f).sp,
                            fontWeight = FontWeight.Bold,
                            style = TextStyle(
                                platformStyle = PlatformTextStyle(includeFontPadding = false),
                                textDirection = if (isArabicPreview) TextDirection.Rtl else TextDirection.Ltr
                            )
                        )
                    }
                }
            }

            // Floating Long-Press Alternatives Popup anchored above the pressed key
            if (popupState != null && popupState.key.popups.isNotEmpty() && gridWidthPx > 0) {
                val popups = popupState.key.popups
                val isArabicPopup = containsArabicScript(popupState.key.label) ||
                    popups.any { containsArabicScript(it.label) } ||
                    settings.selectedSubtypeLocale.startsWith("ar", ignoreCase = true)
                val maxPopupWidthDp = with(density) {
                    (gridWidthPx.toDp() - 12.dp).coerceAtLeast(140.dp)
                }
                val chunkSize = if (popups.size > 6) 6 else popups.size
                val popupRows = popups.chunked(chunkSize)

                Surface(
                    shape = RoundedCornerShape(12.dp),
                    color = palette.popupBackground,
                    border = BorderStroke(1.dp, palette.keyHintText.copy(alpha = 0.25f)),
                    shadowElevation = 10.dp,
                    modifier = Modifier
                        .zIndex(30f)
                        .widthIn(max = maxPopupWidthDp)
                        .layout { measurable, constraints ->
                            val placeable = measurable.measure(constraints)
                            val pos = calculateAnchoredOverlayOffset(
                                keyRect = popupState.keyRect,
                                overlaySize = IntSize(placeable.width, placeable.height),
                                containerWidth = gridWidthPx,
                                horizontalMarginPx = horizontalMarginPx,
                                verticalGapPx = verticalGapPx,
                                minTopPx = minTopPx
                            )
                            layout(placeable.width, placeable.height) {
                                placeable.place(pos.x, pos.y)
                            }
                        }
                        .testTag("key_popup_menu")
                ) {
                    val popupDirection = if (isArabicPopup) LayoutDirection.Rtl else LayoutDirection.Ltr
                    CompositionLocalProvider(LocalLayoutDirection provides popupDirection) {
                        Column(
                            modifier = Modifier.padding(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            var runningIdx = 0
                            popupRows.forEach { rowItems ->
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    rowItems.forEach { popup ->
                                        val globalIdx = runningIdx++
                                        val (disp, outText) = resolvePopupDisplayAndOutput(popup, uiState.shiftState)
                                        val isSelected = popupState.selectedIndex == globalIdx
                                        val itemArabic = containsArabicScript(disp)
                                        Surface(
                                            shape = RoundedCornerShape(8.dp),
                                            color = if (isSelected) palette.accent else palette.keyBackground,
                                            modifier = Modifier
                                                .defaultMinSize(minWidth = 40.dp, minHeight = 40.dp)
                                                .onGloballyPositioned { coords ->
                                                    val gCoords = gridCoords
                                                    if (gCoords != null && gCoords.isAttached && coords.isAttached) {
                                                        popupItemRects[globalIdx] = gCoords.localBoundingBoxOf(coords, clipBounds = false)
                                                    }
                                                }
                                                .let { mod ->
                                                    if (popupState.isTapStickyMode) {
                                                        mod.clickable {
                                                            val replaceInstant = popupState.instantCommittedOnDown
                                                            activePopup = null
                                                            onPopupSelected(popupState.key, popup, outText, replaceInstant)
                                                        }
                                                    } else mod
                                                }
                                                .testTag("popup_option_${popup.label}")
                                        ) {
                                            Box(
                                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                                contentAlignment = Alignment.Center
                                            ) {
                                                Text(
                                                    text = disp,
                                                    color = if (isSelected) palette.actionKeyIcon else palette.keyText,
                                                    fontFamily = if (itemArabic) NotoSansArabicFamily else null,
                                                    fontSize = (19f * settings.fontScale).coerceIn(16f, 24f).sp,
                                                    fontWeight = FontWeight.Medium,
                                                    style = TextStyle(
                                                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                                                        textDirection = if (itemArabic) TextDirection.Rtl else TextDirection.Ltr
                                                    )
                                                )
                                            }
                                        }
                                    }
                                    if (popupState.isTapStickyMode && runningIdx == popups.size) {
                                        IconButton(
                                            onClick = { activePopup = null },
                                            modifier = Modifier
                                                .size(36.dp)
                                                .testTag("popup_close_button")
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Close,
                                                contentDescription = "Close popup keys",
                                                tint = palette.keyText,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun KeyboardRowView(
    row: KeyRow,
    palette: KeyboardColorPalette,
    settings: HeliSettings,
    shiftState: ShiftState,
    imeAction: ImeActionType,
    rowHeight: Dp,
    gap: Dp,
    cornerRadius: Dp,
    gridCoords: LayoutCoordinates?,
    keyRects: MutableMap<String, Rect>,
    popupItemRects: Map<Int, Rect>,
    onShowPreview: (keyId: String, label: String, rect: Rect) -> Unit,
    onHidePreview: (keyId: String) -> Unit,
    onDismissActivePopup: () -> Unit,
    onOpenPopup: (key: KeySpec, rect: Rect, instantCommitted: Boolean) -> Unit,
    onUpdatePopupHover: (selectedIdx: Int?) -> Unit,
    onFinishPopupHold: (key: KeySpec, selectedIdx: Int?, commitHover: Boolean, keepSticky: Boolean) -> Unit,
    onKeyPress: (KeySpec) -> Unit,
    onSpaceSwipe: (Int) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(gap),
        verticalAlignment = Alignment.CenterVertically
    ) {
        val midIndex = row.keys.size / 2
        row.keys.forEachIndexed { keyIdx, key ->
            if (settings.splitKeyboard && keyIdx == midIndex && row.keys.size >= 4) {
                Spacer(modifier = Modifier.width(28.dp))
            }
            KeyButton(
                key = key,
                palette = palette,
                settings = settings,
                shiftState = shiftState,
                imeAction = imeAction,
                rowHeight = rowHeight,
                cornerRadius = cornerRadius,
                gridCoords = gridCoords,
                keyRects = keyRects,
                popupItemRects = popupItemRects,
                modifier = Modifier.weight(key.weight.coerceAtLeast(0.5f)),
                onShowPreview = onShowPreview,
                onHidePreview = onHidePreview,
                onDismissActivePopup = onDismissActivePopup,
                onOpenPopup = onOpenPopup,
                onUpdatePopupHover = onUpdatePopupHover,
                onFinishPopupHold = onFinishPopupHold,
                onKeyPress = onKeyPress,
                onSpaceSwipe = onSpaceSwipe
            )
        }
    }
}

@Composable
private fun KeyButton(
    key: KeySpec,
    palette: KeyboardColorPalette,
    settings: HeliSettings,
    shiftState: ShiftState,
    imeAction: ImeActionType,
    rowHeight: Dp,
    cornerRadius: Dp,
    gridCoords: LayoutCoordinates?,
    keyRects: MutableMap<String, Rect>,
    popupItemRects: Map<Int, Rect>,
    modifier: Modifier = Modifier,
    onShowPreview: (keyId: String, label: String, rect: Rect) -> Unit,
    onHidePreview: (keyId: String) -> Unit,
    onDismissActivePopup: () -> Unit,
    onOpenPopup: (key: KeySpec, rect: Rect, instantCommitted: Boolean) -> Unit,
    onUpdatePopupHover: (selectedIdx: Int?) -> Unit,
    onFinishPopupHold: (key: KeySpec, selectedIdx: Int?, commitHover: Boolean, keepSticky: Boolean) -> Unit,
    onKeyPress: (KeySpec) -> Unit,
    onSpaceSwipe: (Int) -> Unit
) {
    var pressed by remember(key.keyId) { mutableStateOf(false) }
    var keyCoords by remember(key.keyId) { mutableStateOf<LayoutCoordinates?>(null) }

    val currentKey by rememberUpdatedState(key)
    val currentGridCoords by rememberUpdatedState(gridCoords)
    val currentSettings by rememberUpdatedState(settings)
    val currentShiftState by rememberUpdatedState(shiftState)
    val currentPopupRects by rememberUpdatedState(popupItemRects)
    val currentOnShowPreview by rememberUpdatedState(onShowPreview)
    val currentOnHidePreview by rememberUpdatedState(onHidePreview)
    val currentOnDismissActivePopup by rememberUpdatedState(onDismissActivePopup)
    val currentOnOpenPopup by rememberUpdatedState(onOpenPopup)
    val currentOnUpdatePopupHover by rememberUpdatedState(onUpdatePopupHover)
    val currentOnFinishPopupHold by rememberUpdatedState(onFinishPopupHold)
    val currentOnKeyPress by rememberUpdatedState(onKeyPress)
    val currentOnSpaceSwipe by rememberUpdatedState(onSpaceSwipe)

    val bgColor = when {
        key.actionType == KeyActionType.ENTER -> palette.accent
        key.actionType == KeyActionType.SPACE -> palette.spaceBarBackground
        key.isFunctional -> palette.functionalKeyBackground
        settings.keyBorders -> palette.keyBackground
        else -> Color.Transparent
    }
    val effectiveBg = if (pressed) {
        if (bgColor == Color.Transparent) palette.functionalKeyBackground.copy(alpha = 0.5f)
        else bgColor.copy(alpha = 0.72f)
    } else bgColor

    val displayLabel = when (key.actionType) {
        KeyActionType.CHARACTER -> {
            if (shiftState != ShiftState.OFF && !containsArabicScript(key.label)) {
                key.shiftedLabel ?: key.label.uppercase()
            } else key.label
        }
        else -> key.label
    }

    val keyShape = if (key.actionType == KeyActionType.ENTER && settings.themeStyle != ThemeStyle.HOLO.id) {
        RoundedCornerShape(50)
    } else {
        RoundedCornerShape(cornerRadius)
    }
    val showRaisedCap = settings.keyBorders && settings.themeStyle != ThemeStyle.HOLO.id
    val lipColor = when {
        !showRaisedCap -> Color.Transparent
        key.actionType == KeyActionType.ENTER -> if (palette.isDark) Color(0xFF7094D4) else Color(0xFF073E9B)
        palette.isDark -> Color(0xFF050506)
        else -> Color(0xFFC0C5CE)
    }
    val border = when {
        !settings.keyBorders -> null
        settings.themeStyle == ThemeStyle.HOLO.id -> BorderStroke(0.5.dp, palette.keyHintText.copy(alpha = 0.25f))
        key.actionType == KeyActionType.ENTER -> null
        !palette.isDark -> BorderStroke(0.5.dp, Color(0xFF000000).copy(alpha = 0.08f))
        else -> BorderStroke(0.5.dp, Color(0xFFFFFFFF).copy(alpha = 0.07f))
    }

    val touchSlop = LocalViewConfiguration.current.touchSlop
    val showCornerHint = settings.showHints && key.hintLabel != null && key.actionType == KeyActionType.CHARACTER

    Box(
        modifier = modifier
            .height(rowHeight)
            .let { mod ->
                if (showRaisedCap) {
                    mod.background(color = lipColor, shape = keyShape)
                } else {
                    mod
                }
            }
            .onGloballyPositioned { coords ->
                keyCoords = coords
                val gCoords = currentGridCoords
                if (gCoords != null && gCoords.isAttached && coords.isAttached) {
                    keyRects[key.keyId] = gCoords.localBoundingBoxOf(coords, clipBounds = false)
                }
            }
            .pointerInput(key.keyId) {
                awaitEachGesture {
                    val down = awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                    val activeKey = currentKey
                    pressed = true
                    currentOnDismissActivePopup()

                    val kCoords = keyCoords
                    val gCoords = currentGridCoords
                    val currentKeyRect = if (kCoords != null && gCoords != null && kCoords.isAttached && gCoords.isAttached) {
                        gCoords.localBoundingBoxOf(kCoords, clipBounds = false)
                    } else {
                        keyRects[activeKey.keyId] ?: Rect(0f, 0f, size.width.toFloat(), size.height.toFloat())
                    }

                    val currentDisplayLabel = if (activeKey.actionType == KeyActionType.CHARACTER &&
                        currentShiftState != ShiftState.OFF &&
                        !containsArabicScript(activeKey.label)
                    ) {
                        activeKey.shiftedLabel ?: activeKey.label.uppercase()
                    } else activeKey.label

                    if (activeKey.actionType == KeyActionType.CHARACTER) {
                        currentOnShowPreview(activeKey.keyId, currentDisplayLabel, currentKeyRect)
                    }

                    if (activeKey.actionType == KeyActionType.SPACE) {
                        var didSwipe = false
                        var dragAccum = 0f
                        var prevX = down.position.x
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                            if (change == null || !change.pressed || change.changedToUpIgnoreConsumed()) {
                                break
                            }
                            val dx = change.position.x - prevX
                            prevX = change.position.x
                            dragAccum += dx
                            if (!didSwipe && abs(change.position.x - down.position.x) > touchSlop) {
                                didSwipe = true
                                dragAccum = change.position.x - down.position.x
                            }
                            if (didSwipe && currentSettings.spacebarSwipeCursor) {
                                change.consume()
                                val stepPx = 24f
                                if (abs(dragAccum) >= stepPx) {
                                    val steps = (dragAccum / stepPx).toInt()
                                    dragAccum -= steps * stepPx
                                    currentOnSpaceSwipe(steps)
                                }
                            }
                        }
                        pressed = false
                        // Spacebar normal tap ALWAYS inserts a space; long-press never switches language
                        if (!didSwipe) {
                            currentOnKeyPress(activeKey)
                        }
                        return@awaitEachGesture
                    }

                    val committedOnDown = currentSettings.instantKeypressCommit &&
                        activeKey.actionType == KeyActionType.CHARACTER
                    if (committedOnDown) {
                        currentOnKeyPress(activeKey)
                    }

                    val sens = currentSettings.keyPressSensitivity.coerceIn(0.20f, 1.00f)
                    val delayScale = when {
                        sens >= 0.65f -> 1.0f + ((sens - 0.65f) / 0.35f) * 0.30f
                        else -> 0.85f + ((sens - 0.20f) / 0.45f) * 0.15f
                    }
                    val timeoutMs = (currentSettings.longPressTimeoutMs * delayScale).roundToInt().toLong().coerceIn(140L, 1200L)

                    var releasedEarly = false
                    var lastPos = down.position

                    val waitResult = withTimeoutOrNull(timeoutMs) {
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                            if (change == null) {
                                releasedEarly = true
                                break
                            }
                            lastPos = change.position
                            if (change.changedToUpIgnoreConsumed() || !change.pressed) {
                                releasedEarly = true
                                break
                            }
                        }
                    }

                    if (waitResult != null) {
                        pressed = false
                        currentOnHidePreview(activeKey.keyId)
                        val draggedFarAway = abs(lastPos.x - down.position.x) > size.width * 2.5f &&
                            abs(lastPos.y - down.position.y) > size.height * 2.5f
                        if (releasedEarly && !draggedFarAway && !committedOnDown) {
                            currentOnKeyPress(activeKey)
                        }
                        return@awaitEachGesture
                    }

                    // Long press triggered while finger is still down
                    if (activeKey.popups.isNotEmpty()) {
                        currentOnHidePreview(activeKey.keyId)
                        currentOnOpenPopup(activeKey, currentKeyRect, committedOnDown)

                        var hasEnteredPopupArea = false
                        var lastHoveredIdx: Int? = null
                        var lastPointerInKey = down.position
                        var pointerReleased = false

                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                            if (change == null) {
                                break
                            }
                            lastPointerInKey = change.position
                            val activeK = keyCoords
                            val activeG = currentGridCoords
                            if (activeK != null && activeG != null && activeK.isAttached && activeG.isAttached) {
                                val pointerInGrid = activeG.localPositionOf(activeK, change.position)
                                val hitIdx = hitTestPopupIndex(pointerInGrid, currentPopupRects)
                                if (hitIdx != null) {
                                    hasEnteredPopupArea = true
                                }
                                if (hitIdx != lastHoveredIdx) {
                                    lastHoveredIdx = hitIdx
                                    currentOnUpdatePopupHover(hitIdx)
                                }
                            }
                            if (change.changedToUpIgnoreConsumed() || !change.pressed) {
                                pointerReleased = true
                                break
                            }
                            change.consume()
                        }

                        pressed = false
                        val movedFarOutsideKey = abs(lastPointerInKey.x - down.position.x) > size.width * 0.8f ||
                            abs(lastPointerInKey.y - down.position.y) > size.height * 0.8f

                        if (pointerReleased && lastHoveredIdx != null) {
                            currentOnFinishPopupHold(activeKey, lastHoveredIdx, true, false)
                        } else if (pointerReleased && !hasEnteredPopupArea && !movedFarOutsideKey) {
                            currentOnFinishPopupHold(activeKey, null, false, true)
                        } else {
                            currentOnFinishPopupHold(activeKey, null, false, false)
                        }
                    } else if (activeKey.actionType == KeyActionType.DELETE) {
                        currentOnKeyPress(activeKey)
                        while (true) {
                            val tick = withTimeoutOrNull(85L) {
                                val event = awaitPointerEvent(PointerEventPass.Initial)
                                val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                                change == null || !change.pressed || change.changedToUpIgnoreConsumed()
                            }
                            if (tick == true) break
                            if (tick == null) {
                                currentOnKeyPress(activeKey)
                            }
                        }
                        pressed = false
                    } else {
                        var releasePos = down.position
                        var released = false
                        while (true) {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            val change = event.changes.firstOrNull { it.id == down.id } ?: event.changes.firstOrNull()
                            if (change == null) break
                            releasePos = change.position
                            if (change.changedToUpIgnoreConsumed() || !change.pressed) {
                                released = true
                                break
                            }
                        }
                        pressed = false
                        currentOnHidePreview(activeKey.keyId)
                        val stillInKey = releasePos.x in (-touchSlop)..(size.width + touchSlop) &&
                            releasePos.y in (-touchSlop)..(size.height + touchSlop)
                        if (released && stillInKey && !committedOnDown) {
                            currentOnKeyPress(activeKey)
                        }
                    }
                }
            }
            .testTag("key_${key.actionType.name.lowercase()}_${key.label}")
    ) {
        Surface(
            shape = keyShape,
            color = effectiveBg,
            border = border,
            shadowElevation = if (showRaisedCap && !pressed) 1.dp else 0.dp,
            modifier = Modifier
                .fillMaxSize()
                .padding(
                    top = if (showRaisedCap && pressed) 1.dp else 0.dp,
                    bottom = if (showRaisedCap) (if (pressed) 0.5.dp else 1.75.dp) else 0.dp
                )
        ) {
            BoxWithConstraints(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center
            ) {
            val isNarrowKey = maxWidth < 32.dp
            if (showCornerHint) {
                val hint = key.hintLabel!!
                val hintIsArabic = containsArabicScript(hint)
                val hintSizeSp = (if (isNarrowKey) 8.5f else 9.5f) * settings.fontScale
                Text(
                    text = hint,
                    color = palette.keyHintText.copy(alpha = 0.94f),
                    fontFamily = if (hintIsArabic) NotoSansArabicFamily else null,
                    fontSize = hintSizeSp.coerceIn(8f, 12.5f).sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    softWrap = false,
                    style = TextStyle(
                        platformStyle = PlatformTextStyle(includeFontPadding = false),
                        textDirection = if (hintIsArabic) TextDirection.Rtl else TextDirection.Ltr
                    ),
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(
                            top = if (isNarrowKey) 1.5.dp else 2.dp,
                            end = if (isNarrowKey) 2.5.dp else 4.dp
                        )
                )
            }

            when (key.actionType) {
                KeyActionType.SHIFT -> {
                    val tint = when (shiftState) {
                        ShiftState.OFF -> palette.keyText
                        ShiftState.SHIFTED -> palette.accent
                        ShiftState.CAPS_LOCK -> palette.accent
                    }
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Shift",
                        tint = tint,
                        modifier = Modifier.size(23.dp)
                    )
                    if (shiftState != ShiftState.OFF) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 5.dp)
                                .size(width = if (shiftState == ShiftState.CAPS_LOCK) 16.dp else 8.dp, height = 2.5.dp)
                                .background(palette.accent, RoundedCornerShape(2.dp))
                        )
                    }
                }
                KeyActionType.DELETE -> {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace",
                        tint = palette.keyText,
                        modifier = Modifier.size(19.dp)
                    )
                }
                KeyActionType.ENTER -> {
                    val actionIcon = when (imeAction) {
                        ImeActionType.SEARCH -> Icons.Default.Search
                        ImeActionType.SEND -> Icons.AutoMirrored.Filled.Send
                        ImeActionType.DONE -> Icons.Default.Check
                        ImeActionType.NEXT -> Icons.AutoMirrored.Filled.ArrowForward
                        ImeActionType.GO -> Icons.AutoMirrored.Filled.ArrowForward
                        ImeActionType.PREVIOUS -> Icons.AutoMirrored.Filled.ArrowBack
                        ImeActionType.ENTER -> Icons.AutoMirrored.Filled.KeyboardReturn
                    }
                    Icon(
                        imageVector = actionIcon,
                        contentDescription = imeAction.name,
                        tint = palette.actionKeyIcon,
                        modifier = Modifier.size(20.dp)
                    )
                }
                KeyActionType.LANGUAGE_SWITCH -> {
                    Icon(
                        imageVector = Icons.Default.Language,
                        contentDescription = "Switch language",
                        tint = palette.keyText,
                        modifier = Modifier.size(19.dp)
                    )
                }
                KeyActionType.EMOJI -> {
                    Icon(
                        imageVector = Icons.Default.EmojiEmotions,
                        contentDescription = "Emoji panel",
                        tint = palette.keyText,
                        modifier = Modifier.size(19.dp)
                    )
                }
                KeyActionType.SPACE -> {
                    val spaceText = if (settings.incognitoMode) stringResource(R.string.incognito_mode_label) else displayLabel
                    val spaceArabic = containsArabicScript(spaceText)
                    Text(
                        text = spaceText,
                        color = palette.spaceBarText,
                        fontFamily = if (spaceArabic) NotoSansArabicFamily else null,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        style = TextStyle(
                            platformStyle = PlatformTextStyle(includeFontPadding = false),
                            textDirection = if (spaceArabic) TextDirection.Rtl else TextDirection.Ltr
                        )
                    )
                }
                else -> {
                    val labelArabic = containsArabicScript(displayLabel)
                    val baseSize = when {
                        key.isFunctional && displayLabel.length > 2 -> 12.5f
                        key.isFunctional -> 15f
                        isNarrowKey -> 16.5f
                        else -> 18.5f
                    }
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(
                                top = if (showCornerHint) (if (isNarrowKey) 6.dp else 7.dp) else 0.dp,
                                start = 1.5.dp,
                                end = 1.5.dp
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = displayLabel,
                            color = palette.keyText,
                            fontFamily = if (labelArabic) NotoSansArabicFamily else null,
                            fontSize = (baseSize * settings.fontScale).sp,
                            fontWeight = if (key.isFunctional) FontWeight.SemiBold else FontWeight.Medium,
                            maxLines = 1,
                            textAlign = TextAlign.Center,
                            style = TextStyle(
                                platformStyle = PlatformTextStyle(includeFontPadding = false),
                                textDirection = if (labelArabic) TextDirection.Rtl else TextDirection.Ltr
                            )
                        )
                    }
                }
            }
        }
        }
    }
}

fun iconForToolbarKey(key: ToolbarKey): ImageVector = when (key) {
    ToolbarKey.VOICE -> Icons.Default.Mic
    ToolbarKey.EMOJI -> Icons.Default.EmojiEmotions
    ToolbarKey.CLIPBOARD -> Icons.Default.ContentPaste
    ToolbarKey.NUMPAD -> Icons.Default.Dialpad
    ToolbarKey.DPAD -> Icons.Default.Gamepad
    ToolbarKey.UNDO -> Icons.AutoMirrored.Filled.Undo
    ToolbarKey.REDO -> Icons.AutoMirrored.Filled.Redo
    ToolbarKey.SELECT_ALL -> Icons.Default.SelectAll
    ToolbarKey.COPY -> Icons.Default.ContentCopy
    ToolbarKey.CUT -> Icons.Default.ContentCut
    ToolbarKey.PASTE -> Icons.Default.ContentPaste
    ToolbarKey.ONE_HANDED -> Icons.Default.Smartphone
    ToolbarKey.SPLIT -> Icons.Default.Splitscreen
    ToolbarKey.INCOGNITO -> Icons.Default.VisibilityOff
    ToolbarKey.AUTOCORRECT -> Icons.Default.AutoFixHigh
    ToolbarKey.CLEAR_CLIPBOARD -> Icons.Default.DeleteSweep
    ToolbarKey.LEFT -> Icons.AutoMirrored.Filled.ArrowBack
    ToolbarKey.RIGHT -> Icons.AutoMirrored.Filled.ArrowForward
    ToolbarKey.SETTINGS -> Icons.Default.Settings
    ToolbarKey.AI_WRITING -> Icons.Default.AutoFixHigh
    ToolbarKey.TRANSLATE -> Icons.Default.Translate
}

@Composable
fun localizedToolbarKeyLabel(key: ToolbarKey): String = when (key) {
    ToolbarKey.VOICE -> stringResource(R.string.toolbar_key_voice)
    ToolbarKey.EMOJI -> stringResource(R.string.toolbar_key_emoji)
    ToolbarKey.CLIPBOARD -> stringResource(R.string.toolbar_key_clipboard)
    ToolbarKey.NUMPAD -> stringResource(R.string.toolbar_key_numpad)
    ToolbarKey.DPAD -> stringResource(R.string.toolbar_key_dpad)
    ToolbarKey.UNDO -> stringResource(R.string.toolbar_key_undo)
    ToolbarKey.REDO -> stringResource(R.string.toolbar_key_redo)
    ToolbarKey.SELECT_ALL -> stringResource(R.string.toolbar_key_select_all)
    ToolbarKey.COPY -> stringResource(R.string.toolbar_key_copy)
    ToolbarKey.CUT -> stringResource(R.string.toolbar_key_cut)
    ToolbarKey.PASTE -> stringResource(R.string.toolbar_key_paste)
    ToolbarKey.ONE_HANDED -> stringResource(R.string.toolbar_key_one_handed)
    ToolbarKey.SPLIT -> stringResource(R.string.toolbar_key_split)
    ToolbarKey.INCOGNITO -> stringResource(R.string.toolbar_key_incognito)
    ToolbarKey.AUTOCORRECT -> stringResource(R.string.toolbar_key_autocorrect)
    ToolbarKey.CLEAR_CLIPBOARD -> stringResource(R.string.toolbar_key_clear_clipboard)
    ToolbarKey.LEFT -> stringResource(R.string.toolbar_key_left)
    ToolbarKey.RIGHT -> stringResource(R.string.toolbar_key_right)
    ToolbarKey.SETTINGS -> stringResource(R.string.toolbar_key_settings)
    ToolbarKey.AI_WRITING -> stringResource(R.string.toolbar_key_ai_writing)
    ToolbarKey.TRANSLATE -> stringResource(R.string.toolbar_key_translate)
}
