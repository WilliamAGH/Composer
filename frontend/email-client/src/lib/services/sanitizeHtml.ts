const DEFAULT_ALLOWED_TAGS = new Set([
  'a',
  'abbr',
  'b',
  'blockquote',
  'br',
  'code',
  'div',
  'em',
  'h1',
  'h2',
  'h3',
  'h4',
  'h5',
  'h6',
  'hr',
  'i',
  'img',
  'li',
  'ol',
  'p',
  'pre',
  'span',
  'strong',
  'table',
  'tbody',
  'td',
  'th',
  'thead',
  'tr',
  'u',
  'ul'
]);

const GLOBAL_ATTRS = new Set(['title', 'aria-label', 'role', 'tabindex', 'dir']);
const URI_ATTRS = new Set(['href', 'src']);
const SAFE_PROTOCOLS = ['http:', 'https:', 'mailto:', 'tel:'];

const ATTRS_PER_TAG: Record<string, string[]> = {
  a: ['href', 'target', 'rel'],
  img: ['src', 'alt', 'title', 'width', 'height'],
  table: ['border', 'cellpadding', 'cellspacing'],
  td: ['colspan', 'rowspan'],
  th: ['colspan', 'rowspan']
};

/**
 * Sanitizes potentially unsafe HTML before it is injected via {@html}.
 * Uses the browser DOM to strip disallowed tags/attributes and neutralize JS URLs.
 * Falls back to entity-escaping when DOM APIs are unavailable (SSR).
 */
export function sanitizeHtml(input: string | null | undefined): string {
  if (!input || typeof input !== 'string') {
    return '';
  }

  if (typeof window === 'undefined' || typeof DOMParser === 'undefined') {
    return escapeHtml(input);
  }

  const parser = new DOMParser();
  const doc = parser.parseFromString(input, 'text/html');
  const body = doc.body;
  sanitizeNode(body);
  return body.innerHTML;
}

function sanitizeNode(node: Node) {
  // Create a snapshot of childNodes before iterating to avoid issues when
  // mutating the DOM (unwrapping/removing nodes) during iteration
  const children = Array.from(node.childNodes);
  for (const child of children) {
    if (child.nodeType === Node.ELEMENT_NODE) {
      const element = child as HTMLElement;
      const tag = element.tagName.toLowerCase();
      if (!DEFAULT_ALLOWED_TAGS.has(tag)) {
        // Sanitize descendants before unwrapping so no unsafe nodes survive
        sanitizeNode(element);
        unwrapElement(element);
        continue;
      }
      sanitizeAttributes(element, tag);
      sanitizeNode(element);
    } else if (child.nodeType === Node.COMMENT_NODE) {
      child.parentNode?.removeChild(child);
    }
  }
}

function sanitizeAttributes(element: HTMLElement, tag: string) {
  const allowedAttrs = new Set([...GLOBAL_ATTRS, ...(ATTRS_PER_TAG[tag] || [])]);
  for (const attr of Array.from(element.attributes)) {
    const attrName = attr.name.toLowerCase();
    if (!allowedAttrs.has(attrName) && !attrName.startsWith('data-')) {
      element.removeAttribute(attr.name);
      continue;
    }

    if (URI_ATTRS.has(attrName)) {
      if (!isSafeUri(attr.value)) {
        element.removeAttribute(attr.name);
        continue;
      }
    }

    if (attrName === 'target') {
      element.setAttribute('rel', 'noopener noreferrer');
    }
  }
}

function isSafeUri(value: string) {
  const trimmed = value.trim();
  if (trimmed.startsWith('#')) {
    return true;
  }
  try {
    const url = new URL(trimmed, 'http://localhost');
    return SAFE_PROTOCOLS.includes(url.protocol);
  } catch {
    return false;
  }
}

function unwrapElement(element: HTMLElement) {
  const parent = element.parentNode;
  if (!parent) {
    element.remove();
    return;
  }
  while (element.firstChild) {
    parent.insertBefore(element.firstChild, element);
  }
  parent.removeChild(element);
}

function escapeHtml(input: string) {
  return input
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#039;');
}

