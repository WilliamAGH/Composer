# Type Safety & Zod Validation Guide

Runtime validation for API responses using Zod v4. This ensures external data is validated before use, with full error context when validation fails.

## Core Principles

1. **External data is `unknown` until validated** - Never trust API responses
2. **Never swallow errors** - Every validation failure is logged with full context
3. **Discriminated unions, not null** - Return `{ success: true, data }` or `{ success: false, error }`
4. **Record identification is mandatory** - Every error log identifies WHICH record failed

## Import Pattern

```typescript
import { z } from 'zod/v4';
```

## Architecture

### Validation Infrastructure (`src/lib/validation/`)

- `result.ts` - Discriminated union result types
- `zodLogging.ts` - Error logging with full context extraction
- `validatedParse.ts` - Type-safe parsing utilities

### Domain Schemas (`src/lib/schemas/`)

- `emailSchemas.ts` - EmailMessage schema
- `mailboxSchemas.ts` - Mailbox state/move result schemas
- `catalogSchemas.ts` - AI catalog response schemas
- `bootstrapSchemas.ts` - Page bootstrap data schema

**No barrel files** - Import directly from each schema file per `[AB1a]`.

## Validation Pattern

### The Result Type

```typescript
type ValidationResult<T> = 
  | { success: true; data: T } 
  | { success: false; error: z.ZodError };
```

### Correct Usage

```typescript
import { getJsonValidated } from '../services/sessionNonceClient';
import { MailboxStateSnapshotSchema } from '../schemas/mailboxSchemas';

const result = await getJsonValidated(
  url,
  MailboxStateSnapshotSchema,
  `mailbox-state:${mailboxId}`  // Record identifier for debugging
);

if (!result.success) {
  // Validation failure already logged with full context
  return null;
}

// result.data is now properly typed
const snapshot = result.data;
```

### FORBIDDEN Patterns

```typescript
// FORBIDDEN: parse() throws and crashes rendering
const data = schema.parse(raw);

// FORBIDDEN: silent fallback swallows errors
const data = schema.safeParse(raw).data ?? defaultValue;

// FORBIDDEN: empty catch hides failures
try { schema.parse(raw); } catch { return null; }

// FORBIDDEN: no record identifier
logZodFailure('parseResponse', error, raw);  // Which record??

// FORBIDDEN: unsafe type assertions
const data = response as MyType;
```

## Schema Design

### Optional vs Nullable

```typescript
// optional() - field may be OMITTED (undefined), but null fails
field: z.string().optional(),     // ✓ undefined, ✗ null

// nullable() - field accepts null, but MUST be present
field: z.string().nullable(),     // ✓ null, ✗ omitted

// nullish() - field may be omitted OR be null
field: z.string().nullish(),      // ✓ undefined, ✓ null
```

**Rule**: Match the API contract exactly. Check real API responses before writing schemas.

### Deriving Types from Schemas

```typescript
// Schema is the source of truth
export const EmailMessageSchema = z.object({
  id: z.string(),
  subject: z.string(),
  // ...
});

// Type is derived - never duplicated
export type EmailMessage = z.infer<typeof EmailMessageSchema>;
```

## Error Logging

When validation fails, the console shows:

```text
[Zod] parseMailboxState [primary] validation failed
Issues:
  - messages.0.labels: Expected array, received null
  - folderCounts.inbox: Expected number, received string
Payload keys: mailboxId, messages, folderCounts, effectiveFolders
```

This tells you:
- **Which record**: `primary` mailbox
- **Which field**: `messages.0.labels` (first message's labels)
- **What was expected**: `array`
- **What was received**: `null`

## Adding New Schemas

1. Create schema in appropriate file under `src/lib/schemas/`
2. Export both schema and inferred type
3. Add `extractRecordId` helper if validating arrays
4. Use `getJsonValidated` or `postJsonValidated` in API client
5. Handle `ValidationResult` at call site

## Checklist Before Shipping

- [ ] All external data validated with `safeParse()` (never `parse()`)
- [ ] Validation failures return discriminated union `{ success, data/error }`
- [ ] Error logs include record identifier (ID, slug, URL, etc.)
- [ ] Schema optional/nullable matches actual API contract
- [ ] No `as` type assertions for external data
- [ ] No silent fallbacks (`?? defaultValue` after safeParse)
- [ ] No barrel files - direct imports only

## Zod v4 Built-in Utilities

```typescript
// Error formatting
z.prettifyError(error)    // Human-readable multi-line string
z.flattenError(error)     // Flat object for form field errors

// String format validators
z.email()                 // Validates email format
z.uuid()                  // Validates UUID format
z.url()                   // Validates URL format
z.iso.datetime()          // Validates ISO datetime strings

// Coercion (with explicit error handling)
const numResult = z.coerce.number().safeParse(input);
if (!numResult.success) {
  // Handle coercion failure - don't ignore it
}
```
