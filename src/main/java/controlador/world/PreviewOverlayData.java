package controlador.world;

import controlador.MCARenderer;

import java.util.List;

public record PreviewOverlayData(int originBlockX,
                                 int originBlockZ,
                                 int pixelsPerBlock,
                                 MCARenderer.WorldPoint spawnPoint,
                                 List<PreviewPlayerPoint> playerPoints) {
}
