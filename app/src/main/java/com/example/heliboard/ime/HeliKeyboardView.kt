package com.example.heliboard.ime

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.KeyboardReturn
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.AutoFixHigh
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
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.OpenWith
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Smartphone
import androidx.compose.material.icons.filled.Splitscreen
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.heliboard.data.ClipboardEntry
import com.example.heliboard.data.CustomThemeSpec
import com.example.heliboard.data.EmojiCategoryData
import com.example.heliboard.data.HeliSettings
import com.example.heliboard.data.KeyActionType
import com.example.heliboard.data.KeyRow
import com.example.heliboard.data.KeySpec
import com.example.heliboard.data.KeyboardMode
import com.example.heliboard.data.ShiftState
import com.example.heliboard.data.ThemeStyle
import com.example.heliboard.data.ToolbarKey
import com.example.heliboard.data.ToolbarMode
import com.example.heliboard.theme.KeyboardColorPalette
import com.example.heliboard.theme.KeyboardThemes

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

    var activePopupKey by remember { mutableStateOf<KeySpec?>(null) }
    var previewLabel by remember { mutableStateOf<String?>(null) }

    val baseModifier = modifier
        .fillMaxWidth()
        .background(palette.background)
        .let { if (applyNavigationBarPadding) it.navigationBarsPadding() else it }

    Column(modifier = baseModifier) {
        // Popup accent selector strip when long-pressing a key
        AnimatedVisibility(visible = activePopupKey != null && activePopupKey!!.popups.isNotEmpty()) {
            val popups = activePopupKey?.popups.orEmpty()
            Surface(
                color = palette.popupBackground,
                shadowElevation = 6.dp,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    popups.forEach { ch ->
                        val out = if (uiState.shiftState != ShiftState.OFF && ch.length == 1 && ch[0].isLetter()) {
                            ch.uppercase()
                        } else ch
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = palette.keyBackground,
                            modifier = Modifier
                                .height(38.dp)
                                .clickable {
                                    controller.onKeyPress(activePopupKey!!, customOutput = out)
                                    activePopupKey = null
                                }
                        ) {
                            Box(
                                modifier = Modifier.padding(horizontal = 14.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = out,
                                    color = palette.keyText,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(
                        onClick = { activePopupKey = null },
                        modifier = Modifier.size(34.dp)
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

        // Top Suggestion & Toolbar Strip
        if (settings.toolbarMode != ToolbarMode.HIDDEN.id) {
            TopKeyboardStrip(
                palette = palette,
                settings = settings,
                uiState = uiState,
                onSuggestionClick = { controller.onSuggestionSelected(it) },
                onToggleExpand = { controller.toggleToolbarExpanded() },
                onToolbarAction = { controller.onToolbarAction(it) }
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
                    }
                )
            }
            KeyboardMode.CLIPBOARD -> {
                ClipboardPanel(
                    palette = palette,
                    entries = clipboardEntries,
                    onPaste = { controller.onPasteClip(it) },
                    onTogglePin = onPinClipboard,
                    onDelete = onDeleteClipboard,
                    onClearUnpinned = onClearUnpinnedClipboard,
                    onBackToAlpha = { controller.setMode(KeyboardMode.ALPHA) }
                )
            }
            KeyboardMode.DPAD -> {
                DpadPanel(
                    palette = palette,
                    onAction = { controller.onDpadAction(it) },
                    onUndo = { controller.onToolbarAction(ToolbarKey.UNDO) },
                    onRedo = { controller.onToolbarAction(ToolbarKey.REDO) },
                    onBackToAlpha = { controller.setMode(KeyboardMode.ALPHA) }
                )
            }
            else -> {
                val content = @Composable {
                    KeyboardKeyGrid(
                        palette = palette,
                        settings = settings,
                        uiState = uiState,
                        previewLabel = previewLabel,
                        onPreviewChange = { previewLabel = it },
                        onKeyPress = { key ->
                            activePopupKey = null
                            controller.onKeyPress(key)
                        },
                        onKeyLongPress = { key ->
                            if (key.popups.isNotEmpty()) {
                                activePopupKey = key
                            } else if (key.actionType == KeyActionType.SPACE) {
                                controller.onKeyPress(KeySpec("🌐", actionType = KeyActionType.LANGUAGE_SWITCH))
                            }
                        },
                        onSpaceSwipe = { steps -> controller.onSpacebarSwipe(steps) }
                    )
                }

                if (settings.oneHandedMode) {
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
        IconButton(onClick = onExit, modifier = Modifier.size(38.dp)) {
            Icon(
                imageVector = Icons.Default.OpenWith,
                contentDescription = "Exit one-handed mode",
                tint = palette.keyText
            )
        }
        IconButton(onClick = onSwapSide, modifier = Modifier.size(38.dp)) {
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
    onSuggestionClick: (String) -> Unit,
    onToggleExpand: () -> Unit,
    onToolbarAction: (ToolbarKey) -> Unit
) {
    val showToolbarOnly = settings.toolbarMode == ToolbarMode.TOOLBAR_KEYS.id ||
        (settings.toolbarMode == ToolbarMode.EXPANDABLE.id &&
            (uiState.toolbarExpanded || (uiState.currentWord.isEmpty() && uiState.suggestions.isEmpty())))

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(42.dp)
            .background(palette.stripBackground)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (settings.toolbarMode == ToolbarMode.EXPANDABLE.id) {
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

        if (showToolbarOnly) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(2.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                settings.toolbarKeys.forEach { keyName ->
                    val action = runCatching { ToolbarKey.valueOf(keyName) }.getOrNull() ?: return@forEach
                    val isActive = when (action) {
                        ToolbarKey.ONE_HANDED -> settings.oneHandedMode
                        ToolbarKey.SPLIT -> settings.splitKeyboard
                        ToolbarKey.INCOGNITO -> settings.incognitoMode
                        ToolbarKey.AUTOCORRECT -> settings.autoCorrection
                        ToolbarKey.EMOJI -> uiState.mode == KeyboardMode.EMOJI
                        ToolbarKey.CLIPBOARD -> uiState.mode == KeyboardMode.CLIPBOARD
                        ToolbarKey.DPAD -> uiState.mode == KeyboardMode.DPAD
                        ToolbarKey.NUMPAD -> uiState.mode == KeyboardMode.NUMPAD
                        else -> false
                    }
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
                            contentDescription = action.label,
                            tint = if (isActive) palette.accent else palette.suggestionText,
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
                        text = if (settings.incognitoMode) "Incognito Mode" else "HeliBoard",
                        color = palette.keyHintText,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 12.dp)
                    )
                } else {
                    list.forEachIndexed { idx, word ->
                        val isAutocorrect = word == uiState.autocorrectCandidate ||
                            (idx == 0 && uiState.autocorrectCandidate != null)
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
                                fontWeight = if (isAutocorrect) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 14.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
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
    previewLabel: String?,
    onPreviewChange: (String?) -> Unit,
    onKeyPress: (KeySpec) -> Unit,
    onKeyLongPress: (KeySpec) -> Unit,
    onSpaceSwipe: (Int) -> Unit
) {
    val rowHeight: Dp = (46f * settings.keyboardHeightScale).coerceIn(32f, 68f).dp
    val gap: Dp = (4.5f * settings.keyGapScale).coerceIn(1.5f, 9f).dp

    val cornerRadius = when (settings.themeStyle) {
        ThemeStyle.ROUNDED.id -> 14.dp
        ThemeStyle.HOLO.id -> 3.dp
        else -> 7.dp
    }

    Box(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 6.dp),
            verticalArrangement = Arrangement.spacedBy(gap + 2.dp)
        ) {
            uiState.layout.rows.forEachIndexed { rowIdx, row ->
                KeyboardRowView(
                    row = row,
                    rowIndex = rowIdx,
                    palette = palette,
                    settings = settings,
                    shiftState = uiState.shiftState,
                    rowHeight = rowHeight,
                    gap = gap,
                    cornerRadius = cornerRadius,
                    onPreviewChange = onPreviewChange,
                    onKeyPress = onKeyPress,
                    onKeyLongPress = onKeyLongPress,
                    onSpaceSwipe = onSpaceSwipe
                )
            }
        }

        // Key press popup preview badge
        if (settings.keyPopupEnabled && previewLabel != null) {
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = palette.popupBackground,
                shadowElevation = 6.dp,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = 2.dp)
            ) {
                Text(
                    text = previewLabel,
                    color = palette.keyText,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
private fun KeyboardRowView(
    row: KeyRow,
    rowIndex: Int,
    palette: KeyboardColorPalette,
    settings: HeliSettings,
    shiftState: ShiftState,
    rowHeight: Dp,
    gap: Dp,
    cornerRadius: Dp,
    onPreviewChange: (String?) -> Unit,
    onKeyPress: (KeySpec) -> Unit,
    onKeyLongPress: (KeySpec) -> Unit,
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
                rowHeight = rowHeight,
                cornerRadius = cornerRadius,
                modifier = Modifier.weight(key.weight.coerceAtLeast(0.5f)),
                onPreviewChange = onPreviewChange,
                onKeyPress = onKeyPress,
                onKeyLongPress = onKeyLongPress,
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
    rowHeight: Dp,
    cornerRadius: Dp,
    modifier: Modifier = Modifier,
    onPreviewChange: (String?) -> Unit,
    onKeyPress: (KeySpec) -> Unit,
    onKeyLongPress: (KeySpec) -> Unit,
    onSpaceSwipe: (Int) -> Unit
) {
    var pressed by remember { mutableStateOf(false) }
    var dragAccum by remember { mutableFloatStateOf(0f) }

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
            if (shiftState != ShiftState.OFF) {
                key.shiftedLabel ?: key.label.uppercase()
            } else key.label
        }
        else -> key.label
    }

    val border = if (settings.keyBorders && settings.themeStyle == ThemeStyle.HOLO.id) {
        BorderStroke(0.5.dp, palette.keyHintText.copy(alpha = 0.25f))
    } else null

    val spaceModifier = if (key.actionType == KeyActionType.SPACE) {
        Modifier.pointerInput(key) {
            detectDragGestures(
                onDragStart = { dragAccum = 0f },
                onDragEnd = { dragAccum = 0f },
                onDragCancel = { dragAccum = 0f },
                onDrag = { change, dragAmount ->
                    change.consume()
                    dragAccum += dragAmount.x
                    val threshold = 24f
                    if (kotlin.math.abs(dragAccum) >= threshold) {
                        val steps = (dragAccum / threshold).toInt()
                        dragAccum -= steps * threshold
                        onSpaceSwipe(steps)
                    }
                }
            )
        }
    } else Modifier

    Surface(
        shape = RoundedCornerShape(cornerRadius),
        color = effectiveBg,
        border = border,
        shadowElevation = if (settings.keyBorders && settings.themeStyle != ThemeStyle.HOLO.id) 1.dp else 0.dp,
        modifier = modifier
            .height(rowHeight)
            .then(spaceModifier)
            .pointerInput(key, shiftState) {
                detectTapGestures(
                    onPress = {
                        pressed = true
                        if (key.actionType == KeyActionType.CHARACTER) {
                            onPreviewChange(displayLabel)
                        }
                        tryAwaitRelease()
                        pressed = false
                        onPreviewChange(null)
                    },
                    onLongPress = {
                        onPreviewChange(null)
                        onKeyLongPress(key)
                    },
                    onTap = {
                        onKeyPress(key)
                    }
                )
            }
            .testTag("key_${key.actionType.name.lowercase()}_${key.label}")
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            if (settings.showHints && key.hintLabel != null && key.actionType == KeyActionType.CHARACTER) {
                Text(
                    text = key.hintLabel,
                    color = palette.keyHintText,
                    fontSize = (9f * settings.fontScale).sp,
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .padding(top = 2.dp, end = 4.dp)
                )
            }

            when (key.actionType) {
                KeyActionType.SHIFT -> {
                    val tint = if (shiftState == ShiftState.CAPS_LOCK) palette.accent else palette.keyText
                    Icon(
                        imageVector = Icons.Default.KeyboardArrowUp,
                        contentDescription = "Shift",
                        tint = tint,
                        modifier = Modifier.size(22.dp)
                    )
                    if (shiftState == ShiftState.CAPS_LOCK) {
                        Box(
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .padding(bottom = 5.dp)
                                .size(width = 14.dp, height = 2.5.dp)
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
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.KeyboardReturn,
                        contentDescription = "Enter",
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
                    Text(
                        text = if (settings.incognitoMode) "Incognito" else displayLabel,
                        color = palette.spaceBarText,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                else -> {
                    Text(
                        text = displayLabel,
                        color = palette.keyText,
                        fontSize = ((if (key.isFunctional) 13f else 18f) * settings.fontScale).sp,
                        fontWeight = if (key.isFunctional) FontWeight.SemiBold else FontWeight.Normal,
                        maxLines = 1
                    )
                }
            }
        }
    }
}

fun iconForToolbarKey(key: ToolbarKey): ImageVector = when (key) {
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
}
