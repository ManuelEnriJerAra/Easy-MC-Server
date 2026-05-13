# Filesystem And Paths Pipeline

Use this guide before editing file operations, imports/exports, backups, paths, or directory migration.

## General Rules

- Resolve paths defensively.
- Avoid destructive operations unless the user explicitly chose that flow.
- Preserve user files when converting, importing, or upgrading.
- Keep app-managed metadata separate from Minecraft-owned files where possible.
- Use `controlador.AppPaths` for app-managed user data roots: config, cache, locks, and generated stats.
- Surface IO failures with actionable Spanish messages in UI flows.

## Server Directories

`GestorServidores` owns most server-level directory operations:

- import existing server
- create new server
- backup/preserve files
- remove from app
- delete when explicitly requested

Platform adapters may create or validate platform-specific files.

## World Directories

`GestorMundos` owns world-level filesystem operations:

- sync managed worlds
- import/export worlds
- switch active world
- generate metadata for new worlds
- migrate legacy world layout into managed layout

World metadata helpers:

- `WorldFilesService`
- `WorldDataReader`
- `WorldStorageAnalyzer`

## Extension Files

`ServerExtensionsService` owns extension jar operations:

- detect installed jars
- copy manual installs
- install catalog downloads
- remove extensions
- validate jar contents
- persist installed extension cache

Extension directories are resolved through platform adapters. Do not hard-code `mods` or `plugins` unless the adapter guarantees it.

## Backups And Preservation

When changing conversion/upgrade flows, inspect preservation lists in `GestorServidores`, such as preservable config files and extension directories.

## Tests

Relevant tests:

- `GestorMundosTest`
- `WorldFilesServiceTest`
- `WorldStorageAnalyzerTest`
- `ServerExtensionsServiceTest`
- `GestorServidoresTest`

Run targeted tests for any path or filesystem behavior change.
