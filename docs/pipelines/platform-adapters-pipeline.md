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
