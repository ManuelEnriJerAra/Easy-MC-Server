# Filesystem Operations Need Stronger Shared Helpers

## Status

Pending

## Area

Server, world, extension, import/export, and backup filesystem operations.

## Issue

File operations are spread across multiple services. Each area handles validation, copy/move/delete, and error reporting locally. This can lead to inconsistent safety checks and messaging.

## Desired Behavior

Common filesystem safety behavior should be centralized where practical, especially for recursive copy/move/delete, path normalization, and user-facing IO errors.

## Evidence

- `GestorServidores`
- `GestorMundos`
- `ServerExtensionsService`
- `CurseForgeModpackService`
- `WorldFilesService`

## Suggested Approach

Create focused utilities only for repeated, safety-sensitive patterns:

- recursive copy with preservation rules
- safe delete checks
- path containment checks
- friendly IO error formatting

Do not hide domain-specific decisions in generic helpers.

## Verification

- `mvn -q -DskipTests compile`
- filesystem-related tests in server/world/extensions packages.

## Related Docs

- `docs/pipelines/filesystem-and-paths-pipeline.md`
- `docs/pipelines/server-lifecycle-pipeline.md`
- `docs/pipelines/world-management-pipeline.md`
- `docs/pipelines/extensions-pipeline.md`
