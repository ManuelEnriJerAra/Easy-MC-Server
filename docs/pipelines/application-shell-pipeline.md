# Application Shell Pipeline

Use this guide before editing startup, navigation, window layout, or right-side panel composition.

## Entry Point

Main class: `src/main/java/controlador/Main.java`.

The application starts the Swing UI, configures Look and Feel/theme, and opens the main window.

## Main Window

Primary class: `src/main/java/vista/VentanaPrincipal.java`.

Responsibilities:

- Owns the root frame.
- Builds left navigation and right content.
- Tracks the selected server.
- Wires global actions such as create/import/delete/open folder.
- Toggles Debug mode through the Info navigation entry click sequence.
- Updates the title when Debug mode changes.

Helpers:

- `VentanaPrincipalNavigationBuilder`: navigation construction.
- `VentanaPrincipalRightContentBuilder`: right-side content panel creation.

## Main Panels

Right-side content is composed from `vista` panels:

- `PanelTotalServidor`: server overview/dashboard.
- `PanelControlServidor`: start/stop/restart controls.
- `PanelConsola`: live console and command input.
- `PanelConfigServidor`: server.properties and runtime settings.
- `PanelJugadores`: connected players and player lists.
- `PanelMundo`: worlds, metadata, storage, previews, recent connections.
- `PanelExtensiones`: mods/plugins listing, install/remove, catalog integration.
- `PanelPrevisualizacion`: preview/edit of server presentation data.
- `PanelEstadisticas`: stats view.

## Selection Flow

`PanelServidores` renders the left server list and reports selection changes to `VentanaPrincipal`.

When a server changes:

1. The selected `Server` is stored in `GestorServidores`.
2. Visible panels refresh from the selected server.
3. Runtime-sensitive panels update state based on process status and filesystem state.

Do not let individual panels keep stale selected-server assumptions. Prefer fetching through `GestorServidores.getServidorSeleccionado()` or responding to the existing selection refresh path.

## Debug Mode

`VentanaPrincipal` owns the hidden click sequence that toggles `DebugMode`. Panels should only consume `DebugMode`, not duplicate toggle logic.

See `debug-mode-pipeline.md`.

## Layout Notes

The app is dense operational UI, not a marketing page. Prefer compact, scan-friendly cards and controls.

Use existing builders and panels rather than constructing alternate navigation trees.

## When Editing

- If changing navigation, inspect both `VentanaPrincipal` and the builder classes.
- If adding a panel, decide where it belongs in the navigation enum/page model before wiring the view.
- If changing selected-server propagation, audit panels that cache listeners or server references.
