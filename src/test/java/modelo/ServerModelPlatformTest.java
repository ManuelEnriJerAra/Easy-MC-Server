package modelo;

import modelo.extensions.ServerCapability;
import modelo.extensions.ServerEcosystemType;
import modelo.extensions.ServerLoader;
import modelo.extensions.ServerPlatform;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import java.util.EnumSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class ServerModelPlatformTest {
    @Test
    void serverPlatformDefaults_debenSepararVanillaModsYPlugins() {
        assertPlatform(ServerPlatform.VANILLA, ServerLoader.VANILLA, ServerEcosystemType.NONE, false, false, false);

        assertPlatform(ServerPlatform.FORGE, ServerLoader.FORGE, ServerEcosystemType.MODS, true, true, false);
        assertPlatform(ServerPlatform.NEOFORGE, ServerLoader.NEOFORGE, ServerEcosystemType.MODS, true, true, false);
        assertPlatform(ServerPlatform.FABRIC, ServerLoader.FABRIC, ServerEcosystemType.MODS, true, true, false);
        assertPlatform(ServerPlatform.QUILT, ServerLoader.QUILT, ServerEcosystemType.MODS, true, true, false);

        assertPlatform(ServerPlatform.PAPER, ServerLoader.PAPER, ServerEcosystemType.PLUGINS, true, false, true);
        assertPlatform(ServerPlatform.SPIGOT, ServerLoader.SPIGOT, ServerEcosystemType.PLUGINS, true, false, true);
        assertPlatform(ServerPlatform.BUKKIT, ServerLoader.BUKKIT, ServerEcosystemType.PLUGINS, true, false, true);
        assertPlatform(ServerPlatform.PURPUR, ServerLoader.PURPUR, ServerEcosystemType.PLUGINS, true, false, true);
        assertPlatform(ServerPlatform.PUFFERFISH, ServerLoader.PUFFERFISH, ServerEcosystemType.PLUGINS, true, false, true);

        assertPlatform(ServerPlatform.UNKNOWN, ServerLoader.UNKNOWN, ServerEcosystemType.UNKNOWN, false, false, false);
    }

    @Test
    void setPlatform_debeSincronizarMetadatosYCapacidades() {
        Server vanilla = new Server();
        vanilla.setPlatform(ServerPlatform.VANILLA);

        assertThat(vanilla.getTipo()).isEqualTo("VANILLA");
        assertThat(vanilla.getLoader()).isEqualTo(ServerLoader.VANILLA);
        assertThat(vanilla.getEcosystemType()).isEqualTo(ServerEcosystemType.NONE);
        assertThat(vanilla.getCapabilities()).doesNotContain(
                ServerCapability.EXTENSIONS,
                ServerCapability.MOD_EXTENSIONS,
                ServerCapability.PLUGIN_EXTENSIONS
        );
        assertThat(vanilla.supportsExtensions()).isFalse();

        Server paper = new Server();
        paper.setPlatform(ServerPlatform.PAPER);

        assertThat(paper.getTipo()).isEqualTo("PAPER");
        assertThat(paper.getLoader()).isEqualTo(ServerLoader.PAPER);
        assertThat(paper.getEcosystemType()).isEqualTo(ServerEcosystemType.PLUGINS);
        assertThat(paper.getCapabilities()).contains(ServerCapability.EXTENSIONS, ServerCapability.PLUGIN_EXTENSIONS);
        assertThat(paper.getCapabilities()).doesNotContain(ServerCapability.MOD_EXTENSIONS);
        assertThat(paper.isPluginServer()).isTrue();
        assertThat(paper.supportsPluginExtensions()).isTrue();
        assertThat(paper.supportsModExtensions()).isFalse();
    }

    @Test
    void migrarModeloLegacy_debeMantenerCompatibilidadConTipoSerializado() throws Exception {
        String legacyJson = """
                {
                  "id": "legacy-plugin",
                  "displayName": "Legacy Plugin Server",
                  "tipo": "SPIGOT"
                }
                """;

        Server server = new ObjectMapper().readValue(legacyJson, Server.class);
        server.migrarModeloLegacy();

        assertThat(server.getTipo()).isEqualTo("SPIGOT");
        assertThat(server.getPlatform()).isEqualTo(ServerPlatform.SPIGOT);
        assertThat(server.getLoader()).isEqualTo(ServerLoader.SPIGOT);
        assertThat(server.getEcosystemType()).isEqualTo(ServerEcosystemType.PLUGINS);
        assertThat(server.getCapabilities()).contains(ServerCapability.EXTENSIONS, ServerCapability.PLUGIN_EXTENSIONS);
        assertThat(server.getCapabilities()).doesNotContain(ServerCapability.MOD_EXTENSIONS);
        assertThat(server.isPluginServer()).isTrue();
    }

    @Test
    void migrarModeloLegacy_debeCorregirCapacidadesObsoletasSegunPlataforma() {
        Server server = new Server();
        server.setTipo("VANILLA");
        server.setCapabilities(EnumSet.of(
                ServerCapability.CORE_SERVER,
                ServerCapability.EXTENSIONS,
                ServerCapability.MOD_EXTENSIONS,
                ServerCapability.PLUGIN_EXTENSIONS
        ));

        boolean changed = server.migrarModeloLegacy();

        assertThat(changed).isTrue();
        assertThat(server.getPlatform()).isEqualTo(ServerPlatform.VANILLA);
        assertThat(server.getEcosystemType()).isEqualTo(ServerEcosystemType.NONE);
        assertThat(server.getCapabilities()).doesNotContain(
                ServerCapability.EXTENSIONS,
                ServerCapability.MOD_EXTENSIONS,
                ServerCapability.PLUGIN_EXTENSIONS
        );
        assertThat(server.supportsExtensions()).isFalse();
    }

    @Test
    void tipoLegacyDesconocido_noDebeActivarComportamientoForgeNiExtensiones() {
        Server server = new Server();
        server.setTipo("legacy-forge-like-but-unknown");
        server.setCapabilities(EnumSet.of(ServerCapability.EXTENSIONS, ServerCapability.MOD_EXTENSIONS));

        boolean changed = server.migrarModeloLegacy();

        assertThat(changed).isTrue();
        assertThat(server.getPlatform()).isEqualTo(ServerPlatform.UNKNOWN);
        assertThat(server.getLoader()).isEqualTo(ServerLoader.UNKNOWN);
        assertThat(server.getEcosystemType()).isEqualTo(ServerEcosystemType.UNKNOWN);
        assertThat(server.getCapabilities()).contains(ServerCapability.CORE_SERVER);
        assertThat(server.getCapabilities()).doesNotContain(
                ServerCapability.EXTENSIONS,
                ServerCapability.MOD_EXTENSIONS,
                ServerCapability.PLUGIN_EXTENSIONS
        );
        assertThat(server.isModServer()).isFalse();
        assertThat(server.isPluginServer()).isFalse();
        assertThat(server.supportsExtensions()).isFalse();
    }

    @Test
    void fromLegacyType_debeAceptarAliasesHistoricosSinInferirForgePorDefecto() {
        assertThat(ServerPlatform.fromLegacyType("PaperMC")).isEqualTo(ServerPlatform.PAPER);
        assertThat(ServerPlatform.fromLegacyType("CraftBukkit")).isEqualTo(ServerPlatform.BUKKIT);
        assertThat(ServerPlatform.fromLegacyType("Forge plugin server")).isEqualTo(ServerPlatform.UNKNOWN);
    }

    private void assertPlatform(ServerPlatform platform,
                                ServerLoader loader,
                                ServerEcosystemType ecosystemType,
                                boolean supportsExtensions,
                                boolean modPlatform,
                                boolean pluginPlatform) {
        assertThat(platform.getDefaultLoader()).isEqualTo(loader);
        assertThat(platform.getDefaultEcosystemType()).isEqualTo(ecosystemType);
        assertThat(platform.supportsExtensions()).isEqualTo(supportsExtensions);
        assertThat(platform.isModPlatform()).isEqualTo(modPlatform);
        assertThat(platform.isPluginPlatform()).isEqualTo(pluginPlatform);

        Set<ServerCapability> capabilities = platform.defaultCapabilities();
        assertThat(capabilities).contains(ServerCapability.CORE_SERVER);
        assertThat(capabilities.contains(ServerCapability.EXTENSIONS)).isEqualTo(supportsExtensions);
        assertThat(capabilities.contains(ServerCapability.MOD_EXTENSIONS)).isEqualTo(modPlatform);
        assertThat(capabilities.contains(ServerCapability.PLUGIN_EXTENSIONS)).isEqualTo(pluginPlatform);
    }
}
