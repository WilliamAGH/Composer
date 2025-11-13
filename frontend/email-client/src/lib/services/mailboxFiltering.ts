type EmailMessage = Record<string, any>;

/**
 * Returns true when the message belongs in the currently selected mailbox bucket.
 */
export function matchesMailbox(mailbox: string, email: EmailMessage): boolean {
  const labels = (email.labels || []).map((label) => String(label).toLowerCase());
  switch (mailbox) {
    case 'inbox':
      return true;
    case 'starred':
      return Boolean(email.starred);
    case 'snoozed':
      return labels.includes('snoozed');
    case 'sent':
      return labels.includes('sent');
    case 'drafts':
      return labels.includes('drafts') || labels.includes('draft');
    case 'archive':
      return labels.includes('archive') || labels.includes('archived');
    case 'trash':
      return labels.includes('trash') || labels.includes('deleted');
    default:
      return true;
  }
}

/**
 * Applies the active mailbox filter first, then performs a simple substring search across subject/from/preview.
 */
export function filterEmailsByMailbox(
  emails: EmailMessage[],
  mailbox: string,
  searchQuery: string
): EmailMessage[] {
  const base = emails.filter((email) => matchesMailbox(mailbox, email));
  if (!searchQuery.trim()) {
    return base;
  }
  const needle = searchQuery.toLowerCase();
  return base.filter((email) => {
    const haystack = [email.subject, email.from, email.preview].join(' ').toLowerCase();
    return haystack.includes(needle);
  });
}
