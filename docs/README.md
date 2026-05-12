# Easy MC Server Docs

This folder is the project playbook for coding agents. Before editing an established area, open the matching guide below and use it as local context. If behavior changes, update the relevant doc in the same change.

## Start Here

- `pipelines/panel-map.md`: fastest way to locate UI behavior by panel/component.
- `pipelines/application-shell-pipeline.md`: startup, main window, navigation, selected server propagation.
- `pipelines/build-test-pipeline.md`: Maven commands, test layout, expected warnings.
- `pipelines/ui-component-pipeline.md`: Swing/FlatLaf visual patterns and shared UI helpers.

## Work Notebooks

- `pending-fixes/README.md`: standard for documenting unresolved issues and user-reported bugs.
- `fixes/README.md`: standard for documenting solved issues, root causes, and regression tips.
- `fixes/process/README.md`: standard for detailed step-by-step process files linked from solved fixes.
- `pending-features/README.md`: standard for documenting requested features that are not implemented yet.
- `features/README.md`: standard for documenting completed features.
- `features/process/README.md`: standard for detailed step-by-step process files linked from features.

## By Task

Creating/importing/listing/running servers:

- `pipelines/server-lifecycle-pipeline.md`
- `pipelines/server-creation-pipeline.md`
- `pipelines/platform-adapters-pipeline.md`
- `pipelines/filesystem-and-paths-pipeline.md`

Changing the server creation wizard:

- `pipelines/server-creation-pipeline.md`
- `pipelines/ui-component-pipeline.md`
- `pipelines/platform-adapters-pipeline.md` if platform/version creation options are involved.

Changing start/stop/restart or process output:

- `pipelines/server-lifecycle-pipeline.md`
- `pipelines/console-and-players-pipeline.md`
- `pipelines/platform-adapters-pipeline.md` if startup commands are platform-specific.

Changing worlds, previews, or map rendering:

- `pipelines/world-management-pipeline.md`
- `pipelines/world-rendering-pipeline.md`
- `pipelines/filesystem-and-paths-pipeline.md`

Changing mods/plugins/extensions:

- `pipelines/extensions-pipeline.md`
- `pipelines/models-and-data-pipeline.md`
- `pipelines/filesystem-and-paths-pipeline.md`

Changing server.properties, persisted config, or preferences:

- `pipelines/configuration-pipeline.md`
- `pipelines/models-and-data-pipeline.md`

Changing console, players, whitelist/ban/op behavior:

- `pipelines/console-and-players-pipeline.md`
- `pipelines/debug-mode-pipeline.md` if fake/debug state is involved.

Changing Debug mode:

- `pipelines/debug-mode-pipeline.md`
- the guide for the feature area receiving debug controls.

Changing shared models or records:

- `pipelines/models-and-data-pipeline.md`
- the affected subsystem guide.

## Full Guide List

- `pipelines/application-shell-pipeline.md`: app startup, main frame, navigation, selected server flow.
- `pipelines/build-test-pipeline.md`: compile/test/package commands and verification guidance.
- `pipelines/configuration-pipeline.md`: app config, server.properties editor, dirty tracking.
- `pipelines/console-and-players-pipeline.md`: console rendering, commands, player lists, known users.
- `pipelines/debug-mode-pipeline.md`: global Debug mode and fake state rules.
- `pipelines/extensions-pipeline.md`: mods/plugins detection, manual install, marketplace, dependencies, modpacks.
- `pipelines/filesystem-and-paths-pipeline.md`: safe file/path operations for servers/worlds/extensions.
- `pipelines/models-and-data-pipeline.md`: shared model and record contracts.
- `pipelines/panel-map.md`: UI panel/component lookup.
- `pipelines/platform-adapters-pipeline.md`: platform detection, validation, creation, installation, startup.
- `pipelines/server-creation-pipeline.md`: create-server wizard and final target directory flow.
- `pipelines/server-lifecycle-pipeline.md`: managed server list, import, start/stop, persistence.
- `pipelines/ui-component-pipeline.md`: Swing/FlatLaf conventions.
- `pipelines/world-management-pipeline.md`: world UI, metadata, settings, storage, recent connections.
- `pipelines/world-rendering-pipeline.md`: MCA rendering, previews, overlay data, world data reads.
