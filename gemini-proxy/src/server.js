import http from 'node:http';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath, pathToFileURL } from 'node:url';

const DEFAULT_MODEL = 'gemini-2.5-flash';
const DEFAULT_MAX_BODY_BYTES = 32 * 1024;
const VALID_WRITING_ACTIONS = new Set(['proofread', 'rewrite', 'compose_message', 'compose_email']);
const VALID_TONES = new Set(['same', 'formal', 'friendly', 'concise']);
const VALID_LANGS = new Set(['auto', 'ar', 'en']);
const VALID_TARGET_LANGS = new Set(['ar', 'en']);

function parseTokenList(raw) {
  if (!raw) return [];
  return String(raw)
    .split(',')
    .map((t) => t.trim())
    .filter((t) => t.length > 0);
}

function timingSafeTokenEquals(a, b) {
  const bufA = Buffer.from(String(a), 'utf8');
  const bufB = Buffer.from(String(b), 'utf8');
  if (bufA.length !== bufB.length) return false;
  return crypto.timingSafeEqual(bufA, bufB);
}

function hashIdentifier(value) {
  return crypto.createHash('sha256').update(String(value)).digest('hex').slice(0, 12);
}

export function buildGeminiRequestSpec(payload) {
  const { task, text } = payload;

  if (task === 'writing') {
    const action = VALID_WRITING_ACTIONS.has(payload.action) ? payload.action : 'proofread';
    const tone = VALID_TONES.has(payload.tone) ? payload.tone : 'same';
    const targetLanguage = VALID_LANGS.has(payload.targetLanguage) ? payload.targetLanguage : 'auto';

    let systemInstruction = '';
    if (action === 'proofread') {
      systemInstruction =
        'You are a careful bilingual Arabic and English proofreader. ' +
        'Fix spelling, grammar, and punctuation errors while strictly preserving the original meaning, language, tone, and register. ' +
        'If the text is written in Egyptian Arabic dialect (العامية المصرية), keep it in natural Egyptian Arabic without converting it to Modern Standard Arabic. ' +
        'Preserve all names, numbers, URLs, emails, and line breaks. Return JSON matching the schema.';
    } else if (action === 'rewrite') {
      const toneRule = {
        same: 'Keep the exact same tone, register, and dialect (including Egyptian Arabic if used), improving clarity only.',
        formal: 'Use a clear, polite, formal register appropriate for work or official communication.',
        friendly: 'Use a warm, polite, friendly tone.',
        concise: 'Make the text tighter, clearer, and more concise while preserving all meaning.'
      }[tone];
      systemInstruction =
        `Rewrite the user's text more clearly while preserving all proper names, numbers, dates, URLs, and core meaning. ${toneRule} ` +
        'Keep the original language unless otherwise clear from the input. Return JSON matching the schema.';
    } else if (action === 'compose_message') {
      const langInstruction =
        targetLanguage === 'ar'
          ? 'Write the message draft in natural Arabic.'
          : targetLanguage === 'en'
            ? 'Write the message draft in natural English.'
            : 'Write the message draft in the same language as the user notes.';
      systemInstruction =
        `Turn the user's idea or bullet points into a ready-to-send chat/message draft. ${langInstruction} ` +
        'Use ONLY facts provided by the user. If any specific detail (such as a name, time, or place) is missing and needed, use a clear bracketed placeholder like [الاسم] or [Name]. Return JSON matching the schema.';
    } else {
      const langInstruction =
        targetLanguage === 'ar'
          ? 'Write both subject and email body in natural Arabic.'
          : targetLanguage === 'en'
            ? 'Write both subject and email body in natural English.'
            : 'Write both subject and email body in the same language as the user notes.';
      const toneInstruction = {
        same: 'Use a clear professional tone.',
        formal: 'Use a formal professional email tone.',
        friendly: 'Use a warm, friendly yet polite email tone.',
        concise: 'Keep the email concise and direct.'
      }[tone];
      systemInstruction =
        `Turn the user's bullet points into a separate email subject line and email body. ${langInstruction} ${toneInstruction} ` +
        'Use ONLY the details provided by the user—never invent facts, dates, metrics, or recipient names. ' +
        'Leave any missing information clearly marked with placeholders such as [اسم المستلم] / [Recipient Name] or [التاريخ] / [Date] so the user can fill them in. ' +
        'Return JSON with separate "subject" and "body" fields.';
    }

    const properties =
      action === 'compose_email'
        ? {
            subject: { type: 'STRING' },
            body: { type: 'STRING' },
            detectedLanguage: { type: 'STRING' }
          }
        : {
            text: { type: 'STRING' },
            detectedLanguage: { type: 'STRING' }
          };

    const required = action === 'compose_email' ? ['subject', 'body'] : ['text'];

    return {
      action,
      geminiBody: {
        systemInstruction: { parts: [{ text: systemInstruction }] },
        contents: [{ role: 'user', parts: [{ text }] }],
        generationConfig: {
          temperature: action === 'proofread' ? 0.1 : 0.3,
          responseMimeType: 'application/json',
          responseSchema: {
            type: 'OBJECT',
            properties,
            required
          }
        }
      }
    };
  }

  // Translation
  const sourceLanguage = VALID_LANGS.has(payload.sourceLanguage) ? payload.sourceLanguage : 'auto';
  const targetLanguage = VALID_TARGET_LANGS.has(payload.targetLanguage) ? payload.targetLanguage : 'ar';

  const sourceHint =
    sourceLanguage === 'ar'
      ? 'The source text is Arabic (which may be Modern Standard Arabic or Egyptian Arabic dialect).'
      : sourceLanguage === 'en'
        ? 'The source text is English.'
        : 'Automatically detect whether the source text is Arabic (including Egyptian Arabic) or English.';
  const targetHint =
    targetLanguage === 'ar' ? 'Translate into clear, natural Arabic.' : 'Translate into clear, natural English.';

  const systemInstruction =
    `You are an expert Arabic-English translator. ${sourceHint} ${targetHint} ` +
    'If the Arabic source uses Egyptian Arabic dialect or idioms, translate the intended meaning accurately into natural phrasing. ' +
    'Strictly preserve proper names, numbers, URLs, email addresses, hashtags, emojis, and line breaks (\\n). ' +
    'Do not add explanations or commentary. Return JSON matching the schema with "translatedText" and "detectedSourceLanguage" ("ar" or "en").';

  return {
    action: 'translate',
    geminiBody: {
      systemInstruction: { parts: [{ text: systemInstruction }] },
      contents: [{ role: 'user', parts: [{ text }] }],
      generationConfig: {
        temperature: 0.1,
        responseMimeType: 'application/json',
        responseSchema: {
          type: 'OBJECT',
          properties: {
            translatedText: { type: 'STRING' },
            detectedSourceLanguage: { type: 'STRING' }
          },
          required: ['translatedText', 'detectedSourceLanguage']
        }
      }
    }
  };
}

function sendJson(res, statusCode, body, extraHeaders = {}) {
  const jsonText = JSON.stringify(body);
  res.writeHead(statusCode, {
    'Content-Type': 'application/json; charset=utf-8',
    'Cache-Control': 'no-store',
    ...extraHeaders
  });
  res.end(jsonText);
}

async function readJsonBody(req, maxBytes) {
  return new Promise((resolve, reject) => {
    const chunks = [];
    let total = 0;
    req.on('data', (chunk) => {
      total += chunk.length;
      if (total > maxBytes) {
        reject(Object.assign(new Error('Payload too large'), { statusCode: 413, code: 'PAYLOAD_TOO_LARGE' }));
        req.destroy();
        return;
      }
      chunks.push(chunk);
    });
    req.on('end', () => {
      try {
        const raw = Buffer.concat(chunks).toString('utf8');
        resolve(raw ? JSON.parse(raw) : {});
      } catch {
        reject(Object.assign(new Error('Invalid JSON payload'), { statusCode: 400, code: 'INVALID_JSON' }));
      }
    });
    req.on('error', (err) => reject(err));
  });
}

export function createProxyHandler(options = {}) {
  const geminiApiKey = (options.geminiApiKey ?? process.env.GEMINI_API_KEY ?? '').trim();
  const authTokens = Array.isArray(options.authTokens)
    ? options.authTokens.filter(Boolean)
    : parseTokenList(options.authTokens ?? process.env.PROXY_AUTH_TOKENS);
  const geminiModel = (options.geminiModel ?? process.env.GEMINI_MODEL ?? DEFAULT_MODEL).trim() || DEFAULT_MODEL;
  const rateLimitWindowMs = Number(options.rateLimitWindowMs ?? process.env.RATE_LIMIT_WINDOW_MS ?? 60_000);
  const rateLimitMax = Number(options.rateLimitMax ?? process.env.RATE_LIMIT_MAX_REQUESTS ?? 20);
  const maxInputChars = Number(options.maxInputChars ?? process.env.MAX_INPUT_CHARS ?? 4000);
  const requestTimeoutMs = Number(options.requestTimeoutMs ?? process.env.REQUEST_TIMEOUT_MS ?? 25_000);
  const fetchImpl = options.fetchImpl ?? globalThis.fetch;
  const nowFn = options.nowFn ?? (() => Date.now());
  const logger = options.logger ?? ((meta) => console.info(JSON.stringify(meta)));

  // In-memory sliding/fixed rate limiter bucket per authenticated client + IP
  const buckets = new Map();

  function checkRateLimit(clientKey) {
    const now = nowFn();
    let bucket = buckets.get(clientKey);
    if (!bucket || now - bucket.windowStart >= rateLimitWindowMs) {
      bucket = { windowStart: now, count: 0 };
      buckets.set(clientKey, bucket);
    }
    bucket.count += 1;
    const remaining = Math.max(0, rateLimitMax - bucket.count);
    const resetSeconds = Math.max(1, Math.ceil((bucket.windowStart + rateLimitWindowMs - now) / 1000));
    return {
      allowed: bucket.count <= rateLimitMax,
      remaining,
      resetSeconds
    };
  }

  return async function handleRequest(req, res) {
    const startMs = nowFn();
    const method = req.method || 'GET';
    const urlPath = (req.url || '/').split('?')[0];

    if (method === 'GET' && urlPath === '/health') {
      return sendJson(res, 200, {
        ok: true,
        configured: Boolean(geminiApiKey) && authTokens.length > 0,
        model: geminiModel
      });
    }

    if (method !== 'POST' || urlPath !== '/v1/ai/process') {
      return sendJson(res, 404, { error: { code: 'NOT_FOUND', message: 'Endpoint not found' } });
    }

    if (authTokens.length === 0 || !geminiApiKey) {
      logger({ event: 'proxy_unconfigured', status: 503 });
      return sendJson(res, 503, {
        error: { code: 'SERVER_NOT_CONFIGURED', message: 'Gemini proxy server secrets are not configured' }
      });
    }

    // Extract & verify client bearer token
    const authHeader = String(req.headers.authorization || '');
    const customToken = String(req.headers['x-proxy-token'] || '');
    const token = authHeader.startsWith('Bearer ') ? authHeader.slice(7).trim() : customToken.trim();
    const authorized = token.length > 0 && authTokens.some((expected) => timingSafeTokenEquals(token, expected));
    if (!authorized) {
      logger({ event: 'proxy_unauthorized', status: 401 });
      return sendJson(res, 401, {
        error: { code: 'UNAUTHORIZED', message: 'Missing or invalid proxy authentication token' }
      });
    }

    const remoteIp =
      String(req.headers['x-forwarded-for'] || '')
        .split(',')[0]
        .trim() ||
      req.socket?.remoteAddress ||
      'unknown';
    const clientHash = hashIdentifier(`${token}:${remoteIp}`);
    const limitResult = checkRateLimit(clientHash);
    const rateHeaders = {
      'X-RateLimit-Limit': String(rateLimitMax),
      'X-RateLimit-Remaining': String(limitResult.remaining),
      'Retry-After': String(limitResult.resetSeconds)
    };
    if (!limitResult.allowed) {
      logger({ event: 'proxy_rate_limited', client: clientHash, status: 429 });
      return sendJson(
        res,
        429,
        { error: { code: 'RATE_LIMITED', message: 'Too many requests, please slow down.' } },
        rateHeaders
      );
    }

    let body;
    try {
      body = await readJsonBody(req, DEFAULT_MAX_BODY_BYTES);
    } catch (err) {
      const code = err?.statusCode || 400;
      return sendJson(res, code, { error: { code: err?.code || 'BAD_REQUEST', message: err.message } }, rateHeaders);
    }

    const task = body?.task;
    const text = typeof body?.text === 'string' ? body.text.trim() : '';
    if (task !== 'writing' && task !== 'translate') {
      return sendJson(
        res,
        400,
        { error: { code: 'INVALID_TASK', message: 'task must be "writing" or "translate"' } },
        rateHeaders
      );
    }
    if (!text) {
      return sendJson(
        res,
        400,
        { error: { code: 'EMPTY_TEXT', message: 'text is required' } },
        rateHeaders
      );
    }
    if (text.length > maxInputChars) {
      return sendJson(
        res,
        400,
        { error: { code: 'TEXT_TOO_LONG', message: `text exceeds ${maxInputChars} characters` } },
        rateHeaders
      );
    }

    if (task === 'writing' && body.action && !VALID_WRITING_ACTIONS.has(body.action)) {
      return sendJson(
        res,
        400,
        { error: { code: 'INVALID_ACTION', message: 'Unsupported writing action' } },
        rateHeaders
      );
    }
    if (task === 'translate' && body.targetLanguage && !VALID_TARGET_LANGS.has(body.targetLanguage)) {
      return sendJson(
        res,
        400,
        { error: { code: 'INVALID_TARGET_LANGUAGE', message: 'targetLanguage must be "ar" or "en"' } },
        rateHeaders
      );
    }

    const { action, geminiBody } = buildGeminiRequestSpec({ ...body, text });
    const endpoint = `https://generativelanguage.googleapis.com/v1beta/models/${encodeURIComponent(geminiModel)}:generateContent`;

    const abortController = new AbortController();
    const timer = setTimeout(() => abortController.abort(), requestTimeoutMs);

    try {
      const upstream = await fetchImpl(endpoint, {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'x-goog-api-key': geminiApiKey
        },
        body: JSON.stringify(geminiBody),
        signal: abortController.signal
      });

      if (!upstream.ok) {
        const statusOut = upstream.status === 429 ? 429 : 502;
        logger({
          event: 'gemini_upstream_error',
          client: clientHash,
          task,
          action,
          upstreamStatus: upstream.status,
          status: statusOut,
          latencyMs: nowFn() - startMs
        });
        return sendJson(
          res,
          statusOut,
          { error: { code: 'UPSTREAM_ERROR', message: `Upstream model error (${upstream.status})` } },
          rateHeaders
        );
      }

      const upstreamJson = await upstream.json();
      const rawText = upstreamJson?.candidates?.[0]?.content?.parts?.[0]?.text;
      if (!rawText) {
        logger({ event: 'gemini_empty_response', client: clientHash, task, action, status: 502 });
        return sendJson(
          res,
          502,
          { error: { code: 'EMPTY_RESPONSE', message: 'Model returned an empty response' } },
          rateHeaders
        );
      }

      let parsed;
      try {
        parsed = JSON.parse(rawText);
      } catch {
        logger({ event: 'gemini_invalid_json', client: clientHash, task, action, status: 502 });
        return sendJson(
          res,
          502,
          { error: { code: 'INVALID_UPSTREAM_JSON', message: 'Model returned malformed JSON' } },
          rateHeaders
        );
      }

      // Privacy invariant: NEVER log input text or generated text
      logger({
        event: 'proxy_success',
        client: clientHash,
        task,
        action,
        inputLength: text.length,
        status: 200,
        latencyMs: nowFn() - startMs
      });

      return sendJson(res, 200, { task, action, ...parsed }, rateHeaders);
    } catch (err) {
      const isTimeout = err?.name === 'AbortError';
      const status = isTimeout ? 504 : 502;
      logger({
        event: isTimeout ? 'gemini_timeout' : 'gemini_network_error',
        client: clientHash,
        task,
        action,
        status,
        latencyMs: nowFn() - startMs
      });
      return sendJson(
        res,
        status,
        {
          error: {
            code: isTimeout ? 'TIMEOUT' : 'UPSTREAM_UNREACHABLE',
            message: isTimeout ? 'Upstream request timed out' : 'Failed to reach Gemini upstream'
          }
        },
        rateHeaders
      );
    } finally {
      clearTimeout(timer);
    }
  };
}

export function createProxyServer(options = {}) {
  const handler = createProxyHandler(options);
  return http.createServer((req, res) => {
    handler(req, res).catch(() => {
      if (!res.headersSent) {
        sendJson(res, 500, { error: { code: 'INTERNAL_ERROR', message: 'Unexpected server error' } });
      }
    });
  });
}

function loadEnvFileIfPresent(filePath) {
  try {
    if (!fs.existsSync(filePath)) return;
    const raw = fs.readFileSync(filePath, 'utf8');
    for (const line of raw.split(/\r?\n/)) {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith('#')) continue;
      const eq = trimmed.indexOf('=');
      if (eq <= 0) continue;
      const key = trimmed.slice(0, eq).trim();
      let val = trimmed.slice(eq + 1).trim();
      if ((val.startsWith('"') && val.endsWith('"')) || (val.startsWith("'") && val.endsWith("'"))) {
        val = val.slice(1, -1);
      }
      if (!process.env[key] && val) {
        process.env[key] = val;
      }
    }
  } catch {
    // Ignore unreadable local env files cleanly
  }
}

const isDirectExecution =
  typeof process !== 'undefined' &&
  process.argv[1] &&
  import.meta.url === pathToFileURL(process.argv[1]).href;

if (isDirectExecution) {
  const __dirname = path.dirname(fileURLToPath(import.meta.url));
  loadEnvFileIfPresent(path.resolve(__dirname, '../.env'));
  loadEnvFileIfPresent(path.resolve(__dirname, '../../.env'));
  const port = Number(process.env.PORT || 8080);
  const server = createProxyServer();
  server.listen(port, () => {
    console.info(JSON.stringify({ event: 'heliboard_gemini_proxy_started', port }));
  });
}
