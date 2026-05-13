# Modpack Import Existing Mods Process

## Status

Completed

## Linked Fix

- `docs/fixes/extensions-modpack-import-existing-mods.md`

## Scope

Fix direct modpack imports when the target mod server already has managed mods. The fix covers the direct Modrinth `.mrpack` and legacy CurseForge ZIP controller paths plus the Swing import prompt. It does not implement catalog modpack search.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked extension and filesystem pipelines plus the pending fix note. |
| DONE | 2. Repair import flow wiring | Passed the selected keep/replace policy from `PanelExtensiones` into the import worker and controller methods. |
| DONE | 3. Safely replace existing mods | The replace policy validates the pack first, then deletes only managed `.jar` files from managed extension directories before installing the pack. |
| DONE | 4. Skip conflicts on keep | The keep policy skips filename conflicts before remote metadata resolution when possible, then uses catalog install evaluation and records warnings for already installed or filename-conflicting mods instead of failing the whole import. |
| DONE | 5. Verify behavior | Ran targeted compile and tests with JDK 25. |

## Implementation Notes

- `PanelExtensiones` asks about existing mods only when the synchronized extension list contains non-plugin entries for the mod server.
- `GestorServidores` reads and validates modpack metadata and planned downloads before deleting current managed jars for replace mode.
- Existing duplicate detection remains centralized through `ServerExtensionsService.evaluateCatalogInstallation(...)`.
- Replacement is destructive but scoped: it deletes only non-symlink regular `.jar` files inside adapter-managed extension directories under the server folder, then refreshes extension state before installing the pack.

## Verification Notes

- `mvn -q -DskipTests compile`
- `mvn -q -Dtest=GestorServidoresTest test`
- `mvn -q -Dtest=ModrinthModpackServiceTest test`

Commands were run with `JAVA_HOME=C:\Users\MJE\AppData\Local\Programs\Eclipse Adoptium\jdk-25.0.2.10-hotspot` because the default `java` on PATH is Java 8.
