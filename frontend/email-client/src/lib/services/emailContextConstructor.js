const EMAIL_REGEX = /^[^\s@]+@[^\s@]+\.[^\s@]+$/;

export function formatRecipientDisplay(name, email) {
  const safeName = (name || '').trim();
  const safeEmail = (email || '').trim();
  if (safeName && safeEmail) {
    return `${safeName} <${safeEmail}>`;
  }
  return safeName || safeEmail || '';
}

export function parseRecipientInput(value) {
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

export function recipientFromEmail(email) {
  if (!email) {
    return { name: '', email: '' };
  }
  const name = (email.from || email.senderName || '').trim();
  const address = (email.fromEmail || email.senderEmail || '').trim();
  return { name, email: address };
}

export function normalizeRecipient(recipient = {}) {
  const name = typeof recipient.name === 'string' ? recipient.name.trim() : '';
  const email = typeof recipient.email === 'string' ? recipient.email.trim() : '';
  return { name, email };
}

export function deriveRecipientContext({ toInput, composePayload, fallbackEmail } = {}) {
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

function formatParticipant(name, email, fallback) {
  const safeName = (name || '').trim();
  const safeEmail = (email || '').trim();
  const label = safeName || fallback;
  if (safeEmail) {
    return `${label} <${safeEmail}>`;
  }
  return label;
}

export function buildEmailContextString(email) {
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
