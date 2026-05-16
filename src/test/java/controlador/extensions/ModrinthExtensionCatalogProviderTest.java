package controlador.extensions;

import modelo.Server;
import modelo.extensions.ExtensionSourceType;
import modelo.extensions.ServerExtensionType;
import modelo.extensions.ServerPlatform;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ModrinthExtensionCatalogProviderTest {
    @Test
    void shouldFilterSearchByMinecraftVersionAndLoader() throws IOException {
        ModrinthExtensionCatalogProvider provider = new ModrinthExtensionCatalogProvider(new FakeHttpClient(Map.of(
                modrinthSearchUrl("sodium", 10, 0, "downloads", "[[\"project_type:mod\"],[\"categories:fabric\"],[\"versions:1.21.1\"]]"),
                """
                {
                  "hits": [
                    {
                      "project_id": "A1",
                      "slug": "sodium",
                      "project_type": "mod",
                      "title": "Sodium",
                      "author": "jellysquid",
                      "description": "Performance mod",
                      "categories": ["fabric"],
                      "versions": ["1.21.1"],
                      "icon_url": "https://cdn.example/sodium.png",
                      "latest_version": "v100"
                    },
                    {
                      "project_id": "A2",
                      "slug": "other",
                      "project_type": "mod",
                      "title": "Other",
                      "author": "someone",
                      "description": "Wrong version",
                      "categories": ["fabric"],
                      "versions": ["1.20.6"],
                      "latest_version": "v200"
                    }
                  ]
                }
                """
        )));

        List<ExtensionCatalogEntry> results = provider.search(
                new ExtensionCatalogQuery("sodium", ServerPlatform.FABRIC, ServerExtensionType.MOD, "1.21.1", 10)
        );

        assertThat(results).singleElement().satisfies(entry -> {
            assertThat(entry.projectId()).isEqualTo("A1");
            assertThat(entry.versionId()).isNull();
            assertThat(entry.version()).isNull();
            assertThat(entry.sourceType()).isEqualTo(ExtensionSourceType.MODRINTH);
            assertThat(entry.compatiblePlatforms()).isEmpty();
            assertThat(entry.compatibleMinecraftVersions()).isEmpty();
        });
    }

    @Test
    void shouldAllowBroadTypedSearchWithoutLoaderOrVersionFacets() throws IOException {
        ModrinthExtensionCatalogProvider provider = new ModrinthExtensionCatalogProvider(new FakeHttpClient(Map.of(
                modrinthSearchUrl("sodium", 10, 0, "downloads", "[[\"project_type:mod\"]]"),
                """
                {
                  "hits": [
                    {
                      "project_id": "A1",
                      "slug": "sodium",
                      "project_type": "mod",
                      "title": "Sodium",
                      "author": "jellysquid",
                      "description": "Performance mod",
                      "categories": ["fabric"],
                      "versions": ["1.21.1"],
                      "latest_version": "v100",
                      "downloads": 9000
                    },
                    {
                      "project_id": "A2",
                      "slug": "sodium-forge",
                      "project_type": "mod",
                      "title": "Sodium Forge Port",
                      "author": "someone",
                      "description": "Different loader",
                      "categories": ["forge"],
                      "versions": ["1.20.1"],
                      "latest_version": "v200",
                      "downloads": 100
                    }
                  ]
                }
                """
        )));

        List<ExtensionCatalogEntry> results = provider.search(
                new ExtensionCatalogQuery("sodium", ServerPlatform.UNKNOWN, ServerExtensionType.MOD, "", 10)
        );

        assertThat(results).extracting(ExtensionCatalogEntry::projectId).containsExactly("A1", "A2");
        assertThat(results).extracting(ExtensionCatalogEntry::downloads).containsExactly(9000L, 100L);
        assertThat(results).allSatisfy(entry -> {
            assertThat(entry.versionId()).isNull();
            assertThat(entry.version()).isNull();
            assertThat(entry.compatiblePlatforms()).isEmpty();
            assertThat(entry.compatibleMinecraftVersions()).isEmpty();
        });
    }

    @Test
    void shouldNotAdvertiseProjectWideMetadataAsCompatibilityForBroadSearches() throws IOException {
        ModrinthExtensionCatalogProvider provider = new ModrinthExtensionCatalogProvider(new FakeHttpClient(Map.of(
                modrinthSearchUrl("create", 10, 0, "downloads", "[[\"project_type:mod\"]]"),
                """
                {
                  "hits": [
                    {
                      "project_id": "create",
                      "slug": "create",
                      "project_type": "mod",
                      "title": "Create",
                      "author": "simibubi",
                      "description": "Build contraptions",
                      "categories": ["fabric", "forge", "neoforge"],
                      "versions": ["1.20.1", "1.21.1"],
                      "latest_version": "latest-project-version",
                      "downloads": 1000
                    }
                  ]
                }
                """
        )));

        List<ExtensionCatalogEntry> results = provider.search(
                new ExtensionCatalogQuery("create", ServerPlatform.UNKNOWN, ServerExtensionType.MOD, "", 10)
        );

        assertThat(results).singleElement().satisfies(entry -> {
            assertThat(entry.projectId()).isEqualTo("create");
            assertThat(entry.versionId()).isNull();
            assertThat(entry.version()).isNull();
            assertThat(entry.extensionType()).isEqualTo(ServerExtensionType.MOD);
            assertThat(entry.compatiblePlatforms()).isEmpty();
            assertThat(entry.compatibleMinecraftVersions()).isEmpty();
        });
    }

    @Test
    void shouldNotAdvertiseProjectWideMetadataAsCompatibilityForFilteredSearches() throws IOException {
        ModrinthExtensionCatalogProvider provider = new ModrinthExtensionCatalogProvider(new FakeHttpClient(Map.of(
                modrinthSearchUrl("tacz", 10, 0, "downloads", "[[\"project_type:mod\"],[\"categories:forge\"],[\"versions:1.21.1\"]]"),
                """
                {
                  "hits": [
                    {
                      "project_id": "SzzJttH8",
                      "slug": "timeless-and-classics-zero",
                      "project_type": "mod",
                      "title": "[TaCZ] Timeless and Classics Zero",
                      "author": "TACZ",
                      "description": "Project has Forge and 1.21.1 somewhere, but not in the same build.",
                      "categories": ["forge"],
                      "versions": ["1.20.1", "1.21.1"],
                      "latest_version": "latest-project-version",
                      "downloads": 1000
                    }
                  ]
                }
                """
        )));

        List<ExtensionCatalogEntry> results = provider.search(
                new ExtensionCatalogQuery("tacz", ServerPlatform.FORGE, ServerExtensionType.MOD, "1.21.1", 10)
        );

        assertThat(results).singleElement().satisfies(entry -> {
            assertThat(entry.projectId()).isEqualTo("SzzJttH8");
            assertThat(entry.versionId()).isNull();
            assertThat(entry.version()).isNull();
            assertThat(entry.compatiblePlatforms()).isEmpty();
            assertThat(entry.compatibleMinecraftVersions()).isEmpty();
        });
    }

    @Test
    void shouldKeepDetailsConservativeWhenNoVersionBuildMatchesServerFilters() throws IOException {
        ModrinthExtensionCatalogProvider provider = new ModrinthExtensionCatalogProvider(new FakeHttpClient(Map.of(
                "https://api.modrinth.com/v2/project/timeless-and-classics-zero",
                """
                {
                  "id": "SzzJttH8",
                  "slug": "timeless-and-classics-zero",
                  "project_type": "mod",
                  "title": "[TaCZ] Timeless and Classics Zero",
                  "description": "Gun mod",
                  "body": "Long description",
                  "categories": ["forge"],
                  "game_versions": ["1.20.1", "1.21.1"]
                }
                """,
                "https://api.modrinth.com/v2/project/timeless-and-classics-zero/version?loaders=%5B%22forge%22%5D&game_versions=%5B%221.21.1%22%5D&include_changelog=false",
                "[]"
        )));

        ExtensionCatalogDetails details = provider.getDetails(
                "timeless-and-classics-zero",
                new ExtensionCatalogQuery("tacz", ServerPlatform.FORGE, ServerExtensionType.MOD, "1.21.1", 20)
        ).orElseThrow();

        assertThat(details.versions()).isEmpty();
        assertThat(details.entry().compatiblePlatforms()).isEmpty();
        assertThat(details.entry().compatibleMinecraftVersions()).isEmpty();
    }


    @Test
    void shouldFilterPluginSearchByPaperPlatformAndServerSide() throws IOException {
        ModrinthExtensionCatalogProvider provider = new ModrinthExtensionCatalogProvider(new FakeHttpClient(Map.of(
                modrinthSearchUrl("viaversion", 10, 0, "downloads", "[[\"project_type:plugin\",\"project_type:mod\"],[\"categories:paper\"],[\"versions:1.21.1\"],[\"client_side:unsupported\"],[\"server_side:required\",\"server_side:optional\"]]"),
                """
                {
                  "hits": [
                    {
                      "project_id": "P1",
                      "slug": "viaversion",
                      "project_type": "plugin",
                      "title": "ViaVersion",
                      "author": "ViaVersion",
                      "description": "Protocol plugin",
                      "categories": ["paper"],
                      "versions": ["1.21.1"],
                      "icon_url": "https://cdn.example/vv.png",
                      "latest_version": "v-plugin",
                      "client_side": "unsupported",
                      "server_side": "required"
                    },
                    {
                      "project_id": "P1",
                      "slug": "viaversion",
                      "project_type": "plugin",
                      "title": "ViaVersion",
                      "author": "ViaVersion",
                      "description": "Duplicate result from broadened facets",
                      "categories": ["paper"],
                      "versions": ["1.21.1"],
                      "latest_version": "v-plugin-duplicate",
                      "client_side": "unsupported",
                      "server_side": "required"
                    },
                    {
                      "project_id": "MM",
                      "slug": "mythicmobs",
                      "project_type": "mod",
                      "title": "MythicMobs",
                      "author": "Lumine",
                      "description": "Plugin marked as mod by API",
                      "categories": ["paper", "spigot"],
                      "versions": ["1.21.1"],
                      "latest_version": "v-mythic",
                      "client_side": "unsupported",
                      "server_side": "required"
                    },
                    {
                      "project_id": "GEYSER",
                      "slug": "geyser",
                      "project_type": "mod",
                      "title": "Geyser",
                      "author": "GeyserMC",
                      "description": "Multi-loader plugin marked as a mod by the API",
                      "categories": ["bungeecord", "fabric", "neoforge", "paper", "spigot", "velocity"],
                      "versions": ["1.21.1"],
                      "latest_version": "v-geyser",
                      "client_side": "unsupported",
                      "server_side": "required"
                    },
                    {
                      "project_id": "M1",
                      "slug": "viamod",
                      "project_type": "mod",
                      "title": "Via Mod",
                      "author": "Someone",
                      "description": "Wrong ecosystem",
                      "categories": ["fabric"],
                      "versions": ["1.21.1"],
                      "latest_version": "v-mod"
                    }
                  ]
                }
                """
        )));

        List<ExtensionCatalogEntry> results = provider.search(new ExtensionCatalogQuery(
                "viaversion",
                ServerPlatform.PAPER,
                ServerExtensionType.PLUGIN,
                "1.21.1",
                10,
                "downloads",
                ExtensionSideFilter.SERVER
        ));

        assertThat(results).hasSize(3);
        assertThat(results).extracting(ExtensionCatalogEntry::projectId).containsExactly("P1", "MM", "GEYSER");
        assertThat(results).anySatisfy(entry -> {
            assertThat(entry.projectId()).isEqualTo("P1");
            assertThat(entry.extensionType()).isEqualTo(ServerExtensionType.PLUGIN);
            assertThat(entry.compatiblePlatforms()).isEmpty();
            assertThat(entry.compatibleMinecraftVersions()).isEmpty();
            assertThat(entry.serverSide()).isEqualTo("required");
        });
        assertThat(results).anySatisfy(entry -> {
            assertThat(entry.projectId()).isEqualTo("MM");
            assertThat(entry.displayName()).isEqualTo("MythicMobs");
            assertThat(entry.extensionType()).isEqualTo(ServerExtensionType.PLUGIN);
            assertThat(entry.projectUrl()).contains("/plugin/mythicmobs");
        });
        assertThat(results).anySatisfy(entry -> {
            assertThat(entry.projectId()).isEqualTo("GEYSER");
            assertThat(entry.displayName()).isEqualTo("Geyser");
            assertThat(entry.extensionType()).isEqualTo(ServerExtensionType.PLUGIN);
            assertThat(entry.compatiblePlatforms()).isEmpty();
            assertThat(entry.compatibleMinecraftVersions()).isEmpty();
            assertThat(entry.projectUrl()).contains("/plugin/geyser");
        });
    }

    @Test
    void shouldResolveCompatibleDownloadForSelectedServer() throws IOException {
        ModrinthExtensionCatalogProvider provider = new ModrinthExtensionCatalogProvider(new FakeHttpClient(Map.of(
                "https://api.modrinth.com/v2/project/sodium",
                """
                {
                  "id": "A1",
                  "slug": "sodium",
                  "project_type": "mod",
                  "title": "Sodium",
                  "description": "Performance mod",
                  "body": "Long description",
                  "categories": ["fabric"],
                  "game_versions": ["1.21.1"],
                  "license": { "name": "MIT" }
                }
                """,
                "https://api.modrinth.com/v2/project/sodium/version?loaders=%5B%22fabric%22%5D&game_versions=%5B%221.21.1%22%5D&include_changelog=false",
                """
                [
                  {
                    "id": "v100",
                    "name": "Sodium 0.6.0",
                    "version_number": "0.6.0",
                    "loaders": ["fabric"],
                    "game_versions": ["1.21.1"],
                    "date_published": "2026-03-01T12:00:00Z",
                    "files": [
                      {
                        "primary": true,
                        "filename": "sodium-fabric-0.6.0.jar",
                        "url": "https://cdn.modrinth.com/data/A1/versions/v100/sodium-fabric-0.6.0.jar"
                      }
                    ]
                  }
                ]
                """
        )));
        Server server = new Server();
        server.setPlatform(ServerPlatform.FABRIC);
        server.setVersion("1.21.1");

        ExtensionDownloadPlan plan = provider.resolveDownload("sodium", null, server).orElseThrow();

        assertThat(plan.sourceType()).isEqualTo(ExtensionSourceType.MODRINTH);
        assertThat(plan.extensionType()).isEqualTo(ServerExtensionType.MOD);
        assertThat(plan.platform()).isEqualTo(ServerPlatform.FABRIC);
        assertThat(plan.minecraftVersionConstraint()).contains("1.21.1");
        assertThat(plan.downloadUrl()).contains("sodium-fabric-0.6.0.jar");
    }

    @Test
    void shouldResolvePluginDownloadPlanWithDependencies() throws IOException {
        ModrinthExtensionCatalogProvider provider = new ModrinthExtensionCatalogProvider(new FakeHttpClient(Map.of(
                "https://api.modrinth.com/v2/project/geyser",
                """
                {
                  "id": "P1",
                  "slug": "geyser",
                  "project_type": "plugin",
                  "title": "Geyser",
                  "author": "GeyserMC",
                  "description": "Bedrock bridge",
                  "body": "Long plugin description",
                  "categories": ["paper"],
                  "game_versions": ["1.21.1"],
                  "icon_url": "https://cdn.example/geyser.png",
                  "downloads": 100,
                  "issues_url": "https://github.com/GeyserMC/Geyser/issues",
                  "source_url": "https://github.com/GeyserMC/Geyser",
                  "wiki_url": "https://wiki.geysermc.org",
                  "license": { "name": "MIT" },
                  "client_side": "unsupported",
                  "server_side": "required"
                }
                """,
                "https://api.modrinth.com/v2/project/geyser/version?loaders=%5B%22paper%22%5D&game_versions=%5B%221.21.1%22%5D&include_changelog=false",
                """
                [
                  {
                    "id": "gv1",
                    "name": "Geyser 2.6.0",
                    "version_number": "2.6.0",
                    "loaders": ["paper"],
                    "game_versions": ["1.21.1"],
                    "date_published": "2026-03-01T12:00:00Z",
                    "dependencies": [
                      { "project_id": "floodgate", "dependency_type": "required" },
                      { "project_id": "viafabric", "dependency_type": "required" },
                      { "project_id": "luckperms", "dependency_type": "optional" }
                    ],
                    "files": [
                      {
                        "primary": true,
                        "filename": "geyser-paper.jar",
                        "url": "https://cdn.modrinth.com/data/P1/versions/gv1/geyser-paper.jar"
                      }
                    ]
                  }
                ]
                """,
                "https://api.modrinth.com/v2/project/floodgate",
                "{ \"id\": \"floodgate\", \"slug\": \"floodgate\", \"title\": \"Floodgate\", \"project_type\": \"plugin\" }",
                "https://api.modrinth.com/v2/project/viafabric",
                "{ \"id\": \"viafabric\", \"slug\": \"viafabric\", \"title\": \"ViaFabric\", \"project_type\": \"mod\" }",
                "https://api.modrinth.com/v2/project/luckperms",
                "{ \"id\": \"luckperms\", \"slug\": \"luckperms\", \"title\": \"LuckPerms\", \"project_type\": \"plugin\" }"
        )));
        Server server = new Server();
        server.setPlatform(ServerPlatform.PAPER);
        server.setVersion("1.21.1");

        ExtensionCatalogDetails details = provider.getDetails(
                "geyser",
                new ExtensionCatalogQuery("geyser", ServerPlatform.PAPER, ServerExtensionType.PLUGIN, "1.21.1", 20)
        ).orElseThrow();
        ExtensionDownloadPlan plan = provider.resolveDownload("geyser", null, server).orElseThrow();

        assertThat(details.summary()).contains("Long plugin description");
        assertThat(details.websiteUrl()).isEqualTo("https://wiki.geysermc.org");
        assertThat(details.issuesUrl()).isEqualTo("https://github.com/GeyserMC/Geyser/issues");
        assertThat(plan.extensionType()).isEqualTo(ServerExtensionType.PLUGIN);
        assertThat(plan.platform()).isEqualTo(ServerPlatform.PAPER);
        assertThat(plan.downloadUrl()).endsWith("geyser-paper.jar");
        assertThat(plan.dependencies()).anySatisfy(dependency -> {
            assertThat(dependency.projectId()).isEqualTo("floodgate");
            assertThat(dependency.displayName()).isEqualTo("Floodgate");
            assertThat(dependency.required()).isTrue();
        });
        assertThat(plan.dependencies()).anySatisfy(dependency -> {
            assertThat(dependency.projectId()).isEqualTo("luckperms");
            assertThat(dependency.displayName()).isEqualTo("LuckPerms");
            assertThat(dependency.required()).isFalse();
        });
        assertThat(plan.dependencies())
                .extracting(ExtensionDependency::projectId)
                .doesNotContain("viafabric");
    }

    @Test
    void shouldResolveDownloadForModernNumericMinecraftVersion() throws IOException {
        ModrinthExtensionCatalogProvider provider = new ModrinthExtensionCatalogProvider(new FakeHttpClient(Map.of(
                "https://api.modrinth.com/v2/project/nova-framework",
                """
                {
                  "id": "yCVqpwUy",
                  "slug": "nova-framework",
                  "project_type": "mod",
                  "title": "Nova",
                  "author": "xenondevs",
                  "description": "server-side modding framework for paper",
                  "categories": ["paper", "purpur"],
                  "game_versions": ["26.1.1", "26.1.2"],
                  "client_side": "unsupported",
                  "server_side": "required"
                }
                """,
                "https://api.modrinth.com/v2/project/nova-framework/version?loaders=%5B%22paper%22%5D&game_versions=%5B%2226.1.1%22%5D&include_changelog=false",
                """
                [
                  {
                    "id": "nova-2611",
                    "name": "Nova v0.23.0-alpha.1",
                    "version_number": "0.23.0-alpha.1",
                    "version_type": "alpha",
                    "loaders": ["paper", "purpur"],
                    "game_versions": ["26.1.1"],
                    "date_published": "2026-04-01T12:00:00Z",
                    "files": [
                      {
                        "primary": true,
                        "filename": "Nova-0.23.0-alpha.1+MC-26.1.1.jar",
                        "url": "https://cdn.modrinth.com/data/yCVqpwUy/versions/nova-2611/Nova-0.23.0-alpha.1%2BMC-26.1.1.jar"
                      }
                    ]
                  }
                ]
                """
        )));
        Server server = new Server();
        server.setPlatform(ServerPlatform.PAPER);
        server.setVersion("26.1.1");

        ExtensionDownloadPlan plan = provider.resolveDownload("nova-framework", null, server).orElseThrow();

        assertThat(plan.versionId()).isEqualTo("nova-2611");
        assertThat(plan.platform()).isEqualTo(ServerPlatform.PAPER);
        assertThat(plan.minecraftVersionConstraint()).isEqualTo("26.1.1");
        assertThat(plan.downloadUrl()).contains("MC-26.1.1");
    }

    @Test
    void shouldRespectSelectedVersionIdWhenResolvingDownloadPlan() throws IOException {
        ModrinthExtensionCatalogProvider provider = new ModrinthExtensionCatalogProvider(new FakeHttpClient(Map.of(
                "https://api.modrinth.com/v2/project/geyser",
                """
                {
                  "id": "P1",
                  "slug": "geyser",
                  "project_type": "plugin",
                  "title": "Geyser",
                  "author": "GeyserMC",
                  "categories": ["paper"],
                  "game_versions": ["1.21.1"]
                }
                """,
                "https://api.modrinth.com/v2/project/geyser/version?loaders=%5B%22paper%22%5D&game_versions=%5B%221.21.1%22%5D&include_changelog=false",
                """
                [
                  {
                    "id": "latest",
                    "name": "Latest",
                    "version_number": "2.0.0",
                    "loaders": ["paper"],
                    "game_versions": ["1.21.1"],
                    "date_published": "2026-04-01T12:00:00Z",
                    "files": [
                      { "primary": true, "filename": "geyser-latest.jar", "url": "https://cdn.example/geyser-latest.jar" }
                    ]
                  },
                  {
                    "id": "selected",
                    "name": "Selected",
                    "version_number": "1.9.0",
                    "loaders": ["paper"],
                    "game_versions": ["1.21.1"],
                    "date_published": "2026-03-01T12:00:00Z",
                    "files": [
                      { "primary": true, "filename": "geyser-selected.jar", "url": "https://cdn.example/geyser-selected.jar" }
                    ]
                  }
                ]
                """
        )));
        Server server = new Server();
        server.setPlatform(ServerPlatform.PAPER);
        server.setVersion("1.21.1");

        ExtensionDownloadPlan selected = provider.resolveDownload("geyser", "selected", server).orElseThrow();

        assertThat(selected.versionId()).isEqualTo("selected");
        assertThat(selected.versionNumber()).isEqualTo("1.9.0");
        assertThat(selected.downloadUrl()).endsWith("geyser-selected.jar");
        ExtensionDownloadPlan fallback = provider.resolveDownload("geyser", null, server).orElseThrow();

        assertThat(fallback.versionId()).isEqualTo("latest");
        assertThat(fallback.versionNumber()).isEqualTo("2.0.0");
        assertThat(fallback.downloadUrl()).endsWith("geyser-latest.jar");
        assertThat(provider.resolveDownload("geyser", "missing", server)).isEmpty();
    }

    @Test
    void shouldPreferReleaseOverNewerBetaByDefault() throws IOException {
        ModrinthExtensionCatalogProvider provider = new ModrinthExtensionCatalogProvider(new FakeHttpClient(Map.of(
                "https://api.modrinth.com/v2/project/geyser",
                """
                {
                  "id": "P1",
                  "slug": "geyser",
                  "project_type": "plugin",
                  "title": "Geyser",
                  "author": "GeyserMC",
                  "categories": ["paper"],
                  "game_versions": ["1.21.1"]
                }
                """,
                "https://api.modrinth.com/v2/project/geyser/version?loaders=%5B%22paper%22%5D&game_versions=%5B%221.21.1%22%5D&include_changelog=false",
                """
                [
                  {
                    "id": "beta",
                    "name": "Geyser 2.1.0 beta",
                    "version_number": "2.1.0-beta.1",
                    "version_type": "beta",
                    "loaders": ["paper"],
                    "game_versions": ["1.21.1"],
                    "date_published": "2026-04-01T12:00:00Z",
                    "files": [
                      { "primary": true, "filename": "geyser-beta.jar", "url": "https://cdn.example/geyser-beta.jar" }
                    ]
                  },
                  {
                    "id": "release",
                    "name": "Geyser 2.0.0",
                    "version_number": "2.0.0",
                    "version_type": "release",
                    "loaders": ["paper"],
                    "game_versions": ["1.21.1"],
                    "date_published": "2026-03-01T12:00:00Z",
                    "files": [
                      { "primary": true, "filename": "geyser-release.jar", "url": "https://cdn.example/geyser-release.jar" }
                    ]
                  }
                ]
                """
        )));
        Server server = new Server();
        server.setPlatform(ServerPlatform.PAPER);
        server.setVersion("1.21.1");

        ExtensionDownloadPlan selected = provider.resolveDownload("geyser", null, server).orElseThrow();

        assertThat(selected.versionId()).isEqualTo("release");
        assertThat(selected.versionNumber()).isEqualTo("2.0.0");
        assertThat(selected.downloadUrl()).endsWith("geyser-release.jar");
    }

    @Test
    void shouldAdvertiseModAndPluginSupport() {
        ExtensionCatalogProviderDescriptor descriptor = new ModrinthExtensionCatalogProvider().describeProvider();

        assertThat(descriptor.supportedExtensionTypes()).contains(ServerExtensionType.MOD, ServerExtensionType.PLUGIN);
        assertThat(descriptor.supportedPlatforms()).contains(ServerPlatform.FABRIC, ServerPlatform.PAPER);
        assertThat(descriptor.capabilities()).contains(ExtensionCatalogCapability.SEARCH, ExtensionCatalogCapability.DOWNLOAD);
    }

    private static String modrinthSearchUrl(String query, int limit, int offset, String index, String facets) {
        return "https://api.modrinth.com/v2/search?query=" + encode(query)
                + "&limit=" + limit
                + "&offset=" + offset
                + "&index=" + encode(index)
                + "&facets=" + encode(facets);
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
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
