# Server Selection Should Not Freeze While Rebuilding Hidden Pages

## Status

Fixed

## Original Issue

Selecting a server from the left list could make the app feel unresponsive and also disturb the list position, even when the user stayed on a lightweight page.

## Root Cause

Every server selection rebuilt the entire right side synchronously on the EDT. That eager rebuild constructed hidden pages such as Mundo, Extensiones, and Estadísticas even when the user did not open them. `PanelMundo` in particular performs synchronous world sync, metadata reads, storage analysis, preview loading, and recent-connection refresh during construction.

## Solution

Changed the right-side content flow to create page containers immediately but instantiate page content lazily. The selected server now rebuilds the right-side shell first, and each page panel is only created when that page is shown for the first time. Home-specific console wiring is attached when the Home page is loaded instead of during every server selection.

## Files Changed

- `src/main/java/vista/VentanaPrincipal.java`
- `src/main/java/vista/VentanaPrincipalRightContentBuilder.java`
- `src/main/java/vista/PanelServidores.java`

## Verification

- `mvn -q -DskipTests compile` not run here because `mvn` is unavailable in this shell.
- Manual follow-up:
  - scroll the server list and switch between visible servers while staying on Home
  - confirm the app remains responsive and the list no longer jumps upward
  - open Mundo, Extensiones, Configuración, and Estadísticas afterward and confirm each page still loads when first opened

## Detailed Process

- `docs/fixes/process/server-selection-lazy-right-panel-loading.md`

## Regression Notes

When adding heavy right-side pages, do not instantiate them automatically on every server change. Keep server selection cheap and defer disk-heavy page setup until the page is actually shown.

## Related Docs

- `docs/pipelines/ui-component-pipeline.md`
