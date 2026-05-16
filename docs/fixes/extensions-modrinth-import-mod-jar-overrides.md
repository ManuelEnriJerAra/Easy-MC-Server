# Modrinth Import Mod Jar Overrides

## Status

Fixed

## Original Issue

Modrinth `.mrpack` import accepted override entries such as `server-overrides/mods/unmanaged.jar`. Those files were copied after indexed downloads, bypassing Modrinth hash verification, compatibility checks, and installed-extension cache metadata.

## Root Cause

`isAllowedOverridePath(...)` allowed top-level `mods/*.jar` override paths. The same permissive policy was used during extraction.

## Solution

Override extraction now allows only config-style paths under `config/` and `defaultconfigs/`. Mod jars must arrive through indexed Modrinth downloads.

## Files Changed

- `src/main/java/controlador/extensions/ModrinthModpackService.java`
- `src/test/java/controlador/extensions/ModrinthModpackServiceTest.java`
- `docs/pipelines/extensions-pipeline.md`

## Verification

- `mvn -q -Dtest=ModrinthModpackServiceTest test`
- `mvn -q -Dtest=GestorServidoresTest test`
- `mvn -q -DskipTests compile`

## Detailed Process

- `docs/fixes/process/extensions-modrinth-import-mod-jar-overrides.md`

## Regression Notes

Do not reintroduce `mods/*.jar` as an override path for imports. Managed mod jars need indexed hashes and install metadata.

## Related Docs

- `docs/pipelines/extensions-pipeline.md`
- `docs/pipelines/filesystem-and-paths-pipeline.md`
- `docs/pipelines/models-and-data-pipeline.md`
