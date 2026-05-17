# Info Panel Content And Navigation

## Status

Implemented

## Feature

The `Información` navigation entry now opens a real information panel with app details, creator details, support email, and project/social links.

## Motivation

The app already exposed an `Información` navigation button, but the page did not provide useful content. The requested design also needs this page to focus on project/about content rather than the selected server list.

## Solution

Added a dedicated `PanelInformacion` that follows the existing Swing/FlatLaf card style and reuses Dora's logo, the real Dora photo, and existing SVG icons. `VentanaPrincipal` now loads `PaginaDerecha.INFO` like the other right-side pages, while still preserving the repeated-click debug shortcut. The main split pane hides the server list column only while `Información` is selected and restores it when returning to server pages.

Existing support links from `NoServerFrame` were centralized in `DoraAboutLinks`, along with the requested email `manu.ejerara@gmail.com`.

## Files Changed

- `src/main/java/vista/DoraAboutLinks.java`
- `src/main/java/vista/PanelInformacion.java`
- `src/main/java/vista/NoServerFrame.java`
- `src/main/java/vista/VentanaPrincipal.java`
- `src/main/java/vista/VentanaPrincipalRightContentBuilder.java`

## Verification

- `mvn -q -DskipTests compile` passed with only the expected Lombok `sun.misc.Unsafe` warning.
- Manual UI verification was not run in this session.

## Detailed Process

- `docs/features/process/info-panel-content-and-navigation.md`

## Follow-Up Notes

If the about copy becomes release-facing, consider deriving the displayed version from build metadata rather than the current UI string.

## Related Docs

- `docs/pipelines/application-shell-pipeline.md`
- `docs/pipelines/ui-component-pipeline.md`
