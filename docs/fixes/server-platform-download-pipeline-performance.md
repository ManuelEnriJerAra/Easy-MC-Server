# Server Platform Download Pipeline Performance

## Status

Fixed

## Original Issue

Server creation platform version listing used inconsistent stability filtering and avoidable remote metadata calls. Some adapters capped visible options, non-Vanilla stability metadata was missing or discarded, and Paper resolved one build endpoint per Minecraft version while rendering the list.

## Root Cause

The creation option pipeline treated Vanilla version type metadata as a special case. Non-Vanilla clients either returned only stable entries before the wizard could filter them, returned uncategorized options, or resolved expensive build details during listing because the option model did not consistently carry stability/channel information.

## Solution

`ServerCreationOption` now exposes common release/snapshot type constants and helpers. Fabric and Quilt return stable and unstable game versions with explicit type metadata. Forge and NeoForge keep the latest stable artifact for stable Minecraft releases, fall back to the latest downloadable loader artifact only when no stable loader exists for that Minecraft release, and keep snapshot-targeted artifacts individually so unstable loader builds do not replace stable defaults. Paper lists lightweight aliases and resolves the exact selected build lazily during download/install, falling back to the latest downloadable build if a newly released Minecraft version has no stable Paper build yet. Paper, Purpur, Fabric, Quilt, Forge, and NeoForge no longer apply arbitrary creation option caps.

The server creation wizard now enables its snapshot filter through `ServerPlatformAdapter.supportsUnstableCreationOptions()` instead of hard-coding Vanilla.

## Files Changed

- `src/main/java/controlador/GestorServidores.java`
- `src/main/java/controlador/platform/ServerCreationOption.java`
- `src/main/java/controlador/platform/ServerPlatformAdapter.java`
- `src/main/java/controlador/platform/VanillaServerPlatformAdapter.java`
- `src/main/java/controlador/platform/FabricServerPlatformAdapter.java`
- `src/main/java/controlador/platform/QuiltServerPlatformAdapter.java`
- `src/main/java/controlador/platform/ForgeServerPlatformAdapter.java`
- `src/main/java/controlador/platform/NeoForgeServerPlatformAdapter.java`
- `src/main/java/controlador/platform/FabricMetaClient.java`
- `src/main/java/controlador/platform/QuiltMetaClient.java`
- `src/main/java/controlador/platform/PaperDownloadsClient.java`
- `src/main/java/controlador/platform/PaperServerPlatformAdapter.java`
- `src/main/java/controlador/platform/PurpurDownloadsClient.java`
- `src/main/java/controlador/platform/ForgeRepositoryClient.java`
- `src/main/java/controlador/platform/NeoForgeRepositoryClient.java`
- `src/main/java/controlador/platform/VersionStringComparator.java`
- `src/test/java/controlador/GestorServidoresTest.java`
- `src/test/java/controlador/platform/ServerPlatformAdaptersTest.java`
- `src/test/java/vista/PlatformSelectorPanelTest.java`
- `docs/pipelines/platform-adapters-pipeline.md`
- `docs/pipelines/server-creation-pipeline.md`

## Verification

- `mvn -q "-Dtest=controlador.platform.ServerPlatformAdaptersTest" test`
- `mvn -q -DskipTests compile`
- `mvn test`

## Detailed Process

- `docs/fixes/process/server-platform-download-pipeline-performance.md`

## Regression Notes

Keep platform option listing lightweight. If an upstream API needs per-version detail calls for exact download URLs, list an alias and resolve the exact URL only for the selected option.

Keep stability metadata on `ServerCreationOption`; do not filter unstable entries inside clients unless the platform cannot install them. Wizard filters must remain the shared path for release-only and snapshots-enabled views.

## Related Docs

- `docs/pipelines/platform-adapters-pipeline.md`
- `docs/pipelines/server-creation-pipeline.md`
- `docs/fixes/server-platform-network-calls-cache.md`
- `docs/fixes/purpur-version-list-slow.md`
- `docs/fixes/quilt-stable-version-list.md`
