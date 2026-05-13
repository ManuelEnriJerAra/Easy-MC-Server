# UI Layout Coverage Fix Process

## Status

Fixed

## Linked Fix

- `docs/fixes/tests-ui-layout-coverage.md`

## Scope

Add focused headless tests for layout and debug-list edge cases that were previously checked mainly by manual inspection or compile checks. Keep the change scoped to helper extraction and unit coverage.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked pending-fix, fixes, build/test, UI component, debug mode, and server creation guidance. |
| DONE | 2. Implement focused coverage | Extracted path-editor layout math and added tests for long folder names, left ellipsis width, and debug recent-connection caps. |
| DONE | 3. Verify behavior | Ran focused tests and full `mvn test`. |

## Implementation Notes

- `FolderPathLayout` centralizes the server creation folder path editor's prefix/field width math so it can be tested without showing the wizard.
- `PanelMundo.mergeDebugRecentConnections(...)` makes the debug recent-connections cap deterministic and testable without constructing the panel.
- `TextEllipsizerTest` now covers narrow left-ellipsis cases where only the ellipsis or one suffix character fits.

## Verification Notes

- `mvn -q "-Dtest=TextEllipsizerTest,FolderPathLayoutTest,PanelMundoDebugConnectionsTest" test`
- `mvn test`
- Full suite result: 235 tests run, 0 failures, 0 errors, 1 skipped.
