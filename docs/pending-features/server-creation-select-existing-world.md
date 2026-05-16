# Select Existing World During Server Creation

## Status

Pending

## Area

`controlador.GestorServidores`, server creation wizard, and world import/management services.

## Feature Request

Let the player select an existing world while creating a new server.

## Motivation

Creating a server currently focuses on platform/version/server settings. Players who already have a world must handle that after creation, which makes the first-run flow less direct for casual users and for users migrating an existing save into Dora.

## Desired Behavior

During new server creation, the wizard should offer a clear way to choose an existing world folder. If selected, the created server should start with that world prepared as the active world using Dora's managed world layout and normal world validation/import behavior.

The flow should make it clear whether the world will be copied into the server or referenced/imported, avoid mutating the source world unexpectedly, and preserve current creation behavior when no existing world is selected.

## Notes

- The server creation wizard lives mostly in `GestorServidores`.
- Existing world operations live around `GestorMundos` and the `dora-worlds` managed-world layout.
- Preserve the current folder-name path editor behavior when adding new wizard controls.
- User-facing copy should stay Spanish.

## Suggested Approach

Add an optional world-selection step or option to the creation wizard after the server folder is known. Reuse `GestorMundos` validation and copy/import helpers where possible, then set the imported world as the active `level-name` before the first start.

Keep the source-world handling conservative: copy into the new server's managed worlds area, report validation failures before creating irreversible state, and surface clear Spanish errors.

## Verification

- `mvn -q -DskipTests compile`
- Create a server without selecting a world and confirm the existing flow is unchanged.
- Create a server with a valid existing world and confirm the world is copied/imported, selected as active, and starts correctly.
- Try an invalid folder and confirm the wizard shows a clear validation error.

## Related Docs

- `docs/pipelines/server-creation-pipeline.md`
- `docs/pipelines/world-management-pipeline.md`
- `docs/pipelines/filesystem-and-paths-pipeline.md`
