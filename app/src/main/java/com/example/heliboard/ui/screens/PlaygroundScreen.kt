package com.example.heliboard.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.example.heliboard.R
import com.example.heliboard.ime.HeliKeyboardView
import com.example.heliboard.theme.KeyboardThemes
import com.example.heliboard.ui.HeliBoardViewModel

@Composable
fun PlaygroundScreen(
    viewModel: HeliBoardViewModel,
    onOpenThemes: () -> Unit,
    onOpenLanguages: () -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val settings by viewModel.settings.collectAsState()
    val customThemes by viewModel.customThemes.collectAsState()
    val clipboard by viewModel.clipboard.collectAsState()
    val keyboardUiState by viewModel.keyboardUiState.collectAsState()
    val sandboxText by viewModel.sandboxText.collectAsState()
    val acceptsImages by viewModel.sandboxTarget.acceptsImages.collectAsState()
    val pastedImages by viewModel.sandboxTarget.pastedImages.collectAsState()
    val imeStatus by viewModel.imeStatus.collectAsState()

    val audioPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        viewModel.sandboxController.onVoicePermissionResult(granted)
    }

    DisposableEffect(viewModel.sandboxController) {
        viewModel.sandboxController.onRequestAudioPermission = {
            audioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
        onDispose {
            viewModel.sandboxController.onRequestAudioPermission = null
        }
    }

    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshImeStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val activeSubtype = settings.subtypes.firstOrNull {
        it.localeTag == settings.selectedSubtypeLocale && it.isEnabled
    } ?: settings.subtypes.first()

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        // Scrollable top workspace: hero + IME setup + sandbox editor
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Hero Banner
            Card(
                shape = RoundedCornerShape(18.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(132.dp)
                ) {
                    Image(
                        painter = painterResource(R.drawable.img_keyboard_hero),
                        contentDescription = stringResource(R.string.app_name),
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.horizontalGradient(
                                    listOf(
                                        Color(0xE60F172A),
                                        Color(0x990F172A),
                                        Color(0x330F172A)
                                    )
                                )
                            )
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.SpaceBetween
                    ) {
                        Surface(
                            color = Color(0xFF0EA5E9).copy(alpha = 0.22f),
                            shape = RoundedCornerShape(20.dp)
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Security,
                                    contentDescription = null,
                                    tint = Color(0xFF7DD3FC),
                                    modifier = Modifier.size(14.dp)
                                )
                                Text(
                                    text = stringResource(R.string.hero_badge_offline),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = Color(0xFFE0F2FE)
                                )
                            }
                        }
                        Column {
                            Text(
                                text = stringResource(R.string.app_name),
                                style = MaterialTheme.typography.headlineMedium,
                                color = Color.White
                            )
                            Text(
                                text = stringResource(R.string.hero_subtitle),
                                style = MaterialTheme.typography.bodyMedium,
                                color = Color(0xFFCBD5E1)
                            )
                        }
                    }
                }
            }

            // System IME Setup Status Card
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (imeStatus.isEnabled && imeStatus.isSelected) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Icon(
                            imageVector = if (imeStatus.isEnabled && imeStatus.isSelected) {
                                Icons.Default.CheckCircle
                            } else {
                                Icons.Default.Keyboard
                            },
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = when {
                                    imeStatus.isEnabled && imeStatus.isSelected -> stringResource(R.string.ime_status_active)
                                    imeStatus.isEnabled -> stringResource(R.string.ime_status_step2)
                                    else -> stringResource(R.string.ime_status_step1)
                                },
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = if (imeStatus.isEnabled && imeStatus.isSelected) {
                                    stringResource(R.string.ime_status_active_sub)
                                } else {
                                    stringResource(R.string.ime_status_setup_sub)
                                },
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { viewModel.openSystemImeSettings(context) },
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                            modifier = Modifier.testTag("enable_ime_button")
                        ) {
                            Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(
                                if (imeStatus.isEnabled) stringResource(R.string.ime_settings_button)
                                else stringResource(R.string.ime_enable_keyboard)
                            )
                        }
                        if (imeStatus.isEnabled) {
                            OutlinedButton(
                                onClick = { viewModel.showInputMethodPicker(context) },
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                modifier = Modifier.testTag("select_ime_button")
                            ) {
                                Icon(Icons.Default.SwapHoriz, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(stringResource(R.string.ime_switch_input))
                            }
                        }
                    }
                }
            }

            // Quick Customization Chips
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected = false,
                    onClick = onOpenLanguages,
                    label = { Text("${activeSubtype.displayName} • ${activeSubtype.layoutId}") },
                    leadingIcon = { Icon(Icons.Default.Language, contentDescription = null, Modifier.size(16.dp)) }
                )
                val currentThemeLabel = KeyboardThemes.PRESET_THEMES
                    .firstOrNull { it.first == settings.themeName }?.second ?: settings.themeName
                FilterChip(
                    selected = false,
                    onClick = onOpenThemes,
                    label = { Text(stringResource(R.string.sandbox_theme_chip, currentThemeLabel)) },
                    leadingIcon = { Icon(Icons.Default.Palette, contentDescription = null, Modifier.size(16.dp)) }
                )
                FilterChip(
                    selected = settings.showNumberRow,
                    onClick = { viewModel.updateSettings { it.copy(showNumberRow = !it.showNumberRow) } },
                    label = { Text(stringResource(R.string.sandbox_number_row)) }
                )
                FilterChip(
                    selected = settings.oneHandedMode,
                    onClick = { viewModel.updateSettings { it.copy(oneHandedMode = !it.oneHandedMode) } },
                    label = { Text(stringResource(R.string.sandbox_one_handed)) }
                )
                FilterChip(
                    selected = settings.splitKeyboard,
                    onClick = { viewModel.updateSettings { it.copy(splitKeyboard = !it.splitKeyboard) } },
                    label = { Text(stringResource(R.string.sandbox_split)) }
                )
                FilterChip(
                    selected = acceptsImages,
                    onClick = { viewModel.sandboxTarget.setAcceptsImages(!acceptsImages) },
                    label = { Text(stringResource(R.string.sandbox_accepts_images)) },
                    leadingIcon = { Icon(Icons.Default.Image, contentDescription = null, Modifier.size(16.dp)) },
                    modifier = Modifier.testTag("sandbox_accepts_images_chip")
                )
            }

            // Live Interactive Sandbox Field
            OutlinedTextField(
                value = sandboxText,
                onValueChange = { viewModel.sandboxTarget.setExternalValue(it) },
                label = { Text(stringResource(R.string.sandbox_label)) },
                placeholder = { Text(stringResource(R.string.sandbox_placeholder)) },
                trailingIcon = {
                    if (sandboxText.text.isNotEmpty()) {
                        IconButton(onClick = { viewModel.sandboxTarget.setExternalValue(TextFieldValue("")) }) {
                            Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.sandbox_clear_desc))
                        }
                    }
                },
                minLines = 2,
                maxLines = 4,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sandbox_text_field")
            )

            if (pastedImages.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.sandbox_pasted_images_label, pastedImages.size),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary
                    )
                    TextButton(onClick = { viewModel.sandboxTarget.clearPastedImages() }) {
                        Text(stringResource(R.string.clipboard_clear))
                    }
                }
            }
        }

        // Embedded Live HeliBoard Keyboard
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
