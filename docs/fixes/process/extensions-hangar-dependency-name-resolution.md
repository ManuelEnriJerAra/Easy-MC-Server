# Hangar Dependency Name Resolution Process

## Status

Completed

## Linked Fix

- `docs/fixes/extensions-hangar-dependency-name-resolution.md`

## Scope

Fix Hangar dependency download resolution when a dependency reports a display-style identifier such as `NBT+API` instead of the canonical Hangar project id or namespace slug, or when it references an external/non-Hangar dependency such as `PacketEvents`. Keep the change scoped to Hangar project lookup and dependency download preparation.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked `docs/README.md` and `docs/pipelines/extensions-pipeline.md`; dependency resolution belongs to the marketplace/extensions pipeline. |
| DONE | 2. Reproduce API shape | Confirmed live Hangar returns 404 for `/projects/NBT+API`, while `/projects/NBTAPI`, `/projects/tr7zw/NBTAPI`, and project search resolve the canonical `NBTAPI` project. |
| DONE | 3. Implement lookup fallback | Added a Hangar project search fallback, using it before direct lookup for non-numeric simple identifiers and after direct lookup failures otherwise. Exact normalized matches are required. |
| DONE | 4. Preserve canonical plan identity | Download plans resolved through the fallback now carry the canonical Hangar project id from the resolved project node. |
| DONE | 5. Verify behavior | Added regression tests for `NBT+API` resolution and `PacketEvents` unresolved handling, then ran targeted Hangar provider tests plus compile. |

## Implementation Notes

`ExtensionMarketplaceDialog.resolveDependencyDownloadPlan(...)` forwards `ExtensionDependency.projectId()` to `ExtensionCatalogService.resolveDownload(...)`. For Hangar, some version dependency metadata can use a display-ish identifier rather than the canonical project id exposed by search results. Calling `/api/v1/projects/NBT+API` causes a 404, which produced the warning reported by the user.

`HangarExtensionCatalogProvider.readProject(...)` now recognizes non-numeric simple identifiers before calling the direct endpoint, then performs `/projects?query=...&platform=PAPER`. Other direct lookup failures still fall back to that same search. The fallback intentionally does not take the first search result blindly; it requires the requested dependency identifier to normalize exactly to the result id, name, namespace slug, or owner/slug. This resolves `NBT+API` to `NBTAPI` without broadening dependency resolution to unrelated search hits or logging a direct 404 first.

When Hangar search completes but has no exact match, as with `PacketEvents` returning addons and plugins that merely mention PacketEvents, the provider returns no download plan. Dependency resolution can then continue through the existing unresolved-dependency warning path instead of logging a provider exception.

The download plan uses `projectNode.id` after fallback so queue keys and persisted install metadata use Hangar's canonical project id.

## Verification Notes

- `mvn -q "-Dtest=controlador.extensions.HangarExtensionCatalogProviderTest" test`
- `mvn -q -DskipTests compile`
- Maven emitted the expected `sun.misc.Unsafe` warning from Lombok-related startup output.
