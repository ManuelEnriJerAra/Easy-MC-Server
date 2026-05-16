# Server Resource Labels Should Not Ellipsize

## Status

Pending

## Area

Server overview/statistics UI components that render CPU, RAM, and DISCO values.

## Issue

The CPU, RAM, and DISCO labels are being ellipsized together with their values. These metric labels and values are core status information and should not be shortened with ellipses.

## Desired Behavior

CPU, RAM, and DISCO text should always render fully, including their displayed values. The UI should allocate enough stable space, adjust layout, or wrap/reflow nearby content instead of applying ellipsis to these metric strings.

## Evidence

- User reports that `CPU`, `RAM`, and `DISCO` are currently ellipsized along with their values.
- The issue likely lives in the server card, Home panel, statistics summary, or another compact resource-display row that applies shared text ellipsis too broadly.

## Suggested Approach

Search for the resource metric labels and any shared `TextEllipsizer` or label-sizing logic applied to their components. Exclude these metric labels from ellipsis, give them stable preferred/minimum sizing, and ensure nearby flexible text is the part that yields when horizontal space is tight.

Keep the fix scoped to resource metric labels; do not change unrelated row ellipsis behavior.

## Verification

- `mvn -q -DskipTests compile`
- Open the server page or card that shows CPU, RAM, and DISCO at narrow and normal widths.
- Confirm the labels and values never show ellipses.
- Confirm adjacent text still behaves acceptably in constrained layouts.

## Related Docs

- `docs/pipelines/ui-component-pipeline.md`
