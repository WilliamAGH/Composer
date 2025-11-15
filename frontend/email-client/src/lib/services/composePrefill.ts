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
  if (!context) return '';
  const lines = [];
  lines.push(includeHeaders ? '--- Forwarded message ---' : '--- Previous message ---');
  lines.push(context);
  return lines.join('\n');
}
