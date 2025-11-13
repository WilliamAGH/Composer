<script>
  /**
   * Shared dropdown for all folder move actions (toolbar + list rows). Keeps icons/labels consistent.
   */
  import { createEventDispatcher } from 'svelte';
  import { Inbox, Archive, Send, FileText, Trash2, Loader2, Check } from 'lucide-svelte';
  import { MAILBOX_MOVE_TARGETS, resolveVisibleTargets } from './constants/mailboxMoveTargets';

  export let currentFolderId = 'inbox';
  export let pending = false;
  export let hideIds = [];
  export let customTargets = MAILBOX_MOVE_TARGETS;

  const dispatch = createEventDispatcher();
  const iconMap = { Inbox, Archive, Send, FileText, Trash2 };

  $: visibleTargets = resolveVisibleTargets(currentFolderId)
    .filter((target) => !hideIds.includes(target.id))
    .map((target) => customTargets.find((entry) => entry.id === target.id) || target);

  function handleSelect(targetId) {
    dispatch('select', { targetId });
  }
</script>

<div class="move-menu" role="menu">
  {#if pending}
    <div class="move-menu__status">
      <Loader2 class="h-4 w-4 animate-spin" aria-hidden="true" />
      <span>Updating…</span>
    </div>
  {/if}
  <ul>
    {#each visibleTargets as target (target.id)}
      <li>
        <button
          type="button"
          class={`move-menu__item ${target.destructive ? 'move-menu__item--destructive' : ''}`}
          role="menuitem"
          aria-current={currentFolderId === target.id}
          disabled={currentFolderId === target.id || pending}
          on:click={() => handleSelect(target.id)}>
          <span class="move-menu__icon">
            <svelte:component this={iconMap[target.icon] || Inbox} class="h-4 w-4" aria-hidden="true" />
          </span>
          <span class="move-menu__label">{target.label}</span>
          {#if currentFolderId === target.id}
            <Check class="h-4 w-4 text-slate-500" aria-hidden="true" />
          {/if}
        </button>
      </li>
    {/each}
  </ul>
</div>

<style>
  /* Base menu container */
  .move-menu {
    min-width: 12rem;
  }
  /* Inline status row for pending state */
  .move-menu__status {
    display: flex;
    align-items: center;
    gap: 0.4rem;
    font-size: 0.75rem;
    color: #475569;
    padding: 0.35rem 0.75rem;
  }
  /* Stack options vertically */
  .move-menu ul {
    display: flex;
    flex-direction: column;
    gap: 0.15rem;
  }
  /* Base button styles */
  .move-menu__item {
    width: 100%;
    display: flex;
    align-items: center;
    justify-content: space-between;
    gap: 0.65rem;
    padding: 0.5rem 0.75rem;
    border-radius: 0.45rem;
    background: transparent;
    border: 0;
    text-align: left;
    font-size: 0.85rem;
    color: #0f172a;
    transition: background 0.15s ease, color 0.15s ease;
  }
  .move-menu__item:disabled {
    opacity: 0.6;
    cursor: default;
  }
  .move-menu__item:focus-visible {
    background: rgba(148, 163, 184, 0.15);
  }
  .move-menu__item:hover:not(:disabled) {
    background: rgba(148, 163, 184, 0.15);
  }
  /* Highlight destructive options */
  .move-menu__item--destructive {
    color: #b91c1c;
  }
  /* Icon alignment */
  .move-menu__icon {
    display: inline-flex;
    align-items: center;
    justify-content: center;
    color: #475569;
  }
  .move-menu__label {
    flex: 1;
  }
</style>
