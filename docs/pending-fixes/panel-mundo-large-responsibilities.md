# PanelMundo Has Too Many Responsibilities

## Status

Pending

## Area

`vista.PanelMundo`.

## Issue

`PanelMundo` owns world selection, metadata labels, world settings, storage stats, preview preferences, preview generation, preview painting, recent connections, and debug controls. This makes it difficult to change one world feature without risking another.

## Desired Behavior

World UI behavior should remain cohesive but split high-complexity internals into focused helpers where it meaningfully reduces risk.

## Evidence

- `src/main/java/vista/PanelMundo.java`
- preview image panel and preview worker logic are nested with unrelated world settings/recent connection code.

## Suggested Approach

Refactor only around active work. Possible extraction targets:

- recent connections model/render helper
- preview controls/preferences panel
- preview generation coordinator
- metadata/settings card builders

Keep public behavior unchanged and preserve EDT/background worker boundaries.

## Verification

- `mvn -q -DskipTests compile`
- World UI smoke test.
- Relevant world tests if touching services.

## Related Docs

- `docs/pipelines/world-management-pipeline.md`
- `docs/pipelines/world-rendering-pipeline.md`
- `docs/pipelines/ui-component-pipeline.md`
