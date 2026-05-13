# Server Creation Folder Name Document Refresh Fix

## Status

Fixed

## Original Issue

Typing in the server creation wizard folder-name step could throw `IllegalStateException: Attempt to mutate in notification`.

## Root Cause

The folder-name text field `DocumentListener` called `wizard.refresh()` synchronously. `ProcessWizardDialog.refresh()` invokes the step-changed callback, and for the folder-name step that callback can recompute the suggested folder name and call `folderNameField.setText(...)`. Swing rejects that mutation while the same document is still notifying listeners.

## Solution

The folder-name listener now updates state and repaints the path editor immediately, but schedules the wizard refresh with `SwingUtilities.invokeLater(...)` so any text-field mutation happens after the document notification finishes. The suggestion replacement rule also no longer treats a manually blank folder name as an automatic request to restore the suggestion.

## Files Changed

- `src/main/java/controlador/GestorServidores.java`
- `docs/pipelines/server-creation-pipeline.md`

## Verification

- `mvn -q -DskipTests compile`

## Detailed Process

- `docs/fixes/process/server-creation-folder-name-document-refresh.md`

## Regression Notes

Do not synchronously call wizard refresh logic from Swing text document listeners if that refresh can call `setText(...)` on the same document. Defer the refresh until after the listener returns.

## Related Docs

- `docs/pipelines/server-creation-pipeline.md`
- `docs/pipelines/ui-component-pipeline.md`
