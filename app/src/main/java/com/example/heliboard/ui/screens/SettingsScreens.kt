package com.example.heliboard.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Spellcheck
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.example.heliboard.BuildConfig
import com.example.heliboard.R
import com.example.heliboard.ai.AiConfigResolver
import com.example.heliboard.ai.AiConnectionMode
import com.example.heliboard.data.PersonalWordEntry
import com.example.heliboard.data.ToolbarKey
import com.example.heliboard.data.ToolbarMode
import com.example.heliboard.voice.AndroidVoiceDictationBackend
import com.example.heliboard.ime.iconForToolbarKey
import com.example.heliboard.ime.localizedToolbarKeyLabel
import com.example.heliboard.ui.HeliBoardViewModel
import com.example.heliboard.util.LocaleHelper
import kotlin.math.roundToInt

@Composable
fun SettingsHubScreen(
    viewModel: HeliBoardViewModel,
    onOpenPreferences: () -> Unit,
    onOpenAiSettings: () -> Unit = {},
    onOpenToolbar: () -> Unit,
    onOpenTextCorrection: () -> Unit,
    onOpenPersonalDictionary: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(stringResource(R.string.settings_title), style = MaterialTheme.typography.headlineMedium)

        // Prominent Settings Language Switcher Card
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier
                .fillMaxWidth()
                .testTag("settings_language_card")
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Translate,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary
                    )
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = stringResource(R.string.settings_language_title),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.settings_language_subtitle),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val options = listOf(
                        LocaleHelper.LANG_SYSTEM to stringResource(R.string.settings_language_system),
                        LocaleHelper.LANG_ARABIC to stringResource(R.string.settings_language_ar),
                        LocaleHelper.LANG_ENGLISH to stringResource(R.string.settings_language_en)
                    )
                    options.forEach { (code, label) ->
                        FilterChip(
                            selected = settings.appLanguage.equals(code, ignoreCase = true),
                            onClick = {
                                viewModel.updateSettings { it.copy(appLanguage = code) }
                                LocaleHelper.syncSystemApplicationLocales(context, code)
                            },
                            label = { Text(label) },
                            modifier = Modifier.testTag("settings_lang_$code")
                        )
                    }
                }
            }
        }

        SettingsNavCard(
            icon = Icons.Default.Tune,
            title = stringResource(R.string.settings_preferences_title),
            subtitle = stringResource(R.string.settings_preferences_sub),
            onClick = onOpenPreferences,
            tag = "settings_preferences"
        )
        SettingsNavCard(
            icon = Icons.Default.AutoFixHigh,
            title = stringResource(R.string.settings_ai_title),
            subtitle = stringResource(R.string.settings_ai_sub),
            onClick = onOpenAiSettings,
            tag = "settings_ai"
        )
        SettingsNavCard(
            icon = Icons.Default.Build,
            title = stringResource(R.string.settings_toolbar_title),
            subtitle = stringResource(R.string.settings_toolbar_sub),
            onClick = onOpenToolbar,
            tag = "settings_toolbar"
        )
        SettingsNavCard(
            icon = Icons.Default.Spellcheck,
            title = stringResource(R.string.settings_text_correction_title),
            subtitle = stringResource(R.string.settings_text_correction_sub),
            onClick = onOpenTextCorrection,
            tag = "settings_text_correction"
        )
        SettingsNavCard(
            icon = Icons.Default.Book,
            title = stringResource(R.string.settings_personal_dict_title),
            subtitle = stringResource(R.string.settings_personal_dict_sub),
            onClick = onOpenPersonalDictionary,
            tag = "settings_personal_dictionary"
        )
        SettingsNavCard(
            icon = Icons.Default.Backup,
            title = stringResource(R.string.settings_backup_title),
            subtitle = stringResource(R.string.settings_backup_sub),
            onClick = onOpenBackup,
            tag = "settings_backup"
        )
        SettingsNavCard(
            icon = Icons.Default.Info,
            title = stringResource(R.string.settings_about_title),
            subtitle = stringResource(R.string.settings_about_sub),
            onClick = onOpenAbout,
            tag = "settings_about"
        )
    }
}

@Composable
private fun SettingsNavCard(
    icon: ImageVector,
    title: String,
    subtitle: String,
    tag: String,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .testTag(tag)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(26.dp)
            )
            Spacer(Modifier.width(14.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Icon(Icons.AutoMirrored.Filled.KeyboardArrowRight, contentDescription = null)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SubSettingsScaffold(
    title: String,
    onBack: () -> Unit,
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (Modifier) -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(title) },
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.action_back))
                    }
                }
            )
        },
        floatingActionButton = floatingActionButton
    ) { innerPadding ->
        content(Modifier.padding(innerPadding))
    }
}

@Composable
fun PreferencesScreen(viewModel: HeliBoardViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val context = LocalContext.current
    SubSettingsScaffold(title = stringResource(R.string.settings_preferences_title), onBack = onBack) { mod ->
        Column(
            modifier = mod
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Keyboard Height control card
            Card(
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pref_keyboard_height_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.appearance_height),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.pref_keyboard_height_sub),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${(settings.keyboardHeightScale * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val presets = listOf(
                            Triple("compact", 0.85f, R.string.height_preset_compact),
                            Triple("normal", 1.00f, R.string.height_preset_normal),
                            Triple("tall", 1.15f, R.string.height_preset_tall),
                            Triple("extra_tall", 1.35f, R.string.height_preset_extra_tall)
                        )
                        presets.forEach { (id, scale, labelRes) ->
                            FilterChip(
                                selected = kotlin.math.abs(settings.keyboardHeightScale - scale) < 0.03f,
                                onClick = { viewModel.updateSettings { it.copy(keyboardHeightScale = scale) } },
                                label = { Text(stringResource(labelRes)) },
                                modifier = Modifier.testTag("pref_height_preset_$id")
                            )
                        }
                    }
                    Slider(
                        value = settings.keyboardHeightScale.coerceIn(0.70f, 1.50f),
                        onValueChange = { v ->
                            val snapped = (v * 20f).roundToInt() / 20f
                            viewModel.updateSettings { it.copy(keyboardHeightScale = snapped.coerceIn(0.70f, 1.50f)) }
                        },
                        valueRange = 0.70f..1.50f,
                        modifier = Modifier.testTag("pref_height_slider")
                    )
                }
            }

            SwitchRow(
                stringResource(R.string.pref_show_number_row),
                stringResource(R.string.pref_show_number_row_sub),
                settings.showNumberRow
            ) {
                viewModel.updateSettings { s -> s.copy(showNumberRow = it) }
            }
            SwitchRow(
                stringResource(R.string.pref_show_hints),
                stringResource(R.string.pref_show_hints_sub),
                settings.showHints
            ) {
                viewModel.updateSettings { s -> s.copy(showHints = it) }
            }
            SwitchRow(
                stringResource(R.string.pref_key_popup),
                stringResource(R.string.pref_key_popup_sub),
                settings.keyPopupEnabled
            ) {
                viewModel.updateSettings { s -> s.copy(keyPopupEnabled = it) }
            }
            SwitchRow(
                stringResource(R.string.pref_vibrate),
                stringResource(R.string.pref_vibrate_sub),
                settings.vibrateOnKeypress
            ) {
                viewModel.updateSettings { s -> s.copy(vibrateOnKeypress = it) }
            }
            SwitchRow(
                stringResource(R.string.pref_sound),
                stringResource(R.string.pref_sound_sub),
                settings.soundOnKeypress
            ) {
                viewModel.updateSettings { s -> s.copy(soundOnKeypress = it) }
            }
            SwitchRow(
                stringResource(R.string.pref_space_swipe),
                stringResource(R.string.pref_space_swipe_sub),
                settings.spacebarSwipeCursor
            ) {
                viewModel.updateSettings { s -> s.copy(spacebarSwipeCursor = it) }
            }
            SwitchRow(
                stringResource(R.string.pref_bottom_emoji),
                stringResource(R.string.pref_bottom_emoji_sub),
                settings.showEmojiKey
            ) {
                viewModel.updateSettings { s -> s.copy(showEmojiKey = it) }
            }
            SwitchRow(
                stringResource(R.string.pref_one_handed),
                stringResource(R.string.pref_one_handed_sub),
                settings.oneHandedMode
            ) {
                viewModel.updateSettings { s -> s.copy(oneHandedMode = it) }
            }
            SwitchRow(
                stringResource(R.string.pref_split),
                stringResource(R.string.pref_split_sub),
                settings.splitKeyboard
            ) {
                viewModel.updateSettings { s -> s.copy(splitKeyboard = it) }
            }
            Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text(stringResource(R.string.pref_long_press_delay), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.pref_long_press_ms, settings.longPressTimeoutMs),
                            style = MaterialTheme.typography.labelLarge
                        )
                    }
                    Slider(
                        value = settings.longPressTimeoutMs.toFloat(),
                        onValueChange = { v -> viewModel.updateSettings { it.copy(longPressTimeoutMs = v.roundToInt()) } },
                        valueRange = 150f..600f
                    )
                }
            }

            // Key Press Sensitivity & Fast-Typing card
            Card(
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("pref_key_sensitivity_card")
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.pref_key_sensitivity_title),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.pref_key_sensitivity_sub),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${(settings.keyPressSensitivity * 100).roundToInt()}%",
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        FilterChip(
                            selected = !settings.instantKeypressCommit && kotlin.math.abs(settings.keyPressSensitivity - 0.65f) < 0.05f,
                            onClick = {
                                viewModel.updateSettings {
                                    it.copy(keyPressSensitivity = 0.65f, instantKeypressCommit = false)
                                }
                            },
                            label = { Text(stringResource(R.string.sensitivity_preset_balanced)) },
                            modifier = Modifier.testTag("pref_sensitivity_balanced")
                        )
                        FilterChip(
                            selected = !settings.instantKeypressCommit && kotlin.math.abs(settings.keyPressSensitivity - 0.85f) < 0.05f,
                            onClick = {
                                viewModel.updateSettings {
                                    it.copy(keyPressSensitivity = 0.85f, instantKeypressCommit = false)
                                }
                            },
                            label = { Text(stringResource(R.string.sensitivity_preset_fast)) },
                            modifier = Modifier.testTag("pref_sensitivity_fast")
                        )
                        FilterChip(
                            selected = settings.instantKeypressCommit && settings.keyPressSensitivity >= 0.95f,
                            onClick = {
                                viewModel.updateSettings {
                                    it.copy(keyPressSensitivity = 1.0f, instantKeypressCommit = true)
                                }
                            },
                            label = { Text(stringResource(R.string.sensitivity_preset_ultra)) },
                            modifier = Modifier.testTag("pref_sensitivity_ultra")
                        )
                    }
                    Slider(
                        value = settings.keyPressSensitivity.coerceIn(0.20f, 1.00f),
                        onValueChange = { v ->
                            val snapped = (v * 20f).roundToInt() / 20f
                            viewModel.updateSettings { it.copy(keyPressSensitivity = snapped.coerceIn(0.20f, 1.00f)) }
                        },
                        valueRange = 0.20f..1.00f,
                        modifier = Modifier.testTag("pref_sensitivity_slider")
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.updateSettings { it.copy(instantKeypressCommit = !it.instantKeypressCommit) }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.pref_instant_commit),
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.pref_instant_commit_sub),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = settings.instantKeypressCommit,
                            onCheckedChange = {
                                viewModel.updateSettings { s -> s.copy(instantKeypressCommit = it) }
                            },
                            modifier = Modifier.testTag("pref_instant_commit_switch")
                        )
                    }
                }
            }

            // Arabic Voice Recognition Dialect & Offline/Continuous controls card
            Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Mic,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = stringResource(R.string.voice_dialect_title),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium
                            )
                            Text(
                                text = stringResource(R.string.voice_dialect_sub),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        val dialects = listOf(
                            "ar-EG" to stringResource(R.string.voice_dialect_eg),
                            "ar-SA" to stringResource(R.string.voice_dialect_sa),
                            "ar" to stringResource(R.string.voice_dialect_ar)
                        )
                        dialects.forEach { (tag, label) ->
                            FilterChip(
                                selected = settings.arabicVoiceDialect.equals(tag, ignoreCase = true),
                                onClick = { viewModel.updateSettings { it.copy(arabicVoiceDialect = tag) } },
                                label = { Text(label) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.updateSettings { it.copy(preferOfflineVoice = !it.preferOfflineVoice) }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.voice_offline_pref), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(stringResource(R.string.voice_offline_pref_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = settings.preferOfflineVoice,
                            onCheckedChange = { viewModel.updateSettings { s -> s.copy(preferOfflineVoice = it) } },
                            modifier = Modifier.testTag("pref_voice_offline_switch")
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable {
                                viewModel.updateSettings { it.copy(continuousVoiceDictation = !it.continuousVoiceDictation) }
                            },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(stringResource(R.string.voice_continuous_pref), style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            Text(stringResource(R.string.voice_continuous_pref_sub), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Spacer(Modifier.width(12.dp))
                        Switch(
                            checked = settings.continuousVoiceDictation,
                            onCheckedChange = { viewModel.updateSettings { s -> s.copy(continuousVoiceDictation = it) } },
                            modifier = Modifier.testTag("pref_voice_continuous_switch")
                        )
                    }
                    OutlinedButton(
                        onClick = { AndroidVoiceDictationBackend(context).openOfflineVoiceSettings() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("pref_voice_offline_settings_btn")
                    ) {
                        Icon(
                            imageVector = Icons.Default.Tune,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.voice_offline_settings))
                    }
                    Text(
                        text = stringResource(R.string.voice_offline_settings_sub),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            Text(
                text = stringResource(R.string.clipboard_section_title),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(top = 6.dp)
            )
            SwitchRow(
                stringResource(R.string.clipboard_enable_history),
                stringResource(R.string.clipboard_enable_history_sub),
                settings.clipboardHistoryEnabled
            ) {
                viewModel.updateSettings { s -> s.copy(clipboardHistoryEnabled = it) }
            }
            SwitchRow(
                stringResource(R.string.clipboard_pinned_first),
                stringResource(R.string.clipboard_pinned_first_sub),
                settings.clipboardPinnedFirst
            ) {
                viewModel.updateSettings { s -> s.copy(clipboardPinnedFirst = it) }
            }
            Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.clipboard_retention_title),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium
                    )
                    val options = listOf(
                        10 to stringResource(R.string.clipboard_retention_10m),
                        60 to stringResource(R.string.clipboard_retention_1h),
                        120 to stringResource(R.string.clipboard_retention_2h),
                        1440 to stringResource(R.string.clipboard_retention_24h),
                        -1 to stringResource(R.string.clipboard_retention_forever)
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        options.forEach { (minutes, label) ->
                            FilterChip(
                                selected = settings.clipboardRetentionMinutes == minutes,
                                onClick = {
                                    viewModel.updateSettings { it.copy(clipboardRetentionMinutes = minutes) }
                                },
                                label = { Text(label) }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun ToolbarScreen(viewModel: HeliBoardViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    val density = LocalDensity.current
    val swapThresholdPx = with(density) { 52.dp.toPx() }
    var draggedKeyName by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val latestEnabledNames by rememberUpdatedState(settings.toolbarKeys)

    SubSettingsScaffold(title = stringResource(R.string.toolbar_custom_title), onBack = onBack) { mod ->
        Column(
            modifier = mod
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.toolbar_mode_title), style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ToolbarMode.entries.forEach { mode ->
                    val label = when (mode) {
                        ToolbarMode.EXPANDABLE -> stringResource(R.string.toolbar_mode_expandable)
                        ToolbarMode.TOOLBAR_KEYS -> stringResource(R.string.toolbar_mode_always)
                        ToolbarMode.SUGGESTION_STRIP -> stringResource(R.string.toolbar_mode_suggestions)
                        ToolbarMode.HIDDEN -> stringResource(R.string.toolbar_mode_hidden)
                    }
                    FilterChip(
                        selected = settings.toolbarMode == mode.id,
                        onClick = { viewModel.updateSettings { it.copy(toolbarMode = mode.id) } },
                        label = { Text(label) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(stringResource(R.string.toolbar_buttons_order), style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { viewModel.resetToolbarKeys() }) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.action_reset))
                }
            }
            Text(
                text = stringResource(R.string.toolbar_drag_hint),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            // Ordered enabled keys first with drag-and-drop handle + up/down controls
            settings.toolbarKeys.forEachIndexed { idx, keyName ->
                val tk = runCatching { ToolbarKey.valueOf(keyName) }.getOrNull() ?: return@forEachIndexed
                val isDragging = draggedKeyName == keyName
                Card(
                    shape = RoundedCornerShape(12.dp),
                    border = if (isDragging) BorderStroke(1.5.dp, MaterialTheme.colorScheme.primary) else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (isDragging) dragOffsetY else 0f
                        }
                        .testTag("settings_toolbar_item_${tk.name.lowercase()}")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(8.dp))
                                .pointerInput(keyName) {
                                    detectVerticalDragGestures(
                                        onDragStart = {
                                            draggedKeyName = keyName
                                            dragOffsetY = 0f
                                        },
                                        onVerticalDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetY += dragAmount
                                            val currentList = latestEnabledNames
                                            val curIdx = currentList.indexOf(keyName)
                                            if (curIdx != -1) {
                                                if (dragOffsetY > swapThresholdPx && curIdx < currentList.lastIndex) {
                                                    viewModel.moveToolbarKey(curIdx, curIdx + 1)
                                                    dragOffsetY -= swapThresholdPx
                                                } else if (dragOffsetY < -swapThresholdPx && curIdx > 0) {
                                                    viewModel.moveToolbarKey(curIdx, curIdx - 1)
                                                    dragOffsetY += swapThresholdPx
                                                }
                                            }
                                        },
                                        onDragEnd = {
                                            draggedKeyName = null
                                            dragOffsetY = 0f
                                        },
                                        onDragCancel = {
                                            draggedKeyName = null
                                            dragOffsetY = 0f
                                        }
                                    )
                                }
                                .testTag("settings_toolbar_drag_handle_${tk.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DragHandle,
                                contentDescription = stringResource(R.string.clipboard_drag_handle),
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Icon(iconForToolbarKey(tk), contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(localizedToolbarKeyLabel(tk), modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        IconButton(onClick = { viewModel.moveToolbarKey(keyName, -1) }, enabled = idx > 0) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = stringResource(R.string.clipboard_move_up))
                        }
                        IconButton(
                            onClick = { viewModel.moveToolbarKey(keyName, 1) },
                            enabled = idx < settings.toolbarKeys.lastIndex
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = stringResource(R.string.clipboard_move_down))
                        }
                        Switch(
                            checked = true,
                            onCheckedChange = { viewModel.toggleToolbarKey(tk) },
                            modifier = Modifier.testTag("settings_toolbar_toggle_${tk.name.lowercase()}")
                        )
                    }
                }
            }

            // Disabled keys
            val disabled = ToolbarKey.entries.filterNot { it.name in settings.toolbarKeys }
            if (disabled.isNotEmpty()) {
                Text(stringResource(R.string.toolbar_available_buttons), style = MaterialTheme.typography.labelLarge)
                disabled.forEach { tk ->
                    Card(
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("settings_toolbar_item_${tk.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(iconForToolbarKey(tk), contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(localizedToolbarKeyLabel(tk), modifier = Modifier.weight(1f))
                            Switch(
                                checked = false,
                                onCheckedChange = { viewModel.toggleToolbarKey(tk) },
                                modifier = Modifier.testTag("settings_toolbar_toggle_${tk.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun TextCorrectionScreen(
    viewModel: HeliBoardViewModel,
    onOpenPersonalDictionary: () -> Unit,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    val learnedCount by viewModel.learnedCount.collectAsState()
    val customDictCount by viewModel.customDictCount.collectAsState()

    val importWordlistLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            val text = runCatching {
                context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
            }.getOrNull()
            if (!text.isNullOrBlank()) {
                val count = viewModel.importWordlist(text)
                Toast.makeText(context, context.getString(R.string.tc_imported_toast, count), Toast.LENGTH_SHORT).show()
            }
        }
    }

    SubSettingsScaffold(title = stringResource(R.string.tc_title), onBack = onBack) { mod ->
        Column(
            modifier = mod
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SwitchRow(stringResource(R.string.tc_show_suggestions), stringResource(R.string.tc_show_suggestions_sub), settings.showSuggestions) {
                viewModel.updateSettings { s -> s.copy(showSuggestions = it) }
            }
            SwitchRow(stringResource(R.string.tc_autocorrect), stringResource(R.string.tc_autocorrect_sub), settings.autoCorrection) {
                viewModel.updateSettings { s -> s.copy(autoCorrection = it) }
            }
            SwitchRow(stringResource(R.string.tc_auto_cap), stringResource(R.string.tc_auto_cap_sub), settings.autoCap) {
                viewModel.updateSettings { s -> s.copy(autoCap = it) }
            }
            SwitchRow(stringResource(R.string.tc_double_space), stringResource(R.string.tc_double_space_sub), settings.doubleSpacePeriod) {
                viewModel.updateSettings { s -> s.copy(doubleSpacePeriod = it) }
            }
            SwitchRow(stringResource(R.string.tc_emoji_suggestions), stringResource(R.string.tc_emoji_suggestions_sub), settings.suggestEmojis) {
                viewModel.updateSettings { s -> s.copy(suggestEmojis = it) }
            }
            SwitchRow(stringResource(R.string.tc_incognito), stringResource(R.string.tc_incognito_sub), settings.incognitoMode) {
                viewModel.updateSettings { s -> s.copy(incognitoMode = it) }
            }

            Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.tc_dictionaries_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.tc_dictionaries_stats, learnedCount, customDictCount),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onOpenPersonalDictionary) {
                            Text(stringResource(R.string.settings_personal_dict_title))
                        }
                        OutlinedButton(onClick = { importWordlistLauncher.launch(arrayOf("text/*")) }) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text(stringResource(R.string.tc_import_wordlist))
                        }
                    }
                    if (learnedCount > 0) {
                        TextButton(onClick = { viewModel.clearLearnedWords() }) {
                            Text(stringResource(R.string.tc_clear_learned))
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun PersonalDictionaryScreen(viewModel: HeliBoardViewModel, onBack: () -> Unit) {
    val words by viewModel.personalWords.collectAsState()
    var query by remember { mutableStateOf("") }
    var editingWord by remember { mutableStateOf<PersonalWordEntry?>(null) }

    val filtered = remember(words, query) {
        if (query.isBlank()) words
        else words.filter {
            it.word.contains(query, ignoreCase = true) || it.shortcut.contains(query, ignoreCase = true)
        }
    }

    SubSettingsScaffold(
        title = stringResource(R.string.settings_personal_dict_title),
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editingWord = PersonalWordEntry(0L, "", "", 250, "") },
                modifier = Modifier.testTag("add_personal_word_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.pd_add_word))
            }
        }
    ) { mod ->
        Column(
            modifier = mod
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                placeholder = { Text(stringResource(R.string.pd_search_placeholder)) },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                items(filtered, key = { it.id }) { entry ->
                    Card(
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { editingWord = entry }
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(entry.word, style = MaterialTheme.typography.titleMedium)
                                val weightStr = stringResource(R.string.pd_weight_value, entry.weight)
                                val sub = if (entry.shortcut.isNotBlank()) {
                                    "$weightStr  •  ${stringResource(R.string.pd_shortcut_value, entry.shortcut)}"
                                } else weightStr
                                Text(
                                    sub,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { editingWord = entry }) {
                                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.pd_edit_word))
                            }
                            IconButton(onClick = { viewModel.deletePersonalWord(entry.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.pd_delete_word))
                            }
                        }
                    }
                }
            }
        }
    }

    editingWord?.let { current ->
        var word by remember(current) { mutableStateOf(current.word) }
        var shortcut by remember(current) { mutableStateOf(current.shortcut) }
        var weight by remember(current) { mutableStateOf(current.weight.toString()) }

        AlertDialog(
            onDismissRequest = { editingWord = null },
            title = { Text(stringResource(if (current.id == 0L) R.string.pd_add_word else R.string.pd_edit_word)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = word,
                        onValueChange = { word = it },
                        label = { Text(stringResource(R.string.pd_word_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = shortcut,
                        onValueChange = { shortcut = it },
                        label = { Text(stringResource(R.string.pd_shortcut_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it.filter { c -> c.isDigit() } },
                        label = { Text(stringResource(R.string.pd_weight_label)) },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.upsertPersonalWord(
                            current.copy(
                                word = word.trim(),
                                shortcut = shortcut.trim(),
                                weight = (weight.toIntOrNull() ?: 250).coerceIn(1, 255)
                            )
                        )
                        editingWord = null
                    },
                    enabled = word.isNotBlank()
                ) {
                    Text(stringResource(R.string.action_save))
                }
            },
            dismissButton = {
                TextButton(onClick = { editingWord = null }) { Text(stringResource(R.string.action_cancel)) }
            }
        )
    }
}

@Composable
fun BackupScreen(viewModel: HeliBoardViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    var statusMessage by remember { mutableStateOf<String?>(null) }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                    it.write(viewModel.prefsRepo.exportBackupJson())
                }
                statusMessage = context.getString(R.string.backup_export_success)
            }.onFailure {
                statusMessage = context.getString(R.string.backup_export_failed, it.localizedMessage ?: "")
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            runCatching {
                val raw = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                viewModel.prefsRepo.importBackupJson(raw)
                    .onSuccess { msg -> statusMessage = msg }
                    .onFailure { err ->
                        statusMessage = context.getString(R.string.backup_invalid_file, err.localizedMessage ?: "")
                    }
            }
        }
    }

    SubSettingsScaffold(title = stringResource(R.string.settings_backup_title), onBack = onBack) { mod ->
        Column(
            modifier = mod
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.backup_card_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.backup_card_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { exportLauncher.launch("heliboard_backup.json") }) {
                            Text(stringResource(R.string.backup_export_btn))
                        }
                        OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) }) {
                            Text(stringResource(R.string.backup_restore_btn))
                        }
                    }
                    OutlinedButton(onClick = {
                        viewModel.prefsRepo.copyToSystemClipboard(viewModel.prefsRepo.exportBackupJson())
                        Toast.makeText(context, context.getString(R.string.backup_copied_toast), Toast.LENGTH_SHORT).show()
                    }) {
                        Text(stringResource(R.string.backup_copy_btn))
                    }
                    statusMessage?.let {
                        Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        }
    }
}

@Composable
fun AboutScreen(onBack: () -> Unit) {
    SubSettingsScaffold(title = stringResource(R.string.settings_about_title), onBack = onBack) { mod ->
        Column(
            modifier = mod
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
                    Text(stringResource(R.string.about_version, BuildConfig.VERSION_NAME), style = MaterialTheme.typography.bodyMedium)
                    Text(
                        stringResource(R.string.about_desc),
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.about_privacy_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.about_privacy_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(R.string.about_licenses_title), style = MaterialTheme.typography.titleMedium)
                    Text(
                        stringResource(R.string.about_licenses_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun AiSettingsScreen(viewModel: HeliBoardViewModel, onBack: () -> Unit) {
    val context = LocalContext.current
    val settings by viewModel.settings.collectAsState()
    var urlInput by remember(settings.aiProxyUrl) { mutableStateOf(settings.aiProxyUrl) }
    var tokenInput by remember(settings.aiProxyAuthToken) { mutableStateOf(settings.aiProxyAuthToken) }

    SubSettingsScaffold(title = stringResource(R.string.settings_ai_title), onBack = onBack) { mod ->
        Column(
            modifier = mod
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = stringResource(R.string.ai_settings_card_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    Text(
                        text = stringResource(R.string.ai_settings_card_desc),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    val connectionMode = AiConfigResolver.resolveConnectionMode(
                        settingsProxyUrl = settings.aiProxyUrl,
                        buildProxyUrl = BuildConfig.AI_PROXY_URL,
                        buildGeminiApiKey = BuildConfig.GEMINI_API_KEY
                    )
                    val modeLabel = stringResource(
                        when (connectionMode) {
                            AiConnectionMode.USER_PROXY -> R.string.ai_settings_mode_user_proxy
                            AiConnectionMode.BUILD_PROXY -> R.string.ai_settings_mode_build_proxy
                            AiConnectionMode.AUTO_GEMINI_KEY -> R.string.ai_settings_mode_auto_key
                            AiConnectionMode.UNCONFIGURED -> R.string.ai_settings_mode_unconfigured
                        }
                    )
                    Text(
                        text = stringResource(R.string.ai_settings_status_label, modeLabel),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.testTag("ai_connection_mode_label")
                    )
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text(stringResource(R.string.ai_settings_url_label)) },
                        placeholder = { Text(stringResource(R.string.ai_settings_url_hint)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_proxy_url_input")
                    )
                    OutlinedTextField(
                        value = tokenInput,
                        onValueChange = { tokenInput = it },
                        label = { Text(stringResource(R.string.ai_settings_token_label)) },
                        placeholder = { Text(stringResource(R.string.ai_settings_token_hint)) },
                        singleLine = true,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("ai_proxy_token_input")
                    )
                    Button(
                        onClick = {
                            viewModel.updateSettings {
                                it.copy(
                                    aiProxyUrl = urlInput.trim(),
                                    aiProxyAuthToken = tokenInput.trim()
                                )
                            }
                            Toast.makeText(
                                context,
                                context.getString(R.string.ai_settings_saved_toast),
                                Toast.LENGTH_SHORT
                            ).show()
                        },
                        modifier = Modifier.testTag("ai_proxy_save_button")
                    ) {
                        Text(stringResource(R.string.action_save))
                    }
                }
            }

            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = stringResource(R.string.ai_settings_privacy_card_title),
                        style = MaterialTheme.typography.titleMedium
                    )
                    val acceptedText = stringResource(
                        if (settings.aiCloudNoticeAccepted) R.string.ai_settings_yes else R.string.ai_settings_no
                    )
                    Text(
                        text = stringResource(R.string.ai_settings_privacy_card_desc, acceptedText),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (settings.aiCloudNoticeAccepted) {
                        OutlinedButton(
                            onClick = {
                                viewModel.updateSettings { it.copy(aiCloudNoticeAccepted = false) }
                            },
                            modifier = Modifier.testTag("ai_reset_disclosure_button")
                        ) {
                            Text(stringResource(R.string.ai_settings_reset_disclosure))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onCheckedChange(!checked) }
                .padding(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
                Text(
                    subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(Modifier.width(12.dp))
            Switch(checked = checked, onCheckedChange = onCheckedChange)
        }
    }
}
