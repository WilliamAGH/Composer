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
  <div class="menu-list" role="menu">
    {#each visibleTargets as target (target.id)}
      <button
        type="button"
        class="menu-item"
        role="menuitem"
        aria-current={currentFolderId === target.id ? "true" : undefined}
        disabled={currentFolderId === target.id || pending}
        on:click={() => handleSelect(target.id)}>
        <span class="menu-item-icon">
          <svelte:component this={iconMap[target.icon] || Inbox} class="h-4 w-4" aria-hidden="true" />
        </span>
        <span class="move-menu__label" class:move-menu__label--destructive={target.destructive}>{target.label}</span>
        {#if currentFolderId === target.id}
          <Check class="h-4 w-4 text-slate-500" aria-hidden="true" />
        {/if}
      </button>
    {/each}
  </div>
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
  .move-menu__label {
    flex: 1;
    text-align: left;
  }

  .move-menu__label--destructive {
    color: #b91c1c;
  }
</style>
