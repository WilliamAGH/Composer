import type { FrontendEmailMessage } from './emailUtils';
import { buildEmailContextString } from './emailContextConstructor';
import { normalizeReplySubject, normalizeForwardSubject } from './emailSubjectPrefixHandler';

/**
 * Builds compose payload defaults for reply windows while keeping App.svelte lean.
 * Returned body values always leave blank space above the quoted context so greetings land naturally.
 */
export function buildReplyPrefill(email: FrontendEmailMessage | null | undefined) {
  const subject = email?.subject ? normalizeReplySubject(email.subject) : '';
  const quotedContext = quoteEmailContext(email);
  const body = quotedContext ? `\n\n${quotedContext}` : '';
  return {
    subject,
    body,
    quotedContext,
    hasQuotedContext: Boolean(quotedContext)
  };
}

/**
 * Builds compose payload defaults for forward windows.
 * Includes a distinct forwarded header before the quoted metadata/body.
 */
export function buildForwardPrefill(email: FrontendEmailMessage | null | undefined) {
  const subject = email?.subject ? normalizeForwardSubject(email.subject) : '';
  const quotedContext = quoteEmailContext(email, true);
  const body = quotedContext ? `\n\n${quotedContext}` : '';
  return {
    subject,
    body,
    quotedContext,
    hasQuotedContext: Boolean(quotedContext)
  };
}

/**
 * Serializes an email into the canonical metadata/body context block.
 * Optionally prefixes a forwarded header line.
 */
export function quoteEmailContext(email: FrontendEmailMessage | null | undefined, includeHeaders = false) {
  if (!email) return '';
  const context = buildEmailContextString(email);
  const pieces = [];
  if (includeHeaders) {
    pieces.push('Forwarded message:');
    const header = formatForwardHeader(email);
    if (header) {
      pieces.push(header);
    }
  } else {
    pieces.push('Previous message:');
  }
  if (context) {
    pieces.push(context);
  }
  return pieces.join('\n').trim();
}

function formatForwardHeader(email: FrontendEmailMessage | null | undefined) {
  if (!email) return '';
  const rows = [];
  if (email.senderName || email.senderEmail) {
    rows.push(`From: ${email.senderName || ''}${email.senderEmail ? ` <${email.senderEmail}>` : ''}`.trim());
  }
  if (email.recipientName || email.recipientEmail) {
    rows.push(`To: ${email.recipientName || ''}${email.recipientEmail ? ` <${email.recipientEmail}>` : ''}`.trim());
  }
  if (email.timestamp) {
    rows.push(`Sent: ${email.timestamp}`);
  } else if (email.timestampIso) {
    rows.push(`Sent: ${email.timestampIso}`);
  }
  if (email.subject) {
    rows.push(`Subject: ${email.subject}`);
  }
  return rows.join('\n');
}
