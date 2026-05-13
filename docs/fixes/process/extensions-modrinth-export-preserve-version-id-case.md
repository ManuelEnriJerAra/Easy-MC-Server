# Modrinth Export Version Id Case Process

## Status

Completed

## Linked Fix

- `docs/fixes/extensions-modrinth-export-preserve-version-id-case.md`

## Scope

Fix Modrinth `.mrpack` export 404s caused by lowercasing stored Modrinth project/version identifiers before calling the Modrinth API.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Inspect reported stack trace | The export worker calls `ModrinthModpackService.resolveVersionFileMetadata(...)`, which requested lowercased version IDs such as `/version/6beadhvq`. |
| DONE | 2. Preserve remote identifiers | Kept Modrinth project/version IDs and URL/file-name comparisons case-preserving while still lowercasing fields where that is semantically safe. |
| DONE | 3. Verify behavior | Added a mixed-case regression test and ran targeted compile/test plus the full suite. |

## Implementation Notes

Modrinth identifiers in API paths are case-sensitive. Do not use a generic lowercasing normalizer for remote IDs or canonical URLs.

## Verification Notes

- `mvn -q -Dtest=ModrinthModpackServiceTest test`
- `mvn -q -DskipTests compile`
- `mvn test`

Commands were run with `JAVA_HOME=C:\Users\MJE\AppData\Local\Programs\Eclipse Adoptium\jdk-25.0.2.10-hotspot` because the default `java` on PATH is Java 8.
