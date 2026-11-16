package com.example.heliboard.voice

import android.Manifest
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.provider.Settings
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.view.inputmethod.InputMethodManager
import androidx.core.content.ContextCompat

enum class VoiceSessionStatus {
    IDLE,
    LISTENING,
    PROCESSING,
    PERMISSION_REQUIRED,
    UNAVAILABLE,
    ERROR
}

enum class VoiceErrorType {
    NO_MATCH,
    AUDIO,
    BUSY,
    PERMISSION_DENIED,
    ENGINE_UNAVAILABLE,
    LANGUAGE_UNSUPPORTED
}

data class VoiceUiState(
    val status: VoiceSessionStatus = VoiceSessionStatus.IDLE,
    val activeLocaleTag: String = "",
    val partialText: String = "",
    val message: String? = null,
    val canUseSystemVoiceInput: Boolean = false,
    val canOpenOfflineVoiceSettings: Boolean = false,
    val sessionId: Long = 0L,
    val isContinuous: Boolean = false,
    val isOfflinePreferred: Boolean = false
)

interface VoiceRecognitionListener {
    fun onReady(sessionId: Long)
    fun onSpeechStarted(sessionId: Long) {}
    fun onPartial(sessionId: Long, text: String)
    fun onFinal(sessionId: Long, text: String)
    fun onError(sessionId: Long, type: VoiceErrorType, detail: String? = null)
}

interface VoiceDictationBackend {
    fun hasAudioPermission(): Boolean
    fun isLocalRecognitionAvailable(localeTag: String): Boolean
    fun isSystemVoiceInputAvailable(): Boolean
    fun isOnDeviceOfflineAvailable(): Boolean = false
    fun openOfflineVoiceSettings(): Boolean = false
    fun startListening(
        sessionId: Long,
        localeTag: String,
        fallbackLocaleTag: String?,
        listener: VoiceRecognitionListener,
        preferOffline: Boolean = false
    )

    fun stopListening(sessionId: Long)
    fun cancel(sessionId: Long)
    fun launchSystemVoiceInput(): Boolean
    fun destroy()
}

internal interface PlatformSpeechRecognizer {
    fun setRecognitionListener(listener: RecognitionListener)
    fun startListening(recognizerIntent: Intent)
    fun stopListening()
    fun cancel()
    fun destroy()
    fun triggerModelDownload(recognizerIntent: Intent)
}

private class RealPlatformSpeechRecognizer(
    private val delegate: SpeechRecognizer
) : PlatformSpeechRecognizer {
    override fun setRecognitionListener(listener: RecognitionListener) {
        delegate.setRecognitionListener(listener)
    }

    override fun startListening(recognizerIntent: Intent) {
        delegate.startListening(recognizerIntent)
    }

    override fun stopListening() {
        delegate.stopListening()
    }

    override fun cancel() {
        delegate.cancel()
    }

    override fun destroy() {
        delegate.destroy()
    }

    override fun triggerModelDownload(recognizerIntent: Intent) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            delegate.triggerModelDownload(recognizerIntent)
        }
    }
}

class AndroidVoiceDictationBackend(
    private val context: Context,
    private val systemVoiceLauncher: (() -> Boolean)? = null
) : VoiceDictationBackend {

    internal data class RecognitionAttempt(
        val localeTag: String,
        val useOnDeviceRecognizer: Boolean,
        val forceOfflineExtra: Boolean = useOnDeviceRecognizer
    )

    internal var testRecognizerFactory: ((useOnDevice: Boolean) -> Pair<PlatformSpeechRecognizer, Boolean>?)? = null
    internal var testCooldownMs: Long = 90L
    internal var testStandardAvailableOverride: Boolean? = null
    internal var testOnDeviceAvailableOverride: Boolean? = null

    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: PlatformSpeechRecognizer? = null
    private var recognizerIsOnDevice: Boolean = false
    private var activeSessionId: Long = -1L
    private var pendingAttempts: ArrayDeque<RecognitionAttempt> = ArrayDeque()
    private var activeListener: VoiceRecognitionListener? = null
    private var activeRequestKey: Pair<String, Boolean>? = null
    private val workingAttempts = mutableMapOf<Pair<String, Boolean>, RecognitionAttempt>()
    private val modelDownloadAttemptedLocales = mutableSetOf<String>()
    private var busyRetryCountForAttempt: Int = 0
    private var scheduledRetryRunnable: Runnable? = null
    private var lastActivityEndedAtMs: Long = 0L

    override fun hasAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun isLocalRecognitionAvailable(localeTag: String): Boolean {
        val standardAvailable = testStandardAvailableOverride ?: runCatching {
            SpeechRecognizer.isRecognitionAvailable(context)
        }.getOrDefault(false)
        val onDeviceAvailable = isOnDeviceOfflineAvailable()
        return standardAvailable || onDeviceAvailable
    }

    override fun isSystemVoiceInputAvailable(): Boolean {
        val hasVoiceIme = runCatching {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            imm?.enabledInputMethodList.orEmpty().any { info ->
                if (info.packageName == context.packageName) return@any false
                (0 until info.subtypeCount).any { idx ->
                    info.getSubtypeAt(idx).mode.equals("voice", ignoreCase = true)
                }
            }
        }.getOrDefault(false)

        if (hasVoiceIme) return true

        val hasSpeechActivity = runCatching {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH)
            context.packageManager.queryIntentActivities(intent, 0).isNotEmpty()
        }.getOrDefault(false)

        return hasSpeechActivity
    }

    override fun isOnDeviceOfflineAvailable(): Boolean {
        testOnDeviceAvailableOverride?.let { return it }
        return Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)
    }

    override fun openOfflineVoiceSettings(): Boolean {
        val candidates = listOf(
            Intent("com.android.settings.VOICE_INPUT_SETTINGS"),
            Intent(Settings.ACTION_INPUT_METHOD_SETTINGS),
            Intent("com.google.android.googlequicksearchbox.action.VOICE_SEARCH_SETTINGS"),
            Intent(Settings.ACTION_SETTINGS)
        )
        for (candidate in candidates) {
            candidate.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            val resolved = runCatching {
                context.packageManager.resolveActivity(candidate, 0) != null
            }.getOrDefault(false)
            if (resolved) {
                if (runCatching { context.startActivity(candidate) }.isSuccess) {
                    return true
                }
            }
        }
        return false
    }

    override fun startListening(
        sessionId: Long,
        localeTag: String,
        fallbackLocaleTag: String?,
        listener: VoiceRecognitionListener,
        preferOffline: Boolean
    ) {
        runOnMain {
            clearScheduledRetry()
            activeSessionId = sessionId
            activeListener = listener
            val key = localeTag to preferOffline
            activeRequestKey = key

            val attempts = buildRecognitionAttempts(
                localeTag = localeTag,
                fallbackLocaleTag = fallbackLocaleTag,
                preferOffline = preferOffline
            ).toMutableList()

            val cached = workingAttempts[key]
            if (cached != null) {
                attempts.remove(cached)
                attempts.add(0, cached)
            }

            pendingAttempts = ArrayDeque(attempts)
            busyRetryCountForAttempt = 0

            val elapsedSinceLast = SystemClock.uptimeMillis() - lastActivityEndedAtMs
            if (lastActivityEndedAtMs > 0L && elapsedSinceLast in 0 until testCooldownMs) {
                val delayMs = (testCooldownMs - elapsedSinceLast).coerceAtLeast(1L)
                scheduleDelayed(sessionId, delayMs) {
                    startNextAttempt(sessionId, listener)
                }
            } else {
                startNextAttempt(sessionId, listener)
            }
        }
    }

    internal fun buildRecognitionAttempts(
        localeTag: String,
        fallbackLocaleTag: String?,
        standardAvailable: Boolean = testStandardAvailableOverride ?: runCatching {
            SpeechRecognizer.isRecognitionAvailable(context)
        }.getOrDefault(false),
        onDeviceAvailable: Boolean = isOnDeviceOfflineAvailable(),
        preferOffline: Boolean = false
    ): List<RecognitionAttempt> {
        val locales = linkedSetOf<String>()
        if (localeTag.isNotBlank()) locales += localeTag
        if (localeTag.startsWith("ar", ignoreCase = true)) {
            locales += "ar-EG"
            locales += "ar-SA"
        }
        if (!fallbackLocaleTag.isNullOrBlank()) locales += fallbackLocaleTag
        if (localeTag.startsWith("ar", ignoreCase = true)) {
            locales += "ar"
        }

        val attempts = mutableListOf<RecognitionAttempt>()
        if (preferOffline) {
            if (onDeviceAvailable) {
                locales.forEach { loc ->
                    attempts += RecognitionAttempt(
                        localeTag = loc,
                        useOnDeviceRecognizer = true,
                        forceOfflineExtra = true
                    )
                }
            }
            if (standardAvailable || !onDeviceAvailable) {
                locales.forEach { loc ->
                    attempts += RecognitionAttempt(
                        localeTag = loc,
                        useOnDeviceRecognizer = false,
                        forceOfflineExtra = true
                    )
                }
                if (standardAvailable) {
                    locales.forEach { loc ->
                        attempts += RecognitionAttempt(
                            localeTag = loc,
                            useOnDeviceRecognizer = false,
                            forceOfflineExtra = false
                        )
                    }
                }
            }
        } else {
            // Standard SpeechRecognizer first (supports full Arabic + online/hybrid + cached models)
            if (standardAvailable || !onDeviceAvailable) {
                locales.forEach { loc ->
                    attempts += RecognitionAttempt(
                        localeTag = loc,
                        useOnDeviceRecognizer = false,
                        forceOfflineExtra = false
                    )
                }
            }
            if (onDeviceAvailable) {
                locales.forEach { loc ->
                    attempts += RecognitionAttempt(
                        localeTag = loc,
                        useOnDeviceRecognizer = true,
                        forceOfflineExtra = true
                    )
                }
            }
        }
        return attempts
    }

    internal fun buildRecognitionIntent(attempt: RecognitionAttempt): Intent {
        return Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, attempt.localeTag)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, attempt.localeTag)
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            if (attempt.forceOfflineExtra) {
                putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            }
            // Phrase-level silence so Android emits onResults cleanly for continuous multi-segment dictation
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1800L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1300L)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }
    }

    private fun startNextAttempt(
        sessionId: Long,
        listener: VoiceRecognitionListener
    ) {
        if (activeSessionId != sessionId) return
        val attempt = pendingAttempts.removeFirstOrNull()
        if (attempt == null) {
            listener.onError(sessionId, VoiceErrorType.ENGINE_UNAVAILABLE)
            return
        }
        busyRetryCountForAttempt = 0
        launchAttempt(sessionId, attempt, listener)
    }

    private fun launchAttempt(
        sessionId: Long,
        attempt: RecognitionAttempt,
        listener: VoiceRecognitionListener
    ) {
        if (activeSessionId != sessionId) return

        // Reuse the active recognizer instance if the engine mode matches; otherwise tear down cleanly first
        val existing = recognizer
        if (existing != null && recognizerIsOnDevice != attempt.useOnDeviceRecognizer) {
            destroyInternal()
        }

        val instance = recognizer ?: createRecognizer(attempt.useOnDeviceRecognizer)?.let { (rec, actualOnDevice) ->
            recognizer = rec
            recognizerIsOnDevice = actualOnDevice
            rec
        }

        if (instance == null) {
            if (pendingAttempts.isNotEmpty()) {
                startNextAttempt(sessionId, listener)
            } else {
                listener.onError(sessionId, VoiceErrorType.ENGINE_UNAVAILABLE)
            }
            return
        }

        var attemptGotReady = false

        instance.setRecognitionListener(object : RecognitionListener {
            override fun onReadyForSpeech(params: Bundle?) {
                if (activeSessionId != sessionId) return
                attemptGotReady = true
                listener.onReady(sessionId)
            }

            override fun onBeginningOfSpeech() {
                if (activeSessionId != sessionId) return
                listener.onSpeechStarted(sessionId)
            }

            override fun onRmsChanged(rmsdB: Float) = Unit

            override fun onBufferReceived(buffer: ByteArray?) = Unit

            override fun onEndOfSpeech() = Unit

            override fun onError(error: Int) {
                if (activeSessionId != sessionId) return
                lastActivityEndedAtMs = SystemClock.uptimeMillis()

                if (error == SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS) {
                    destroyInternal()
                    listener.onError(sessionId, VoiceErrorType.PERMISSION_DENIED)
                    return
                }

                // Trigger offline model download once in background if Android 13+ reports missing offline pack
                if (attempt.useOnDeviceRecognizer &&
                    (error == SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE ||
                        error == SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED) &&
                    modelDownloadAttemptedLocales.add(attempt.localeTag)
                ) {
                    runCatching {
                        instance.triggerModelDownload(buildRecognitionIntent(attempt))
                    }
                }

                // Clear cached working attempt if it suddenly failed so future turns try fallbacks cleanly
                activeRequestKey?.let { key ->
                    if (workingAttempts[key] == attempt) {
                        workingAttempts.remove(key)
                    }
                }

                // Transient recognizer-busy backoff before giving up on the same attempt
                if (error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY &&
                    !attemptGotReady &&
                    busyRetryCountForAttempt == 0
                ) {
                    busyRetryCountForAttempt++
                    destroyInternal()
                    scheduleDelayed(sessionId, 200L) {
                        launchAttempt(sessionId, attempt, listener)
                    }
                    return
                }

                val isNaturalSilencePause = attemptGotReady &&
                    (error == SpeechRecognizer.ERROR_NO_MATCH || error == SpeechRecognizer.ERROR_SPEECH_TIMEOUT)

                if (!isNaturalSilencePause && pendingAttempts.isNotEmpty()) {
                    // Destroy recognizer cleanly if switching engine mode or if error left it unhealthy;
                    // yield briefly on MainHandler so binder / AudioRecord teardown finishes before next start.
                    val nextAttempt = pendingAttempts.firstOrNull()
                    if (nextAttempt == null || nextAttempt.useOnDeviceRecognizer != recognizerIsOnDevice ||
                        error == SpeechRecognizer.ERROR_SERVER_DISCONNECTED ||
                        error == SpeechRecognizer.ERROR_CLIENT ||
                        error == SpeechRecognizer.ERROR_AUDIO
                    ) {
                        destroyInternal()
                    } else {
                        runCatching { instance.cancel() }
                    }
                    scheduleDelayed(sessionId, testCooldownMs) {
                        startNextAttempt(sessionId, listener)
                    }
                    return
                }

                val mapped = when (error) {
                    SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED,
                    SpeechRecognizer.ERROR_LANGUAGE_UNAVAILABLE -> VoiceErrorType.LANGUAGE_UNSUPPORTED
                    SpeechRecognizer.ERROR_NO_MATCH,
                    SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> VoiceErrorType.NO_MATCH
                    SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> VoiceErrorType.BUSY
                    SpeechRecognizer.ERROR_AUDIO -> VoiceErrorType.AUDIO
                    else -> VoiceErrorType.ENGINE_UNAVAILABLE
                }
                if (error != SpeechRecognizer.ERROR_NO_MATCH && error != SpeechRecognizer.ERROR_SPEECH_TIMEOUT) {
                    destroyInternal()
                }
                listener.onError(sessionId, mapped)
            }

            override fun onResults(results: Bundle?) {
                if (activeSessionId != sessionId) return
                lastActivityEndedAtMs = SystemClock.uptimeMillis()
                activeRequestKey?.let { workingAttempts[it] = attempt }
                val text = results
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                if (text.isNotEmpty()) {
                    listener.onFinal(sessionId, text)
                } else {
                    listener.onError(sessionId, VoiceErrorType.NO_MATCH)
                }
            }

            override fun onPartialResults(partialResults: Bundle?) {
                if (activeSessionId != sessionId) return
                activeRequestKey?.let { workingAttempts[it] = attempt }
                val text = partialResults
                    ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                    ?.firstOrNull()
                    ?.trim()
                    .orEmpty()
                if (text.isNotEmpty()) {
                    listener.onPartial(sessionId, text)
                }
            }

            override fun onEvent(eventType: Int, params: Bundle?) = Unit
        })

        val intent = buildRecognitionIntent(attempt)
        runCatching {
            instance.startListening(intent)
        }.onFailure {
            lastActivityEndedAtMs = SystemClock.uptimeMillis()
            destroyInternal()
            if (pendingAttempts.isNotEmpty()) {
                scheduleDelayed(sessionId, testCooldownMs) {
                    startNextAttempt(sessionId, listener)
                }
            } else {
                listener.onError(sessionId, VoiceErrorType.ENGINE_UNAVAILABLE, it.localizedMessage)
            }
        }
    }

    override fun stopListening(sessionId: Long) {
        runOnMain {
            if (activeSessionId != sessionId) return@runOnMain
            runCatching { recognizer?.stopListening() }
        }
    }

    override fun cancel(sessionId: Long) {
        runOnMain {
            if (activeSessionId == sessionId || sessionId < 0L) {
                clearScheduledRetry()
                activeSessionId = -1L
                pendingAttempts.clear()
                lastActivityEndedAtMs = SystemClock.uptimeMillis()
                destroyInternal()
            }
        }
    }

    override fun launchSystemVoiceInput(): Boolean {
        if (systemVoiceLauncher?.invoke() == true) {
            return true
        }
        return runCatching {
            val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE) as? InputMethodManager
            if (imm != null) {
                val hasOtherVoiceIme = imm.enabledInputMethodList.any { info ->
                    info.packageName != context.packageName &&
                        (0 until info.subtypeCount).any { idx ->
                            info.getSubtypeAt(idx).mode.equals("voice", ignoreCase = true)
                        }
                }
                if (hasOtherVoiceIme) {
                    imm.showInputMethodPicker()
                    return@runCatching true
                }
            }
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            if (context.packageManager.resolveActivity(intent, 0) != null) {
                context.startActivity(intent)
                true
            } else {
                false
            }
        }.getOrDefault(false)
    }

    override fun destroy() {
        runOnMain {
            clearScheduledRetry()
            activeSessionId = -1L
            pendingAttempts.clear()
            destroyInternal()
        }
    }

    private fun createRecognizer(useOnDevice: Boolean): Pair<PlatformSpeechRecognizer, Boolean>? {
        testRecognizerFactory?.let { factory ->
            return factory(useOnDevice)
        }
        return runCatching {
            if (useOnDevice && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            ) {
                RealPlatformSpeechRecognizer(SpeechRecognizer.createOnDeviceSpeechRecognizer(context)) to true
            } else if (SpeechRecognizer.isRecognitionAvailable(context)) {
                RealPlatformSpeechRecognizer(SpeechRecognizer.createSpeechRecognizer(context)) to false
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
                SpeechRecognizer.isOnDeviceRecognitionAvailable(context)
            ) {
                RealPlatformSpeechRecognizer(SpeechRecognizer.createOnDeviceSpeechRecognizer(context)) to true
            } else {
                null
            }
        }.getOrNull()
    }

    private fun scheduleDelayed(sessionId: Long, delayMs: Long, action: () -> Unit) {
        clearScheduledRetry()
        val runnable = Runnable {
            scheduledRetryRunnable = null
            if (activeSessionId == sessionId) {
                action()
            }
        }
        scheduledRetryRunnable = runnable
        mainHandler.postDelayed(runnable, delayMs)
    }

    private fun clearScheduledRetry() {
        scheduledRetryRunnable?.let { mainHandler.removeCallbacks(it) }
        scheduledRetryRunnable = null
    }

    private fun destroyInternal() {
        val current = recognizer ?: return
        recognizer = null
        recognizerIsOnDevice = false
        runCatching { current.cancel() }
        runCatching { current.destroy() }
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    companion object {
        fun resolveVoiceLocaleTags(
            writingLocaleTag: String,
            arabicDialect: String = "ar-EG"
        ): Pair<String, String?> {
            if (writingLocaleTag.startsWith("ar", ignoreCase = true)) {
                val preferred = arabicDialect.ifBlank { "ar-EG" }
                val fallback = if (preferred.equals("ar", ignoreCase = true)) null else "ar"
                return preferred to fallback
            }
            if (writingLocaleTag.isBlank()) {
                return "en-US" to "en"
            }
            val base = writingLocaleTag.substringBefore("-")
            val fallback = if (base.isNotEmpty() && !base.equals(writingLocaleTag, ignoreCase = true)) base else null
            return writingLocaleTag to fallback
        }
    }
}
