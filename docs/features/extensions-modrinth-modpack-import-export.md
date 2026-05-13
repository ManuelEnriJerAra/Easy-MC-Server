# Modrinth Modpack Import And Export

## Status

Implemented

## Feature

Easy MC Server can export mod servers as Modrinth `.mrpack` archives and import `.mrpack` archives into compatible mod servers.

## Motivation

The existing CurseForge manifest workflow depends on CurseForge API credentials for import. Modrinth metadata and downloads are already part of the extension marketplace, so `.mrpack` support makes ordinary modpack sharing usable without that private API-key setup.

## Solution

- Added `ModrinthModpackService` for `modrinth.index.json` parsing/writing, Modrinth version metadata lookup, SHA-512/SHA-1 verification, side filtering, path-validated override extraction, and skipped-entry reporting.
- Added `GestorServidores` wrappers for Modrinth export, index reading, and import. Import still installs jars through `ServerExtensionsService.installCatalogDownload(...)` so compatibility validation and extension cache persistence stay centralized.
- Updated `PanelExtensiones` so export defaults to Modrinth `.mrpack`, import accepts `.mrpack` and legacy CurseForge `.zip`, and CurseForge import is labeled as API-key dependent.
- Kept `CurseForgeModpackService` available for legacy manifest ZIPs.

## Files Changed

- `src/main/java/controlador/extensions/ModrinthModpackService.java`
- `src/main/java/controlador/GestorServidores.java`
- `src/main/java/vista/PanelExtensiones.java`
- `src/test/java/controlador/extensions/ModrinthModpackServiceTest.java`
- `docs/pipelines/extensions-pipeline.md`
- `docs/pending-features/extensions-modpack-catalog-search.md`

## Verification

- `mvn -q -Dtest=ModrinthModpackServiceTest test`
- `mvn -q -DskipTests compile`
- `mvn test`

Commands were run with `JAVA_HOME=C:\Users\MJE\AppData\Local\Programs\Eclipse Adoptium\jdk-25.0.2.10-hotspot` because the default `java` on PATH is Java 8.

## Detailed Process

- `docs/features/process/extensions-modrinth-modpack-import-export.md`

## Follow-Up Notes

- The importer includes required and optional entries for the selected side. There is still no per-entry optional-file selection UI.
- Export currently includes safely classified server config overrides from `config/` and `defaultconfigs/`; broader override support should stay conservative and path-validated.

## Related Docs

- `docs/pipelines/extensions-pipeline.md`
- `docs/pipelines/filesystem-and-paths-pipeline.md`
- `docs/pipelines/models-and-data-pipeline.md`
- `docs/pipelines/ui-component-pipeline.md`
- `https://support.modrinth.com/en/articles/8802351-modrinth-modpack-format-mrpack`
