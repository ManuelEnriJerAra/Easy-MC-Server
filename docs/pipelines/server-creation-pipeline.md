# Server Creation Pipeline

The server creation flow is centered in `src/main/java/controlador/GestorServidores.java`.

## Main Flow

1. `mostrarAsistenteCreacionServidor()` opens the modal wizard.
2. The wizard collects platform, version, parent directory, EULA acceptance, and folder name.
3. `validarPasoCreacionServidor(...)` validates the active step.
4. `ServerCreationWizardResult` carries the selected adapter, option, and final target directory.
5. The selected `ServerPlatformAdapter` performs platform-specific installation.
6. The created server is added to the managed server list and persisted through the normal server management path.

## Wizard Steps

- `platform`: choose a `ServerPlatformAdapter` from `ServerPlatformAdapters.creatable()`.
- `version`: choose a `ServerCreationOption`, filtered by snapshots/releases, and accept the Mojang EULA.
- `parent`: choose the parent folder through `JFileChooser`.
- `folderName`: edit the final child folder name.

The `folderName` step uses a custom visual path editor:

- The parent path is rendered with leading ellipsis when it cannot fit.
- The editable folder name is left-aligned.
- The folder name starts immediately after the visible parent path.
- A very long folder name may hide the parent path entirely.

## State Rules

`ServerCreationWizardState` stores wizard state. Keep it as the source of truth for selections and suggested folder names.

When the platform, version, or parent directory changes, the suggested folder name may be recomputed with `actualizarSugerenciaNombreCarpeta(...)`.

When the user edits the folder name manually:

- Update `state.folderName`.
- Update `state.folderNameEdited`.
- Revalidate/repaint the folder path editor if its layout depends on text width.
- Refresh the Next/Create button enabled state.

## Validation Rules

Folder names are validated with `validarNombreCarpetaServidor(...)`.

Also check for existing folders with `existeCarpetaConNombreNoPortable(...)` so Windows-style case-insensitive conflicts are caught.

Avoid writing files during wizard validation. Creation should happen only after the final step succeeds.

## UI Notes

Use existing app UI helpers:

- `AppTheme.applyHeaderIconButtonStyle(...)`
- `SvgIconFactory`
- existing FlatLaf/Swing components

The local wizard should match shared process wizard navigation behavior: use the right-arrow icon for intermediate steps and switch the final right-side action to `easymcicons/rocket.svg` when the active step creates the server.

Keep Spanish user-facing text consistent with the rest of the wizard.
