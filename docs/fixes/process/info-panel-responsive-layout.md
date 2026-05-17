# Info Panel Responsive Layout Process

## Status

Fixed

## Linked Fix

- `docs/fixes/info-panel-responsive-layout.md`

## Scope

Fix the `Información` panel layout so text and side content stay aligned and visible within the available window width. This covers the info page only and does not redesign shared layout helpers elsewhere.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Diagnose layout clipping | The page content was wider than the viewport and the detail side column could be starved or clipped when the window resized. |
| DONE | 2. Implement responsive layout | Rebuilt the panel as a compact header plus one detail row with a flexible Dora card, fixed-width profile card, and a custom text component that wraps from its current width instead of using `JTextArea` preferred sizing. |
| DONE | 3. Verify compile | `mvn -q -DskipTests compile` passed with only the expected Lombok warning. |

## Implementation Notes

Swing layouts can preserve a child component's preferred width unless the scroll content explicitly tracks the viewport width. The info page now uses a `Scrollable` panel that returns `true` for `getScrollableTracksViewportWidth()`, which prevents horizontal clipping from stale preferred widths.

The lower info layout uses `GridBagLayout` with a flexible main Dora card and a fixed-width side profile card. This keeps the side content aligned while the main card absorbs extra width.

The custom text block measures and paints text with `LineBreakMeasurer`. Its preferred width is intentionally tiny so `GridBagLayout` cannot preserve a long one-line paragraph width; height is recalculated from the actual assigned width when the component resizes.

## Verification Notes

`mvn -q -DskipTests compile` passed.

Manual UI verification should still resize the `Información` page across narrow and wide window sizes and confirm the side cards remain visible and text wraps instead of clipping.
