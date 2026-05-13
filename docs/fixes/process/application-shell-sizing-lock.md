# Application Shell Sizing Lock Process

## Status

Completed

## Linked Fix

- `docs/fixes/application-shell-sizing-lock.md`

## Scope

Fix the main window and extensions page layout pressure that made resizing feel locked or impossible. Keep the existing shell, navigation, cards, and split panes, but remove oversized preferred/minimum sizes that force the frame to stay wide.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Inspect shell and extension layout | Reviewed `VentanaPrincipal`, `VentanaPrincipalRightContentBuilder`, `PanelExtensiones`, and `ExtensionDetailsLayout` sizing behavior. |
| DONE | 2. Identify hard constraints | Found frame-width console preferred sizing, large split child minimums, details scroll preferred-size inflation, and main split minimum pressure. |
| DONE | 3. Relax shell constraints | Stopped using the frame width as the console preferred width, allowed the main split wrappers to shrink, and removed resize-time root revalidation. |
| DONE | 4. Relax extensions constraints | Removed hard minimums from the extensions split children, prevented the details scroll body from propagating unbounded preferred width, and stopped renderer fallback/text-floor widths from pinning narrow lists. |
| DONE | 5. Verify compile and focused tests | Ran compile and focused Swing helper/panel tests. |

## Implementation Notes

The application did not call `setResizable(false)`. Resizing was blocked by layout pressure. The HOME page advertised a console preferred width based on the whole frame width, including in an unused duplicate path. The Extensions page also used a horizontal split where both children previously required large minimum widths, and details labels could inflate the scroll body preferred width through long names, paths, links, or metadata.

`CardLayout` can consider all cards when calculating preferred/minimum size, so oversized hidden pages still matter. The fix keeps preferred visual sizes where useful but removes hard minimums that stop the user from resizing the frame or split panes.

The installed extension row renderer must not keep a minimum text-column width after the list has a real viewport width. A floor such as 120 or 160 pixels makes ellipsis stop adapting below that size and can push the row border outside the viewport.

## Verification Notes

- `mvn -q '-Dtest=InstalledExtensionRowTextLayoutTest,PanelExtensionesTest' test`
- `mvn -q -DskipTests compile`
- An initial unquoted PowerShell Maven test command failed because the comma in `-Dtest=...` was parsed as a parameter separator; the quoted command passed.
- Maven emitted the expected `sun.misc.Unsafe` warning from Guice/Lombok-related startup output.
