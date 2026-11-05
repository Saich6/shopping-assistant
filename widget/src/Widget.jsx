import { useEffect, useLayoutEffect, useRef, useState } from 'react';
import css from './widget.css?inline';
import { MAX_MESSAGE_LENGTH } from './api.js';
import { emit } from './analytics.js';
import { Markdown } from './markdown.jsx';
import { useChat } from './useChat.js';

const SUGGESTIONS = ['TVs on offer', 'Olive oil options', 'Headphones under $300'];

function Message({ kind, text }) {
  return (
    <div className={`msg ${kind}`}>
      {/* Assistant text is Markdown; user text and errors are plain text. */}
      {kind === 'assistant' ? <Markdown text={text} /> : text}
    </div>
  );
}

export function Widget({ config }) {
  const { messages, busy, send, reset } = useChat(config.apiUrl);
  const [open, setOpen] = useState(false);
  const [draft, setDraft] = useState('');
  const inputRef = useRef(null);
  const launcherRef = useRef(null);
  const listRef = useRef(null);

  // Keep the newest message in view.
  useEffect(() => {
    const list = listRef.current;
    if (list) list.scrollTop = list.scrollHeight;
  }, [messages, busy, open]);

  // Focus the message box when the panel opens and after each answer arrives.
  useEffect(() => {
    if (open && !busy && inputRef.current) inputRef.current.focus();
  }, [open, busy]);

  // Grow the message box with its content (up to a limit).
  useLayoutEffect(() => {
    const box = inputRef.current;
    if (!box) return;
    box.style.height = 'auto';
    box.style.height = `${Math.min(box.scrollHeight, 110)}px`;
  }, [draft]);

  function toggle(next) {
    setOpen(next);
    if (next) emit('assistant_opened', {});
  }

  function submit(text) {
    if (!text.trim() || busy) return;
    setDraft('');
    send(text);
  }

  const showChips = messages.length === 1 && !busy; // only the welcome message so far

  return (
    <>
      <style>{css}</style>
      <div className="sa">
        <button
          ref={launcherRef}
          type="button"
          className="launcher"
          aria-label={`Open ${config.title}`}
          aria-expanded={open}
          onClick={() => toggle(!open)}
        >
          <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round" aria-hidden="true">
            <path d="M21 15a2 2 0 0 1-2 2H8l-5 4V5a2 2 0 0 1 2-2h14a2 2 0 0 1 2 2z" />
          </svg>
        </button>

        <section
          className={`panel${open ? ' open' : ''}`}
          role="dialog"
          aria-label={config.title}
          onKeyDown={(e) => {
            if (e.key === 'Escape') { toggle(false); launcherRef.current.focus(); }
          }}
        >
          <div className="header">
            <div className="title">{config.title}</div>
            <button type="button" className="hbtn" onClick={() => { reset(); setDraft(''); inputRef.current.focus(); }}>
              New chat
            </button>
            <button type="button" className="hbtn" aria-label={`Close ${config.title}`}
              onClick={() => { toggle(false); launcherRef.current.focus(); }}>
              {'✕'}
            </button>
          </div>

          <div className="messages" role="log" aria-live="polite" ref={listRef}>
            {messages.map((m) => <Message key={m.id} kind={m.kind} text={m.text} />)}
            {showChips && (
              <div className="chips">
                {SUGGESTIONS.map((s) => (
                  <button key={s} type="button" className="chip" onClick={() => submit(s)}>{s}</button>
                ))}
              </div>
            )}
            {busy && (
              <div className="msg assistant typing" aria-label="The assistant is typing">
                <span className="dot" /><span className="dot" /><span className="dot" />
              </div>
            )}
          </div>

          <form className="form" onSubmit={(e) => { e.preventDefault(); submit(draft); }}>
            <textarea
              ref={inputRef}
              rows={1}
              maxLength={MAX_MESSAGE_LENGTH}
              placeholder={'Ask about products or offers…'}
              aria-label="Your message"
              value={draft}
              onChange={(e) => setDraft(e.target.value)}
              onKeyDown={(e) => {
                if (e.key === 'Enter' && !e.shiftKey) { e.preventDefault(); submit(draft); }
              }}
            />
            <button type="submit" className="send" disabled={busy}>Send</button>
          </form>
          <div className="note">AI assistant. Results from other stores are third-party listings.</div>
        </section>
      </div>
    </>
  );
}
