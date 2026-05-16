# Hangar Direct External Download Resolution Fix Process

## Status

Fixed

## Linked Fix

- `docs/fixes/extensions-hangar-direct-external-download-resolution.md`

## Scope

Fix Hangar download resolution for plugins whose Hangar version points directly to an external jar asset instead of a Hangar CDN URL. Preserve the existing safety behavior that rejects generic external websites.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Inspect Hangar provider | Reviewed `HangarExtensionCatalogProvider` download resolution and its external Modrinth fallback. |
| DONE | 2. Verify live Nova metadata | Checked Hangar API data for `xenondevs/Nova` on Paper `26.1.2`; version `0.23.0` exposes a GitHub `.jar` in `downloads.PAPER.externalUrl`. |
| DONE | 3. Implement direct jar handling | Added direct external `.jar` URL handling while leaving non-jar external websites unresolved. |
| DONE | 4. Add regression test | Covered Nova-style Paper `26.1.2` GitHub release asset resolution in `HangarExtensionCatalogProviderTest`. |
| DONE | 5. Verify behavior | Ran targeted Hangar provider tests. |

## Implementation Notes

Hangar's `externalUrl` field can mean different things. For some projects it is a provider page, such as a Modrinth project page, which Dora already resolves through Modrinth's API. For Nova `0.23.0`, the field is already the final GitHub release jar:

`https://github.com/xenondevs/Nova/releases/download/0.23.0/Nova-0.23.0%2BMC-26.1.2.jar`

The provider now treats `http` and `https` external URLs whose raw path ends in `.jar` as direct downloads. The file name is derived from the raw path and URL-decoded while preserving literal plus signs. Non-jar external URLs still return no plan unless another resolver, such as the existing Modrinth external resolver, produces a concrete jar.

## Verification Notes

- `mvn -q -Dtest=HangarExtensionCatalogProviderTest test` passed after adding the Nova `26.1.2` direct external jar regression.

