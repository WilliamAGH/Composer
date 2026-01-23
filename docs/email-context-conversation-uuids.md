# Email Context & Conversation Identifiers

```text
┌─────────────────────────────────────────────────────────────────────┐
│ 1. User clicks an email row                                         │
└──────────────┬──────────────────────────────────────────────────────┘
               │ selection event
               v
┌─────────────────────────────────────────────────────────────────────┐
│ App.svelte (UI coordinator)                                         │
│ • Reads `selected` from `mailboxLayoutStore`                        │
│ • Asks `conversationLedger.resolveKey(scope, target, contextId)`    │
└──────────────┬──────────────────────────────────────────────────────┘
               │ conversation key (contextId || email.id || windowId)
               v
┌─────────────────────────────────────────────────────────────────────┐
│ conversationLedger (lib/services/conversationLedger.ts)             │
│ • Returns stored `conversationId` for the key                       │
│ • Falls back to null (backend will mint UUID)                       │
└──────────────┬──────────────────────────────────────────────────────┘
               │ `conversationId` (may be null)
               v
┌─────────────────────────────────────────────────────────────────────┐
│ callAiCommand (App.svelte)                                          │
│ • Payload includes:                                                 │
│   - `contextId` (true contextId or derived key)                     │
│   - `emailContext` (markdown fallback when no contextId)            │
│   - `conversationId`                                                │
│ • POST → `/api/catalog-commands/{commandKey}/execute`               │
└──────────────┬──────────────────────────────────────────────────────┘
               │ HTTP (JSON body)
               v
┌─────────────────────────────────────────────────────────────────────┐
│ CatalogCommandController → ChatService                              │
│ • ChatService resolves context via EmailContextRegistry when        │
│   `contextId` is known                                              │
│ • Otherwise it consumes `emailContext` payload                      │
│ • Ensures/returns `conversationId` via `StringUtils.ensureConversationId`│
└──────────────┬──────────────────────────────────────────────────────┘
               │ ChatResponse (conversationId, sanitizedHtml, etc.)
               v
┌─────────────────────────────────────────────────────────────────────┐
│ App.svelte                                                          │
│ • Stores returned conversationId via `conversationLedger.write(key)`│
│ • Dispatches panel updates through `aiPanelStore`                   │
└─────────────────────────────────────────────────────────────────────┘
```

## Identifier Reference

| Identifier        | Purpose                                                   | Source of Truth                                             | Where Set / Used |
|-------------------|-----------------------------------------------------------|--------------------------------------------------------------|------------------|
| `email.id`        | UI-visible mailbox row identifier.                        | `EmailMessage.id` (backend) or synthetic draft IDs.         | `mailboxLayoutStore`, selection + move events. |
| `email.contextId` | Registry key for sanitized markdown context.              | `EmailParsingService` stores markdown in `EmailContextRegistry`. | Sent in `callAiCommand` when present; retrieved by `ChatService`. |
| `conversationId`  | LLM chat history thread ID.                               | `ChatService` via `StringUtils.ensureConversationId`.        | Stored per key in `conversationLedger`; sent on every AI call. |
| Conversation Key  | Stable client key for ledger lookups (panel/compose/global). | Derived in `conversationLedger`: panel → `contextId/id`, compose → window id, global → literal `__global__`. | Keeps per-email/windows conversation IDs isolated. |
| `emailContext`    | Raw markdown fallback when no registry entry exists.      | Built in `buildEmailContextString` (App.svelte) or compose payload. | Sent only when `contextId` missing; backend consumes directly. |

## Recipient metadata in `ChatRequest`

`ChatRequest` supports optional `recipientName` and `recipientEmail`. The UI should populate these when an AI action is initiated from a compose surface so the backend can:

- Personalize salutations when appropriate (and stay generic when metadata is missing).
- Infer friendly names from email addresses when needed.
- Emit explicit prompt instructions (for example: avoid names/signatures, or keep greetings intact) while still keeping templates declarative.

If you are building a new client integration, send the best-known recipient metadata alongside compose/draft/tone commands so greeting behavior stays consistent with the main UI.

## How We Handle Each Identifier

1. **Email IDs**: `mailboxLayoutStore` normalizes every message through `mapEmailMessage`, guaranteeing `id` exists. Drafts get deterministic ids (`draft-${uuid}`) so they can participate in the same flows.

2. **Context IDs**: Real emails parsed server-side carry a `contextId`. When present, `callAiCommand` simply forwards it; ChatService calls `EmailContextRegistry.contextForAi(contextId)` to fetch the markdown and never needs the raw body. Compose windows mint `draft-${uuid}` context IDs the first time an AI helper runs, upload the current draft markdown via `/api/catalog-commands/draft-context`, and reuse that identifier until the draft changes.

3. **Context fallback**: Not every UI-generated message has a backend context. Instead of rejecting those payloads, we now (a) drop the old validator requirement in `ChatRequest`, (b) send `emailContext` markdown, and (c) set `contextId` to the conversation key so the backend still receives a stable identifier. Draft uploads use the exact same registry, so tone/composition commands always resolve to the latest draft body rather than the original inbound email.

4. **Conversation IDs**: The backend is authoritative—every call goes through `StringUtils.ensureConversationId(request.getConversationId())`. The frontend’s `conversationLedger` retains whatever value comes back for that specific key so that summarizing the same email again stays in the same conversation while other emails start fresh threads.

5. **Panel/session state**: `aiPanelStore` consumes `conversationLedger` keys for caching responses and errors per email. The store never invents identifiers; it relies on the same key that the ledger uses, which ultimately resolves back to `email.contextId`/`email.id`.

## Files to Consult

- `frontend/email-client/src/lib/services/conversationLedger.ts` – explains conversation-key derivation and storage.
- `frontend/email-client/src/App.svelte` – shows how the ledger, `callAiCommand`, and `aiPanelStore` interact.
- `src/main/java/com/composerai/api/service/ChatService.java` – authoritative handling of context IDs, conversation IDs, and registry lookups.
- `src/main/java/com/composerai/api/service/ContextBuilder.EmailContextRegistry` – stores and retrieves the markdown associated with server-issued context IDs.
- `src/main/java/com/composerai/api/dto/ChatRequest.java` – documents payload fields and validation constraints.

Keeping these contracts synchronized ensures every AI request carries the right trio: **email identifier**, **context identifier**, and **conversation identifier**. If a message ever lacks one of them, the fallback rules above guarantee the backend still receives enough information to run the catalog command deterministically.

For a concrete example of how these identifiers feed the forthcoming conversation ledger, see `docs/reference-conversation-ledger.md`.

### Conversation Ledger Structure

The ledger stores each mailbox/AI interaction as a `ConversationEnvelope` (`com.composerai.api.shared.ledger`) with:

- **Metadata**: conversation id, created timestamp, version, high-level journey scope.
- **Events**: ordered `ConversationEvent` entries containing the raw system/user/assistant text plus optional payloads:
  - `LlmCallPayload` – serialized OpenAI Java SDK request/response JSON (`com.openai.models.chat.completions.*` or `com.openai.models.responses.*`) and token usage stats.
  - `ToolCallPayload` – raw OpenAI `tool_calls` element plus the executed request/response for our internal tools.
  - `ContextRef` records – pointers back to email/context IDs described in this doc so resolvers can fetch the exact markdown injected into the SDK request.
- **Email snapshots**: minimal `EmailObject` entries referencing the existing `EmailMessage` so replay tooling can reconstruct the markdown that was sent upstream.

The JSON example explicitly shows how `contextId`, `conversationId`, and email bodies surface in the OpenAI payloads via the ledger.
