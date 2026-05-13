# Modrinth Export Symlink Overrides

## Status

Pending

## Area

`controlador.extensions.ModrinthModpackService`

## Issue

Modrinth `.mrpack` export walks `config/` and `defaultconfigs/` and filters files with `Files.isRegularFile(file)`, which follows symbolic links by default.

A symlink inside an allowed override directory can point outside the server directory and still be packaged into the `.mrpack` under a safe-looking relative path.

## Desired Behavior

Export should not follow symlinks when collecting override files. Only real regular files inside the server directory should be copied into the archive.

## Evidence

- `src/main/java/controlador/extensions/ModrinthModpackService.java`
  - `writeOverrides(...)`
  - `writeOverrideFile(...)`
- `writeOverrides(...)` currently uses `Files.walk(root)` and `.filter(Files::isRegularFile)`.
- `Files.isRegularFile(Path)` follows symlinks unless `LinkOption.NOFOLLOW_LINKS` is passed.

## Suggested Approach

Change override collection and copy checks to skip symbolic links and use `Files.isRegularFile(path, LinkOption.NOFOLLOW_LINKS)`.

Add a regression test that creates a symlink under `config/` pointing outside the server directory and verifies the target file is not included in the exported `.mrpack`.

## Verification

- `mvn -q -Dtest=ModrinthModpackServiceTest test`
- `mvn -q -DskipTests compile`

## Related Docs

- `docs/pipelines/extensions-pipeline.md`
- `docs/pipelines/filesystem-and-paths-pipeline.md`
