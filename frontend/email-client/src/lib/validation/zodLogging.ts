/* eslint-disable no-console */
/**
 * Zod error logging with full context extraction.
 * ZodError objects collapse to `{}` in browser consoles - this extracts actionable details.
 *
 * CRITICAL: Every validation failure MUST include a record identifier for debugging.
 *
 * @see docs/type-safety-zod-validation.md
 */

import { z } from "zod/v4";

/**
 * Extracts and logs human-readable Zod validation failure details.
 *
 * @param context - Identifier including WHICH record failed (e.g., "parseEmailMessage [msg-123]")
 * @param error - The error to log (handles ZodError specially, logs others as-is)
 * @param payload - The raw payload that failed validation (for context)
 */
export function logZodFailure(context: string, error: unknown, payload?: unknown): void {
  const payloadKeys = extractPayloadKeys(payload);

  if (error instanceof z.ZodError) {
    const issueSummaries = formatZodIssues(error);

    console.error(
      `[Zod] ${context} validation failed\n` +
        `Issues:\n${issueSummaries.join("\n")}\n` +
        `Payload keys: ${payloadKeys.join(", ") || "(none)"}`,
    );

    console.error(`[Zod] ${context} - full details:`, {
      prettifiedError: z.prettifyError(error),
      issues: error.issues,
      payload,
    });
  } else {
    console.error(`[Zod] ${context} validation failed (non-ZodError):`, error);
  }
}

/**
 * Formats Zod issues into human-readable strings with path, message, expected, and received values.
 */
function formatZodIssues(error: z.ZodError): string[] {
  const maxIssues = 10;
  return error.issues.slice(0, maxIssues).map((issue) => {
    const path = issue.path.length > 0 ? issue.path.join(".") : "(root)";

    // Zod v4: 'input' contains the failing value, 'received' for type errors
    const inputValue = "input" in issue ? issue.input : undefined;
    const receivedValue = "received" in issue ? issue.received : undefined;
    const actualValue = receivedValue ?? inputValue;

    const received = actualValue !== undefined ? ` (received: ${JSON.stringify(actualValue)})` : "";
    const expected = "expected" in issue ? ` (expected: ${issue.expected})` : "";

    return `  - ${path}: ${issue.message}${expected}${received}`;
  });
}

/**
 * Extracts top-level keys from payload for context logging.
 */
function extractPayloadKeys(payload: unknown): string[] {
  if (typeof payload !== "object" || payload === null) {
    return [];
  }
  return Object.keys(payload).slice(0, 20);
}

/**
 * Creates a scoped logger that prefixes all messages with a record identifier.
 * Use this when validating multiple records in a loop.
 *
 * @example
 * const logger = createScopedZodLogger('parseEmailList');
 * for (const raw of rawEmails) {
 *   const result = EmailMessageSchema.safeParse(raw);
 *   if (!result.success) {
 *     logger.failure(raw.id ?? 'unknown', result.error, raw);
 *   }
 * }
 */
export function createScopedZodLogger(baseContext: string) {
  return {
    failure(recordId: string, error: z.ZodError, payload?: unknown): void {
      logZodFailure(`${baseContext} [${recordId}]`, error, payload);
    },
  };
}
