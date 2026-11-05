import { render, screen, waitFor } from '@testing-library/react';
import userEvent from '@testing-library/user-event';
import { afterEach, beforeEach, expect, test, vi } from 'vitest';
import { Widget } from '../Widget.jsx';

const config = { apiUrl: '/api/chat', title: 'Shopping Assistant' };
const SID = '11111111-1111-1111-1111-111111111111';

/** Each argument is one answer: { status, body }. */
function mockFetch(...answers) {
  const fn = vi.fn();
  answers.forEach((a) => fn.mockResolvedValueOnce({ ok: a.status < 400, status: a.status, json: async () => a.body }));
  vi.stubGlobal('fetch', fn);
  return fn;
}
const ok = (reply) => ({ status: 200, body: { reply, sessionId: SID } });
const sentBody = (fn, n) => JSON.parse(fn.mock.calls[n][1].body);

let events;
const listener = (e) => events.push(e.detail.type);
beforeEach(() => { events = []; window.addEventListener('shopping-assistant', listener); });
afterEach(() => { window.removeEventListener('shopping-assistant', listener); vi.unstubAllGlobals(); });

async function openAndGetBox(user) {
  await user.click(screen.getByRole('button', { name: 'Open Shopping Assistant' }));
  return screen.getByLabelText('Your message');
}
const panel = (container) => container.querySelector('.panel');

test('panel is closed at first, opens on click, and announces it', async () => {
  const user = userEvent.setup();
  const { container } = render(<Widget config={config} />);
  expect(panel(container).classList.contains('open')).toBe(false);
  await openAndGetBox(user);
  expect(panel(container).classList.contains('open')).toBe(true);
  expect(events).toEqual(['assistant_opened']);
});

test('shows a welcome message and three suggestion chips', async () => {
  render(<Widget config={config} />);
  expect(screen.getByText(/What are you looking for/)).toBeTruthy();
  expect(document.querySelectorAll('.chip').length).toBe(3);
});

test('sends on Enter, renders the Markdown answer, and removes the chips', async () => {
  const user = userEvent.setup();
  const fetchFn = mockFetch(ok('One TV is on offer:\n\n**Samsung TV**\n*   **Sale Price:** $764.99'));
  const { container } = render(<Widget config={config} />);
  const box = await openAndGetBox(user);

  await user.type(box, 'Show me TVs{Enter}');

  await screen.findByText('Samsung TV');
  expect(screen.getByText('Show me TVs')).toBeTruthy();
  expect(container.querySelectorAll('.msg.assistant li').length).toBe(1);
  expect(container.querySelectorAll('.chip').length).toBe(0);
  expect(sentBody(fetchFn, 0)).toEqual({ message: 'Show me TVs' }); // no sessionId on the first message
  expect(events).toContain('query_sent');
  expect(events).toContain('response_received');
});

test('a follow-up message carries the session id from the first answer', async () => {
  const user = userEvent.setup();
  const fetchFn = mockFetch(ok('first'), ok('second'));
  render(<Widget config={config} />);
  const box = await openAndGetBox(user);

  await user.type(box, 'one{Enter}');
  await screen.findByText('first');
  await user.type(box, 'two{Enter}');
  await screen.findByText('second');

  expect(sentBody(fetchFn, 1)).toEqual({ message: 'two', sessionId: SID });
});

test('the session id survives a reload (sessionStorage)', async () => {
  const user = userEvent.setup();
  const fetchFn = mockFetch(ok('first'), ok('after reload'));
  const first = render(<Widget config={config} />);
  const box = await openAndGetBox(user);
  await user.type(box, 'one{Enter}');
  await screen.findByText('first');
  first.unmount(); // like reloading the page

  render(<Widget config={config} />);
  await user.type(await openAndGetBox(user), 'again{Enter}');
  await screen.findByText('after reload');
  expect(sentBody(fetchFn, 1).sessionId).toBe(SID);
});

test('the send button is disabled while waiting, and a second send is ignored', async () => {
  const user = userEvent.setup();
  let finish;
  const fetchFn = vi.fn().mockReturnValueOnce(new Promise((resolve) => { finish = resolve; }));
  vi.stubGlobal('fetch', fetchFn);
  const { container } = render(<Widget config={config} />);
  const box = await openAndGetBox(user);

  await user.type(box, 'slow question{Enter}');
  expect(container.querySelector('.typing')).toBeTruthy();
  expect(container.querySelector('.send').disabled).toBe(true);
  await user.type(box, 'second{Enter}');
  expect(fetchFn).toHaveBeenCalledTimes(1);

  finish({ ok: true, status: 200, json: async () => ({ reply: 'done', sessionId: SID }) });
  await screen.findByText('done');
  expect(container.querySelector('.typing')).toBeNull();
  expect(container.querySelector('.send').disabled).toBe(false);
});

test('a 503 shows the server friendly message', async () => {
  const user = userEvent.setup();
  mockFetch({ status: 503, body: { error: 'The assistant is busy right now. Please try again in a moment.' } });
  render(<Widget config={config} />);
  await user.type(await openAndGetBox(user), 'hi{Enter}');
  expect(await screen.findByText(/busy right now/)).toBeTruthy();
});

test('a 400 shows a generic message, not "Bad Request"', async () => {
  const user = userEvent.setup();
  mockFetch({ status: 400, body: { error: 'Bad Request' } });
  render(<Widget config={config} />);
  await user.type(await openAndGetBox(user), 'hi{Enter}');
  expect(await screen.findByText(/could not process/)).toBeTruthy();
  expect(screen.queryByText('Bad Request')).toBeNull();
});

test('a network failure shows a connection message and re-enables sending', async () => {
  const user = userEvent.setup();
  vi.stubGlobal('fetch', vi.fn().mockRejectedValue(new TypeError('Failed to fetch')));
  const { container } = render(<Widget config={config} />);
  await user.type(await openAndGetBox(user), 'hi{Enter}');
  expect(await screen.findByText(/could not reach the assistant/)).toBeTruthy();
  expect(container.querySelector('.send').disabled).toBe(false);
});

test('a hostile reply is shown as text and nothing runs', async () => {
  const user = userEvent.setup();
  mockFetch(ok('<img src=x onerror="window.__xss=1"> [click](javascript:window.__xss=3)'));
  const { container } = render(<Widget config={config} />);
  await user.type(await openAndGetBox(user), 'attack{Enter}');
  await screen.findByText(/<img src=x/);
  expect(container.querySelectorAll('.msg img, .msg script, .msg a').length).toBe(0);
  expect(window.__xss).toBeUndefined();
});

test('New chat clears the messages and starts without a session id', async () => {
  const user = userEvent.setup();
  const fetchFn = mockFetch(ok('first'), ok('fresh'));
  const { container } = render(<Widget config={config} />);
  const box = await openAndGetBox(user);
  await user.type(box, 'one{Enter}');
  await screen.findByText('first');

  await user.click(screen.getByRole('button', { name: 'New chat' }));
  expect(container.querySelectorAll('.msg.user').length).toBe(0);
  expect(container.querySelectorAll('.chip').length).toBe(3);
  expect(events).toContain('new_chat');

  await user.type(box, 'two{Enter}');
  await screen.findByText('fresh');
  expect(sentBody(fetchFn, 1)).toEqual({ message: 'two' });
});

test('clicking a suggestion chip sends it', async () => {
  const user = userEvent.setup();
  const fetchFn = mockFetch(ok('here you go'));
  render(<Widget config={config} />);
  await user.click(screen.getByRole('button', { name: 'TVs on offer' }));
  await screen.findByText('here you go');
  expect(sentBody(fetchFn, 0).message).toBe('TVs on offer');
});

test('Shift+Enter adds a new line instead of sending', async () => {
  const user = userEvent.setup();
  const fetchFn = mockFetch();
  render(<Widget config={config} />);
  const box = await openAndGetBox(user);
  await user.type(box, 'line1{Shift>}{Enter}{/Shift}line2');
  expect(box.value).toBe('line1\nline2');
  expect(fetchFn).not.toHaveBeenCalled();
});

test('Escape closes the panel', async () => {
  const user = userEvent.setup();
  const { container } = render(<Widget config={config} />);
  await openAndGetBox(user);
  await user.keyboard('{Escape}');
  await waitFor(() => expect(panel(container).classList.contains('open')).toBe(false));
});
