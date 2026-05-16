# Server Texture Pack Management

## Status

Pending

## Area

Server configuration UI, texture pack/server resource pack settings, and server properties persistence.

## Feature Request

Let the player administrate a texture pack for the server through a real UI instead of plain text labels.

## Motivation

Server texture pack settings are currently exposed as text labels, which is not intuitive for casual users. Players need a guided interface that explains the current pack state and gives direct actions for adding, changing, removing, and validating a server resource pack.

## Desired Behavior

The server UI should provide a texture pack management area where users can see whether a pack is configured, choose or replace a pack, remove the configured pack, and understand what clients will receive when joining.

The interface should hide raw technical details unless they are useful, while still keeping advanced values such as URL, hash, prompt, and requirement state available where appropriate.

## Notes

- Minecraft server resource packs are usually configured through `server.properties` values such as `resource-pack`, `resource-pack-sha1`, `resource-pack-prompt`, and `require-resource-pack`.
- User-facing copy should stay Spanish and be approachable for casual players.
- Avoid making unrelated configuration-panel redesigns.
- Use existing FlatLaf/Swing components and `AppTheme` styling patterns.

## Suggested Approach

Replace the current label-only presentation with a compact management section: status summary, primary action to add/change a pack, secondary action to remove it, and controls for optional requirement/prompt settings. Consider validation for URL/hash format and a local-file helper if the app already has or later adds a hosting/upload path.

Persist changes through the existing server configuration/property update path so server files are updated consistently.

## Verification

- `mvn -q -DskipTests compile`
- Open server configuration and confirm texture pack state is understandable without editing raw labels.
- Add or change texture pack settings and confirm `server.properties` updates correctly.
- Remove the configured pack and confirm all related displayed state clears.

## Related Docs

- `docs/pipelines/configuration-pipeline.md`
- `docs/pipelines/ui-component-pipeline.md`
- `docs/pipelines/filesystem-and-paths-pipeline.md`
