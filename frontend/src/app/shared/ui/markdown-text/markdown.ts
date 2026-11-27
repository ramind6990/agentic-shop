/**
 * A small markdown parser for chat text: paragraphs (single line breaks kept), `-` / `*` / `+` bullet lists, `1.`
 * numbered lists, `#` headings, `**bold**`, `*italic*` / `_italic_`, `` `code` `` and `[text](https://…)` links.
 * It returns a tree that templates render with normal bindings (never innerHTML), so the text can never inject
 * markup. Anything it does not understand stays plain text.
 */

export type MdInline =
  | { kind: 'text'; text: string }
  | { kind: 'strong'; children: MdInline[] }
  | { kind: 'em'; children: MdInline[] }
  | { kind: 'code'; text: string }
  | { kind: 'link'; href: string; children: MdInline[] };

export type MdBlock =
  | { kind: 'paragraph'; lines: MdInline[][] }
  | { kind: 'heading'; content: MdInline[] }
  | { kind: 'bullets'; items: MdInline[][] }
  | { kind: 'numbers'; start: number; items: MdInline[][] };

const HEADING = /^\s{0,3}#{1,6}\s+(.*?)\s*#*\s*$/;
const BULLET = /^\s*[-*+]\s+(.*)$/;
const NUMBER = /^\s*(\d{1,9})[.)]\s+(.*)$/;
const LINK_TARGET = /^(https?:\/\/|mailto:)/i;

/**
 * `code`, **strong**, __strong__, *em*, _em_ (underscores only at word edges, so snake_case stays text) and
 * [text](url). Emphasis needs non-space characters right inside its markers.
 */
const INLINE =
  /`([^`\n]+)`|\*\*(?=\S)([\s\S]*?\S)\*\*|(?<![\w])__(?=\S)([\s\S]*?\S)__(?![\w])|\*(?=[^\s*])([^*\n]*?[^\s*])\*|(?<![\w])_(?=[^\s_])([^_\n]*?[^\s_])_(?![\w])|\[([^\]\n]+)\]\(([^)\s]+)\)/g;

export function parseMarkdown(source: string): MdBlock[] {
  const blocks: MdBlock[] = [];
  let paragraph: string[] = [];
  let list: { ordered: boolean; start: number; items: string[] } | null = null;

  const flushParagraph = () => {
    if (paragraph.length) blocks.push({ kind: 'paragraph', lines: paragraph.map(parseInline) });
    paragraph = [];
  };
  const flushList = () => {
    if (list) {
      const items = list.items.map(parseInline);
      blocks.push(
        list.ordered ? { kind: 'numbers', start: list.start, items } : { kind: 'bullets', items },
      );
    }
    list = null;
  };

  for (const line of source.replace(/\r\n?/g, '\n').split('\n')) {
    const heading = HEADING.exec(line);
    const bullet = BULLET.exec(line);
    const number = NUMBER.exec(line);
    if (!line.trim()) {
      flushParagraph();
      flushList();
    } else if (heading) {
      flushParagraph();
      flushList();
      blocks.push({ kind: 'heading', content: parseInline(heading[1]) });
    } else if (bullet || number) {
      flushParagraph();
      const ordered = !bullet;
      if (list && list.ordered !== ordered) flushList();
      list ??= { ordered, start: number ? Number(number[1]) : 1, items: [] };
      list.items.push(bullet ? bullet[1] : number![2]);
    } else if (list && /^\s+\S/.test(line)) {
      // an indented line continues the last list item
      list.items[list.items.length - 1] += ' ' + line.trim();
    } else {
      flushList();
      paragraph.push(line.trim());
    }
  }
  flushParagraph();
  flushList();
  return blocks;
}

export function parseInline(text: string): MdInline[] {
  const out: MdInline[] = [];
  let last = 0;
  for (const m of text.matchAll(INLINE)) {
    const at = m.index ?? 0;
    if (at > last) out.push({ kind: 'text', text: text.slice(last, at) });
    out.push(toInline(m));
    last = at + m[0].length;
  }
  if (last < text.length) out.push({ kind: 'text', text: text.slice(last) });
  return out;
}

function toInline(m: RegExpMatchArray): MdInline {
  const [whole, code, strong, strongUnderscore, em, emUnderscore, linkText, href] = m;
  if (code !== undefined) return { kind: 'code', text: code };
  if (strong !== undefined || strongUnderscore !== undefined) {
    return { kind: 'strong', children: parseInline(strong ?? strongUnderscore) };
  }
  if (em !== undefined || emUnderscore !== undefined) {
    return { kind: 'em', children: parseInline(em ?? emUnderscore) };
  }
  if (linkText !== undefined && LINK_TARGET.test(href)) {
    return { kind: 'link', href, children: parseInline(linkText) };
  }
  return { kind: 'text', text: whole };
}
