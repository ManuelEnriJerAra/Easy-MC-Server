# Server Creation Folder Name Document Refresh Process

## Status

Fixed

## Linked Fix

- `docs/fixes/server-creation-folder-name-document-refresh.md`

## Scope

Fix the crash reported while typing in the server creation wizard folder-name step. The scope is limited to the folder path editor refresh flow and suggestion replacement rules.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked server creation and UI component pipelines before editing `GestorServidores`. |
| DONE | 2. Identify the crash path | Traced the stack to `folderNameField` document notifications calling `wizard.refresh()`, which called the folder-step refresh callback and could call `setText(...)` on the same field. |
| DONE | 3. Implement the fix | Deferred folder-name wizard refreshes with `SwingUtilities.invokeLater(...)` and stopped treating a manually blank folder name as permission to restore the suggestion. |
| DONE | 4. Verify behavior | Ran compile successfully. |
| DONE | 5. Document regression | Added solved fix notes and updated the server creation pipeline warning. |

## Implementation Notes

Swing text documents cannot be mutated while they are notifying document listeners. The folder-name listener now updates wizard state, revalidates/repaints the path editor, and schedules the wizard refresh after the document notification returns. Suggestion replacement still happens for forced updates, untouched names, null names, and names matching the previous suggestion, but not for a manually blank field.

## Verification Notes

`mvn -q -DskipTests compile` completed successfully. Maven printed the expected `sun.misc.Unsafe` warning.
