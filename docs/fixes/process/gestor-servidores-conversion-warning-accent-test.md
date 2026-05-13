# Conversion Warning Accent Test Process

## Status

Completed

## Linked Fix

- `docs/fixes/gestor-servidores-conversion-warning-accent-test.md`

## Scope

Resolve the pending test mismatch where `GestorServidoresTest.construirAvisoConversion_debeMarcarCambioDeEcosistemaComoRiesgoso` expected unaccented Spanish text while `GestorServidores` returned the intended accented user-facing copy.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Read pending fix and evidence | Confirmed the mismatch is isolated to the test assertion and the application copy already says `no se migrarán`. |
| DONE | 2. Align the test | Updated the assertion to preserve the current accented Spanish copy. |
| DONE | 3. Verify and close docs | Ran targeted test, compile, and full test suite; moved the pending note into solved fix docs. |

## Implementation Notes

The fix should preserve the existing user-facing Spanish copy in `GestorServidores` and update the stale assertion rather than weakening the test through accent normalization.

## Verification Notes

- `mvn -q -Dtest=GestorServidoresTest#construirAvisoConversion_debeMarcarCambioDeEcosistemaComoRiesgoso test`
- `mvn -q -DskipTests compile`
- `mvn test`

Commands were run with `JAVA_HOME=C:\Users\MJE\AppData\Local\Programs\Eclipse Adoptium\jdk-25.0.2.10-hotspot` because the default `java` on PATH is Java 8.
