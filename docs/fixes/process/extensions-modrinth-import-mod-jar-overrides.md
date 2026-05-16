# Modrinth Import Mod Jar Overrides Process

## Status

Fixed

## Linked Fix

- `docs/fixes/extensions-modrinth-import-mod-jar-overrides.md`

## Scope

Prevent `.mrpack` override extraction from writing unmanaged mod jars while keeping config-style overrides usable.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked extension, filesystem, and model/data pipeline notes relevant to modpack imports. |
| DONE | 2. Inspect import path | Confirmed `isAllowedOverridePath(...)` allowed `mods/*.jar` entries during extraction. |
| DONE | 3. Tighten override policy | Limited override paths to `config/` and `defaultconfigs/`. |
| DONE | 4. Add regression test | Added coverage for `server-overrides/mods/unmanaged.jar` being skipped while config overrides extract. |
| DONE | 5. Verify behavior | Ran targeted Modrinth tests and compile. |

## Implementation Notes

Mod jars continue to be installed only through the indexed-file path, where downloads are host-checked and hashes are verified before `ServerExtensionsService` installs them.

## Verification Notes

- `mvn -q -Dtest=ModrinthModpackServiceTest test`
- `mvn -q -Dtest=GestorServidoresTest test`
- `mvn -q -DskipTests compile`
