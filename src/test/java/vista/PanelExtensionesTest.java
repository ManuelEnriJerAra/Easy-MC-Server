package vista;

import modelo.Server;
import modelo.extensions.ServerEcosystemType;
import modelo.extensions.ServerExtensionType;
import modelo.extensions.ServerPlatform;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PanelExtensionesTest {
    @Test
    void resolvesPaperAsPluginEcosystem() {
        Server server = new Server();
        server.setPlatform(ServerPlatform.PAPER);

        assertThat(PanelExtensiones.ecosystemOf(server)).isEqualTo(ServerEcosystemType.PLUGINS);
        assertThat(PanelExtensiones.extensionTypeForEcosystem(PanelExtensiones.ecosystemOf(server)))
                .isEqualTo(ServerExtensionType.PLUGIN);
        assertThat(PanelExtensiones.supportsModpackActions(PanelExtensiones.ecosystemOf(server))).isFalse();
    }

    @Test
    void resolvesForgeAndFabricAsModEcosystems() {
        Server forge = new Server();
        forge.setPlatform(ServerPlatform.FORGE);
        Server fabric = new Server();
        fabric.setPlatform(ServerPlatform.FABRIC);

        assertThat(PanelExtensiones.ecosystemOf(forge)).isEqualTo(ServerEcosystemType.MODS);
        assertThat(PanelExtensiones.ecosystemOf(fabric)).isEqualTo(ServerEcosystemType.MODS);
        assertThat(PanelExtensiones.extensionTypeForEcosystem(ServerEcosystemType.MODS))
                .isEqualTo(ServerExtensionType.MOD);
        assertThat(PanelExtensiones.supportsModpackActions(ServerEcosystemType.MODS)).isTrue();
    }

    @Test
    void resolvesVanillaAsNoManagedExtensionEcosystem() {
        Server server = new Server();
        server.setPlatform(ServerPlatform.VANILLA);

        assertThat(PanelExtensiones.ecosystemOf(server)).isEqualTo(ServerEcosystemType.NONE);
        assertThat(PanelExtensiones.extensionTypeForEcosystem(PanelExtensiones.ecosystemOf(server)))
                .isEqualTo(ServerExtensionType.UNKNOWN);
        assertThat(PanelExtensiones.supportsModpackActions(PanelExtensiones.ecosystemOf(server))).isFalse();
    }
}
