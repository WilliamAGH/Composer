# ComposerAI Agent Guide

Operational guidance for autonomous contributors extending the ComposerAI, an email AI web application that makes it easy to search a mailbox via traditional queries and RAG all via one easy to use AI chat interface.

Follow these standards to deliver clean, DRY implementations that slot into the existing Java 21 + Spring Boot 3 stack and polished front-end experience.

## Mission

- Preserve a dependable email-intelligence API that can ingest mailbox artifacts, enrich them with retrieval context, and expose them through performant chat workflows.
- Ship changes that are production-conscious: predictable logging, explicit error handling, and defensive validation at the boundaries.

## Tech Baseline

Refer to `README.md` (Technology Stack and Requirements) for current runtime versions, dependencies, and tooling expectations. Use those values unless a task explicitly overrides them.

## Step 0: Purpose Alignment (Why-First Mandate)

- Pause before building anything new or modifying existing behavior until you can state the precise reason the functionality must change. If the why is unclear or second-hand, stop and ask the user for clarification before touching the codebase.
- Maintain a lightweight working note (scratchpad, checklist, or plan) that records the validated why and revisit it throughout the task so investigations stay anchored to the goal instead of assumptions.
- Audit existing documentation up front and bring it in sync with the confirmed purpose following the guidance in "Documentation & Communication" before proceeding.
- Treat purpose alignment as a gate—do not move into execution until both the why and the documentation plan are explicit.

## Backend Development Principles

- Write DRY code: whenever considering adding any code, always first review existing related code available to use/re-use
- Use idiomatic modern Java JDK 21+ practices, and use whatever built in defaults are available in Java 21 and Spring Boot 3+ instead of custom code
- Do NOT make new files without explicit permission to do so first
- Favor composition over inheritance; keep services stateless
- Inject dependencies via constructors; avoid field injection
- Never swallow exceptions—log with context and propagate or translate into meaningful responses
- Validate incoming payloads using Jakarta Validation annotations and guard clauses
- Keep request/response DTOs simple POJOs; avoid leaking domain entities beyond service boundaries
- Encapsulate integration logic (OpenAI, Qdrant) in dedicated services with interface-level abstractions when complexity grows
- Use records or compact classes for immutable data when appropriate, but ensure Spring serialization compatibility

### Spring Boot Conventions

- Expose REST endpoints beneath `/api/**`; document new routes in README and keep controller methods slim.
- Place application-specific configuration in `AppProperties`-style classes scoped under the `app.` prefix.
- Prefer `@Service` + `@Transactional` (where needed) for business logic; keep transactions tightly scoped.
- Return `ResponseEntity` from controllers for explicit status codes and headers.

## Testing Expectations

- Add unit tests for non-trivial logic using JUnit 5 (bundled with `spring-boot-starter-test`)
- Mock external integrations (OpenAI, Qdrant) to keep tests deterministic
- Cover happy path, validation failures, and error handling branches for new endpoints or services
- For CLI utilities, include integration-style tests that exercise argument parsing when practical

## Front-End & Template Guidance

- Treat `layout.html` as the shared frame; inject page-specific content through fragments
- Leverage Tailwind utilities for layout; use `app-shared.css` sparingly for tokens or reset rules
- Uphold the product aesthetic: layered glass cards over soft gradients, translucent surfaces, diffused midnight/navy accents, rounded 12-24px radii, ultra-light borders, and generous spacing. Typography stays in crisp system sans families with breathable line height and label tracking.
- Color language: anchor backgrounds in cool off-whites (#F8FAFC, #F5F5F7), use translucent whites with subtle vertical gradients for surfaces, keep primary accents in deep slate blues or charcoal greens (#0F172A, #1A2433), reserve muted lilac/sage/amber for highlights, and apply borders/shadows around #E2E8F0 with rgba(15, 23, 42, 0.1-0.2) for depth without harsh black.
- Keep JavaScript modular and progressive-enhancement friendly. Use plain ES modules over large frameworks
- **NEVER duplicate backend constants/enums in HTML/JS**: Use `@ControllerAdvice` + `@ModelAttribute` + `th:inline="javascript"` to inject Java enums directly into templates. See `GlobalModelAttributes.java` and `WebViewControllerTest.java` for the canonical pattern.

### Design Language Reference

- **Layering**: Build UIs with stacked cards and translucent panels over soft vertical gradients. Use backdrop blur and subtle glassy highlights to separate tiers without heavy borders.
- **Spacing**: Maintain wide gutters, 32px+ section padding, and consistent vertical rhythm so content blocks breathe. Labels can use increased letter-spacing for a technical feel.
- **Geometry**: Default to rounded corners between 12-24px and keep iconography flat with minimal stroke weight. Reserve sharper corners for utility elements only.
- **Typography**: Stick to system sans stacks (`-apple-system`, `BlinkMacSystemFont`, `Segoe UI`, `system-ui`). Pair medium-weight headings with regular body copy; line heights ~1.5 and label tracking +0.2em reinforce clarity.
- **Shadows & Borders**: Prefer diffuse drop shadows such as `0 25px 50px -12px rgba(15, 23, 42, 0.18)` and hairline borders in the #E2E8F0 family. Avoid hard outlines or solid black shadows.
- **State Treatments**: Hover/focus states should nudge background opacity, brighten borders slightly, or add soft glows instead of loud color shifts. Motion should be 150-200ms ease-in-out; rely on Tailwind focus utilities to keep focus rings accessible.
- **Accessibility**: Ensure contrast ratios hit WCAG AA—deep slate text on off-white surfaces, and lighten translucent layers when stacking content on gradients.

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

1. File creation and temporary artifacts
   - ALL markdown files MUST BE CREATED IN tmp/ UNLESS EXPLICITLY REQUESTED BY THE USER.
   - ALL test files, documentation, summaries, or temporary artifacts (HTML, JSON, etc.) MUST BE CREATED IN tmp/ or /tmp/ UNLESS EXPLICITLY REQUESTED BY THE USER.
   - tmp/ files MUST BE DELETED AFTER THEY ARE NO LONGER REQUIRED/COMPLETED.

2. Use of repository tools and code style
   - We have the tools in-repo; find and reuse them — maintain a single DRY source of truth.
   - Use idiomatic Java 21+ and Spring Boot 3.3.x best practices.
   - Produce light, lean, modern code free of unnecessary or legacy boilerplate.

3. Database migrations and SQL
   - We DO NOT use Flyway, Liquibase, or any automatic migration tool.
   - Agents may create temporary .sql migration files for review, but AGENTS ARE NOT PERMITTED TO RUN ANY MIGRATIONS.
   - Automatic migrations in application code (including on-boot migrations) are NEVER ALLOWED UNDER ANY CIRCUMSTANCES.
   - All SQL performed by agents must be manual queries crafted by the agent (for review/execution by humans or approved processes).

4. Forbidden shortcuts
   - The usage of @SuppressWarnings is NEVER AN ALLOWED SOLUTION. EVER.
   - Never rewrite history: avoid `git commit --amend`, `git rebase`, or any history-altering commands.
   - Never revert or 'restore' user changes unless the user provides explicit commands—see Command Execution Guardrails for the escalation-first process.

5. OpenAI Java SDK awareness
   - Before modifying any OpenAI integration, read the current SDK version from `pom.xml` and treat it as the source of truth (presently `4.6.1`).
   - Inspect the matching local artifact at `~/.m2/repository/com/openai/openai-java-core/<VERSION>/openai-java-core-<VERSION>.jar` for API behavior, using the exact version from `pom.xml`.
   - Review the latest examples under [`openai-java-example`](https://github.com/openai/openai-java/tree/main/openai-java-example/src/main/java/com/openai/example) for up-to-date usage patterns and reasoning/streaming guidance.

6. Enforcement & review
   - Every agent-generated change must include a short compliance note listing:
     - where temp files were created (tmp/ path),
     - which repo tools were reused,
     - confirmation that no migrations were run,
     - confirmation that @SuppressWarnings was not used.
   - Pull requests or change summaries should highlight any SQL files produced and explicitly mark them as "DO NOT RUN — REVIEW ONLY".

7. Quick checklist (must be satisfied before marking a task complete)
   - tmp/ markdown created if needed, and scheduled for deletion.
   - Reused existing in-repo tools (list them).
   - No automatic migrations added or executed.
   - Any .sql files are marked "review only" and not executed.
   - No @SuppressWarnings used.

8. Git commit authorship
   - Agents must never add co-authorship metadata (including `Co-authored-by` trailers) to any git commit.

## Delivery Checklist

1. Run `mvn test` and any affected integration checks
2. Validate templates in a browser when modifying UI to confirm Tailwind class usage
3. Ensure Docker builds with `make docker-build TAG=local` when dependencies change
4. Verify new configuration parameters have sane defaults and are documented

Adherence to these guidelines keeps ComposerAI cohesive, resilient, and approachable for future iterations.
