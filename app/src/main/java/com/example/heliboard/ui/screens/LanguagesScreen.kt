package com.example.heliboard.ui.screens

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.heliboard.data.LayoutInfo
import com.example.heliboard.data.SubtypeConfig
import com.example.heliboard.theme.JetBrainsMonoFamily
import com.example.heliboard.ui.HeliBoardViewModel

@Composable
fun LanguagesScreen(
    viewModel: HeliBoardViewModel,
    modifier: Modifier = Modifier
) {
    val settings by viewModel.settings.collectAsState()
    val customLayouts by viewModel.customLayouts.collectAsState()
    val layouts = viewModel.availableLayouts

    var pickingLayoutForLocale by remember { mutableStateOf<String?>(null) }
    var editingLayoutId by remember { mutableStateOf<String?>(null) }
    var showAddLanguageDialog by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Languages & Layouts", style = MaterialTheme.typography.headlineMedium)
                Text(
                    "${layouts.size} built-in layouts available",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Button(
                onClick = { showAddLanguageDialog = true },
                modifier = Modifier.testTag("add_language_button")
            ) {
                Icon(Icons.Default.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(4.dp))
                Text("Add")
            }
        }

        settings.subtypes.forEach { subtype ->
            val isActive = settings.selectedSubtypeLocale == subtype.localeTag
            val hasCustom = customLayouts.containsKey(subtype.layoutId)
            Card(
                shape = RoundedCornerShape(16.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isActive) {
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                    } else {
                        MaterialTheme.colorScheme.surfaceVariant
                    }
                ),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        if (subtype.isEnabled) viewModel.selectActiveSubtype(subtype.localeTag)
                    }
            ) {
                Column(
                    modifier = Modifier.padding(14.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(subtype.displayName, style = MaterialTheme.typography.titleMedium)
                                if (isActive) {
                                    Spacer(Modifier.width(8.dp))
                                    Text(
                                        "ACTIVE",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.primary,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Text(
                                text = "Layout: ${subtype.layoutId}" + if (hasCustom) " (customized)" else "",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = subtype.isEnabled,
                            onCheckedChange = { viewModel.toggleSubtypeEnabled(subtype.localeTag) }
                        )
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(
                            onClick = { pickingLayoutForLocale = subtype.localeTag }
                        ) {
                            Text("Change Layout")
                        }
                        OutlinedButton(
                            onClick = { editingLayoutId = subtype.layoutId }
                        ) {
                            Icon(Icons.Default.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(4.dp))
                            Text("Edit Keys")
                        }
                        if (settings.subtypes.size > 1) {
                            IconButton(onClick = { viewModel.removeSubtype(subtype.localeTag) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Remove language")
                            }
                        }
                    }
                }
            }
        }
    }

    pickingLayoutForLocale?.let { localeTag ->
        LayoutPickerDialog(
            layouts = layouts,
            currentLayoutId = settings.subtypes.firstOrNull { it.localeTag == localeTag }?.layoutId.orEmpty(),
            onSelect = { layoutId ->
                viewModel.setSubtypeLayout(localeTag, layoutId)
                pickingLayoutForLocale = null
            },
            onDismiss = { pickingLayoutForLocale = null }
        )
    }

    editingLayoutId?.let { layoutId ->
        val initialSource = remember(layoutId, customLayouts) {
            viewModel.assetRepo.readLayoutSource(layoutId, customLayouts)
        }
        LayoutSourceEditorDialog(
            layoutId = layoutId,
            initialSource = initialSource,
            isModified = customLayouts.containsKey(layoutId),
            onSave = { updated ->
                viewModel.saveCustomLayout(layoutId, updated)
                editingLayoutId = null
            },
            onReset = {
                viewModel.resetCustomLayout(layoutId)
                editingLayoutId = null
            },
            onDismiss = { editingLayoutId = null }
        )
    }

    if (showAddLanguageDialog) {
        AddSubtypeDialog(
            existingLocales = settings.subtypes.map { it.localeTag }.toSet(),
            onAdd = { sub ->
                viewModel.addOrUpdateSubtype(sub)
                showAddLanguageDialog = false
            },
            onDismiss = { showAddLanguageDialog = false }
        )
    }
}

@Composable
private fun LayoutPickerDialog(
    layouts: List<LayoutInfo>,
    currentLayoutId: String,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var query by remember { mutableStateOf("") }
    val filtered = remember(query, layouts) {
        if (query.isBlank()) layouts
        else layouts.filter {
            it.displayName.contains(query, ignoreCase = true) ||
                it.id.contains(query, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Choose Keyboard Layout") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search ${layouts.size} layouts...") },
                    leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(filtered, key = { it.id }) { info ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onSelect(info.id) }
                                .padding(vertical = 10.dp, horizontal = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(info.displayName, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    text = info.id,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (info.id == currentLayoutId) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = "Selected",
                                    tint = MaterialTheme.colorScheme.primary
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
private fun LayoutSourceEditorDialog(
    layoutId: String,
    initialSource: String,
    isModified: Boolean,
    onSave: (String) -> Unit,
    onReset: () -> Unit,
    onDismiss: () -> Unit
) {
    var text by remember(initialSource) { mutableStateOf(initialSource) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Edit Layout: $layoutId") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Each line is a key (with optional popup keys separated by spaces). Blank lines separate rows.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    textStyle = TextStyle(fontFamily = JetBrainsMonoFamily, fontSize = 12.sp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(260.dp)
                )
                if (isModified) {
                    TextButton(onClick = onReset) {
                        Icon(Icons.Default.Restore, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Reset to Default")
                    }
                }
            }
        },
        confirmButton = {
            Button(onClick = { onSave(text) }, enabled = text.isNotBlank()) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
private fun AddSubtypeDialog(
    existingLocales: Set<String>,
    onAdd: (SubtypeConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val catalog = remember {
        listOf(
            SubtypeConfig("en-US", "English (US)", "qwerty", true),
            SubtypeConfig("en-GB", "English (UK)", "qwerty", true),
            SubtypeConfig("de-DE", "German", "qwertz", true),
            SubtypeConfig("fr-FR", "French", "azerty", true),
            SubtypeConfig("es-ES", "Spanish", "qwerty", true),
            SubtypeConfig("it-IT", "Italian", "qwerty", true),
            SubtypeConfig("pt-BR", "Portuguese (Brazil)", "qwerty", true),
            SubtypeConfig("nl-NL", "Dutch", "qwerty", true),
            SubtypeConfig("pl-PL", "Polish", "qwerty", true),
            SubtypeConfig("ru-RU", "Russian", "russian", true),
            SubtypeConfig("uk-UA", "Ukrainian", "ukrainian", true),
            SubtypeConfig("ar-SA", "Arabic", "arabic", true),
            SubtypeConfig("fa-IR", "Persian (Farsi)", "farsi", true),
            SubtypeConfig("hi-IN", "Hindi", "hindi", true),
            SubtypeConfig("bn-BD", "Bengali", "bengali_unijoy", true),
            SubtypeConfig("ko-KR", "Korean", "korean", true),
            SubtypeConfig("el-GR", "Greek", "greek", true),
            SubtypeConfig("he-IL", "Hebrew", "hebrew", true),
            SubtypeConfig("tr-TR", "Turkish", "turkish", true),
            SubtypeConfig("th-TH", "Thai", "thai", true)
        )
    }
    val available = catalog.filterNot { it.localeTag in existingLocales }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add Language") },
        text = {
            if (available.isEmpty()) {
                Text("All preset languages are already added. You can change any language's layout from the list.")
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                    items(available, key = { it.localeTag }) { sub ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onAdd(sub) }
                                .padding(vertical = 10.dp, horizontal = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(sub.displayName, style = MaterialTheme.typography.bodyLarge)
                                Text(
                                    "${sub.localeTag} • ${sub.layoutId}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            Icon(Icons.Default.Add, contentDescription = "Add ${sub.displayName}")
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
