# Build And Test Pipeline

The project is a Maven Java 25 application.

## Common Commands

Compile without tests:

```bash
mvn -q -DskipTests compile
```

Run tests:

```bash
mvn test
```

Build executable jar:

```bash
mvn clean package
```

## Expected Warning

Maven may print a Lombok warning about `sun.misc.Unsafe::objectFieldOffset` being terminally deprecated. This warning is expected and is not a build failure.

## Test Layout

Tests live in `src/test/java`.

Common test areas:

- controller services and filesystem logic
- world rendering/preview services
- extension marketplace and dependency handling
- UI theme/component helpers

Surefire sets:

```text
java.awt.headless=true
```

## Verification Guidance

For small UI changes, run at least:

```bash
mvn -q -DskipTests compile
```

For changes to parsing, filesystem behavior, platform installation, extension resolution, or shared service logic, run targeted tests or `mvn test`.

If a UI change cannot be fully verified headlessly, mention that in the final summary and describe the compile/test coverage that was run.
