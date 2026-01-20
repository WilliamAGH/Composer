# Composer Agent Guide

## 🚨🚨🚨 CRITICAL: CLEAN CODE & FILE CREATION REQUIREMENTS 🚨🚨🚨

### FILE CREATION — MANDATORY WORKFLOW

**STOP! BEFORE CREATING ANY NEW FILE YOU MUST:**

1. **SEARCH EXHAUSTIVELY**: Find ALL existing files related to EVERY function, class, method, or component you envision. Use grep, file_glob, and semantic search across the ENTIRE codebase.
2. **ANALYZE**: Review each discovered file—does the functionality ALREADY EXIST or can it be EXTENDED?
3. **CONFIRM**: Only after exhaustively confirming NO existing solution may you proceed.
4. **REQUEST PERMISSION**: Obtain explicit user approval before creating any file.
5. **COMPLY WITH STANDARDS**: All new files MUST follow the code quality mandates below.

### CODE QUALITY MANDATES (per Robert Martin's Clean Code & Clean Architecture)

| Requirement | Rule | Violation Example |
|-------------|------|-------------------|
| **Type Safety** | NEVER use `Object`, `Map<String,Object>`, raw types, or `any`. Use typed DTOs/records. | ❌ `Map<String, Object>` / `any` |
| **Clean Code** | Succinct, single-responsibility functions. NO useless try/catch that swallows errors. NO dead code. | ❌ `catch (Exception e) { log.error(e); }` |
| **Clean Architecture** | Dependencies point inward. Domain has ZERO framework imports. Respect layer boundaries. | ❌ Repository importing from Controller |
| **No Suppression** | NEVER use `@SuppressWarnings`, `@ts-ignore`, or `eslint-disable`. Fix the root cause. | ❌ `@SuppressWarnings("unchecked")` |

**These are NON-NEGOTIABLE requirements, not suggestions.**

---

Operational guidance for autonomous contributors extending the Composer, an email AI web application that makes it easy to search a mailbox via traditional queries and RAG all via one easy to use AI chat interface.

Follow these standards to deliver clean, DRY implementations that slot into the existing Java 21 + Spring Boot 3 stack and polished front-end experience.

## Mission

- Preserve a dependable email-intelligence API that can ingest mailbox artifacts, enrich them with retrieval context, and expose them through performant chat workflows.
- Ship changes that are production-conscious: predictable logging, explicit error handling, and defensive validation at the boundaries.

## Tech Baseline

Refer to `README.md` (Technology Stack and Requirements) for current runtime versions, dependencies, and tooling expectations. Use those values unless a task explicitly overrides them. For a high-level architectural map (flow diagram, component responsibilities, and file tree guidance) consult `docs/00-architectural-entrypoint.md` before touching backend or frontend structure.

- Frontend: Svelte + Vite app lives at `frontend/email-client` and is served by Spring as static assets under `/app/email-client/`.
- Builds: `make build` composes both (Vite → Maven). Use `make build-vite` or `make build-java` for sub-builds; `make run` launches Spring locally.
- Do not commit `node_modules/` or built assets (`src/main/resources/static/app/email-client/`).

## Step 0: Purpose Alignment (Why-First Mandate)

- Pause before building anything new or modifying existing behavior until you can state the precise reason the functionality must change. If the why is unclear or second-hand, stop and ask the user for clarification before touching the codebase.
- Maintain a lightweight working note (scratchpad, checklist, or plan) that records the validated why and revisit it throughout the task so investigations stay anchored to the goal instead of assumptions.
- Audit existing documentation up front and bring it in sync with the confirmed purpose following the guidance in "Documentation & Communication" before proceeding.
- Treat purpose alignment as a gate—do not move into execution until both the why and the documentation plan are explicit.
- Never undo, overwrite, or “clean up” another agent’s work just because it looks unfamiliar—review the relevant docs/code and coordinate before touching files you didn’t introduce.

## Backend Development Principles

**Code Quality & Reuse:**
- Write DRY code: always review existing related code before adding new implementations
- Use idiomatic modern Java 21+ practices; prefer built-in JDK and Spring Boot 3+ defaults over custom code
- Do NOT create new files without explicit permission. **🚨🚨🚨 STOP! BEFORE CREATING ANY NEW FILE YOU MUST:** (1) Find ALL existing files related to EVERY function/class/method you're envisioning by searching the ENTIRE codebase, (2) Analyze if functionality ALREADY EXISTS or can be EXTENDED, (3) ONLY after confirming NO existing solution exists may you request permission. All new files MUST comply with OOP best practices (SOLID, DRY, separation of concerns) and be idiomatically clean for the language.
- Produce light, lean code free of unnecessary boilerplate

**Architecture & Design:**
- Favor composition over inheritance; keep services stateless
- Inject dependencies via constructors; avoid field injection
- Use records or compact classes for immutable data when appropriate, but ensure Spring serialization compatibility
- Encapsulate integration logic (OpenAI, Qdrant) in dedicated services with interface-level abstractions when complexity grows

**Validation & Error Handling:**
- Never swallow exceptions—log with context and propagate or translate into meaningful responses
- Validate incoming payloads using Jakarta Validation annotations and guard clauses
- Keep request/response DTOs simple POJOs; avoid leaking domain entities beyond service boundaries

### Spring Boot Conventions

- Expose REST endpoints beneath `/api/**`; document new routes in README and keep controller methods slim.
- Place application-specific configuration in `AppProperties`-style classes scoped under the `app.` prefix.
- Prefer `@Service` + `@Transactional` (where needed) for business logic; keep transactions tightly scoped.
- Return `ResponseEntity` from controllers for explicit status codes and headers.

## Repository structure (Spring Boot + Svelte)

Canonical package layout for new code and any classes you touch:

```text path=null start=null
src/main/java/com/composerai/api
├── boot/                 # Spring Boot entry, typed @ConfigurationProperties, main application class
├── application/
│   ├── usecase/          # One class per business action (e.g., CreateThreadUseCase)
│   └── dto/              # Command/response records used by use cases
├── domain/
│   ├── model/            # Aggregates/value objects with invariants
│   ├── service/          # Pure domain services (no framework deps)
│   └── port/             # Interfaces for persistence and outbound services (OpenAI, Qdrant, etc.)
├── adapters/
│   ├── in/web/           # HTTP controllers + web DTOs
│   └── out/persistence/  # Repository impls, vector stores, external adapters
└── shared/               # Cross-cutting helpers (validation, error envelopes, utils)
```

Resources:

```text path=null start=null
src/main/resources
├── application.properties            # plus application-*.properties
├── templates/                        # Thymeleaf pages/fragments
└── static/                           # Built Vite assets under app/email-client/
```

Frontend:

```text path=null start=null
frontend/email-client
├── src/**          # Svelte + Vite source
└── dist/**         # Build output (never committed)
```

### Layer boundaries

- Controllers (adapters/in/web) translate HTTP ↔ web DTOs, delegate to a single use case (or existing service until migrated), and return `ResponseEntity` with DTOs. No repository access or business normalization in controllers.
- Use cases (application/usecase) define the transactional boundary; accept a single command record; orchestrate domain services and ports.
- Domain (domain/model, domain/service) enforces invariants and transformations; keep framework-free.
- Outbound adapters (adapters/out/persistence) implement domain ports and persist validated models; no HTTP/web DTOs here.

### File placement and movement

- Canonical roots above are the only sanctioned locations for new Java files when creation is explicitly approved. Do not introduce new top-level packages.
- Anything under current folders like `controller/`, `service/`, `dto/`, etc. is considered legacy layout; when you touch one of those classes, relocate it into the canonical directory that matches its role as part of the change.
- Keep typed configuration and the Spring Boot entry point under `boot/` (move `AppProperties`-style classes there when you edit them).

### Tests layout

```text path=null start=null
src/test/java/com/composerai/api
├── application/**             # *UseCaseTest.java
├── adapters/in/web/**         # *ControllerIT.java
├── adapters/out/persistence/**# *RepositoryIT.java or adapter tests
└── shared/**                  # Utility/mapper tests
```

Note: This structure governs placement only; it does not change existing error envelope behavior. Continue using `GlobalExceptionHandler` + `ErrorResponse` and `@Valid` for validation.

## Testing Expectations

- Add unit tests for non-trivial logic using JUnit 5 (bundled with `spring-boot-starter-test`)
- Mock external integrations (OpenAI, Qdrant) to keep tests deterministic
- Cover happy path, validation failures, and error handling branches for new endpoints or services
- For CLI utilities, include integration-style tests that exercise argument parsing when practical

## Front-End & Template Guidance

### Core Technologies & Patterns

- Primary UI is Svelte; Thymeleaf hosts the page and injects bootstrap JSON + CSP/nonce
- Tailwind CSS is bundled via the Vite/PostCSS pipeline (never via CDN); keep `src/app.css` importing `@tailwind base/components/utilities`.
- Treat Tailwind as the source of truth for spacing/color/typography. Build components with utility classes first, and only drop into custom CSS when utilities cannot express the design. Before touching CSS, skim Tailwind’s “Utility-First”/“Best Practices” docs and remember that utilities win by order, not specificity.
- Icons: use `lucide-svelte`
- Email HTML must render via the sandboxed iframe (`email-renderer.js`) — never use raw {@html} with email bodies
- Treat `layout.html` as the shared frame; inject page-specific content through fragments
- Keep JavaScript modular and progressive-enhancement friendly; use plain ES modules over large frameworks
- **NEVER duplicate backend constants/enums in HTML/JS**: Use `@ControllerAdvice` + `@ModelAttribute` + `th:inline="javascript"` to inject Java enums directly into templates. See `GlobalModelAttributes.java` and `WebViewControllerTest.java` for the canonical pattern
- **Before touching UI tasks**: carefully review Tailwind CSS docs (especially their notes on CDN-injected specificity) and skim the relevant Svelte docs plus node_modules helpers before starting so implementation details stay accurate.

### Design Language

**Core Aesthetic:** Layered glass cards over soft gradients with translucent surfaces, diffused midnight/navy accents, rounded 12-24px radii, ultra-light borders, and generous spacing.

- **Color Palette**:
  - Backgrounds: cool off-whites (#F8FAFC, #F5F5F7)
  - Surfaces: translucent whites with subtle vertical gradients
  - Primary accents: deep slate blues or charcoal greens (#0F172A, #1A2433)
  - Highlights: muted lilac/sage/amber
  - Borders/shadows: #E2E8F0 with rgba(15, 23, 42, 0.1-0.2) for depth without harsh black
- **Layering**: Stacked cards and translucent panels with backdrop blur and subtle glassy highlights to separate tiers without heavy borders
- **Spacing**: Wide gutters, 32px+ section padding, consistent vertical rhythm; labels can use increased letter-spacing for technical feel
- **Geometry**: Rounded corners 12-24px; flat iconography with minimal stroke weight; reserve sharper corners for utility elements only
- **Typography**: System sans stacks (`-apple-system`, `BlinkMacSystemFont`, `Segoe UI`, `system-ui`); medium-weight headings with regular body copy; line heights ~1.5 and label tracking +0.2em
- **Shadows & Borders**: Diffuse drop shadows (`0 25px 50px -12px rgba(15, 23, 42, 0.18)`); hairline borders in #E2E8F0 family; avoid hard outlines or solid black shadows
- **State Treatments**: Hover/focus states nudge background opacity, brighten borders slightly, or add soft glows; 150-200ms ease-in-out transitions; use Tailwind focus utilities for accessible focus rings
- **Accessibility**: Ensure WCAG AA contrast ratios—deep slate text on off-white surfaces; lighten translucent layers when stacking content on gradients

### CSS Organization & Style Architecture

Maintain a strict separation between global styles, component-scoped styles, and Tailwind utilities to prevent duplication, specificity conflicts, and maintenance burden.
- Scoped component styles (`<style>` in `.svelte`) are preferred for Tailwind overrides since Svelte adds hashed selectors that beat utility conflicts. Use `:global()` only for shared tokens (buttons, nav pills, etc.).
- When extracting repeated Tailwind patterns, prefer Svelte components or `@apply` inside component styles instead of re-creating selectors in `app-shared.css`.
- Keep inline `style=` bindings for stateful transforms (e.g., drawer translate) if Tailwind utilities would otherwise override them. Inline styles and scoped selectors should be the last resort after checking for appropriate utilities.
- Avoid `!important` unless Tailwind’s `!` prefix (e.g., `!translate-x-0`) is absolutely required; review Tailwind docs before adding it.
- If you must write custom CSS, do it in the component `<style>` block and use `@apply` to pull Tailwind tokens. Reserve `app-shared.css` for true design tokens (buttons, nav pills, z-index vars).
- `:global()` is for shared tokens only. If a selector names a component (e.g., `.compose-mobile__field`), move it into that component or replace it with utilities.

#### Style Placement Rules

1. **Global Styles** (`frontend/email-client/src/app-shared.css`)
   - **Purpose**: Reusable design tokens, component base classes (`.btn`, `.menu-surface`, `.nav-pill`), and z-index architecture
   - **Scope**: Use `:global()` wrapper for all class definitions
   - **When to use**: Styles shared across 3+ components OR styles that define the design system (buttons, cards, pills, dropdowns)
   - **Never duplicate**: If a style exists in `app-shared.css`, do NOT redefine it in component `<style>` blocks

2. **Component-Scoped Styles** (`.svelte` files `<style>` blocks)
   - **Purpose**: Component-specific layout, positioning, or styling unique to that component
   - **Scope**: Scoped by default (Svelte adds hash suffixes); use `:global()` only when intentionally targeting child components
   - **When to use**: Styling that applies ONLY to this component and won't be reused elsewhere
   - **Before adding**: Search `app-shared.css` to verify the class doesn't already exist globally

3. **Tailwind Utilities** (inline `class` attributes)
   - **Purpose**: Layout primitives (flex, grid, spacing, sizing), responsive breakpoints, and one-off adjustments
   - **When to use**: Structural layout, responsive design, and utility classes that don't warrant custom CSS
   - **Avoid**: Duplicating design system styles (e.g., don't recreate `.btn` with Tailwind classes)

#### Z-Index Architecture

All z-index values MUST follow the documented stack order defined in `app-shared.css`:

```css
/**
 * Z-INDEX ARCHITECTURE:
 * 10  - Panel overlays (ai-panel-wrapper)
 * 30  - Maximized panels (ai-panel-wrapper.maximized)
 * 50  - Drawer backdrop (DrawerBackdrop)
 * 60  - Drawer sidebar (MailboxSidebar fixed mobile)
 * 70+ - Interactive controls (buttons, menus in overlay contexts)
 * 80  - Window frames
 * 120 - Window notices
 * 140 - Maximized window backdrop
 * 150 - Action toolbar, Panel dock chip
 * 180 - Modals (ComingSoonModal)
 * 200 - Menu surfaces (dropdowns)
 * 220 - Nested dropdowns
 */
```

**Rules:**
- **Never use arbitrary z-index values** without documenting them in the architecture comment
- **Use CSS variables** (`var(--z-dropdown)`) when defining z-index in `app-shared.css`
- **Use Tailwind utilities** (`z-[70]`) for inline component-specific overrides in overlays
- **Interactive controls in overlays** (hamburger buttons, close buttons) MUST have `z-[70]+` to render above drawer backdrop (z-50) and sidebar (z-60)
- **Update the architecture comment** in `app-shared.css` when introducing new stacking contexts

#### CSS Documentation Requirements

Every reusable class in `app-shared.css` MUST include a JSDoc-style comment:

```css
/**
 * Icon-only button variant (42x42px circular)
 * @usage - Navigation controls, modal actions, toolbar buttons
 * @z-index-warning - Add explicit z-[70]+ via Tailwind when used in drawer/overlay contexts
 *                    to ensure button remains clickable above DrawerBackdrop (z-50) and
 *                    MailboxSidebar (z-60). See App.svelte:920 for reference.
 * @related - .btn--inset for inset shadow effect
 */
:global(.btn--icon) {
  /* ... */
}
```

**Required documentation fields:**
- **Purpose/description**: One-line explanation of what the class does
- **@usage**: Where/when to use this class (component types, contexts)
- **@z-index-warning**: If the class requires z-index management in certain contexts
- **@related**: Other classes commonly used with this one (modifiers, variants)

#### Style Auditing Checklist

Before committing CSS changes, verify:

1. ✅ **No duplicates**: Searched `app-shared.css` and all `.svelte` files for duplicate class definitions
2. ✅ **Z-index compliance**: All z-index values follow the documented architecture
3. ✅ **Documentation**: Global classes have JSDoc comments with @usage and relevant warnings
4. ✅ **Scoping**: `:global()` used correctly (global in CSS file, scoped in components)
5. ✅ **Tailwind first**: Not recreating Tailwind utilities with custom CSS
6. ✅ **Bundle impact**: CSS bundle size hasn't grown unnecessarily (check build output)

#### Common Anti-Patterns to Avoid

- ❌ **Duplicate global classes in component files**: Remove; reference `app-shared.css` instead
- ❌ **Undocumented z-index values**: Add to architecture comment with rationale
- ❌ **Mixing concerns**: Don't put component-specific styles in `app-shared.css`
- ❌ **Arbitrary z-index**: Use documented tiers; add new tier if none fit
- ❌ **Missing `:global()` in app-shared.css**: All classes must be globally scoped
- ❌ **Over-using `:global()` in components**: Keep component styles scoped by default

## Documentation & Communication

- Update `README.md` and API docs whenever endpoints, configuration, or workflows change
- Maintain changelog snippets in commit messages; use imperative mood.
- Leave concise code comments only where intent is not obvious (e.g., tricky algorithms or non-obvious constraints)
- When creating or updating functionality, document the confirmed why (per Step 0) in JSDoc/JavaDoc or equivalent comments along with critical operational context—keep it factual and succinct.

## 🔒 Command Execution Guardrails — Non-Negotiable

1. **Escalate the original failing command first.** When a command errors because of permission or sandbox restrictions (including `.git/index.lock` or filesystem gating), immediately re-run the same command with `with_escalated_permissions=true` and provide a one-sentence justification before considering alternatives.
2. **Avoid destructive git or filesystem commands unless explicitly directed.** Tasks such as `git reset --hard`, `git checkout -- <path>`, `git clean`, deleting lock files, or manually restoring tracked files must only be run when the user supplies the exact command verbatim.
3. **Handle repository locks solely through escalation.** Do not delete `.git/index.lock`, `.git/next-index-*.lock`, or similar artifacts. Escalate the original command; if it still fails, surface the stderr output and wait for instructions.
4. **Skip inference-driven cleanup.** Never guess at fixes for command failures or attempt to "clean up" side effects. Share the exact command and error and pause if escalation does not resolve it.

## LLM Agent Rules — Mandatory (project-wide)

1. **File creation and temporary artifacts**
   - ALL markdown files MUST BE CREATED IN tmp/ UNLESS EXPLICITLY REQUESTED BY THE USER
   - ALL test files, documentation, summaries, or temporary artifacts (HTML, JSON, etc.) MUST BE CREATED IN tmp/ or /tmp/ UNLESS EXPLICITLY REQUESTED BY THE USER
   - tmp/ files MUST BE DELETED AFTER THEY ARE NO LONGER REQUIRED/COMPLETED

2. **Code quality and reuse** (see Backend Development Principles for detailed guidance)
   - Find and reuse existing in-repo tools—maintain a single DRY source of truth
   - Follow idiomatic Java 21+ and Spring Boot 3.3.x best practices
   - Never create new files without explicit permission. **🚨 MANDATORY:** Before ANY new file, you MUST first search for ALL existing files related to the functionality, confirm no existing solution exists, and ensure compliance with OOP best practices.

3. **Database migrations and SQL**
   - We DO NOT use Flyway, Liquibase, or any automatic migration tool.
   - Agents may create temporary .sql migration files for review, but AGENTS ARE NOT PERMITTED TO RUN ANY MIGRATIONS.
   - Automatic migrations in application code (including on-boot migrations) are NEVER ALLOWED UNDER ANY CIRCUMSTANCES.
   - All SQL performed by agents must be manual queries crafted by the agent (for review/execution by humans or approved processes).

4. **Forbidden shortcuts**
   - The usage of @SuppressWarnings is NEVER AN ALLOWED SOLUTION. EVER
   - Never rewrite history: avoid `git commit --amend`, `git rebase`, or any history-altering commands
   - Never revert or 'restore' user changes unless the user provides explicit commands—see Command Execution Guardrails for the escalation-first process

5. **OpenAI Java SDK awareness**
   - Before modifying any OpenAI integration, read the current SDK version from `pom.xml` and treat it as the source of truth (presently `4.6.1`)
   - Inspect the matching local artifact at `~/.m2/repository/com/openai/openai-java-core/<VERSION>/openai-java-core-<VERSION>.jar` for API behavior, using the exact version from `pom.xml`
   - Review the latest examples under [`openai-java-example`](https://github.com/openai/openai-java/tree/main/openai-java-example/src/main/java/com/openai/example) for up-to-date usage patterns and reasoning/streaming guidance

6. **Git commit authorship**
   - Agents must never add co-authorship metadata (including `Co-authored-by` trailers) to any git commit

7. **Compliance checklist** (must be satisfied before marking a task complete)
   - ✅ tmp/ files created for temporary artifacts and scheduled for deletion
   - ✅ Reused existing in-repo tools (list them in commit/PR)
   - ✅ No automatic migrations added or executed
   - ✅ Any .sql files marked "DO NOT RUN — REVIEW ONLY"
   - ✅ No @SuppressWarnings used
   - ✅ Pull requests highlight SQL files and mark them as review-only

## Delivery Checklist

1. Run `make lint` to check for bugs (SpotBugs), code issues (Oxlint), CSS duplicates (Stylelint), and dependency problems (Maven Enforcer)
2. Run `mvn test` and any affected integration checks
3. Validate templates in a browser when modifying UI to confirm Tailwind class usage
4. Ensure Docker builds with `make docker-build TAG=local` when dependencies change
5. Verify new configuration parameters have sane defaults and are documented

Adherence to these guidelines keeps Composer cohesive, resilient, and approachable for future iterations.
