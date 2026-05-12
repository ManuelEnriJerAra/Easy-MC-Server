package vista;

import controlador.GestorServidores;
import modelo.Server;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.swing.JPanel;
import java.awt.Component;
import java.lang.reflect.Constructor;
import java.io.File;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class PanelServidoresTest {
    @TempDir
    Path tempDir;

    @Test
    void muestraServidoresAunqueNoTenganVersionDetectada() {
        GestorServidores gestor = gestorServidores(tempDir.resolve("servers.json").toFile());
        Server server = new Server();
        server.setDisplayName("Imported Paper");
        server.setServerDir(tempDir.resolve("paper").toString());
        server.setVersion(null);
        gestor.guardarServidor(server);

        PanelServidores panel = new PanelServidores(gestor);
        Component view = panel.getViewport().getView();

        assertThat(view).isInstanceOf(JPanel.class);
        assertThat(((JPanel) view).getComponentCount()).isEqualTo(1);
    }

    private GestorServidores gestorServidores(File jsonFile) {
        try {
            Constructor<GestorServidores> constructor = GestorServidores.class.getDeclaredConstructor(File.class);
            constructor.setAccessible(true);
            return constructor.newInstance(jsonFile);
        } catch (ReflectiveOperationException e) {
            throw new AssertionError(e);
        }
    }
}
