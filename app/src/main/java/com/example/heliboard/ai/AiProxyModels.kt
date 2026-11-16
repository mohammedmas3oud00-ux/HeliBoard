package com.example.heliboard.ai

import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.put
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URI
import java.net.URL
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

enum class AiWritingOperation(val apiValue: String) {
    PROOFREAD("proofread"),
    REWRITE("rewrite"),
    COMPOSE_MESSAGE("compose_message"),
    COMPOSE_EMAIL("compose_email")
}

enum class AiTone(val apiValue: String) {
    SAME("same"),
    FORMAL("formal"),
    FRIENDLY("friendly"),
    CONCISE("concise")
}

enum class AiLanguageOption(val apiValue: String) {
    AUTO("auto"),
    AR("ar"),
    EN("en")
}

data class AiProxyConfig(
    val baseUrl: String,
    val authToken: String = "",
    val directGeminiApiKey: String = ""
)

enum class AiConnectionMode {
    USER_PROXY,
    BUILD_PROXY,
    AUTO_GEMINI_KEY,
    UNCONFIGURED
}

object AiConfigResolver {
    private val placeholderSentinels = setOf(
        "GEMINI_API_KEY_DEFAULT_VALUE",
        "REPLACE-WITH-YOUR-GEMINI-API-KEY",
        "REPLACE-WITH-CLIENT-BEARER-TOKEN",
        "YOUR_API_KEY_HERE"
    )

    fun isPlaceholderSecret(raw: String?): Boolean {
        val clean = raw?.trim().orEmpty()
        if (clean.isEmpty()) return true
        return clean.uppercase() in placeholderSentinels
    }

    fun resolveConnectionMode(
        settingsProxyUrl: String,
        buildProxyUrl: String,
        buildGeminiApiKey: String
    ): AiConnectionMode = when {
        settingsProxyUrl.trim().isNotEmpty() -> AiConnectionMode.USER_PROXY
        buildProxyUrl.trim().isNotEmpty() -> AiConnectionMode.BUILD_PROXY
        !isPlaceholderSecret(buildGeminiApiKey) -> AiConnectionMode.AUTO_GEMINI_KEY
        else -> AiConnectionMode.UNCONFIGURED
    }

    fun resolveConfig(
        settingsProxyUrl: String,
        settingsAuthToken: String,
        buildProxyUrl: String,
        buildProxyToken: String,
        buildGeminiApiKey: String
    ): AiProxyConfig? {
        val effectiveUrl = settingsProxyUrl.trim().ifEmpty { buildProxyUrl.trim() }
        if (effectiveUrl.isNotEmpty()) {
            val effectiveToken = settingsAuthToken.trim().ifEmpty { buildProxyToken.trim() }
            return AiProxyConfig(baseUrl = effectiveUrl, authToken = effectiveToken)
        }
        val cleanKey = buildGeminiApiKey.trim().takeUnless { isPlaceholderSecret(it) }
        if (!cleanKey.isNullOrEmpty()) {
            return AiProxyConfig(baseUrl = "", authToken = "", directGeminiApiKey = cleanKey)
        }
        return null
    }
}

data class AiWritingRequest(
    val operation: AiWritingOperation,
    val tone: AiTone,
    val targetLanguage: AiLanguageOption,
    val text: String
)

data class AiWritingResult(
    val operation: AiWritingOperation,
    val text: String = "",
    val subject: String = "",
    val body: String = "",
    val detectedLanguage: String? = null
)

data class AiTranslateRequest(
    val sourceLanguage: AiLanguageOption,
    val targetLanguage: AiLanguageOption,
    val text: String
)

data class AiTranslateResult(
    val translatedText: String,
    val detectedSourceLanguage: String? = null
)

enum class AiProxyErrorType {
    EMPTY_INPUT,
    UNCONFIGURED,
    INVALID_URL,
    OFFLINE,
    UNAUTHORIZED,
    RATE_LIMITED,
    TIMEOUT,
    NETWORK,
    SERVER_ERROR
}

class AiProxyException(
    val type: AiProxyErrorType,
    val statusCode: Int? = null,
    override val message: String? = null
) : Exception(message ?: type.name)

interface AiProxyClient {
    suspend fun processWriting(config: AiProxyConfig, request: AiWritingRequest): AiWritingResult
    suspend fun translate(config: AiProxyConfig, request: AiTranslateRequest): AiTranslateResult
}

@Serializable
private data class ProxyWritingPayload(
    val task: String = "writing",
    val action: String,
    val tone: String,
    val targetLanguage: String,
    val text: String
)

@Serializable
private data class ProxyTranslatePayload(
    val task: String = "translate",
    val sourceLanguage: String,
    val targetLanguage: String,
    val text: String
)

@Serializable
private data class ProxyErrorEnvelope(
    val error: ProxyErrorBody? = null
)

@Serializable
private data class ProxyErrorBody(
    val code: String? = null,
    val message: String? = null
)

@Serializable
private data class ProxyResponsePayload(
    val task: String? = null,
    val action: String? = null,
    val text: String? = null,
    val subject: String? = null,
    val body: String? = null,
    val detectedLanguage: String? = null,
    val translatedText: String? = null,
    val detectedSourceLanguage: String? = null
)

class HttpAiProxyClient(
    private val context: Context? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val connectTimeoutMs: Int = 10_000,
    private val readTimeoutMs: Int = 25_000,
    private val directGeminiEndpoint: String = DEFAULT_GEMINI_ENDPOINT
) : AiProxyClient {

    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    private val executor = Executors.newCachedThreadPool()

    override suspend fun processWriting(
        config: AiProxyConfig,
        request: AiWritingRequest
    ): AiWritingResult {
        val cleanText = request.text.trim()
        if (cleanText.isEmpty()) throw AiProxyException(AiProxyErrorType.EMPTY_INPUT)

        val responseJsonText = if (config.baseUrl.trim().isNotEmpty()) {
            val payload = json.encodeToString(
                ProxyWritingPayload(
                    action = request.operation.apiValue,
                    tone = request.tone.apiValue,
                    targetLanguage = request.targetLanguage.apiValue,
                    text = cleanText
                )
            )
            executeProxyPost(config, payload)
        } else {
            val apiKey = config.directGeminiApiKey.trim()
            if (apiKey.isEmpty()) throw AiProxyException(AiProxyErrorType.UNCONFIGURED)
            val geminiPayload = buildDirectWritingGeminiBody(request, cleanText)
            executeDirectGeminiPost(apiKey, geminiPayload)
        }

        val parsed = runCatching { json.decodeFromString<ProxyResponsePayload>(responseJsonText) }
            .getOrElse { throw AiProxyException(AiProxyErrorType.SERVER_ERROR, message = "Malformed JSON response") }

        return if (request.operation == AiWritingOperation.COMPOSE_EMAIL) {
            val subject = parsed.subject?.trim().orEmpty()
            val body = parsed.body?.trim().orEmpty()
            if (subject.isEmpty() && body.isEmpty()) {
                throw AiProxyException(AiProxyErrorType.SERVER_ERROR, message = "Empty email response")
            }
            AiWritingResult(
                operation = request.operation,
                subject = subject,
                body = body,
                text = listOf(subject, body).filter { it.isNotEmpty() }.joinToString("\n\n"),
                detectedLanguage = parsed.detectedLanguage
            )
        } else {
            val resultText = parsed.text?.trim().orEmpty()
            if (resultText.isEmpty()) {
                throw AiProxyException(AiProxyErrorType.SERVER_ERROR, message = "Empty text response")
            }
            AiWritingResult(
                operation = request.operation,
                text = resultText,
                detectedLanguage = parsed.detectedLanguage
            )
        }
    }

    override suspend fun translate(
        config: AiProxyConfig,
        request: AiTranslateRequest
    ): AiTranslateResult {
        val cleanText = request.text.trim()
        if (cleanText.isEmpty()) throw AiProxyException(AiProxyErrorType.EMPTY_INPUT)

        val responseJsonText = if (config.baseUrl.trim().isNotEmpty()) {
            val payload = json.encodeToString(
                ProxyTranslatePayload(
                    sourceLanguage = request.sourceLanguage.apiValue,
                    targetLanguage = request.targetLanguage.apiValue,
                    text = cleanText
                )
            )
            executeProxyPost(config, payload)
        } else {
            val apiKey = config.directGeminiApiKey.trim()
            if (apiKey.isEmpty()) throw AiProxyException(AiProxyErrorType.UNCONFIGURED)
            val geminiPayload = buildDirectTranslateGeminiBody(request, cleanText)
            executeDirectGeminiPost(apiKey, geminiPayload)
        }

        val parsed = runCatching { json.decodeFromString<ProxyResponsePayload>(responseJsonText) }
            .getOrElse { throw AiProxyException(AiProxyErrorType.SERVER_ERROR, message = "Malformed JSON response") }

        val translated = parsed.translatedText?.trim().orEmpty()
        if (translated.isEmpty()) {
            throw AiProxyException(AiProxyErrorType.SERVER_ERROR, message = "Empty translation response")
        }
        return AiTranslateResult(
            translatedText = translated,
            detectedSourceLanguage = parsed.detectedSourceLanguage
        )
    }

    private suspend fun executeProxyPost(config: AiProxyConfig, bodyJson: String): String {
        val endpointUrl = resolveProcessEndpoint(config.baseUrl)
        val headers = buildMap {
            put("Content-Type", "application/json; charset=utf-8")
            put("Accept", "application/json")
            val token = config.authToken.trim()
            if (token.isNotEmpty()) {
                put("Authorization", "Bearer $token")
            }
        }
        return executeHttpPost(endpointUrl, bodyJson, headers)
    }

    private suspend fun executeDirectGeminiPost(apiKey: String, bodyJson: String): String {
        val endpointUrl = runCatching { URL(directGeminiEndpoint) }.getOrNull()
            ?: throw AiProxyException(AiProxyErrorType.INVALID_URL)
        val headers = mapOf(
            "Content-Type" to "application/json; charset=utf-8",
            "Accept" to "application/json",
            "x-goog-api-key" to apiKey
        )
        val rawGeminiResponse = executeHttpPost(endpointUrl, bodyJson, headers)
        return extractGeminiCandidateText(rawGeminiResponse)
    }

    private fun extractGeminiCandidateText(rawResponse: String): String {
        val root = runCatching { json.parseToJsonElement(rawResponse).jsonObject }.getOrNull()
            ?: throw AiProxyException(AiProxyErrorType.SERVER_ERROR, message = "Malformed upstream JSON")
        val candidates = root["candidates"] as? JsonArray
        val firstCandidate = candidates?.firstOrNull() as? JsonObject
        val content = firstCandidate?.get("content") as? JsonObject
        val parts = content?.get("parts") as? JsonArray
        val firstPart = parts?.firstOrNull() as? JsonObject
        val text = firstPart?.get("text")?.jsonPrimitive?.content?.trim().orEmpty()
        if (text.isEmpty()) {
            throw AiProxyException(AiProxyErrorType.SERVER_ERROR, message = "Empty model response")
        }
        return text
    }

    private suspend fun executeHttpPost(
        endpointUrl: URL,
        bodyJson: String,
        headers: Map<String, String>
    ): String {
        val host = endpointUrl.host?.lowercase().orEmpty()
        val isLoopbackHost = host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2"
        if (!isLoopbackHost && isExplicitlyOffline()) {
            throw AiProxyException(AiProxyErrorType.OFFLINE)
        }

        return withContext(ioDispatcher) {
            suspendCancellableCoroutine { cont ->
                var connection: HttpURLConnection? = null
                val future = executor.submit {
                    try {
                        val conn = (endpointUrl.openConnection() as HttpURLConnection).apply {
                            connection = this
                            requestMethod = "POST"
                            connectTimeout = connectTimeoutMs
                            readTimeout = readTimeoutMs
                            doInput = true
                            doOutput = true
                            headers.forEach { (k, v) -> setRequestProperty(k, v) }
                        }
                        conn.outputStream.use { os ->
                            os.write(bodyJson.toByteArray(Charsets.UTF_8))
                        }

                        val status = conn.responseCode
                        val stream = if (status in 200..299) conn.inputStream else conn.errorStream
                        val body = stream?.bufferedReader(Charsets.UTF_8)?.use { it.readText() }.orEmpty()

                        if (status in 200..299) {
                            if (cont.isActive) cont.resume(body)
                        } else {
                            val errMsg = runCatching {
                                json.decodeFromString<ProxyErrorEnvelope>(body).error?.message
                            }.getOrNull() ?: "HTTP $status"
                            val errorType = when (status) {
                                401, 403 -> AiProxyErrorType.UNAUTHORIZED
                                429 -> AiProxyErrorType.RATE_LIMITED
                                504 -> AiProxyErrorType.TIMEOUT
                                503 -> AiProxyErrorType.SERVER_ERROR
                                else -> AiProxyErrorType.SERVER_ERROR
                            }
                            if (cont.isActive) {
                                cont.resumeWithException(AiProxyException(errorType, statusCode = status, message = errMsg))
                            }
                        }
                    } catch (e: SocketTimeoutException) {
                        if (cont.isActive) {
                            cont.resumeWithException(AiProxyException(AiProxyErrorType.TIMEOUT, message = e.localizedMessage))
                        }
                    } catch (e: IOException) {
                        if (cont.isActive) {
                            cont.resumeWithException(AiProxyException(AiProxyErrorType.NETWORK, message = e.localizedMessage))
                        }
                    } catch (e: Throwable) {
                        if (cont.isActive) {
                            cont.resumeWithException(
                                if (e is AiProxyException) e
                                else AiProxyException(AiProxyErrorType.SERVER_ERROR, message = e.localizedMessage)
                            )
                        }
                    } finally {
                        runCatching { connection?.disconnect() }
                    }
                }

                cont.invokeOnCancellation {
                    future.cancel(true)
                    runCatching { connection?.disconnect() }
                }
            }
        }
    }

    private fun buildDirectWritingGeminiBody(request: AiWritingRequest, cleanText: String): String {
        val systemInstruction = when (request.operation) {
            AiWritingOperation.PROOFREAD ->
                "You are a careful bilingual Arabic and English proofreader. " +
                    "Fix spelling, grammar, and punctuation errors while strictly preserving the original meaning, language, tone, and register. " +
                    "If the text is written in Egyptian Arabic dialect (العامية المصرية), keep it in natural Egyptian Arabic without converting it to Modern Standard Arabic. " +
                    "Preserve all names, numbers, URLs, emails, and line breaks. Return JSON matching the schema."
            AiWritingOperation.REWRITE -> {
                val toneRule = when (request.tone) {
                    AiTone.SAME -> "Keep the exact same tone, register, and dialect (including Egyptian Arabic if used), improving clarity only."
                    AiTone.FORMAL -> "Use a clear, polite, formal register appropriate for work or official communication."
                    AiTone.FRIENDLY -> "Use a warm, polite, friendly tone."
                    AiTone.CONCISE -> "Make the text tighter, clearer, and more concise while preserving all meaning."
                }
                "Rewrite the user's text more clearly while preserving all proper names, numbers, dates, URLs, and core meaning. $toneRule " +
                    "Keep the original language unless otherwise clear from the input. Return JSON matching the schema."
            }
            AiWritingOperation.COMPOSE_MESSAGE -> {
                val langRule = when (request.targetLanguage) {
                    AiLanguageOption.AR -> "Write the message draft in natural Arabic."
                    AiLanguageOption.EN -> "Write the message draft in natural English."
                    AiLanguageOption.AUTO -> "Write the message draft in the same language as the user notes."
                }
                "Turn the user's idea or bullet points into a ready-to-send chat/message draft. $langRule " +
                    "Use ONLY facts provided by the user. If any specific detail (such as a name, time, or place) is missing and needed, " +
                    "use a clear bracketed placeholder like [الاسم] or [Name]. Return JSON matching the schema."
            }
            AiWritingOperation.COMPOSE_EMAIL -> {
                val langRule = when (request.targetLanguage) {
                    AiLanguageOption.AR -> "Write both subject and email body in natural Arabic."
                    AiLanguageOption.EN -> "Write both subject and email body in natural English."
                    AiLanguageOption.AUTO -> "Write both subject and email body in the same language as the user notes."
                }
                val toneRule = when (request.tone) {
                    AiTone.SAME -> "Use a clear professional tone."
                    AiTone.FORMAL -> "Use a formal professional email tone."
                    AiTone.FRIENDLY -> "Use a warm, friendly yet polite email tone."
                    AiTone.CONCISE -> "Keep the email concise and direct."
                }
                "Turn the user's bullet points into a separate email subject line and email body. $langRule $toneRule " +
                    "Use ONLY the details provided by the user—never invent facts, dates, metrics, or recipient names. " +
                    "Leave any missing information clearly marked with placeholders such as [اسم المستلم] / [Recipient Name] or [التاريخ] / [Date] so the user can fill them in. " +
                    "Return JSON with separate \"subject\" and \"body\" fields."
            }
        }

        val bodyObj = buildJsonObject {
            put("systemInstruction", buildJsonObject {
                put("parts", buildJsonArray {
                    add(buildJsonObject { put("text", systemInstruction) })
                })
            })
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", cleanText) })
                    })
                })
            })
            put("generationConfig", buildJsonObject {
                put("temperature", if (request.operation == AiWritingOperation.PROOFREAD) 0.1 else 0.3)
                put("responseMimeType", "application/json")
                put("responseSchema", buildJsonObject {
                    put("type", "OBJECT")
                    put("properties", buildJsonObject {
                        if (request.operation == AiWritingOperation.COMPOSE_EMAIL) {
                            put("subject", buildJsonObject { put("type", "STRING") })
                            put("body", buildJsonObject { put("type", "STRING") })
                        } else {
                            put("text", buildJsonObject { put("type", "STRING") })
                        }
                        put("detectedLanguage", buildJsonObject { put("type", "STRING") })
                    })
                    put("required", buildJsonArray {
                        if (request.operation == AiWritingOperation.COMPOSE_EMAIL) {
                            add(JsonPrimitive("subject"))
                            add(JsonPrimitive("body"))
                        } else {
                            add(JsonPrimitive("text"))
                        }
                    })
                })
            })
        }
        return json.encodeToString(JsonObject.serializer(), bodyObj)
    }

    private fun buildDirectTranslateGeminiBody(request: AiTranslateRequest, cleanText: String): String {
        val sourceHint = when (request.sourceLanguage) {
            AiLanguageOption.AR -> "The source text is Arabic (which may be Modern Standard Arabic or Egyptian Arabic dialect)."
            AiLanguageOption.EN -> "The source text is English."
            AiLanguageOption.AUTO -> "Automatically detect whether the source text is Arabic (including Egyptian Arabic) or English."
        }
        val targetHint = if (request.targetLanguage == AiLanguageOption.AR) {
            "Translate into clear, natural Arabic."
        } else {
            "Translate into clear, natural English."
        }
        val systemInstruction =
            "You are an expert Arabic-English translator. $sourceHint $targetHint " +
                "If the Arabic source uses Egyptian Arabic dialect or idioms, translate the intended meaning accurately into natural phrasing. " +
                "Strictly preserve proper names, numbers, URLs, email addresses, hashtags, emojis, and line breaks (\\n). " +
                "Do not add explanations or commentary. Return JSON matching the schema with \"translatedText\" and \"detectedSourceLanguage\" (\"ar\" or \"en\")."

        val bodyObj = buildJsonObject {
            put("systemInstruction", buildJsonObject {
                put("parts", buildJsonArray {
                    add(buildJsonObject { put("text", systemInstruction) })
                })
            })
            put("contents", buildJsonArray {
                add(buildJsonObject {
                    put("role", "user")
                    put("parts", buildJsonArray {
                        add(buildJsonObject { put("text", cleanText) })
                    })
                })
            })
            put("generationConfig", buildJsonObject {
                put("temperature", 0.1)
                put("responseMimeType", "application/json")
                put("responseSchema", buildJsonObject {
                    put("type", "OBJECT")
                    put("properties", buildJsonObject {
                        put("translatedText", buildJsonObject { put("type", "STRING") })
                        put("detectedSourceLanguage", buildJsonObject { put("type", "STRING") })
                    })
                    put("required", buildJsonArray {
                        add(JsonPrimitive("translatedText"))
                        add(JsonPrimitive("detectedSourceLanguage"))
                    })
                })
            })
        }
        return json.encodeToString(JsonObject.serializer(), bodyObj)
    }

    private fun isExplicitlyOffline(): Boolean {
        val ctx = context ?: return false
        return runCatching {
            val cm = ctx.getSystemService(Context.CONNECTIVITY_SERVICE) as? ConnectivityManager ?: return@runCatching false
            val active = cm.activeNetwork ?: return@runCatching true
            val caps = cm.getNetworkCapabilities(active) ?: return@runCatching true
            !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
        }.getOrDefault(false)
    }

    companion object {
        const val DEFAULT_GEMINI_ENDPOINT =
            "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.5-flash:generateContent"

        fun resolveProcessEndpoint(rawBaseUrl: String): URL {
            val trimmed = rawBaseUrl.trim()
            if (trimmed.isEmpty()) {
                throw AiProxyException(AiProxyErrorType.UNCONFIGURED)
            }
            val uri = runCatching { URI(trimmed) }.getOrNull()
                ?: throw AiProxyException(AiProxyErrorType.INVALID_URL)
            val scheme = uri.scheme?.lowercase()
            if ((scheme != "https" && scheme != "http") || uri.host.isNullOrBlank()) {
                throw AiProxyException(AiProxyErrorType.INVALID_URL)
            }
            val clean = trimmed.trimEnd('/')
            val finalUrl = if (clean.endsWith("/v1/ai/process")) {
                clean
            } else {
                "$clean/v1/ai/process"
            }
            return runCatching { URL(finalUrl) }.getOrNull()
                ?: throw AiProxyException(AiProxyErrorType.INVALID_URL)
        }
    }
}
