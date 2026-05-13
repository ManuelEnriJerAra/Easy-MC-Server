# Application Shell Sizing Lock

## Status

Fixed

## Original Issue

The main window and extensions page could feel impossible to resize. Split panes and page content resisted shrinking, leaving large blank or unusable regions and keeping installed extension rows overly constrained.

## Root Cause

Several layout constraints combined:

- `VentanaPrincipalRightContentBuilder` gave the console a preferred width derived from the whole frame width.
- An unused duplicate right-panel builder in `VentanaPrincipal` still had the same bad console preferred-width pattern.
- The main split kept a hard left minimum, and the right wrapper had no explicit shrinkable minimum.
- `PanelExtensiones` forced both inner split children to large minimum widths.
- `ExtensionDetailsLayout` returned its full body preferred size as the scrollable viewport size, allowing long labels/paths/metadata to inflate the page width.
- The installed-extension row renderer used fallback widths that could keep list preferred width larger than the actual narrow viewport.

## Solution

The shell and extensions page now allow real shrinking:

- Console panels use `new Dimension(0, 100)` so they do not pin the right side to the current frame width.
- Main split wrappers can shrink to zero, while the initial divider still starts near the normal server-list width.
- The resize listener no longer revalidates the root pane on every resize tick.
- The extensions split children no longer impose hard minimum widths.
- The details scroll body reports a modest default preferred viewport size.
- Installed row renderer width falls back only before layout, then honors actual list/viewport width all the way down to zero available text width.

## Files Changed

- `src/main/java/vista/VentanaPrincipal.java`
- `src/main/java/vista/VentanaPrincipalRightContentBuilder.java`
- `src/main/java/vista/PanelExtensiones.java`
- `src/main/java/vista/ExtensionDetailsLayout.java`

## Verification

- `mvn -q '-Dtest=InstalledExtensionRowTextLayoutTest,PanelExtensionesTest' test`
- `mvn -q -DskipTests compile`

## Detailed Process

- `docs/fixes/process/application-shell-sizing-lock.md`

## Regression Notes

Avoid using the frame width as a child component preferred width. For split-pane children and card pages, prefer low or zero minimum sizes and let the visible layout/renderer handle cramped states with ellipsis or scrolling. Do not add minimum text-column floors inside list renderers unless the containing component can actually reserve that width.

## Related Docs

- `docs/pipelines/application-shell-pipeline.md`
- `docs/pipelines/ui-component-pipeline.md`
- `docs/pipelines/extensions-pipeline.md`
