package com.example.heliboard.ime

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.filled.Backspace
import androidx.compose.material.icons.automirrored.filled.Redo
import androidx.compose.material.icons.automirrored.filled.Undo
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.LastPage
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.heliboard.data.ClipboardEntry
import com.example.heliboard.data.EmojiCategoryData
import com.example.heliboard.data.HeliSettings
import com.example.heliboard.data.KeyActionType
import com.example.heliboard.data.KeySpec
import com.example.heliboard.data.KeyboardMode
import com.example.heliboard.data.ToolbarKey
import com.example.heliboard.theme.KeyboardColorPalette

@Composable
fun EmojiPanel(
    palette: KeyboardColorPalette,
    settings: HeliSettings,
    categories: List<EmojiCategoryData>,
    onSelectEmoji: (String) -> Unit,
    onBackToAlpha: () -> Unit,
    onBackspace: () -> Unit
) {
    var selectedTab by remember { mutableIntStateOf(0) }
    var searchQuery by remember { mutableStateOf("") }

    val allTabs = remember(categories, settings.recentEmojis) {
        listOf(
            EmojiCategoryData("RECENTS", "Recent", "recent", settings.recentEmojis)
        ) + categories
    }

    val currentEmojis = remember(selectedTab, searchQuery, allTabs) {
        if (searchQuery.isNotBlank()) {
            allTabs.drop(1).flatMap { it.emojis }.distinct().take(120)
        } else {
            allTabs.getOrNull(selectedTab)?.emojis.orEmpty()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .background(palette.background)
            .padding(horizontal = 6.dp, vertical = 4.dp)
    ) {
        // Search + category strip
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .weight(1f)
                    .height(34.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(palette.functionalKeyBackground)
                    .padding(horizontal = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Search,
                    contentDescription = "Filter emojis",
                    tint = palette.keyHintText,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Box(modifier = Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                    if (searchQuery.isEmpty()) {
                        Text(
                            text = allTabs.getOrNull(selectedTab)?.title ?: "Emojis",
                            color = palette.keyHintText,
                            fontSize = 13.sp
                        )
                    }
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = { searchQuery = it },
                        singleLine = true,
                        textStyle = TextStyle(color = palette.keyText, fontSize = 13.sp),
                        cursorBrush = SolidColor(palette.accent),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (searchQuery.isNotEmpty()) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = "Clear filter",
                        tint = palette.keyHintText,
                        modifier = Modifier
                            .size(16.dp)
                            .clickable { searchQuery = "" }
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Text(
                text = "${currentEmojis.size}",
                color = palette.keyHintText,
                fontSize = 11.sp,
                modifier = Modifier.padding(horizontal = 4.dp)
            )
        }

        // Category tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState())
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            allTabs.forEachIndexed { idx, cat ->
                val active = idx == selectedTab && searchQuery.isEmpty()
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(if (active) palette.accent else palette.functionalKeyBackground)
                        .clickable {
                            searchQuery = ""
                            selectedTab = idx
                        }
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = cat.title,
                        color = if (active) palette.actionKeyIcon else palette.keyText,
                        fontSize = 12.sp,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Medium
                    )
                }
            }
        }

        // Emoji grid
        LazyVerticalGrid(
            columns = GridCells.Adaptive(minSize = 42.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
            contentPadding = PaddingValues(2.dp)
        ) {
            items(currentEmojis) { emoji ->
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .clickable { onSelectEmoji(emoji) },
                    contentAlignment = Alignment.Center
                ) {
                    Text(text = emoji, fontSize = 22.sp)
                }
            }
        }

        // Bottom bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = palette.functionalKeyBackground,
                modifier = Modifier
                    .height(38.dp)
                    .clickable { onBackToAlpha() }
                    .testTag("emoji_back_abc")
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("ABC", color = palette.keyText, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                }
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = palette.functionalKeyBackground,
                modifier = Modifier
                    .height(38.dp)
                    .clickable { onBackspace() }
                    .testTag("emoji_backspace")
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 18.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.AutoMirrored.Filled.Backspace,
                        contentDescription = "Backspace",
                        tint = palette.keyText,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun ClipboardPanel(
    palette: KeyboardColorPalette,
    entries: List<ClipboardEntry>,
    onPaste: (String) -> Unit,
    onTogglePin: (Long) -> Unit,
    onDelete: (Long) -> Unit,
    onClearUnpinned: () -> Unit,
    onBackToAlpha: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .background(palette.background)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "Clipboard History (${entries.size})",
                color = palette.keyText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp
            )
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = palette.functionalKeyBackground,
                    modifier = Modifier
                        .height(32.dp)
                        .clickable { onClearUnpinned() }
                        .testTag("clipboard_clear_unpinned")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.DeleteSweep,
                            contentDescription = "Clear unpinned",
                            tint = palette.keyText,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text("Clear", color = palette.keyText, fontSize = 12.sp)
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = palette.accent,
                    modifier = Modifier
                        .height(32.dp)
                        .clickable { onBackToAlpha() }
                        .testTag("clipboard_back_abc")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("ABC", color = palette.actionKeyIcon, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
        }

        if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "Copied text will appear here automatically.",
                    color = palette.keyHintText,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(entries, key = { it.id }) { item ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = palette.keyBackground,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPaste(item.text) }
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = item.text,
                                color = palette.keyText,
                                fontSize = 13.sp,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { onTogglePin(item.id) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = if (item.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                                    contentDescription = if (item.isPinned) "Unpin clip" else "Pin clip",
                                    tint = if (item.isPinned) palette.accent else palette.keyHintText,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                            IconButton(
                                onClick = { onDelete(item.id) },
                                modifier = Modifier.size(34.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Delete,
                                    contentDescription = "Delete clip",
                                    tint = palette.keyHintText,
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

@Composable
fun DpadPanel(
    palette: KeyboardColorPalette,
    onAction: (String) -> Unit,
    onUndo: () -> Unit,
    onRedo: () -> Unit,
    onBackToAlpha: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(250.dp)
            .background(palette.background)
            .padding(10.dp),
        verticalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DpadButton(palette, Icons.Default.SelectAll, "Select All", Modifier.weight(1f)) { onAction("SELECT_ALL") }
            DpadButton(palette, Icons.Default.ContentCut, "Cut", Modifier.weight(1f)) { onAction("CUT") }
            DpadButton(palette, Icons.Default.ContentCopy, "Copy", Modifier.weight(1f)) { onAction("COPY") }
            DpadButton(palette, Icons.Default.ContentPaste, "Paste", Modifier.weight(1f)) { onAction("PASTE") }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            DpadButton(palette, Icons.Default.FirstPage, "Home", Modifier.weight(1f)) { onAction("HOME") }
            DpadButton(palette, Icons.AutoMirrored.Filled.Undo, "Undo", Modifier.weight(1f)) { onUndo() }
            DpadButton(palette, Icons.Default.ArrowUpward, "Up", Modifier.weight(1f), highlighted = true) { onAction("UP") }
            DpadButton(palette, Icons.AutoMirrored.Filled.Redo, "Redo", Modifier.weight(1f)) { onRedo() }
            DpadButton(palette, Icons.Default.LastPage, "End", Modifier.weight(1f)) { onAction("END") }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            DpadButton(palette, null, "Word ◀", Modifier.weight(1f)) { onAction("WORD_LEFT") }
            DpadButton(palette, Icons.AutoMirrored.Filled.ArrowBack, "Left", Modifier.weight(1f), highlighted = true) { onAction("LEFT") }
            DpadButton(palette, Icons.Default.ArrowDownward, "Down", Modifier.weight(1f), highlighted = true) { onAction("DOWN") }
            DpadButton(palette, Icons.AutoMirrored.Filled.ArrowForward, "Right", Modifier.weight(1f), highlighted = true) { onAction("RIGHT") }
            DpadButton(palette, null, "▶ Word", Modifier.weight(1f)) { onAction("WORD_RIGHT") }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = palette.accent,
                modifier = Modifier
                    .height(40.dp)
                    .clickable { onBackToAlpha() }
                    .testTag("dpad_back_abc")
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text("ABC", color = palette.actionKeyIcon, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                }
            }
            Text(
                text = "Cursor & Text Editing",
                color = palette.keyHintText,
                fontSize = 12.sp,
                modifier = Modifier.align(Alignment.CenterVertically)
            )
        }
    }
}

@Composable
private fun DpadButton(
    palette: KeyboardColorPalette,
    icon: ImageVector?,
    label: String,
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    onClick: () -> Unit
) {
    Surface(
        shape = RoundedCornerShape(10.dp),
        color = if (highlighted) palette.keyBackground else palette.functionalKeyBackground,
        modifier = modifier
            .height(46.dp)
            .clickable { onClick() }
    ) {
        Box(contentAlignment = Alignment.Center) {
            if (icon != null) {
                Icon(
                    imageVector = icon,
                    contentDescription = label,
                    tint = palette.keyText,
                    modifier = Modifier.size(20.dp)
                )
            } else {
                Text(
                    text = label,
                    color = palette.keyText,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}
