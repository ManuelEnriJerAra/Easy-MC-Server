# AGENTS.md

Guidance for coding agents working in this repository.

## Project Overview

Easy MC Server is a Java 25 Swing desktop application for managing Minecraft servers. It uses Maven, FlatLaf/FlatLaf Extras for UI, Jackson/Gson for data handling, Lombok, and JUnit 5 tests.

Primary source areas:

- `src/main/java/controlador`: application logic, server lifecycle, platform installation, world/extension services, and creation/import flows.
- `src/main/java/vista`: Swing views, custom components, theme helpers, and UI panels.
- `src/main/java/modelo`: domain models and configuration objects.
- `src/main/resources/easymcicons`: SVG icons used by the UI through `SvgIconFactory`.
- `src/test/java`: unit and integration-style tests.
- `docs`: implementation guides for important program pipelines.
- `docs/pending-fixes`: known issues and cleanup candidates, one Markdown file per issue.
- `docs/fixes`: solved issue notes and regression tips, one Markdown file per fixed issue.

Main class: `controlador.Main`.

## Build And Test

Use Maven from the repository root.

```bash
mvn -q -DskipTests compile
mvn test
mvn clean package
```

The project targets Java 25. Maven/Surefire runs tests with `java.awt.headless=true`.

It is normal for Maven output to include a Lombok warning about `sun.misc.Unsafe`; do not treat that warning as a failure.

## Coding Conventions

- Before touching an established pipeline, read `docs/README.md`, then the matching guide in `docs/pipelines/`; update docs when behavior changes.
- When the user reports a problem, create or update a file in `docs/pending-fixes/` using its README standard unless the fix is completed immediately.
- When a bug is fixed, add a note in `docs/fixes/` using its README standard so similar regressions have a reference.
- Prefer existing patterns in nearby classes over new abstractions.
- Keep edits scoped to the requested behavior.
- Do not reformat unrelated files.
- Use `rg` for searching.
- Use `AppTheme` helpers for button, card, color, border, and cursor styling.
- Use `SvgIconFactory` for SVG icons instead of hand-drawn icons.
- Keep Swing updates on the EDT when reacting to listeners or background work.
- Remove listeners when panels/dialogs become undisplayable if the listener can outlive the component.
- Preserve user-facing Spanish copy and existing terminology.

## UI Notes

The app is Swing/FlatLaf, with many reusable view helpers in `vista`:

- `CardPanel` for titled cards with header/footer action areas.
- `BoxCategory` for compact settings/info rows.
- `FlatButton`, `FlatTextField`, `FlatComboBox`, `FlatScrollPane`, and related FlatLaf components where already used.
- `AppTheme.applyHeaderIconButtonStyle(...)` for icon-only header/debug buttons.

For icon buttons, use existing SVG assets from `src/main/resources/easymcicons` and set meaningful tooltips.

Avoid large visual redesigns unless requested. Fix layout issues by respecting existing panel structure and component sizing.

## Debug Mode

`vista.DebugMode` is the global debug toggle. Existing debug UI should:

- Be visible only while `DebugMode.isEnabled()` is true.
- Listen to `DebugMode.PROPERTY_ENABLED` if it needs to appear/disappear live.
- Clear fake/debug-only state when Debug mode is disabled.
- Avoid mutating real server files, logs, player data, or config unless explicitly requested.

Current examples:

- `PanelJugadores` adds/removes fake connected players.
- `PanelMundo` adds/removes fake recent connections in "Ultimas conexiones".

See `docs/pipelines/debug-mode-pipeline.md` before changing Debug mode behavior.

## Server Creation Wizard

Server creation lives mostly in `controlador.GestorServidores`, including the modal wizard and validation flow.

When editing the folder-name step:

- The parent path and editable folder name are rendered as one visual path editor.
- The editable folder name should remain left-aligned and start immediately after the visible parent path.
- The parent path may ellipsize from the left as space shrinks.
- Very long folder names are allowed to consume all available path-editor space, hiding the parent path entirely.

See `docs/pipelines/server-creation-pipeline.md` before changing server creation behavior.

## Testing Guidance

For narrow UI/layout changes, at minimum run:

```bash
mvn -q -DskipTests compile
```

Run targeted tests when touching services or model/controller behavior. Run `mvn test` when changing shared logic, filesystem behavior, platform installation, extension handling, or parsing.

## Git Hygiene

The worktree may contain user edits. Do not revert changes you did not make. Before summarizing work, use `git status --short` and inspect relevant diffs.

## Commit Messages

Use the existing repository style:

```text
Type: Concise summary
```

Rules:

- Use one of these types: `Feature`, `Fix`, `Refactor`, `Change`, `Chore`, `Docs`, `Test`, `Hotfix`.
- Prefer `Fix` over `Bugfix` for new commits.
- Keep the summary in English, present-tense/imperative style, and under about 72 characters when practical.
- Capitalize the first word after the colon.
- Do not end the subject with a period.
- Avoid `WIP` commits unless the user explicitly asks for a checkpoint.
- If a commit spans unrelated areas, either split it or use the dominant user-facing purpose.
- When asked for a commit message, inspect all current changes with `git status --short` and relevant `git diff` output before suggesting it; do not base the message only on the latest request.

Examples:

```text
Feature: Add debug controls for recent connections
Fix: Keep folder path editor aligned with long names
Refactor: Extract extension queue state from marketplace dialog
Docs: Add pipeline guide for platform adapters
Test: Cover world storage analyzer edge cases
Chore: Update project version to 0.6-beta
```
