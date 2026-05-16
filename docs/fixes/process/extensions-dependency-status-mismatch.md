# Installed Extension Dependency Status Mismatch Fix Process

## Status

Fixed

## Linked Fix

- `docs/fixes/extensions-dependency-status-mismatch.md`

## Scope

Resolve dependency-status drift between marketplace dependency resolution and installed extension diagnostics. This covers dependency identity matching and metadata merge behavior after rescans.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked extension, filesystem, and model guidance before touching dependency matching and metadata persistence. |
| DONE | 2. Inspect mismatch | Compared marketplace dependency matching with installed-status matching and confirmed the service used narrower exact keys. |
| DONE | 3. Share matcher | Added `ExtensionDependencyMatcher` and delegated marketplace matching wrappers to it. |
| DONE | 4. Align installed diagnostics | `ServerExtensionsService` now checks required dependencies against installed extensions through the shared matcher. |
| DONE | 5. Preserve dependency identities | Metadata merge now keeps catalog and embedded descriptor dependency identities, dedupliced by normalized keys. |
| DONE | 6. Verify behavior | Added regression coverage and ran targeted extension tests. |

## Implementation Notes

The shared matcher normalizes by trimming, lowercasing, stripping `.jar`, and removing non-alphanumeric punctuation. It prefers provider/project matches when both sides provide them, then falls back through project ID, display name, file name, local ID, local dependency descriptions, and embedded dependency metadata.

Keeping installed-status matching extension-based rather than key-only avoids losing the richer identity surface used by the marketplace while still batching profile and ecosystem resolution through `ExtensionStatusContext`.

## Verification Notes

- `mvn -q "-Dtest=ExtensionMarketplaceDependencyTest,ServerExtensionsServiceTest" test` passed.
- Maven emitted the expected Lombok `sun.misc.Unsafe` warning.
