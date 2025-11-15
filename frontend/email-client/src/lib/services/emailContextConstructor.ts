import type { FrontendEmailMessage } from './emailUtils';

const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export type Recipient = { name: string; email: string };

type RecipientSource = {
  from?: string | null;
  fromEmail?: string | null;
  to?: string | null;
  toEmail?: string | null;
  contentMarkdown?: string | null;
  contentText?: string | null;
  timestamp?: string | null;
  timestampIso?: string | null;
};

type EmailContextSource = Partial<FrontendEmailMessage> & RecipientSource & {
  subject?: string | null;
  timestamp?: string | null;
  timestampIso?: string | null;
  contentMarkdown?: string | null;
  contentText?: string | null;
  senderName?: string | null;
  senderEmail?: string | null;
  recipientName?: string | null;
  recipientEmail?: string | null;
};

export function formatRecipientDisplay(name: string | null | undefined, email: string | null | undefined) {
  const safeName = (name || '').trim();
  const safeEmail = (email || '').trim();
  if (safeName && safeEmail) {
    return `${safeName} <${safeEmail}>`;
  }
  return safeName || safeEmail || '';
}

export function parseRecipientInput(value: string | null | undefined): Recipient {
  const trimmed = (value || '').trim();
  if (!trimmed) {
    return { name: '', email: '' };
  }
  const bracketMatch = trimmed.match(/^(.*?)\s*<([^>]+)>$/);
  if (bracketMatch) {
    const name = (bracketMatch[1] || '').trim();
    const email = (bracketMatch[2] || '').trim();
    return { name, email };
  }
  if (EMAIL_REGEX.test(trimmed)) {
    return { name: '', email: trimmed };
  }
  return { name: trimmed, email: '' };
}

export function recipientFromEmail(email: EmailContextSource | null | undefined): Recipient {
  if (!email) {
    return { name: '', email: '' };
  }
  const name = (email.senderName || '').trim();
  const address = (email.senderEmail || '').trim();
  return { name, email: address };
}

export function normalizeRecipient(recipient: Partial<Recipient> | null | undefined = {}): Recipient {
  const source = recipient ?? {};
  const name = typeof source.name === 'string' ? source.name.trim() : '';
  const email = typeof source.email === 'string' ? source.email.trim() : '';
  return { name, email };
}

export function deriveRecipientContext({
  toInput,
  composePayload,
  fallbackEmail
}: {
  toInput?: string | null;
  composePayload?: {
    recipientName?: string | null;
    recipientEmail?: string | null;
    toEmail?: string | null;
    to?: string | null;
  };
  fallbackEmail?: EmailContextSource | null;
} = {}): Recipient {
  const fromInput = normalizeRecipient(parseRecipientInput(toInput));
  if (fromInput.name || fromInput.email) {
    return fromInput;
  }

  if (composePayload) {
    const directPayload = normalizeRecipient({
      name: composePayload.recipientName ?? '',
      email: composePayload.recipientEmail ?? composePayload.toEmail ?? ''
    });
    if (directPayload.name || directPayload.email) {
      return directPayload;
    }
    if (composePayload.to) {
      const parsedTo = normalizeRecipient(parseRecipientInput(composePayload.to));
      if (parsedTo.name || parsedTo.email) {
        return parsedTo;
      }
    }
  }

  if (fallbackEmail) {
    const fallbackRecipient = normalizeRecipient(recipientFromEmail(fallbackEmail));
    if (fallbackRecipient.name || fallbackRecipient.email) {
      return fallbackRecipient;
    }
  }

  return { name: '', email: '' };
}

function formatParticipant(name: string | null | undefined, email: string | null | undefined, fallback: string) {
  const safeName = (name || '').trim();
  const safeEmail = (email || '').trim();
  const label = safeName || fallback;
  if (safeEmail) {
    return `${label} <${safeEmail}>`;
  }
  return label;
}

export function buildEmailContextString(email: EmailContextSource | null | undefined) {
  if (!email) return '';
  const markdown = typeof email.contentMarkdown === 'string' ? email.contentMarkdown.trim() : '';
  if (markdown) {
    return sanitizeQuotedContextBlock(markdown);
  }
  const lines = [];
  lines.push('=== Email Metadata ===');
  lines.push(`Subject: ${email.subject || 'No subject'}`);
  lines.push(`From: ${formatParticipant(email.senderName, email.senderEmail, 'Unknown sender')}`);
  lines.push(`To: ${formatParticipant(email.recipientName, email.recipientEmail, 'Unknown recipient')}`);
  if (email.timestamp) {
    lines.push(`Email sent on: ${email.timestamp}`);
  }
  if (email.timestampIso) {
    lines.push(`Email sent (ISO): ${email.timestampIso}`);
  }
  lines.push('');
  lines.push('=== Email Body ===');
  const body = typeof email.contentText === 'string' ? email.contentText.trim() : '';
  const sanitizedBody = sanitizeQuotedContextBlock(body);
  lines.push(sanitizedBody || '(Email body is empty)');
  return lines.join('\n').trim();
}

function sanitizeQuotedContextBlock(input: string | null | undefined) {
  if (!input) return '';
  let working = input
    .replace(/\r\n/g, '\n')
    // Drop anchor fragments appended to markdown links
    .replace(/\{#[^}\s]+\}/g, '');

  // Normalize markdown links; if text == href, keep a single href, otherwise keep "text (href)"
  working = working.replace(/\[([^\]]+)]\((\S+?)(?:\s+"[^"]*")?\)/g, (m, text, href) => {
    if (text && href && text.trim() === href.trim()) {
      return href;
    }
    return `${text} (${href})`;
  });

  // Remove markdown escapes for common punctuation
  working = working.replace(/\\([\\`*_[\]{}()#+.!>])/g, '$1');

  const lines = working.split(/\n/);
  const kept: string[] = [];
  let blankRun = 0;
  for (const raw of lines) {
    const trimmed = raw.replace(/^\uFEFF/, '').trim();
    if (isDividerLine(trimmed)) {
      continue;
    }
    if (trimmed.length === 0) {
      blankRun += 1;
      if (blankRun > 2) continue;
      kept.push('');
      continue;
    }
    blankRun = 0;
    kept.push(raw);
  }
  return kept.join('\n').replace(/\n{3,}/g, '\n\n').trim();
}

function isDividerLine(line: string) {
  if (!line) return false;
  const withoutQuote = line.replace(/^>+\s*/, '');
  const normalized = withoutQuote
    .replace(/\s+/g, '')
    .replace(/[\u200B-\u200D\uFEFF]/g, '');
  if (normalized.length >= 3 && /^[-*_]+$/.test(normalized)) return true;
  return false;
}
