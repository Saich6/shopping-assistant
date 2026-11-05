/**
 * The widget never talks to an analytics system directly. It announces what happened as a browser event;
 * whoever embeds the widget listens for it. detail.type is one of:
 * assistant_opened | query_sent | response_received | link_click | new_chat
 */
export function emit(type, extra) {
  try {
    window.dispatchEvent(new CustomEvent('shopping-assistant', { detail: { type, ...extra } }));
  } catch (e) {
    /* analytics must never break the widget */
  }
}
