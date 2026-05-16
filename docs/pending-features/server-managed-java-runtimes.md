# Managed Java Runtimes For Servers

## Status

Pending

## Area

`controlador.GestorServidores`, `controlador.platform`, server creation/import/startup, app-owned runtime storage, application configuration, and server configuration.

## Feature Request

Automatically provision and use the correct Java runtime for each managed Minecraft server after the Dora desktop application itself has already been launched with Java 25.

The user should not need to understand, install, configure, or switch Java versions manually for Minecraft servers. Dora may still require Java 25 to run the desktop app, but server creation, loader installation, server startup, conversion, and imported-server startup should use app-managed Java runtimes whenever possible.

## Motivation

Minecraft Java Edition servers and modded loaders do not all work best with the same Java major version. Modern Minecraft versions may require Java 21, older versions often expect Java 8 or Java 17, and modded ecosystems can be especially sensitive to running with a Java version that is too new or too old.

The current startup paths mostly invoke `java` from the user's system `PATH`. That creates several user-facing problems:

- A server can fail even though Dora itself launched correctly.
- Users may be forced to install multiple Java versions manually.
- The app cannot reliably choose Java 8, 17, 21, or 25 per server.
- Forge, NeoForge, Fabric, Quilt, Paper, Purpur, Vanilla, and imported servers can behave inconsistently depending on the user's machine.
- Troubleshooting Java failures is noisy and feels unrelated to the user's goal of managing a Minecraft server.

The desired product experience is: the user installs enough Java to launch Dora, then Dora quietly prepares the Java runtimes needed by the servers it manages.

## Desired Behavior

Dora should keep the desktop app runtime and the server runtime conceptually separate.

The desktop app should continue to target Java 25. The managed Minecraft server process should use a Java executable selected by Dora for that specific server.

When creating a server automatically:

- Determine the required Java major version from the selected Minecraft version whenever possible.
- Prefer official Mojang version metadata for the Java requirement.
- Download and install a managed runtime if the required Java major is not already available.
- Run any required platform installer with the same selected runtime, or with an installer-specific runtime when a platform requires that distinction.
- Persist enough metadata so future starts do not repeat expensive remote checks unnecessarily.
- Keep all user-facing progress and error copy in Spanish.

When importing an existing server:

- Detect the server platform and Minecraft version using the existing platform detection pipeline.
- Resolve the Java runtime requirement from detected metadata.
- If the version cannot be determined confidently, use a safe fallback policy and explain the limitation in Spanish.
- Allow an advanced manual override for users who know a specific server needs a custom Java path.

When starting a server:

- Resolve the selected Java executable before building the `ProcessBuilder`.
- Use the managed runtime executable path instead of the string `java`.
- Show a progress dialog if a runtime needs to be downloaded before the first start.
- Do not block the Swing event dispatch thread while downloading, extracting, checking, or validating runtimes.
- Append useful console lines such as the selected Java major/version and whether it is managed or custom.
- If runtime provisioning fails, do not start the server with a random system Java fallback unless the user has explicitly enabled that behavior.

When Java is already installed on the user's system:

- The app may detect compatible system Java installations for diagnostics or optional use.
- The default behavior should still prefer app-managed runtimes for reproducibility.
- Advanced settings may allow a custom Java executable path per server.

When runtimes are stored locally:

- Store them under the app-owned root, not inside individual server folders.
- Suggested location: `AppPaths.rootDirectory()/runtimes`, or a dedicated `AppPaths.runtimesDirectory()`.
- Keep one install directory per provider, Java major, OS, architecture, image type, and concrete release.
- Use a stable alias or manifest entry to mark the active runtime for each Java major.
- Download archives to temporary `.part` files and extract into temporary directories before atomically publishing an installed runtime.
- Verify SHA-256 checksums before installing a downloaded archive.
- Never mutate global `JAVA_HOME`, the user's `PATH`, or system-wide Java installations.

## Notes

- The app is a Java 25 Swing desktop application. This feature does not remove the Java 25 requirement for launching the application unless a future packaging feature bundles the app runtime.
- The server lifecycle pipeline starts servers in `GestorServidores.iniciarServidor(...)`.
- The platform adapter API currently exposes `ServerPlatformAdapter.buildStartProcess(Server, Path)`.
- `AbstractServerPlatformAdapter.buildStartProcess(...)` currently hardcodes `java`.
- `ForgeServerPlatformAdapter.buildStartProcess(...)` can use generated `run.bat` or `run.sh` scripts, generated `win_args.txt` or `unix_args.txt` files, or the default jar fallback. The args-file fallback currently hardcodes `java`.
- `NeoForgeServerPlatformAdapter.buildStartProcess(...)` delegates to Forge startup behavior.
- `QuiltServerPlatformAdapter.buildStartProcess(...)` hardcodes `java` for the Quilt server launcher path.
- `JavaProcessForgeInstallerRunner` currently hardcodes `java` when running Forge and NeoForge installers.
- `ExternalServerInstallerRunner` runs externally supplied command lists and will need careful handling if it is reused for Java-based installers.
- `MojangAPI` already reads the Mojang version manifest and version JSON URLs for Vanilla server downloads.
- Mojang launcher version JSON metadata contains `javaVersion.majorVersion` for modern versions. This should be the preferred source when available.
- Eclipse Adoptium documents a stable API URL pattern for Eclipse Temurin binaries by Java major version, OS, architecture, image type, JVM implementation, heap size, and vendor. It also documents SHA-256 checksum verification for downloaded binaries.
- Prefer archive downloads over MSI/pkg installers so Dora can install runtimes in its own app directory without requiring administrator permissions.
- Prefer JRE images when available because servers only need to run Java bytecode. Fall back to JDK images when a JRE is unavailable for a required major/platform combination.
- Windows executables should resolve to `bin/java.exe`; macOS and Linux should resolve to `bin/java`.
- macOS archives can contain nested `.jdk/Contents/Home` structures, so runtime executable detection should search known layouts rather than assuming the extracted root is already `JAVA_HOME`.
- The implementation should support at least Windows x64 first, because that is the main current development environment, but the design should keep OS and architecture mapping explicit for macOS/Linux support.
- Avoid using Java 25 for every server just because the app runs on Java 25. Older modded servers may fail on a runtime that is too new.
- Keep user-facing Spanish terminology consistent with the rest of the project.

## Suggested Approach

Implement this as an app-managed runtime subsystem instead of scattering Java selection inside every platform adapter.

### Step 1: Add runtime domain models

Create a new package such as `controlador.runtime` with small records/classes:

- `JavaRuntimeRequirement`: requested Java major version, source, confidence, and optional reason.
- `JavaRuntimeInstallation`: provider, Java major, concrete version, OS, architecture, image type, installation directory, executable path, checksum, and installed timestamp.
- `JavaRuntimeResolution`: resolved executable path, source type (`MANAGED`, `CUSTOM`, `SYSTEM`, `UNAVAILABLE`), selected major version, and diagnostics.
- `JavaRuntimeUseCase`: `SERVER_START`, `PLATFORM_INSTALLER`, `VALIDATION`, and possibly `IMPORT_PREFLIGHT`.
- `JavaRuntimeProvider`: provider identity and download metadata, initially Eclipse Temurin through Adoptium.

Keep these models independent of Swing so they can be unit tested.

### Step 2: Add app-owned runtime paths

Extend `AppPaths` with a dedicated method:

```java
public static Path runtimesDirectory() {
    return rootDirectory().resolve("runtimes");
}
```

Use a sublayout similar to:

```text
runtimes/
  temurin/
    windows-x64/
      java-8/
      java-17/
      java-21/
      java-25/
    manifest.json
```

The exact layout can change, but it must be app-owned, deterministic, and separate from server directories.

### Step 3: Resolve Java requirements from Minecraft metadata

Add a resolver that maps a Minecraft version to a Java requirement.

Preferred path:

- Use Mojang's version manifest to locate the version JSON.
- Read `javaVersion.majorVersion` when present.
- Cache successful metadata lookups in memory and optionally persist a compact metadata cache later if startup latency requires it.

Fallback path:

- Use a conservative mapping only when official metadata is unavailable.
- Suggested fallback policy:
  - Minecraft `1.16.5` and older: Java 8.
  - Minecraft `1.17.x`: Java 16 when metadata says so; otherwise prefer Java 17 or prompt if the exact requirement is ambiguous.
  - Minecraft `1.18` through `1.20.4`: Java 17.
  - Minecraft `1.20.5` and newer modern releases: Java 21 unless metadata says otherwise.
  - Future versions: prefer metadata; if unavailable, use the app runtime major only as a clearly reported last resort.

The fallback table must be covered by tests and should be documented as less authoritative than Mojang metadata.

### Step 4: Add a runtime installer/downloader

Implement `JavaRuntimeInstaller` around the Adoptium API.

The installer should:

- Map Java major, OS, architecture, and desired image type to an Adoptium API URL.
- Prefer `jre`; fall back to `jdk` if no JRE archive exists for that combination.
- Follow redirects to the concrete release artifact.
- Download to a `.part` file.
- Download or derive the `.sha256.txt` checksum URL and verify the archive before extraction.
- Extract to a temporary directory inside the runtime storage area.
- Locate the runtime home and Java executable after extraction.
- Execute `java -version` from the extracted runtime to verify it starts and reports the expected major version.
- Atomically publish the completed installation.
- Write a local manifest entry only after the runtime is verified.

Do not use OS-level installers by default. They require permissions, mutate global machine state, and make uninstall/cleanup harder.

### Step 5: Add a runtime manager facade

Create `JavaRuntimeManager` as the main entry point used by lifecycle/platform code.

It should expose methods similar to:

```java
JavaRuntimeRequirement requirementForServer(Server server, JavaRuntimeUseCase useCase);
JavaRuntimeResolution resolve(Server server, JavaRuntimeUseCase useCase);
JavaRuntimeResolution ensureInstalled(Server server, JavaRuntimeUseCase useCase, ProgressListener listener);
```

The manager should:

- Honor per-server custom Java overrides when configured.
- Prefer existing verified managed runtimes.
- Download missing managed runtimes when allowed.
- Return clear unavailable diagnostics instead of throwing raw network/archive exceptions into UI code.
- Avoid duplicate simultaneous downloads for the same Java major by coordinating in-flight installations.

### Step 6: Thread runtime resolution into server startup

Update the server startup path so runtime resolution happens before `adapter.buildStartProcess(...)`.

Potential API direction:

- Add `JavaRuntimeResolution` or `Path javaExecutable` to the adapter startup context.
- Replace `ServerPlatformAdapter.buildStartProcess(Server, Path)` with an overload or context object such as `ServerStartContext`.
- Keep a compatibility bridge during refactor if needed for tests.

The target outcome is that all normal server start commands use the selected executable:

```java
javaExecutable.toString()
```

instead of:

```java
"java"
```

Update at least these startup paths:

- `AbstractServerPlatformAdapter.buildStartProcess(...)`
- `ForgeServerPlatformAdapter.buildStartProcess(...)`
- `NeoForgeServerPlatformAdapter.buildStartProcess(...)`
- `QuiltServerPlatformAdapter.buildStartProcess(...)`

### Step 7: Handle Forge and NeoForge generated scripts carefully

Forge and NeoForge may create `run.bat` or `run.sh` scripts that call `java` internally.

Do not assume the generated script will use the app-selected runtime.

Possible strategies:

- Prefer args-file startup over generated scripts when possible, because the app can put the selected Java executable at the front of the command.
- If a script must be used, set `JAVA_HOME` and prepend the managed runtime `bin` directory to the process environment `PATH` for that process only.
- Do not modify the user's global environment.
- Do not rewrite generated scripts unless there is no safer option.
- Add tests for Forge/NeoForge paths that verify the managed runtime is used for args-file startup.

### Step 8: Use managed Java for platform installers

Update Java-based installer runners so they do not invoke the system `java`.

At minimum:

- Change `JavaProcessForgeInstallerRunner` to receive a Java executable path or a runtime resolver.
- Ensure Forge and NeoForge installers run with the runtime selected for the target Minecraft version.
- Review Fabric and Quilt install paths. They mostly download server launcher jars today, but any future Java installer step should use the same runtime manager.
- Keep installer output capture behavior so failures remain visible.

### Step 9: Add configuration and overrides

Add optional config fields only after the core runtime manager exists.

Global app config candidates in `DoraConfig`:

- Runtime provider, default `temurin`.
- Whether managed runtime downloads are enabled.
- Whether system Java fallback is allowed.
- Maximum cached runtime count or cleanup preference, if cleanup is implemented.

Per-server config candidates:

- Runtime mode: `AUTO`, `MANAGED_MAJOR`, `CUSTOM_PATH`.
- Custom Java executable path.
- Last resolved Java major and runtime source, for diagnostics only.

Keep defaults automatic and simple. Advanced controls should not make normal users choose Java manually.

### Step 10: Add Swing progress and errors

Runtime provisioning may be slow and must not block the EDT.

Add a small progress flow for creation/startup:

- "Preparing Java runtime..."
- "Downloading Java 21..."
- "Verifying download..."
- "Installing runtime..."
- "Starting server..."

Use Spanish UI copy in implementation, even though this pending feature is written in English.

Errors should distinguish:

- Unsupported OS/architecture.
- No runtime available from the provider.
- Download timeout or network failure.
- Checksum mismatch.
- Extraction failure.
- Java executable missing after extraction.
- Java executable starts but reports the wrong major version.

### Step 11: Add tests before broad UI work

Start with unit tests around pure services:

- Minecraft version to Java major requirement.
- Mojang metadata parsing including `javaVersion.majorVersion`.
- Fallback mapping for old versions.
- Adoptium URL construction.
- OS/architecture mapping.
- Runtime executable discovery for Windows, Linux, and macOS-style layouts.
- Manifest read/write normalization.
- Duplicate in-flight install coordination if implemented.

Then add adapter/lifecycle tests:

- Default adapter startup command uses the resolved managed Java path.
- Quilt launcher command uses the resolved managed Java path.
- Forge args-file command uses the resolved managed Java path.
- Forge/NeoForge installer runner uses the resolved managed Java path.
- Server startup reports a clear error when runtime resolution fails.

### Step 12: Document the completed behavior when implemented

When implemented, move this pending feature into:

- `docs/features/server-managed-java-runtimes.md`
- `docs/features/process/server-managed-java-runtimes.md`

Also update relevant pipeline docs if startup, platform adapters, config, or filesystem behavior changes.

## Verification

When implementing this feature, run:

- `mvn -q -DskipTests compile`
- `mvn -q -Dtest=controlador.platform.ServerPlatformAdaptersTest test`
- `mvn -q -Dtest=controlador.GestorServidoresTest test`
- New targeted tests for Java runtime requirement resolution, installer URL construction, checksum handling, extraction, executable discovery, and process command construction.

Manual checks should include:

- Create and start a Vanilla server for a modern Minecraft version that requires Java 21.
- Create and start a Vanilla or modded server for a version that should use Java 17.
- Import and start an older server that should use Java 8 when possible.
- Create and start Forge and NeoForge servers that use generated args files.
- Confirm the server process uses the managed Java executable, not the Java 25 app runtime or the system `PATH` Java.
- Confirm runtime provisioning progress appears without freezing the UI.
- Disconnect the network and verify the app reports a clear Spanish error without corrupting partial runtime state.
- Corrupt a downloaded archive or checksum in a test fixture and verify installation is rejected.
- Delete one managed runtime and verify the app can reinstall it on demand.
- Configure a custom Java path for one server and verify only that server uses it.

## Related Docs

- `docs/README.md`
- `docs/pipelines/application-shell-pipeline.md`
- `docs/pipelines/server-lifecycle-pipeline.md`
- `docs/pipelines/platform-adapters-pipeline.md`
- `docs/pipelines/server-creation-pipeline.md`
- `docs/pipelines/configuration-pipeline.md`
- `docs/pipelines/filesystem-and-paths-pipeline.md`
- `docs/pipelines/models-and-data-pipeline.md`
- `https://piston-meta.mojang.com/mc/game/version_manifest_v2.json`
- `https://minecraft.wiki/w/Version_manifest.json`
- `https://minecraft.wiki/w/Client.json`
- `https://adoptium.net/installation/ci-scripts`
- `https://api.adoptium.net/q/swagger-ui/`
