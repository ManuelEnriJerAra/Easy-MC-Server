package vista;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;
import java.lang.reflect.Field;

import static org.assertj.core.api.Assertions.assertThat;

class AppThemeTest {
    @AfterEach
    void tearDown() {
        UIManager.put("Panel.background", null);
        UIManager.put("Label.foreground", null);
        UIManager.put("Label.disabledForeground", null);
    }

    @Test
    void usaElFondoDeConsolaClaroEnTemasClaros() throws Exception {
        UIManager.put("Panel.background", Color.WHITE);
        UIManager.put("Label.foreground", Color.BLACK);

        assertThat(AppTheme.getConsoleBackground()).isEqualTo(colorConstanteConsolaClara());
    }

    @Test
    void panelConsolaAplicaElFondoDeConsolaClaroEnTemaClaro() throws Exception {
        UIManager.put("Panel.background", Color.WHITE);
        UIManager.put("Label.foreground", Color.BLACK);

        PanelConsola panelConsola = new PanelConsola(null);
        Field consolaPaneField = PanelConsola.class.getDeclaredField("consolaPane");
        consolaPaneField.setAccessible(true);
        JTextPane consolaPane = (JTextPane) consolaPaneField.get(panelConsola);

        assertThat(consolaPane.getBackground()).isEqualTo(colorConstanteConsolaClara());
    }

    private Color colorConstanteConsolaClara() throws Exception {
        Field field = AppTheme.class.getDeclaredField("LIGHT_THEME_CONSOLE_BACKGROUND");
        field.setAccessible(true);
        return (Color) field.get(null);
    }
}
