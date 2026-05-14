# Quilt Stable Version List

## Status

Fixed

## Original Issue

Quilt availability could look limited to recent `26.x` entries in the server creation wizard even though Quilt metadata includes older stable Minecraft releases.

## Root Cause

`QuiltMetaClient` created options from every `/v3/versions/game` entry, including snapshots and release candidates, then applied the option cap. Unstable entries could consume the cap before older stable releases were included.

## Solution

`QuiltMetaClient` now filters game metadata by the `stable` flag before creating options. The Quilt option cap was raised to 80 so current stable releases fit without exposing unstable entries.

## Files Changed

- `src/main/java/controlador/platform/QuiltMetaClient.java`
- `src/test/java/controlador/platform/ServerPlatformAdaptersTest.java`
- `docs/pipelines/platform-adapters-pipeline.md`

## Verification

- `mvn -q "-Dtest=controlador.platform.ServerPlatformAdaptersTest" test`
- `mvn -q -DskipTests compile`

## Detailed Process

- `docs/fixes/process/quilt-stable-version-list.md`

## Regression Notes

Do not apply the Quilt option cap before filtering stable releases. If snapshot support is added for Quilt later, expose it deliberately in the wizard instead of mixing unstable entries into the default release list.

## Related Docs

- `docs/pipelines/platform-adapters-pipeline.md`
