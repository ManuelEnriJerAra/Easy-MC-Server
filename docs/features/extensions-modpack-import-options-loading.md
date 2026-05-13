# Modpack Import Options And Loading

## Status

Implemented

## Feature

Modrinth modpack import now asks the user what content to import and shows loading dialogs while preflight, downloads, and installation run.

## Motivation

Modrinth `.mrpack` files can contain client-only, server-only, and shared entries. The app should not guess which side the user wants to install.

## Solution

- Added explicit Modrinth import options: `Servidor`, `Cliente`, or `Completo`, defaulting to `Completo`.
- Added import filtering in `ModrinthModpackService` and routed `GestorServidores.importarModpackModrinth(...)` through those options.
- Updated `PanelExtensiones.importarModpack()` to pre-read pack metadata and current installed extensions in a background worker, then ask for options before confirming a Modrinth import.
- Added modal loading dialogs with the shared loading progress bar while preflight and `ImportModpackWorker` run.
- Aligned existing blocking download/task dialogs to the same progress bar style.
- Updated tests so client/server/complete side filtering is covered.

## Files Changed

- `src/main/java/controlador/extensions/ModrinthModpackService.java`
- `src/main/java/controlador/GestorServidores.java`
- `src/main/java/vista/PanelExtensiones.java`
- `src/main/java/vista/AppTheme.java`
- `src/test/java/controlador/extensions/ModrinthModpackServiceTest.java`
- `docs/pipelines/extensions-pipeline.md`

## Verification

- `mvn -q -Dtest=ModrinthModpackServiceTest test`
- `mvn -q -DskipTests compile`
- `mvn test`

Commands were run with `JAVA_HOME=C:\Users\MJE\AppData\Local\Programs\Eclipse Adoptium\jdk-25.0.2.10-hotspot` because the default `java` on PATH is Java 8.

## Detailed Process

- `docs/features/process/extensions-modpack-import-options-loading.md`

## Follow-Up Notes

Loading dialogs use the shared `AppTheme.createLoadingProgressBar(...)` helper.

## Related Docs

- `docs/pipelines/extensions-pipeline.md`
- `docs/features/extensions-modrinth-modpack-import-export.md`
