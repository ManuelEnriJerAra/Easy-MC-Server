package vista;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.awt.Font;
import java.awt.FontMetrics;

import javax.swing.JLabel;

import org.junit.jupiter.api.Test;

class TextEllipsizerTest {
    private final JLabel metricsSource = new JLabel();
    private final FontMetrics metrics = metricsSource.getFontMetrics(new Font(Font.SANS_SERIF, Font.PLAIN, 12));

    @Test
    void rightKeepsFullTextWhenItFits() {
        String text = "Servidor local";

        assertEquals(text, TextEllipsizer.right(text, metrics, metrics.stringWidth(text)));
    }

    @Test
    void rightAddsSuffixEllipsisWhenTextDoesNotFit() {
        String result = TextEllipsizer.right("Servidor local muy largo", metrics, metrics.stringWidth("Servidor..."));

        assertTrue(result.endsWith("..."));
        assertTrue(metrics.stringWidth(result) <= metrics.stringWidth("Servidor..."));
    }

    @Test
    void rightStrictUsesSingleDotWhenEllipsisDoesNotFit() {
        int width = metrics.stringWidth(".");

        assertEquals(".", TextEllipsizer.rightStrict("Servidor", metrics, width));
    }

    @Test
    void leftPreservesSuffixWhenTextDoesNotFit() {
        String result = TextEllipsizer.left("C:/Servers/Minecraft/Survival", metrics, metrics.stringWidth(".../Survival"));

        assertTrue(result.startsWith("..."));
        assertTrue(result.endsWith("Survival"));
        assertTrue(metrics.stringWidth(result) <= metrics.stringWidth(".../Survival"));
    }
}
