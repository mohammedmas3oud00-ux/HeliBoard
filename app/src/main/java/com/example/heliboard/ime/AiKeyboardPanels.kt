package com.example.heliboard.ime

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.heliboard.R
import com.example.heliboard.ai.AiLanguageOption
import com.example.heliboard.ai.AiProxyErrorType
import com.example.heliboard.ai.AiTone
import com.example.heliboard.ai.AiWritingOperation
import com.example.heliboard.theme.KeyboardColorPalette
import com.example.heliboard.theme.NotoSansArabicFamily
import com.example.heliboard.theme.containsArabicScript

@Composable
fun AiWritingPanel(
    palette: KeyboardColorPalette,
    state: AiWritingUiState,
    isPasswordInput: Boolean,
    cloudNoticeAccepted: Boolean,
    onAcceptCloudNotice: () -> Unit,
    onSelectOperation: (AiWritingOperation) -> Unit,
    onSelectTone: (AiTone) -> Unit,
    onSelectTargetLanguage: (AiLanguageOption) -> Unit,
    onInputChanged: (String) -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    onInsert: () -> Unit,
    onReplaceSelection: () -> Unit,
    onInsertEmailSubject: () -> Unit,
    onInsertEmailBody: () -> Unit,
    onCopy: (String?) -> Unit,
    onConfigureServer: () -> Unit,
    onBackToAlpha: () -> Unit,
    keyboardHeightScale: Float = 1f
) {
    val scaledHeight = (312f * keyboardHeightScale.coerceIn(0.70f, 1.50f)).coerceIn(240f, 420f).dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(scaledHeight)
            .background(palette.background)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("ai_writing_panel")
    ) {
        PanelHeader(
            palette = palette,
            icon = Icons.Default.AutoFixHigh,
            title = stringResource(R.string.ai_writing_title),
            backTestTag = "ai_writing_back_button",
            onBackToAlpha = onBackToAlpha,
            onOpenSettings = onConfigureServer
        )

        if (isPasswordInput) {
            PasswordBlockedNotice(palette = palette)
            return
        }

        if (!cloudNoticeAccepted) {
            CloudDisclosureCard(
                palette = palette,
                onAccept = onAcceptCloudNotice,
                acceptTag = "ai_disclosure_accept_button"
            )
            return
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Operation selector chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val ops = listOf(
                    AiWritingOperation.PROOFREAD to R.string.ai_op_proofread,
                    AiWritingOperation.REWRITE to R.string.ai_op_rewrite,
                    AiWritingOperation.COMPOSE_MESSAGE to R.string.ai_op_compose_message,
                    AiWritingOperation.COMPOSE_EMAIL to R.string.ai_op_compose_email
                )
                ops.forEach { (op, labelRes) ->
                    PanelChip(
                        palette = palette,
                        label = stringResource(labelRes),
                        selected = state.operation == op,
                        onClick = { onSelectOperation(op) },
                        testTag = "ai_op_${op.apiValue}"
                    )
                }
            }

            // Tone & Target Language controls
            if (state.operation == AiWritingOperation.REWRITE || state.operation == AiWritingOperation.COMPOSE_EMAIL) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    val tones = listOf(
                        AiTone.SAME to R.string.ai_tone_same,
                        AiTone.FORMAL to R.string.ai_tone_formal,
                        AiTone.FRIENDLY to R.string.ai_tone_friendly,
                        AiTone.CONCISE to R.string.ai_tone_concise
                    )
                    tones.forEach { (tone, labelRes) ->
                        PanelChip(
                            palette = palette,
                            label = stringResource(labelRes),
                            selected = state.tone == tone,
                            onClick = { onSelectTone(tone) },
                            testTag = "ai_tone_${tone.apiValue}"
                        )
                    }
                }
            }

            if (state.operation == AiWritingOperation.COMPOSE_MESSAGE || state.operation == AiWritingOperation.COMPOSE_EMAIL) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val langs = listOf(
                        AiLanguageOption.AUTO to R.string.ai_lang_auto,
                        AiLanguageOption.AR to R.string.ai_lang_ar,
                        AiLanguageOption.EN to R.string.ai_lang_en
                    )
                    langs.forEach { (lang, labelRes) ->
                        PanelChip(
                            palette = palette,
                            label = stringResource(labelRes),
                            selected = state.targetLanguage == lang,
                            onClick = { onSelectTargetLanguage(lang) },
                            testTag = "ai_target_lang_${lang.apiValue}"
                        )
                    }
                }
            }

            if (state.loadedFromSelection) {
                Text(
                    text = stringResource(R.string.ai_selection_loaded_badge),
                    color = palette.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("ai_selection_loaded_badge")
                )
            }

            // Editable Input Box + Action Row
            val placeholder = if (state.operation == AiWritingOperation.COMPOSE_MESSAGE ||
                state.operation == AiWritingOperation.COMPOSE_EMAIL
            ) {
                stringResource(R.string.ai_input_placeholder_compose)
            } else {
                stringResource(R.string.ai_input_placeholder_proofread)
            }

            PanelInputCard(
                palette = palette,
                value = state.inputText,
                placeholder = placeholder,
                onValueChange = onInputChanged,
                inputTag = "ai_writing_input",
                onPaste = onPaste,
                pasteTag = "ai_writing_paste_button",
                onClear = onClear,
                clearTag = "ai_writing_clear_button"
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = palette.accent,
                    modifier = Modifier
                        .height(34.dp)
                        .clickable(enabled = !state.isLoading) { onSubmit() }
                        .testTag("ai_writing_submit_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Send,
                            contentDescription = null,
                            tint = palette.actionKeyIcon,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.ai_action_run),
                            color = palette.actionKeyIcon,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (state.isLoading) {
                LoadingBarCard(
                    palette = palette,
                    label = stringResource(R.string.ai_loading_writing),
                    cancelTag = "ai_writing_cancel_button",
                    onCancel = onCancel
                )
            }

            if (!state.errorMessage.isNullOrBlank()) {
                ErrorCard(
                    palette = palette,
                    message = state.errorMessage,
                    showConfigure = state.errorType == AiProxyErrorType.UNCONFIGURED ||
                        state.errorType == AiProxyErrorType.INVALID_URL ||
                        state.errorType == AiProxyErrorType.UNAUTHORIZED,
                    retryTag = "ai_writing_retry_button",
                    configureTag = "ai_configure_server_button",
                    onRetry = onSubmit,
                    onConfigureServer = onConfigureServer
                )
            }

            val res = state.result
            if (res != null) {
                if (state.isFieldChanged) {
                    FieldChangedWarning(palette = palette, testTag = "ai_writing_field_changed_banner")
                }

                if (state.operation == AiWritingOperation.COMPOSE_EMAIL &&
                    (res.subject.isNotEmpty() || res.body.isNotEmpty())
                ) {
                    // Email subject card
                    ResultCard(
                        palette = palette,
                        header = stringResource(R.string.ai_email_subject_label),
                        bodyText = res.subject,
                        previewTag = "ai_email_subject_preview"
                    ) {
                        if (!state.isFieldChanged && res.subject.isNotEmpty()) {
                            ActionChipButton(
                                palette = palette,
                                label = stringResource(R.string.ai_email_insert_subject),
                                primary = true,
                                testTag = "ai_email_insert_subject_button",
                                onClick = onInsertEmailSubject
                            )
                        }
                        ActionChipButton(
                            palette = palette,
                            label = stringResource(R.string.ai_email_copy_subject),
                            primary = false,
                            testTag = "ai_email_copy_subject_button",
                            onClick = { onCopy(res.subject) }
                        )
                    }

                    // Email body card
                    ResultCard(
                        palette = palette,
                        header = stringResource(R.string.ai_email_body_label),
                        bodyText = res.body,
                        previewTag = "ai_email_body_preview"
                    ) {
                        if (!state.isFieldChanged && res.body.isNotEmpty()) {
                            ActionChipButton(
                                palette = palette,
                                label = stringResource(R.string.ai_email_insert_body),
                                primary = true,
                                testTag = "ai_email_insert_body_button",
                                onClick = onInsertEmailBody
                            )
                        }
                        ActionChipButton(
                            palette = palette,
                            label = stringResource(R.string.ai_email_copy_body),
                            primary = false,
                            testTag = "ai_email_copy_body_button",
                            onClick = { onCopy(res.body) }
                        )
                    }

                    Text(
                        text = stringResource(R.string.ai_email_placeholder_hint),
                        color = palette.keyHintText,
                        fontSize = 10.sp
                    )
                } else {
                    ResultCard(
                        palette = palette,
                        header = null,
                        bodyText = res.text,
                        previewTag = "ai_writing_result_preview"
                    ) {
                        if (!state.isFieldChanged) {
                            ActionChipButton(
                                palette = palette,
                                label = stringResource(R.string.ai_action_insert),
                                primary = true,
                                testTag = "ai_writing_insert_button",
                                onClick = onInsert
                            )
                            if (state.canReplaceSelection) {
                                ActionChipButton(
                                    palette = palette,
                                    label = stringResource(R.string.ai_action_replace_selection),
                                    primary = false,
                                    testTag = "ai_writing_replace_button",
                                    onClick = onReplaceSelection
                                )
                            }
                        }
                        ActionChipButton(
                            palette = palette,
                            label = stringResource(
                                if (state.isFieldChanged) R.string.ai_action_copy_only else R.string.ai_action_copy
                            ),
                            primary = false,
                            testTag = "ai_writing_copy_button",
                            onClick = { onCopy(res.text) }
                        )
                        ActionChipButton(
                            palette = palette,
                            label = stringResource(R.string.ai_action_retry),
                            primary = false,
                            testTag = "ai_writing_retry_result_button",
                            onClick = onSubmit
                        )
                    }
                }

                if (state.copiedNotice) {
                    Text(
                        text = stringResource(R.string.ai_copied_notice),
                        color = palette.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
fun TranslatePanel(
    palette: KeyboardColorPalette,
    state: TranslateUiState,
    isPasswordInput: Boolean,
    cloudNoticeAccepted: Boolean,
    onAcceptCloudNotice: () -> Unit,
    onSelectSource: (AiLanguageOption) -> Unit,
    onSelectTarget: (AiLanguageOption) -> Unit,
    onSwapLanguages: () -> Unit,
    onInputChanged: (String) -> Unit,
    onPaste: () -> Unit,
    onClear: () -> Unit,
    onToggleAutoTranslate: (Boolean) -> Unit,
    onSubmit: () -> Unit,
    onCancel: () -> Unit,
    onInsert: () -> Unit,
    onReplaceSelection: () -> Unit,
    onCopy: () -> Unit,
    onConfigureServer: () -> Unit,
    onBackToAlpha: () -> Unit,
    keyboardHeightScale: Float = 1f
) {
    val scaledHeight = (312f * keyboardHeightScale.coerceIn(0.70f, 1.50f)).coerceIn(240f, 420f).dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(scaledHeight)
            .background(palette.background)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("translate_panel")
    ) {
        PanelHeader(
            palette = palette,
            icon = Icons.Default.Translate,
            title = stringResource(R.string.ai_translate_title),
            backTestTag = "translate_back_button",
            onBackToAlpha = onBackToAlpha,
            onOpenSettings = onConfigureServer
        )

        if (isPasswordInput) {
            PasswordBlockedNotice(palette = palette)
            return
        }

        if (!cloudNoticeAccepted) {
            CloudDisclosureCard(
                palette = palette,
                onAccept = onAcceptCloudNotice,
                acceptTag = "ai_disclosure_accept_button"
            )
            return
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState()),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Clear "From" -> "To" bar with Swap button
            Surface(
                shape = RoundedCornerShape(10.dp),
                color = palette.functionalKeyBackground,
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.ai_translate_from) + ":",
                            color = palette.keyHintText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            modifier = Modifier
                                .weight(1f)
                                .horizontalScroll(rememberScrollState()),
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                AiLanguageOption.AUTO to R.string.ai_lang_auto,
                                AiLanguageOption.EN to R.string.ai_lang_en,
                                AiLanguageOption.AR to R.string.ai_lang_ar
                            ).forEach { (lang, labelRes) ->
                                PanelChip(
                                    palette = palette,
                                    label = stringResource(labelRes),
                                    selected = state.sourceLanguage == lang,
                                    onClick = { onSelectSource(lang) },
                                    testTag = "translate_from_${lang.apiValue}"
                                )
                            }
                        }

                        IconButton(
                            onClick = onSwapLanguages,
                            enabled = state.canSwapLanguages,
                            modifier = Modifier
                                .size(32.dp)
                                .alpha(if (state.canSwapLanguages) 1f else 0.35f)
                                .testTag("translate_swap_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.SwapHoriz,
                                contentDescription = stringResource(R.string.ai_translate_swap),
                                tint = palette.keyText,
                                modifier = Modifier.size(18.dp)
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = stringResource(R.string.ai_translate_to) + ":",
                            color = palette.keyHintText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            listOf(
                                AiLanguageOption.AR to R.string.ai_lang_ar,
                                AiLanguageOption.EN to R.string.ai_lang_en
                            ).forEach { (lang, labelRes) ->
                                PanelChip(
                                    palette = palette,
                                    label = stringResource(labelRes),
                                    selected = state.targetLanguage == lang,
                                    onClick = { onSelectTarget(lang) },
                                    testTag = "translate_to_${lang.apiValue}"
                                )
                            }
                        }
                    }
                }
            }

            // Auto-translate switch row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(8.dp))
                    .background(palette.functionalKeyBackground.copy(alpha = 0.6f))
                    .padding(horizontal = 8.dp, vertical = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.ai_translate_auto_switch),
                        color = palette.keyText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = stringResource(R.string.ai_translate_auto_off_hint),
                        color = palette.keyHintText,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Switch(
                    checked = state.autoTranslateEnabled,
                    onCheckedChange = onToggleAutoTranslate,
                    modifier = Modifier.testTag("translate_auto_switch")
                )
            }

            if (state.loadedFromSelection) {
                Text(
                    text = stringResource(R.string.ai_selection_loaded_badge),
                    color = palette.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.testTag("translate_selection_loaded_badge")
                )
            }

            PanelInputCard(
                palette = palette,
                value = state.inputText,
                placeholder = stringResource(R.string.ai_translate_input_placeholder),
                onValueChange = onInputChanged,
                inputTag = "translate_input",
                onPaste = onPaste,
                pasteTag = "translate_paste_button",
                onClear = onClear,
                clearTag = "translate_clear_button"
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = palette.accent,
                    modifier = Modifier
                        .height(34.dp)
                        .clickable(enabled = !state.isLoading) { onSubmit() }
                        .testTag("translate_submit_button")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Translate,
                            contentDescription = null,
                            tint = palette.actionKeyIcon,
                            modifier = Modifier.size(14.dp)
                        )
                        Text(
                            text = stringResource(R.string.ai_translate_btn),
                            color = palette.actionKeyIcon,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }

            if (state.isLoading) {
                LoadingBarCard(
                    palette = palette,
                    label = stringResource(R.string.ai_loading_translating),
                    cancelTag = "translate_cancel_button",
                    onCancel = onCancel
                )
            }

            if (!state.errorMessage.isNullOrBlank()) {
                ErrorCard(
                    palette = palette,
                    message = state.errorMessage,
                    showConfigure = state.errorType == AiProxyErrorType.UNCONFIGURED ||
                        state.errorType == AiProxyErrorType.INVALID_URL ||
                        state.errorType == AiProxyErrorType.UNAUTHORIZED,
                    retryTag = "translate_retry_button",
                    configureTag = "translate_configure_server_button",
                    onRetry = onSubmit,
                    onConfigureServer = onConfigureServer
                )
            }

            val res = state.result
            if (res != null) {
                if (state.isFieldChanged) {
                    FieldChangedWarning(palette = palette, testTag = "translate_field_changed_banner")
                }

                val detectedBadge = when {
                    state.sourceLanguage != AiLanguageOption.AUTO -> null
                    res.detectedSourceLanguage.equals("ar", ignoreCase = true) ->
                        stringResource(R.string.ai_translate_detected_ar)
                    res.detectedSourceLanguage.equals("en", ignoreCase = true) ->
                        stringResource(R.string.ai_translate_detected_en)
                    else -> null
                }

                ResultCard(
                    palette = palette,
                    header = detectedBadge ?: stringResource(R.string.ai_translate_preview_label),
                    bodyText = res.translatedText,
                    previewTag = "translate_result_preview"
                ) {
                    if (!state.isFieldChanged) {
                        ActionChipButton(
                            palette = palette,
                            label = stringResource(R.string.ai_action_insert),
                            primary = true,
                            testTag = "translate_insert_button",
                            onClick = onInsert
                        )
                        if (state.canReplaceSelection) {
                            ActionChipButton(
                                palette = palette,
                                label = stringResource(R.string.ai_action_replace_selection),
                                primary = false,
                                testTag = "translate_replace_button",
                                onClick = onReplaceSelection
                            )
                        }
                    }
                    ActionChipButton(
                        palette = palette,
                        label = stringResource(
                            if (state.isFieldChanged) R.string.ai_action_copy_only else R.string.ai_action_copy
                        ),
                        primary = false,
                        testTag = "translate_copy_button",
                        onClick = onCopy
                    )
                }

                if (state.copiedNotice) {
                    Text(
                        text = stringResource(R.string.ai_copied_notice),
                        color = palette.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelHeader(
    palette: KeyboardColorPalette,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    backTestTag: String,
    onBackToAlpha: () -> Unit,
    onOpenSettings: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            modifier = Modifier.weight(1f)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(18.dp)
            )
            Text(
                text = title,
                color = palette.keyText,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(
                onClick = onOpenSettings,
                modifier = Modifier.size(30.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.Settings,
                    contentDescription = stringResource(R.string.ai_action_configure_server),
                    tint = palette.keyHintText,
                    modifier = Modifier.size(16.dp)
                )
            }
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = palette.functionalKeyBackground,
                modifier = Modifier
                    .height(30.dp)
                    .clickable { onBackToAlpha() }
                    .testTag(backTestTag)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 12.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.clipboard_back_abc),
                        color = palette.keyText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }
    }
}

@Composable
private fun PanelChip(
    palette: KeyboardColorPalette,
    label: String,
    selected: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (selected) palette.accent else palette.functionalKeyBackground,
        modifier = Modifier
            .heightIn(min = 30.dp)
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Box(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = label,
                color = if (selected) palette.actionKeyIcon else palette.keyText,
                fontSize = 11.5.sp,
                fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun PanelInputCard(
    palette: KeyboardColorPalette,
    value: String,
    placeholder: String,
    onValueChange: (String) -> Unit,
    inputTag: String,
    onPaste: () -> Unit,
    pasteTag: String,
    onClear: () -> Unit,
    clearTag: String,
    trailingAction: @Composable () -> Unit
) {
    val isArabic = containsArabicScript(value)
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = palette.keyBackground,
        border = BorderStroke(0.75.dp, palette.keyHintText.copy(alpha = 0.25f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 42.dp, max = 76.dp)
            ) {
                if (value.isEmpty()) {
                    Text(
                        text = placeholder,
                        color = palette.keyHintText,
                        fontSize = 12.sp
                    )
                }
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    textStyle = TextStyle(
                        color = palette.keyText,
                        fontFamily = if (isArabic) NotoSansArabicFamily else null,
                        fontSize = 13.sp,
                        textDirection = if (isArabic) TextDirection.Rtl else TextDirection.Content
                    ),
                    cursorBrush = SolidColor(palette.accent),
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag(inputTag)
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = palette.functionalKeyBackground,
                        modifier = Modifier
                            .height(32.dp)
                            .clickable { onPaste() }
                            .testTag(pasteTag)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.ContentPaste,
                                contentDescription = null,
                                tint = palette.keyText,
                                modifier = Modifier.size(14.dp)
                            )
                            Text(
                                text = stringResource(R.string.ai_action_paste),
                                color = palette.keyText,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }

                    if (value.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = palette.functionalKeyBackground,
                            modifier = Modifier
                                .height(32.dp)
                                .clickable { onClear() }
                                .testTag(clearTag)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(3.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Close,
                                    contentDescription = null,
                                    tint = palette.keyHintText,
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = stringResource(R.string.ai_action_clear),
                                    color = palette.keyText,
                                    fontSize = 11.sp
                                )
                            }
                        }
                    }
                }

                trailingAction()
            }
        }
    }
}

@Composable
private fun LoadingBarCard(
    palette: KeyboardColorPalette,
    label: String,
    cancelTag: String,
    onCancel: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = palette.accent.copy(alpha = 0.12f),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            CircularProgressIndicator(
                color = palette.accent,
                strokeWidth = 2.dp,
                modifier = Modifier.size(16.dp)
            )
            Text(
                text = label,
                color = palette.keyText,
                fontSize = 12.sp,
                modifier = Modifier.weight(1f)
            )
            TextButton(
                onClick = onCancel,
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 2.dp),
                colors = ButtonDefaults.textButtonColors(contentColor = palette.accent),
                modifier = Modifier.testTag(cancelTag)
            ) {
                Text(stringResource(R.string.ai_action_cancel), fontSize = 11.sp, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun ErrorCard(
    palette: KeyboardColorPalette,
    message: String,
    showConfigure: Boolean,
    retryTag: String,
    configureTag: String,
    onRetry: () -> Unit,
    onConfigureServer: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = palette.functionalKeyBackground,
        border = BorderStroke(0.75.dp, palette.accent.copy(alpha = 0.45f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag("ai_error_card")
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.WarningAmber,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(16.dp)
                )
                Text(
                    text = message,
                    color = palette.keyText,
                    fontSize = 11.5.sp,
                    modifier = Modifier.weight(1f)
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                if (showConfigure) {
                    ActionChipButton(
                        palette = palette,
                        label = stringResource(R.string.ai_action_configure_server),
                        primary = true,
                        testTag = configureTag,
                        onClick = onConfigureServer
                    )
                }
                ActionChipButton(
                    palette = palette,
                    label = stringResource(R.string.ai_action_retry),
                    primary = !showConfigure,
                    testTag = retryTag,
                    onClick = onRetry
                )
            }
        }
    }
}

@Composable
private fun ResultCard(
    palette: KeyboardColorPalette,
    header: String?,
    bodyText: String,
    previewTag: String,
    actions: @Composable () -> Unit
) {
    val isArabic = containsArabicScript(bodyText)
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = palette.keyBackground,
        border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.35f)),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            if (!header.isNullOrBlank()) {
                Text(
                    text = header,
                    color = palette.accent,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
            Text(
                text = bodyText,
                color = palette.keyText,
                fontFamily = if (isArabic) NotoSansArabicFamily else null,
                fontSize = 13.sp,
                style = TextStyle(
                    textDirection = if (isArabic) TextDirection.Rtl else TextDirection.Content
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(previewTag)
            )
            @OptIn(ExperimentalLayoutApi::class)
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                actions()
            }
        }
    }
}

@Composable
private fun ActionChipButton(
    palette: KeyboardColorPalette,
    label: String,
    primary: Boolean,
    testTag: String,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = if (primary) palette.accent else palette.functionalKeyBackground,
        modifier = Modifier
            .height(32.dp)
            .clickable { onClick() }
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Icon(
                imageVector = if (primary) Icons.Default.Check else Icons.Default.ContentCopy,
                contentDescription = null,
                tint = if (primary) palette.actionKeyIcon else palette.keyText,
                modifier = Modifier.size(13.dp)
            )
            Text(
                text = label,
                color = if (primary) palette.actionKeyIcon else palette.keyText,
                fontSize = 11.5.sp,
                fontWeight = FontWeight.SemiBold
            )
        }
    }
}

@Composable
private fun CloudDisclosureCard(
    palette: KeyboardColorPalette,
    onAccept: () -> Unit,
    acceptTag: String
) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = palette.keyBackground,
        border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .testTag("ai_disclosure_card")
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = stringResource(R.string.ai_disclosure_title),
                color = palette.keyText,
                fontWeight = FontWeight.Bold,
                fontSize = 13.sp
            )
            Text(
                text = stringResource(R.string.ai_disclosure_body),
                color = palette.keyHintText,
                fontSize = 12.sp
            )
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = palette.accent,
                modifier = Modifier
                    .height(36.dp)
                    .clickable { onAccept() }
                    .testTag(acceptTag)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 16.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.ai_disclosure_accept),
                        color = palette.actionKeyIcon,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
private fun PasswordBlockedNotice(palette: KeyboardColorPalette) {
    Surface(
        shape = RoundedCornerShape(12.dp),
        color = palette.functionalKeyBackground,
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp)
            .testTag("ai_password_blocked_banner")
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = Icons.Default.Lock,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = stringResource(R.string.ai_blocked_password),
                color = palette.keyText,
                fontSize = 12.5.sp
            )
        }
    }
}

@Composable
private fun FieldChangedWarning(palette: KeyboardColorPalette, testTag: String) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = palette.functionalKeyBackground,
        border = BorderStroke(0.75.dp, palette.accent.copy(alpha = 0.4f)),
        modifier = Modifier
            .fillMaxWidth()
            .testTag(testTag)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = Icons.Default.WarningAmber,
                contentDescription = null,
                tint = palette.accent,
                modifier = Modifier.size(15.dp)
            )
            Text(
                text = stringResource(R.string.ai_field_changed_copy_only),
                color = palette.keyText,
                fontSize = 11.sp
            )
        }
    }
}
