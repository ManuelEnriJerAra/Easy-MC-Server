# Forge NeoForge Release Loader Fallbacks

## Status

Fixed

## Original Issue

Forge and NeoForge creation options could hide a stable Minecraft release when upstream only had beta/alpha loader artifacts for that release. This was visible in current NeoForge metadata where recent `26.1.2.*` artifacts are beta loader builds.

## Root Cause

The repository clients discarded all unstable loader artifacts for release-shaped Minecraft versions. That preserved stable defaults when both stable and beta loader builds existed, but it also removed the only available build when a Minecraft release had no stable loader yet.

## Solution

Forge and NeoForge now track a fallback latest loader artifact per stable Minecraft release. Stable loader artifacts still take priority; the fallback is used only if no stable loader artifact exists for that Minecraft version.

## Files Changed

- `src/main/java/controlador/platform/ForgeRepositoryClient.java`
- `src/main/java/controlador/platform/NeoForgeRepositoryClient.java`
- `src/test/java/controlador/platform/ServerPlatformAdaptersTest.java`
- `docs/pipelines/platform-adapters-pipeline.md`
- `docs/pipelines/server-creation-pipeline.md`

## Verification

- `mvn -q "-Dtest=controlador.platform.ServerPlatformAdaptersTest#forgeCreationOptions_debenSepararBuildsEstablesEInestables+neoForgeCreationOptions_debenIgnorarBuildsInestablesDeLoaderParaReleasesMinecraft" test`
- `mvn test`

## Detailed Process

- `docs/fixes/process/forge-neoforge-release-loader-fallbacks.md`

## Regression Notes

Do not expose beta/alpha loader artifacts as snapshot options for stable Minecraft releases. Keep them as release fallbacks only when no stable loader exists.

## Related Docs

- `docs/pipelines/platform-adapters-pipeline.md`
- `docs/pipelines/server-creation-pipeline.md`
