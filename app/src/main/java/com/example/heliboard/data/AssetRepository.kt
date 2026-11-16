package com.example.heliboard.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.Locale

class AssetRepository(private val context: Context) {

    private var cachedLayouts: List<LayoutInfo>? = null
    private var cachedEmojis: List<EmojiCategoryData>? = null
    private val popupCache = HashMap<String, Map<String, List<PopupSpec>>>()

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
        val isArabic = isArabicContext(localeTag, layoutId)
        val parsedRows = if (raw.trimStart().startsWith("[")) {
            parseJsonRows(raw, popupMap, shiftState)
        } else {
            parseTxtRows(raw, popupMap)
        }

        val rows = mutableListOf<KeyRow>()
        if (showNumberRow) {
            rows += loadNumberRow(shiftState, isArabic)
        }

        val alphaRows = parsedRows.take(3).toMutableList()
        // Attach shift & backspace to the 3rd row if it's a standard 3-row alpha layout.
        // Arabic is a unicameral script and does not use a Shift key, keeping Row 3 uncrowded.
        if (alphaRows.size >= 3) {
            val row3 = alphaRows[2].keys.toMutableList()
            val hasShift = row3.any { it.actionType == KeyActionType.SHIFT }
            val hasDelete = row3.any { it.actionType == KeyActionType.DELETE }
            if (!hasShift && !isArabic) {
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
                        weight = if (isArabic) 1.35f else 1.45f
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
        rows += buildBottomAlphaRow(showLanguageSwitch, showEmojiKey, localeTag, popupMap)
        return KeyboardLayout(name = layoutId, rows = assignKeyIds(rows))
    }

    fun loadSymbolsLayout(shifted: Boolean, localeTag: String = "en-US"): KeyboardLayout {
        val isArabic = localeTag.startsWith("ar", ignoreCase = true)
        val path = when {
            shifted -> "layouts/more_symbols/symbols_shifted.txt"
            isArabic -> "layouts/symbols/symbols_arabic.txt"
            else -> "layouts/symbols/symbols.txt"
        }
        val raw = runCatching {
            context.assets.open(path).bufferedReader().use { it.readText() }
        }.getOrDefault("")
        val parsed = parseTxtRows(raw, emptyMap()).toMutableList()
        val rows = mutableListOf<KeyRow>()
        rows += loadNumberRow(if (shifted) ShiftState.SHIFTED else ShiftState.OFF, isArabic)

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

        val backLabel = if (isArabic) "أ ب ج" else "ABC"
        val commaSpec = if (isArabic) {
            KeySpec(
                label = "،",
                outputText = "،",
                hintLabel = "؛",
                popups = listOf(PopupSpec("؛"), PopupSpec("؟"), PopupSpec(","), PopupSpec(":")),
                weight = 0.95f
            )
        } else {
            KeySpec(",", hintLabel = ";", popups = listOf(PopupSpec(";"), PopupSpec(":")), weight = 0.95f)
        }
        val periodSpec = if (isArabic) {
            KeySpec(
                label = ".",
                outputText = ".",
                hintLabel = "؟",
                popups = listOf(PopupSpec("؟"), PopupSpec("!"), PopupSpec("،"), PopupSpec("…")),
                weight = 0.95f
            )
        } else {
            KeySpec(".", hintLabel = "!", popups = listOf(PopupSpec("!"), PopupSpec("?"), PopupSpec("…")), weight = 0.95f)
        }

        rows += KeyRow(
            listOf(
                KeySpec(backLabel, actionType = KeyActionType.SWITCH_ALPHA, isFunctional = true, weight = 1.4f),
                commaSpec,
                KeySpec("123", actionType = KeyActionType.SWITCH_NUMPAD, isFunctional = true, weight = 0.95f),
                KeySpec(" ", outputText = " ", actionType = KeyActionType.SPACE, weight = 4.2f),
                periodSpec,
                KeySpec("↵", outputText = "\n", actionType = KeyActionType.ENTER, isFunctional = true, weight = 1.4f)
            )
        )
        return KeyboardLayout(
            if (shifted) "symbols_shifted" else "symbols",
            assignKeyIds(rows)
        )
    }

    fun loadNumpadLayout(): KeyboardLayout {
        val rows = listOf(
            KeyRow(
                listOf(
                    KeySpec("+", popups = listOf("(", "<", "±").map { PopupSpec(it) }, isFunctional = true, weight = 1f),
                    KeySpec("1", weight = 1.3f),
                    KeySpec("2", weight = 1.3f),
                    KeySpec("3", weight = 1.3f),
                    KeySpec("%", popups = listOf("$", "€", "£").map { PopupSpec(it) }, isFunctional = true, weight = 1f)
                )
            ),
            KeyRow(
                listOf(
                    KeySpec("-", popups = listOf(")", ">", "~").map { PopupSpec(it) }, isFunctional = true, weight = 1f),
                    KeySpec("4", weight = 1.3f),
                    KeySpec("5", weight = 1.3f),
                    KeySpec("6", weight = 1.3f),
                    KeySpec("␣", outputText = " ", actionType = KeyActionType.SPACE, isFunctional = true, weight = 1f)
                )
            ),
            KeyRow(
                listOf(
                    KeySpec("*", popups = listOf("/", "×", "÷").map { PopupSpec(it) }, isFunctional = true, weight = 1f),
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
                    KeySpec("=", popups = listOf("≠", "≈").map { PopupSpec(it) }, weight = 0.85f),
                    KeySpec(".", weight = 0.85f),
                    KeySpec("↵", outputText = "\n", actionType = KeyActionType.ENTER, isFunctional = true, weight = 1.2f)
                )
            )
        )
        return KeyboardLayout("numpad", assignKeyIds(rows))
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
        localeTag: String,
        popupMap: Map<String, List<PopupSpec>>
    ): KeyRow {
        val isArabic = localeTag.startsWith("ar", ignoreCase = true)
        val keys = mutableListOf<KeySpec>()
        // 1. Symbols key (?123 / ؟١٢٣)
        keys += KeySpec(
            label = if (isArabic) "؟١٢٣" else "?123",
            actionType = KeyActionType.SWITCH_SYMBOLS,
            isFunctional = true,
            weight = 1.35f
        )
        // 2. Comma key with Emoji integrated as primary long-press shortcut
        val emojiPopup = PopupSpec(label = "☺", outputText = "", actionType = KeyActionType.EMOJI)
        val tashkeelPopups = popupMap["punctuation"].orEmpty()
        if (isArabic) {
            val commaPopups = mergePopups(
                listOf(emojiPopup, PopupSpec("؛"), PopupSpec("؟"), PopupSpec(","), PopupSpec(":")),
                tashkeelPopups
            )
            keys += KeySpec(
                label = "،",
                outputText = "،",
                hintLabel = "☺",
                popups = commaPopups,
                weight = 0.9f
            )
        } else {
            keys += KeySpec(
                label = ",",
                outputText = ",",
                hintLabel = "☺",
                popups = listOf(emojiPopup, PopupSpec(";"), PopupSpec(":"), PopupSpec("'")),
                weight = 0.9f
            )
        }
        // 3. Language switch Globe key
        if (showLanguageSwitch) {
            keys += KeySpec("🌐", actionType = KeyActionType.LANGUAGE_SWITCH, isFunctional = true, weight = 0.95f)
        }
        // Optional standalone emoji key only if explicitly enabled by user preference
        if (showEmojiKey) {
            keys += KeySpec("☺", actionType = KeyActionType.EMOJI, isFunctional = true, weight = 0.9f)
        }
        // 4. Wide Spacebar showing current writing language
        val langLabel = if (isArabic) {
            "العربية"
        } else {
            Locale.forLanguageTag(localeTag).getDisplayLanguage(Locale.ENGLISH).ifBlank { localeTag }
        }
        keys += KeySpec(
            label = langLabel,
            outputText = " ",
            actionType = KeyActionType.SPACE,
            weight = when {
                showLanguageSwitch && showEmojiKey -> 3.65f
                showLanguageSwitch -> 4.55f
                showEmojiKey -> 4.6f
                else -> 5.5f
            }
        )
        // 5. Period key
        if (isArabic) {
            val periodPopups = mergePopups(
                listOf("؟", "!", "،", "؛", "…", "«", "»", "-", "\"", "'", "(", ")").map { PopupSpec(it) },
                tashkeelPopups
            )
            keys += KeySpec(
                label = ".",
                outputText = ".",
                popups = periodPopups,
                hintLabel = "؟",
                weight = 0.9f
            )
        } else {
            keys += KeySpec(
                label = ".",
                outputText = ".",
                popups = listOf("!", "?", ",", "…", "/", "@", "#", ":", ";", "-", "'", "\"").map { PopupSpec(it) },
                hintLabel = "!",
                weight = 0.9f
            )
        }
        // 6. Adaptive Enter / Action key
        keys += KeySpec("↵", outputText = "\n", actionType = KeyActionType.ENTER, isFunctional = true, weight = 1.35f)
        return KeyRow(keys)
    }

    private fun loadNumberRow(shiftState: ShiftState, isArabic: Boolean = false): KeyRow {
        val westernDigits = listOf("1", "2", "3", "4", "5", "6", "7", "8", "9", "0")
        val arabicDigits = listOf("١", "٢", "٣", "٤", "٥", "٦", "٧", "٨", "٩", "٠")
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
        val digits = if (isArabic) arabicDigits else westernDigits
        val altDigits = if (isArabic) westernDigits else arabicDigits
        return KeyRow(
            digits.indices.map { idx ->
                val ch = digits[idx]
                val mergedPopups = listOf(PopupSpec(altDigits[idx])) + popupsList[idx].map { PopupSpec(it) }
                KeySpec(
                    label = ch,
                    outputText = ch,
                    shiftedLabel = ch,
                    shiftedOutput = ch,
                    hintLabel = null,
                    popups = mergedPopups,
                    weight = 1f
                )
            }
        )
    }

    private fun parseTxtRows(raw: String, popupMap: Map<String, List<PopupSpec>>): List<KeyRow> {
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
            val primarySpec = parsePopupToken(tokens.first()) ?: continue
            val inlinePopups = tokens.drop(1).mapNotNull { parsePopupToken(it) }
            val mappedPopups = popupMap[primarySpec.label.lowercase()].orEmpty() +
                popupMap[primarySpec.outputText.lowercase()].orEmpty()
            val mergedPopups = mergePopups(inlinePopups, mappedPopups)
            currentRow += KeySpec(
                label = primarySpec.label,
                outputText = primarySpec.outputText,
                hintLabel = mergedPopups.firstOrNull()?.label,
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
        popupMap: Map<String, List<PopupSpec>>,
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
        popupMap: Map<String, List<PopupSpec>>,
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
        val parsedPrimary = parsePopupToken(rawLabel) ?: return null
        val label = parsedPrimary.label
        val width = resolved.optDouble("width", 0.0).toFloat()
        val weight = if (width > 0f) (width * 10f).coerceIn(0.6f, 3f) else 1f

        val inlinePopups = mutableListOf<PopupSpec>()
        val popupObj = resolved.optJSONObject("popup")
        if (popupObj != null) {
            popupObj.optJSONObject("main")?.optString("label")?.takeIf { it.isNotEmpty() }?.let {
                parsePopupToken(it)?.let { p -> inlinePopups += p }
            }
            val relevant = popupObj.optJSONArray("relevant")
            if (relevant != null) {
                for (k in 0 until relevant.length()) {
                    val pLabel = relevant.optJSONObject(k)?.optString("label").orEmpty()
                    if (pLabel.isNotEmpty()) {
                        parsePopupToken(pLabel)?.let { p -> inlinePopups += p }
                    }
                }
            }
        }
        val mergedPopups = mergePopups(inlinePopups, popupMap[label.lowercase()].orEmpty())

        return when (label.lowercase()) {
            "shift" -> KeySpec("⇧", actionType = KeyActionType.SHIFT, isFunctional = true, weight = weight.coerceAtLeast(1.35f))
            "delete" -> KeySpec("⌫", actionType = KeyActionType.DELETE, isFunctional = true, weight = weight.coerceAtLeast(1.35f))
            "space" -> KeySpec(" ", outputText = " ", actionType = KeyActionType.SPACE, weight = weight.coerceAtLeast(3.5f))
            "action", "enter" -> KeySpec("↵", outputText = "\n", actionType = KeyActionType.ENTER, isFunctional = true, weight = weight.coerceAtLeast(1.35f))
            else -> KeySpec(
                label = label,
                outputText = parsedPrimary.outputText,
                hintLabel = mergedPopups.firstOrNull()?.label,
                popups = mergedPopups,
                weight = weight
            )
        }
    }

    private fun loadPopupMap(localeTag: String): Map<String, List<PopupSpec>> {
        val lang = localeTag.substringBefore("-").lowercase()
        popupCache[lang]?.let { return it }

        val map = HashMap<String, MutableList<PopupSpec>>()
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
                            val base = normalizeToken(parts.first()).lowercase()
                            val bucket = map.getOrPut(base) { mutableListOf() }
                            parts.drop(1).forEach { rawToken ->
                                val parsed = parsePopupToken(rawToken) ?: return@forEach
                                if (bucket.none { it.outputText == parsed.outputText && it.label == parsed.label }) {
                                    bucket += parsed
                                }
                            }
                        }
                    }
                }
            }
        }
        // Mirror 'ه' popups to Arabic presentation form 'ﻩ' used in Hijai layout
        map["ه"]?.let { hehPopups ->
            val altBucket = map.getOrPut("ﻩ") { mutableListOf() }
            hehPopups.forEach { p ->
                if (altBucket.none { it.outputText == p.outputText && it.label == p.label }) {
                    altBucket += p
                }
            }
        }
        popupCache[lang] = map
        return map
    }

    private fun parsePopupToken(rawToken: String): PopupSpec? {
        val trimmed = rawToken.trim()
        if (trimmed.isEmpty()) return null
        // Skip layout metadata directives such as !fixedColumnOrder!5
        if (trimmed.startsWith("!") && trimmed.length > 1) return null
        if (trimmed == "|") return PopupSpec(label = "|", outputText = "|")
        if ("|" in trimmed) {
            val idx = trimmed.indexOf('|')
            val display = normalizeToken(trimmed.substring(0, idx))
            val output = normalizeToken(trimmed.substring(idx + 1))
            if (display.isEmpty() && output.isEmpty()) return null
            val resolvedDisplay = display.ifEmpty { output }
            val resolvedOutput = output.ifEmpty { resolvedDisplay }
            return PopupSpec(label = resolvedDisplay, outputText = resolvedOutput)
        }
        val normalized = normalizeToken(trimmed)
        return PopupSpec(label = normalized, outputText = normalized)
    }

    private fun mergePopups(first: List<PopupSpec>, second: List<PopupSpec>): List<PopupSpec> {
        val out = mutableListOf<PopupSpec>()
        val seen = HashSet<String>()
        for (item in first + second) {
            val key = "${item.label}\u0000${item.outputText}"
            if (seen.add(key)) {
                out += item
            }
        }
        return out
    }

    private fun assignKeyIds(rows: List<KeyRow>): List<KeyRow> {
        return rows.mapIndexed { rowIdx, row ->
            KeyRow(
                row.keys.mapIndexed { colIdx, key ->
                    key.copy(keyId = "r${rowIdx}_c${colIdx}_${key.actionType.name}_${key.label}")
                }
            )
        }
    }

    private fun isArabicContext(localeTag: String, layoutId: String): Boolean {
        return localeTag.startsWith("ar", ignoreCase = true) ||
            layoutId.startsWith("arabic", ignoreCase = true)
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
