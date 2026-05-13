# Modrinth Export Resolve Manual Mods

## Status

Implemented

## Feature

Modrinth `.mrpack` export can now include manually installed/local mods when the local jar can be resolved to an exact Modrinth version file by hash.

## Motivation

Users often install mods manually before exporting a pack. When the jar is actually a Modrinth file, skipping it makes the exported pack incomplete even though the app can safely recover a real Modrinth identity.

## Solution

- Kept the existing stored Modrinth export path for extensions with complete `projectId` and `versionId` metadata.
- Added a hash-based fallback for manual/local/incomplete Modrinth sources using Modrinth `/version_file/{hash}`.
- Verified returned Modrinth file hashes against the local jar before adding the entry to `modrinth.index.json`.
- Reused one-pass SHA-1/SHA-512 calculation during export so manual resolution and final verification do not repeatedly read the same jar.
- Persisted recovered Modrinth source metadata after export so later scans and exports do not need to rediscover the same jar.
- Continued skipping unresolved manual jars instead of exporting them as `mods/*.jar` overrides.

## Files Changed

- `src/main/java/controlador/extensions/ModrinthModpackService.java`
- `src/main/java/controlador/GestorServidores.java`
- `src/test/java/controlador/extensions/ModrinthModpackServiceTest.java`
- `docs/pipelines/extensions-pipeline.md`

## Verification

- `mvn -q -Dtest=ModrinthModpackServiceTest test`
- `mvn -q -Dtest=ModrinthModpackServiceTest,ServerExtensionsServiceTest,GestorServidoresTest test`
- `mvn -q -DskipTests compile`

Commands were run with `JAVA_HOME=C:\Users\MJE\AppData\Local\Programs\Eclipse Adoptium\jdk-25.0.2.10-hotspot` because the default `java` on PATH is Java 8.

## Detailed Process

- `docs/features/process/extensions-modrinth-export-resolve-manual-mods.md`

## Follow-Up Notes

Keep future export expansion strict: unresolved non-Modrinth jars should remain out of `.mrpack` overrides unless a separate, explicit policy is added.

## Related Docs

- `docs/pipelines/extensions-pipeline.md`
- `docs/pipelines/filesystem-and-paths-pipeline.md`
- `docs/pipelines/models-and-data-pipeline.md`
- `docs/features/extensions-modrinth-modpack-import-export.md`
