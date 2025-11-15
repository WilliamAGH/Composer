/**
 * Subject prefix normalization for reply/forward flows.
 *
 * Goals:
 * - Prevent duplicate prefixes from accumulating (e.g., "Re: Re: Subject")
 * - Canonicalize variants to "Re:" and "Fwd:" (handles RE, re, FW, Fwd, etc. with optional colons/spaces)
 * - For Reply: ensure one "Re:" first; preserve a single "Fwd:" if present
 * - For Forward: ensure one "Fwd:" first; preserve a single "Re:" if present
 */

/** Build a normalized reply subject: one "Re:" first, keep one "Fwd:" if it existed. */
export function normalizeReplySubject(original: string | null | undefined) {
  return buildNormalizedSubject(original, 're');
}

/** Build a normalized forward subject: one "Fwd:" first, keep one "Re:" if it existed. */
export function normalizeForwardSubject(original: string | null | undefined) {
  return buildNormalizedSubject(original, 'fwd');
}

function buildNormalizedSubject(original: string | null | undefined, target: string) {
  const base = typeof original === 'string' ? original.trim() : '';
  if (!base) return '';
  const { tokens, subject } = extractLeadingPrefixes(base);
  const normalized = dedupe(tokens.map(normalizeToken));
  const targetNorm = normalizeToken(target);

  // Rebuild chain: target first, then other unique tokens (excluding target)
  const finalTokens = [targetNorm, ...normalized.filter((t) => t !== targetNorm)];
  return `${finalTokens.map(toLabel).join(' ')} ${subject}`.trim();
}

function extractLeadingPrefixes(text: string) {
  let s = text;
  const tokens: string[] = [];
  // Peel off leading prefixes like re, r, fw, fwd (case-insensitive),
  // with optional trailing dot, optional count in [] or (), and optional colon or hyphen.
  // Examples matched: "RE:", "Re :", "Re-", "Re[2]:", "Re (3) -", "FW:", "Fwd.", "Fw[10] :"
  const rx = /^(?:\s*)((re|r|fwd?|fw)\.?\s*(?:(?:\(|\[)\s*\d+\s*(?:\)|\]))?\s*[:-]?)\s*/i;
  while (true) {
    const m = s.match(rx);
    if (!m) break;
    // m[2] is the base token (re, r, fwd, fw)
    tokens.push(m[2]);
    s = s.slice(m[0].length);
  }
  // Trim any leading separators left behind (defensive)
  s = s.replace(/^(\s*[:-])+\s*/, '');
  return { tokens, subject: s.trim() };
}

function normalizeToken(t: string) {
  const v = String(t || '').toLowerCase();
  if (v === 're' || v === 'r') return 're';
  if (v === 'fw' || v === 'fwd') return 'fwd';
  return v;
}

function toLabel(t: string) {
  if (t === 're') return 'Re:';
  if (t === 'fwd') return 'Fwd:';
  return `${t}:`;
}

function dedupe(arr: string[]) {
  const out: string[] = [];
  const seen = new Set<string>();
  for (const x of arr) {
    if (!seen.has(x)) {
      seen.add(x);
      out.push(x);
    }
  }
  return out;
}
