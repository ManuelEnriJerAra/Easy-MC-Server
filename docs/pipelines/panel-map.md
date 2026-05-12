# Panel Map

Use this quick map when locating UI behavior.

## Main Shell

- `VentanaPrincipal`: main frame, selected server flow, global actions, Debug mode toggle.
- `VentanaPrincipalNavigationBuilder`: navigation UI.
- `VentanaPrincipalRightContentBuilder`: page/panel assembly.
- `NoServerFrame`: fallback/no-server UI.

## Server List And Overview

- `PanelServidores`: left server list, selection, drag reorder, favorites, context menu, open folder.
- `PanelTotalServidor`: overview for the selected server.
- `PanelIndicadoresRecursos`: resource indicators.
- `ServerResourceSnapshot`, `ResourcePalette`: resource display helpers.

## Runtime

- `PanelControlServidor`: start/stop/restart controls.
- `PanelConsola`: console output, simple view, command input, connected-player parsing.

## Configuration And Preview

- `PanelConfigServidor`: server.properties and runtime config editing.
- `PanelPrevisualizacion`: server icon/MOTD/presentation preview.
- `MotdEditorDialog`, `MotdRenderUtil`: MOTD editing/rendering.
- `CropIconDialog`, `ImagenRedondaLabel`: icon cropping/display.

## Players

- `PanelJugadores`: connected players, whitelist/ban/op dialogs, player debug controls.
- `PlayerIdentityView`: compact player identity rendering.

## Worlds

- `PanelMundo`: world selection, metadata, config, storage stats, preview rendering, recent connections.

## Extensions

- `PanelExtensiones`: installed mods/plugins list and details.
- `ExtensionMarketplaceDialog`: catalog search/details/install queue.
- `ExtensionDetailsLayout`: detail view layout helpers.
- `ExtensionDescriptionRenderer`: Markdown-ish description rendering.
- `ExtensionIconLoader`: async icon loading and cache.
- `ExtensionStatusPresentation`: installed/update status labels.
- `MarketplaceEntryViewModel`: marketplace list row model.

## Shared UI Components

- `AppTheme`: colors, borders, button styles, scroll/theme helpers.
- `CardPanel`: card shell with title/header/footer actions.
- `BoxCategory`: compact setting/info cards.
- `RoundedBackgroundPanel`: rounded surface.
- `CardTitleLabel`: standardized card title text.
- `WrapLayout`: wrapping layout manager.
- `SvgIconFactory`: SVG icon loading/tinting/rotation.
- `DebugMode`: global debug toggle state.
