package vista;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;

import javax.swing.*;
import java.awt.*;

import static org.assertj.core.api.Assertions.assertThat;

class CardPanelThemeTest {
    @AfterEach
    void tearDown() {
        UIManager.put("Panel.background", null);
        UIManager.put("Label.foreground", null);
        UIManager.put("Label.disabledForeground", null);
    }

    @Test
    void actualizaElColorDelTituloAlCambiarElTema() {
        UIManager.put("Panel.background", Color.WHITE);
        UIManager.put("Label.foreground", Color.BLACK);

        CardPanel cardPanel = new CardPanel("Lista de servidores");
        Color colorClaro = cardPanel.getTitleLabel().getForeground();

        UIManager.put("Panel.background", new Color(38, 43, 51));
        UIManager.put("Label.foreground", Color.WHITE);
        cardPanel.refreshTheme();
        Color colorOscuro = cardPanel.getTitleLabel().getForeground();

        assertThat(colorClaro).isNotEqualTo(colorOscuro);
        assertThat(colorOscuro).isEqualTo(AppTheme.getCardTitleColor());
    }
}
