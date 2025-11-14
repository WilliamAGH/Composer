/**
 * Shared email helpers extracted to keep App.svelte lean and reusable across components.
 *
 * ⚠️ WARNING: DO NOT ADD NEW FUNCTIONS TO THIS FILE
 * This file has a generic name ("emailUtils") which violates our naming standards.
 * It exists for legacy reasons only and contains:
 *   - mapEmailMessage: Backend message → frontend email format
 *   - coalescePreview: Truncate email preview text
 *   - parseSubjectAndBody: Extract subject/body from text
 *   - computeMailboxCounts: Calculate folder counts
 *
 * For NEW functionality, create a specifically-named file that describes its purpose.
 * See AGENTS.md for guidance on avoiding generic "utils" files.
 */
export function mapEmailMessage(message, index = 0) {
  const preview = coalescePreview(message);
  return {
    id: message?.id || message?.contextId || `email-${index + 1}`,
    contextId: message?.contextId || null,
    from: message?.senderName || message?.senderEmail || 'Unknown sender',
    fromEmail: message?.senderEmail || '',
    senderName: message?.senderName || '',
    to: message?.recipientName || message?.recipientEmail || '',
    toEmail: message?.recipientEmail || '',
    recipientName: message?.recipientName || '',
    subject: message?.subject || 'No subject',
    preview,
    contentText: message?.emailBodyTransformedText || '',
    contentMarkdown: message?.emailBodyTransformedMarkdown || '',
    contentHtml: typeof message?.emailBodyHtml === 'string' && message.emailBodyHtml.trim().length > 0 ? message.emailBodyHtml : null,
    timestamp: message?.receivedTimestampDisplay || '',
    timestampIso: message?.receivedTimestampIso || null,
    read: Boolean(message?.read),
    starred: Boolean(message?.starred),
    avatar: message?.avatarUrl || message?.companyLogoUrl || '',
    labels: Array.isArray(message?.labels) ? message.labels : [],
    companyLogoUrl: message?.companyLogoUrl || null,
    contextForAi: message?.contextForAi || message?.contextForAI || null
  };
}

export function coalescePreview(message) {
  const text = typeof message?.emailBodyTransformedText === 'string' ? message.emailBodyTransformedText.trim() : '';
  if (!text) return '';
  const normalized = text.replace(/\s+/g, ' ');
  return normalized.length <= 180 ? normalized : `${normalized.slice(0, 177)}...`;
}

export function parseSubjectAndBody(text) {
  if (!text || !text.trim()) return { subject: '', body: '' };
  const trimmed = text.trim();
  const match = trimmed.match(/^Subject:\s*(.+?)$/m);
  if (match) {
    const subject = match[1].trim();
    const bodyStart = trimmed.indexOf(match[0]) + match[0].length;
    const body = trimmed.substring(bodyStart).replace(/^\s*\n+/, '').trim();
    return { subject, body };
  }
  return { subject: '', body: trimmed };
}

export function computeMailboxCounts(list) {
  const totals = { inbox: list.length, starred: 0, snoozed: 0, sent: 0, drafts: 0, archive: 0, trash: 0 };
  for (const email of list) {
    const labels = (email.labels || []).map((label) => String(label).toLowerCase());
    if (email.starred) totals.starred++;
    if (labels.includes('snoozed')) totals.snoozed++;
    if (labels.includes('sent')) totals.sent++;
    if (labels.includes('drafts') || labels.includes('draft')) totals.drafts++;
    if (labels.includes('archive') || labels.includes('archived')) totals.archive++;
    if (labels.includes('trash') || labels.includes('deleted')) totals.trash++;
  }
  return totals;
}
