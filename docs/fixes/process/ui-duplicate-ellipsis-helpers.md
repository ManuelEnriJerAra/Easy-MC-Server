# Duplicate Text Ellipsis Helpers Process

## Status

Fixed

## Linked Fix

- `docs/fixes/ui-duplicate-ellipsis-helpers.md`

## Scope

Centralize duplicated Swing text ellipsis logic from the original pending fix. This covers right ellipsis helpers in server list labels, server control buttons, extension rows, marketplace rows, and preview labels, plus left ellipsis for the server-creation folder path editor. This does not change unrelated text wrapping or length-only summaries.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Checked pending fix, UI component pipeline, and solved-fix documentation standards. |
| DONE | 2. Locate duplicate helpers | Found repeated binary-search and string-width ellipsis helpers in the named UI classes and `PanelPrevisualizacion`. |
| DONE | 3. Implement shared helper | Added `TextEllipsizer` and migrated practical right/left ellipsis call sites. |
| DONE | 4. Verify behavior | Ran the targeted helper test and compile successfully. |
| DONE | 5. Finalize documentation | Added solved fix note and deleted the pending-fix file after verification. |

## Implementation Notes

`TextEllipsizer.right(...)` preserves the common label/button behavior that returns `...` when the ellipsis itself exceeds the requested width. `TextEllipsizer.rightStrict(...)` preserves the extension list and marketplace behavior that returns `.` or an empty string when only that can fit. `TextEllipsizer.left(...)` keeps the suffix-preserving behavior used by the folder path editor.

## Verification Notes

`mvn -q "-Dtest=vista.TextEllipsizerTest" test` completed successfully.

`mvn -q -DskipTests compile` completed successfully.

An initial unquoted PowerShell command, `mvn -q -Dtest=vista.TextEllipsizerTest test`, failed before Surefire ran because PowerShell split the dotted property value into an invalid Maven lifecycle phase. The quoted command above is the valid verification result.
