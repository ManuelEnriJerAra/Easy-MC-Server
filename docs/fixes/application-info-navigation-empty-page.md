# Application Info Navigation Empty Page

## Status

Fixed

## Original Issue

Clicking the Info entry in the main navigation could show a blank right-side area, making it look like an unfinished or deleted feature.

## Root Cause

`VentanaPrincipalRightContentBuilder` created an empty transparent `JPanel` for `PaginaDerecha.INFO`, and `VentanaPrincipal` registered that empty panel in the right-side `CardLayout`. The same Info navigation entry is also used for the hidden Debug-mode click sequence.

## Solution

The active right-content builder no longer returns an empty Info panel, and `VentanaPrincipal` no longer registers an Info card. Info clicks still pass through the Debug-mode click counter, but `INFO` is normalized back to `HOME` before the right-side page is shown.

## Files Changed

- `src/main/java/vista/VentanaPrincipal.java`
- `src/main/java/vista/VentanaPrincipalRightContentBuilder.java`

## Verification

- `mvn -q -DskipTests compile`

## Detailed Process

- `docs/fixes/process/application-info-navigation-empty-page.md`

## Regression Notes

If Info navigation is reintroduced as a real page, add actual content before registering `PaginaDerecha.INFO` in the right-side `CardLayout`. Keep the hidden Debug-mode click sequence in `VentanaPrincipal` unless the debug pipeline is intentionally changed.

## Related Docs

- `docs/pipelines/application-shell-pipeline.md`
