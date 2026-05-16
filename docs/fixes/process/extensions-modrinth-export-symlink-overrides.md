# Modrinth Export Symlink Overrides Process

## Status

Fixed

## Linked Fix

- `docs/fixes/extensions-modrinth-export-symlink-overrides.md`

## Scope

Prevent Modrinth export from following symlinks inside override roots while preserving normal `config/` and `defaultconfigs/` override export.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked extension and filesystem pipeline rules. |
| DONE | 2. Inspect export path | Confirmed `writeOverrides(...)` followed symlinked files through `Files.isRegularFile(...)`. |
| DONE | 3. Patch export checks | Switched root and file collection to no-follow checks and added a final no-follow guard before copying. |
| DONE | 4. Add regression test | Added a test with a symlink under `config/` pointing outside the server directory. |
| DONE | 5. Verify behavior | Ran targeted Modrinth tests and compile. |

## Implementation Notes

`Files.walk(...)` is left without `FOLLOW_LINKS`; the key fix is that file acceptance uses `Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)` through `FileSystemSafety`.

## Verification Notes

- `mvn -q -Dtest=ModrinthModpackServiceTest test`
- `mvn -q -DskipTests compile`
