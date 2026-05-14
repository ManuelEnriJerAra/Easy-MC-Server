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

Quilt creation options should filter game metadata by Quilt's `stable` flag before applying the option cap. The wizard does not expose snapshots for non-Vanilla platforms, so unstable Quilt entries must not hide older stable releases.

Purpur creation options should use the API's `latest` build alias instead of resolving every Minecraft version's build metadata during list rendering. Resolving the concrete build number per version makes the wizard perform one remote call per listed version and can make platform selection feel stalled.

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
