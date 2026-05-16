# Process Wizard

## Status

Implemented

## Feature

Added a reusable process wizard shell for workflows that need multiple guided steps, left/right arrow navigation, validation, and a final action. The final step swaps the right-arrow icon for `rocket.svg`.

## Motivation

Modpack import had become a chain of separate dialogs: file read, content mode, existing-mod policy, and confirmation. This was slow and awkward because the flow repeated preflight work and asked the user to make related decisions in separate windows.

## Solution

`vista.ProcessWizardDialog` now provides the reusable shell. Its final step swaps the right-arrow action for `rocket.svg`; the existing server creation wizard mirrors that final-step behavior until it is fully migrated to the shared shell. Modpack import uses the shared wizard to group decisions by topic:

- content selection for Modrinth `.mrpack` files and, when existing mods are detected, existing-mod handling in the same step;
- review before starting import.

Modpack preflight now reads pack metadata and synchronizes existing extensions in a background worker before opening the wizard, so large packs or servers with many mods do not block the Swing event thread. The import worker then receives the pre-read modpack metadata and already-synchronized extension state from the wizard flow, avoiding an extra pack read and an extra extension rescan.

## Files Changed

- `src/main/java/vista/ProcessWizardDialog.java`
- `src/main/java/vista/WizardDialog.java`
- `src/main/java/vista/PanelExtensiones.java`
- `src/main/java/controlador/GestorServidores.java`
- `src/test/java/controlador/GestorServidoresTest.java`
- `src/main/resources/doraicons/rocket.svg`
- `docs/pipelines/ui-component-pipeline.md`
- `docs/pipelines/extensions-pipeline.md`
- `docs/pipelines/server-creation-pipeline.md`
- `docs/pending-features/server-platform-icon-selector.md`

## Verification

- `mvn -q -DskipTests compile`
- `mvn -q '-Dtest=GestorServidoresTest,PanelExtensionesTest,ModrinthModpackServiceTest' test`

Commands were run with `JAVA_HOME=C:\Users\MJE\AppData\Local\Programs\Eclipse Adoptium\jdk-25.0.2.10-hotspot` because the default `java` on PATH is Java 8.

## Detailed Process

- `docs/features/process/process-wizard.md`

## Follow-Up Notes

The server creation assistant still uses its local wizard implementation, but its final navigation action now follows the same right-arrow-to-rocket behavior as `ProcessWizardDialog`. Future work can migrate the full creation flow to `ProcessWizardDialog` once the creation-specific validation and async version loading are separated from `GestorServidores`.

## Related Docs

- `docs/pipelines/ui-component-pipeline.md`
- `docs/pipelines/extensions-pipeline.md`
