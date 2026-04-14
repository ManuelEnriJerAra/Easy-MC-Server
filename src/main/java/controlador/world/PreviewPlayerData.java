package controlador.world;

import controlador.MCARenderer;

import java.nio.file.attribute.FileTime;

public record PreviewPlayerData(String username, MCARenderer.WorldPoint point, FileTime lastSeen) {
}
