# Modrinth Broad Search Download Resolution Fix Process

## Status

Fixed

## Linked Fix

- `docs/fixes/extensions-modrinth-broad-search-download-resolution.md`

## Scope

Fix marketplace results that appear compatible after a typed Modrinth search but later fail to resolve a downloadable build for the selected server. Keep provider search broad enough for discovery, but only call a result compatible when version-level data proves it. Cover current Modrinth/Nova `26.1.x` Minecraft versions as part of the same mismatch family.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked `docs/README.md` and `docs/pipelines/extensions-pipeline.md`; marketplace compatibility and download-plan work belongs in the extensions pipeline with targeted tests. |
| DONE | 2. Investigate mismatch | Compared broad Modrinth search metadata with `/project/{id}/version` filtering and found project-level loader/version metadata can imply compatibility when no matching server build exists. |
| DONE | 3. Implement provider and UI fix | Tightened Modrinth rows further because filtered search facets are still project-level, not version-level. |
| DONE | 4. Add modern version parsing | Extended shared Minecraft version detection to include numeric `26.1.x` style versions used by current Modrinth data. |
| DONE | 5. Block unresolved UI queue attempts | Changed marketplace row actions and empty detail version handling so no-build entries load details and show a blocked state instead of queueing a failed preparation item. |
| DONE | 6. Fix action-state override | Stopped the queue button label refresh from re-enabling the button after availability checks disabled it. |
| DONE | 7. Align catalog safety fallback | Made catalog download safety infer ecosystem from a known server platform when persisted ecosystem metadata is unknown. |
| DONE | 8. Remove project-global version ids | Stopped Modrinth search rows and unresolved queue preparation items from carrying `latest_version` before server-filtered resolution. |
| DONE | 9. Tighten dialog caches | Included server context in dialog download cache keys and entry metadata in compatibility cache keys. |
| DONE | 10. Verify behavior | Re-ran targeted Modrinth provider/service/catalog/dialog tests and compile. |

## Implementation Notes

Typed marketplace searches intentionally omit loader and Minecraft version facets to improve discovery. Modrinth search results then expose project-level categories and supported Minecraft versions, not a guaranteed loader/version/file combination. That metadata is useful for browsing but too broad for a green compatible badge or direct queue preparation.

Download-plan resolution is version-level. The durable boundary is: search rows can be broad, details and queue plans must be resolved against the active server platform and Minecraft version.

Quick-add from a search row now resolves without passing the row's latest version id, so it can select the newest compatible build for the server. Version combo selections still pass an exact version id and fail cleanly if that selected build is not compatible with the active server constraints.

Follow-up investigation with TaCZ/Nova-style examples showed that even filtered Modrinth search facets can be project-level: a project can match `categories:forge` and `versions:1.21.1` while only offering Forge files for 1.20.1 and 1.21.1 files for another loader. Modrinth search rows therefore should not expose platform/version sets to Dora's green compatibility badge. Details remain server-filtered through `/project/{id}/version`.

Follow-up investigation with Nova by xenondevs showed another concrete leak: Modrinth's current Nova builds use Minecraft versions such as `26.1.1` and `26.1.2`. Dora's shared parser only recognized `1.xx` versions, so download resolution and service-level compatibility could treat `26.1.x` as unknown and skip the version filter/check. The parser now recognizes two-digit-major numeric Minecraft versions as well.

The dialog could still show `No se ha podido preparar una descarga compatible` when the user clicked the result-row plus on a row that was only marked for review, or when details returned no compatible versions and the UI created a fallback option from the search row's latest version id. Rows that are not confidently compatible now load details first, and empty server-filtered details show `No hay build compatible para este servidor.` without creating an installable fallback.

Careful follow-up review found the direct source of the lingering message: `refreshSelectionActionState()` disabled the queue button correctly, then `updatePrimaryActionLabels()` unconditionally re-enabled it for any selected entry. That allowed a blocked or unresolved selection to call `enqueueEntryAsync(...)` anyway. The label refresh now preserves the availability result unless the selected item is already installed or already queued, where the button is intentionally used for remove/uninstall.

`ExtensionCatalogService.matchesServerSafety(...)` also used the raw persisted ecosystem only. This was stricter than `ServerExtensionQueryFactory`, which already falls back from a known platform to that platform's default ecosystem. The safety filter now uses the same fallback so a Paper server with stale/unknown ecosystem metadata does not drop a valid Nova plugin plan.

The next failing path was the Modrinth search hit's `latest_version`: it is the latest project version globally, not the latest version compatible with the active server. For Nova, a search result for a 1.21.x server can carry a `26.1.x` version id. Search entries now leave `versionId` and `version` empty, and unresolved queue preparation items keep `versionId` empty until `ExtensionDownloadPlan` is resolved. The dialog download-plan cache also includes server platform, loader, and version, and compatibility caching now includes the actual platform/version metadata sets used for the assessment.

## Verification Notes

- `mvn -q -Dtest=ModrinthExtensionCatalogProviderTest test` passed after the stricter TaCZ/Nova and Nova/xenondevs follow-ups.
- `mvn -q -Dtest=ServerExtensionsServiceTest test` passed after adding service-level `26.1.x` coverage.
- `mvn -q "-Dtest=ModrinthExtensionCatalogProviderTest,ServerExtensionsServiceTest" test` passed after blocking unresolved UI queue attempts.
- `mvn -q "-Dtest=ModrinthExtensionCatalogProviderTest,ServerExtensionsServiceTest,ExtensionCatalogServiceTest" test` passed after the action-state and safety-filter review fixes. The run logs expected warnings from existing broken-provider tests.
- `mvn -q "-Dtest=ModrinthExtensionCatalogProviderTest,ExtensionMarketplaceDependencyTest,ExtensionCatalogServiceTest,ServerExtensionsServiceTest" test` passed after removing project-global search version ids and tightening dialog cache keys.
- `mvn -q -DskipTests compile` passed after the stricter TaCZ/Nova and Nova/xenondevs follow-ups.
- Maven emitted the expected Lombok/Guice `sun.misc.Unsafe` warning.
