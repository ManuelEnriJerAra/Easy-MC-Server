package vista;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MotdRenderUtilTest {
    @Test
    void stripCodes_debeEliminarCodigosYRepararMojibakeBasico() {
        String raw = "§aA §lMinecraft§r Server";

        String limpio = MotdRenderUtil.stripCodes(raw);

        assertThat(limpio).isEqualTo("A Minecraft Server");
    }

    @Test
    void toHtml_debeAceptarMotdMojibakeadoSinMostrarCaracteresBasura() {
        String html = MotdRenderUtil.toHtml("§a Hola");

        assertThat(html).doesNotContain("�");
        assertThat(html).contains("Hola");
    }
}
