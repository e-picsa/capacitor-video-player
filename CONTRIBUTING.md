# Contributing

Thank you for your interest in contributing to `@picsa/capacitor-video-player`! This document outlines the development, pull request, and release workflows for this repository.

## Development Workflow

1. Fork the repository and create your feature branch from `main`.
2. Ensure you have the required dependencies installed (Node.js, Capacitor requirements for iOS/Android).
3. Make your changes in the `src/` directory (or native code directories).
4. Run `npm run build` to verify your code compiles correctly.
5. Run `npm run lint` and verify there are no errors.

## Pull Requests & Conventional Commits

This repository uses **[Google Release Please](https://github.com/googleapis/release-please)** to automatically calculate semantic versions (SemVer) and generate `CHANGELOG.md`.

Because of this automation, **all Pull Request titles must follow the [Conventional Commits](https://www.conventionalcommits.org/en/v1.0.0/) specification.**

When creating a PR, ensure the title follows this format:

- `feat: [description]` for new features (triggers a Minor release)
- `fix: [description]` for bug fixes (triggers a Patch release)
- `docs: [description]` for documentation changes (no release)
- `chore: [description]` for maintenance tasks (no release)
- `refactor: [description]` for code refactoring without behavior change (no release)

_Note: If your change includes a breaking change, add a `!` after the type (e.g., `feat!: new API design`) or include `BREAKING CHANGE:` in the footer. This triggers a Major release._

## PR Previews (`pkg.pr.new`)

To make reviewing easier, we use `pkg.pr.new` to automatically generate installable preview builds for every Pull Request.

When you open or update a PR, a GitHub Action will run in the background. Once complete, a bot will comment on your PR with an installation command.

Reviewers can install and test your exact PR code in their own projects by simply running that command, for example:

```bash
npm install https://pkg.pr.new/e-picsa/capacitor-video-player@123
```

This allows testing native and web implementation changes without needing to build the plugin locally from source!
