# Platform Runtime Snapshot Coordinate Detection

## Status

Fixed

## Original Issue

Forge and NeoForge detection could collapse runtime library coordinates for Minecraft snapshots or pre-releases into release-shaped versions.

## Root Cause

The Forge coordinate regex captured only a release prefix from `net/minecraftforge/forge/...`, and the NeoForge regex captured only the first three numeric segments before suffix metadata. That dropped values needed to infer snapshot/pre-release Minecraft targets.

## Solution

Forge coordinate detection now captures the full Minecraft version pattern, including `pre`, `rc`, and snapshot markers. NeoForge coordinate detection now captures the complete artifact coordinate before passing it to `NeoForgeRepositoryClient.inferMinecraftVersion(...)`.

## Files Changed

- `src/main/java/controlador/platform/MinecraftServerVersionDetector.java`
- `src/test/java/controlador/platform/ServerPlatformAdaptersTest.java`

## Verification

- `mvn -q "-Dtest=controlador.platform.ServerPlatformAdaptersTest#detect_debeInferirSnapshotForgeDesdeCoordenadaRuntime+detect_debeInferirSnapshotNeoForgeDesdeCoordenadaRuntime+creationClients_debenParsearOpcionesYDirectorios" test`

## Detailed Process

- `docs/fixes/process/platform-runtime-snapshot-coordinate-detection.md`

## Regression Notes

Keep runtime-coordinate tests for both Forge `1.20.3-pre1-49.0.0` and NeoForge `26.1.0.0-alpha.2+snapshot-1`.

## Related Docs

- `docs/pipelines/platform-adapters-pipeline.md`
