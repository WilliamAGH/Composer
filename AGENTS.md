# Composer Agent Guide

Operational guidance for autonomous contributors extending Composer, an email AI web application with mailbox search via traditional queries and RAG through a unified chat interface.

## Rule Summary [SUM]

- [ZA1a-d] Zero Tolerance Policy (zero assumptions, validation, forbidden practices, dependency verification)
- [GT1a-j] Git & Permissions (escalate first, no destructive commands, no lock deletion)
- [CC1a-d] Clean Code & DDD (Mandatory)
- [ID1a-d] Idiomatic Patterns & Defaults
- [FS1a-j] File Creation & Type Safety (exhaustive search, typed records, no maps, clean architecture)
- [LOC1a-e] Line Count Ceiling (350 lines max; SRP enforcer; zero tolerance)
- [MO1a-g] No Monoliths (Strict SRP; Decision Logic; Extension/OCP)
- [ND1a-c] Naming Discipline (no generic names, intent-revealing identifiers)
- [AB1a-d] Abstraction Discipline (no anemic wrappers, abstractions earn reuse)
- [CS1a-h] Code Smells (primitive obsession, data clumps, feature envy, magic literals)
- [RC1a-d] Root Cause Resolution (no fallbacks, no shims/workarounds ever)
- [NO1a-d] Null/Optional Discipline (no null returns, empty collections, Optional restrictions)
- [RP1a-d] Refactor Playbook (one seam at a time, typed replacements)
- [AR1a-f] Architecture & Boundaries (canonical roots, layer rules, thin controllers)
- [TS1a-e] Testing Standards (coverage mandatory, observable behavior, refactor-resilient)
- [GT1a-d] Git & Permissions (escalate first, no destructive commands, no lock deletion)
- [TL1a-f] Tooling & Commands (make targets, tmp/ artifacts, npm-only, compliance checklist)
- [ZV1a-h] Zod Validation (discriminated unions, never swallow errors, record identification)

---

## [ZA1] Epistemic Humility (Zero Assumptions)

- [ZA1a] **Assume Blindness**: Your training data for APIs/versions is FALSE until verified.
- [ZA1b] **Scout Phase**: Before coding, use tools (`context7`, `perplexity`) and check local sources (`~/.m2`, `~/.gradle`, `node_modules`) to verify existence/signatures of APIs.
- [ZA1c] **Source Verification**: For dependency code questions, inspect `~/.m2` JARs or `~/.gradle/caches/` (backend) or `frontend/email-client/node_modules` (frontend) first; fallback to upstream GitHub; never answer without referencing code.
- [ZA1d] **Forbidden Practices**:
  - No `Map<String, Object>`, raw types, unchecked casts, `@SuppressWarnings`, or `eslint-disable` in production.
  - No trusting memory—verify every import/API/config against current docs.
- [ZA1e] **Mandatory Research**: You MUST research dependency questions and correct usage. Never use legacy or `@deprecated` usage from dependencies. Ensure correct usage by reviewing related code directly in `node_modules` or Gradle caches and using online tool calls.
- [ZA1f] **Dependency Search**: To search `node_modules` efficiently with `ast-grep`, target specific packages: `ast-grep run --pattern '...' node_modules/<package>`. Do NOT scan the entire `node_modules` folder.

## [CC1] Clean Code & DDD (Mandatory)

- [CC1a] **Mandatory Principles**: Clean Code principles (Robert C. Martin) and Domain-Driven Design (DDD) are **mandatory** and required in this repository.
- [CC1b] **DRY (Don't Repeat Yourself)**: Avoid redundant code. Reuse code where appropriate and consistent with clean code principles.
- [CC1c] **YAGNI (You Aren't Gonna Need It)**: Do not build features or abstractions "just in case". Implement only what is required for the current task.
- [CC1d] **Clean Architecture**: Dependencies point inward. Domain logic has zero framework imports.

## [ID1] Idiomatic Patterns & Defaults

- [ID1a] **Defaults First**: Always prefer the idiomatic, expected, and default patterns provided by the framework, library, or SDK (Spring Boot, Svelte, Tailwind, etc.).
- [ID1b] **Custom Justification**: Custom implementations require a compelling reason. If you can't justify it, use the standard way.
- [ID1c] **No Reinventing**: Do not build custom utilities for things the platform already does.
- [ID1d] **Dependencies**: Make careful use of dependencies. Do not make assumptions—use the correct idiomatic behavior to avoid boilerplate.

## [DS1] Dependency Source Verification

- [DS1a] **Locate**: Find source JARs in Gradle cache: `find ~/.gradle/caches/modules-2/files-2.1 -name "*-sources.jar" | grep <artifact>`.
- [DS1b] **List**: View JAR contents without extraction: `unzip -l <jar_path> | grep <ClassName>`.
- [DS1c] **Read**: Pipe specific file content to stdout: `unzip -p <jar_path> <internal/path/to/Class.java>`.
- [DS1d] **Search**: To use `ast-grep` on dependencies, pipe content directly: `unzip -p <jar> <file> | ast-grep run --pattern '...' --lang java --stdin`. No temp files required.
- [DS1e] **Efficiency**: Do not extract full JARs. Use CLI piping for instant access.

## [FS1] File Creation & Type Safety

- [FS1a] Before any new file: search exhaustively for existing logic → if found, reuse/extend → if not found, request explicit permission, then create in canonical locations.
- [FS1b] No `Map<String, Object>`, raw types, unchecked casts, `@SuppressWarnings`, `@ts-ignore`, or `eslint-disable` in production code. Fix root causes with typed records/value objects.
- [FS1c] If a cast is unavoidable, guard with explicit conversions (e.g., `Number::intValue`) instead of suppressing.
- [FS1d] Single-responsibility methods; no dead code; no empty try/catch that swallows exceptions.
- [FS1e] Domain has zero framework imports; dependencies point inward.
- [FS1f] Convention over configuration: prefer Spring Boot defaults and existing utilities.
- [FS1g] Ban map/bloated tooling: no `toMap()/fromMap()`, no stringly helpers, no redundant adapters.
- [FS1h] No generic utilities: reject `*Utils/*Helper/*Common`; banned: `BaseMapper<T>`, `GenericRepository<T,ID>`, `SharedUtils`.
- [FS1i] File size discipline: see [LOC1a] and [MO1a].
- [FS1j] Domain value types: identifiers (`EmailId`, `ThreadId`), amounts (`Money`), slugs wrap in records with constructor validation—never raw primitives across API boundaries.

## [LOC1] Line Count Ceiling (Repo-Wide)

- [LOC1a] All written, non-generated source files in this repository MUST be <= 350 lines (`wc -l`), including `AGENTS.md`
- [LOC1b] SRP Enforcer: This 350-line "stick" forces modularity (DDD/SRP); > 350 lines = too many responsibilities (see [MO1d])
- [LOC1c] Zero Tolerance: No edits allowed to files > 350 LOC (even legacy); you MUST split/retrofit before applying your change
- [LOC1d] Enforcement: run line count checks and treat failures as merge blockers
- [LOC1e] Exempt files: generated content, lockfiles, and large example/data dumps

## [MO1] No Monoliths

- [MO1a] No monoliths: avoid multi-concern files and catch-all modules
- [MO1b] New work starts in new files; when touching a monolith, extract at least one seam
- [MO1c] If safe extraction impossible, halt and ask
- [MO1d] Strict SRP: each unit serves one actor; separate logic that changes for different reasons
- [MO1e] Boundary rule: cross-module interaction happens only through explicit, typed contracts with dependencies pointing inward; don’t reach into other modules’ internals or mix web/use-case/domain/persistence concerns in one unit
- [MO1f] Decision Logic: New feature → New file; Bug fix → Edit existing; Logic change → Extract/Replace
- [MO1g] Extension (OCP): Add functionality via new classes/composition; do not modify stable code to add features
-   Contract: `docs/contracts/code-change.md`

## [ND1] Naming Discipline

- [ND1a] No generic identifiers. Names must be domain-specific and intent-revealing.
- [ND1b] Banned: `data`, `info`, `value`, `values`, `item`, `items`, `obj`, `object`, `thing`, `result`, `results`, `temp`, `tmp`, `misc`, `foo`, `bar`, `a`, `b`, `x`, `y`, `i`, `j`, `k`.
- [ND1c] When legacy code uses generic names, rename in the same edit; never introduce new generic names.

## [AB1] Abstraction Discipline

- [AB1a] No anemic wrappers: do not add classes that only forward calls without domain value.
- [AB1b] Abstractions must earn reuse: extend existing code first; only add new type/helper when it removes real duplication.
- [AB1c] Keep behavior close to objects: invariants live in domain model/services, not mappers or helpers.
- [AB1d] Delete unused code/legacy DTOs instead of keeping them "just in case."

## [CS1] Code Smells

- [CS1a] Primitive obsession: wrap IDs/amounts/business values in domain types when they carry invariants.
- [CS1b] Data clumps: when 3+ parameters travel together, extract into a record (`DateRange`, `PageSpec`, `SearchCriteria`).
- [CS1c] Long parameter lists: >4 parameters → use parameter object or builder; never add 5th positional argument.
- [CS1d] Feature envy: if method uses another object's data more than its own, move it there.
- [CS1e] Switch/if-else on type: replace with polymorphism when branches >3 or recur.
- [CS1f] Temporal coupling: enforce call order via state machine, builder, or combined API—never rely on caller discipline.
- [CS1g] Magic literals: no inline numbers (except 0, 1, -1) or strings; define named constants with intent-revealing names.
- [CS1h] Comment deodorant: if comment explains _what_, refactor; comments explain _why_ only.

## [RC1] Root Cause Resolution

- [RC1a] No fallback code that masks issues; no silent degradation (catch-and-log-empty, return-null on failure).
- [RC1b] Investigate → understand → fix. No workarounds. Let errors surface.
- [RC1c] One definition only: no alternate implementations behind flags. Dev-only logging allowed; remove before shipping.
- [RC1d] **No shims/workarounds—EVER.** Never introduce adapters, wrappers, type casts, or bridge code to silence errors. Fix at source or halt.

## [NO1] Null/Optional Discipline

- [NO1a] Controllers/use cases/ports never return null; singletons use Optional; collections are concrete (empty, not null).
- [NO1b] Domain models enforce invariants; avoid nullable fields unless business-optional and documented.
- [NO1c] Prefer empty collections: return `List.of()`, `Set.of()`, `Map.of()` instead of null.
- [NO1d] Optional parameters prohibited in business logic: accept nullable `T`, check internally; call sites unwrap with `.orElse(null)`.

## [RP1] Refactor Playbook

- [RP1a] One seam at a time: target real seams with typed contracts; confirm no existing contract already exists.
- [RP1b] Prefer tightening existing types over adding new ones; preserve outward JSON contracts.
- [RP1c] Avoid migrations that explode call sites; pick contained seams.
- [RP1d] Tests assert observable behavior/response shape; tests coupled to implementation details are defects.

## [AR1] Architecture & Boundaries

- [AR1a] Canonical roots: `boot/`, `application/`, `domain/`, `adapters/`, `shared/`. Legacy (`controller/`, `service/`, `dto/`) relocate when touched.
- [AR1b] Controllers (adapters/in/web): translate HTTP ↔ DTOs, delegate to one use case, return `ResponseEntity`. No repo calls, no business logic.
- [AR1c] Use cases (application/usecase): transactional boundary, single command record, orchestrate domain/ports.
- [AR1d] Domain (domain/model, domain/service): invariants/transformations, framework-free.
- [AR1e] Adapters (adapters/out/persistence): implement ports, persist validated models, no HTTP/web DTOs.
- [AR1f] Favor composition over inheritance; constructor injection only; services stateless.

## [TS1] Testing Standards

- [TS1a] Test coverage mandatory: new functionality requires tests before completion.
- [TS1b] Discovery-first: locate existing tests, follow patterns, reuse utilities before writing new.
- [TS1c] Assert observable behavior: test response shapes/outcomes, not SQL strings or internal invocations.
- [TS1d] Refactor-resilient: unchanged behavior = passing tests regardless of internal restructuring.
- [TS1e] Naming: integration tests end with `IT`; unit tests end with `Test`.

## [GT1] Git & Permissions

- [GT1a] All git commands require elevated permissions; never run without escalation.
- [GT1b] Never remove `.git/index.lock` automatically—stop and ask the user or seek explicit approval.
- [GT1c] Read-only git commands (e.g., `git status`, `git diff`, `git log`, `git show`) never require permission. Any git command that writes to the working tree, index, or history requires explicit permission.
- [GT1d] Do not skip commit signing or hooks; no `--no-verify`. No `Co-authored-by` or AI attribution.
- [GT1e] Commit messages: one logical change per commit; follow README guidance; no amend/branch changes without instruction; treat existing changes as intentional.
- [GT1f] Destructive git commands are prohibited unless explicitly ordered by the user (e.g., `git restore`, `git reset`, force checkout).
- [GT1g] Treat existing staged/unstaged changes as intentional unless the user says otherwise; never “clean up” someone else’s work unprompted.
- [GT1h] Examples of write operations that require permission: `git add`, `git commit`, `git checkout`, `git merge`, `git rebase`, `git reset`, `git restore`, `git clean`, `git cherry-pick`.

## [TL1] Tooling & Commands

- [TL1a] Builds: `make build` (Vite → Gradle), `make build-vite`, `make build-java`, `make run`.
- [TL1b] Lint: `make lint` (SpotBugs, Oxlint, Stylelint).
- [TL1c] Tests: `make test`; validate Docker with `make docker-build TAG=local` when deps change.
- [TL1d] Temporary artifacts: ALL markdown/test/doc/temp files in `tmp/` unless user requests otherwise; delete when done.
- [TL1e] Never commit `node_modules/` or built assets (`src/main/resources/static/app/email-client/`).
- [TL1f] Package Manager: `npm` is the ONLY supported package manager. `bun`, `pnpm`, `yarn` are PROHIBITED.
- [TL1g] npm overrides MUST NOT target direct dependencies; pin the direct dependency version instead to keep `npm ci` consistent.

---

## Tech Baseline

Refer to `README.md` for runtime versions and `docs/00-architectural-entrypoint.md` for architectural map.

- Frontend: Svelte + Vite at `frontend/email-client`, served as static assets under `/app/email-client/`.
- Backend: Java 25 + Spring Boot 3; REST under `/api/**`; typed `@ConfigurationProperties` under `app.` prefix.

## Repository Structure

```text
src/main/java/com/composerai/api
├── boot/           # Entry, @ConfigurationProperties
├── application/
│   ├── usecase/    # One class per business action
│   └── dto/        # Command/response records
├── domain/
│   ├── model/      # Aggregates/value objects
│   ├── service/    # Pure domain services
│   └── port/       # Interfaces for persistence/outbound
├── adapters/
│   ├── in/web/     # Controllers + web DTOs
│   └── out/persistence/  # Repo impls, adapters
└── shared/         # Cross-cutting (validation, error envelopes)
```

Tests mirror: `application/**` → `*UseCaseTest.java`; `adapters/in/web/**` → `*ControllerIT.java`.

---

## Front-End Standards

- Primary UI: Svelte; Thymeleaf hosts page with bootstrap JSON + CSP/nonce.
- Tailwind via Vite/PostCSS (never CDN); icons via `lucide-svelte`.
- Email HTML via sandboxed iframe (`email-renderer.js`)—never raw `{@html}`.
- Never duplicate backend constants in JS; use `@ControllerAdvice` + `@ModelAttribute` + `th:inline="javascript"`.

### Design Language

Glass cards, soft gradients, translucent surfaces, midnight/navy accents, 12-24px radii, generous spacing. Colors: backgrounds #F8FAFC/#F5F5F7; accents #0F172A/#1A2433; borders #E2E8F0. Shadows diffuse, never hard black.

### CSS Architecture

- Global (`app-shared.css`): design tokens, `.btn`, `.menu-surface`, z-index vars. All `:global()`.
- Component (`<style>`): scoped by default; `:global()` only for child targeting.
- Tailwind inline: layout primitives, responsive, one-offs.
- Z-index tiers: 10 (panels) → 50 (backdrop) → 60 (sidebar) → 70+ (controls) → 200 (dropdowns). Document new tiers.

---

## Compliance Checklist

Before marking task complete:
- ✅ Searched exhaustively before any new file
- ✅ tmp/ used for temporary artifacts; deleted when done
- ✅ Reused existing in-repo tools
- ✅ No `@SuppressWarnings`, `@ts-ignore`, `eslint-disable`
- ✅ No automatic migrations; .sql files marked "DO NOT RUN — REVIEW ONLY"
- ✅ Tests cover new functionality
- ✅ `make lint` passes

---

## OpenAI SDK

Before modifying OpenAI integration: read version from `build.gradle.kts` (currently `4.16.1`), review examples at [`openai-java-example`](https://github.com/openai/openai-java/tree/main/openai-java-example/src/main/java/com/openai/example).

## Database & Migrations

- No Flyway/Liquibase. Agents may author .sql in `tmp/` with header `-- DO NOT RUN — REVIEW ONLY`.
- Agents never execute migrations. All SQL is for human review/execution.

---

## [ZV1] Zod Validation (Frontend)

Runtime validation for API responses using Zod v4. See `docs/type-safety-zod-validation.md` for full guide.

- [ZV1a] **Import Pattern**: Use `import { z } from 'zod/v4';` - never bare `'zod'`.
- [ZV1b] **Discriminated Unions**: API calls return `ValidationResult<T>` - never raw data or null.
- [ZV1c] **Never Swallow Errors**: Every validation failure logs with: context, field path, expected, received, record ID.
- [ZV1d] **Record Identification**: Every `logZodFailure` call MUST include record identifier (e.g., `mailbox-state:primary`).
- [ZV1e] **Forbidden Patterns**:
  - No `schema.parse()` - throws and crashes rendering
  - No `safeParse().data ?? default` - swallows errors silently
  - No `as T` assertions for external data
  - No empty catch blocks
- [ZV1f] **Schema Location**: Schemas in `frontend/email-client/src/lib/schemas/` - no barrel files, direct imports only.
- [ZV1g] **Type Derivation**: Types derived from schemas via `z.infer<typeof Schema>` - never duplicated interfaces.
- [ZV1h] **API Contract Match**: Schema `optional()`/`nullable()`/`nullish()` must match actual API response shape.
