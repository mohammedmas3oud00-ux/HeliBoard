# HeliBoard Gemini Proxy Service

Standalone HTTP proxy that keeps `GEMINI_API_KEY` on the server, enforces bearer token authentication and per-client rate limits, and exposes a minimal JSON API for HeliBoard's AI Writing and Translation panels.

## Environment Variables

- `GEMINI_API_KEY` (required): Google AI Studio / Gemini API key stored strictly as a server secret.
- `PROXY_AUTH_TOKENS` (required): Comma-separated list of client bearer tokens allowed to access `/v1/ai/process`.
- `GEMINI_MODEL` (optional, default `gemini-2.5-flash`): Gemini model name.
- `PORT` (optional, default `8080`): HTTP listening port.
- `RATE_LIMIT_WINDOW_MS` (optional, default `60000`): Rate-limit window in milliseconds.
- `RATE_LIMIT_MAX_REQUESTS` (optional, default `20`): Max requests per window per client token/IP.
- `MAX_INPUT_CHARS` (optional, default `4000`): Max allowed characters per request.

## Local Run & Tests

The server automatically loads `gemini-proxy/.env` and root `.env` if present:

```bash
cd gemini-proxy
cp .env.example .env
# Edit .env with GEMINI_API_KEY and PROXY_AUTH_TOKENS
npm start
npm test
```

## API

- `GET /health` -> `{ "ok": true, "configured": true, "model": "gemini-2.5-flash" }`
- `POST /v1/ai/process` (requires `Authorization: Bearer <token>`)
  - AI Writing body:
    `{ "task": "writing", "action": "proofread|rewrite|compose_message|compose_email", "text": "...", "tone": "same|formal|friendly|concise", "targetLanguage": "auto|ar|en" }`
  - Translate body:
    `{ "task": "translate", "text": "...", "sourceLanguage": "auto|ar|en", "targetLanguage": "ar|en" }`
