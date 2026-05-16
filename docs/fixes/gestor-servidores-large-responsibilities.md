# GestorServidores Has Too Many Responsibilities

## Status

Fixed

## Original Issue

`GestorServidores` had accumulated another dense block of filesystem-preservation logic for server conversion, making the class harder to navigate and grow safely.

## Root Cause

Backup creation, preservation snapshot building, selective restore, and cleanup all lived inline in `GestorServidores`, even though they formed a focused conversion-preservation responsibility.

## Solution

Extracted the conversion backup/snapshot/restore workflow into `controlador.ConversionPreservationHelper`. `GestorServidores` now coordinates the conversion flow through that helper instead of owning the low-level preservation details directly.

## Files Changed

- `src/main/java/controlador/ConversionPreservationHelper.java`
- `src/main/java/controlador/GestorServidores.java`
- `docs/pipelines/server-lifecycle-pipeline.md`

## Verification

- `mvn -q -DskipTests compile` not run here because `mvn` is unavailable in this shell.
- Targeted follow-up: run `mvn test -Dtest=GestorServidoresTest` and smoke-test a server conversion that preserves config/extensions.

## Detailed Process

- `docs/fixes/process/gestor-servidores-large-responsibilities.md`

## Regression Notes

When conversion preservation changes again, prefer extending `ConversionPreservationHelper` or extracting another focused helper instead of putting more backup/snapshot detail back into `GestorServidores`.

## Related Docs

- `docs/pipelines/server-lifecycle-pipeline.md`
- `docs/pipelines/server-creation-pipeline.md`
- `docs/pipelines/platform-adapters-pipeline.md`
