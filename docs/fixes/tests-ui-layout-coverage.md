# UI Layout Coverage Fix

## Status

Fixed

## Original Issue

Several Swing layout behaviors were verified mainly by manual inspection or compile checks, including the server creation path editor ellipsis behavior and debug-only recent connection caps.

## Root Cause

Some layout and debug-list decisions were embedded inside panel methods, which made important edge cases hard to test headlessly.

## Solution

Extracted the folder path editor width calculation into `FolderPathLayout`, added focused tests for left ellipsis behavior, and exposed a small debug recent-connections merge helper for cap/order coverage.

## Files Changed

- `src/main/java/controlador/GestorServidores.java`
- `src/main/java/vista/FolderPathLayout.java`
- `src/main/java/vista/PanelMundo.java`
- `src/test/java/vista/FolderPathLayoutTest.java`
- `src/test/java/vista/PanelMundoDebugConnectionsTest.java`
- `src/test/java/vista/TextEllipsizerTest.java`

## Verification

- `mvn -q "-Dtest=TextEllipsizerTest,FolderPathLayoutTest,PanelMundoDebugConnectionsTest" test`
- `mvn test`

## Detailed Process

- `docs/fixes/process/tests-ui-layout-coverage.md`

## Regression Notes

When adding custom Swing layout math, keep the pixel calculations in small helpers that can be exercised with `FontMetrics` under headless tests. For debug-only fake data, keep list merging and caps deterministic enough to test without mutating real server data.

## Related Docs

- `docs/pipelines/ui-component-pipeline.md`
- `docs/pipelines/build-test-pipeline.md`
- `docs/pipelines/server-creation-pipeline.md`
