# Modpack Import Existing Mods

## Status

Fixed

## Original Issue

Importing a modpack into a mod server that already had mods could fail when a modpack entry matched an installed mod or wanted to write a jar filename already present in the managed mods folder.

## Root Cause

The import flow asked for Modrinth side options and a generic confirmation, but it never chose a policy for the server's current mods. Direct modpack imports then called the same catalog install path used for single-mod installs, where duplicate and filename conflicts are blocking errors.

## Solution

The import UI now asks how to handle existing mods when installed non-plugin entries are detected on a mod server. Keep mode skips filename conflicts before remote metadata resolution where possible, then evaluates each planned catalog download and skips already installed/conflicting entries with warnings. Replace mode validates the pack first, then deletes current managed mod jars and installs the modpack entries.

## Files Changed

- `src/main/java/controlador/GestorServidores.java`
- `src/main/java/vista/PanelExtensiones.java`
- `src/main/java/controlador/extensions/ModrinthModpackService.java`
- `src/test/java/controlador/GestorServidoresTest.java`
- `docs/pipelines/extensions-pipeline.md`

## Verification

- `mvn -q -DskipTests compile`
- `mvn -q -Dtest=GestorServidoresTest test`
- `mvn -q -Dtest=ModrinthModpackServiceTest test`

Commands were run with `JAVA_HOME=C:\Users\MJE\AppData\Local\Programs\Eclipse Adoptium\jdk-25.0.2.10-hotspot` because the default `java` on PATH is Java 8.

## Detailed Process

- `docs/fixes/process/extensions-modpack-import-existing-mods.md`

## Regression Notes

When changing modpack imports, verify both conflict policies. Keep mode must not download entries already represented by current mods. Replace mode must only delete non-symlink regular `.jar` files inside adapter-managed extension directories under the server folder, and only after pack metadata/plans have been resolved.

## Related Docs

- `docs/pipelines/extensions-pipeline.md`
- `docs/pipelines/filesystem-and-paths-pipeline.md`
