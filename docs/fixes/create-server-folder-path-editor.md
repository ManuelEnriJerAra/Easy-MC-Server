# Create Server Folder Path Editor Alignment

## Status

Fixed

## Original Issue

In the create-server wizard folder-name step, the editable folder name was centered inside its reserved field area. When the parent path was long, the editable text appeared detached from the path, and long folder names had too little useful space.

## Root Cause

The row used a `BorderLayout` with the parent path label in `WEST` and the editable `JTextField` in `CENTER`. The text field itself was horizontally centered, so it centered within whatever space remained instead of starting immediately after the visible parent path.

## Solution

`GestorServidores` now uses a custom `FolderPathPanel` for the visual path editor:

- parent path uses a left-ellipsis label
- folder name field is left-aligned
- folder name is laid out immediately after the visible parent path
- long folder names can consume all text space and hide the parent path
- full, non-ellipsized parent path uses normal `JLabel` rendering to avoid font/rendering changes

## Files Changed

- `src/main/java/controlador/GestorServidores.java`

## Verification

- `mvn -q -DskipTests compile`
- Manual UI feedback confirmed the font-shift case and follow-up fix.

## Detailed Process

- `docs/fixes/process/create-server-folder-path-editor.md`

## Regression Notes

If the path appears visually detached again, check:

- `folderNameField.setHorizontalAlignment(JTextField.LEFT)`
- `FolderPathPanel.doLayout()`
- `LeftEllipsisLabel.paintComponent(...)`

Do not reintroduce a centered field inside `BorderLayout.CENTER`.

## Related Docs

- `docs/pipelines/server-creation-pipeline.md`
- `docs/pipelines/ui-component-pipeline.md`
