# Filesystem Operations Centralization Process

## Status

Fixed

## Linked Fix

- `docs/fixes/filesystem-operations-centralization.md`

## Scope

Resolve the pending centralization note by introducing focused shared filesystem safety helpers while keeping domain behavior in server, world, and extension services.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked docs README plus extension and filesystem pipelines. |
| DONE | 2. Identify repeated primitives | Found duplicated safe-relative-path logic and recursive copy/delete helpers. |
| DONE | 3. Implement shared helper | Added `FileSystemSafety` for path validation, containment, no-follow file checks, recursive copy, and recursive delete. |
| DONE | 4. Wire existing callers | Routed `Utilidades.copiarDirectorio(...)`, `Utilidades.eliminarDirectorio(...)`, and Modrinth path checks through the helper. |
| DONE | 5. Verify behavior | Ran targeted Modrinth tests and compile. |

## Implementation Notes

`FileSystemSafety` intentionally avoids product decisions. It exposes small primitives so callers still decide which folders, file types, and destructive flows are valid for their domain.

## Verification Notes

- `mvn -q -Dtest=ModrinthModpackServiceTest test`
- `mvn -q -Dtest=GestorServidoresTest test`
- `mvn -q -DskipTests compile`
