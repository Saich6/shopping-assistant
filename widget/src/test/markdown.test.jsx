import { render } from '@testing-library/react';
import { expect, test } from 'vitest';
import { Markdown } from '../markdown.jsx';

const html = (text) => render(<Markdown text={text} />).container;

test('renders bold, bullets and paragraphs', () => {
  const c = html('Intro line\n\n**Samsung TV**\n*   **Price:** $899.99\n*   **Discount:** 15% off');
  expect(c.querySelectorAll('strong').length).toBe(3);
  expect(c.querySelectorAll('li').length).toBe(2);
  expect(c.querySelectorAll('p').length).toBe(2);
  expect(c.textContent).not.toContain('**');
});

test('renders numbered lists and headings', () => {
  const c = html('### Options\n1. First\n2) Second');
  expect(c.querySelector('p.sa-heading strong').textContent).toBe('Options');
  expect(c.querySelectorAll('ol li').length).toBe(2);
});

test('single newlines inside a paragraph become line breaks', () => {
  expect(html('line one\nline two').querySelectorAll('br').length).toBe(1);
});

test('markdown links and bare links open safely in a new tab', () => {
  const links = html('See [this listing](https://example.com/tv) or https://example.com/more.').querySelectorAll('a');
  expect(links.length).toBe(2);
  expect(links[0].getAttribute('href')).toBe('https://example.com/tv');
  expect(links[0].getAttribute('rel')).toBe('noopener noreferrer');
  expect(links[0].getAttribute('target')).toBe('_blank');
  expect(links[1].getAttribute('href')).toBe('https://example.com/more'); // trailing "." is not part of the link
});

test('javascript: and data: links never become links', () => {
  const c = html('[click](javascript:alert(1)) [data](data:text/html,hi)');
  expect(c.querySelectorAll('a').length).toBe(0);
});

test('HTML in the text is shown literally, never created as elements', () => {
  const c = html('<img src=x onerror="window.__xss=1"> <script>window.__xss=2</script> **<b>bold?</b>**');
  expect(c.querySelectorAll('img, script, b').length).toBe(0);
  expect(c.textContent).toContain('<img src=x');
  expect(window.__xss).toBeUndefined();
});

test('link clicks are announced as analytics events', () => {
  const seen = [];
  const listener = (e) => seen.push(e.detail);
  window.addEventListener('shopping-assistant', listener);
  html('[x](https://example.com)').querySelector('a').click();
  window.removeEventListener('shopping-assistant', listener);
  expect(seen).toEqual([{ type: 'link_click', url: 'https://example.com' }]);
});
