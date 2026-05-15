# Platform Adapters Pipeline

Use this guide before editing Minecraft platform detection, validation, installation, creation options, or startup commands.

## Package

Main package: `src/main/java/controlador/platform`.

Core API:

- `ServerPlatformAdapter`
- `AbstractServerPlatformAdapter`
- `ServerPlatformAdapters`
- `ServerPlatformProfile`
- `ServerInstallationRequest`
- `ServerCreationOption`
- `ServerValidationResult`

Adapters include Vanilla, Forge, NeoForge, Fabric, Quilt, Paper, Bukkit, Spigot, Purpur, and Pufferfish.

Automated creation is supported only by adapters that override `supportsAutomatedCreation()`. Bukkit, Spigot, and Pufferfish currently provide import/detection support for existing installations, not automated creation.

## Adapter Responsibilities

Each adapter can provide:

- platform identity via `getPlatform()`
- detection priority
- server directory detection
- validation
- managed extension directories
- capabilities
- automated creation availability
- creation options
- install behavior
- start command overrides

Keep adapter behavior platform-specific. Shared logic belongs in `AbstractServerPlatformAdapter`, `ServerPlatformInstallSupport`, or small helper clients.

## Detection

Detection is usually based on:

- jar names
- marker files
- generated libraries/argument files
- metadata files
- known platform directory structures

`ServerPlatformAdapters` coordinates adapter ordering. Detection priority matters when multiple markers exist.

## Creation Options

Adapters that support automated creation return `ServerCreationOption` values.

Remote version clients:

- `FabricMetaClient`
- `QuiltMetaClient`
- `ForgeRepositoryClient`
- `NeoForgeRepositoryClient`
- `PaperDownloadsClient`
- `PurpurDownloadsClient`

When changing a remote client, prefer primary APIs and keep parsing defensive. Network availability can fail; UI should surface a useful message.

Remote metadata lookups follow the shared `PlatformRemoteLookupPolicy`:

- connect timeout: 5 seconds
- read timeout: 10 seconds
- successful metadata cache: in-memory only, 10 minutes
- failed metadata lookup cooldown: in-memory only, 30 seconds
- refresh behavior: app restart or cache expiry; do not add persistent metadata cache unless the UX explicitly needs it
- user-facing lookup failures should use the shared remote-platform message so platform adapters fail consistently

The policy covers JSON metadata through `CachedPlatformHttpClient` and Maven metadata XML through the repository clients. It does not cache actual server jars or installers.

Creation options should populate `ServerCreationOption.versionType` from Minecraft version stability. When upstream metadata exposes game-version stability, use that. When only text is available, classify the Minecraft version string for snapshot, pre-release, release-candidate, or other unstable Minecraft-version markers. Do not classify a stable Minecraft release as a snapshot only because the loader/build artifact has a beta, alpha, experimental, or dev qualifier. The server creation wizard applies the release/snapshot filter consistently across adapters that opt into unstable creation options, and when both release and snapshot filters are active it should display both groups.

Vanilla creation options come from Mojang's manifest, but known release versions before the first downloadable Java server jar (`1.2.5`) should not be offered as installable options. Keep the selected-version server-jar URL check as the final guard, and avoid probing every manifest entry's version JSON while rendering the Vanilla list.

The snapshots checkbox means "Minecraft snapshot/pre-release/release-candidate versions", not "unstable loader builds for stable Minecraft releases." Forge and NeoForge may need alpha or prerelease loader artifacts to target a Minecraft snapshot; those should be shown as Minecraft snapshot options. Beta/alpha loader artifacts that target stable Minecraft releases should not appear under the snapshots checkbox unless a separate loader-channel filter is added. If no stable loader artifact exists yet for a stable Minecraft release, use the latest downloadable loader artifact as the release option so newly supported Minecraft versions are not hidden.

When a loader metadata endpoint lists beta loaders before stable loaders, prefer the latest stable-looking loader for release defaults and only fall back to the first loader if no stable-looking loader is available.

Sort Minecraft versions with Minecraft-specific rules rather than raw token comparison. Weekly snapshots such as `26w14a` should not sort above later semantic snapshots such as `26.2-snapshot-7`.

Do not cap remote creation options before the wizard can apply release/snapshot filters. Stable releases must not be hidden because unstable entries appeared first in an upstream response.

Paper creation options should list Minecraft versions without resolving one build endpoint per version. Classify Paper pre-release, release-candidate, and other unstable Minecraft versions from the project version list, then resolve the selected build lazily when the user installs/downloads that option. Release-shaped Paper options should prefer stable builds, but may fall back to the latest downloadable build when a newly released Minecraft version has no stable Paper build yet. Purpur creation options should use the API's `latest` build alias instead of resolving every Minecraft version's build metadata during list rendering, while still classifying unstable Minecraft version strings when the API exposes them. Resolving concrete build numbers per version makes the wizard perform one remote call per listed version and can make platform selection feel stalled.

For conversion flows, compare Minecraft versions through `ServerCreationOption.isCompatibleMinecraftVersion(...)` instead of raw string equality so Vanilla-style snapshot names and upstream loader artifact naming variants can still match. Use canonical values for comparison only; keep provider artifact versions and user-facing option labels faithful to upstream naming unless a small display normalization, such as Forge `_pre` to `-pre`, is already established locally.

Version detection and persistence must accept modern semantic snapshot IDs such as `26.2-snapshot-7`, `26.1-pre-3`, and `26.1.2-rc-1`. If a detector truncates those values to the release prefix, conversion filtering will fail because loader metadata keeps the full Minecraft snapshot ID. When reading jar `version.json`, prefer the full Minecraft `id` over a shorter release-shaped `name`, for example `id=26.2-snapshot-7` and `name=26.2`.

Metadata version hints and jar `version.json` values should share the same Minecraft-version normalization rules. Known platform-suffixed values such as `1.20.1-forge-47.4.0`, `1.20.1-neoforge-47.4.0`, or `1.20.1-fabric-0.15.11` should normalize to `1.20.1`, while unrelated hyphenated text should still be rejected by the strict generic matcher.

Forge runtime jars can report the Forge loader version, for example `41.1.0`, in their own `version.json`. Do not use that value as `Server.version`. Forge detection should prefer explicit Minecraft metadata from run scripts, Forge args, library coordinates, modpack manifests, or real Minecraft server classes, and skip Forge-marked top-level jars as generic Minecraft-version sources.

Forge Maven artifact parsing should identify the Minecraft version from the artifact prefix. Older official artifacts can append an extra Minecraft branch suffix after the Forge build, for example `1.10-12.18.0.2000-1.10.0`; those suffixes are part of the Forge artifact coordinate and must not create pseudo Minecraft-version rows.

Forge automated creation uses installer jars, so creation options must exclude old Forge releases that only publish legacy ZIP artifacts. Keep Minecraft versions older than `1.5.2` out of the Forge creation list unless a dedicated legacy ZIP install path is intentionally implemented.

## Installation

Installation typically:

1. Creates or validates the target directory.
2. Downloads a jar or installer.
3. Runs installer if needed.
4. Writes EULA acceptance when requested.
5. Ensures default `server.properties`.
6. Sets `Server` platform/version metadata.

Use `ServerPlatformInstallSupport` for common setup tasks.

Do not overwrite user files unnecessarily. Preserve existing server data on conversion/upgrade flows.

Vanilla server jar downloads are large enough that they should not share the short JSON metadata read timeout. Download them with the dedicated large-download timeout/retry path in `MojangAPI.descargar(...)`, write to a temporary `.part` file first, and move it into place only after the stream completes.

## Startup

Most platforms start with `java -jar <server jar>`. Some modded platforms require generated argument files or special launchers.

Before changing startup, inspect:

- adapter `buildStartProcess(...)`
- `requiresExecutableJarForStart()`
- `ServerJarLocator`
- `MinecraftServerJarInspector`

## Tests

Relevant tests:

- `ServerPlatformAdaptersTest`
- platform-related coverage in `GestorServidoresTest`

Run targeted tests plus compile when changing adapters.
