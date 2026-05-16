# Extension Sync Performance Process

## Status

Fixed

## Linked Fix

- `docs/fixes/extensions-sync-performance.md`

## Scope

Improve installed mod/plugin sync performance by removing eager and repeated work while keeping full jar inspection for actual sync scans.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked extension and filesystem pipeline rules before editing sync behavior. |
| DONE | 2. Inspect sync flow | Found eager constructor sync, repeated save/persist on unchanged sync, repeated per-row status work, platform redetection, and unstable cache serialization. |
| DONE | 3. Remove eager work | Deferred extension synchronization out of the `GestorServidores` constructor and kept sync lazy for extension/modpack flows. |
| DONE | 4. Reduce repeated work | Persist only on changes, reuse runtime extension objects before cache copies, avoid platform redetection for classified servers, batch status evaluation, and compare cache content without `updatedAtEpochMillis`. |
| DONE | 5. Verify behavior | Ran targeted server, extension service, panel, and compile checks. |

## Implementation Notes

The first attempted persisted-metadata shortcut was removed after review because size and timestamp alone can miss same-size replacements with preserved mtimes. The final fix keeps full jar inspection during actual sync scans and removes avoidable sync triggers, repeated persistence, platform redetection, and per-row status recalculation.

## Verification Notes

- `mvn -q -Dtest=ServerExtensionsServiceTest test`
- `mvn -q -Dtest=GestorServidoresTest test`
- `mvn -q -Dtest=PanelExtensionesTest test`
- `mvn -q -DskipTests compile`
