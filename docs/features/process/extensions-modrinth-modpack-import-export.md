# Modrinth Modpack Import And Export Process

## Status

Completed

## Linked Feature

- `docs/features/extensions-modrinth-modpack-import-export.md`

## Scope

Implement Modrinth `.mrpack` export/import for mod servers while preserving the existing legacy CurseForge manifest code. The work covers service logic, controller wrappers, the extensions panel entry points, targeted tests, and pipeline documentation.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Reviewed the pending feature plus extension, filesystem, model, UI, and feature documentation guidance. |
| DONE | 2. Implement Modrinth service and UI wiring | Added `.mrpack` read/write, hash verification, metadata resolution, controller wrappers, and panel routing. |
| DONE | 3. Add tests | Covered valid export/import parsing, missing indexes, unsafe paths, hash mismatch, optional/client-only skips, dependency warnings, and export contents. |
| DONE | 4. Verify and document | Compile/test, update permanent feature docs, update the pipeline, and remove the pending feature note. |

## Implementation Notes

- Modrinth `.mrpack` archives store `modrinth.index.json` at the ZIP root and use `overrides/`, `server-overrides/`, and `client-overrides/` for filesystem overrides.
- The import path should continue through `ServerExtensionsService.installCatalogDownload(...)` so installed jars are validated, copied into the managed extension directory, and persisted through the existing cache.
- Hash verification should happen inside the downloader passed to `installCatalogDownload(...)`, so the normal installer still owns the final local copy.
- Export resolves Modrinth version file metadata by stored version id, compares local SHA-1/SHA-512 against published hashes, and skips files whose local jar no longer matches the remote version.
- Export computes SHA-1 and SHA-512 together once per local jar and reuses those hashes for all verification checks in that export run.
- Import resolves Modrinth identity by SHA-512/SHA-1 through `/version_file/{hash}` when possible and falls back to parsing canonical CDN paths, then stores the result as a Modrinth-origin catalog install.
- Import caches Modrinth project metadata per service instance so repeated files from the same project do not repeat the same project API lookup.
- ZIP paths and indexed file paths reject absolute paths, drive names, empty path segments, and any `..` segment before normalization.

## Verification Notes

- `mvn -q -Dtest=ModrinthModpackServiceTest test`
- `mvn -q -DskipTests compile`
- `mvn test`

Commands were run with `JAVA_HOME=C:\Users\MJE\AppData\Local\Programs\Eclipse Adoptium\jdk-25.0.2.10-hotspot` because the default `java` on PATH is Java 8.
