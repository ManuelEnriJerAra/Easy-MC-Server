# Installed Extension Dependency Status Mismatch

## Status

Fixed

## Original Issue

Installing an extension with prompted dependencies could succeed, but the installed extensions tab could still warn that a required dependency was missing after refresh or rescan.

## Root Cause

The marketplace queue and the installed-tab diagnostics used different dependency identity rules. The marketplace matched provider/project IDs, names, file names, local IDs, and embedded metadata with normalized punctuation-insensitive matching. `ServerExtensionsService` only checked exact provider/project or display-name keys. Rescans could also replace catalog dependency metadata with embedded descriptor dependencies, losing the remote identity that the install plan used.

## Solution

Dependency matching now flows through `ExtensionDependencyMatcher`, shared by the marketplace dialog and installed-status service. Installed-status checks compare required dependencies against installed extensions using the same normalized identity logic as the marketplace.

Installed metadata merge now preserves both catalog dependency identity and embedded descriptor dependency identity, deduplicing by normalized dependency keys.

## Files Changed

- `src/main/java/controlador/extensions/ExtensionDependencyMatcher.java`
- `src/main/java/controlador/extensions/ServerExtensionsService.java`
- `src/main/java/vista/ExtensionMarketplaceDialog.java`
- `src/test/java/controlador/extensions/ServerExtensionsServiceTest.java`
- `src/test/java/vista/ExtensionMarketplaceDependencyTest.java`

## Verification

- `mvn -q "-Dtest=ExtensionMarketplaceDependencyTest,ServerExtensionsServiceTest" test`

Passed. Maven emitted the expected Lombok `sun.misc.Unsafe` warning.

## Detailed Process

- `docs/fixes/process/extensions-dependency-status-mismatch.md`

## Regression Notes

Dependency matching must stay shared between marketplace queue resolution and installed-tab diagnostics. When jar descriptors declare local dependency IDs after a catalog install, merge those IDs with catalog plan dependencies instead of replacing one source with the other.

## Related Docs

- `docs/pipelines/extensions-pipeline.md`
- `docs/pipelines/filesystem-and-paths-pipeline.md`
