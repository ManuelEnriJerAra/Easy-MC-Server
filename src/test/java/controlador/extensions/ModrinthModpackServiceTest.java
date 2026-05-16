package controlador.extensions;

import modelo.Server;
import modelo.extensions.ExtensionLocalMetadata;
import modelo.extensions.ExtensionSource;
import modelo.extensions.ExtensionSourceType;
import modelo.extensions.ServerExtension;
import modelo.extensions.ServerLoader;
import modelo.extensions.ServerPlatform;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.File;
import java.io.IOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ModrinthModpackServiceTest {
    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @TempDir
    Path tempDir;

    @Test
    void readIndexParsesValidMrpackAndReportsDependencyWarnings() throws IOException {
        Path pack = writePack("""
                {
                  "formatVersion": 1,
                  "game": "minecraft",
                  "versionId": "pack-1",
                  "name": "Pack de prueba",
                  "files": [
                    {
                      "path": "mods/example.jar",
                      "hashes": {
                        "sha1": "0123456789012345678901234567890123456789",
                        "sha512": "0123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123456789012345678901234567890123"
                      },
                      "downloads": ["https://cdn.modrinth.com/data/proj/versions/ver/example.jar"],
                      "fileSize": 123,
                      "env": { "client": "optional", "server": "required" }
                    }
                  ],
                  "dependencies": {
                    "minecraft": "1.21.1",
                    "fabric-loader": "0.16.10"
                  }
                }
                """);
        ModrinthModpackService service = new ModrinthModpackService(new FakeHttpClient(Map.of()));
        Server server = new Server();
        server.setPlatform(ServerPlatform.FORGE);
        server.setVersion("1.20.1");

        ModrinthModpackService.ImportIndex index = service.readIndex(pack);
        List<String> warnings = service.validateDependencies(index, server);

        assertThat(index.name()).isEqualTo("Pack de prueba");
        assertThat(index.files()).singleElement().satisfies(file -> {
            assertThat(file.path()).isEqualTo("mods/example.jar");
            assertThat(file.env().server()).isEqualTo("required");
        });
        assertThat(warnings).anySatisfy(warning -> assertThat(warning).contains("Minecraft 1.21.1"));
        assertThat(warnings).anySatisfy(warning -> assertThat(warning).contains("fabric-loader"));
    }

    @Test
    void readIndexRejectsMissingIndexAndUnsafePaths() throws IOException {
        Path missingIndex = tempDir.resolve("missing.mrpack");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(missingIndex), StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("other.json"));
            zip.write("{}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }

        Path unsafeIndex = writePack("""
                {
                  "formatVersion": 1,
                  "game": "minecraft",
                  "name": "Unsafe",
                  "files": [
                    {
                      "path": "../mods/example.jar",
                      "hashes": { "sha1": "a" },
                      "downloads": ["https://cdn.modrinth.com/data/proj/versions/ver/example.jar"]
                    }
                  ],
                  "dependencies": { "minecraft": "1.21.1" }
                }
                """);
        Path normalizedTraversal = writePack("""
                {
                  "formatVersion": 1,
                  "game": "minecraft",
                  "name": "Unsafe normalized",
                  "files": [
                    {
                      "path": "mods/../example.jar",
                      "hashes": { "sha1": "a" },
                      "downloads": ["https://cdn.modrinth.com/data/proj/versions/ver/example.jar"]
                    }
                  ],
                  "dependencies": { "minecraft": "1.21.1" }
                }
                """);
        ModrinthModpackService service = new ModrinthModpackService(new FakeHttpClient(Map.of()));

        assertThatThrownBy(() -> service.readIndex(missingIndex))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("modrinth.index.json");
        assertThatThrownBy(() -> service.readIndex(unsafeIndex))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("ruta insegura");
        assertThatThrownBy(() -> service.readIndex(normalizedTraversal))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("ruta insegura");
    }

    @Test
    void readIndexRejectsUnsafeOverrideEntries() throws IOException {
        Path pack = tempDir.resolve("unsafe-overrides.mrpack");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(pack), StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("modrinth.index.json"));
            zip.write("""
                    {
                      "formatVersion": 1,
                      "game": "minecraft",
                      "name": "Unsafe overrides",
                      "files": [
                        {
                          "path": "mods/example.jar",
                          "hashes": { "sha1": "a" },
                          "downloads": ["https://cdn.modrinth.com/data/proj/versions/ver/example.jar"]
                        }
                      ],
                      "dependencies": { "minecraft": "1.21.1" }
                    }
                    """.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("overrides/../server.properties"));
            zip.write("bad=true".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        ModrinthModpackService service = new ModrinthModpackService(new FakeHttpClient(Map.of()));

        assertThatThrownBy(() -> service.readIndex(pack))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("insegura");
    }

    @Test
    void readIndexRejectsUnsafeZipEntriesOutsideOverrides() throws IOException {
        Path pack = tempDir.resolve("unsafe-root-entry.mrpack");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(pack), StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("modrinth.index.json"));
            zip.write("""
                    {
                      "formatVersion": 1,
                      "game": "minecraft",
                      "name": "Unsafe root",
                      "files": [
                        {
                          "path": "mods/example.jar",
                          "hashes": { "sha1": "a" },
                          "downloads": ["https://cdn.modrinth.com/data/proj/versions/ver/example.jar"]
                        }
                      ],
                      "dependencies": { "minecraft": "1.21.1" }
                    }
                    """.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("../ignored.txt"));
            zip.write("bad=true".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        ModrinthModpackService service = new ModrinthModpackService(new FakeHttpClient(Map.of()));

        assertThatThrownBy(() -> service.readIndex(pack))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("ruta ZIP insegura");
    }

    @Test
    void downloadAndVerifyPrefersSha512AndRejectsHashMismatch() throws IOException {
        byte[] expected = "expected jar bytes".getBytes(StandardCharsets.UTF_8);
        byte[] wrong = "wrong jar bytes".getBytes(StandardCharsets.UTF_8);
        ModrinthModpackService.IndexedFile file = new ModrinthModpackService.IndexedFile(
                "mods/example.jar",
                Map.of("sha512", hash(expected, "SHA-512"), "sha1", hash(expected, "SHA-1")),
                new ModrinthModpackService.Env("optional", "required"),
                List.of("https://cdn.modrinth.com/data/proj/versions/ver/example.jar"),
                expected.length
        );
        ModrinthModpackService service = new ModrinthModpackService(new FakeHttpClient(Map.of()));

        assertThatThrownBy(() -> service.downloadAndVerify(
                file,
                file.downloads().getFirst(),
                tempDir.resolve("download.jar").toFile(),
                (url, destination) -> Files.write(destination.toPath(), wrong)
        ))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("SHA-512");
    }

    @Test
    void resolvesImportPlanFromHashMetadataAndSkipsClientOnlyFiles() throws IOException {
        byte[] content = "example".getBytes(StandardCharsets.UTF_8);
        String sha512 = hash(content, "SHA-512");
        String sha1 = hash(content, "SHA-1");
        ModrinthModpackService.IndexedFile serverFile = new ModrinthModpackService.IndexedFile(
                "mods/example.jar",
                Map.of("sha512", sha512, "sha1", sha1),
                new ModrinthModpackService.Env("optional", "required"),
                List.of("https://cdn.modrinth.com/data/project-id/versions/version-id/example.jar"),
                content.length
        );
        ModrinthModpackService.IndexedFile clientOnly = new ModrinthModpackService.IndexedFile(
                "mods/client.jar",
                Map.of("sha512", sha512, "sha1", sha1),
                new ModrinthModpackService.Env("required", "unsupported"),
                List.of("https://cdn.modrinth.com/data/project-id/versions/version-id/client.jar"),
                content.length
        );
        ModrinthModpackService service = new ModrinthModpackService(new FakeHttpClient(Map.of(
                "https://api.modrinth.com/v2/version_file/" + sha512 + "?algorithm=sha512",
                """
                {
                  "id": "version-id",
                  "project_id": "project-id",
                  "version_number": "1.0.0"
                }
                """,
                "https://api.modrinth.com/v2/project/project-id",
                """
                {
                  "title": "Example Mod",
                  "author": "Author",
                  "description": "Summary",
                  "slug": "example",
                  "project_type": "mod",
                  "downloads": 10
                }
                """
        )));
        Server server = new Server();
        server.setPlatform(ServerPlatform.FABRIC);
        server.setVersion("1.21.1");

        ExtensionDownloadPlan plan = service.resolveImportDownloadPlan(serverFile, server);

        assertThat(plan.sourceType()).isEqualTo(ExtensionSourceType.MODRINTH);
        assertThat(plan.projectId()).isEqualTo("project-id");
        assertThat(plan.versionId()).isEqualTo("version-id");
        assertThat(plan.displayName()).isEqualTo("Example Mod");
        assertThat(service.isServerSideInstallable(clientOnly)).isFalse();
    }

    @Test
    void evaluatesImportFilesFromExplicitUserOptions() {
        ModrinthModpackService service = new ModrinthModpackService(new FakeHttpClient(Map.of()));
        ModrinthModpackService.IndexedFile serverRequired = indexedFile(
                "mods/server.jar",
                new ModrinthModpackService.Env("unsupported", "required")
        );
        ModrinthModpackService.IndexedFile clientRequired = indexedFile(
                "mods/client.jar",
                new ModrinthModpackService.Env("required", "unsupported")
        );
        ModrinthModpackService.IndexedFile optionalBoth = indexedFile(
                "mods/optional.jar",
                new ModrinthModpackService.Env("optional", "optional")
        );
        ModrinthModpackService.IndexedFile unknown = indexedFile("mods/unknown.jar", null);

        assertThat(service.evaluateImportFile(serverRequired,
                new ModrinthModpackService.ImportOptions(ModrinthModpackService.ImportMode.SERVER)).install())
                .isTrue();
        assertThat(service.evaluateImportFile(clientRequired,
                new ModrinthModpackService.ImportOptions(ModrinthModpackService.ImportMode.SERVER)).install())
                .isFalse();
        assertThat(service.evaluateImportFile(clientRequired,
                new ModrinthModpackService.ImportOptions(ModrinthModpackService.ImportMode.CLIENT)).install())
                .isTrue();
        assertThat(service.evaluateImportFile(optionalBoth,
                new ModrinthModpackService.ImportOptions(ModrinthModpackService.ImportMode.SERVER)).install())
                .isTrue();
        assertThat(service.evaluateImportFile(unknown,
                new ModrinthModpackService.ImportOptions(ModrinthModpackService.ImportMode.SERVER)).install())
                .isTrue();
        assertThat(service.evaluateImportFile(unknown,
                new ModrinthModpackService.ImportOptions(ModrinthModpackService.ImportMode.COMPLETE)).install())
                .isTrue();
    }

    @Test
    void exportWritesModrinthIndexWithHashesAndServerOverrides() throws IOException {
        Path serverDir = tempDir.resolve("server");
        Path modsDir = Files.createDirectories(serverDir.resolve("mods"));
        Path configDir = Files.createDirectories(serverDir.resolve("config"));
        Path jar = modsDir.resolve("example.jar");
        Path curseForgeJar = modsDir.resolve("curseforge-example.jar");
        writeFabricJar(jar);
        writeFabricJar(curseForgeJar);
        Files.writeString(configDir.resolve("example.json"), "{}", StandardCharsets.UTF_8);
        String sha1 = hash(Files.readAllBytes(jar), "SHA-1");
        String sha512 = hash(Files.readAllBytes(jar), "SHA-512");

        Server server = new Server();
        server.setDisplayName("Servidor de prueba");
        server.setServerDir(serverDir.toString());
        server.setPlatform(ServerPlatform.FABRIC);
        server.setLoader(ServerLoader.FABRIC);
        server.setLoaderVersion("0.16.10");
        server.setVersion("1.21.1");
        server.setExtensions(List.of(
                installedModrinthExtension("Example", "mods/example.jar", "project-id", "AbCdEf12"),
                installedRemoteExtension("CurseForge Example", "mods/curseforge-example.jar", ExtensionSourceType.CURSEFORGE, "curseforge", "123", "456")
        ));

        ModrinthModpackService service = new ModrinthModpackService(new FakeHttpClient(Map.of(
                "https://api.modrinth.com/v2/version/AbCdEf12",
                """
                {
                  "files": [
                    {
                      "primary": true,
                      "filename": "example.jar",
                      "url": "https://cdn.modrinth.com/data/project-id/versions/AbCdEf12/example.jar",
                      "size": 42,
                      "hashes": {
                        "sha1": "%s",
                        "sha512": "%s"
                      }
                    }
                  ]
                }
                """.formatted(sha1, sha512)
        )));
        Path target = tempDir.resolve("export.mrpack");

        ModrinthModpackService.ExportResult result = service.exportServerPack(server, target, ModrinthModpackService.ExportMode.SERVER);

        assertThat(result.exportedFiles()).isEqualTo(1);
        assertThat(result.overrideFiles()).isEqualTo(1);
        assertThat(result.skippedEntries()).anySatisfy(entry -> assertThat(entry).contains("CurseForge Example").contains("omitido"));
        try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(target.toFile(), StandardCharsets.UTF_8)) {
            assertThat(zipFile.getEntry("modrinth.index.json")).isNotNull();
            assertThat(zipFile.getEntry("server-overrides/config/example.json")).isNotNull();
            assertThat(zipFile.getEntry("server-overrides/mods/curseforge-example.jar")).isNull();
            JsonNode root = OBJECT_MAPPER.readTree(zipFile.getInputStream(zipFile.getEntry("modrinth.index.json")));
            assertThat(root.path("name").asText()).isEqualTo("Servidor de prueba");
            assertThat(root.path("dependencies").path("minecraft").asText()).isEqualTo("1.21.1");
            assertThat(root.path("dependencies").path("fabric-loader").asText()).isEqualTo("0.16.10");
            JsonNode file = root.path("files").get(0);
            assertThat(file.path("path").asText()).isEqualTo("mods/example.jar");
            assertThat(file.path("hashes").path("sha512").asText()).isEqualTo(sha512);
            assertThat(file.path("downloads").get(0).asText()).contains("cdn.modrinth.com");
        }
    }

    @Test
    void exportSkipsSymlinkedOverrideFiles() throws IOException {
        Path serverDir = tempDir.resolve("server-symlink-overrides");
        Path modsDir = Files.createDirectories(serverDir.resolve("mods"));
        Path configDir = Files.createDirectories(serverDir.resolve("config"));
        Path jar = modsDir.resolve("example.jar");
        Path outside = tempDir.resolve("outside-secret.txt");
        Path linkedOverride = configDir.resolve("linked-secret.txt");
        writeFabricJar(jar);
        Files.writeString(configDir.resolve("real.json"), "{}", StandardCharsets.UTF_8);
        Files.writeString(outside, "secret", StandardCharsets.UTF_8);
        Assumptions.assumeTrue(createSymbolicLinkIfSupported(linkedOverride, outside), "No se pueden crear enlaces simbolicos en este entorno.");
        String sha1 = hash(Files.readAllBytes(jar), "SHA-1");
        String sha512 = hash(Files.readAllBytes(jar), "SHA-512");

        Server server = new Server();
        server.setDisplayName("Servidor con symlink");
        server.setServerDir(serverDir.toString());
        server.setPlatform(ServerPlatform.FABRIC);
        server.setLoader(ServerLoader.FABRIC);
        server.setVersion("1.21.1");
        server.setExtensions(List.of(installedModrinthExtension("Example", "mods/example.jar", "project-id", "version-id")));

        ModrinthModpackService service = new ModrinthModpackService(new FakeHttpClient(Map.of(
                "https://api.modrinth.com/v2/version/version-id",
                """
                {
                  "files": [
                    {
                      "primary": true,
                      "filename": "example.jar",
                      "url": "https://cdn.modrinth.com/data/project-id/versions/version-id/example.jar",
                      "size": 42,
                      "hashes": {
                        "sha1": "%s",
                        "sha512": "%s"
                      }
                    }
                  ]
                }
                """.formatted(sha1, sha512)
        )));
        Path target = tempDir.resolve("symlink-overrides.mrpack");

        ModrinthModpackService.ExportResult result = service.exportServerPack(server, target, ModrinthModpackService.ExportMode.SERVER);

        assertThat(result.overrideFiles()).isEqualTo(1);
        try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(target.toFile(), StandardCharsets.UTF_8)) {
            assertThat(zipFile.getEntry("server-overrides/config/real.json")).isNotNull();
            assertThat(zipFile.getEntry("server-overrides/config/linked-secret.txt")).isNull();
        }
    }

    @Test
    void extractOverridesSkipsModJarOverridesAndKeepsConfigOverrides() throws IOException {
        Path pack = tempDir.resolve("mod-jar-overrides.mrpack");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(pack), StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("modrinth.index.json"));
            zip.write("""
                    {
                      "formatVersion": 1,
                      "game": "minecraft",
                      "name": "Override import",
                      "files": [
                        {
                          "path": "mods/indexed.jar",
                          "hashes": { "sha1": "a" },
                          "downloads": ["https://cdn.modrinth.com/data/proj/versions/ver/indexed.jar"]
                        }
                      ],
                      "dependencies": { "minecraft": "1.21.1" }
                    }
                    """.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("server-overrides/mods/unmanaged.jar"));
            zip.write("unverified jar".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
            zip.putNextEntry(new ZipEntry("server-overrides/config/example.json"));
            zip.write("{}".getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        Path serverDir = Files.createDirectories(tempDir.resolve("server-import-overrides"));
        Server server = new Server();
        server.setServerDir(serverDir.toString());
        List<String> warnings = new java.util.ArrayList<>();
        ModrinthModpackService service = new ModrinthModpackService(new FakeHttpClient(Map.of()));

        int extracted = service.extractOverrides(pack, server, ModrinthModpackService.ImportOptions.server(), warnings);

        assertThat(extracted).isEqualTo(1);
        assertThat(serverDir.resolve("config").resolve("example.json")).isRegularFile();
        assertThat(serverDir.resolve("mods").resolve("unmanaged.jar")).doesNotExist();
        assertThat(warnings).anySatisfy(warning -> assertThat(warning)
                .contains("Override omitido")
                .contains("mods/unmanaged.jar"));
    }

    @Test
    void exportResolvesManualModByModrinthFileHash() throws IOException {
        Path serverDir = tempDir.resolve("server-manual-resolved");
        Path modsDir = Files.createDirectories(serverDir.resolve("mods"));
        Path jar = modsDir.resolve("fallingtree.jar");
        writeFabricJar(jar);
        String sha1 = hash(Files.readAllBytes(jar), "SHA-1");
        String sha512 = hash(Files.readAllBytes(jar), "SHA-512");
        ServerExtension manual = installedManualExtension("FallingTree", "mods/fallingtree.jar");

        Server server = new Server();
        server.setDisplayName("Servidor manual");
        server.setServerDir(serverDir.toString());
        server.setPlatform(ServerPlatform.FABRIC);
        server.setLoader(ServerLoader.FABRIC);
        server.setVersion("1.21.1");
        server.setExtensions(List.of(manual));

        ModrinthModpackService service = new ModrinthModpackService(new FakeHttpClient(Map.of(
                "https://api.modrinth.com/v2/version_file/" + sha512 + "?algorithm=sha512",
                """
                {
                  "id": "version-id",
                  "project_id": "fallingtree-project",
                  "version_number": "1.0.0",
                  "files": [
                    {
                      "primary": true,
                      "filename": "fallingtree.jar",
                      "url": "https://cdn.modrinth.com/data/fallingtree-project/versions/version-id/fallingtree.jar",
                      "size": 42,
                      "hashes": {
                        "sha1": "%s",
                        "sha512": "%s"
                      }
                    }
                  ]
                }
                """.formatted(sha1, sha512)
        )));
        Path target = tempDir.resolve("manual-resolved.mrpack");

        ModrinthModpackService.ExportResult result = service.exportServerPack(server, target, ModrinthModpackService.ExportMode.SERVER);

        assertThat(result.exportedFiles()).isEqualTo(1);
        assertThat(result.resolvedManualFiles()).isEqualTo(1);
        assertThat(result.skippedEntries()).isEmpty();
        assertThat(manual.getSource().getType()).isEqualTo(ExtensionSourceType.MODRINTH);
        assertThat(manual.getSource().getProvider()).isEqualTo("modrinth");
        assertThat(manual.getSource().getProjectId()).isEqualTo("fallingtree-project");
        assertThat(manual.getSource().getVersionId()).isEqualTo("version-id");
        assertThat(manual.getLocalMetadata().getKnownRemoteVersion()).isEqualTo("1.0.0");
        try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(target.toFile(), StandardCharsets.UTF_8)) {
            JsonNode root = OBJECT_MAPPER.readTree(zipFile.getInputStream(zipFile.getEntry("modrinth.index.json")));
            JsonNode file = root.path("files").get(0);
            assertThat(file.path("path").asText()).isEqualTo("mods/fallingtree.jar");
            assertThat(file.path("hashes").path("sha512").asText()).isEqualTo(sha512);
            assertThat(file.path("downloads").get(0).asText()).contains("cdn.modrinth.com");
        }
    }

    @Test
    void exportSkipsManualModWhenModrinthHashCannotBeResolved() throws IOException {
        Path serverDir = tempDir.resolve("server-manual-unresolved");
        Path modsDir = Files.createDirectories(serverDir.resolve("mods"));
        Path jar = modsDir.resolve("local-only.jar");
        writeFabricJar(jar);
        ServerExtension manual = installedManualExtension("Local Only", "mods/local-only.jar");

        Server server = new Server();
        server.setDisplayName("Servidor manual");
        server.setServerDir(serverDir.toString());
        server.setPlatform(ServerPlatform.FABRIC);
        server.setLoader(ServerLoader.FABRIC);
        server.setVersion("1.21.1");
        server.setExtensions(List.of(manual));

        ModrinthModpackService service = new ModrinthModpackService(new FakeHttpClient(Map.of()));
        Path target = tempDir.resolve("manual-unresolved.mrpack");

        ModrinthModpackService.ExportResult result = service.exportServerPack(server, target, ModrinthModpackService.ExportMode.SERVER);

        assertThat(result.exportedFiles()).isZero();
        assertThat(result.resolvedManualFiles()).isZero();
        assertThat(result.skippedEntries()).anySatisfy(entry -> assertThat(entry)
                .contains("Local Only")
                .contains("identidad Modrinth verificable"));
        assertThat(manual.getSource().getType()).isEqualTo(ExtensionSourceType.MANUAL);
        try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(target.toFile(), StandardCharsets.UTF_8)) {
            JsonNode root = OBJECT_MAPPER.readTree(zipFile.getInputStream(zipFile.getEntry("modrinth.index.json")));
            assertThat(root.path("files")).isEmpty();
            assertThat(zipFile.getEntry("server-overrides/mods/local-only.jar")).isNull();
        }
    }

    private Path writePack(String indexJson) throws IOException {
        Path pack = tempDir.resolve("pack-" + System.nanoTime() + ".mrpack");
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(pack), StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("modrinth.index.json"));
            zip.write(indexJson.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return pack;
    }

    private static ServerExtension installedModrinthExtension(String name,
                                                             String relativePath,
                                                             String projectId,
                                                             String versionId) {
        return installedRemoteExtension(name, relativePath, ExtensionSourceType.MODRINTH, "modrinth", projectId, versionId);
    }

    private static ServerExtension installedManualExtension(String name, String relativePath) {
        ServerExtension extension = installedRemoteExtension(name, relativePath, ExtensionSourceType.MANUAL, "local-manual", null, null);
        extension.getSource().setUrl(Path.of(relativePath).getFileName().toString());
        return extension;
    }

    private static ServerExtension installedRemoteExtension(String name,
                                                           String relativePath,
                                                           ExtensionSourceType sourceType,
                                                           String provider,
                                                           String projectId,
                                                           String versionId) {
        ServerExtension extension = new ServerExtension();
        extension.setDisplayName(name);
        extension.setFileName(Path.of(relativePath).getFileName().toString());
        ExtensionSource source = new ExtensionSource();
        source.setType(sourceType);
        source.setProvider(provider);
        source.setProjectId(projectId);
        source.setVersionId(versionId);
        extension.setSource(source);
        ExtensionLocalMetadata metadata = new ExtensionLocalMetadata();
        metadata.setRelativePath(relativePath);
        metadata.setClientSide("optional");
        metadata.setServerSide("required");
        extension.setLocalMetadata(metadata);
        return extension;
    }

    private static ModrinthModpackService.IndexedFile indexedFile(String path, ModrinthModpackService.Env env) {
        return new ModrinthModpackService.IndexedFile(
                path,
                Map.of("sha1", "0123456789012345678901234567890123456789"),
                env,
                List.of("https://cdn.modrinth.com/data/project/versions/version/" + path.substring(path.lastIndexOf('/') + 1)),
                1L
        );
    }

    private static void writeFabricJar(Path jar) throws IOException {
        Files.createDirectories(jar.getParent());
        try (ZipOutputStream zip = new ZipOutputStream(Files.newOutputStream(jar), StandardCharsets.UTF_8)) {
            zip.putNextEntry(new ZipEntry("fabric.mod.json"));
            zip.write("""
                    {
                      "schemaVersion": 1,
                      "id": "example",
                      "name": "Example",
                      "version": "1.0.0",
                      "depends": { "minecraft": "1.21.1", "fabricloader": ">=0.16.0" }
                    }
                    """.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
    }

    private static boolean createSymbolicLinkIfSupported(Path link, Path target) {
        try {
            Files.createSymbolicLink(link, target);
            return true;
        } catch (UnsupportedOperationException | IOException | SecurityException ex) {
            return false;
        }
    }

    private static String hash(byte[] bytes, String algorithm) throws IOException {
        try {
            MessageDigest digest = MessageDigest.getInstance(algorithm);
            return HexFormat.of().formatHex(digest.digest(bytes));
        } catch (Exception ex) {
            throw new IOException(ex);
        }
    }

    private static final class FakeHttpClient extends ExtensionHttpClient {
        private final Map<String, String> responses;

        private FakeHttpClient(Map<String, String> responses) {
            this.responses = responses;
        }

        @Override
        String get(URI uri, Map<String, String> headers) throws IOException {
            String body = responses.get(uri.toString());
            if (body == null) {
                throw new IOException("URI no stubbeada en test: " + uri);
            }
            return body;
        }
    }
}
