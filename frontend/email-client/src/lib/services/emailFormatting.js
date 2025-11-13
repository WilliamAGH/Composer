/**
 * Escapes HTML using the server-provided helper when available.
 */
export function escapeHtmlContent(value) {
  if (window.Composer?.escapeHtml) {
    return window.Composer.escapeHtml(value || '');
  }
  return value || '';
}

/**
 * Renders markdown content into HTML using the bootstrap helper.
 */
export function renderMarkdownContent(markdown) {
  if (window.Composer?.renderMarkdown) {
    return window.Composer.renderMarkdown(markdown || '');
  }
  return markdown || '';
}

const relativeTimeFormatter = new Intl.RelativeTimeFormat('en', { numeric: 'auto' });

function parseDate(value) {
  if (!value) return null;
  const date = new Date(value);
  return Number.isNaN(date.getTime()) ? null : date;
}

/**
 * Returns a friendly “x minutes ago” string or falls back to the provided label.
 */
export function formatRelativeTimestamp(primary, fallback) {
  const date = parseDate(primary) || parseDate(fallback);
  if (!date) return escapeHtmlContent(fallback || '');
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
export function formatFullTimestamp(primary, fallback) {
  const date = parseDate(primary) || parseDate(fallback);
  if (!date) return escapeHtmlContent(fallback || '');
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
