# English Translation And I18n Foundation

## Status

Pending

## Area

Application-wide UI text and user-facing messages across `vista`, `controlador`, and surfaced service-layer errors.

## Feature Request

Add an English translation for Dora and introduce a maintainable localization system so the application can support Spanish and English without duplicating UI code or relying on ad hoc string replacement.

## Motivation

Dora is currently built around Spanish UI copy. Adding English would make the app easier to use for a broader Minecraft server audience, but the current codebase stores most user-facing text directly in Swing panels, controller dialogs, validation messages, and service exceptions. This is large enough that it needs a staged i18n plan rather than a one-pass translation.

## Desired Behavior

- Support at least Spanish and English UI text.
- Keep Spanish as a supported language, not as a removed/default-only legacy state.
- Let the user choose the language from a reasonable app-level location, likely settings/theme/app preferences.
- Persist the selected language in app configuration.
- Apply the language consistently to:
  - navigation labels and tooltips
  - dialogs and confirmation prompts
  - validation errors
  - server creation/import/conversion flows
  - console/player/world/extension/statistics/automation panels
  - info/welcome/about screens
  - user-visible service errors surfaced through the UI
- Keep layout stable after translation, especially long labels, HTML snippets, tooltips, buttons, and compact cards.

## Notes

- There is no repo-wide i18n system yet.
- No locale `.properties` bundles or active `ResourceBundle` usage were found.
- `PanelConfigServidor` has a local `tr(String key, String fallback)` helper, but it currently returns the fallback and is not an app-wide localization service.
- Major high-density Spanish copy areas include:
  - `src/main/java/controlador/GestorServidores.java`
  - `src/main/java/controlador/GestorMundos.java`
  - `src/main/java/vista/PanelMundo.java`
  - `src/main/java/vista/PanelExtensiones.java`
  - `src/main/java/vista/ExtensionMarketplaceDialog.java`
  - `src/main/java/vista/PanelJugadores.java`
  - `src/main/java/vista/PanelAutomatizacion.java`
  - `src/main/java/vista/PanelEstadisticas.java`
  - `src/main/java/vista/VentanaPrincipal.java`
  - `src/main/java/vista/VentanaPrincipalNavigationBuilder.java`
  - `src/main/java/vista/NoServerFrame.java`
  - `src/main/java/vista/PanelInformacion.java`
- Some service-layer exceptions in extension/world services are user-visible through dialogs; these should not remain hard-coded Spanish if the UI is switched to English.
- Dynamic grammar in extension flows is risky because text is assembled from package type, article, pluralization, dependencies, queue states, and provider metadata.
- Long translated strings can break compact Swing layouts, especially cards, action buttons, and HTML labels.
- Encoding needs care. Existing files include accented Spanish text and some terminal output may appear mojibaked depending on shell rendering.

## Suggested Approach

Implement this in phases:

1. Add a small i18n service.
   - Use Java `ResourceBundle` or a thin wrapper around it.
   - Add bundles such as `messages_es.properties` and `messages_en.properties`.
   - Include parameter formatting support for values like names, ports, versions, counts, paths, and server states.
   - Define fallback behavior when a key is missing.

2. Add app-level language configuration.
   - Store the selected locale in `DoraConfig` or the existing app config path.
   - Add a language selector in an app-level UI area.
   - Decide whether language changes apply immediately or after restart. Immediate switching is nicer but requires more panel refresh work.

3. Migrate shell and low-risk panels first.
   - Navigation, welcome/about/info screens, theme dialog, simple buttons/tooltips.
   - Keep keys stable and grouped by area.

4. Migrate complex workflows in dedicated passes.
   - Server creation/import/conversion in `GestorServidores`.
   - World management in `PanelMundo` and `GestorMundos`.
   - Players in `PanelJugadores`.
   - Automation in `PanelAutomatizacion`.
   - Statistics/export/history in `PanelEstadisticas`.
   - Extensions/catalog/modpack flows in `PanelExtensiones`, `ExtensionMarketplaceDialog`, and related services.

5. Move user-visible service errors away from raw Spanish strings.
   - Prefer error codes plus localized UI rendering for new code.
   - For existing exceptions, either localize at throw sites carefully or map known messages/errors near the UI boundary.

6. Add verification support.
   - Add tests for missing bundle keys where practical.
   - Add targeted tests for parameterized messages and plural-sensitive text.
   - Manually review both Spanish and English layouts at normal and narrow window sizes.

## Verification

When implemented:

- Run `mvn -q -DskipTests compile`.
- Run targeted tests for any new i18n service/config behavior.
- Run `mvn test` if shared configuration, services, or exception behavior changes.
- Manually open the app in Spanish and English.
- Verify server creation, import, conversion, worlds, extensions, players, automation, statistics, welcome, and info screens.
- Check compact layouts for clipped/overlapping English text.
- Verify language selection persists after restarting the app.

## Related Docs

- `docs/pipelines/application-shell-pipeline.md`
- `docs/pipelines/ui-component-pipeline.md`
- `docs/pipelines/configuration-pipeline.md`
- `docs/pipelines/server-creation-pipeline.md`
- `docs/pipelines/world-management-pipeline.md`
- `docs/pipelines/extensions-pipeline.md`
- `docs/pipelines/console-and-players-pipeline.md`
