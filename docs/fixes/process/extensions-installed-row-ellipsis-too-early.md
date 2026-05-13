# Installed Extension Row Ellipsis Process

## Status

Completed

## Linked Fix

- `docs/fixes/extensions-installed-row-ellipsis-too-early.md`

## Scope

Fix installed mod/plugin rows that ellipsized names, metadata, authors, and descriptions too early in `PanelExtensiones`. Keep the existing detailed and compact row design, and avoid changing marketplace rows or extension detection behavior.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked `docs/README.md`, `docs/pipelines/extensions-pipeline.md`, and `docs/pipelines/ui-component-pipeline.md`; the fix must preserve existing Swing patterns and use shared ellipsis behavior. |
| DONE | 2. Identify row width cause | Found that installed extension rows precomputed text widths before layout, permanently reserved the trailing SVG/action slot, and split the remaining title budget exactly in half. |
| DONE | 3. Implement row text allocation | Added layout-driven title allocation so full labels are clipped inside their actual bounds, and made the delete SVG consume row space only while visible. |
| DONE | 4. Remove double description truncation | Removed the fixed 110-character description cap so descriptions are truncated once by `TextEllipsizer` using the row pixel budget. |
| DONE | 5. Verify fix | Ran compile and focused tests for the new layout helper. |

## Implementation Notes

The installed row renderer tried to predict layout during renderer creation. Detailed rows started from `list.getWidth() - 18`, then removed another `64 + 58 + 42` pixels, and later revisions still permanently reserved a trailing status/delete SVG slot. That differs from `PanelServidores`, where the favorite button is invisible unless hovered or favorited, allowing the text to reclaim that east-side space.

Name and metadata labels also each received `budget / 2`. That made names like `FancyHolograms` truncate even when metadata had spare space, and it made metadata truncate when the name was short. `ExtensionTitleRow` now lays out full-text labels with the real component width, gives unused width from either side to the other, and lets `JLabel` handle clipping inside assigned bounds.

Descriptions previously truncated by character count before pixel ellipsis, causing a second and earlier truncation independent of the rendered row width. They now keep the full single-line description string and clip inside the actual label bounds.

## Verification Notes

- `mvn -q -Dtest=InstalledExtensionRowTextLayoutTest test`
- `mvn -q -DskipTests compile`
- Maven emitted the expected `sun.misc.Unsafe` warning from Guice/Lombok-related startup output.
