# Contributing to ComposerAI

Found a bug or have a feature request? Please open an issue in this repository.

Contributions and feedback are welcome, and Pull Requests (PRs) are encouraged.

## Getting started

1. Fork the repository (or create a branch if you have write access).
2. Clone your fork: `git clone https://github.com/YOUR_USERNAME/ComposerAI.git`
3. Create a feature branch: `git checkout -b my-feature`
4. Make your changes.
5. Run checks (below).
6. Push to your fork/branch and open a Pull Request.

## Development setup

Requirements:
- JDK 25
- Node 22.17.0 (see `.nvmrc`)
- `npm` (the only supported JS package manager for this repo)

Common commands:
- `make dev` – run Spring Boot + Vite dev server together
- `make run` – run Spring Boot only (`SPRING_PROFILES_ACTIVE=local`)
- `make fe-dev` – run Vite dev server only
- `make test` – run backend tests (`./gradlew test`)
- `make lint` – run Java + frontend linters
- `make build` – build the frontend bundle + Spring Boot JAR

You’ll usually need `OPENAI_API_KEY` (or compatible provider credentials). See `docs/getting-started.md`.

## Guidelines

- Keep PRs focused on a single change (or tightly related set of changes).
- Add tests for new functionality and bug fixes when feasible.
- Update documentation when behavior, config, or UX changes.
- Follow repository conventions in `AGENTS.md` (type-safety, clean boundaries, no monoliths, etc.).
- Don’t commit build artifacts or local dependencies (`node_modules/`, `dist/`, `build/`, `target/`, or `src/main/resources/static/app/email-client/`).

## Reporting issues

When reporting an issue, please include:
- A clear description of the problem
- Steps to reproduce
- Expected vs. actual behavior
- Logs or screenshots (if applicable)
- Your environment (OS, Java version, Node version)

## License

By contributing, you agree that your contributions are licensed under `LICENSE.md`.
