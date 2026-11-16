package com.example.heliboard.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class AssetRepository(private val context: Context) {

    private var cachedLayouts: List<LayoutInfo>? = null
    private var cachedEmojis: List<EmojiCategoryData>? = null
    private val popupCache = HashMap<String, Map<String, List<String>>>()

    fun getAvailableMainLayouts(): List<LayoutInfo> {
        cachedLayouts?.let { return it }
        val files = context.assets.list("layouts/main").orEmpty().sorted()
        val list = files.mapNotNull { file ->
            when {
                file.endsWith(".json") -> {
                    val id = file.removeSuffix(".json")
                    LayoutInfo(
                        id = id,
                        displayName = formatLayoutName(id),
                        assetPath = "layouts/main/$file",
                        isJson = true
                    )
                }
                file.endsWith(".txt") -> {
                    val id = file.removeSuffix(".txt")
                    LayoutInfo(
                        id = id,
                        displayName = formatLayoutName(id),
                        assetPath = "layouts/main/$file",
                        isJson = false
                    )
                }
                else -> null
            }
        }
        cachedLayouts = list
        return list
    }

    fun readLayoutSource(layoutId: String, customLayouts: Map<String, String>): String {
        customLayouts[layoutId]?.let { return it }
        val info = getAvailableMainLayouts().firstOrNull { it.id == layoutId }
            ?: getAvailableMainLayouts().firstOrNull { it.id == "qwerty" }
            ?: return DEFAULT_QWERTY_TEXT
        return runCatching {
            context.assets.open(info.assetPath).bufferedReader().use { it.readText() }
        }.getOrDefault(DEFAULT_QWERTY_TEXT)
    }

    fun loadMainKeyboardLayout(
        layoutId: String,
        localeTag: String,
        customLayouts: Map<String, String>,
        showNumberRow: Boolean,
        showLanguageSwitch: Boolean,
        showEmojiKey: Boolean,
        shiftState: ShiftState
    ): KeyboardLayout {
        val raw = readLayoutSource(layoutId, customLayouts)
        val popupMap = loadPopupMap(localeTag)
        val parsedRows = if (raw.trimStart().startsWith("[")) {
            parseJsonRows(raw, popupMap, shiftState)
        } else {
            parseTxtRows(raw, popupMap)
        }

        val rows = mutableListOf<KeyRow>()
        if (showNumberRow) {
            rows += loadNumberRow(shiftState)
        }

        val alphaRows = parsedRows.take(3).toMutableList()
        // Attach shift & backspace to the 3rd row if it's a standard 3-row alpha layout
        if (alphaRows.size >= 3) {
            val row3 = alphaRows[2].keys.toMutableList()
            val hasShift = row3.any { it.actionType == KeyActionType.SHIFT }
            val hasDelete = row3.any { it.actionType == KeyActionType.DELETE }
            if (!hasShift) {
                row3.add(
                    0,
                    KeySpec(
                        label = "⇧",
                        actionType = KeyActionType.SHIFT,
                        isFunctional = true,
                        weight = 1.45f
                    )
                )
            }
            if (!hasDelete) {
                row3.add(
                    KeySpec(
                        label = "⌫",
                        actionType = KeyActionType.DELETE,
                        isFunctional = true,
                        weight = 1.45f
                    )
                )
            }
            alphaRows[2] = KeyRow(row3)
        }
        rows.addAll(alphaRows)

        // Add any extra rows (e.g., 4-row layouts)
        if (parsedRows.size > 3) {
            rows.addAll(parsedRows.drop(3))
        }

        // Bottom functional row
        rows += buildBottomAlphaRow(showLanguageSwitch, showEmojiKey, localeTag)
        return KeyboardLayout(name = layoutId, rows = rows)
    }

    fun loadSymbolsLayout(shifted: Boolean): KeyboardLayout {
        val path = if (shifted) "layouts/more_symbols/symbols_shifted.txt" else "layouts/symbols/symbols.txt"
        val raw = runCatching {
            context.assets.open(path).bufferedReader().use { it.readText() }
        }.getOrDefault("")
        val parsed = parseTxtRows(raw, emptyMap()).toMutableList()
        val rows = mutableListOf<KeyRow>()
        rows += loadNumberRow(if (shifted) ShiftState.SHIFTED else ShiftState.OFF)

        if (parsed.size >= 3) {
            rows += parsed[0]
            rows += parsed[1]
            val row3 = parsed[2].keys.toMutableList()
            row3.add(
                0,
                KeySpec(
                    label = if (shifted) "?123" else "=\\<",
                    actionType = KeyActionType.SWITCH_MORE_SYMBOLS,
                    isFunctional = true,
                    weight = 1.4f
                )
            )
            row3.add(
                KeySpec(
                    label = "⌫",
                    actionType = KeyActionType.DELETE,
                    isFunctional = true,
                    weight = 1.4f
                )
            )
            rows += KeyRow(row3)
        } else {
            rows.addAll(parsed)
        }

        rows += KeyRow(
            listOf(
                KeySpec("ABC", actionType = KeyActionType.SWITCH_ALPHA, isFunctional = true, weight = 1.5f),
                KeySpec(",", popups = listOf(";", ":"), weight = 1f),
                KeySpec("123", actionType = KeyActionType.SWITCH_NUMPAD, isFunctional = true, weight = 1f),
                KeySpec(" ", outputText = " ", actionType = KeyActionType.SPACE, weight = 3.8f),
                KeySpec(".", popups = listOf("!", "?", "…"), weight = 1f),
                KeySpec("↵", outputText = "\n", actionType = KeyActionType.ENTER, isFunctional = true, weight = 1.5f)
            )
        )
        return KeyboardLayout(if (shifted) "symbols_shifted" else "symbols", rows)
    }

    fun loadNumpadLayout(): KeyboardLayout {
        val rows = listOf(
            KeyRow(
                listOf(
                    KeySpec("+", popups = listOf("(", "<", "±"), isFunctional = true, weight = 1f),
                    KeySpec("1", weight = 1.3f),
                    KeySpec("2", weight = 1.3f),
                    KeySpec("3", weight = 1.3f),
                    KeySpec("%", popups = listOf("$", "€", "£"), isFunctional = true, weight = 1f)
                )
            ),
            KeyRow(
                listOf(
                    KeySpec("-", popups = listOf(")", ">", "~"), isFunctional = true, weight = 1f),
                    KeySpec("4", weight = 1.3f),
                    KeySpec("5", weight = 1.3f),
                    KeySpec("6", weight = 1.3f),
                    KeySpec("␣", outputText = " ", actionType = KeyActionType.SPACE, isFunctional = true, weight = 1f)
                )
            ),
            KeyRow(
                listOf(
                    KeySpec("*", popups = listOf("/", "×", "÷"), isFunctional = true, weight = 1f),
                    KeySpec("7", weight = 1.3f),
                    KeySpec("8", weight = 1.3f),
                    KeySpec("9", weight = 1.3f),
                    KeySpec("⌫", actionType = KeyActionType.DELETE, isFunctional = true, weight = 1f)
                )
            ),
            KeyRow(
                listOf(
                    KeySpec("ABC", actionType = KeyActionType.SWITCH_ALPHA, isFunctional = true, weight = 1.2f),
                    KeySpec(",", weight = 0.85f),
                    KeySpec("?123", actionType = KeyActionType.SWITCH_SYMBOLS, isFunctional = true, weight = 1f),
                    KeySpec("0", weight = 1.3f),
                    KeySpec("=", popups = listOf("≠", "≈"), weight = 0.85f),
                    KeySpec(".", weight = 0.85f),
                    KeySpec("↵", outputText = "\n", actionType = KeyActionType.ENTER, isFunctional = true, weight = 1.2f)
                )
            )
        )
        return KeyboardLayout("numpad", rows)
    }

    fun loadEmojiCategories(): List<EmojiCategoryData> {
        cachedEmojis?.let { return it }
        val specs = listOf(
            Triple("SMILEYS_AND_EMOTION", "Smileys", "emoji_smileys"),
            Triple("PEOPLE_AND_BODY", "People", "emoji_people"),
            Triple("ANIMALS_AND_NATURE", "Nature", "emoji_nature"),
            Triple("FOOD_AND_DRINK", "Food", "emoji_food"),
            Triple("TRAVEL_AND_PLACES", "Travel", "emoji_travel"),
            Triple("ACTIVITIES", "Activities", "emoji_activities"),
            Triple("OBJECTS", "Objects", "emoji_objects"),
            Triple("SYMBOLS", "Symbols", "emoji_symbols"),
            Triple("FLAGS", "Flags", "emoji_flags"),
            Triple("EMOTICONS", "Kaomoji", "emoji_emoticons")
        )
        val categories = specs.map { (fileBase, title, icon) ->
            val emojis = runCatching {
                context.assets.open("emoji/$fileBase.txt").bufferedReader().useLines { lines ->
                    lines.map { it.trim() }
                        .filter { it.isNotEmpty() && !it.startsWith("#") }
                        .map { line -> line.substringBefore(" ").trim() }
                        .filter { it.isNotEmpty() }
                        .toList()
                }
            }.getOrDefault(emptyList())
            EmojiCategoryData(fileBase, title, icon, emojis)
        }.filter { it.emojis.isNotEmpty() }
        cachedEmojis = categories
        return categories
    }

    private fun buildBottomAlphaRow(
        showLanguageSwitch: Boolean,
        showEmojiKey: Boolean,
        localeTag: String
    ): KeyRow {
        val keys = mutableListOf<KeySpec>()
        keys += KeySpec("?123", actionType = KeyActionType.SWITCH_SYMBOLS, isFunctional = true, weight = 1.45f)
        if (showLanguageSwitch) {
            keys += KeySpec("🌐", actionType = KeyActionType.LANGUAGE_SWITCH, isFunctional = true, weight = 1f)
        }
        keys += KeySpec(",", popups = listOf(";", ":", "'"), weight = 0.9f)
        if (showEmojiKey) {
            keys += KeySpec("☺", actionType = KeyActionType.EMOJI, isFunctional = true, weight = 1f)
        }
        val langLabel = Locale.forLanguageTag(localeTag).displayLanguage.ifBlank { localeTag }
        keys += KeySpec(
            label = langLabel,
            outputText = " ",
            actionType = KeyActionType.SPACE,
            weight = 4.2f
        )
        keys += KeySpec(
            label = ".",
            popups = listOf("!", "?", ",", "…", "/", "@", "#", ":", ";", "-", "'", "\""),
            hintLabel = "…",
            weight = 0.95f
        )
        keys += KeySpec("↵", outputText = "\n", actionType = KeyActionType.ENTER, isFunctional = true, weight = 1.5f)
        return KeyRow(keys)
    }

    private fun loadNumberRow(shiftState: ShiftState): KeyRow {
        val digits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        val shifted = listOf("!", "@", "#", "$", "%", "^", "&", "*", "(", ")")
        val popupsList = listOf(
            listOf("¹", "½", "⅓", "¼"),
            listOf("²", "⅔"),
            listOf("³", "¾", "⅜"),
            listOf("⁴"),
            listOf("⁵", "⅝"),
            listOf("⁶"),
            listOf("⁷", "⅞"),
            listOf("⁸"),
            listOf("⁹"),
            listOf("⁰", "ⁿ", "∅")
        )
        val useShifted = shiftState != ShiftState.OFF
        return KeyRow(
            digits.indices.map { idx ->
                val ch = if (useShifted) shifted[idx] else digits[idx]
                KeySpec(
                    label = ch,
                    outputText = ch,
                    hintLabel = if (useShifted) digits[idx] else shifted[idx],
                    popups = popupsList[idx],
                    weight = 1f
                )
            }
        )
    }

    private fun parseTxtRows(raw: String, popupMap: Map<String, List<String>>): List<KeyRow> {
        val rows = mutableListOf<KeyRow>()
        var currentRow = mutableListOf<KeySpec>()

        for (rawLine in raw.lines()) {
            val line = rawLine.trim()
            if (line.isEmpty()) {
                if (currentRow.isNotEmpty()) {
                    rows += KeyRow(currentRow)
                    currentRow = mutableListOf()
                }
                continue
            }
            if (line.startsWith("#")) continue
            val tokens = line.split(Regex("\\s+")).filter { it.isNotEmpty() }
            if (tokens.isEmpty()) continue
            val primary = normalizeToken(tokens.first())
            val inlinePopups = tokens.drop(1).map { normalizeToken(it) }
            val mergedPopups = (inlinePopups + popupMap[primary.lowercase()].orEmpty()).distinct()
            currentRow += KeySpec(
                label = primary,
                outputText = primary,
                hintLabel = mergedPopups.firstOrNull(),
                popups = mergedPopups
            )
        }
        if (currentRow.isNotEmpty()) {
            rows += KeyRow(currentRow)
        }
        return rows
    }

    private fun parseJsonRows(
        raw: String,
        popupMap: Map<String, List<String>>,
        shiftState: ShiftState
    ): List<KeyRow> {
        val rows = mutableListOf<KeyRow>()
        val root = runCatching { JSONArray(raw) }.getOrNull() ?: return emptyList()
        for (i in 0 until root.length()) {
            val rowArray = root.optJSONArray(i) ?: continue
            val keys = mutableListOf<KeySpec>()
            for (j in 0 until rowArray.length()) {
                val obj = rowArray.optJSONObject(j) ?: continue
                val keySpec = parseJsonKey(obj, popupMap, shiftState)
                if (keySpec != null) keys += keySpec
            }
            if (keys.isNotEmpty()) rows += KeyRow(keys)
        }
        return rows
    }

    private fun parseJsonKey(
        obj: JSONObject,
        popupMap: Map<String, List<String>>,
        shiftState: ShiftState
    ): KeySpec? {
        val selector = obj.optString("$", "")
        val resolved = when (selector) {
            "shift_state_selector" -> {
                when (shiftState) {
                    ShiftState.SHIFTED -> obj.optJSONObject("shiftedManual")
                        ?: obj.optJSONObject("manualOrLocked")
                        ?: obj.optJSONObject("default")
                    ShiftState.CAPS_LOCK -> obj.optJSONObject("manualOrLocked")
                        ?: obj.optJSONObject("shiftedManual")
                        ?: obj.optJSONObject("default")
                    ShiftState.OFF -> obj.optJSONObject("default")
                } ?: obj
            }
            "case_selector" -> {
                if (shiftState != ShiftState.OFF) {
                    obj.optJSONObject("upper") ?: obj.optJSONObject("default")
                } else {
                    obj.optJSONObject("lower") ?: obj.optJSONObject("default")
                } ?: obj
            }
            else -> obj
        }

        val type = resolved.optString("type", "")
        if (type == "placeholder") return null
        val rawLabel = resolved.optString("label", "")
        if (rawLabel.isEmpty()) return null
        val label = normalizeToken(rawLabel)
        val width = resolved.optDouble("width", 0.0).toFloat()
        val weight = if (width > 0f) (width * 10f).coerceIn(0.6f, 3f) else 1f

        val popups = mutableListOf<String>()
        val popupObj = resolved.optJSONObject("popup")
        if (popupObj != null) {
            popupObj.optJSONObject("main")?.optString("label")?.takeIf { it.isNotEmpty() }?.let {
                popups += normalizeToken(it)
            }
            val relevant = popupObj.optJSONArray("relevant")
            if (relevant != null) {
                for (k in 0 until relevant.length()) {
                    val pLabel = relevant.optJSONObject(k)?.optString("label").orEmpty()
                    if (pLabel.isNotEmpty()) popups += normalizeToken(pLabel)
                }
            }
        }
        popups += popupMap[label.lowercase()].orEmpty()
        val distinctPopups = popups.distinct()

        return when (label.lowercase()) {
            "shift" -> KeySpec("⇧", actionType = KeyActionType.SHIFT, isFunctional = true, weight = weight.coerceAtLeast(1.35f))
            "delete" -> KeySpec("⌫", actionType = KeyActionType.DELETE, isFunctional = true, weight = weight.coerceAtLeast(1.35f))
            "space" -> KeySpec(" ", outputText = " ", actionType = KeyActionType.SPACE, weight = weight.coerceAtLeast(3.5f))
            "action", "enter" -> KeySpec("↵", outputText = "\n", actionType = KeyActionType.ENTER, isFunctional = true, weight = weight.coerceAtLeast(1.35f))
            else -> KeySpec(
                label = label,
                outputText = label,
                hintLabel = distinctPopups.firstOrNull(),
                popups = distinctPopups,
                weight = weight
            )
        }
    }

    private fun loadPopupMap(localeTag: String): Map<String, List<String>> {
        val lang = localeTag.substringBefore("-").lowercase()
        popupCache[lang]?.let { return it }

        val map = HashMap<String, MutableList<String>>()
        val candidateFiles = listOfNotNull(
            "locale_key_texts/$lang.txt",
            "locale_key_texts/more_popups_main.txt"
        )
        for (path in candidateFiles) {
            runCatching {
                context.assets.open(path).bufferedReader().useLines { lines ->
                    var inPopupSection = false
                    lines.forEach { rawLine ->
                        val line = rawLine.trim()
                        if (line.startsWith("[")) {
                            inPopupSection = line == "[popup_keys]"
                            return@forEach
                        }
                        if (!inPopupSection || line.isEmpty() || line.startsWith("#")) return@forEach
                        val parts = line.split(Regex("\\s+")).filter { it.isNotEmpty() }
                        if (parts.size >= 2) {
                            val base = parts.first().lowercase()
                            val bucket = map.getOrPut(base) { mutableListOf() }
                            parts.drop(1).forEach { p ->
                                if (p !in bucket) bucket += p
                            }
                        }
                    }
                }
            }
        }
        popupCache[lang] = map
        return map
    }

    private fun normalizeToken(token: String): String {
        return when (token) {
            "$$$" -> "$"
            else -> token
        }
    }

    private fun formatLayoutName(id: String): String {
        return id.split("_").joinToString(" ") { part ->
            if (part.length <= 2 && part.all { it.isLetter() }) part.uppercase()
            else part.replaceFirstChar { it.uppercase() }
        }
    }

    companion object {
        private const val DEFAULT_QWERTY_TEXT = "q\nw\ne\nr\nt\ny\nu\ni\no\np\n\na\ns\nd\nf\ng\nh\nj\nk\nl\n\nz\nx\nc\nv\nb\nn\nm\n"
    }
}
