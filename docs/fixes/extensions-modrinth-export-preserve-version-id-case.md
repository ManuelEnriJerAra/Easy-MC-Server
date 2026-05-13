# Modrinth Export Version Id Case

## Status

Fixed

## Original Issue

Modrinth `.mrpack` export logged repeated 404 warnings such as `/version/6beadhvq` even though the selected server cache stored valid mixed-case version IDs like `6beaDHvq`.

## Root Cause

`ModrinthModpackService` reused a generic normalizer that lowercased `ExtensionSource.projectId`, `ExtensionSource.versionId`, and comparison strings. Modrinth version IDs are case-sensitive, so lowercasing them produced invalid API URLs.

## Solution

Split case-preserving trimming from lowercasing normalization and use the case-preserving helper for Modrinth project/version IDs and URL/file-name comparisons during export.

## Files Changed

- `src/main/java/controlador/extensions/ModrinthModpackService.java`
- `src/test/java/controlador/extensions/ModrinthModpackServiceTest.java`

## Verification

- `mvn -q -Dtest=ModrinthModpackServiceTest test`
- `mvn -q -DskipTests compile`
- `mvn test`

Commands were run with `JAVA_HOME=C:\Users\MJE\AppData\Local\Programs\Eclipse Adoptium\jdk-25.0.2.10-hotspot` because the default `java` on PATH is Java 8.

## Detailed Process

- `docs/fixes/process/extensions-modrinth-export-preserve-version-id-case.md`

## Regression Notes

Do not lowercase remote provider IDs unless the provider explicitly documents them as case-insensitive. Modrinth project and version IDs must be preserved exactly in API paths.

## Related Docs

- `docs/pipelines/extensions-pipeline.md`
- `docs/pipelines/filesystem-and-paths-pipeline.md`
