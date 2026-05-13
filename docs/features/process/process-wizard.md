# Process Wizard Process

## Status

Completed

## Linked Feature

- `docs/features/process-wizard.md`

## Scope

Build a reusable Swing process wizard: a shared shell for app workflows that need multiple guided steps, left/right arrow navigation, per-step validation, and one final confirmation action.

The first consumer is modpack import. The server creation wizard is the visual reference; it still uses its local implementation, but its final navigation action follows the same rocket-icon behavior.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read current wizard and import flow | Reviewed `GestorServidores.mostrarAsistenteCreacionServidor()` and `PanelExtensiones.importarModpack()`. |
| DONE | 2. Add reusable process wizard shell | Added `vista.ProcessWizardDialog` with card steps, arrow navigation, final-step rocket action, validation, status text, and window-close cancellation through `Options.confirmCancel`. |
| DONE | 3. Move modpack import into process wizard | Replaced the chain of option/conflict/confirm dialogs with topic-based process steps. Modpack content and existing-mod handling share one step when existing mods are detected. |
| DONE | 4. Reduce repeated preflight work | Moved pack reading and installed-extension synchronization into a preflight worker, then reused that state during the import worker. |
| DONE | 5. Verify behavior | Compiled and ran targeted tests for panel helpers and modpack/controller behavior. |

## Implementation Notes

- Process wizard steps are plain `JComponent`s so workflows can own domain layout and state.
- The shell owns navigation, cards, status text, finish behavior, and window-close cancellation through `Options.confirmCancel`.
- The shell changes the last right-navigation icon to `rocket.svg`, so final actions are visually distinct in every process.
- The server creation wizard mirrors that final-step icon switch while it remains a local wizard in `GestorServidores`.
- Domain flows own validation and result creation through callbacks and local state.
- Modpack import steps are grouped by topic: content and existing-mod handling together when existing mods are detected, then review.
- Modpack preflight runs in a background worker before the wizard opens, so pack parsing and extension scans do not block the Swing event thread.

## Verification Notes

- `mvn -q -DskipTests compile`
- `mvn -q '-Dtest=GestorServidoresTest,PanelExtensionesTest,ModrinthModpackServiceTest' test`

Commands were run with `JAVA_HOME=C:\Users\MJE\AppData\Local\Programs\Eclipse Adoptium\jdk-25.0.2.10-hotspot` because the default `java` on PATH is Java 8.
