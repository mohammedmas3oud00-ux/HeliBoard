import test from 'node:test';
import assert from 'node:assert/strict';
import { createProxyServer } from '../src/server.js';

async function withTestServer(options, fn) {
  const server = createProxyServer(options);
  await new Promise((resolve) => server.listen(0, '127.0.0.1', resolve));
  const addr = server.address();
  const baseUrl = `http://127.0.0.1:${addr.port}`;
  try {
    await fn(baseUrl);
  } finally {
    await new Promise((resolve) => server.close(resolve));
  }
}

test('health endpoint reports configured status accurately', async () => {
  await withTestServer({ geminiApiKey: 'test-key', authTokens: ['tok-1'], logger: () => {} }, async (url) => {
    const res = await fetch(`${url}/health`);
    assert.equal(res.status, 200);
    const body = await res.json();
    assert.equal(body.ok, true);
    assert.equal(body.configured, true);
  });

  await withTestServer({ geminiApiKey: '', authTokens: [], logger: () => {} }, async (url) => {
    const res = await fetch(`${url}/health`);
    const body = await res.json();
    assert.equal(body.configured, false);
  });
});

test('rejects unauthenticated and unconfigured requests cleanly', async () => {
  await withTestServer({ geminiApiKey: '', authTokens: [], logger: () => {} }, async (url) => {
    const res = await fetch(`${url}/v1/ai/process`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify({ task: 'translate', text: 'hello', sourceLanguage: 'en', targetLanguage: 'ar' })
    });
    assert.equal(res.status, 503);
  });

  await withTestServer({ geminiApiKey: 'secret-gemini', authTokens: ['secret-client'], logger: () => {} }, async (url) => {
    const res = await fetch(`${url}/v1/ai/process`, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json', Authorization: 'Bearer wrong-token' },
      body: JSON.stringify({ task: 'translate', text: 'hello', sourceLanguage: 'en', targetLanguage: 'ar' })
    });
    assert.equal(res.status, 401);
  });
});

test('enforces rate limit and never logs sensitive text or results', async () => {
  const logs = [];
  let seenGeminiKey = null;
  let seenGeminiBody = null;

  const mockFetch = async (_endpoint, init) => {
    seenGeminiKey = init.headers['x-goog-api-key'];
    seenGeminiBody = JSON.parse(init.body);
    return {
      ok: true,
      status: 200,
      json: async () => ({
        candidates: [
          {
            content: {
              parts: [
                {
                  text: JSON.stringify({
                    subject: 'متابعة الاجتماع [التاريخ]',
                    body: 'أهلاً [الاسم]، بخصوص الاجتماع الساعة 3.'
                  })
                }
              ]
            }
          }
        ]
      })
    };
  };

  await withTestServer(
    {
      geminiApiKey: 'server-only-gemini-key',
      authTokens: ['client-tok'],
      rateLimitMax: 2,
      rateLimitWindowMs: 60_000,
      fetchImpl: mockFetch,
      logger: (entry) => logs.push(JSON.stringify(entry))
    },
    async (url) => {
      const secretUserPhrase = 'اجتماع الساعة 3 مهم جدا مع سارة';
      const r1 = await fetch(`${url}/v1/ai/process`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: 'Bearer client-tok' },
        body: JSON.stringify({
          task: 'writing',
          action: 'compose_email',
          tone: 'formal',
          targetLanguage: 'ar',
          text: secretUserPhrase
        })
      });
      assert.equal(r1.status, 200);
      const data1 = await r1.json();
      assert.equal(data1.subject, 'متابعة الاجتماع [التاريخ]');
      assert.ok(data1.body.includes('الساعة 3'));
      assert.equal(seenGeminiKey, 'server-only-gemini-key');
      assert.ok(seenGeminiBody.systemInstruction.parts[0].text.includes('subject'));

      const r2 = await fetch(`${url}/v1/ai/process`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: 'Bearer client-tok' },
        body: JSON.stringify({
          task: 'translate',
          sourceLanguage: 'auto',
          targetLanguage: 'en',
          text: secretUserPhrase
        })
      });
      assert.equal(r2.status, 200);

      // 3rd request hits rate limit -> 429
      const r3 = await fetch(`${url}/v1/ai/process`, {
        method: 'POST',
        headers: { 'Content-Type': 'application/json', Authorization: 'Bearer client-tok' },
        body: JSON.stringify({
          task: 'translate',
          sourceLanguage: 'en',
          targetLanguage: 'ar',
          text: 'hello'
        })
      });
      assert.equal(r3.status, 429);

      const allLogs = logs.join('\n');
      assert.equal(allLogs.includes(secretUserPhrase), false);
      assert.equal(allLogs.includes('متابعة الاجتماع'), false);
      assert.equal(allLogs.includes('server-only-gemini-key'), false);
    }
  );
});
