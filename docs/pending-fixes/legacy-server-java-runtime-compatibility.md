# Legacy Server Java Runtime Compatibility

## Status

Pending

## Area

Server startup commands in `controlador.platform.AbstractServerPlatformAdapter` and old server creation/import flows.

## Issue

The app can list and install very old server versions from upstream providers, but startup uses plain `java` from the user's PATH. On a Java 25-only system, old Minecraft servers may fail or behave incorrectly because those versions require older Java runtimes.

This is especially relevant for old Paper versions: official Paper metadata still exposes `1.7.10` and `1.8.8`, but Paper documentation recommends Java 8 for old Minecraft ranges.

## Desired Behavior

The app should warn users before running server versions that need an older Java runtime, or provide a way to choose a compatible Java executable per server/version.

## Evidence

- `AbstractServerPlatformAdapter.buildStartProcess(...)` starts most platforms with plain `java -jar`.
- Paper Fill metadata currently exposes old versions including `1.7.10` and `1.8.8`.
- Paper documentation publishes Java-version guidance for Minecraft version ranges.
- The project itself targets Java 25, which does not imply old Minecraft server jars can run on Java 25.

## Suggested Approach

Add runtime compatibility handling without changing version availability:

- Add Java-version guidance in server creation/import validation for old Minecraft versions.
- Optionally allow a per-server Java executable override.
- Show a clear warning when a selected server version is known to require Java 8, Java 16, Java 17, or Java 21 instead of the current default.
- Keep platform-specific exceptions documented, because modded loaders and plugin platforms may have their own runtime requirements.

## Verification

- Create/import old Paper `1.8.8` and confirm the UI warns about Java compatibility before start.
- Create/import a modern server and confirm no legacy Java warning appears.
- If a Java executable override is implemented, confirm startup uses the configured executable.
- Run lifecycle/platform tests:

```bash
mvn -q -Dtest=controlador.platform.ServerPlatformAdaptersTest test
```

- Run at least:

```bash
mvn -q -DskipTests compile
```

## Related Docs

- `docs/pipelines/platform-adapters-pipeline.md`
- `docs/pipelines/server-lifecycle-pipeline.md`
