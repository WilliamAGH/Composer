/**
 * Escapes HTML using the server-provided helper when available.
 * Falls back to local escaping to prevent XSS when the helper is unavailable.
 */
const isNil = (value: unknown): value is null | undefined => value === null || value === undefined;

declare global {
  interface Window {
    Composer?: {
      escapeHtml?: (value: string) => string;
      renderMarkdown?: (value: string) => string;
    };
  }
}

export function escapeHtmlContent(value: string | null | undefined) {
  const safeValue = isNil(value) ? '' : String(value);
  if (window.Composer?.escapeHtml) {
    return window.Composer.escapeHtml(safeValue);
  }
  // Fallback escaping to prevent XSS
  return safeValue
    .replace(/&/g, '&amp;')
    .replace(/</g, '&lt;')
    .replace(/>/g, '&gt;')
    .replace(/"/g, '&quot;')
    .replace(/'/g, '&#39;');
}

/**
 * Renders markdown content into HTML using the bootstrap helper.
 * Falls back to safe HTML escaping to prevent XSS when the helper is unavailable.
 */
export function renderMarkdownContent(markdown: string | null | undefined) {
  const safeMarkdown = isNil(markdown) ? '' : String(markdown);
  if (window.Composer?.renderMarkdown) {
    return window.Composer.renderMarkdown(safeMarkdown);
  }
  // Fallback: escape HTML to prevent XSS when {@html} is used with this output
  return escapeHtmlContent(safeMarkdown);
}

const relativeTimeFormatter = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });

function parseDate(value: string | Date | null | undefined) {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

/**
 * Returns a friendly “x minutes ago” string or falls back to the provided label.
 */
export function formatRelativeTimestamp(primary?: string | Date | null, fallback?: string | Date | null) {
  const date = parseDate(primary) || parseDate(fallback);
  if (!date) return escapeHtmlContent(fallback ? String(fallback) : '');
  const now = new Date();
  const diffMs = date.getTime() - now.getTime();
  const minute = 60 * 1000;
  const hour = 60 * minute;
  const day = 24 * hour;
  const month = 30 * day;
  const year = 365 * day;
  const absMs = Math.abs(diffMs);
  if (absMs < minute) return 'just now';
  if (absMs < hour) return relativeTimeFormatter.format(Math.round(diffMs / minute), 'minute');
  if (absMs < day) return relativeTimeFormatter.format(Math.round(diffMs / hour), 'hour');
  if (absMs < month) return relativeTimeFormatter.format(Math.round(diffMs / day), 'day');
  if (absMs < year) return relativeTimeFormatter.format(Math.round(diffMs / month), 'month');
  return relativeTimeFormatter.format(Math.round(diffMs / year), 'year');
}

/**
 * Formats a full timestamp for the email detail header.
 */
export function formatFullTimestamp(primary?: string | Date | null, fallback?: string | Date | null) {
  const date = parseDate(primary) || parseDate(fallback);
  if (!date) return escapeHtmlContent(fallback ? String(fallback) : '');
  return date.toLocaleString(undefined, {
    year: 'numeric',
    month: 'short',
    day: '2-digit',
    hour: 'numeric',
    minute: '2-digit',
    hour12: true,
    timeZoneName: 'short'
  });
}
