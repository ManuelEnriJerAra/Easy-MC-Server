# GestorServidores Large Responsibilities Process

## Status

Fixed

## Linked Fix

- `docs/fixes/gestor-servidores-large-responsibilities.md`

## Scope

Reduce one high-noise responsibility inside `GestorServidores` by extracting conversion preservation mechanics, while keeping the existing conversion flow and public behavior stable.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Reviewed the pending-fix note plus the server lifecycle pipeline to choose a bounded extraction target. |
| DONE | 2. Implement the fix | Moved conversion backup/snapshot/restore/cleanup behavior into `ConversionPreservationHelper` and rewired `GestorServidores` to call it. |
| DONE | 3. Update docs | Added the helper note to the lifecycle pipeline and created the solved-fix note/process pair. |
| TO DO | 4. Verify behavior | Compile and run focused `GestorServidores` coverage when Maven is available. |

## Implementation Notes

The extraction keeps `GestorServidores` as the orchestrator but removes a self-contained block of preservation mechanics. This was chosen over a broader one-shot split so the refactor stays local to the conversion path already being touched.

## Verification Notes

`mvn` was not available in this shell, so compile/test execution is still pending. Manual follow-up should include a conversion flow that preserves server properties, EULA, icons, and managed extension folders as expected.
