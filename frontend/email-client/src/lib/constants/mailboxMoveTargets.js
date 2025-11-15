/**
 * Canonical definition of all move targets. UI surfaces import this list to render consistent labels/icons.
 */
export const MAILBOX_MOVE_TARGETS = Object.freeze([
  { id: 'inbox', label: 'Move to Inbox', icon: 'Inbox' },
  { id: 'archive', label: 'Archive', icon: 'Archive' },
  { id: 'sent', label: 'Move to Sent', icon: 'Send' },
  { id: 'drafts', label: 'Move to Drafts', icon: 'FileText' },
  { id: 'trash', label: 'Delete', icon: 'Trash2', destructive: true }
]);

/**
 * Filters move targets based on current folder so we never display a redundant option (e.g., Archive while already in Archive).
 */
export function resolveVisibleTargets(currentFolderId) {
  return MAILBOX_MOVE_TARGETS.filter((target) => {
    if (!currentFolderId) return true;
    if (currentFolderId === 'archive' && target.id === 'archive') return false;
    if (currentFolderId === 'drafts' && target.id === 'drafts') return false;
    if (currentFolderId === 'sent' && target.id === 'sent') return false;
    if (currentFolderId === 'trash' && target.id === 'trash') return false;
    return true;
  });
}

export function isDestructiveTarget(targetId) {
  return targetId === 'trash';
}
