# Convert Button For Vanilla Extensions Panel

## Status

Pending

## Area

`vista.PanelExtensiones`, server conversion UI, and `controlador.GestorServidores`.

## Feature Request

Add a large centered convert button to the extensions panel when the selected server is Vanilla and cannot use extensions.

## Motivation

The extensions panel currently shows two labels explaining that extensions are not available for Vanilla servers. That communicates the limitation, but does not give casual users an obvious next action from the place where they discovered the limitation.

## Desired Behavior

When a Vanilla server opens the extensions panel, the unavailable-state content should include a prominent centered conversion button. The button should use the same conversion behavior as the convert action available from Home, including the same validation, platform selection flow, preservation behavior, and Spanish user-facing copy.

The button should be visually large enough to read as the main action in the empty/unavailable state, while preserving the existing panel structure and explanatory labels.

## Notes

- Existing unavailable-state labels should remain Spanish and keep the current terminology.
- Reuse the existing Home conversion action instead of creating a separate conversion path.
- Keep debug and real server state separate; conversion must continue to operate only on the actual selected server.
- Use `AppTheme` helpers and `SvgIconFactory` if the button needs an icon.

## Suggested Approach

Find the Home convert button/action wiring, extract or reuse the shared conversion trigger from `PanelExtensiones`, and render it in the Vanilla unavailable-state panel. Keep the action disabled or guarded whenever conversion is already in progress or the selected server cannot be converted.

## Verification

- `mvn -q -DskipTests compile`
- Open a Vanilla server, go to Extensiones, and confirm the large centered convert button appears.
- Click the button and confirm it follows the same flow and outcomes as the Home convert action.
- Confirm non-Vanilla extension panels are unchanged.

## Related Docs

- `docs/pipelines/extensions-pipeline.md`
- `docs/pipelines/server-lifecycle-pipeline.md`
- `docs/pipelines/ui-component-pipeline.md`
