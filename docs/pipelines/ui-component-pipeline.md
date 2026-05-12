# UI Component Pipeline

The UI is Swing with FlatLaf. Most custom UI helpers are in `src/main/java/vista`.

## Theme Helpers

Use `AppTheme` for:

- foreground/background colors
- muted foreground
- rounded borders
- action button styles
- header icon button styles
- refresh icon behavior
- scroll speed/style helpers where present

Avoid hard-coded colors unless the surrounding code already uses them or the value is clearly temporary/local.

## Icons

Use SVG assets from `src/main/resources/easymcicons` through `SvgIconFactory`.

Prefer existing icon assets:

- `plus.svg`, `minus.svg`
- `reset.svg`, `refresh.svg`
- navigation arrows
- user/shield/folder/shop/settings icons

Set button tooltips, especially for icon-only controls.

## Cards And Layout

Common components:

- `CardPanel`: titled cards, header actions, footer actions, content panel.
- `BoxCategory`: compact boolean, field, and info rows.
- `WrapLayout`: wrapping action/player layouts.
- `RoundedBackgroundPanel`: rounded surfaces.

Preserve established layouts. For narrow fixes, adjust sizing/layout inside the affected component instead of redesigning surrounding panels.

## Swing Threading

Use the EDT for UI mutations.

Common patterns:

- `SwingUtilities.invokeLater(...)` from listeners/background callbacks.
- `SwingWorker` for background work with UI updates in `process(...)` or `done()`.

Remove long-lived listeners when components become undisplayable.

## Text And Localization

User-facing text is mostly Spanish. Keep new strings Spanish unless adding internal diagnostics or comments.

Preserve accents where the file already uses UTF-8 correctly. Some older text may appear mojibaked; avoid broad encoding churn unless explicitly fixing that file.
