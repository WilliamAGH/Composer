# Composer Agent Guide

Operational guidance for autonomous contributors extending Composer, an email AI web application with mailbox search via traditional queries and RAG through a unified chat interface.

## Rule Summary [SUM]

- [FS1a-j] File Creation & Type Safety (exhaustive search, typed records, no maps, clean architecture)
- [MO1a-c] No Monoliths (>500 LOC, shrink on touch, new features in new files)
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

---

## [FS1] File Creation & Type Safety

- [FS1a] Before any new file: search exhaustively for existing logic → if found, reuse/extend → if not found, request explicit permission, then create in canonical locations.
- [FS1b] No `Map<String, Object>`, raw types, unchecked casts, `@SuppressWarnings`, `@ts-ignore`, or `eslint-disable` in production code. Fix root causes with typed records/value objects.
- [FS1c] If a cast is unavoidable, guard with explicit conversions (e.g., `Number::intValue`) instead of suppressing.
- [FS1d] Single-responsibility methods; no dead code; no empty try/catch that swallows exceptions.
- [FS1e] Domain has zero framework imports; dependencies point inward.
- [FS1f] Convention over configuration: prefer Spring Boot defaults and existing utilities.
- [FS1g] Ban map/bloated tooling: no `toMap()/fromMap()`, no stringly helpers, no redundant adapters.
- [FS1h] No generic utilities: reject `*Utils/*Helper/*Common`; banned: `BaseMapper<T>`, `GenericRepository<T,ID>`, `SharedUtils`.
- [FS1i] Large files (>500 LOC): extract only pieces you touch into clean-architecture roots; avoid broad refactors.
- [FS1j] Domain value types: identifiers (`EmailId`, `ThreadId`), amounts (`Money`), slugs wrap in records with constructor validation—never raw primitives across API boundaries.

## [MO1] No Monoliths

- [MO1a] Monolith = >500 LOC or multi-concern catch-all (`*Utils/*Helper/*Common`).
- [MO1b] New functionality starts in new files in canonical roots. Never add code to monoliths.
- [MO1c] Shrink on touch: when editing monoliths, extract at least one seam and net-decrease file size. If unsafe, stop and ask.

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

- [GT1a] Escalate first: when commands fail due to sandbox/permissions, re-run with escalated permissions before alternatives.
- [GT1b] No destructive git: avoid `reset --hard`, `checkout -- <path>`, `clean`, lock deletion unless user provides exact command.
- [GT1c] Never delete `.git/index.lock`; escalate original command; if still fails, surface stderr and wait.
- [GT1d] No `Co-authored-by` or AI attribution in commits; no `--amend`, `--rebase`, or history-altering commands.

## [TL1] Tooling & Commands

- [TL1a] Builds: `make build` (Vite → Maven), `make build-vite`, `make build-java`, `make run`.
- [TL1b] Lint: `make lint` (SpotBugs, Oxlint, Stylelint, Maven Enforcer).
- [TL1c] Tests: `mvn test`; validate Docker with `make docker-build TAG=local` when deps change.
- [TL1d] Temporary artifacts: ALL markdown/test/doc/temp files in `tmp/` unless user requests otherwise; delete when done.
- [TL1e] Never commit `node_modules/` or built assets (`src/main/resources/static/app/email-client/`).
- [TL1f] Package Manager: `npm` is the ONLY supported package manager. `bun`, `pnpm`, `yarn` are PROHIBITED.

---

## Tech Baseline

Refer to `README.md` for runtime versions and `docs/00-architectural-entrypoint.md` for architectural map.

- Frontend: Svelte + Vite at `frontend/email-client`, served as static assets under `/app/email-client/`.
- Backend: Java 21 + Spring Boot 3; REST under `/api/**`; typed `@ConfigurationProperties` under `app.` prefix.

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

Before modifying OpenAI integration: read version from `pom.xml` (currently `4.6.1`), inspect artifact at `~/.m2/repository/com/openai/openai-java-core/<VERSION>/`, review examples at [`openai-java-example`](https://github.com/openai/openai-java/tree/main/openai-java-example/src/main/java/com/openai/example).

## Database & Migrations

- No Flyway/Liquibase. Agents may author .sql in `tmp/` with header `-- DO NOT RUN — REVIEW ONLY`.
- Agents never execute migrations. All SQL is for human review/execution.
