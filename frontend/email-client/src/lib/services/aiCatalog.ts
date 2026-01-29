/**
 * Catalog utilities live in a JS module so the logic can be reused by multiple Svelte components/stores
 * without instantiating hidden components. Java holds the canonical catalog data, while this client-side
 * helper handles fetching/merging in the browser.
 */
import { writable, get, type Writable } from "svelte/store";
import type { AiFunctionCatalogDto, AiFunctionSummary, AiFunctionVariantSummary } from "../../main";
export type { AiFunctionCatalogDto, AiFunctionSummary, AiFunctionVariantSummary } from "../../main";
import { dispatchClientWarning } from "./sessionNonceClient";

const catalog: Writable<AiFunctionCatalogDto | null> = writable(null);
let fetchPromise: Promise<boolean> | null = null;

export function hydrateCatalog(initialValue?: AiFunctionCatalogDto | null) {
  if (initialValue) {
    catalog.set(initialValue);
  }
}

export function catalogStore() {
  return catalog;
}

export function getFunctionMeta(
  data: AiFunctionCatalogDto | null,
  key: string,
): AiFunctionSummary | null {
  if (!data || !key) return null;
  return data.functionsByKey?.[key] || null;
}

export function mergeDefaultArgs(
  meta: AiFunctionSummary | null,
  variant: AiFunctionVariantSummary | null,
  overrides: Record<string, string | null | undefined> = {},
) {
  const merged: Record<string, string> = {};
  if (meta?.defaultArgs) {
    Object.assign(merged, meta.defaultArgs);
  }
  if (variant?.defaultArgs) {
    Object.assign(merged, variant.defaultArgs);
  }
  Object.entries(overrides ?? {}).forEach(([k, v]) => {
    if (k && typeof v === "string" && v.length > 0) merged[k] = v;
  });
  return merged;
}

export function resolveDefaultInstruction(
  meta: AiFunctionSummary | null,
  variant: AiFunctionVariantSummary | null,
) {
  if (variant?.defaultInstruction) return variant.defaultInstruction;
  return meta?.defaultInstruction || "Assist with the selected email.";
}

export async function ensureCatalogLoaded(initialValue?: AiFunctionCatalogDto | null) {
  if (initialValue && !get(catalog)) {
    catalog.set(initialValue);
    return true;
  }
  if (get(catalog)) return true;
  if (!fetchPromise) {
    fetchPromise = fetch("/api/ai-functions", { headers: { Accept: "application/json" } })
      .then((resp) => {
        if (!resp.ok) throw new Error(`Failed to load catalog (HTTP ${resp.status})`);
        return resp.json() as Promise<AiFunctionCatalogDto>;
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
    dispatchClientWarning({ message: "Unable to load AI function catalog.", error });
    return false;
  }
}
