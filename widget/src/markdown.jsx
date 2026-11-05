import { emit } from './analytics.js';

// The model answers in Markdown. We turn it into React elements. React escapes all text, and we never use
// dangerouslySetInnerHTML, so nothing the model (or a web page it read) writes can run as HTML or script.
// Only http(s) links can match, so javascript: and data: links never become clickable.
const INLINE = /\*\*(.+?)\*\*|\[([^\]]+)\]\((https?:\/\/[^\s)]+)\)|(https?:\/\/[^\s<>)]+)/g;

function Link({ url, label }) {
  return (
    <a href={url} target="_blank" rel="noopener noreferrer" onClick={() => emit('link_click', { url })}>
      {label}
    </a>
  );
}

function renderInline(text, keyPrefix) {
  const nodes = [];
  const re = new RegExp(INLINE.source, 'g');
  let last = 0;
  let m;
  let i = 0;
  while ((m = re.exec(text))) {
    if (m.index > last) nodes.push(text.slice(last, m.index));
    const key = `${keyPrefix}-${i++}`;
    if (m[1] !== undefined) {
      nodes.push(<strong key={key}>{m[1]}</strong>);
    } else if (m[3]) {
      nodes.push(<Link key={key} url={m[3]} label={m[2]} />);
    } else {
      // bare URL: keep trailing punctuation ("see https://x.com/a.") out of the link
      const trailing = /[.,;:!?]+$/.exec(m[4]);
      const url = trailing ? m[4].slice(0, -trailing[0].length) : m[4];
      nodes.push(<Link key={key} url={url} label={url} />);
      if (trailing) nodes.push(trailing[0]);
    }
    last = re.lastIndex;
  }
  if (last < text.length) nodes.push(text.slice(last));
  return nodes;
}

/** Groups lines into paragraphs, headings and lists, then renders them. */
export function Markdown({ text }) {
  const blocks = [];
  let para = null;
  let list = null;
  const endPara = () => { para = null; };
  const endList = () => { list = null; };

  String(text).replace(/\r\n/g, '\n').split('\n').forEach((raw) => {
    const bullet = /^\s*[*-]\s+(.*)$/.exec(raw);
    const numbered = /^\s*\d+[.)]\s+(.*)$/.exec(raw);
    const heading = /^\s*#{1,6}\s+(.*)$/.exec(raw);
    if (bullet || numbered) {
      endPara();
      const type = bullet ? 'ul' : 'ol';
      if (!list || list.type !== type) { list = { type, items: [] }; blocks.push(list); }
      list.items.push((bullet || numbered)[1]);
    } else if (!raw.trim()) {
      endPara(); endList();
    } else if (heading) {
      endPara(); endList();
      blocks.push({ type: 'heading', text: heading[1] });
    } else {
      endList();
      if (!para) { para = { type: 'p', lines: [] }; blocks.push(para); }
      para.lines.push(raw.trim());
    }
  });

  return blocks.map((b, i) => {
    if (b.type === 'heading') return <p key={i} className="sa-heading"><strong>{b.text}</strong></p>;
    if (b.type === 'p') {
      return (
        <p key={i}>
          {b.lines.map((line, j) => (
            <span key={j}>{j > 0 && <br />}{renderInline(line, `${i}-${j}`)}</span>
          ))}
        </p>
      );
    }
    const Tag = b.type;
    return <Tag key={i}>{b.items.map((item, j) => <li key={j}>{renderInline(item, `${i}-${j}`)}</li>)}</Tag>;
  });
}
