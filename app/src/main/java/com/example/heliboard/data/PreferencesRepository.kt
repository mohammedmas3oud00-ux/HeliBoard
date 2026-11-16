package com.example.heliboard.data

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.core.content.FileProvider
import androidx.core.content.edit
import com.example.heliboard.data.room.ClipboardRoomRepository
import com.example.heliboard.data.room.ClipboardSnippetEntity
import com.example.heliboard.data.room.HeliBoardDatabase
import com.example.heliboard.data.room.toClipboardEntry
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import java.io.File
import java.security.MessageDigest
import java.util.concurrent.atomic.AtomicLong

class PreferencesRepository(
    private val context: Context,
    private val roomRepo: ClipboardRoomRepository = ClipboardRoomRepository(
        HeliBoardDatabase.getInstance(context)
    ),
    private val ioScope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
) {

    private val prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val json = Json {
        ignoreUnknownKeys = true
        prettyPrint = true
        encodeDefaults = true
    }

    private val idCounter = AtomicLong(System.currentTimeMillis())
    private val roomSyncMutex = Mutex()

    private val clipboardImagesDir: File
        get() = File(context.filesDir, CLIPBOARD_IMAGES_DIR).apply { if (!exists()) mkdirs() }

    private val _settings = MutableStateFlow(loadSettings())
    val settings: StateFlow<HeliSettings> = _settings.asStateFlow()

    private val _clipboard = MutableStateFlow<List<ClipboardEntry>>(emptyList())
    val clipboard: StateFlow<List<ClipboardEntry>> = _clipboard.asStateFlow()

    val roomSnippets: Flow<List<ClipboardSnippetEntity>> = roomRepo.snippets

    private val _personalWords = MutableStateFlow(loadPersonalWords())
    val personalWords: StateFlow<List<PersonalWordEntry>> = _personalWords.asStateFlow()

    private val _customThemes = MutableStateFlow(loadCustomThemes())
    val customThemes: StateFlow<List<CustomThemeSpec>> = _customThemes.asStateFlow()

    private val _customLayouts = MutableStateFlow(loadCustomLayouts())
    val customLayouts: StateFlow<Map<String, String>> = _customLayouts.asStateFlow()

    private val clipboardManager by lazy {
        context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    }

    private var lastCapturedClipKey: String? = null

    private val clipListener = ClipboardManager.OnPrimaryClipChangedListener {
        captureSystemClipboard()
    }

    init {
        val prefsHadClipboard = prefs.contains(KEY_CLIPBOARD)
        val loaded = loadClipboard()
        _clipboard.value = loaded
        pruneExpiredClipboard()
        initializeRoomSync(prefsHadClipboard)
        runCatching {
            clipboardManager.addPrimaryClipChangedListener(clipListener)
            captureSystemClipboard()
        }
    }

    private fun initializeRoomSync(prefsHadClipboard: Boolean) {
        ioScope.launch {
            roomSyncMutex.withLock {
                val roomEntries = runCatching { roomRepo.getAllOnce().map { it.toClipboardEntry() } }.getOrDefault(emptyList())
                if (!prefsHadClipboard && roomEntries.isNotEmpty()) {
                    val current = _clipboard.value
                    val currentText = current.filter { !it.isImage }
                    val existingTexts = currentText.mapTo(HashSet()) { it.text }
                    val restored = roomEntries.filter { it.text !in existingTexts }
                    val images = current.filter { it.isImage }
                    val merged = applyRetentionAndSort(renormalizePinnedOrder(currentText + restored + images))
                    _clipboard.value = merged
                    prefs.edit { putString(KEY_CLIPBOARD, json.encodeToString(merged)) }
                    runCatching { roomRepo.syncAllTextSnippets(merged) }
                } else {
                    runCatching { roomRepo.syncAllTextSnippets(_clipboard.value) }
                }
            }
        }
    }

    private fun scheduleRoomSync(items: List<ClipboardEntry>) {
        val snapshot = items.toList()
        ioScope.launch {
            roomSyncMutex.withLock {
                runCatching { roomRepo.syncAllTextSnippets(snapshot) }
            }
        }
    }

    private fun nextId(): Long {
        while (true) {
            val prev = idCounter.get()
            val candidate = maxOf(System.currentTimeMillis(), prev + 1L)
            if (idCounter.compareAndSet(prev, candidate)) return candidate
        }
    }

    fun updateSettings(transform: (HeliSettings) -> HeliSettings) {
        val prev = _settings.value
        val updated = transform(prev)
        _settings.value = updated
        prefs.edit { putString(KEY_SETTINGS, json.encodeToString(updated)) }
        if (prev.clipboardPinnedFirst != updated.clipboardPinnedFirst ||
            prev.clipboardRetentionMinutes != updated.clipboardRetentionMinutes
        ) {
            saveClipboard(applyRetentionAndSort(_clipboard.value))
        }
    }

    fun toggleToolbarKey(key: ToolbarKey) {
        updateSettings { s ->
            val list = s.toolbarKeys.toMutableList()
            if (key.name in list) list.remove(key.name) else list.add(key.name)
            s.copy(toolbarKeys = list)
        }
    }

    fun moveToolbarKey(fromIndex: Int, toIndex: Int) {
        updateSettings { s ->
            val list = s.toolbarKeys.toMutableList()
            if (fromIndex !in list.indices || toIndex !in list.indices || fromIndex == toIndex) {
                return@updateSettings s
            }
            val item = list.removeAt(fromIndex)
            list.add(toIndex, item)
            s.copy(toolbarKeys = list)
        }
    }

    fun moveToolbarKey(keyName: String, delta: Int) {
        updateSettings { s ->
            val list = s.toolbarKeys.toMutableList()
            val idx = list.indexOf(keyName)
            val target = idx + delta
            if (idx >= 0 && target in list.indices) {
                val item = list.removeAt(idx)
                list.add(target, item)
            }
            s.copy(toolbarKeys = list)
        }
    }

    fun resetToolbarKeys() {
        updateSettings { s -> s.copy(toolbarKeys = HeliSettings().toolbarKeys) }
    }

    fun cycleSubtype() {
        val current = _settings.value
        val enabled = current.subtypes.filter { it.isEnabled }
        if (enabled.size <= 1) return
        val idx = enabled.indexOfFirst { it.localeTag == current.selectedSubtypeLocale }
        val next = enabled[(idx + 1) % enabled.size]
        updateSettings { it.copy(selectedSubtypeLocale = next.localeTag) }
    }

    fun activeSubtype(): SubtypeConfig {
        val current = _settings.value
        return current.subtypes.firstOrNull { it.localeTag == current.selectedSubtypeLocale && it.isEnabled }
            ?: current.subtypes.firstOrNull { it.isEnabled }
            ?: SubtypeConfig("en-US", "English (US)", "qwerty", true)
    }

    fun addEmojiToRecents(emoji: String) {
        updateSettings { s ->
            val updated = (listOf(emoji) + s.recentEmojis.filterNot { it == emoji }).take(24)
            s.copy(recentEmojis = updated)
        }
    }

    fun captureSystemClipboard(isSensitiveField: Boolean = false) {
        val currentSettings = _settings.value
        if (!currentSettings.clipboardHistoryEnabled) return
        if (currentSettings.incognitoMode || isSensitiveField) return

        val clip = runCatching { clipboardManager.primaryClip }.getOrNull() ?: return
        if (clip.itemCount == 0) return
        if (isClipSensitive(clip.description)) return

        val item = clip.getItemAt(0)
        val uri = item.uri
        val imageMime = uri?.let { detectImageMimeType(clip.description, it) }
        if (uri != null && imageMime != null) {
            val clipKey = "img:$uri"
            if (clipKey == lastCapturedClipKey) return
            lastCapturedClipKey = clipKey
            val caption = item.text?.toString()?.trim().orEmpty()
            addClipboardImageEntry(
                uri = uri,
                caption = caption,
                mimeType = imageMime,
                isSensitiveField = false
            )
            return
        }

        val text = item.coerceToText(context)?.toString()?.trim().orEmpty()
        if (text.isEmpty()) return
        val clipKey = "txt:$text"
        if (clipKey == lastCapturedClipKey) return
        lastCapturedClipKey = clipKey
        addClipboardEntry(text, isSensitiveField = false)
    }

    fun addClipboardEntry(text: String, isSensitiveField: Boolean = false) {
        val currentSettings = _settings.value
        if (!currentSettings.clipboardHistoryEnabled) return
        if (currentSettings.incognitoMode || isSensitiveField) return

        val clean = text.trim()
        if (clean.isEmpty()) return

        val now = System.currentTimeMillis()
        val current = _clipboard.value
        val existing = current.firstOrNull { !it.isImage && it.text == clean }
        val newEntry = if (existing != null) {
            existing.copy(timestamp = now)
        } else {
            ClipboardEntry(
                id = nextId(),
                text = clean,
                timestamp = now,
                isPinned = false
            )
        }
        val list = listOf(newEntry) + current.filterNot { !it.isImage && it.text == clean }
        saveClipboard(applyRetentionAndSort(list))
    }

    fun addClipboardImageEntry(
        uri: Uri,
        caption: String = "",
        mimeType: String? = null,
        isSensitiveField: Boolean = false
    ) {
        val currentSettings = _settings.value
        if (!currentSettings.clipboardHistoryEnabled) return
        if (currentSettings.incognitoMode || isSensitiveField) return

        val now = System.currentTimeMillis()
        val resolvedMime = mimeType ?: detectImageMimeType(null, uri) ?: "image/png"
        val newId = nextId()
        val persisted = persistImageFromUri(uri, newId, resolvedMime)
        val uriString = uri.toString()

        val current = _clipboard.value
        val existing = current.firstOrNull { entry ->
            entry.isImage && (
                (persisted?.second != null && entry.contentHash == persisted.second) ||
                    entry.imageUri == uriString
                )
        }

        val entry = if (existing != null) {
            // Remove duplicate newly written file if keeping existing local file
            if (persisted != null && existing.localImagePath != null && existing.localImagePath != persisted.first) {
                runCatching { File(persisted.first).delete() }
            }
            existing.copy(
                timestamp = now,
                text = if (caption.isNotBlank()) caption else existing.text,
                imageUri = uriString,
                localImagePath = existing.localImagePath?.takeIf { File(it).exists() } ?: persisted?.first,
                mimeType = resolvedMime,
                contentHash = existing.contentHash ?: persisted?.second
            )
        } else {
            ClipboardEntry(
                id = newId,
                text = caption,
                timestamp = now,
                isPinned = false,
                imageUri = uriString,
                localImagePath = persisted?.first,
                mimeType = resolvedMime,
                pinnedOrder = null,
                contentHash = persisted?.second
            )
        }

        val remaining = if (existing != null) current.filterNot { it.id == existing.id } else current
        saveClipboard(applyRetentionAndSort(listOf(entry) + remaining))
    }

    fun copyToSystemClipboard(text: String) {
        lastCapturedClipKey = "txt:$text"
        runCatching {
            clipboardManager.setPrimaryClip(ClipData.newPlainText("HeliBoard", text))
        }
    }

    /**
     * Reads current non-sensitive plain text from the system clipboard (or latest text clip)
     * strictly when invoked by an explicit user Paste action inside AI/Translate panels.
     */
    fun readSystemClipboardTextExplicitly(): String? {
        val clip = runCatching { clipboardManager.primaryClip }.getOrNull()
        if (clip != null && clip.itemCount > 0 && !isClipSensitive(clip.description)) {
            val sysText = clip.getItemAt(0).coerceToText(context)?.toString()?.trim()
            if (!sysText.isNullOrEmpty()) return sysText
        }
        return _clipboard.value
            .filter { !it.isImage && it.text.isNotBlank() }
            .maxByOrNull { it.timestamp }
            ?.text
    }

    fun togglePinClipboard(id: Long) {
        val current = _clipboard.value
        val target = current.firstOrNull { it.id == id } ?: return
        val nextPinnedState = !target.isPinned

        val updated = if (nextPinnedState) {
            val maxOrder = current.filter { it.isPinned }.mapNotNull { it.pinnedOrder }.maxOrNull() ?: -1
            val ensuredLocalPath = if (target.isImage && !hasValidLocalImage(target) && !target.imageUri.isNullOrBlank()) {
                runCatching {
                    persistImageFromUri(Uri.parse(target.imageUri), target.id, target.mimeType)
                }.getOrNull()
            } else null

            current.map { entry ->
                if (entry.id == id) {
                    entry.copy(
                        isPinned = true,
                        pinnedOrder = maxOrder + 1,
                        localImagePath = entry.localImagePath?.takeIf { File(it).exists() } ?: ensuredLocalPath?.first,
                        contentHash = entry.contentHash ?: ensuredLocalPath?.second
                    )
                } else {
                    entry
                }
            }
        } else {
            val toggled = current.map { entry ->
                if (entry.id == id) entry.copy(isPinned = false, pinnedOrder = null) else entry
            }
            renormalizePinnedOrder(toggled)
        }

        saveClipboard(applyRetentionAndSort(updated))
    }

    fun movePinnedEntry(fromIndex: Int, toIndex: Int) {
        val orderedPinned = getOrderedPinnedEntries().toMutableList()
        if (fromIndex !in orderedPinned.indices || toIndex !in orderedPinned.indices || fromIndex == toIndex) {
            return
        }
        val moved = orderedPinned.removeAt(fromIndex)
        orderedPinned.add(toIndex, moved)
        val reindexedPinned = orderedPinned.mapIndexed { idx, entry ->
            entry.copy(pinnedOrder = idx)
        }
        val unpinned = _clipboard.value.filterNot { it.isPinned }
        saveClipboard(applyRetentionAndSort(reindexedPinned + unpinned))
    }

    fun movePinnedEntryById(id: Long, delta: Int) {
        val orderedPinned = getOrderedPinnedEntries()
        val idx = orderedPinned.indexOfFirst { it.id == id }
        if (idx == -1) return
        val target = (idx + delta).coerceIn(0, orderedPinned.lastIndex)
        movePinnedEntry(idx, target)
    }

    fun getOrderedPinnedEntries(source: List<ClipboardEntry> = _clipboard.value): List<ClipboardEntry> {
        return source.filter { it.isPinned }
            .sortedWith(compareBy<ClipboardEntry> { it.pinnedOrder ?: Int.MAX_VALUE }.thenByDescending { it.timestamp })
    }

    fun deleteClipboardEntry(id: Long) {
        val current = _clipboard.value
        val toDelete = current.firstOrNull { it.id == id }
        val remaining = renormalizePinnedOrder(current.filterNot { it.id == id })
        saveClipboard(applyRetentionAndSort(remaining))
        cleanupUnusedImageFile(toDelete?.localImagePath, remaining)
    }

    fun clearUnpinnedClipboard() {
        val current = _clipboard.value
        val pinnedOnly = renormalizePinnedOrder(current.filter { it.isPinned })
        val removed = current.filterNot { it.isPinned }
        saveClipboard(applyRetentionAndSort(pinnedOnly))
        removed.forEach { cleanupUnusedImageFile(it.localImagePath, pinnedOnly) }
    }

    fun pruneExpiredClipboard() {
        val current = _clipboard.value
        val pruned = applyRetentionAndSort(current)
        if (pruned != current) {
            val remainingIds = pruned.map { it.id }.toSet()
            val removed = current.filterNot { it.id in remainingIds }
            saveClipboard(pruned)
            removed.forEach { cleanupUnusedImageFile(it.localImagePath, pruned) }
        }
    }

    fun isImageAvailable(entry: ClipboardEntry): Boolean {
        if (!entry.isImage) return false
        if (hasValidLocalImage(entry)) return true
        val uriStr = entry.imageUri ?: return false
        return runCatching {
            val uri = Uri.parse(uriStr)
            context.contentResolver.openInputStream(uri)?.use { stream ->
                stream.read() != -1
            } ?: false
        }.getOrDefault(false)
    }

    fun resolveImageForPaste(entry: ClipboardEntry): Pair<Uri, String>? {
        if (!entry.isImage) return null
        val mime = entry.mimeType ?: "image/png"
        val localPath = entry.localImagePath
        if (!localPath.isNullOrBlank()) {
            val file = File(localPath)
            if (file.exists() && file.length() > 0L) {
                val uri = runCatching {
                    FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
                }.getOrElse {
                    Uri.fromFile(file)
                }
                return uri to mime
            }
        }
        val uriStr = entry.imageUri ?: return null
        val parsed = Uri.parse(uriStr)
        val accessible = runCatching {
            context.contentResolver.openInputStream(parsed)?.use { it.read() != -1 } ?: false
        }.getOrDefault(false)
        if (!accessible) return null
        // Cache locally if we can read it now
        val cached = persistImageFromUri(parsed, entry.id, mime)
        if (cached != null) {
            val updated = _clipboard.value.map {
                if (it.id == entry.id) it.copy(localImagePath = cached.first, contentHash = it.contentHash ?: cached.second)
                else it
            }
            saveClipboard(updated)
            val file = File(cached.first)
            val contentUri = runCatching {
                FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
            }.getOrElse { Uri.fromFile(file) }
            return contentUri to mime
        }
        return parsed to mime
    }

    private fun hasValidLocalImage(entry: ClipboardEntry): Boolean {
        val path = entry.localImagePath ?: return false
        val f = File(path)
        return f.exists() && f.length() > 0L
    }

    private fun renormalizePinnedOrder(items: List<ClipboardEntry>): List<ClipboardEntry> {
        val pinnedSorted = items.filter { it.isPinned }
            .sortedWith(compareBy<ClipboardEntry> { it.pinnedOrder ?: Int.MAX_VALUE }.thenByDescending { it.timestamp })
        val orderById = pinnedSorted.mapIndexed { idx, entry -> entry.id to idx }.toMap()
        return items.map { entry ->
            if (entry.isPinned) entry.copy(pinnedOrder = orderById[entry.id]) else entry.copy(pinnedOrder = null)
        }
    }

    private fun applyRetentionAndSort(items: List<ClipboardEntry>): List<ClipboardEntry> {
        val now = System.currentTimeMillis()
        val retentionMinutes = _settings.value.clipboardRetentionMinutes
        val retentionMillis = if (retentionMinutes > 0) retentionMinutes * 60_000L else -1L

        val pinned = items.filter { it.isPinned }
        val validUnpinned = items.filter { !it.isPinned }
            .filter { entry ->
                retentionMillis < 0L || (now - entry.timestamp) <= retentionMillis
            }
            .sortedByDescending { it.timestamp }
            .take(MAX_UNPINNED_CLIPS)

        return sortClipboard(pinned + validUnpinned)
    }

    private fun sortClipboard(items: List<ClipboardEntry>): List<ClipboardEntry> {
        val pinned = getOrderedPinnedEntries(items)
        val unpinned = items.filterNot { it.isPinned }.sortedByDescending { it.timestamp }
        return if (_settings.value.clipboardPinnedFirst) {
            pinned + unpinned
        } else {
            (pinned + unpinned).sortedByDescending { it.timestamp }
        }
    }

    private fun isClipSensitive(description: ClipDescription): Boolean {
        val extras = description.extras ?: return false
        val legacyExtra = extras.getBoolean(EXTRA_IS_SENSITIVE_COMPAT, false)
        val api33Extra = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            extras.getBoolean(ClipDescription.EXTRA_IS_SENSITIVE, false)
        } else {
            false
        }
        return legacyExtra || api33Extra
    }

    private fun detectImageMimeType(description: ClipDescription?, uri: Uri): String? {
        if (description != null) {
            for (i in 0 until description.mimeTypeCount) {
                val mime = description.getMimeType(i)
                if (mime.startsWith("image/")) return mime
            }
        }
        val resolverType = runCatching { context.contentResolver.getType(uri) }.getOrNull()
        return resolverType?.takeIf { it.startsWith("image/") }
    }

    private fun persistImageFromUri(uri: Uri, entryId: Long, mimeType: String?): Pair<String, String?>? {
        return runCatching {
            val ext = when {
                mimeType?.contains("png", ignoreCase = true) == true -> "png"
                mimeType?.contains("webp", ignoreCase = true) == true -> "webp"
                mimeType?.contains("gif", ignoreCase = true) == true -> "gif"
                else -> "jpg"
            }
            val targetFile = File(clipboardImagesDir, "clip_${entryId}.$ext")
            val digest = MessageDigest.getInstance("SHA-256")
            var totalBytes = 0L
            context.contentResolver.openInputStream(uri)?.use { input ->
                targetFile.outputStream().use { output ->
                    val buffer = ByteArray(8192)
                    while (true) {
                        val read = input.read(buffer)
                        if (read <= 0) break
                        totalBytes += read
                        if (totalBytes > MAX_IMAGE_BYTES) {
                            output.close()
                            targetFile.delete()
                            return null
                        }
                        digest.update(buffer, 0, read)
                        output.write(buffer, 0, read)
                    }
                }
            } ?: return null

            if (totalBytes == 0L) {
                targetFile.delete()
                return null
            }
            val hash = digest.digest().joinToString("") { "%02x".format(it) }
            targetFile.absolutePath to hash
        }.getOrNull()
    }

    private fun cleanupUnusedImageFile(path: String?, remaining: List<ClipboardEntry>) {
        if (path.isNullOrBlank()) return
        val stillReferenced = remaining.any { it.localImagePath == path }
        if (!stillReferenced) {
            runCatching { File(path).delete() }
        }
    }

    fun upsertPersonalWord(entry: PersonalWordEntry) {
        val list = _personalWords.value.toMutableList()
        val existingIdx = list.indexOfFirst { it.id == entry.id }
        if (existingIdx >= 0) {
            list[existingIdx] = entry
        } else {
            list.add(0, entry.copy(id = nextId()))
        }
        savePersonalWords(list.sortedBy { it.word.lowercase() })
    }

    fun deletePersonalWord(id: Long) {
        savePersonalWords(_personalWords.value.filterNot { it.id == id })
    }

    fun saveCustomTheme(spec: CustomThemeSpec) {
        val list = (_customThemes.value.filterNot { it.name == spec.name } + spec)
            .sortedBy { it.name.lowercase() }
        _customThemes.value = list
        prefs.edit { putString(KEY_CUSTOM_THEMES, json.encodeToString(list)) }
    }

    fun deleteCustomTheme(name: String) {
        val list = _customThemes.value.filterNot { it.name == name }
        _customThemes.value = list
        prefs.edit { putString(KEY_CUSTOM_THEMES, json.encodeToString(list)) }
        updateSettings { s ->
            s.copy(
                themeName = if (s.themeName == name) "light" else s.themeName,
                nightThemeName = if (s.nightThemeName == name) "dark" else s.nightThemeName
            )
        }
    }

    fun saveCustomLayout(layoutId: String, source: String) {
        val map = _customLayouts.value.toMutableMap()
        map[layoutId] = source
        _customLayouts.value = map
        prefs.edit { putString(KEY_CUSTOM_LAYOUTS, json.encodeToString(map)) }
    }

    fun resetCustomLayout(layoutId: String) {
        val map = _customLayouts.value.toMutableMap()
        map.remove(layoutId)
        _customLayouts.value = map
        prefs.edit { putString(KEY_CUSTOM_LAYOUTS, json.encodeToString(map)) }
    }

    fun exportBackupJson(): String {
        val bundle = HeliBackupBundle(
            settings = _settings.value,
            personalWords = _personalWords.value,
            customThemes = _customThemes.value,
            customLayouts = _customLayouts.value,
            pinnedClips = getOrderedPinnedEntries()
        )
        return json.encodeToString(bundle)
    }

    fun importBackupJson(raw: String): Result<String> = runCatching {
        val bundle = json.decodeFromString<HeliBackupBundle>(raw)
        _settings.value = bundle.settings
        _personalWords.value = bundle.personalWords
        _customThemes.value = bundle.customThemes
        _customLayouts.value = bundle.customLayouts

        val migratedPinned = migratePinnedOrderPreservingInputOrder(
            bundle.pinnedClips.map { it.copy(isPinned = true) }
        ).first
        val mergedClips = (migratedPinned + _clipboard.value)
            .distinctBy { if (it.isImage) "img:${it.contentHash ?: it.imageUri ?: it.id}" else "txt:${it.text}" }
        _clipboard.value = applyRetentionAndSort(renormalizePinnedOrder(mergedClips))

        prefs.edit {
            putString(KEY_SETTINGS, json.encodeToString(bundle.settings))
            putString(KEY_PERSONAL_WORDS, json.encodeToString(bundle.personalWords))
            putString(KEY_CUSTOM_THEMES, json.encodeToString(bundle.customThemes))
            putString(KEY_CUSTOM_LAYOUTS, json.encodeToString(bundle.customLayouts))
            putString(KEY_CLIPBOARD, json.encodeToString(_clipboard.value))
        }
        scheduleRoomSync(_clipboard.value)
        "Restored ${bundle.personalWords.size} words, ${bundle.customThemes.size} custom themes, and all preferences."
    }

    private fun loadSettings(): HeliSettings {
        val raw = prefs.getString(KEY_SETTINGS, null) ?: return HeliSettings()
        val decoded = runCatching { json.decodeFromString<HeliSettings>(raw) }.getOrNull() ?: return HeliSettings()
        val storedVersion = runCatching {
            (json.parseToJsonElement(raw) as? JsonObject)
                ?.get("schemaVersion")
                ?.jsonPrimitive
                ?.intOrNull ?: 1
        }.getOrDefault(1)

        if (storedVersion >= CURRENT_SETTINGS_SCHEMA_VERSION) {
            return decoded
        }

        val hasArabic = decoded.subtypes.any { it.localeTag.startsWith("ar", ignoreCase = true) }
        val migratedSubtypes = if (hasArabic) {
            decoded.subtypes
        } else {
            val baseList = decoded.subtypes.toMutableList()
            val insertIdx = if (baseList.isNotEmpty()) 1 else 0
            baseList.add(insertIdx, SubtypeConfig("ar", "العربية", "arabic", true))
            baseList
        }
        val migratedToolbar = decoded.toolbarKeys.toMutableList()
        if (ToolbarKey.EMOJI.name !in migratedToolbar) {
            migratedToolbar.add(0, ToolbarKey.EMOJI.name)
        }
        if (ToolbarKey.VOICE.name !in migratedToolbar) {
            migratedToolbar.add(0, ToolbarKey.VOICE.name)
        }
        if (storedVersion < 4) {
            val voiceIdx = migratedToolbar.indexOf(ToolbarKey.VOICE.name)
            var insertAt = if (voiceIdx >= 0) voiceIdx + 1 else 0
            if (ToolbarKey.AI_WRITING.name !in migratedToolbar) {
                migratedToolbar.add(insertAt, ToolbarKey.AI_WRITING.name)
                insertAt++
            } else {
                insertAt = migratedToolbar.indexOf(ToolbarKey.AI_WRITING.name) + 1
            }
            if (ToolbarKey.TRANSLATE.name !in migratedToolbar) {
                migratedToolbar.add(insertAt.coerceAtMost(migratedToolbar.size), ToolbarKey.TRANSLATE.name)
            }
        }
        val migrated = decoded.copy(
            dayNightAuto = if (storedVersion < 5) true else decoded.dayNightAuto,
            themeName = if (storedVersion < 3 && (decoded.themeName == "dynamic" || decoded.themeName == "dark")) "light" else decoded.themeName,
            nightThemeName = decoded.nightThemeName.ifBlank { "dark" },
            keyBorders = if (storedVersion < 3) true else decoded.keyBorders,
            showNumberRow = if (storedVersion < 3) true else decoded.showNumberRow,
            showEmojiKey = false,
            showLanguageSwitchKey = true,
            doubleSpacePeriod = false,
            toolbarKeys = migratedToolbar,
            subtypes = migratedSubtypes,
            schemaVersion = CURRENT_SETTINGS_SCHEMA_VERSION
        )
        prefs.edit { putString(KEY_SETTINGS, json.encodeToString(migrated)) }
        return migrated
    }

    private fun loadClipboard(): List<ClipboardEntry> {
        val raw = prefs.getString(KEY_CLIPBOARD, null) ?: return listOf(
            ClipboardEntry(
                id = 1L,
                text = "HeliBoard — 100% offline privacy keyboard",
                timestamp = System.currentTimeMillis(),
                isPinned = true,
                pinnedOrder = 0
            )
        )
        val parsed = parseLegacyCompatibleClipboard(raw)
        val (migrated, changed) = migratePinnedOrderPreservingInputOrder(parsed)

        // Ensure migrated pinned images attempt caching locally if URI is still readable
        var localCachedAny = false
        val hydrated = migrated.map { entry ->
            if (entry.isPinned && entry.isImage && !hasValidLocalImage(entry) && !entry.imageUri.isNullOrBlank()) {
                val cached = persistImageFromUri(Uri.parse(entry.imageUri), entry.id, entry.mimeType)
                if (cached != null) {
                    localCachedAny = true
                    entry.copy(localImagePath = cached.first, contentHash = entry.contentHash ?: cached.second)
                } else {
                    entry
                }
            } else {
                entry
            }
        }

        val sorted = sortClipboard(hydrated)
        if (changed || localCachedAny) {
            prefs.edit { putString(KEY_CLIPBOARD, json.encodeToString(sorted)) }
        }
        return sorted
    }

    private fun parseLegacyCompatibleClipboard(raw: String): List<ClipboardEntry> {
        val direct = runCatching { json.decodeFromString<List<ClipboardEntry>>(raw) }.getOrNull()
        if (direct != null) return direct

        // Fallback parser for older HeliBoard JSON schemas (e.g. timeStamp / content / uri / pinned)
        return runCatching {
            val element = json.parseToJsonElement(raw)
            val array = element as? JsonArray ?: return@runCatching emptyList()
            array.mapIndexedNotNull { idx, item ->
                val obj = item as? JsonObject ?: return@mapIndexedNotNull null
                val ts = obj["timestamp"]?.jsonPrimitive?.longOrNull
                    ?: obj["timeStamp"]?.jsonPrimitive?.longOrNull
                    ?: System.currentTimeMillis()
                val id = obj["id"]?.jsonPrimitive?.longOrNull ?: (ts + idx)
                val text = obj["text"]?.jsonPrimitive?.contentOrNull
                    ?: obj["content"]?.jsonPrimitive?.contentOrNull
                    ?: ""
                val pinned = obj["isPinned"]?.jsonPrimitive?.booleanOrNull
                    ?: obj["pinned"]?.jsonPrimitive?.booleanOrNull
                    ?: false
                val imageUri = obj["imageUri"]?.jsonPrimitive?.contentOrNull
                    ?: obj["uri"]?.jsonPrimitive?.contentOrNull
                val localImagePath = obj["localImagePath"]?.jsonPrimitive?.contentOrNull
                val mimeType = obj["mimeType"]?.jsonPrimitive?.contentOrNull
                val pinnedOrder = obj["pinnedOrder"]?.jsonPrimitive?.intOrNull
                val contentHash = obj["contentHash"]?.jsonPrimitive?.contentOrNull
                ClipboardEntry(
                    id = id,
                    text = text,
                    timestamp = ts,
                    isPinned = pinned,
                    imageUri = imageUri,
                    localImagePath = localImagePath,
                    mimeType = mimeType,
                    pinnedOrder = pinnedOrder,
                    contentHash = contentHash
                )
            }
        }.getOrDefault(emptyList())
    }

    private fun migratePinnedOrderPreservingInputOrder(
        entries: List<ClipboardEntry>
    ): Pair<List<ClipboardEntry>, Boolean> {
        var changed = false
        val pinnedItems = entries.filter { it.isPinned }
        val needsMigration = pinnedItems.any { it.pinnedOrder == null }
        if (!needsMigration) return entries to false

        // Keep explicit pinnedOrder first if any, then preserve exact array order for legacy entries
        val explicitMax = pinnedItems.mapNotNull { it.pinnedOrder }.maxOrNull() ?: -1
        var nextOrder = explicitMax + 1
        val assignedOrderById = mutableMapOf<Long, Int>()
        pinnedItems.forEach { entry ->
            if (entry.pinnedOrder != null) {
                assignedOrderById[entry.id] = entry.pinnedOrder
            } else {
                assignedOrderById[entry.id] = nextOrder++
                changed = true
            }
        }
        val updated = entries.map { entry ->
            if (entry.isPinned) {
                entry.copy(pinnedOrder = assignedOrderById[entry.id])
            } else {
                entry
            }
        }
        return updated to changed
    }

    private fun saveClipboard(items: List<ClipboardEntry>) {
        _clipboard.value = items
        prefs.edit { putString(KEY_CLIPBOARD, json.encodeToString(items)) }
        scheduleRoomSync(items)
    }

    private fun loadPersonalWords(): List<PersonalWordEntry> {
        val raw = prefs.getString(KEY_PERSONAL_WORDS, null) ?: return listOf(
            PersonalWordEntry(1L, "HeliBoard", "hb", 250, ""),
            PersonalWordEntry(2L, "On my way!", "omw", 240, "en-US")
        )
        return runCatching { json.decodeFromString<List<PersonalWordEntry>>(raw) }.getOrDefault(emptyList())
    }

    private fun savePersonalWords(items: List<PersonalWordEntry>) {
        _personalWords.value = items
        prefs.edit { putString(KEY_PERSONAL_WORDS, json.encodeToString(items)) }
    }

    private fun loadCustomThemes(): List<CustomThemeSpec> {
        val raw = prefs.getString(KEY_CUSTOM_THEMES, null) ?: return emptyList()
        return runCatching { json.decodeFromString<List<CustomThemeSpec>>(raw) }.getOrDefault(emptyList())
    }

    private fun loadCustomLayouts(): Map<String, String> {
        val raw = prefs.getString(KEY_CUSTOM_LAYOUTS, null) ?: return emptyMap()
        return runCatching { json.decodeFromString<Map<String, String>>(raw) }.getOrDefault(emptyMap())
    }

    companion object {
        private const val PREFS_NAME = "heliboard_prefs"
        private const val KEY_SETTINGS = "settings_json"
        private const val KEY_CLIPBOARD = "clipboard_json"
        private const val KEY_PERSONAL_WORDS = "personal_words_json"
        private const val KEY_CUSTOM_THEMES = "custom_themes_json"
        private const val KEY_CUSTOM_LAYOUTS = "custom_layouts_json"
        private const val CLIPBOARD_IMAGES_DIR = "clipboard_images"
        private const val EXTRA_IS_SENSITIVE_COMPAT = "android.content.extra.IS_SENSITIVE"
        private const val MAX_UNPINNED_CLIPS = 40
        private const val MAX_IMAGE_BYTES = 12 * 1024 * 1024L
        private const val CURRENT_SETTINGS_SCHEMA_VERSION = 6
    }
}
