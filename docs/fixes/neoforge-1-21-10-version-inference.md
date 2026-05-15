# NeoForge 1.21.10 Version Inference

## Status

Fixed

## Original Issue

NeoForge Maven metadata includes `21.10.*` artifacts for Minecraft 1.21.10, but the app could infer those artifact versions as Minecraft `21.10.64` instead of `1.21.10`.

## Root Cause

`NeoForgeRepositoryClient.inferMinecraftVersion(...)` treated any artifact with a numeric third segment under 100 as a future semantic Minecraft version. That rule incorrectly caught legacy NeoForge artifacts such as `21.10.64`, where `21.10` means Minecraft `1.21.10` and `64` is the NeoForge build number.

## Solution

The semantic-version inference branch now applies only to artifact majors `22` and above. Current `21.*` NeoForge artifacts continue through the legacy mapping and infer Minecraft `1.21.x`.

## Files Changed

- `src/main/java/controlador/platform/NeoForgeRepositoryClient.java`
- `src/test/java/controlador/platform/ServerPlatformAdaptersTest.java`

## Verification

- `mvn -q "-Dtest=controlador.platform.ServerPlatformAdaptersTest#creationClients_debenParsearOpcionesYDirectorios+neoForgeCreationOptions_debenIgnorarBuildsInestablesDeLoaderParaReleasesMinecraft" test`

## Detailed Process

- `docs/fixes/process/neoforge-1-21-10-version-inference.md`

## Regression Notes

Keep tests for both artifact shapes: `21.10.64` should map to `1.21.10`, while future semantic artifacts such as `26.1.2.48-beta` should continue to map to `26.1.2`.

## Related Docs

- `docs/pipelines/platform-adapters-pipeline.md`
