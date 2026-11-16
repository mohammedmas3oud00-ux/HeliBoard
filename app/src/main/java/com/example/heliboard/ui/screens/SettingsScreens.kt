package com.example.heliboard.ui.screens

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
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
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.Book
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Spellcheck
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.heliboard.BuildConfig
import com.example.heliboard.data.PersonalWordEntry
import com.example.heliboard.data.ToolbarKey
import com.example.heliboard.data.ToolbarMode
import com.example.heliboard.ime.iconForToolbarKey
import com.example.heliboard.ui.HeliBoardViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsHubScreen(
    onOpenPreferences: () -> Unit,
    onOpenToolbar: () -> Unit,
    onOpenTextCorrection: () -> Unit,
    onOpenPersonalDictionary: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenAbout: () -> Unit,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text("Settings", style = MaterialTheme.typography.headlineMedium)

        SettingsNavCard(
            icon = Icons.Default.Tune,
            title = "Preferences",
            subtitle = "Number row, vibration, sound, key popup, one-handed & split",
            onClick = onOpenPreferences,
            tag = "settings_preferences"
        )
        SettingsNavCard(
            icon = Icons.Default.Build,
            title = "Toolbar",
            subtitle = "Toolbar visibility, enabled buttons & custom key order",
            onClick = onOpenToolbar,
            tag = "settings_toolbar"
        )
        SettingsNavCard(
            icon = Icons.Default.Spellcheck,
            title = "Text Correction",
            subtitle = "Autocorrect, auto-cap, suggestions, emoji match & learned words",
            onClick = onOpenTextCorrection,
            tag = "settings_text_correction"
        )
        SettingsNavCard(
            icon = Icons.Default.Book,
            title = "Personal Dictionary",
            subtitle = "Custom words, typing shortcuts & wordlist import",
            onClick = onOpenPersonalDictionary,
            tag = "settings_personal_dictionary"
        )
        SettingsNavCard(
            icon = Icons.Default.Backup,
            title = "Backup & Restore",
            subtitle = "Export or import settings, custom themes, layouts & words",
            onClick = onOpenBackup,
            tag = "settings_backup"
        )
        SettingsNavCard(
            icon = Icons.Default.Info,
            title = "About HeliBoard",
            subtitle = "Offline privacy architecture & open-source licenses",
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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
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
    SubSettingsScaffold(title = "Preferences", onBack = onBack) { mod ->
        Column(
            modifier = mod
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SwitchRow("Show Number Row", "Dedicated 1–0 row above letters", settings.showNumberRow) {
                viewModel.updateSettings { s -> s.copy(showNumberRow = it) }
            }
            SwitchRow("Show Key Hints", "Display popup accent hint in key corner", settings.showHints) {
                viewModel.updateSettings { s -> s.copy(showHints = it) }
            }
            SwitchRow("Popup on Keypress", "Show floating preview bubble when tapping keys", settings.keyPopupEnabled) {
                viewModel.updateSettings { s -> s.copy(keyPopupEnabled = it) }
            }
            SwitchRow("Vibrate on Keypress", "Haptic feedback on key tap", settings.vibrateOnKeypress) {
                viewModel.updateSettings { s -> s.copy(vibrateOnKeypress = it) }
            }
            SwitchRow("Sound on Keypress", "System keypress audio click", settings.soundOnKeypress) {
                viewModel.updateSettings { s -> s.copy(soundOnKeypress = it) }
            }
            SwitchRow("Spacebar Swipe Moves Cursor", "Slide horizontally on spacebar to move cursor", settings.spacebarSwipeCursor) {
                viewModel.updateSettings { s -> s.copy(spacebarSwipeCursor = it) }
            }
            SwitchRow("Show Language Switch Key", "Display globe key when multiple languages enabled", settings.showLanguageSwitchKey) {
                viewModel.updateSettings { s -> s.copy(showLanguageSwitchKey = it) }
            }
            SwitchRow("Show Emoji Key", "Display dedicated emoji button on bottom row", settings.showEmojiKey) {
                viewModel.updateSettings { s -> s.copy(showEmojiKey = it) }
            }
            SwitchRow("One-Handed Mode", "Compact keyboard aligned to left or right edge", settings.oneHandedMode) {
                viewModel.updateSettings { s -> s.copy(oneHandedMode = it) }
            }
            SwitchRow("Split Keyboard", "Add center gap between left and right hand keys", settings.splitKeyboard) {
                viewModel.updateSettings { s -> s.copy(splitKeyboard = it) }
            }
            Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Long-Press Delay", style = MaterialTheme.typography.bodyLarge)
                        Text("${settings.longPressTimeoutMs} ms", style = MaterialTheme.typography.labelLarge)
                    }
                    Slider(
                        value = settings.longPressTimeoutMs.toFloat(),
                        onValueChange = { v -> viewModel.updateSettings { it.copy(longPressTimeoutMs = v.roundToInt()) } },
                        valueRange = 150f..600f
                    )
                }
            }
        }
    }
}

@Composable
fun ToolbarScreen(viewModel: HeliBoardViewModel, onBack: () -> Unit) {
    val settings by viewModel.settings.collectAsState()
    SubSettingsScaffold(title = "Toolbar Customization", onBack = onBack) { mod ->
        Column(
            modifier = mod
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text("Toolbar Mode", style = MaterialTheme.typography.titleMedium)
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ToolbarMode.entries.forEach { mode ->
                    FilterChip(
                        selected = settings.toolbarMode == mode.id,
                        onClick = { viewModel.updateSettings { it.copy(toolbarMode = mode.id) } },
                        label = { Text(mode.displayName) }
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Toolbar Buttons & Order", style = MaterialTheme.typography.titleMedium)
                TextButton(onClick = { viewModel.resetToolbarKeys() }) {
                    Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Reset")
                }
            }

            // Ordered enabled keys first
            settings.toolbarKeys.forEachIndexed { idx, keyName ->
                val tk = runCatching { ToolbarKey.valueOf(keyName) }.getOrNull() ?: return@forEachIndexed
                Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(iconForToolbarKey(tk), contentDescription = null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(10.dp))
                        Text(tk.label, modifier = Modifier.weight(1f), fontWeight = FontWeight.Medium)
                        IconButton(onClick = { viewModel.moveToolbarKey(keyName, -1) }, enabled = idx > 0) {
                            Icon(Icons.Default.ArrowUpward, contentDescription = "Move up")
                        }
                        IconButton(
                            onClick = { viewModel.moveToolbarKey(keyName, 1) },
                            enabled = idx < settings.toolbarKeys.lastIndex
                        ) {
                            Icon(Icons.Default.ArrowDownward, contentDescription = "Move down")
                        }
                        Switch(checked = true, onCheckedChange = { viewModel.toggleToolbarKey(tk) })
                    }
                }
            }

            // Disabled keys
            val disabled = ToolbarKey.entries.filterNot { it.name in settings.toolbarKeys }
            if (disabled.isNotEmpty()) {
                Text("Available Buttons", style = MaterialTheme.typography.labelLarge)
                disabled.forEach { tk ->
                    Card(shape = RoundedCornerShape(12.dp), modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(iconForToolbarKey(tk), contentDescription = null, modifier = Modifier.size(20.dp))
                            Spacer(Modifier.width(10.dp))
                            Text(tk.label, modifier = Modifier.weight(1f))
                            Switch(checked = false, onCheckedChange = { viewModel.toggleToolbarKey(tk) })
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
                Toast.makeText(context, "Imported $count dictionary words", Toast.LENGTH_SHORT).show()
            }
        }
    }

    SubSettingsScaffold(title = "Text Correction & Dictionaries", onBack = onBack) { mod ->
        Column(
            modifier = mod
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SwitchRow("Show Suggestions", "Suggest word completions and next words", settings.showSuggestions) {
                viewModel.updateSettings { s -> s.copy(showSuggestions = it) }
            }
            SwitchRow("Auto-Correction", "Automatically fix typos when pressing Space or punctuation", settings.autoCorrection) {
                viewModel.updateSettings { s -> s.copy(autoCorrection = it) }
            }
            SwitchRow("Auto-Capitalization", "Capitalize the first word of each sentence", settings.autoCap) {
                viewModel.updateSettings { s -> s.copy(autoCap = it) }
            }
            SwitchRow("Double-Space Period", "Double-tap spacebar to insert '. '", settings.doubleSpacePeriod) {
                viewModel.updateSettings { s -> s.copy(doubleSpacePeriod = it) }
            }
            SwitchRow("Inline Emoji Suggestions", "Show matching emojis in the suggestion strip", settings.suggestEmojis) {
                viewModel.updateSettings { s -> s.copy(suggestEmojis = it) }
            }
            SwitchRow("Incognito Mode", "Pause learning new typed words", settings.incognitoMode) {
                viewModel.updateSettings { s -> s.copy(incognitoMode = it) }
            }

            Card(shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Dictionaries & Learned Words", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Learned words this session: $learnedCount • Imported wordlist entries: $customDictCount",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = onOpenPersonalDictionary) {
                            Text("Personal Dictionary")
                        }
                        OutlinedButton(onClick = { importWordlistLauncher.launch(arrayOf("text/*")) }) {
                            Icon(Icons.Default.UploadFile, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Import Wordlist")
                        }
                    }
                    if (learnedCount > 0) {
                        TextButton(onClick = { viewModel.clearLearnedWords() }) {
                            Text("Clear Learned Words")
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
        title = "Personal Dictionary",
        onBack = onBack,
        floatingActionButton = {
            FloatingActionButton(
                onClick = { editingWord = PersonalWordEntry(0L, "", "", 250, "") },
                modifier = Modifier.testTag("add_personal_word_fab")
            ) {
                Icon(Icons.Default.Add, contentDescription = "Add word")
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
                placeholder = { Text("Search personal words or shortcuts...") },
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
                                val sub = buildString {
                                    append("Weight ${entry.weight}")
                                    if (entry.shortcut.isNotBlank()) append("  •  Shortcut: ${entry.shortcut}")
                                }
                                Text(
                                    sub,
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { editingWord = entry }) {
                                Icon(Icons.Default.Edit, contentDescription = "Edit word")
                            }
                            IconButton(onClick = { viewModel.deletePersonalWord(entry.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete word")
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
            title = { Text(if (current.id == 0L) "Add Word" else "Edit Word") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = word,
                        onValueChange = { word = it },
                        label = { Text("Word or Phrase") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = shortcut,
                        onValueChange = { shortcut = it },
                        label = { Text("Optional Shortcut (e.g. omw)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = weight,
                        onValueChange = { weight = it.filter { c -> c.isDigit() } },
                        label = { Text("Priority Weight (1–255)") },
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
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingWord = null }) { Text("Cancel") }
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
                statusMessage = "Backup exported successfully."
            }.onFailure {
                statusMessage = "Export failed: ${it.localizedMessage}"
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
                    .onFailure { err -> statusMessage = "Invalid backup file: ${err.localizedMessage}" }
            }
        }
    }

    SubSettingsScaffold(title = "Backup & Restore", onBack = onBack) { mod ->
        Column(
            modifier = mod
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Local JSON Backup", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Export or restore all preferences, custom themes, custom layouts, personal dictionary words, and pinned clipboard entries.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(onClick = { exportLauncher.launch("heliboard_backup.json") }) {
                            Text("Export Backup")
                        }
                        OutlinedButton(onClick = { importLauncher.launch(arrayOf("application/json", "text/*")) }) {
                            Text("Restore Backup")
                        }
                    }
                    OutlinedButton(onClick = {
                        viewModel.prefsRepo.copyToSystemClipboard(viewModel.prefsRepo.exportBackupJson())
                        Toast.makeText(context, "Backup JSON copied to clipboard", Toast.LENGTH_SHORT).show()
                    }) {
                        Text("Copy Backup JSON to Clipboard")
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
    SubSettingsScaffold(title = "About HeliBoard", onBack = onBack) { mod ->
        Column(
            modifier = mod
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("HeliBoard", style = MaterialTheme.typography.headlineMedium)
                    Text("Version ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        "Privacy-conscious, customizable open-source Android keyboard based on AOSP / OpenBoard and rewritten in Kotlin & Jetpack Compose.",
                        style = MaterialTheme.typography.bodyLarge
                    )
                }
            }
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Offline Privacy Guarantee", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "HeliBoard declares zero INTERNET permissions. All layouts, emoji tables, suggestions, clipboard history, and personal dictionary entries stay 100% on your device.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            Card(shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Open-Source Licenses", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Licensed under GNU General Public License v3.0 (GPL-3.0) with AOSP Keyboard components under Apache License 2.0.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
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
