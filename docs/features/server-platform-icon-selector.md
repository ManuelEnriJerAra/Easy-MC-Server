# Server Platform Icon Selector

## Status

Implemented

## Feature

Server platform choices now use a reusable icon selector instead of plain combo boxes in creation and conversion flows. The create-server wizard is hosted by `ProcessWizardDialog`.

## Motivation

The platform choice is a first-class wizard decision. Showing each platform as a clickable icon option makes the flow easier to scan and aligns the UI with the stored server platform mockup.

## Solution

Added `vista.PlatformSelectorPanel`, which groups available `ServerPlatformAdapter` entries into Vanilla, Mods, and Plugins sections. Each option is a keyboard-focusable button-style tile with an SVG icon above the platform label, hover/focus feedback, hand cursor, and selected coloring from `AppTheme.getMainAccent()`.

The selector tries future platform icon resources such as `easymcicons/fabric.svg` and falls back to `easymcicons/box.svg` when the platform-specific SVG is not available. Creation and conversion platform prompts now both read selection from this shared panel.

Server creation now builds its steps with `ProcessWizardDialog.Step`, using the shared wizard navigation and final rocket action. Conversion platform choices are filtered to platforms that support automated creation, so unavailable conversion targets are not shown to the user.

## Files Changed

- `src/main/java/vista/PlatformSelectorPanel.java`
- `src/main/java/vista/ProcessWizardDialog.java`
- `src/main/java/controlador/GestorServidores.java`
- `docs/features/process/server-platform-icon-selector.md`
- `docs/features/server-platform-icon-selector.md`
- `docs/pending-features/server-platform-icon-selector.md`

## Verification

- `mvn -q -DskipTests compile` with JDK 25 configured through `JAVA_HOME`/`PATH`.

Manual UI verification was not run in this session.

## Detailed Process

- `docs/features/process/server-platform-icon-selector.md`

## Follow-Up Notes

When platform-specific SVG assets are added, name them after the lowercase platform enum names, for example `vanilla.svg`, `neoforge.svg`, `fabric.svg`, `purpur.svg`, and `paper.svg`.

The selector remains a standalone component and is embedded directly in the creation wizard's `ProcessWizardDialog.Step` list.

## Related Docs

- `docs/pipelines/server-creation-pipeline.md`
- `docs/pipelines/platform-adapters-pipeline.md`
- `docs/pipelines/ui-component-pipeline.md`
- `docs/features/process/process-wizard.md`
- `docs/mockups/server_platform_mockup.png`
