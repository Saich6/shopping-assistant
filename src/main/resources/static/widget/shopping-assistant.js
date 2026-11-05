/*
 * Shopping Assistant chat widget: one self-contained script, no dependencies.
 * Injects a floating chat button into any page (the local stand-in for the script Adobe Target injects).
 *
 * Usage:   <script src="/widget/shopping-assistant.js" data-api-url="/api/chat" data-title="Shopping Assistant"></script>
 * Or set   window.ShoppingAssistantConfig = { apiUrl: "...", title: "..." }   before the script loads
 * (use this when the script is injected dynamically and document.currentScript is not available).
 *
 * Analytics: dispatches a "shopping-assistant" event on window with detail.type =
 *   assistant_opened | query_sent | response_received | link_click | new_chat
 */
(function () {
  'use strict';
  if (window.__shoppingAssistantLoaded) return; // never inject twice
  window.__shoppingAssistantLoaded = true;

  // ---------- configuration ----------
  var scriptEl = document.currentScript || document.querySelector('script[data-shopping-assistant]');
  var userCfg = window.ShoppingAssistantConfig || {};
  function opt(name, attr, fallback) {
    return userCfg[name] || (scriptEl && scriptEl.getAttribute(attr)) || fallback;
  }
  var cfg = {
    apiUrl: opt('apiUrl', 'data-api-url', '/api/chat'),
    title: opt('title', 'data-title', 'Shopping Assistant')
  };
  var SESSION_KEY = 'shoppingAssistant.sessionId';
  var REQUEST_TIMEOUT_MS = 60000;
  var MAX_MESSAGE_LENGTH = 2000; // matches the server limit
  var SUGGESTIONS = ['TVs on offer', 'Olive oil options', 'Headphones under $300'];

  // ---------- small helpers ----------
  function emit(type, extra) {
    try {
      var detail = { type: type };
      for (var k in extra) if (Object.prototype.hasOwnProperty.call(extra, k)) detail[k] = extra[k];
      window.dispatchEvent(new CustomEvent('shopping-assistant', { detail: detail }));
    } catch (e) { /* analytics must never break the widget */ }
  }
  function store(action, value) {
    try { // storage can be blocked (private mode, cookie settings), so the widget must work without it
      if (action === 'get') return window.sessionStorage.getItem(SESSION_KEY);
      if (action === 'set') window.sessionStorage.setItem(SESSION_KEY, value);
      if (action === 'clear') window.sessionStorage.removeItem(SESSION_KEY);
    } catch (e) { /* ignore */ }
    return null;
  }
  function el(tag, className, attrs) {
    var node = document.createElement(tag);
    if (className) node.className = className;
    if (attrs) for (var k in attrs) node.setAttribute(k, attrs[k]);
    return node;
  }

  // ---------- safe Markdown rendering ----------
  // The model answers in Markdown (**bold**, bullets, links). We build DOM nodes with textContent and never
  // use innerHTML, so nothing the model (or a web page it read) writes can run as HTML or script.
  var INLINE = /\*\*(.+?)\*\*|\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)|(https?:\/\/[^\s<>)]+)/g;

  function makeLink(parent, url, label) {
    var trailing = '';
    var m = /[.,;:!?]+$/.exec(label === url ? url : '');
    if (m) { trailing = m[0]; url = url.slice(0, -trailing.length); label = url; }
    var a = el('a', null, { href: url, target: '_blank', rel: 'noopener noreferrer' });
    a.textContent = label;
    a.addEventListener('click', function () { emit('link_click', { url: url }); });
    parent.appendChild(a);
    if (trailing) parent.appendChild(document.createTextNode(trailing));
  }

  function renderInline(parent, text) {
    var last = 0, m;
    INLINE.lastIndex = 0;
    while ((m = INLINE.exec(text))) {
      if (m.index > last) parent.appendChild(document.createTextNode(text.slice(last, m.index)));
      if (m[1] !== undefined) {
        var strong = el('strong');
        strong.textContent = m[1];
        parent.appendChild(strong);
      } else {
        makeLink(parent, m[3] || m[4], m[2] || m[4]);
      }
      last = INLINE.lastIndex;
    }
    if (last < text.length) parent.appendChild(document.createTextNode(text.slice(last)));
  }

  function renderMarkdown(root, text) {
    var para = null, list = null, listType = null;
    String(text).replace(/\r\n/g, '\n').split('\n').forEach(function (raw) {
      var bullet = /^\s*[*-]\s+(.*)$/.exec(raw);
      var numbered = /^\s*\d+[.)]\s+(.*)$/.exec(raw);
      var heading = /^\s*#{1,6}\s+(.*)$/.exec(raw);
      if (bullet || numbered) {
        para = null;
        var type = bullet ? 'ul' : 'ol';
        if (!list || listType !== type) { list = el(type); listType = type; root.appendChild(list); }
        var li = el('li');
        renderInline(li, (bullet || numbered)[1]);
        list.appendChild(li);
      } else if (!raw.trim()) {
        para = null; list = null; listType = null;
      } else if (heading) {
        para = null; list = null; listType = null;
        var p = el('p', 'sa-heading');
        var strong = el('strong');
        strong.textContent = heading[1];
        p.appendChild(strong);
        root.appendChild(p);
      } else {
        list = null; listType = null;
        if (!para) { para = el('p'); root.appendChild(para); } else { para.appendChild(el('br')); }
        renderInline(para, raw.trim());
      }
    });
  }

  // ---------- styles (inside the shadow root, so the host page cannot affect them) ----------
  var CSS = [
    ':host{all:initial}',
    '*{box-sizing:border-box}',
    '.sa{--bg:#fff;--fg:#1c2330;--muted:#5b6575;--border:#dfe3ea;--accent:#1a56db;--accent-fg:#fff;--user-bg:#1a56db;--user-fg:#fff;--bot-bg:#f1f3f7;--err-bg:#fdecec;--err-fg:#9b1c1c;--shadow:0 12px 36px rgba(16,24,40,.22);',
    'font:14px/1.45 system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;color:var(--fg)}',
    '@media (prefers-color-scheme:dark){.sa{--bg:#1b2130;--fg:#e8ebf2;--muted:#9aa5b8;--border:#33405a;--accent:#5b8def;--accent-fg:#0b1020;--user-bg:#2d5bd0;--user-fg:#fff;--bot-bg:#262e42;--err-bg:#4a2125;--err-fg:#ffc9c9;--shadow:0 12px 36px rgba(0,0,0,.6)}}',
    '.launcher{position:fixed;right:20px;bottom:20px;z-index:2147483000;width:56px;height:56px;border-radius:50%;border:0;cursor:pointer;background:var(--accent);color:var(--accent-fg);box-shadow:var(--shadow);display:flex;align-items:center;justify-content:center}',
    '.launcher:hover{filter:brightness(1.08)}',
    '.launcher svg{width:26px;height:26px}',
    'button:focus-visible,textarea:focus-visible,a:focus-visible{outline:3px solid #f5a524;outline-offset:2px}',
    '.panel{position:fixed;right:20px;bottom:88px;z-index:2147483000;width:380px;height:min(580px,calc(100vh - 110px));display:none;flex-direction:column;background:var(--bg);border:1px solid var(--border);border-radius:14px;box-shadow:var(--shadow);overflow:hidden}',
    '.panel.open{display:flex}',
    '.header{display:flex;align-items:center;gap:8px;padding:12px 14px;background:var(--accent);color:var(--accent-fg)}',
    '.title{flex:1;font-weight:600;font-size:15px}',
    '.hbtn{background:transparent;border:1px solid currentColor;color:inherit;border-radius:8px;padding:4px 9px;font:inherit;font-size:12px;cursor:pointer;opacity:.9}',
    '.hbtn:hover{opacity:1;background:rgba(255,255,255,.15)}',
    '.messages{flex:1;overflow-y:auto;padding:14px;display:flex;flex-direction:column;gap:10px}',
    '.msg{max-width:88%;padding:9px 12px;border-radius:14px;overflow-wrap:anywhere}',
    '.msg p{margin:0 0 .5em}.msg p:last-child{margin-bottom:0}',
    '.msg ul,.msg ol{margin:.3em 0 .5em;padding-left:1.3em}.msg li{margin:.15em 0}',
    '.msg a{color:inherit;text-decoration:underline}',
    '.msg.user{align-self:flex-end;background:var(--user-bg);color:var(--user-fg);border-bottom-right-radius:4px;white-space:pre-wrap}',
    '.msg.assistant{align-self:flex-start;background:var(--bot-bg);border-bottom-left-radius:4px}',
    '.msg.assistant a{color:var(--accent)}',
    '.msg.error{align-self:flex-start;background:var(--err-bg);color:var(--err-fg);border-bottom-left-radius:4px}',
    '.chips{display:flex;flex-wrap:wrap;gap:6px}',
    '.chip{background:var(--bg);color:var(--accent);border:1px solid var(--accent);border-radius:999px;padding:5px 11px;font:inherit;font-size:13px;cursor:pointer}',
    '.chip:hover{background:var(--bot-bg)}',
    '.typing{display:flex;gap:4px;align-items:center;padding:12px}',
    '.dot{width:7px;height:7px;border-radius:50%;background:var(--muted);animation:bounce 1.2s infinite ease-in-out}',
    '.dot:nth-child(2){animation-delay:.15s}.dot:nth-child(3){animation-delay:.3s}',
    '@keyframes bounce{0%,60%,100%{transform:translateY(0);opacity:.5}30%{transform:translateY(-5px);opacity:1}}',
    '@media (prefers-reduced-motion:reduce){.dot{animation:none}}',
    '.form{display:flex;gap:8px;padding:10px;border-top:1px solid var(--border);align-items:flex-end}',
    'textarea{flex:1;resize:none;max-height:110px;min-height:38px;padding:8px 10px;border:1px solid var(--border);border-radius:10px;background:var(--bg);color:var(--fg);font:inherit}',
    '.send{border:0;border-radius:10px;background:var(--accent);color:var(--accent-fg);padding:0 14px;height:38px;font:inherit;font-weight:600;cursor:pointer}',
    '.send:disabled{opacity:.5;cursor:not-allowed}',
    '.note{padding:0 12px 8px;font-size:11px;color:var(--muted)}',
    '.sr{position:absolute;width:1px;height:1px;overflow:hidden;clip:rect(0 0 0 0)}',
    '@media (max-width:480px){.panel{inset:0;width:auto;height:auto;border-radius:0;border:0}.launcher{right:14px;bottom:14px}}'
  ].join('\n');

  // ---------- build the UI ----------
  var host, launcher, panel, messages, input, sendBtn, chipsBox;
  var sessionId = store('get');
  var busy = false;

  function build() {
    // A custom element name (not a <div>): page rules like "div{display:none}" do not match it.
    host = el('shopping-assistant-root', null, { id: 'shopping-assistant-root' });
    var shadow = host.attachShadow({ mode: 'open' });
    var style = el('style');
    style.textContent = CSS;
    var root = el('div', 'sa');

    launcher = el('button', 'launcher', { type: 'button', 'aria-label': 'Open ' + cfg.title, 'aria-expanded': 'false' });
    launcher.innerHTML = '<svg viewBox="0 0 24 24" fill="none" stroke="currentColor" stroke-width="2" stroke-linecap="round" stroke-linejoin="round" aria-hidden="true"><path d="M21 15a2 2 0 0 1-2 2H8l-5 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z"/></svg>'; // fixed markup, no user data

    panel = el('section', 'panel', { role: 'dialog', 'aria-label': cfg.title });
    var header = el('div', 'header');
    var title = el('div', 'title');
    title.textContent = cfg.title;
    var newChat = el('button', 'hbtn', { type: 'button' });
    newChat.textContent = 'New chat';
    var close = el('button', 'hbtn', { type: 'button', 'aria-label': 'Close ' + cfg.title });
    close.textContent = '✕';
    header.appendChild(title); header.appendChild(newChat); header.appendChild(close);

    messages = el('div', 'messages', { role: 'log', 'aria-live': 'polite' });
    chipsBox = el('div', 'chips');

    var form = el('form', 'form');
    input = el('textarea', null, { rows: '1', maxlength: String(MAX_MESSAGE_LENGTH), placeholder: 'Ask about products or offers…', 'aria-label': 'Your message' });
    sendBtn = el('button', 'send', { type: 'submit' });
    sendBtn.textContent = 'Send';
    form.appendChild(input); form.appendChild(sendBtn);

    var note = el('div', 'note');
    note.textContent = 'AI assistant. Results from other stores are third-party listings.';

    panel.appendChild(header); panel.appendChild(messages); panel.appendChild(form); panel.appendChild(note);
    root.appendChild(launcher); root.appendChild(panel);
    shadow.appendChild(style); shadow.appendChild(root);
    document.body.appendChild(host);

    // events
    launcher.addEventListener('click', function () { setOpen(!panel.classList.contains('open')); });
    close.addEventListener('click', function () { setOpen(false); launcher.focus(); });
    newChat.addEventListener('click', resetChat);
    form.addEventListener('submit', function (e) { e.preventDefault(); send(input.value); });
    input.addEventListener('keydown', function (e) {
      if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); send(input.value); }
    });
    input.addEventListener('input', autoGrow);
    panel.addEventListener('keydown', function (e) {
      if (e.key === 'Escape') { setOpen(false); launcher.focus(); }
    });

    showWelcome();
  }

  function setOpen(open) {
    panel.classList.toggle('open', open);
    launcher.setAttribute('aria-expanded', String(open));
    if (open) { emit('assistant_opened', {}); input.focus(); scrollDown(); }
  }

  function showWelcome() {
    addMessage('assistant', 'Hi! I can help you find products, compare options and spot offers. What are you looking for?');
    chipsBox = el('div', 'chips');
    SUGGESTIONS.forEach(function (text) {
      var chip = el('button', 'chip', { type: 'button' });
      chip.textContent = text;
      chip.addEventListener('click', function () { send(text); });
      chipsBox.appendChild(chip);
    });
    messages.appendChild(chipsBox);
  }

  function resetChat() {
    store('clear');
    sessionId = null;
    messages.textContent = '';
    showWelcome();
    emit('new_chat', {});
    input.focus();
  }

  function autoGrow() {
    input.style.height = 'auto';
    input.style.height = Math.min(input.scrollHeight, 110) + 'px';
  }

  function scrollDown() { messages.scrollTop = messages.scrollHeight; }

  function addMessage(kind, text) {
    var box = el('div', 'msg ' + kind);
    if (kind === 'assistant') renderMarkdown(box, text);
    else box.textContent = text; // user text and errors are plain text, never HTML
    messages.appendChild(box);
    scrollDown();
    return box;
  }

  function addTyping() {
    var box = el('div', 'msg assistant typing', { 'aria-label': 'The assistant is typing' });
    box.appendChild(el('span', 'dot')); box.appendChild(el('span', 'dot')); box.appendChild(el('span', 'dot'));
    messages.appendChild(box);
    scrollDown();
    return box;
  }

  function setBusy(value) {
    busy = value;
    sendBtn.disabled = value;
  }

  function errorText(status, data) {
    if (status === 400) return 'Sorry, I could not process that message.';
    if ((status === 502 || status === 503) && data && typeof data.error === 'string' && data.error.length < 300) {
      return data.error; // our server's own friendly message, e.g. "The assistant is busy right now..."
    }
    return 'Something went wrong. Please try again.';
  }

  function send(text) {
    text = (text || '').trim();
    if (!text || busy) return;
    if (chipsBox && chipsBox.parentNode) chipsBox.parentNode.removeChild(chipsBox);
    addMessage('user', text);
    input.value = '';
    autoGrow();
    setBusy(true);
    var typing = addTyping();
    emit('query_sent', { length: text.length });

    var body = { message: text };
    if (sessionId) body.sessionId = sessionId;
    var controller = new AbortController();
    var timer = setTimeout(function () { controller.abort(); }, REQUEST_TIMEOUT_MS);

    fetch(cfg.apiUrl, {
      method: 'POST',
      headers: { 'Content-Type': 'application/json' },
      body: JSON.stringify(body),
      signal: controller.signal
    }).then(function (response) {
      return response.json().catch(function () { return {}; }).then(function (data) {
        return { ok: response.ok, status: response.status, data: data };
      });
    }).then(function (res) {
      typing.remove();
      if (res.ok && res.data && typeof res.data.reply === 'string') {
        if (typeof res.data.sessionId === 'string') { sessionId = res.data.sessionId; store('set', sessionId); }
        addMessage('assistant', res.data.reply);
        emit('response_received', {});
      } else {
        addMessage('error', errorText(res.status, res.data));
      }
    }).catch(function (err) {
      typing.remove();
      addMessage('error', err && err.name === 'AbortError'
        ? 'This is taking too long. Please try again.'
        : 'I could not reach the assistant. Check your connection and try again.');
    }).then(function () {
      clearTimeout(timer);
      setBusy(false);
      input.focus();
    });
  }

  if (document.body) build();
  else document.addEventListener('DOMContentLoaded', build);
})();
