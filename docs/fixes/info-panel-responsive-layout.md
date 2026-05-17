# Info Panel Responsive Layout

## Status

Fixed

## Original Issue

The `Información` panel did not resize reliably. Text could keep an old wide layout, the page could clip horizontally, and the right-side `Sobre mí`/links column could become too narrow or partially hidden.

## Root Cause

The info page had too many competing preferred sizes. Text components, image blocks, and the side panel all pushed their own widths, so resizing could leave content clipped, offset, or taller than necessary.

## Solution

Rebuilt `PanelInformacion` with a compact two-row structure: a stable top header and a single detail row. The detail row keeps `Sobre Dora` as the flexible main card and `Sobre mí` as a fixed-width side card. Paragraphs use a custom painted wrapping component instead of `JTextArea`, so preferred width no longer expands to one long clipped line. Horizontal scrolling is disabled, and vertical scrolling is only a fallback for genuinely small windows.

## Files Changed

- `src/main/java/vista/PanelInformacion.java`

## Verification

- `mvn -q -DskipTests compile`

## Detailed Process

- `docs/fixes/process/info-panel-responsive-layout.md`

## Regression Notes

When adding text-heavy Swing panels inside a `JScrollPane`, make the content implement `Scrollable` and track viewport width if horizontal scrolling is not desired. Keep side columns fixed only when the main content column is explicitly flexible and text can recompute its height from the current width.

## Related Docs

- `docs/pipelines/application-shell-pipeline.md`
- `docs/pipelines/ui-component-pipeline.md`
