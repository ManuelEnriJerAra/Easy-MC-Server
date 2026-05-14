# Purpur Version List Slow

## Status

Fixed

## Original Issue

Purpur versions took a long time to load in the server creation wizard.

## Root Cause

`PurpurDownloadsClient.listCreationOptions()` fetched the project version list and then called `/v2/purpur/{minecraftVersion}` once per Minecraft version to resolve the latest build number before showing the options. On a cold cache, that meant dozens of serial remote calls.

## Solution

Creation options now store Purpur's `latest` build alias and build download URLs like `/v2/purpur/{minecraftVersion}/latest/download`. The version list is populated from the project endpoint alone, while explicit build IDs still work when provided.

## Files Changed

- `src/main/java/controlador/platform/PurpurDownloadsClient.java`
- `src/test/java/controlador/platform/ServerPlatformAdaptersTest.java`
- `docs/pipelines/platform-adapters-pipeline.md`

## Verification

- `mvn -q "-Dtest=controlador.platform.ServerPlatformAdaptersTest" test`
- `mvn -q -DskipTests compile`

## Detailed Process

- `docs/fixes/process/purpur-version-list-slow.md`

## Regression Notes

Do not reintroduce per-version Purpur build lookups in the option listing path. If the UI needs the concrete latest build number, load it lazily for the selected version rather than for every version.

## Related Docs

- `docs/pipelines/platform-adapters-pipeline.md`
