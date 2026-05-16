# Extension Sync Performance

## Status

Fixed

## Original Issue

Syncing installed mods/plugins was too slow, especially when loading servers that already had many detected extension jars.

## Root Cause

Extension sync was doing too much work in several parts of the pipeline: `GestorServidores` scanned extensions for every saved server during construction, explicit sync persisted and rewrote server data even when nothing changed, status evaluation repeated compatibility/dependency work for each row, and the extension cache comparison was defeated by a changing timestamp.

## Solution

Startup no longer synchronizes extensions for every loaded server; extension sync remains lazy for the feature paths that need it. Explicit sync now persists only when detected metadata changes. `ServerExtensionsService` avoids platform redetection for already-classified servers, batches installed-status evaluation, and prefers runtime server extension objects before cache copies so unchanged scans do not force reference-only list changes. `InstalledExtensionsCacheService` now compares stable extension content separately from `updatedAtEpochMillis`, avoiding needless cache rewrites.

## Files Changed

- `src/main/java/controlador/extensions/ServerExtensionsService.java`
- `src/main/java/controlador/extensions/InstalledExtensionsCacheService.java`
- `src/main/java/controlador/GestorServidores.java`
- `src/main/java/vista/PanelExtensiones.java`
- `src/test/java/controlador/GestorServidoresTest.java`
- `src/test/java/controlador/extensions/ServerExtensionsServiceTest.java`
- `docs/pipelines/extensions-pipeline.md`

## Verification

- `mvn -q -Dtest=ServerExtensionsServiceTest test`
- `mvn -q -Dtest=GestorServidoresTest test`
- `mvn -q -Dtest=PanelExtensionesTest test`
- `mvn -q -DskipTests compile`

## Detailed Process

- `docs/fixes/process/extensions-sync-performance.md`

## Regression Notes

Avoid reintroducing eager all-server sync on startup. Keep cache-write comparisons independent from timestamps that intentionally change per write, and prefer batch status/dependency evaluation for UI reloads.

## Related Docs

- `docs/pipelines/extensions-pipeline.md`
- `docs/pipelines/filesystem-and-paths-pipeline.md`
