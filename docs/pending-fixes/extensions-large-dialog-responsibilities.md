# Extension Marketplace Dialog Has Too Many Responsibilities

## Status

Pending

## Area

`vista.ExtensionMarketplaceDialog`.

## Issue

`ExtensionMarketplaceDialog` is very large and owns search UI, detail UI, version selection, queue state, dependency prompts, worker orchestration, compatibility presentation, renderers, and helper records. This makes changes risky and hard to review.

## Desired Behavior

Marketplace responsibilities should be split into smaller focused classes where it reduces real complexity, while preserving current behavior.

## Evidence

- `src/main/java/vista/ExtensionMarketplaceDialog.java` contains many renderers, workers, records, and state machines.

## Suggested Approach

Refactor incrementally. Good extraction candidates:

- queue model/processing state
- dependency prompt/result models
- renderers
- filter option records
- worker classes if they can receive explicit dependencies

Avoid broad redesign. Extract only when touching nearby behavior and tests can cover it.

## Verification

- `mvn -q -DskipTests compile`
- `mvn test -Dtest=ExtensionMarketplaceDependencyTest`
- Catalog/manual UI smoke test if possible.

## Related Docs

- `docs/pipelines/extensions-pipeline.md`
- `docs/pipelines/ui-component-pipeline.md`
