const KEY = 'shoppingAssistant.sessionId';

// sessionStorage can be blocked (private mode, strict cookie settings) and then throws,
// so every access is wrapped: the widget still works, it just cannot remember the session across a reload.
export function loadSessionId() {
  try { return window.sessionStorage.getItem(KEY); } catch (e) { return null; }
}
export function saveSessionId(id) {
  try { window.sessionStorage.setItem(KEY, id); } catch (e) { /* ignore */ }
}
export function clearSessionId() {
  try { window.sessionStorage.removeItem(KEY); } catch (e) { /* ignore */ }
}
