# Quilt Loader Stable Default

## Status

Fixed

## Original Issue

Quilt creation options could use a beta Quilt loader for release Minecraft versions because live Quilt loader metadata lists beta builds before the latest stable loader.

## Root Cause

`QuiltMetaClient` selected the first loader entry from upstream metadata without checking whether the loader version itself looked stable.

## Solution

`QuiltMetaClient` now prefers the first release-looking loader version and falls back to the first loader only if no stable-looking loader exists.

## Files Changed

- `src/main/java/controlador/platform/QuiltMetaClient.java`
- `src/test/java/controlador/platform/ServerPlatformAdaptersTest.java`

## Verification

- `mvn -q "-Dtest=controlador.platform.ServerPlatformAdaptersTest#detect_debeInferirSnapshotForgeDesdeCoordenadaRuntime+detect_debeInferirSnapshotNeoForgeDesdeCoordenadaRuntime+creationClients_debenParsearOpcionesYDirectorios" test`

## Detailed Process

- `docs/fixes/process/quilt-loader-stable-default.md`

## Regression Notes

Keep fixture coverage where `0.30.0-beta.7` appears before `0.29.2`, and assert the encoded Quilt platform version starts with the stable loader.

## Related Docs

- `docs/pipelines/platform-adapters-pipeline.md`
