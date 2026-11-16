package com.example.heliboard.ime

import android.content.Context
import android.graphics.BitmapFactory
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectVerticalDragGestures
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
import androidx.compose.foundation.lazy.itemsIndexed
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
import androidx.compose.material.icons.filled.BrokenImage
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentCut
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.FirstPage
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LastPage
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Restore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.SelectAll
import androidx.compose.material.icons.filled.SwapVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material.icons.outlined.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.example.heliboard.R
import com.example.heliboard.data.ClipboardEntry
import com.example.heliboard.data.EmojiCategoryData
import com.example.heliboard.data.HeliSettings
import com.example.heliboard.data.ToolbarKey
import com.example.heliboard.theme.KeyboardColorPalette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun EmojiPanel(
    palette: KeyboardColorPalette,
    settings: HeliSettings,
    categories: List<EmojiCategoryData>,
    onSelectEmoji: (String) -> Unit,
    onBackToAlpha: () -> Unit,
    onBackspace: () -> Unit,
    keyboardHeightScale: Float = 1f
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

    val scaledHeight = (250f * keyboardHeightScale.coerceIn(0.70f, 1.50f)).coerceIn(190f, 360f).dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(scaledHeight)
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

private sealed interface ClipboardThumbnailState {
    data object Loading : ClipboardThumbnailState
    data class Ready(val bitmap: ImageBitmap) : ClipboardThumbnailState
    data object Unavailable : ClipboardThumbnailState
}

@Composable
private fun rememberClipboardThumbnail(entry: ClipboardEntry): ClipboardThumbnailState {
    val context = LocalContext.current
    val state by produceState<ClipboardThumbnailState>(
        initialValue = ClipboardThumbnailState.Loading,
        key1 = entry.id,
        key2 = entry.localImagePath,
        key3 = entry.imageUri
    ) {
        value = withContext(Dispatchers.IO) {
            loadDecodedThumbnail(context, entry)
        }
    }
    return state
}

private fun loadDecodedThumbnail(context: Context, entry: ClipboardEntry): ClipboardThumbnailState {
    val localFile = entry.localImagePath?.let { File(it) }?.takeIf { it.exists() && it.length() > 0L }
    if (localFile != null) {
        val bmp = decodeSampledBitmap(maxSizePx = 160) { opts ->
            BitmapFactory.decodeFile(localFile.absolutePath, opts)
        }
        if (bmp != null) return ClipboardThumbnailState.Ready(bmp.asImageBitmap())
    }
    val uriStr = entry.imageUri
    if (!uriStr.isNullOrBlank()) {
        val uri = runCatching { Uri.parse(uriStr) }.getOrNull()
        if (uri != null) {
            val bmp = decodeSampledBitmap(maxSizePx = 160) { opts ->
                runCatching {
                    context.contentResolver.openInputStream(uri)?.use { stream ->
                        BitmapFactory.decodeStream(stream, null, opts)
                    }
                }.getOrNull()
            }
            if (bmp != null) return ClipboardThumbnailState.Ready(bmp.asImageBitmap())
        }
    }
    return ClipboardThumbnailState.Unavailable
}

private fun decodeSampledBitmap(
    maxSizePx: Int,
    decode: (BitmapFactory.Options) -> android.graphics.Bitmap?
): android.graphics.Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    decode(bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while (bounds.outWidth / sample > maxSizePx || bounds.outHeight / sample > maxSizePx) {
        sample *= 2
    }
    val opts = BitmapFactory.Options().apply { inSampleSize = sample }
    return decode(opts)
}

@Composable
fun ClipboardPanel(
    palette: KeyboardColorPalette,
    entries: List<ClipboardEntry>,
    isIncognito: Boolean = false,
    isSensitiveInput: Boolean = false,
    feedbackMessage: String? = null,
    onDismissFeedback: () -> Unit = {},
    onPasteEntry: (ClipboardEntry) -> Unit,
    onTogglePin: (Long) -> Unit,
    onMovePinned: (Int, Int) -> Unit = { _, _ -> },
    onMovePinnedById: (Long, Int) -> Unit = { _, _ -> },
    onDelete: (Long) -> Unit,
    onClearUnpinned: () -> Unit,
    onBackToAlpha: () -> Unit,
    keyboardHeightScale: Float = 1f
) {
    var isReorderMode by remember { mutableStateOf(false) }

    val pinnedEntries = remember(entries) {
        entries.filter { it.isPinned }
            .sortedWith(compareBy<ClipboardEntry> { it.pinnedOrder ?: Int.MAX_VALUE }.thenByDescending { it.timestamp })
    }

    LaunchedEffect(pinnedEntries.isEmpty()) {
        if (pinnedEntries.isEmpty() && isReorderMode) {
            isReorderMode = false
        }
    }

    val scaledHeight = (272f * keyboardHeightScale.coerceIn(0.70f, 1.50f)).coerceIn(210f, 380f).dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(scaledHeight)
            .background(palette.background)
            .padding(horizontal = 8.dp, vertical = 6.dp)
    ) {
        // Header Row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 6.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = if (isReorderMode) {
                    stringResource(R.string.clipboard_reorder_pinned)
                } else {
                    stringResource(R.string.clipboard_title, entries.size)
                },
                color = palette.keyText,
                fontWeight = FontWeight.SemiBold,
                fontSize = 13.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )

            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (isReorderMode) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = palette.accent,
                        modifier = Modifier
                            .height(32.dp)
                            .clickable { isReorderMode = false }
                            .testTag("clipboard_reorder_done_button")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Check,
                                contentDescription = stringResource(R.string.clipboard_reorder_done),
                                tint = palette.actionKeyIcon,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.clipboard_reorder_done),
                                color = palette.actionKeyIcon,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                } else {
                    if (pinnedEntries.isNotEmpty()) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = palette.functionalKeyBackground,
                            modifier = Modifier
                                .height(32.dp)
                                .clickable { isReorderMode = true }
                                .testTag("clipboard_reorder_button")
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Default.SwapVert,
                                    contentDescription = stringResource(R.string.clipboard_reorder_pinned),
                                    tint = palette.accent,
                                    modifier = Modifier.size(16.dp)
                                )
                                Text(
                                    text = stringResource(R.string.clipboard_reorder_pinned),
                                    color = palette.keyText,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Medium,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = palette.functionalKeyBackground,
                        modifier = Modifier
                            .height(32.dp)
                            .clickable { onClearUnpinned() }
                            .testTag("clipboard_clear_unpinned")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = stringResource(R.string.clipboard_clear_unpinned_desc),
                                tint = palette.keyText,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = stringResource(R.string.clipboard_clear),
                                color = palette.keyText,
                                fontSize = 11.sp
                            )
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
                            modifier = Modifier.padding(horizontal = 10.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = stringResource(R.string.clipboard_back_abc),
                                color = palette.actionKeyIcon,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }
        }

        // Privacy banner for Incognito / Sensitive field
        if (isIncognito || isSensitiveInput) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = palette.functionalKeyBackground,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 5.dp)
                    .testTag("clipboard_privacy_notice")
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.VisibilityOff,
                        contentDescription = null,
                        tint = palette.accent,
                        modifier = Modifier.size(14.dp)
                    )
                    Text(
                        text = if (isSensitiveInput) {
                            stringResource(R.string.clipboard_sensitive_notice)
                        } else {
                            stringResource(R.string.clipboard_incognito_notice)
                        },
                        color = palette.keyHintText,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }
        }

        // Paste / Unsupported target feedback banner
        if (!feedbackMessage.isNullOrBlank()) {
            Surface(
                shape = RoundedCornerShape(8.dp),
                color = palette.accent.copy(alpha = 0.16f),
                border = BorderStroke(1.dp, palette.accent.copy(alpha = 0.55f)),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 5.dp)
                    .testTag("clipboard_feedback_banner")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 5.dp),
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
                        text = feedbackMessage,
                        color = palette.keyText,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Start
                    )
                    IconButton(
                        onClick = onDismissFeedback,
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Dismiss",
                            tint = palette.keyHintText,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }
        }

        if (isReorderMode) {
            if (pinnedEntries.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = stringResource(R.string.clipboard_reorder_empty),
                        color = palette.keyHintText,
                        fontSize = 13.sp
                    )
                }
            } else {
                Text(
                    text = stringResource(R.string.clipboard_reorder_hint),
                    color = palette.keyHintText,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 4.dp)
                )
                ReorderPinnedList(
                    palette = palette,
                    pinnedEntries = pinnedEntries,
                    onMovePinned = onMovePinned,
                    onMovePinnedById = onMovePinnedById
                )
            }
        } else if (entries.isEmpty()) {
            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = stringResource(R.string.clipboard_empty),
                    color = palette.keyHintText,
                    fontSize = 13.sp
                )
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .testTag("clipboard_list"),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(entries, key = { it.id }) { item ->
                    ClipboardEntryRow(
                        palette = palette,
                        item = item,
                        onPaste = { onPasteEntry(item) },
                        onTogglePin = { onTogglePin(item.id) },
                        onDelete = { onDelete(item.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ClipboardEntryRow(
    palette: KeyboardColorPalette,
    item: ClipboardEntry,
    onPaste: () -> Unit,
    onTogglePin: () -> Unit,
    onDelete: () -> Unit
) {
    val thumbState = if (item.isImage) rememberClipboardThumbnail(item) else null

    Surface(
        shape = RoundedCornerShape(10.dp),
        color = palette.keyBackground,
        border = if (item.isPinned) BorderStroke(1.dp, palette.accent.copy(alpha = 0.45f)) else null,
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onPaste() }
            .testTag("clipboard_item_${item.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (item.isImage) {
                ClipboardImageThumb(
                    palette = palette,
                    thumbState = thumbState ?: ClipboardThumbnailState.Loading
                )
                Column(modifier = Modifier.weight(1f)) {
                    val isUnavailable = thumbState is ClipboardThumbnailState.Unavailable
                    Text(
                        text = if (isUnavailable) {
                            stringResource(R.string.clipboard_image_unavailable)
                        } else if (item.text.isNotBlank()) {
                            item.text
                        } else {
                            stringResource(R.string.clipboard_image_label)
                        },
                        color = if (isUnavailable) palette.keyHintText else palette.keyText,
                        fontSize = 13.sp,
                        fontWeight = if (isUnavailable) FontWeight.Medium else FontWeight.Normal,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        textAlign = TextAlign.Start
                    )
                    if (isUnavailable) {
                        Text(
                            text = stringResource(R.string.clipboard_image_unavailable_hint),
                            color = palette.keyHintText,
                            fontSize = 10.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.testTag("clipboard_item_unavailable_${item.id}")
                        )
                    } else if (item.mimeType != null) {
                        Text(
                            text = item.mimeType,
                            color = palette.keyHintText,
                            fontSize = 10.sp,
                            maxLines = 1
                        )
                    }
                }
            } else {
                Text(
                    text = item.text,
                    color = palette.keyText,
                    fontSize = 13.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Start,
                    modifier = Modifier.weight(1f)
                )
            }

            IconButton(
                onClick = onTogglePin,
                modifier = Modifier
                    .size(34.dp)
                    .testTag("clipboard_pin_${item.id}")
            ) {
                Icon(
                    imageVector = if (item.isPinned) Icons.Filled.PushPin else Icons.Outlined.PushPin,
                    contentDescription = if (item.isPinned) {
                        stringResource(R.string.clipboard_unpin)
                    } else {
                        stringResource(R.string.clipboard_pin)
                    },
                    tint = if (item.isPinned) palette.accent else palette.keyHintText,
                    modifier = Modifier.size(18.dp)
                )
            }
            IconButton(
                onClick = onDelete,
                modifier = Modifier
                    .size(34.dp)
                    .testTag("clipboard_delete_${item.id}")
            ) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.clipboard_delete),
                    tint = palette.keyHintText,
                    modifier = Modifier.size(18.dp)
                )
            }
        }
    }
}

@Composable
private fun ReorderPinnedList(
    palette: KeyboardColorPalette,
    pinnedEntries: List<ClipboardEntry>,
    onMovePinned: (Int, Int) -> Unit,
    onMovePinnedById: (Long, Int) -> Unit
) {
    val density = LocalDensity.current
    val swapThresholdPx = with(density) { 54.dp.toPx() }
    var draggedId by remember { mutableStateOf<Long?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val latestPinned by rememberUpdatedState(pinnedEntries)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .testTag("clipboard_reorder_list"),
        verticalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        itemsIndexed(pinnedEntries, key = { _, item -> item.id }) { idx, item ->
            val isDragging = draggedId == item.id
            val thumbState = if (item.isImage) rememberClipboardThumbnail(item) else null

            Surface(
                shape = RoundedCornerShape(10.dp),
                color = if (isDragging) palette.functionalKeyBackground else palette.keyBackground,
                border = BorderStroke(
                    width = if (isDragging) 1.5.dp else 1.dp,
                    color = palette.accent.copy(alpha = if (isDragging) 0.9f else 0.4f)
                ),
                shadowElevation = if (isDragging) 6.dp else 0.dp,
                modifier = Modifier
                    .fillMaxWidth()
                    .zIndex(if (isDragging) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (isDragging) dragOffsetY else 0f
                    }
                    .testTag("clipboard_reorder_item_${item.id}")
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    // Dedicated drag handle so dragging never fights LazyColumn scroll or long-press
                    Box(
                        modifier = Modifier
                            .size(34.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .pointerInput(item.id) {
                                detectVerticalDragGestures(
                                    onDragStart = {
                                        draggedId = item.id
                                        dragOffsetY = 0f
                                    },
                                    onVerticalDrag = { change, dragAmount ->
                                        change.consume()
                                        dragOffsetY += dragAmount
                                        val currentList = latestPinned
                                        val curIdx = currentList.indexOfFirst { it.id == item.id }
                                        if (curIdx != -1) {
                                            if (dragOffsetY > swapThresholdPx && curIdx < currentList.lastIndex) {
                                                onMovePinned(curIdx, curIdx + 1)
                                                dragOffsetY -= swapThresholdPx
                                            } else if (dragOffsetY < -swapThresholdPx && curIdx > 0) {
                                                onMovePinned(curIdx, curIdx - 1)
                                                dragOffsetY += swapThresholdPx
                                            }
                                        }
                                    },
                                    onDragEnd = {
                                        draggedId = null
                                        dragOffsetY = 0f
                                    },
                                    onDragCancel = {
                                        draggedId = null
                                        dragOffsetY = 0f
                                    }
                                )
                            }
                            .testTag("clipboard_drag_handle_${item.id}"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.DragHandle,
                            contentDescription = stringResource(R.string.clipboard_drag_handle),
                            tint = palette.keyHintText,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    if (item.isImage) {
                        ClipboardImageThumb(
                            palette = palette,
                            thumbState = thumbState ?: ClipboardThumbnailState.Loading,
                            sizeDp = 36
                        )
                        val unavailable = thumbState is ClipboardThumbnailState.Unavailable
                        Text(
                            text = if (unavailable) {
                                stringResource(R.string.clipboard_image_unavailable)
                            } else if (item.text.isNotBlank()) {
                                item.text
                            } else {
                                stringResource(R.string.clipboard_image_label)
                            },
                            color = if (unavailable) palette.keyHintText else palette.keyText,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        Text(
                            text = item.text,
                            color = palette.keyText,
                            fontSize = 13.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Start,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    IconButton(
                        onClick = { onMovePinnedById(item.id, -1) },
                        enabled = idx > 0,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("clipboard_move_up_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = stringResource(R.string.clipboard_move_up),
                            tint = if (idx > 0) palette.keyText else palette.keyHintText.copy(alpha = 0.35f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    IconButton(
                        onClick = { onMovePinnedById(item.id, 1) },
                        enabled = idx < pinnedEntries.lastIndex,
                        modifier = Modifier
                            .size(34.dp)
                            .testTag("clipboard_move_down_${item.id}")
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = stringResource(R.string.clipboard_move_down),
                            tint = if (idx < pinnedEntries.lastIndex) palette.keyText else palette.keyHintText.copy(alpha = 0.35f),
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ClipboardImageThumb(
    palette: KeyboardColorPalette,
    thumbState: ClipboardThumbnailState,
    sizeDp: Int = 44
) {
    Box(
        modifier = Modifier
            .size(sizeDp.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(palette.functionalKeyBackground),
        contentAlignment = Alignment.Center
    ) {
        when (thumbState) {
            is ClipboardThumbnailState.Ready -> {
                Image(
                    bitmap = thumbState.bitmap,
                    contentDescription = stringResource(R.string.clipboard_image_label),
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
            ClipboardThumbnailState.Unavailable -> {
                Icon(
                    imageVector = Icons.Default.BrokenImage,
                    contentDescription = stringResource(R.string.clipboard_image_unavailable),
                    tint = Color(0xFFEF4444),
                    modifier = Modifier.size((sizeDp * 0.52f).dp)
                )
            }
            ClipboardThumbnailState.Loading -> {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = stringResource(R.string.clipboard_image_label),
                    tint = palette.keyHintText,
                    modifier = Modifier.size((sizeDp * 0.52f).dp)
                )
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
    onBackToAlpha: () -> Unit,
    keyboardHeightScale: Float = 1f
) {
    val scaledHeight = (250f * keyboardHeightScale.coerceIn(0.70f, 1.50f)).coerceIn(190f, 360f).dp
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(scaledHeight)
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

@Composable
fun ToolbarCustomizePanel(
    palette: KeyboardColorPalette,
    enabledKeyNames: List<String>,
    onToggleKey: (ToolbarKey) -> Unit,
    onMoveKey: (Int, Int) -> Unit,
    onMoveKeyByName: (String, Int) -> Unit,
    onReset: () -> Unit,
    onBackToAlpha: () -> Unit,
    keyboardHeightScale: Float = 1f
) {
    val scaledHeight = (292f * keyboardHeightScale.coerceIn(0.70f, 1.50f)).coerceIn(220f, 400f).dp
    val enabledKeys = remember(enabledKeyNames) {
        enabledKeyNames.mapNotNull { name -> runCatching { ToolbarKey.valueOf(name) }.getOrNull() }
    }
    val disabledKeys = remember(enabledKeys) {
        val enabledSet = enabledKeys.toSet()
        ToolbarKey.entries.filterNot { it in enabledSet }
    }

    val density = LocalDensity.current
    val swapThresholdPx = with(density) { 48.dp.toPx() }
    var draggedKeyName by remember { mutableStateOf<String?>(null) }
    var dragOffsetY by remember { mutableFloatStateOf(0f) }
    val latestEnabledNames by rememberUpdatedState(enabledKeyNames)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .height(scaledHeight)
            .background(palette.background)
            .padding(horizontal = 8.dp, vertical = 6.dp)
            .testTag("toolbar_customize_panel")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.weight(1f)
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = null,
                    tint = palette.accent,
                    modifier = Modifier.size(18.dp)
                )
                Text(
                    text = stringResource(R.string.toolbar_custom_title),
                    color = palette.keyText,
                    fontWeight = FontWeight.SemiBold,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = palette.functionalKeyBackground,
                    modifier = Modifier
                        .height(32.dp)
                        .clickable { onReset() }
                        .testTag("toolbar_customize_reset")
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Restore,
                            contentDescription = stringResource(R.string.action_reset),
                            tint = palette.keyText,
                            modifier = Modifier.size(15.dp)
                        )
                        Text(
                            text = stringResource(R.string.action_reset),
                            color = palette.keyText,
                            fontSize = 11.sp
                        )
                    }
                }
                Surface(
                    shape = RoundedCornerShape(8.dp),
                    color = palette.accent,
                    modifier = Modifier
                        .height(32.dp)
                        .clickable { onBackToAlpha() }
                        .testTag("toolbar_customize_done")
                ) {
                    Box(
                        modifier = Modifier.padding(horizontal = 12.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = stringResource(R.string.clipboard_reorder_done),
                            color = palette.actionKeyIcon,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }
            }
        }

        Text(
            text = stringResource(R.string.toolbar_drag_hint),
            color = palette.keyHintText,
            fontSize = 11.sp,
            modifier = Modifier.padding(bottom = 4.dp)
        )

        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .testTag("toolbar_customize_list"),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            itemsIndexed(enabledKeys, key = { _, item -> "enabled_${item.name}" }) { idx, tk ->
                val isDragging = draggedKeyName == tk.name
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = if (isDragging) palette.functionalKeyBackground else palette.keyBackground,
                    border = BorderStroke(
                        width = if (isDragging) 1.5.dp else 1.dp,
                        color = palette.accent.copy(alpha = if (isDragging) 0.9f else 0.35f)
                    ),
                    shadowElevation = if (isDragging) 6.dp else 0.dp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .zIndex(if (isDragging) 1f else 0f)
                        .graphicsLayer {
                            translationY = if (isDragging) dragOffsetY else 0f
                        }
                        .testTag("toolbar_custom_item_${tk.name.lowercase()}")
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 8.dp, vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(RoundedCornerShape(6.dp))
                                .pointerInput(tk.name) {
                                    detectVerticalDragGestures(
                                        onDragStart = {
                                            draggedKeyName = tk.name
                                            dragOffsetY = 0f
                                        },
                                        onVerticalDrag = { change, dragAmount ->
                                            change.consume()
                                            dragOffsetY += dragAmount
                                            val currentList = latestEnabledNames
                                            val curIdx = currentList.indexOf(tk.name)
                                            if (curIdx != -1) {
                                                if (dragOffsetY > swapThresholdPx && curIdx < currentList.lastIndex) {
                                                    onMoveKey(curIdx, curIdx + 1)
                                                    dragOffsetY -= swapThresholdPx
                                                } else if (dragOffsetY < -swapThresholdPx && curIdx > 0) {
                                                    onMoveKey(curIdx, curIdx - 1)
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
                                .testTag("toolbar_drag_handle_${tk.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DragHandle,
                                contentDescription = stringResource(R.string.clipboard_drag_handle),
                                tint = palette.keyHintText,
                                modifier = Modifier.size(19.dp)
                            )
                        }

                        Icon(
                            imageVector = iconForToolbarKey(tk),
                            contentDescription = null,
                            tint = palette.accent,
                            modifier = Modifier.size(18.dp)
                        )
                        Text(
                            text = localizedToolbarKeyLabel(tk),
                            color = palette.keyText,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(
                            onClick = { onMoveKeyByName(tk.name, -1) },
                            enabled = idx > 0,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("toolbar_move_up_${tk.name.lowercase()}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.clipboard_move_up),
                                tint = if (idx > 0) palette.keyText else palette.keyHintText.copy(alpha = 0.35f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        IconButton(
                            onClick = { onMoveKeyByName(tk.name, 1) },
                            enabled = idx < enabledKeys.lastIndex,
                            modifier = Modifier
                                .size(32.dp)
                                .testTag("toolbar_move_down_${tk.name.lowercase()}")
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowDown,
                                contentDescription = stringResource(R.string.clipboard_move_down),
                                tint = if (idx < enabledKeys.lastIndex) palette.keyText else palette.keyHintText.copy(alpha = 0.35f),
                                modifier = Modifier.size(18.dp)
                            )
                        }
                        Switch(
                            checked = true,
                            onCheckedChange = { onToggleKey(tk) },
                            modifier = Modifier.testTag("toolbar_toggle_${tk.name.lowercase()}")
                        )
                    }
                }
            }

            if (disabledKeys.isNotEmpty()) {
                item(key = "toolbar_available_header") {
                    Text(
                        text = stringResource(R.string.toolbar_available_buttons),
                        color = palette.keyHintText,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                    )
                }
                items(disabledKeys, key = { "disabled_${it.name}" }) { tk ->
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = palette.functionalKeyBackground,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("toolbar_custom_item_${tk.name.lowercase()}")
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 10.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = iconForToolbarKey(tk),
                                contentDescription = null,
                                tint = palette.keyHintText,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = localizedToolbarKeyLabel(tk),
                                color = palette.keyText,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = false,
                                onCheckedChange = { onToggleKey(tk) },
                                modifier = Modifier.testTag("toolbar_toggle_${tk.name.lowercase()}")
                            )
                        }
                    }
                }
            }
        }
    }
}
