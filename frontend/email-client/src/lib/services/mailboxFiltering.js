/**
 * Returns true when the message belongs in the currently selected mailbox bucket.
 */
export function matchesMailbox(mailbox, email, folderResolver) {
  const normalizedMailbox = `${mailbox || 'inbox'}`.toLowerCase();
  const labels = (email.labels || []).map((label) => String(label).toLowerCase());
  const folderIdRaw = typeof folderResolver === 'function' ? folderResolver(email) : 'inbox';
  const folderId = `${folderIdRaw || 'inbox'}`.toLowerCase();
  switch (normalizedMailbox) {
    case 'inbox':
      return folderId === 'inbox';
    case 'starred':
      return Boolean(email.starred);
    case 'snoozed':
      return labels.includes('snoozed');
    case 'sent':
      return folderId === 'sent';
    case 'drafts':
      return folderId === 'drafts';
    case 'archive':
      return folderId === 'archive';
    case 'trash':
      return folderId === 'trash';
    default:
      return folderId === normalizedMailbox;
  }
}

/**
 * Applies the active mailbox filter first, then performs a simple substring search across subject/from/preview.
 */
export function filterEmailsByMailbox(emails, mailbox, searchQuery, folderResolver) {
  const base = emails.filter((email) => matchesMailbox(mailbox, email, folderResolver));
  if (!searchQuery || !searchQuery.trim()) {
    return base;
  }
  const needle = searchQuery.toLowerCase();
  return base.filter((email) => {
    const haystack = [email.subject, email.from, email.preview].join(' ').toLowerCase();
    return haystack.includes(needle);
  });
}
