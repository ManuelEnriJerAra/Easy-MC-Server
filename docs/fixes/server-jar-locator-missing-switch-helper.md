# Server Jar Locator Missing Switch Helper

## Status

Fixed

## Original Issue

Startup could crash during persisted server validation with:

```text
NoClassDefFoundError: controlador/platform/ServerJarLocator$1
```

The crash happened while platform detection scored candidate server jars.

## Root Cause

`ServerJarLocator.scoreJar` used an enum `switch` over `ServerPlatform`. The compiler can emit a synthetic helper class for that switch. When runtime output contained `ServerJarLocator.class` without the generated `ServerJarLocator$1.class`, the method failed as soon as it evaluated the switch.

## Solution

Replaced the enum `switch` with direct enum comparisons in `ServerJarLocator.scoreJar`, preserving the existing scoring rules without requiring a generated helper class.

## Files Changed

- `src/main/java/controlador/platform/ServerJarLocator.java`
- `docs/fixes/process/server-jar-locator-missing-switch-helper.md`
- `docs/fixes/server-jar-locator-missing-switch-helper.md`

## Verification

Ran `mvn -q -DskipTests compile` with JDK 25; it passed with only the known Lombok `sun.misc.Unsafe` warning.

Confirmed compiled output contains `ServerJarLocator.class` and no `ServerJarLocator$1.class`. A `javap` scan found no `ServerJarLocator$1` or switch bytecode references in `ServerJarLocator`.

## Detailed Process

- `docs/fixes/process/server-jar-locator-missing-switch-helper.md`

## Regression Notes

Avoid enum switches in small runtime helper classes when the app may be launched from copied or stale class output. If enum switches are reintroduced, verify the packaging and launch path include all generated synthetic classes.

## Related Docs

- `docs/pipelines/platform-adapters-pipeline.md`
