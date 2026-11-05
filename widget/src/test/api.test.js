import { afterEach, expect, test, vi } from 'vitest';
import { errorText, postChat } from '../api.js';

afterEach(() => vi.unstubAllGlobals());

test('400 becomes a generic sentence, not "Bad Request"', () => {
  expect(errorText(400, { error: 'Bad Request' })).toMatch(/could not process/);
});

test('our own friendly 502/503 messages are shown', () => {
  expect(errorText(503, { error: 'The assistant is busy right now.' })).toBe('The assistant is busy right now.');
  expect(errorText(502, { error: 'Gemini returned HTTP 403' })).toBe('Gemini returned HTTP 403');
});

test('anything else becomes a generic message', () => {
  expect(errorText(500, { error: 'secret stack trace' })).toMatch(/Something went wrong/);
  expect(errorText(503, {})).toMatch(/Something went wrong/);
  expect(errorText(503, { error: 'x'.repeat(500) })).toMatch(/Something went wrong/);
});

test('postChat does not throw when the error body is not JSON', async () => {
  vi.stubGlobal('fetch', vi.fn().mockResolvedValue({ ok: false, status: 502, json: async () => { throw new Error('no json'); } }));
  expect(await postChat('/api/chat', { message: 'hi' })).toEqual({ ok: false, status: 502, data: {} });
});
