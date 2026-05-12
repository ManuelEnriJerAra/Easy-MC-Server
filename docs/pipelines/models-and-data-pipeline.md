# Models And Data Pipeline

Use this guide before editing model classes or cross-cutting data contracts.

## Core Models

Package: `src/main/java/modelo`.

- `Server`: main server domain object. Stores identity, directory, display metadata, platform/version, runtime process, console listeners, and extension data.
- `ServerConfig`: persisted per-server config.
- `ServerProperties`: server.properties representation/helper.
- `World`: world identity and metadata.
- `EasyMCConfig`: app-level persisted config.
- `MinecraftConstants`: shared Minecraft option constants.

## Extension Models

Package: `src/main/java/modelo/extensions`.

Important types:

- `ServerPlatform`
- `ServerLoader`
- `ServerEcosystemType`
- `ServerCapability`
- `ServerExtension`
- `ServerExtensionType`
- `ExtensionSource`
- `ExtensionSourceType`
- `ExtensionLocalMetadata`
- `ExtensionRemoteDependency`
- `ExtensionInstallState`
- `ExtensionUpdateState`

These are used by both controller services and UI. Treat changes as cross-cutting.

## Controller Records

Many controller package records are API contracts between services and UI, such as:

- `ServerCreationOption`
- `ServerPlatformProfile`
- `ServerInstallationRequest`
- `ExtensionCatalogEntry`
- `ExtensionCatalogDetails`
- `ExtensionCatalogVersion`
- `ExtensionDownloadPlan`
- `ExtensionInstallResolution`
- `ExtensionCompatibilityReport`
- world preview/player records

When changing record fields, update all constructors, UI renderers, tests, and cache serialization if applicable.

## Runtime Fields

Some model fields are runtime-only, especially process handles/listeners on `Server`. Do not accidentally serialize process state.

## Compatibility Rules

Preserve backward compatibility for persisted config/cache where reasonable:

- provide defaults for missing fields
- handle nulls defensively
- avoid renaming serialized fields casually

## Tests

Relevant tests:

- `ServerModelPlatformTest`
- service tests that consume model contracts

Run broader tests when changing shared model contracts.
