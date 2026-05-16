# Repeated Debug Mode Helper Pattern Process

## Status

Fixed

## Linked Fix

- `docs/fixes/debug-mode-repeated-helper-pattern.md`

## Scope

Extract the repeated debug-mode listener and header-action wiring into a shared helper without moving fake-state ownership out of the affected panels.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked the pending-fix note, debug-mode pipeline guidance, and the existing debug consumers before editing. |
| DONE | 2. Implement the fix | Added `DebugModeUiBinder` and switched `PanelJugadores` and `WorldRecentConnectionsPanel` to the shared listener and header rebuild pattern. |
| DONE | 3. Update docs | Recorded the helper in the debug-mode pipeline and created the solved-fix note/process pair. |
| TO DO | 4. Verify behavior | Compile and manually toggle Debug mode in the affected panels once Maven is available. |

## Implementation Notes

The helper intentionally stays small: it filters `DebugMode.PROPERTY_ENABLED`, dispatches UI work on the EDT, and rebuilds mixed debug/non-debug header rows. Fake players and fake recent connections still belong to their panels so disable-time cleanup remains local and explicit.

## Verification Notes

`mvn` was not available in this shell, so compile/test verification could not be run here. Manual validation should confirm both panels still remove listeners when undisplayable and clear fake state when Debug mode turns off.
