# Server Platform Icon Selector Process

## Status

Implemented

## Linked Feature

- `docs/features/server-platform-icon-selector.md`

## Scope

Implement a reusable icon-based `ServerPlatformAdapter` selector for server creation and conversion flows, replacing the existing platform combo boxes while preserving current wizard validation, version loading, and conversion filtering behavior.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read project guidance | Reviewed repository instructions, feature request, mockup, and server creation/platform/UI pipeline docs. |
| DONE | 2. Implement selector component | Added a reusable Swing selector grouped into Vanilla, Mods, and Plugins with SVG icon fallback behavior. |
| DONE | 3. Wire creation and conversion | Replaced the creation and conversion `JComboBox<ServerPlatformAdapter>` callers with selector state and moved creation onto `ProcessWizardDialog`. |
| DONE | 4. Document and verify | Moved the pending feature into completed feature docs and ran compile verification. |

## Implementation Notes

The creation wizard now builds a `ProcessWizardDialog.Step` list for platform, version, parent folder, and folder-name steps. `ProcessWizardDialog` exposes an instance API so the creation flow can refresh the Next/Create button when selector, version, EULA, folder, and background download-check state changes.

`PlatformSelectorPanel` sorts known platforms into the requested display order, tries `easymcicons/<platform>.svg`, and falls back to `easymcicons/box.svg` when the platform-specific asset is not present.

Conversion targets are filtered through `ServerPlatformAdapter.supportsAutomatedCreation()` before rendering, so platforms that cannot be converted automatically are not presented.

Selector rows use equal-width cells and compact option tiles so spacing adapts to the wizard width and all groups remain visible at the default dialog size.

The selector does not preselect a platform. Callers must pass an explicit initial adapter if they want a default selection, and the creation/conversion flows intentionally start with no selected platform.

## Verification Notes

`mvn -q -DskipTests compile` passes when run with `JAVA_HOME` and `PATH` pointed at `C:\Users\MJE\AppData\Local\Programs\Eclipse Adoptium\jdk-25.0.2.10-hotspot`.

The first compile attempt used the default `java.exe` from Java 8 and failed with `invalid target release: 25`; the local `javac.exe` was already JDK 25.
