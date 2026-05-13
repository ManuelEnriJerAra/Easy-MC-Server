# Modrinth Export Skip Non-Modrinth Overrides Process

## Status

Completed

## Linked Fix

- `docs/fixes/extensions-modrinth-export-skip-non-modrinth-overrides.md`

## Scope

Fix Modrinth `.mrpack` export so installed mods without Modrinth origin metadata are not included as local override jars. This keeps normal config overrides from `config/` and `defaultconfigs/` intact.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked the docs index plus extensions, models/data, and filesystem/path pipeline guidance. |
| DONE | 2. Localize behavior | Found `ModrinthModpackService.exportServerPack(...)` queued non-Modrinth jars into `localOverrideFiles`, which `writeOverrides(...)` emitted under `server-overrides/mods/`. |
| DONE | 3. Implement the fix | Changed unresolved non-Modrinth exports to skip the jar and report a skipped entry; removed the unused local-jar override helper. |
| DONE | 4. Verify behavior | Updated `ModrinthModpackServiceTest`; later verification ran with an explicit JDK 25 `JAVA_HOME` because the default Java on PATH is Java 8. |

## Implementation Notes

The exporter still writes safe server config overrides. Only the fallback that packaged unresolved non-Modrinth installed jars as `server-overrides/mods/*.jar` was removed. Manual/local/incomplete Modrinth jars can still be exported as indexed Modrinth files after hash-based Modrinth identity recovery verifies the local jar.

## Verification Notes

- `mvn -q -Dtest=ModrinthModpackServiceTest test`
- `mvn -q -DskipTests compile`
- `mvn test`

Commands were run with `JAVA_HOME=C:\Users\MJE\AppData\Local\Programs\Eclipse Adoptium\jdk-25.0.2.10-hotspot` because the default `java` on PATH is Java 8.
