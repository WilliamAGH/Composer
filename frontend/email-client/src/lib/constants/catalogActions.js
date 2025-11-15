/**
 * Canonical catalog action defaults consumed by the toolbar and mailbox automation surfaces.
 * Centralizing the metadata keeps App.svelte light and guarantees future folder/move actions
 * reuse the same source of truth.
 */
export const MAILBOX_ACTION_FALLBACKS = [
  {
    key: 'smart-triage',
    label: 'Smart triage & cleanup',
    description:
      'Auto-label Action, Read-Later, FYI, Receipts, Calendar, Tasks, Bulk. Merges duplicates and collapses promos.',
    comingSoon: true
  }
];

/**
 * Default quick actions to display when AI suggestions are still loading or absent.
 */
export const ACTION_IDEAS_INSTRUCTION =
  'List three creative but practical follow-up ideas for this email. Each idea must be one sentence and drive the thread toward a clear next step.';

export const DEFAULT_ACTION_OPTIONS = [
  {
    id: 'action-create-task',
    label: 'Create Task',
    actionType: 'comingSoon',
    commandKey: null,
    commandVariant: null,
    instruction: null
  },
  {
    id: 'action-remind-me',
    label: 'Remind Me About This',
    actionType: 'comingSoon',
    commandKey: null,
    commandVariant: null,
    instruction: null
  },
  {
    id: 'action-give-ideas',
    label: 'Give me Ideas',
    actionType: 'summary',
    commandKey: 'summarize',
    commandVariant: null,
    instruction: ACTION_IDEAS_INSTRUCTION
  }
];

/**
 * Prioritization order for toolbar catalog commands so high-value actions surface first.
 */
export const PRIMARY_TOOLBAR_PREFERENCE = ['draft', 'translate'];

/**
 * Catalog command invoked by the “Action ideas” panel.
 */
export const ACTION_MENU_COMMAND_KEY = 'actions_menu';
