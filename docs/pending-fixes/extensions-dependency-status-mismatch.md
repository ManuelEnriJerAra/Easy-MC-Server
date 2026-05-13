# Installed Extension Dependency Status Mismatch

## Status

Pending

## Area

Extension marketplace dependency resolution, installed extension metadata, and installed-tab status checks.

## Issue

Dependency handling can install all prompted dependencies, but the installed extensions tab may still warn that another required dependency is missing. The marketplace install flow and the installed-tab status flow appear to use different dependency identity rules, so a dependency that was resolved and installed from the catalog can later fail the installed-status check after the extension list is rescanned.

## Desired Behavior

If the marketplace resolves and installs all required dependencies for an extension, the installed tab should recognize those dependencies as satisfied after refresh/rescan. Dependency matching should be consistent across queue resolution, install blocking, metadata merge, and installed status diagnostics.

## Evidence

- `ExtensionMarketplaceDialog.dependencyMatchesInstalledExtension(...)` checks provider/project IDs, display names, extension IDs, file names, local dependency descriptions, and embedded dependency metadata through normalized matching.
- `ExtensionMarketplaceDialog.dependencyMatchesCandidate(...)` normalizes identifiers by stripping punctuation/case, so names like `Fabric API`, `fabric-api`, and local IDs can match.
- `ServerExtensionsService.findMissingDependencies(...)` powers installed-tab warnings through `assessInstalledExtension(...)`.
- `ServerExtensionsService.isDependencyInstalled(...)` only checks exact provider/project equality or exact display-name equality. It does not use the broader marketplace matcher behavior, normalized local IDs, file names, extension IDs, or local dependency descriptions.
- `ServerExtensionsService.readExtensionMetadata(...)` replaces `metadata.dependencies` with descriptor dependencies whenever the jar descriptor provides dependencies.
- `ServerExtensionsService.applySourceMetadata(...)` stores catalog plan dependencies after install, but a later detection/rescan can prefer embedded descriptor dependencies such as local Fabric/Forge mod IDs.
- `ServerExtensionsService.mergeInstalledMetadata(...)` only copies installed/cache dependencies into the detected extension when detected dependencies are empty, so catalog dependency identity can be lost when the jar declares its own local dependency IDs.

## Suggested Approach

Unify dependency identity matching between marketplace and installed-status code:

- Extract a shared dependency matcher/helper that both `ExtensionMarketplaceDialog` and `ServerExtensionsService` can use.
- Match dependencies by provider/project when available, then normalized project ID, display name, extension ID, file name without `.jar`, local dependency descriptions, and embedded dependency metadata.
- Preserve both catalog dependency identity and embedded local dependency identity during metadata merge instead of replacing one with the other.
- When `readExtensionMetadata(...)` finds descriptor dependencies after a catalog install, merge them with existing plan/cache dependencies and deduplicate by normalized key.
- Add tests where a catalog install stores remote dependency metadata, a rescan reads local mod IDs from the jar, and `assessInstalledExtension(...)` still recognizes the dependency as installed.

## Verification

- Install a mod with required dependencies from the marketplace and choose to add required dependencies.
- Refresh the installed tab and confirm the parent mod no longer reports missing dependencies.
- Restart the app or rescan extensions and confirm dependency status remains satisfied.
- Test dependency ID variants such as provider/project ID, local mod ID, display name with spaces/hyphens, and jar file name.
- Run targeted tests:

```bash
mvn -q -Dtest=ExtensionMarketplaceDependencyTest,ServerExtensionsServiceTest,PanelExtensionesTest test
```

- Run at least:

```bash
mvn -q -DskipTests compile
```

## Related Docs

- `docs/pipelines/extensions-pipeline.md`
- `docs/pipelines/filesystem-and-paths-pipeline.md`
