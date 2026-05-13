# Conversion Warning Accent Test Mismatch

## Status

Fixed

## Original Issue

`GestorServidoresTest.construirAvisoConversion_debeMarcarCambioDeEcosistemaComoRiesgoso` failed because it expected the unaccented substring `no se migraran` while `GestorServidores` returned the intended Spanish copy `no se migrarán`.

## Root Cause

The test assertion was stale and did not match the accented user-facing text.

## Solution

Updated the assertion in `GestorServidoresTest` to expect `no se migrarán`, preserving the existing Spanish copy in `GestorServidores`.

## Files Changed

- `src/test/java/controlador/GestorServidoresTest.java`

## Verification

- `mvn -q -Dtest=GestorServidoresTest#construirAvisoConversion_debeMarcarCambioDeEcosistemaComoRiesgoso test`
- `mvn -q -DskipTests compile`
- `mvn test`

Commands were run with `JAVA_HOME=C:\Users\MJE\AppData\Local\Programs\Eclipse Adoptium\jdk-25.0.2.10-hotspot` because the default `java` on PATH is Java 8.

## Detailed Process

- `docs/fixes/process/gestor-servidores-conversion-warning-accent-test.md`

## Regression Notes

When tests assert Spanish UI text, keep accents aligned with the actual copy unless the test explicitly normalizes text for a broader reason.

## Related Docs

- `docs/pipelines/server-lifecycle-pipeline.md`
- `docs/pipelines/ui-component-pipeline.md`
