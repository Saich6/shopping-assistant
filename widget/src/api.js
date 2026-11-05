export const REQUEST_TIMEOUT_MS = 60000; // the server may retry and fall back to a second model before it answers
export const MAX_MESSAGE_LENGTH = 2000;  // matches the server limit

/** POSTs one chat message. Never throws for HTTP errors: returns { ok, status, data }. */
export async function postChat(apiUrl, body, signal) {
  const response = await fetch(apiUrl, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(body),
    signal
  });
  const data = await response.json().catch(() => ({}));
  return { ok: response.ok, status: response.status, data };
}

/** Never shows raw server errors; our own friendly 502/503 messages are the only ones passed through. */
export function errorText(status, data) {
  if (status === 400) return 'Sorry, I could not process that message.';
  if ((status === 502 || status === 503) && data && typeof data.error === 'string' && data.error.length < 300) {
    return data.error; // e.g. "The assistant is busy right now. Please try again in a moment."
  }
  return 'Something went wrong. Please try again.';
}
