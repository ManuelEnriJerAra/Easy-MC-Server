package controlador.world;

import controlador.MCARenderer;
import modelo.World;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

public final class WorldPreviewOverlayService {
    private WorldPreviewOverlayService() {
    }

    public static void saveOverlayData(World world, PreviewOverlayData overlayData) {
        if (world == null || overlayData == null) {
            return;
        }

        Properties props = new Properties();
        props.setProperty("originBlockX", Integer.toString(overlayData.originBlockX()));
        props.setProperty("originBlockZ", Integer.toString(overlayData.originBlockZ()));
        props.setProperty("pixelsPerBlock", Integer.toString(overlayData.pixelsPerBlock()));
        if (overlayData.spawnPoint() != null) {
            props.setProperty("spawnX", Integer.toString(overlayData.spawnPoint().x()));
            props.setProperty("spawnZ", Integer.toString(overlayData.spawnPoint().z()));
        }

        int playerIndex = 0;
        if (overlayData.playerPoints() != null) {
            for (PreviewPlayerPoint point : overlayData.playerPoints()) {
                if (point == null || point.username() == null || point.username().isBlank() || point.point() == null) {
                    continue;
                }
                props.setProperty("player." + playerIndex + ".name", point.username());
                props.setProperty("player." + playerIndex + ".x", Integer.toString(point.point().x()));
                props.setProperty("player." + playerIndex + ".z", Integer.toString(point.point().z()));
                playerIndex++;
            }
        }
        props.setProperty("playerCount", Integer.toString(playerIndex));

        Path metadataPath = WorldFilesService.getPreviewOverlayMetadataPath(world);
        if (metadataPath == null) {
            return;
        }

        try (OutputStream out = Files.newOutputStream(metadataPath)) {
            props.store(out, "Preview overlay metadata");
        } catch (IOException ex) {
            System.out.println("No se ha podido guardar la metadata de overlay de la preview: " + metadataPath);
        }
    }

    public static PreviewOverlayData loadOverlayData(World world) {
        if (world == null) {
            return null;
        }

        Path metadataPath = WorldFilesService.getPreviewOverlayMetadataPath(world);
        if (metadataPath == null || !Files.isRegularFile(metadataPath)) {
            return null;
        }

        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(metadataPath)) {
            props.load(in);
            int originBlockX = Integer.parseInt(props.getProperty("originBlockX"));
            int originBlockZ = Integer.parseInt(props.getProperty("originBlockZ"));
            int pixelsPerBlock = Integer.parseInt(props.getProperty("pixelsPerBlock", "1"));
            String spawnX = props.getProperty("spawnX");
            String spawnZ = props.getProperty("spawnZ");
            MCARenderer.WorldPoint spawnPoint = (spawnX != null && spawnZ != null)
                    ? new MCARenderer.WorldPoint(Integer.parseInt(spawnX), Integer.parseInt(spawnZ))
                    : null;

            int playerCount = Integer.parseInt(props.getProperty("playerCount", "0"));
            List<PreviewPlayerPoint> playerPoints = new ArrayList<>();
            for (int i = 0; i < playerCount; i++) {
                String name = props.getProperty("player." + i + ".name");
                String x = props.getProperty("player." + i + ".x");
                String z = props.getProperty("player." + i + ".z");
                if (name == null || x == null || z == null || name.isBlank()) {
                    continue;
                }
                playerPoints.add(new PreviewPlayerPoint(
                        name,
                        new MCARenderer.WorldPoint(Integer.parseInt(x), Integer.parseInt(z))
                ));
            }
            return new PreviewOverlayData(originBlockX, originBlockZ, pixelsPerBlock, spawnPoint, playerPoints);
        } catch (IOException | NumberFormatException ex) {
            return null;
        }
    }
}
