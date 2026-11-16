package com.example.heliboard.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.heliboard.R
import com.example.heliboard.data.CustomThemeSpec
import com.example.heliboard.data.ThemeStyle
import com.example.heliboard.ime.HeliKeyboardView
import com.example.heliboard.theme.KeyboardThemes
import com.example.heliboard.ui.HeliBoardViewModel
import kotlin.math.roundToInt

@Composable
fun AppearanceScreen(
    viewModel: HeliBoardViewModel,
    modifier: Modifier = Modifier
) {
    val systemDark = isSystemInDarkTheme()
    val settings by viewModel.settings.collectAsState()
    val customThemes by viewModel.customThemes.collectAsState()
    val clipboard by viewModel.clipboard.collectAsState()
    val keyboardUiState by viewModel.keyboardUiState.collectAsState()

    var editingCustomTheme by remember { mutableStateOf<CustomThemeSpec?>(null) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(stringResource(R.string.appearance_title), style = MaterialTheme.typography.headlineMedium)

        // Live Keyboard Preview Card
        Card(
            shape = RoundedCornerShape(16.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column {
                Text(
                    text = stringResource(R.string.live_keyboard_preview),
                    style = MaterialTheme.typography.labelLarge,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
                HeliKeyboardView(
                    controller = viewModel.sandboxController,
                    uiState = keyboardUiState,
                    settings = settings,
                    customThemes = customThemes,
                    clipboardEntries = clipboard,
                    emojiCategories = viewModel.emojiCategories,
                    onDeleteClipboard = { viewModel.prefsRepo.deleteClipboardEntry(it) },
                    onPinClipboard = { viewModel.prefsRepo.togglePinClipboard(it) },
                    onClearUnpinnedClipboard = { viewModel.prefsRepo.clearUnpinnedClipboard() },
                    applyNavigationBarPadding = false
                )
            }
        }

        // Theme Style (Material / Rounded / Holo)
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Text(stringResource(R.string.appearance_style_title), style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ThemeStyle.entries.forEach { style ->
                        val styleLabel = when (style) {
                            ThemeStyle.MATERIAL -> stringResource(R.string.appearance_style_material)
                            ThemeStyle.ROUNDED -> stringResource(R.string.appearance_style_rounded)
                            ThemeStyle.HOLO -> stringResource(R.string.appearance_style_holo)
                        }
                        FilterChip(
                            selected = settings.themeStyle == style.id,
                            onClick = { viewModel.updateSettings { it.copy(themeStyle = style.id) } },
                            label = { Text(styleLabel) },
                            modifier = Modifier.testTag("style_${style.id.lowercase()}")
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.appearance_key_borders), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.appearance_key_borders_sub),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.keyBorders,
                        onCheckedChange = { checked -> viewModel.updateSettings { it.copy(keyBorders = checked) } },
                        modifier = Modifier.testTag("switch_key_borders")
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(stringResource(R.string.appearance_day_night), style = MaterialTheme.typography.bodyLarge)
                        Text(
                            stringResource(R.string.appearance_day_night_sub),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = settings.dayNightAuto,
                        onCheckedChange = { checked -> viewModel.updateSettings { it.copy(dayNightAuto = checked) } },
                        modifier = Modifier.testTag("switch_day_night")
                    )
                }
            }
        }

        // Day Theme Swatches
        ThemeSwatchSection(
            title = stringResource(if (settings.dayNightAuto) R.string.appearance_day_theme else R.string.appearance_active_theme),
            selectedThemeId = settings.themeName,
            customThemes = customThemes,
            systemDark = systemDark,
            onSelectTheme = { id -> viewModel.updateSettings { it.copy(themeName = id) } },
            onEditCustom = { editingCustomTheme = it },
            onDeleteCustom = { viewModel.deleteCustomTheme(it) },
            onCreateCustom = {
                editingCustomTheme = KeyboardThemes.defaultCustomTheme(
                    name = "Custom ${customThemes.size + 1}",
                    isDark = false
                )
            }
        )

        // Night Theme Swatches
        if (settings.dayNightAuto) {
            ThemeSwatchSection(
                title = stringResource(R.string.appearance_night_theme),
                selectedThemeId = settings.nightThemeName,
                customThemes = customThemes,
                systemDark = true,
                onSelectTheme = { id -> viewModel.updateSettings { it.copy(nightThemeName = id) } },
                onEditCustom = { editingCustomTheme = it },
                onDeleteCustom = { viewModel.deleteCustomTheme(it) },
                onCreateCustom = {
                    editingCustomTheme = KeyboardThemes.defaultCustomTheme(
                        name = "Night Custom ${customThemes.size + 1}",
                        isDark = true
                    )
                }
            )
        }

        // Dimensions & Typography Sliders
        Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Text(stringResource(R.string.appearance_geometry_title), style = MaterialTheme.typography.titleMedium)

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val heightPresets = listOf(
                        Triple("compact", 0.85f, R.string.height_preset_compact),
                        Triple("normal", 1.00f, R.string.height_preset_normal),
                        Triple("tall", 1.15f, R.string.height_preset_tall),
                        Triple("extra_tall", 1.35f, R.string.height_preset_extra_tall)
                    )
                    heightPresets.forEach { (id, scale, labelRes) ->
                        FilterChip(
                            selected = kotlin.math.abs(settings.keyboardHeightScale - scale) < 0.03f,
                            onClick = { viewModel.updateSettings { it.copy(keyboardHeightScale = scale) } },
                            label = { Text(stringResource(labelRes)) },
                            modifier = Modifier.testTag("appearance_height_preset_$id")
                        )
                    }
                }
                ScaleSliderRow(
                    title = stringResource(R.string.appearance_height),
                    value = settings.keyboardHeightScale.coerceIn(0.70f, 1.50f),
                    range = 0.70f..1.50f,
                    onValueChange = { v -> viewModel.updateSettings { it.copy(keyboardHeightScale = v) } }
                )
                ScaleSliderRow(
                    title = stringResource(R.string.appearance_gap),
                    value = settings.keyGapScale,
                    range = 0.5f..2.0f,
                    onValueChange = { v -> viewModel.updateSettings { it.copy(keyGapScale = v) } }
                )
                ScaleSliderRow(
                    title = stringResource(R.string.appearance_font_size),
                    value = settings.fontScale,
                    range = 0.7f..1.4f,
                    onValueChange = { v -> viewModel.updateSettings { it.copy(fontScale = v) } }
                )
            }
        }
    }

    editingCustomTheme?.let { spec ->
        CustomThemeEditorDialog(
            initial = spec,
            onDismiss = { editingCustomTheme = null },
            onSave = { saved ->
                viewModel.saveCustomTheme(saved)
                viewModel.updateSettings { s ->
                    if (saved.isDark && s.dayNightAuto) s.copy(nightThemeName = saved.name)
                    else s.copy(themeName = saved.name)
                }
                editingCustomTheme = null
            }
        )
    }
}

@Composable
private fun ThemeSwatchSection(
    title: String,
    selectedThemeId: String,
    customThemes: List<CustomThemeSpec>,
    systemDark: Boolean,
    onSelectTheme: (String) -> Unit,
    onEditCustom: (CustomThemeSpec) -> Unit,
    onDeleteCustom: (String) -> Unit,
    onCreateCustom: () -> Unit
) {
    val context = LocalContext.current
    Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                OutlinedButton(
                    onClick = onCreateCustom,
                    modifier = Modifier.testTag("create_custom_theme_button")
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text(stringResource(R.string.appearance_custom_btn))
                }
            }

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                KeyboardThemes.PRESET_THEMES.forEach { (id, label) ->
                    val palette = remember(id, systemDark) {
                        KeyboardThemes.resolvePalette(id, context, systemDark)
                    }
                    ThemeSwatchCard(
                        label = label,
                        bgColor = palette.background,
                        keyColor = palette.keyBackground,
                        accentColor = palette.accent,
                        selected = selectedThemeId == id,
                        onClick = { onSelectTheme(id) }
                    )
                }
            }

            if (customThemes.isNotEmpty()) {
                Text(stringResource(R.string.appearance_your_custom), style = MaterialTheme.typography.labelLarge)
                customThemes.forEach { custom ->
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (selectedThemeId == custom.name) {
                            MaterialTheme.colorScheme.primaryContainer
                        } else {
                            MaterialTheme.colorScheme.surfaceVariant
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onSelectTheme(custom.name) }
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(28.dp)
                                    .clip(CircleShape)
                                    .background(Color(custom.backgroundColor))
                                    .border(2.dp, Color(custom.accentColor), CircleShape)
                            )
                            Spacer(Modifier.width(10.dp))
                            Text(custom.name, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                            IconButton(onClick = { onEditCustom(custom) }) {
                                Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.appearance_edit_custom))
                            }
                            IconButton(onClick = { onDeleteCustom(custom.name) }) {
                                Icon(Icons.Default.Delete, contentDescription = stringResource(R.string.appearance_delete_custom))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ThemeSwatchCard(
    label: String,
    bgColor: Color,
    keyColor: Color,
    accentColor: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .width(82.dp)
            .clip(RoundedCornerShape(12.dp))
            .clickable { onClick() }
            .padding(4.dp)
    ) {
        Box(
            modifier = Modifier
                .size(width = 74.dp, height = 50.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(bgColor)
                .border(
                    width = if (selected) 2.5.dp else 1.dp,
                    color = if (selected) MaterialTheme.colorScheme.primary else Color.Gray.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(10.dp)
                )
                .padding(6.dp)
        ) {
            Row(
                modifier = Modifier.align(Alignment.Center),
                horizontalArrangement = Arrangement.spacedBy(4.dp)
            ) {
                Box(
                    Modifier
                        .size(16.dp, 18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(keyColor)
                )
                Box(
                    Modifier
                        .size(16.dp, 18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(keyColor)
                )
                Box(
                    Modifier
                        .size(16.dp, 18.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(accentColor)
                )
            }
            if (selected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.TopEnd)
                        .size(16.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Check,
                        contentDescription = stringResource(R.string.status_selected),
                        tint = MaterialTheme.colorScheme.onPrimary,
                        modifier = Modifier.size(11.dp)
                    )
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1
        )
    }
}

@Composable
private fun ScaleSliderRow(
    title: String,
    value: Float,
    range: ClosedFloatingPointRange<Float>,
    onValueChange: (Float) -> Unit
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text("${(value * 100).roundToInt()}%", style = MaterialTheme.typography.labelLarge)
        }
        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = range
        )
    }
}

@Composable
private fun CustomThemeEditorDialog(
    initial: CustomThemeSpec,
    onDismiss: () -> Unit,
    onSave: (CustomThemeSpec) -> Unit
) {
    var spec by remember { mutableStateOf(initial) }
    val presetSwatches = listOf(
        Color(0xFF0284C7), Color(0xFF2563EB), Color(0xFF7C3AED), Color(0xFFDB2777),
        Color(0xFF059669), Color(0xFFD97706), Color(0xFFDC2626), Color(0xFF0F172A),
        Color(0xFF1E293B), Color(0xFF334155), Color(0xFFE2E8F0), Color(0xFFFFFFFF)
    )
    var selectedSlot by remember { mutableStateOf("accent") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.appearance_custom_editor)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = spec.name,
                    onValueChange = { spec = spec.copy(name = it) },
                    label = { Text(stringResource(R.string.appearance_theme_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.appearance_dark_palette))
                    Switch(
                        checked = spec.isDark,
                        onCheckedChange = { spec = spec.copy(isDark = it) }
                    )
                }

                val slots = listOf(
                    "accent" to (stringResource(R.string.appearance_slot_accent) to spec.accentColor),
                    "bg" to (stringResource(R.string.appearance_slot_bg) to spec.backgroundColor),
                    "key" to (stringResource(R.string.appearance_slot_keys) to spec.keyColor),
                    "func" to (stringResource(R.string.appearance_slot_func) to spec.functionalKeyColor),
                    "text" to (stringResource(R.string.appearance_slot_text) to spec.textColor)
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    slots.forEach { (id, pair) ->
                        FilterChip(
                            selected = selectedSlot == id,
                            onClick = { selectedSlot = id },
                            label = { Text(pair.first) },
                            leadingIcon = {
                                Box(
                                    Modifier
                                        .size(14.dp)
                                        .clip(CircleShape)
                                        .background(Color(pair.second))
                                )
                            }
                        )
                    }
                }

                Text(stringResource(R.string.appearance_pick_slot_color), style = MaterialTheme.typography.labelLarge)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    presetSwatches.forEach { color ->
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(color)
                                .border(1.dp, Color.Gray, CircleShape)
                                .clickable {
                                    val argb = color.toArgb()
                                    spec = when (selectedSlot) {
                                        "accent" -> spec.copy(accentColor = argb)
                                        "bg" -> spec.copy(backgroundColor = argb)
                                        "key" -> spec.copy(keyColor = argb, spaceBarColor = argb)
                                        "func" -> spec.copy(functionalKeyColor = argb)
                                        "text" -> spec.copy(textColor = argb, hintTextColor = argb)
                                        else -> spec
                                    }
                                }
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = { if (spec.name.isNotBlank()) onSave(spec) },
                enabled = spec.name.isNotBlank()
            ) {
                Text(stringResource(R.string.appearance_save_theme))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        }
    )
}
