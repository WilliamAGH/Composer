/**
 * Type-safe parsing utilities that NEVER swallow errors.
 * All validation failures are logged with full context before returning.
 *
 * @see docs/type-safety-zod-validation.md
 */

import { z } from 'zod/v4';
import type { ValidationResult } from './result';
import { validationSuccess, validationFailure } from './result';
import { logZodFailure, createScopedZodLogger } from './zodLogging';

/**
 * Validates unknown data against a Zod schema with mandatory error logging.
 *
 * FORBIDDEN alternatives:
 * - schema.parse() - throws, crashes rendering
 * - schema.safeParse().data ?? default - swallows errors
 * - try { parse() } catch { return null } - hides failures
 *
 * @param schema - Zod schema to validate against
 * @param raw - Unknown data to validate (external API response, user input, etc.)
 * @param recordId - REQUIRED identifier for debugging (message ID, slug, URL, etc.)
 * @returns Discriminated union: { success: true, data } or { success: false, error }
 */
export function validateWithLogging<T>(
  schema: z.ZodType<T>,
  raw: unknown,
  recordId: string
): ValidationResult<T> {
  const result = schema.safeParse(raw);

  if (!result.success) {
    logZodFailure(`validateWithLogging [${recordId}]`, result.error, raw);
    return validationFailure(result.error);
  }

  return validationSuccess(result.data);
}

/**
 * Validates an array of items, collecting successes and logging each failure.
 * Use when parsing a list where some items may be malformed but others should proceed.
 *
 * @param schema - Zod schema for individual items
 * @param rawItems - Array of unknown items to validate
 * @param context - Base context for logging (e.g., "parseEmailList")
 * @param extractId - Function to extract record ID from raw item for logging
 * @returns Object with validated items and count of failures
 */
export function validateArrayWithLogging<T>(
  schema: z.ZodType<T>,
  rawItems: unknown[],
  context: string,
  extractId: (item: unknown) => string
): { validItems: T[]; failureCount: number } {
  const logger = createScopedZodLogger(context);
  const validItems: T[] = [];
  let failureCount = 0;

  for (const rawItem of rawItems) {
    const recordId = extractId(rawItem);
    const result = schema.safeParse(rawItem);

    if (result.success) {
      validItems.push(result.data);
    } else {
      logger.failure(recordId, result.error, rawItem);
      failureCount++;
    }
  }

  return { validItems, failureCount };
}

/**
 * Validates and unwraps data, throwing a descriptive error on failure.
 * Use ONLY at application boundaries where throwing is appropriate (e.g., bootstrap).
 *
 * Unlike raw parse(), this logs the full error context before throwing.
 *
 * @param schema - Zod schema to validate against
 * @param raw - Unknown data to validate
 * @param recordId - REQUIRED identifier for debugging
 * @throws Error with descriptive message if validation fails
 */
export function validateOrThrow<T>(
  schema: z.ZodType<T>,
  raw: unknown,
  recordId: string
): T {
  const result = validateWithLogging(schema, raw, recordId);

  if (!result.success) {
    const issueCount = result.error.issues.length;
    throw new Error(
      `Validation failed for ${recordId}: ${issueCount} issue(s). See console for details.`
    );
  }

  return result.data;
}
