package controlador.world;

import java.nio.file.Path;

public record WorldPreviewOption(String worldName, Path previewPath, boolean activeWorld) {
    @Override
    public String toString() {
        return worldName == null ? "(sin nombre)" : worldName;
    }
}
