# UI Layout Fixes Need More Focused Coverage

## Status

Pending

## Area

Swing layout and rendering edge cases.

## Issue

Several layout behaviors are verified mainly by manual inspection or compile checks. Recent examples include path editor ellipsis/field positioning and debug-only controls. Pure Swing visual behavior is hard to test, but some model/layout calculations could be covered.

## Desired Behavior

Complex UI layout calculations should expose enough helper logic to unit test important edge cases without requiring full screenshot testing.

## Evidence

- create-server folder path editor custom layout
- server list text ellipsis
- control button ellipsis
- extension row text fitting

## Suggested Approach

When adding custom layout/math, prefer small helper methods/classes that can be tested headlessly. Add tests for:

- long text consumes available space predictably
- left ellipsis returns suffix-preserving output
- debug fake lists cap at expected count

## Verification

- Add/extend focused unit tests.
- `mvn test` for changed areas.

## Related Docs

- `docs/pipelines/ui-component-pipeline.md`
- `docs/pipelines/build-test-pipeline.md`
