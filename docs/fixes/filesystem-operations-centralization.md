# Filesystem Operations Centralization

## Status

Fixed

## Original Issue

Server, world, extension, import/export, and backup filesystem operations repeated safety-sensitive checks for recursive copy/delete, relative path validation, containment, and no-follow regular-file handling.

## Root Cause

The shared `Utilidades` filesystem helpers implemented recursive operations directly, while Modrinth modpack code kept its own relative path validation. This made it easy for related fixes to diverge.

## Solution

Added `controlador.FileSystemSafety` as the shared home for focused filesystem primitives:

- safe relative path validation
- contained relative path resolution
- no-follow regular file and directory checks
- recursive copy/delete wrappers used by `Utilidades`

Domain decisions remain in their owning services.

## Files Changed

- `src/main/java/controlador/FileSystemSafety.java`
- `src/main/java/controlador/Utilidades.java`
- `src/main/java/controlador/extensions/ModrinthModpackService.java`
- `docs/pipelines/filesystem-and-paths-pipeline.md`

## Verification

- `mvn -q -Dtest=ModrinthModpackServiceTest test`
- `mvn -q -Dtest=GestorServidoresTest test`
- `mvn -q -DskipTests compile`

## Detailed Process

- `docs/fixes/process/filesystem-operations-centralization.md`

## Regression Notes

Keep generic helpers limited to reusable safety primitives. Do not move modpack-specific override policy or server/world import choices into `FileSystemSafety`.

## Related Docs

- `docs/pipelines/filesystem-and-paths-pipeline.md`
- `docs/pipelines/extensions-pipeline.md`
- `docs/pipelines/server-lifecycle-pipeline.md`
- `docs/pipelines/world-management-pipeline.md`
