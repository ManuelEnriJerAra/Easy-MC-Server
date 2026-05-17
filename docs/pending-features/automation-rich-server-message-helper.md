# Automation Rich Server Message Helper

## Status

Pending

## Area

`vista.PanelConsola`, `vista.PanelAutomatizacion`, and command-building helpers around server console/automation actions.

## Feature Request

Add a helper for creating and sending rich Minecraft chat messages with `tellraw`, for example:

```text
tellraw @a [{"text":"[Servidor] ","color":"gold","bold":true},{"text":"El evento empieza en 5 minutos.","color":"yellow"}]
```

The helper should make server announcements look better than plain `say BlaBlaBla` output, especially from the automation tab.

## Motivation

Plain `say` commands are visually noisy and do not give Dora-controlled server messages a polished style. Rich `tellraw` messages can provide clearer prefixes, colors, emphasis, and more readable automated announcements.

## Desired Behavior

- Provide a reusable way to build valid `tellraw` JSON messages.
- Support a styled server prefix such as `[Servidor] `.
- Let users enter the actual announcement text without manually writing JSON.
- Add a console-line action/button for sending a rich message to the server.
- Reuse the same message-building logic from the automation tab so scheduled announcements can use the same style.
- Preserve Spanish UI terminology and keep the workflow compact.

## Notes

- User specifically called out the automation tab as the main beneficiary.
- The console could expose a dedicated send-message button near the command input.
- The feature should avoid ad hoc string concatenation for JSON; use an existing structured JSON API from the project dependencies where practical.
- `tellraw` support may depend on server version/platform behavior, so future implementation should consider graceful fallback or clear validation.

## Suggested Approach

- Add a small command/message builder that returns the final console command string for common announcement cases.
- Build the JSON payload with Jackson or Gson rather than hand-escaped strings.
- Add a compact dialog/editor for message text and optional style choices.
- In `PanelConsola`, add a button near the console command line that opens the message helper and sends the generated command.
- In `PanelAutomatizacion`, reuse the same helper when creating command automation rules so automated announcements can be stored as normal server commands.

## Verification

- Run `mvn -q -DskipTests compile`.
- Add targeted tests for the command builder if it is implemented outside UI-only code.
- Manually send a rich message from the console line and verify Minecraft receives a valid `tellraw` command.
- Manually create an automation rule that sends a rich message and verify the stored/generated command matches the helper output.

## Related Docs

- `docs/pipelines/console-and-players-pipeline.md`
- `docs/pipelines/configuration-pipeline.md`
- `docs/pipelines/ui-component-pipeline.md`
- `docs/features/automation-tab-ui.md`
