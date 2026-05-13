# Server Platform Icon Selector

## Status

Pending

## Area

`vista.ProcessWizardDialog`, server creation wizard platform step, server conversion platform selector, `controlador.GestorServidores`, `vista.SvgIconFactory`, and `src/main/resources/easymcicons`.

## Feature Request

Replace the current combo-box platform selector with a reusable icon-based platform selector panel, following the stored mockup at `docs/mockups/server_platform_mockup.png`.

The same selector should be used when creating a new server and when choosing the destination platform for converting the selected server.

Each platform option should render as an interactive SVG icon with its platform name. The SVG assets do not exist yet, but the code should be prepared to load future files named after each platform, such as:

- `vanilla.svg`
- `forge.svg`
- `neoforge.svg`
- `fabric.svg`
- `quilt.svg`
- `paper.svg`
- `purpur.svg`
- and equivalent names for any other supported platform shown by the selector.

## Motivation

The current platform selector is a plain `JComboBox`, which makes platform choice feel like a form field instead of a visual, first-class wizard decision. The requested icon grid makes the creation/conversion flow clearer, more recognizable, and easier to scan.

Now that `vista.ProcessWizardDialog` exists as the shared shell for multi-step processes, this selector should also help move server creation toward that reusable wizard structure instead of keeping the platform step coupled to the older local wizard implementation inside `GestorServidores`.

## Desired Behavior

The platform selector should look and behave like the stored mockup:

- Title remains `Plataforma`.
- `Vanilla` appears centered as a standalone option at the top.
- A horizontal divider separates the top option from the `Mods` group.
- `Mods` group displays platform options as icon buttons, shown in the corrected mockup as `Forge`, `Neoforge`, `Fabric`, and `Quilt`.
- A horizontal divider separates `Mods` from the `Plugins` group.
- `Plugins` group displays platform options as icon buttons, shown in the corrected mockup as `Purpur` and `Paper`.
- Each option shows an SVG icon above the platform label.
- Each icon/label option acts like a button.
- Hover and clickable areas should use the hand cursor.
- The selected option should change to the main accent color.
- The panel should be responsive: options should keep stable spacing and remain usable as the dialog changes size.
- Keyboard navigation and focus feedback should remain accessible enough for a wizard/dialog selector.

## Notes

- `ProcessWizardDialog` is the reusable wizard shell for new multi-step processes. It owns left/right navigation and the final-step `rocket.svg` action. The current server creation wizard mirrors the final rocket action while it waits for a full migration to the shared shell.
- Current creation selector code uses `JComboBox<ServerPlatformAdapter>` in `GestorServidores.seleccionarAdaptadorCreacion()`.
- Current conversion selector code uses `JComboBox<ServerPlatformAdapter>` in `GestorServidores.seleccionarAdaptadorConversion(...)`.
- The current creation wizard still uses a local wizard implementation and a `JComboBox<ServerPlatformAdapter>` for step 0 in `GestorServidores.mostrarAsistenteCreacionServidor()`.
- Wizard validation and navigation currently depend on the combo box through `puedeAvanzarPasoCreacionServidor(...)` and `validarPasoCreacionServidor(...)`.
- Platform options come from `ServerPlatformAdapters.creatable()` for creation and `plataformasObjetivoConversion(...)` for conversion.
- `ServerPlatform` already exposes platform/ecosystem information through `isVanillaPlatform()`, `isModPlatform()`, and `isPluginPlatform()`.
- `Quilt` belongs in the `Mods` group, matching the existing `QUILT` model classification as `ServerEcosystemType.MODS`.
- Use `SvgIconFactory` for SVG loading/coloring rather than hand-drawn icons.
- Use `AppTheme.getMainAccent()` for selected state color and existing theme colors for default/hover/focus states.
- Missing future platform SVG files should not crash the dialog. Provide a fallback icon such as the existing box icon while still trying the platform-specific asset path first. Use `box.svg`.
- Preserve Spanish user-facing copy and existing wizard terminology.
- Mockup reference: `docs/mockups/server_platform_mockup.png`.

## Suggested Approach

Extract a reusable Swing component for selecting a `ServerPlatformAdapter`, for example a `PlatformSelectorPanel` or similarly scoped helper.

Use `ProcessWizardDialog` as the target wizard shell for the creation flow. The platform selector should be designed as a normal wizard-step component that can be embedded in `ProcessWizardDialog.Step`, with selection callbacks refreshing the creation state and navigation validation.

The component should:

- Accept the list of available adapters and an initial selection.
- Expose the selected adapter without tying callers to `JComboBox`.
- Emit a selection-change callback so the creation wizard can clear version state, update snapshot availability, refresh folder-name suggestions, and revalidate navigation.
- Group visible adapters into Vanilla, Mods, and Plugins sections based on platform metadata or an explicit display grouping.
- Build each option as a button-like panel or custom button containing the SVG icon and label.
- Resolve icon paths from platform names, e.g. `easymcicons/vanilla.svg`, with a fallback icon until the platform SVG set exists.
- Reuse the same component for creation and conversion, while allowing unavailable/non-automatable conversion targets to be hidden or marked according to the existing conversion rules.

After replacing the component, update creation wizard methods that currently accept `JComboBox<ServerPlatformAdapter>` so they read from the new selector state instead. If server creation is migrated in the same change, wire its steps through `ProcessWizardDialog` so the shared navigation, validation hooks, and final rocket action are used by the creation wizard too.

## Verification

- Run `mvn -q -DskipTests compile`.
- Manually open the create-server wizard and verify:
  - platform step shows the icon selector instead of a combo box;
  - selecting each available platform updates the selection color to the main accent;
  - Next button enables/disables correctly;
  - changing platform still clears/reloads version options correctly;
  - missing platform SVG assets fall back cleanly.
- Manually open the selected-server conversion flow and verify the same selector component is used for destination platform choice.
- Test light and dark FlatLaf themes for default, hover, selected, focus, and disabled/unavailable states.
- Resize the dialog and verify the selector remains readable and clickable.

## Related Docs

- `docs/pipelines/server-creation-pipeline.md`
- `docs/pipelines/platform-adapters-pipeline.md`
- `docs/pipelines/ui-component-pipeline.md`
- `docs/features/process-wizard.md`
- `docs/features/process/process-wizard.md`
- `docs/pipelines/application-shell-pipeline.md`
- `docs/mockups/server_platform_mockup.png`
