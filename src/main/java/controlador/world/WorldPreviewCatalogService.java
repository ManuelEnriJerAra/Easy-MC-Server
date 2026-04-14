package controlador.world;

import controlador.GestorMundos;
import modelo.Server;
import modelo.World;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public final class WorldPreviewCatalogService {
    private WorldPreviewCatalogService() {
    }

    public static List<WorldPreviewOption> listWorldPreviews(Server server) {
        if (server == null) {
            return List.of();
        }

        try {
            GestorMundos.sincronizarMundosServidor(server);
            World activeWorld = GestorMundos.getMundoActivo(server);
            String activeWorldName = activeWorld != null ? activeWorld.getWorldName() : null;
            List<WorldPreviewOption> previews = new ArrayList<>();
            for (World world : GestorMundos.listarMundos(server)) {
                if (world == null || world.getWorldDir() == null || world.getWorldDir().isBlank()) {
                    continue;
                }
                Path previewPath = WorldFilesService.getPreviewPath(world);
                if (previewPath == null || !Files.isRegularFile(previewPath)) {
                    continue;
                }
                boolean active = activeWorldName != null && activeWorldName.equalsIgnoreCase(world.getWorldName());
                previews.add(new WorldPreviewOption(world.getWorldName(), previewPath, active));
            }
            previews.sort((a, b) -> {
                if (a.activeWorld() != b.activeWorld()) {
                    return a.activeWorld() ? -1 : 1;
                }
                return a.worldName().compareToIgnoreCase(b.worldName());
            });
            return previews;
        } catch (RuntimeException ex) {
            return List.of();
        }
    }
}
