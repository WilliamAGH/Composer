/**
 * Catalog utilities live in a JS module so the logic can be reused by multiple Svelte components/stores
 * without instantiating hidden components. Java holds the canonical catalog data, while this client-side
 * helper handles fetching/merging in the browser.
 */
import { writable, get } from 'svelte/store';
import { dispatchClientWarning } from './sessionNonceClient';

const catalog = writable(null);
let fetchPromise = null;

export function hydrateCatalog(initialValue) {
  if (initialValue) {
    catalog.set(initialValue);
  }
}

export function catalogStore() {
  return catalog;
}

export function getFunctionMeta(data, key) {
  if (!data || !key) return null;
  return data.functionsByKey?.[key] || null;
}

export function mergeDefaultArgs(meta, variant, overrides = {}) {
  const merged = { ...meta?.defaultArgs };
  if (variant?.defaultArgs) {
    Object.assign(merged, variant.defaultArgs);
  }
  Object.entries(overrides ?? {}).forEach(([k, v]) => {
    if (k && v) merged[k] = v;
  });
  return merged;
}

export function resolveDefaultInstruction(meta, variant) {
  if (variant?.defaultInstruction) return variant.defaultInstruction;
  return meta?.defaultInstruction || 'Assist with the selected email.';
}

export async function ensureCatalogLoaded(initialValue) {
  if (initialValue && !get(catalog)) {
    catalog.set(initialValue);
    return true;
  }
  if (get(catalog)) return true;
  if (!fetchPromise) {
    fetchPromise = fetch('/api/ai-functions', { headers: { Accept: 'application/json' } })
      .then((resp) => {
        if (!resp.ok) throw new Error(`Failed to load catalog (HTTP ${resp.status})`);
        return resp.json();
      })
      .then((json) => {
        catalog.set(json);
        return true;
      })
      .finally(() => {
        fetchPromise = null;
      });
  }
  try {
    await fetchPromise;
    return true;
  } catch (error) {
    dispatchClientWarning({ message: 'Unable to load AI function catalog.', error });
    return false;
  }
}
