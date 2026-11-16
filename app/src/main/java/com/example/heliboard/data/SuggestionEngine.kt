package com.example.heliboard.data

import kotlin.math.abs

class SuggestionEngine {

    private val baseWords = LinkedHashMap<String, Int>()
    private val customDictionaryWords = LinkedHashMap<String, Int>()
    private val learnedWords = LinkedHashMap<String, Int>()
    private val learnedBigrams = HashMap<String, LinkedHashMap<String, Int>>()

    init {
        seedCoreVocabulary()
    }

    fun learnWord(word: String, previousWord: String? = null) {
        val clean = word.trim()
        if (clean.length < 2 || clean.any { it.isDigit() }) return
        val key = clean.lowercase()
        learnedWords[key] = (learnedWords[key] ?: 0) + 15
        if (!previousWord.isNullOrBlank()) {
            val prevKey = previousWord.trim().lowercase()
            val nextMap = learnedBigrams.getOrPut(prevKey) { LinkedHashMap() }
            nextMap[clean] = (nextMap[clean] ?: 0) + 20
        }
    }

    fun clearLearnedWords() {
        learnedWords.clear()
        learnedBigrams.clear()
    }

    fun importWordlist(content: String): Int {
        var added = 0
        content.lineSequence().forEach { raw ->
            val line = raw.trim()
            if (line.isEmpty() || line.startsWith("#")) return@forEach
            val word = when {
                line.contains("word=") -> line.substringAfter("word=").substringBefore(",").trim()
                line.contains("\t") -> line.substringBefore("\t").trim()
                else -> line.substringBefore(" ").trim()
            }
            if (word.length >= 2 && word.none { it == '=' || it == ':' }) {
                val freq = if (line.contains("f=")) {
                    line.substringAfter("f=").substringBefore(",").trim().toIntOrNull() ?: 120
                } else 120
                customDictionaryWords[word] = freq
                added++
            }
        }
        return added
    }

    fun getCustomDictionaryCount(): Int = customDictionaryWords.size
    fun getLearnedWordsCount(): Int = learnedWords.size

    fun suggest(
        currentWord: String,
        previousWord: String?,
        personalWords: List<PersonalWordEntry>,
        suggestEmojis: Boolean,
        maxResults: Int = 3
    ): List<String> {
        val trimmed = currentWord.trim()
        if (trimmed.isEmpty()) {
            val prevKey = previousWord?.trim()?.lowercase().orEmpty()
            val bigramResults = learnedBigrams[prevKey]
                ?.entries
                ?.sortedByDescending { it.value }
                ?.map { it.key }
                .orEmpty()
            val defaults = DEFAULT_NEXT_WORDS[prevKey] ?: listOf("the", "I", "and", "to", "is")
            return (bigramResults + defaults).distinct().take(maxResults)
        }

        val lower = trimmed.lowercase()
        val candidates = LinkedHashMap<String, Int>()

        // 1. Exact shortcut match from personal dictionary
        personalWords.filter { it.shortcut.equals(lower, ignoreCase = true) }.forEach {
            candidates[it.word] = 100_000 + it.weight
        }

        // 2. Prefix matches from personal dictionary
        personalWords.filter { it.word.lowercase().startsWith(lower) }.forEach {
            candidates[it.word] = maxOf(candidates[it.word] ?: 0, 50_000 + it.weight)
        }

        // 3. Learned user words
        learnedWords.forEach { (w, score) ->
            if (w.startsWith(lower)) {
                val display = matchCase(w, trimmed)
                candidates[display] = maxOf(candidates[display] ?: 0, 20_000 + score)
            }
        }

        // 4. Custom imported wordlist
        customDictionaryWords.forEach { (w, score) ->
            if (w.lowercase().startsWith(lower)) {
                val display = matchCase(w, trimmed)
                candidates[display] = maxOf(candidates[display] ?: 0, 10_000 + score)
            }
        }

        // 5. Core vocabulary prefix matches
        baseWords.forEach { (w, score) ->
            if (w.lowercase().startsWith(lower)) {
                val display = if (w.first().isUpperCase()) w else matchCase(w, trimmed)
                candidates[display] = maxOf(candidates[display] ?: 0, 5_000 + score)
            }
        }

        // 6. Fuzzy typo correction (edit distance <= 1) if few prefix matches
        if (candidates.size < maxResults && lower.length >= 3) {
            baseWords.forEach { (w, score) ->
                val wl = w.lowercase()
                if (abs(wl.length - lower.length) <= 1 && isWithinEditDistanceOne(lower, wl)) {
                    val display = if (w.first().isUpperCase()) w else matchCase(w, trimmed)
                    candidates[display] = maxOf(candidates[display] ?: 0, 1_500 + score)
                }
            }
        }

        // 7. Inline emoji match
        val emojiMatch = if (suggestEmojis) EMOJI_KEYWORDS[lower] else null

        val sorted = candidates.entries
            .sortedWith(
                compareByDescending<Map.Entry<String, Int>> {
                    it.key.equals(trimmed, ignoreCase = true)
                }.thenByDescending { it.value }
            )
            .map { it.key }
            .toMutableList()

        if (emojiMatch != null) {
            if (sorted.size >= maxResults) {
                sorted[maxResults - 1] = emojiMatch
            } else {
                sorted.add(emojiMatch)
            }
        }

        return sorted.distinct().take(maxResults)
    }

    fun autocorrectCandidate(
        word: String,
        personalWords: List<PersonalWordEntry>
    ): String? {
        val trimmed = word.trim()
        if (trimmed.length < 2) return null
        val lower = trimmed.lowercase()

        // Shortcut expansion takes highest priority
        personalWords.firstOrNull { it.shortcut.equals(lower, ignoreCase = true) }?.let {
            return it.word
        }

        // Common contractions & typos
        COMMON_TYPOS[lower]?.let { return matchCase(it, trimmed) }

        // If already a known valid word, don't alter it
        if (personalWords.any { it.word.equals(trimmed, ignoreCase = true) } ||
            learnedWords.containsKey(lower) ||
            baseWords.keys.any { it.equals(lower, ignoreCase = true) } ||
            customDictionaryWords.keys.any { it.equals(lower, ignoreCase = true) }
        ) {
            return null
        }

        // Find best single-edit match
        var bestWord: String? = null
        var bestScore = -1
        for ((w, score) in baseWords) {
            val wl = w.lowercase()
            if (abs(wl.length - lower.length) <= 1 && isWithinEditDistanceOne(lower, wl)) {
                if (score > bestScore) {
                    bestScore = score
                    bestWord = if (w.first().isUpperCase()) w else matchCase(w, trimmed)
                }
            }
        }
        return bestWord
    }

    fun isKnownWord(word: String, personalWords: List<PersonalWordEntry>): Boolean {
        val lower = word.trim().lowercase()
        if (lower.isEmpty()) return true
        return baseWords.keys.any { it.equals(lower, ignoreCase = true) } ||
            learnedWords.containsKey(lower) ||
            customDictionaryWords.keys.any { it.equals(lower, ignoreCase = true) } ||
            personalWords.any { it.word.equals(lower, ignoreCase = true) }
    }

    private fun matchCase(target: String, typed: String): String {
        if (typed.isEmpty()) return target
        if (typed.length > 1 && typed.all { it.isUpperCase() }) return target.uppercase()
        if (typed.first().isUpperCase()) return target.replaceFirstChar { it.uppercase() }
        return target
    }

    private fun isWithinEditDistanceOne(a: String, b: String): Boolean {
        val la = a.length
        val lb = b.length
        if (abs(la - lb) > 1) return false
        var i = 0
        var j = 0
        var edits = 0
        while (i < la && j < lb) {
            if (a[i] == b[j]) {
                i++
                j++
            } else {
                // Check adjacent transposition
                if (edits == 0 && i + 1 < la && j + 1 < lb && a[i] == b[j + 1] && a[i + 1] == b[j]) {
                    edits++
                    i += 2
                    j += 2
                    continue
                }
                edits++
                if (edits > 1) return false
                when {
                    la > lb -> i++
                    lb > la -> j++
                    else -> {
                        i++
                        j++
                    }
                }
            }
        }
        if (i < la || j < lb) edits++
        return edits <= 1
    }

    private fun seedCoreVocabulary() {
        CORE_WORDS.forEachIndexed { index, word ->
            baseWords[word] = (CORE_WORDS.size - index) + 100
        }
    }

    companion object {
        private val COMMON_TYPOS = mapOf(
            "teh" to "the",
            "adn" to "and",
            "taht" to "that",
            "wiht" to "with",
            "thsi" to "this",
            "waht" to "what",
            "jsut" to "just",
            "dont" to "don't",
            "cant" to "can't",
            "wont" to "won't",
            "im" to "I'm",
            "ive" to "I've",
            "id" to "I'd",
            "ill" to "I'll",
            "youre" to "you're",
            "theyre" to "they're",
            "recieve" to "receive",
            "seperate" to "separate",
            "definately" to "definitely",
            "occured" to "occurred",
            "becuase" to "because",
            "helo" to "hello",
            "thnaks" to "thanks",
            "plese" to "please"
        )

        private val DEFAULT_NEXT_WORDS = mapOf(
            "i" to listOf("am", "have", "will", "think", "can"),
            "the" to listOf("best", "new", "first", "same", "next"),
            "how" to listOf("are", "do", "is", "can", "did"),
            "thank" to listOf("you", "god", "goodness"),
            "good" to listOf("morning", "night", "luck", "job", "idea"),
            "see" to listOf("you", "soon", "later", "if"),
            "on" to listOf("my", "the", "time", "this")
        )

        private val EMOJI_KEYWORDS = mapOf(
            "smile" to "😊", "happy" to "😄", "laugh" to "😂", "lol" to "🤣",
            "love" to "❤️", "heart" to "❤️", "like" to "👍", "thumbs" to "👍",
            "ok" to "👌", "clap" to "👏", "pray" to "🙏", "thanks" to "🙏",
            "fire" to "🔥", "hot" to "🔥", "star" to "⭐", "sparkle" to "✨",
            "party" to "🎉", "congrats" to "🎉", "sun" to "☀️", "moon" to "🌙",
            "rain" to "🌧️", "coffee" to "☕", "pizza" to "🍕", "cake" to "🎂",
            "rocket" to "🚀", "check" to "✅", "yes" to "✅", "no" to "❌",
            "warning" to "⚠️", "lock" to "🔒", "key" to "🔑", "cat" to "🐱",
            "dog" to "🐶", "music" to "🎵", "camera" to "📷", "phone" to "📱",
            "think" to "🤔", "cool" to "😎", "sad" to "😢", "cry" to "😭",
            "angry" to "😠", "wave" to "👋", "bye" to "👋", "hi" to "👋",
            "hello" to "👋", "sleep" to "😴", "eyes" to "👀", "100" to "💯"
        )

        private val CORE_WORDS = listOf(
            "the", "be", "to", "of", "and", "a", "in", "that", "have", "I",
            "it", "for", "not", "on", "with", "he", "as", "you", "do", "at",
            "this", "but", "his", "by", "from", "they", "we", "say", "her", "she",
            "or", "an", "will", "my", "one", "all", "would", "there", "their", "what",
            "so", "up", "out", "if", "about", "who", "get", "which", "go", "me",
            "when", "make", "can", "like", "time", "no", "just", "him", "know", "take",
            "people", "into", "year", "your", "good", "some", "could", "them", "see", "other",
            "than", "then", "now", "look", "only", "come", "its", "over", "think", "also",
            "back", "after", "use", "two", "how", "our", "work", "first", "well", "way",
            "even", "new", "want", "because", "any", "these", "give", "day", "most", "us",
            "hello", "hey", "hi", "thanks", "thank", "please", "sorry", "yes", "yeah", "okay",
            "great", "awesome", "nice", "cool", "love", "happy", "morning", "evening", "night",
            "today", "tomorrow", "yesterday", "week", "month", "hours", "minutes", "seconds",
            "keyboard", "android", "privacy", "offline", "open", "source", "custom", "theme",
            "layout", "language", "english", "german", "french", "spanish", "arabic", "bengali",
            "clipboard", "emoji", "dictionary", "settings", "backup", "restore", "typing",
            "message", "email", "address", "number", "password", "search", "update", "version",
            "I'm", "I've", "I'll", "I'd", "don't", "can't", "won't", "didn't", "isn't", "aren't",
            "you're", "we're", "they're", "it's", "that's", "what's", "here's", "there's",
            "let's", "could've", "should've", "would've", "haven't", "hasn't", "wasn't",
            "always", "never", "sometimes", "often", "usually", "already", "still", "yet",
            "maybe", "probably", "definitely", "actually", "really", "very", "quite", "pretty",
            "before", "during", "while", "until", "since", "between", "through", "around",
            "every", "everything", "everyone", "something", "someone", "nothing", "anything",
            "where", "why", "which", "whose", "whom", "whether", "though", "although",
            "friend", "family", "home", "office", "school", "project", "meeting", "schedule",
            "phone", "screen", "window", "button", "switch", "color", "border", "height",
            "space", "enter", "delete", "shift", "symbol", "accent", "popup", "sound", "vibrate",
            "check", "create", "build", "design", "share", "save", "export", "import", "edit",
            "clear", "paste", "copy", "select", "undo", "redo", "cursor", "split", "handed",
            "fast", "smooth", "clean", "modern", "secure", "local", "personal", "word", "text",
            "write", "read", "send", "reply", "call", "wait", "soon", "later", "right", "left",
            "please", "welcome", "ready", "done", "finish", "start", "continue", "cancel", "close",
            "hallo", "danke", "bitte", "guten", "morgen", "bonjour", "merci", "salut", "hola", "gracias"
        )
    }
}
