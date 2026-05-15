# Geyser Modrinth Plugin Search Fix Process

## Status

Fixed

## Linked Fix

- `docs/fixes/extensions-geyser-modrinth-plugin-search.md`

## Scope

Fix marketplace search and queue preparation for Geyser on plugin servers when Modrinth reports the project as a `mod` while also advertising plugin loaders such as Paper and Spigot. Keep installation compatibility checks strict.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked `docs/README.md` and `docs/pipelines/extensions-pipeline.md`; marketplace changes belong in the extension provider/service path and need targeted tests. |
| DONE | 2. Reproduce metadata mismatch | Confirmed current Modrinth metadata for Geyser uses `project_type: mod` while exposing `paper` and `spigot` loaders. |
| DONE | 3. Implement provider classification fix | Updated Modrinth search entry inference so a requested plugin platform can override ambiguous mixed-loader project metadata. |
| DONE | 4. Harden download resolution | Made Modrinth fall back to the newest compatible build when a requested version id is not present after server filters are applied. |
| DONE | 5. Verify behavior | Ran targeted Modrinth provider tests and compile. |

## Implementation Notes

Geyser is a multi-loader project on Modrinth. Search results can include both mod loaders and plugin loaders, making the old platform-only inference ambiguous. Because the app already asks Modrinth for the current server platform and extension ecosystem, the search entry can safely use the requested platform when deciding whether a mixed-loader result should be treated as a plugin or mod.

The marketplace UI can ask providers to resolve the version id stored on a search result. For Modrinth, that id may not be present after the provider narrows versions by active loader and Minecraft version. Returning the newest compatible filtered version is safer than failing the queue action with no download plan.

Installation safety remains in `ExtensionCatalogService.matchesServerSafety(...)` and `ServerExtensionsService.ensureCatalogPlanCompatibleWithServer(...)`, so this fix only prevents valid marketplace results from disappearing or failing during download-plan preparation.

## Verification Notes

- `mvn -q -Dtest=ModrinthExtensionCatalogProviderTest test` passed.
- `mvn -q -DskipTests compile` passed.
- Maven emitted the expected Lombok/Guice `sun.misc.Unsafe` warning.
