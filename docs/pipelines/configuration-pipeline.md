# Configuration Pipeline

Use this guide before editing app config, server.properties editing, known users, or persisted UI preferences.

## App Configuration

Core classes:

- `controlador.GestorConfiguracion`
- `modelo.EasyMCConfig`
- `modelo.ServerConfig`
- `modelo.Server`

`GestorConfiguracion` handles persisted app-level data such as known servers and UI preferences.

App-owned persistence paths are centralized in `controlador.AppPaths`. Default app config files live under the user data root, currently `~/.easy-mc-server/config/`, while disposable extension caches live under `~/.easy-mc-server/cache/`. Do not add new executable-relative app state; use `AppPaths.configDirectory()`, `cacheDirectory()`, `locksDirectory()`, or `statsDirectory()` as appropriate.

Before changing serialized fields, check existing config compatibility and default handling.

## Server Properties

`modelo.ServerProperties` represents `server.properties` style values.

`vista.PanelConfigServidor` provides the UI for editing server properties and runtime-related settings.

Panel responsibilities:

- Load properties for the selected server.
- Categorize fields into sections.
- Create editors based on field kind.
- Track dirty state.
- Save values back to disk.
- Keep some fields managed elsewhere out of this panel.

Important helper areas in `PanelConfigServidor`:

- `FIELD_LABELS`
- `FIELD_DESCRIPTIONS`
- `SECTION_ORDER`
- `captureCurrentValues()`
- `readComponentValue(...)`
- `markCurrentStateAsPersisted()`
- `updateSaveButtonState()`
- RAM slider/spinner synchronization
- field kind detection and normalization

## Managed Outside Config Panel

Some server/world settings are intentionally managed elsewhere, such as world settings in `PanelMundo`. Do not duplicate controls unless the UX explicitly calls for it.

## Known Users

`GestorUsuariosConocidos` stores and resolves known player identities. Player-related UI uses it to maintain display consistency.

## Dirty Tracking Rules

When adding a new editor:

- Register dirty tracking.
- Include it in value capture.
- Normalize values consistently with save logic.
- Update persisted state after successful save.

## Tests

Relevant tests include:

- `PanelServidoresTest`
- `PanelExtensionesTest`
- config-related coverage in controller/model tests

For property parsing or persistence changes, run targeted tests and compile.
