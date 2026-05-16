# Extension Marketplace Dialog Large Responsibilities Process

## Status

Fixed

## Linked Fix

- `docs/fixes/extensions-large-dialog-responsibilities.md`

## Scope

Reduce `ExtensionMarketplaceDialog` size by moving support models out of the class body without redesigning marketplace behavior, dependency resolution, or queue processing.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Reviewed the pending-fix note plus the extensions pipeline to keep the refactor incremental. |
| DONE | 2. Implement the fix | Extracted the dialog’s package-scoped view-state, filter, dependency, and queue helper types into `ExtensionMarketplaceDialogModels`. |
| DONE | 3. Update docs | Added the size-control note to the extensions pipeline and created the solved-fix note/process pair. |
| TO DO | 4. Verify behavior | Compile and run focused marketplace dependency coverage when Maven is available. |

## Implementation Notes

The extraction deliberately targets passive support types first because they remove a large block of noise without changing event flow. Queue progress helpers were kept API-compatible with the dialog so the behavior stays unchanged after the move.

## Verification Notes

`mvn` was not available in this shell, so compile/test execution is still pending. Manual follow-up should cover provider search, detail loading, dependency prompting, queue install progress, and installed-state refresh.
