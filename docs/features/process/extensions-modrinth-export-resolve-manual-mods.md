# Modrinth Export Resolve Manual Mods Process

## Status

Completed

## Linked Feature

- `docs/features/extensions-modrinth-export-resolve-manual-mods.md`

## Scope

Resolve manually installed/local mods during Modrinth `.mrpack` export when the local jar hash exactly matches a Modrinth version file. The feature keeps unresolved local jars out of overrides and only persists Modrinth source metadata after hash verification.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Reviewed the pending request plus extension, filesystem, model, and Modrinth import/export documentation. |
| DONE | 2. Implement hash resolution | Export now tries Modrinth `/version_file/{hash}` for manual/local jars, verifies returned file hashes, and writes verified matches into `modrinth.index.json`. |
| DONE | 3. Persist safe metadata | Resolved manual jars are updated with Modrinth source metadata and the controller persists the installed-extension cache after export. |
| DONE | 4. Verify behavior | Added focused export tests for resolved and unresolved manual jars and ran the Modrinth service test class with JDK 25. |

## Implementation Notes

- Existing Modrinth-origin exports still use stored `projectId` and `versionId` first.
- Only `UNKNOWN`, `MANUAL`, `LOCAL_FILE`, or incomplete Modrinth sources are eligible for hash-based recovery. Other remote providers are skipped as before.
- Hash recovery requires a Modrinth version response whose file hashes match the local jar SHA-512 or SHA-1. Display-name similarity is not used.
- Export computes SHA-1 and SHA-512 in one pass per local jar and reuses the result for hash-based lookup and final verification.
- Unresolved manual jars remain skipped and are not written to `mods/*.jar` overrides.

## Verification Notes

- `mvn -q -Dtest=ModrinthModpackServiceTest test` with `JAVA_HOME=C:\Users\MJE\AppData\Local\Programs\Eclipse Adoptium\jdk-25.0.2.10-hotspot`.
- `mvn -q -Dtest=ModrinthModpackServiceTest,ServerExtensionsServiceTest,GestorServidoresTest test` with the same JDK 25 environment.
- `mvn -q -DskipTests compile` with the same JDK 25 environment.
