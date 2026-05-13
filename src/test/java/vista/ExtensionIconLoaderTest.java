package vista;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import javax.imageio.ImageIO;
import javax.swing.Icon;
import javax.swing.ImageIcon;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.jar.JarEntry;
import java.util.jar.JarOutputStream;

import static org.assertj.core.api.Assertions.assertThat;

class ExtensionIconLoaderTest {
    @TempDir
    Path tempDir;

    @Test
    void shouldReleaseJarFileAfterLoadingEmbeddedIcon() throws Exception {
        Path jarPath = tempDir.resolve("icon-mod.jar");
        writeJarWithPngIcon(jarPath, "assets/test/icon.png");

        Icon icon = ExtensionIconLoader.loadIconForTesting("jar:" + jarPath.toUri() + "!/assets/test/icon.png", 24);

        assertThat(icon).isInstanceOf(ImageIcon.class);
        Files.delete(jarPath);
        assertThat(jarPath).doesNotExist();
    }

    private static void writeJarWithPngIcon(Path jarPath, String entryName) throws Exception {
        Files.createDirectories(jarPath.getParent());
        try (JarOutputStream jar = new JarOutputStream(Files.newOutputStream(jarPath))) {
            jar.putNextEntry(new JarEntry(entryName));
            jar.write(createPngBytes());
            jar.closeEntry();
        }
    }

    private static byte[] createPngBytes() throws Exception {
        BufferedImage image = new BufferedImage(2, 2, BufferedImage.TYPE_INT_ARGB);
        image.setRGB(0, 0, 0xff3f7fff);
        image.setRGB(1, 0, 0xff3f7fff);
        image.setRGB(0, 1, 0xff3f7fff);
        image.setRGB(1, 1, 0xff3f7fff);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, "png", out);
        return out.toByteArray();
    }
}
