import { useCallback, useRef, useState } from 'react';
import { REQUEST_TIMEOUT_MS, errorText, postChat } from './api.js';
import { emit } from './analytics.js';
import { clearSessionId, loadSessionId, saveSessionId } from './storage.js';

const WELCOME = {
  id: 0,
  kind: 'assistant',
  text: 'Hi! I can help you find products, compare options and spot offers. What are you looking for?'
};

/**
 * All chat logic lives here, so the visual component stays simple.
 * Returns the messages, whether we are waiting for an answer, and the send / reset actions.
 */
export function useChat(apiUrl) {
  const [messages, setMessages] = useState([WELCOME]);
  const [busy, setBusy] = useState(false);
  // A ref (not state): the id never changes what is drawn, and send() must always see the latest value.
  const sessionId = useRef(loadSessionId());
  const nextId = useRef(1);
  const busyRef = useRef(false); // blocks double sends before React has re-rendered

  const add = useCallback((kind, text) => {
    setMessages((prev) => [...prev, { id: nextId.current++, kind, text }]);
  }, []);

  const send = useCallback(async (raw) => {
    const text = (raw || '').trim();
    if (!text || busyRef.current) return;
    busyRef.current = true;
    setBusy(true);
    add('user', text);
    emit('query_sent', { length: text.length });

    const body = { message: text };
    if (sessionId.current) body.sessionId = sessionId.current;
    const controller = new AbortController();
    const timer = setTimeout(() => controller.abort(), REQUEST_TIMEOUT_MS);
    try {
      const res = await postChat(apiUrl, body, controller.signal);
      if (res.ok && typeof res.data.reply === 'string') {
        if (typeof res.data.sessionId === 'string') {
          sessionId.current = res.data.sessionId;
          saveSessionId(res.data.sessionId);
        }
        add('assistant', res.data.reply);
        emit('response_received', {});
      } else {
        add('error', errorText(res.status, res.data));
      }
    } catch (err) {
      add('error', err && err.name === 'AbortError'
        ? 'This is taking too long. Please try again.'
        : 'I could not reach the assistant. Check your connection and try again.');
    } finally {
      clearTimeout(timer);
      busyRef.current = false;
      setBusy(false);
    }
  }, [apiUrl, add]);

  const reset = useCallback(() => {
    clearSessionId();
    sessionId.current = null;
    nextId.current = 1;
    setMessages([WELCOME]);
    emit('new_chat', {});
  }, []);

  return { messages, busy, send, reset };
}
