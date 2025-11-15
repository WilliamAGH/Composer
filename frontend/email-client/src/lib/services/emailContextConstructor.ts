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

type EmailContextSource = RecipientSource & {
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
  const name = (email.from || email.senderName || '').trim();
  const address = (email.fromEmail || email.senderEmail || '').trim();
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
      name: composePayload.recipientName,
      email: composePayload.recipientEmail || composePayload.toEmail
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
    return markdown;
  }
  const lines = [];
  lines.push('=== Email Metadata ===');
  lines.push(`Subject: ${email.subject || 'No subject'}`);
  lines.push(`From: ${formatParticipant(email.from, email.fromEmail, 'Unknown sender')}`);
  lines.push(`To: ${formatParticipant(email.to, email.toEmail, 'Unknown recipient')}`);
  if (email.timestamp) {
    lines.push(`Email sent on: ${email.timestamp}`);
  }
  if (email.timestampIso) {
    lines.push(`Email sent (ISO): ${email.timestampIso}`);
  }
  lines.push('');
  lines.push('=== Email Body ===');
  const body = typeof email.contentText === 'string' ? email.contentText.trim() : '';
  lines.push(body || '(Email body is empty)');
  return lines.join('\n');
}
