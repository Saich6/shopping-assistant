/**
 * Reads settings from (in priority order): window.ShoppingAssistantConfig, data-* attributes on the script tag,
 * then defaults. `scriptEl` is document.currentScript, which is null when the script is injected dynamically
 * (e.g. by Adobe Target); then we look for a tag marked data-shopping-assistant.
 */
export function readConfig(scriptEl) {
  const el = scriptEl || document.querySelector('script[data-shopping-assistant]');
  const user = window.ShoppingAssistantConfig || {};
  const pick = (name, attr, fallback) => user[name] || (el && el.getAttribute(attr)) || fallback;
  return {
    apiUrl: pick('apiUrl', 'data-api-url', '/api/chat'),
    title: pick('title', 'data-title', 'Shopping Assistant')
  };
}
