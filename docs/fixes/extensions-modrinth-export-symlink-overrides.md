# Modrinth Export Symlink Overrides

## Status

Fixed

## Original Issue

Modrinth `.mrpack` export collected override files with regular-file checks that followed symbolic links. A symlink under `config/` or `defaultconfigs/` could point outside the server directory and be packaged under a safe-looking archive path.

## Root Cause

`writeOverrides(...)` used `Files.isRegularFile(...)` without `LinkOption.NOFOLLOW_LINKS`, and `writeOverrideFile(...)` copied whatever path it received.

## Solution

`ModrinthModpackService` now collects and copies override files only when `FileSystemSafety.isRegularFileNoFollow(...)` accepts them. Override roots are also checked without following links.

## Files Changed

- `src/main/java/controlador/extensions/ModrinthModpackService.java`
- `src/test/java/controlador/extensions/ModrinthModpackServiceTest.java`

## Verification

- `mvn -q -Dtest=ModrinthModpackServiceTest test`
- `mvn -q -DskipTests compile`

## Detailed Process

- `docs/fixes/process/extensions-modrinth-export-symlink-overrides.md`

## Regression Notes

Any future export path that packages local override files should use no-follow regular-file checks before copying into an archive.

## Related Docs

- `docs/pipelines/extensions-pipeline.md`
- `docs/pipelines/filesystem-and-paths-pipeline.md`
