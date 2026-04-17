package vista;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MotdRenderUtilTest {
    @Test
    void stripCodes_debeEliminarCodigosYRepararMojibakeBasico() {
        String raw = "Â§aA Â§lMinecraftÂ§r Server";

        String limpio = MotdRenderUtil.stripCodes(raw);

        assertThat(limpio).isEqualTo("A Minecraft Server");
    }

    @Test
    void toHtml_debeAceptarMotdMojibakeadoSinMostrarCaracteresBasura() {
        String html = MotdRenderUtil.toHtml("Â§a Hola");

        assertThat(html).doesNotContain("Â");
        assertThat(html).contains("Hola");
    }
}
