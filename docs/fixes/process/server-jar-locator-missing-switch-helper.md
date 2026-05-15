# Server Jar Locator Missing Switch Helper Process

## Status

Fixed

## Linked Fix

- `docs/fixes/server-jar-locator-missing-switch-helper.md`

## Scope

Fix the runtime crash where server platform detection fails with `NoClassDefFoundError: controlador/platform/ServerJarLocator$1` while scoring executable server jars. This covers the locator implementation only and leaves platform scoring behavior unchanged.

## Step Tracker

| Status | Step | Summary |
| --- | --- | --- |
| DONE | 1. Inspect crash location | Confirmed the stack trace enters `ServerJarLocator.scoreJar` and the compiled output only showed `ServerJarLocator.class`, not `ServerJarLocator$1.class`. |
| DONE | 2. Implement the fix | Replaced the enum `switch` with direct enum comparisons so jar scoring does not require a compiler-generated switch helper class. |
| DONE | 3. Verify behavior | Confirmed compiled output only contains `ServerJarLocator.class`, found no bytecode references to `ServerJarLocator$1`, and ran Maven compile with JDK 25. |

## Implementation Notes

`switch` over `ServerPlatform` can generate a synthetic helper class named after the enclosing class, such as `ServerJarLocator$1`. If a launcher, manual copy, or stale build output includes only `ServerJarLocator.class`, the method fails as soon as the enum switch executes.

The replacement keeps the same scoring branches but uses `if`/`else if` checks against enum constants. That avoids the extra helper class dependency.

## Verification Notes

Ran `mvn -q -DskipTests compile` with `JAVA_HOME` set to `C:\Program Files\Eclipse Adoptium\jdk-25.0.3.9-hotspot`; it passed with only the known Lombok `sun.misc.Unsafe` warning.

After compilation, `target/classes/controlador/platform` contained `ServerJarLocator.class` and no `ServerJarLocator$1.class`. A `javap` scan found no `ServerJarLocator$1` or switch bytecode references in `ServerJarLocator`.
