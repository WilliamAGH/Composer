/**
 * Discriminated union result type for validation operations.
 * NEVER return null from validation - always return { success, data/error }.
 *
 * @see docs/type-safety-zod-validation.md
 */

import { z } from 'zod/v4';

/**
 * Success case: validated data is available.
 */
export interface ValidationSuccess<T> {
  readonly success: true;
  readonly data: T;
}

/**
 * Failure case: ZodError with full diagnostic information.
 */
export interface ValidationFailure {
  readonly success: false;
  readonly error: z.ZodError;
}

/**
 * Discriminated union - callers MUST check `success` before accessing `data`.
 */
export type ValidationResult<T> = ValidationSuccess<T> | ValidationFailure;

/**
 * Creates a success result with validated data.
 */
export function validationSuccess<T>(data: T): ValidationSuccess<T> {
  return { success: true, data };
}

/**
 * Creates a failure result with the ZodError.
 */
export function validationFailure(error: z.ZodError): ValidationFailure {
  return { success: false, error };
}

/**
 * Type guard to narrow ValidationResult to success case.
 */
export function isValidationSuccess<T>(
  result: ValidationResult<T>
): result is ValidationSuccess<T> {
  return result.success === true;
}

/**
 * Type guard to narrow ValidationResult to failure case.
 */
export function isValidationFailure<T>(
  result: ValidationResult<T>
): result is ValidationFailure {
  return result.success === false;
}
