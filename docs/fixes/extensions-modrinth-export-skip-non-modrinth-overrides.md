# Modrinth Export Skip Non-Modrinth Overrides

## Status

Fixed

## Original Issue

Mods that do not come from Modrinth were being included as override jars when exporting a Modrinth `.mrpack`.

## Root Cause

`ModrinthModpackService.exportServerPack(...)` treated every installed extension without Modrinth origin metadata as a candidate local override. Safe local jars were added to `localOverrideFiles`, then `writeOverrides(...)` wrote them into `server-overrides/mods/`.

## Solution

Skip unresolved non-Modrinth installed jars during Modrinth export instead of packaging them as overrides. Manual/local/incomplete Modrinth jars may be exported only after Modrinth `/version_file/{hash}` resolves an exact version file whose hashes match the local jar. Config override export from `config/` and `defaultconfigs/` is unchanged.

## Files Changed

- `src/main/java/controlador/extensions/ModrinthModpackService.java`
- `src/test/java/controlador/extensions/ModrinthModpackServiceTest.java`
- `docs/pipelines/extensions-pipeline.md`

## Verification

- `mvn -q -Dtest=ModrinthModpackServiceTest test`
- `mvn -q -DskipTests compile`
- `mvn test`

Commands were run with `JAVA_HOME=C:\Users\MJE\AppData\Local\Programs\Eclipse Adoptium\jdk-25.0.2.10-hotspot` because the default `java` on PATH is Java 8.

## Detailed Process

- `docs/fixes/process/extensions-modrinth-export-skip-non-modrinth-overrides.md`

## Regression Notes

Modrinth exports should only index mods with verified Modrinth identity, either persisted or recovered by hash. Do not add fallback jar packaging under `server-overrides/mods/` for unresolved manual, CurseForge, Hangar, unknown, or other non-Modrinth source metadata.

## Related Docs

- `docs/pipelines/extensions-pipeline.md`
- `docs/pipelines/filesystem-and-paths-pipeline.md`
- `docs/pipelines/models-and-data-pipeline.md`
