# Server Conversion Preserve Custom Icon Process

## Status

Fixed

## Linked Fix

- `docs/fixes/server-conversion-preserve-custom-icon.md`

## Scope

Keep the old custom server icon when converting a server, without changing the broader preservation rules for the other config files.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Inspect conversion preservation | Checked `ConversionPreservationHelper` and confirmed `server-icon.png` was preserved but only restored when missing. |
| DONE | 2. Implement the fix | Allowed preserved `server-icon.png` to overwrite the generated default icon during restore. |
| DONE | 3. Document the fix | Added the solved-fix note and detailed process file. |
| TO DO | 4. Verify behavior | Convert a server with a custom icon and confirm the original image remains after conversion. |

## Implementation Notes

The fix stays narrow by keeping the current restore rule for the other preserved files and adding an explicit overwrite exception only for `server-icon.png`.

## Verification Notes

`mvn` was not available in this shell, so compile/test execution is still pending. Manual verification should compare the pre-conversion and post-conversion icon file or UI thumbnail.
