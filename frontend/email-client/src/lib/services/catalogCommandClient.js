import { postJsonWithNonce } from './sessionNonceClient';

/**
 * Calls the backend catalog command endpoint so every UI surface reuses the same transport + retry logic.
 */
export async function executeCatalogCommand(commandKey, payload) {
  if (!commandKey) {
    throw new Error('commandKey is required');
  }
  return postJsonWithNonce(`/api/catalog-commands/${encodeURIComponent(commandKey)}/execute`, payload);
}
