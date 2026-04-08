package controlador;

import net.querz.mca.Chunk;
import net.querz.mca.MCAFile;
import net.querz.mca.MCAUtil;
import net.querz.nbt.tag.CompoundTag;
import net.querz.nbt.tag.ListTag;
import net.querz.nbt.tag.LongArrayTag;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.IntStream;

/**
 * Renderizador cenital de archivos MCA.
 *
 * Esta clase toma uno o varios archivos {@code .mca} de Minecraft y genera una imagen
 * donde cada bloque visible desde arriba se representa con un color aproximado.
 *
 * Ideas clave del diseño:
 * - Un archivo {@code .mca} representa una region de 32x32 chunks.
 * - Cada chunk tiene una planta de 16x16 bloques.
 * - Por tanto, una region cubre 512x512 bloques en el plano X/Z.
 * - Para cada columna vertical (x,z) se busca el bloque superior "visible".
 * - Ese bloque se traduce a un color y se pinta en un {@link BufferedImage}.
 *
 * La clase soporta dos esquemas de chunk:
 * - Moderno: raiz con {@code sections}, {@code block_states}, {@code palette}, {@code data}.
 * - Legacy: raiz con {@code Level -> Sections -> Blocks/Data/Add}.
 *
 * Tambien permite:
 * - renderizar una sola region,
 * - renderizar varias regiones en un solo mosaico,
 * - detectar si una region contiene superficie visible,
 * - guardar el resultado directamente como PNG.
 */
public final class MCARenderer {
    // Numero de chunks por lado dentro de una region MCA.
    public static final int REGION_CHUNK_SIDE = 32;
    // Numero de bloques por lado dentro de un chunk.
    public static final int CHUNK_BLOCK_SIDE = 16;
    // Numero total de bloques por lado en una region completa: 32 * 16 = 512.
    public static final int REGION_BLOCK_SIDE = REGION_CHUNK_SIDE * CHUNK_BLOCK_SIDE;
    // Color de fondo usado cuando no se encuentra superficie visible.
    private static final int DEFAULT_ARGB = 0xFF1E1E1E;
    // Flag de Querz para leer chunks en modo "raw" y acceder al NBT moderno sin forzar el esquema legacy "Level".
    private static final long RAW_DATA_FLAG = 65_536L;
    // Logging de depuracion del renderer. Se puede desactivar con -Deasymc.mca.debug=false.
    private static final boolean DEBUG_LOGGING = Boolean.parseBoolean(System.getProperty("easymc.mca.debug", "true"));
    private static final int DEBUG_MAX_REASON_LINES = 12;
    private static final int DEBUG_MAX_CHUNK_LINES = 20;
    private static final int DEBUG_MAX_SAMPLE_LINES = 10;
    private static final int REGION_RENDER_TILE_CHUNKS = 4;
    // Lista minima de bloques que siempre queremos tratar como invisibles desde una vista cenital.
    private static final Set<String> TRANSPARENT_BLOCKS = Set.of(
            "minecraft:air",
            "minecraft:cave_air",
            "minecraft:void_air",
            "minecraft:barrier",
            "minecraft:structure_void",
            "minecraft:light"
    );

    // Sobrecarga de conveniencia: renderiza una region con las opciones por defecto.
    public BufferedImage renderRegion(Path mcaPath) throws IOException {
        return renderRegion(mcaPath, RenderOptions.defaults());
    }

    /**
     * Renderiza una sola region MCA y devuelve la imagen resultante.
     *
     * Flujo:
     * 1. Valida la ruta y la extension.
     * 2. Normaliza las opciones.
     * 3. Extrae las coordenadas regionales del nombre del archivo (r.x.z.mca).
     * 4. Lee el archivo con Querz en modo raw.
     * 5. Crea un lienzo para la region.
     * 6. Pinta todos los chunks/columnas.
     * 7. Recorta la imagen para eliminar bordes totalmente vacios.
     */
    public BufferedImage renderRegion(Path mcaPath, RenderOptions options) throws IOException {
        return renderRegion(mcaPath, options, true);
    }

    /**
     * Comprueba de forma barata si una region tiene al menos un bloque visible.
     *
     * Esto se usa desde la interfaz para evitar escoger regiones completamente vacias
     * como preview principal. No genera imagen; solo recorre columnas hasta encontrar
     * una superficie no transparente.
     */
    public boolean hasVisibleBlocks(Path mcaPath) throws IOException {
        Path normalizedPath = validateMcaPath(mcaPath);
        RenderOptions options = RenderOptions.defaults();
        RegionCoordinates region = parseRegionCoordinates(normalizedPath);
        MCAFile mcaFile = MCAUtil.read(normalizedPath.toFile(), RAW_DATA_FLAG);
        DebugTrace debugTrace = DebugTrace.forRegionScan(normalizedPath, region, options);
        debugTrace.logStart();

        for(int localChunkZ = 0; localChunkZ < REGION_CHUNK_SIDE; localChunkZ++) {
            for(int localChunkX = 0; localChunkX < REGION_CHUNK_SIDE; localChunkX++) {
                Chunk chunk = mcaFile.getChunk(localChunkX, localChunkZ);
                if(chunk == null) {
                    debugTrace.onNullChunk(localChunkX, localChunkZ);
                    continue;
                }
                if(chunkHasVisibleBlocks(chunk, localChunkX, localChunkZ, region, options, debugTrace)) {
                    debugTrace.logFinish(true);
                    return true;
                }
            }
        }
        debugTrace.logFinish(false);
        return false;
    }

    // Sobrecarga de conveniencia para mosaicos de varias regiones.
    public BufferedImage renderWorld(List<Path> regionPaths) throws IOException {
        return renderWorld(regionPaths, RenderOptions.defaults());
    }

    /**
     * Renderiza varias regiones en un mismo lienzo.
     *
     * Ojo con esto:
     * - aqui no se asume que las regiones sean contiguas,
     * - se calculan min/max de coordenadas regionales,
     * - y cada region se dibuja en la posicion que le corresponde dentro del mosaico.
     *
     * Despues se recorta el resultado para quitar los bordes de fondo sobrantes.
     */
    public BufferedImage renderWorld(List<Path> regionPaths, RenderOptions options) throws IOException {
        return renderWorld(regionPaths, options, null);
    }

    /**
     * Variante de mosaico que permite marcar un punto especial del mundo, como el spawn.
     *
     * La marca se pinta antes del recorte final para que:
     * - quede en la posicion correcta del mundo,
     * - siga visible en el PNG final,
     * - y no haya que recalcular coordenadas en la UI.
     */
    public BufferedImage renderWorld(List<Path> regionPaths, RenderOptions options, WorldPoint markerPoint) throws IOException {
        if(regionPaths == null || regionPaths.isEmpty()) {
            throw new IllegalArgumentException("No hay regiones para renderizar");
        }

        RenderOptions normalizedOptions = options == null ? RenderOptions.defaults() : options.normalized();
        List<Path> normalizedPaths = new ArrayList<>();
        List<RegionCoordinates> coords = new ArrayList<>();

        for(Path regionPath : regionPaths) {
            Path normalizedPath = validateMcaPath(regionPath);
            normalizedPaths.add(normalizedPath);
            coords.add(parseRegionCoordinates(normalizedPath));
        }

        int minRegionX = coords.stream().min(Comparator.comparingInt(RegionCoordinates::x)).orElseThrow().x();
        int maxRegionX = coords.stream().max(Comparator.comparingInt(RegionCoordinates::x)).orElseThrow().x();
        int minRegionZ = coords.stream().min(Comparator.comparingInt(RegionCoordinates::z)).orElseThrow().z();
        int maxRegionZ = coords.stream().max(Comparator.comparingInt(RegionCoordinates::z)).orElseThrow().z();
        debug("renderWorld start regions=%d bounds=[%d..%d,%d..%d] marker=%s yRange=%d..%d pixelsPerBlock=%d",
                normalizedPaths.size(),
                minRegionX,
                maxRegionX,
                minRegionZ,
                maxRegionZ,
                markerPoint == null ? "none" : ("(" + markerPoint.x() + "," + markerPoint.z() + ")"),
                normalizedOptions.minY(),
                normalizedOptions.maxY(),
                normalizedOptions.pixelsPerBlock());

        int pixelsPerRegion = REGION_BLOCK_SIDE * normalizedOptions.pixelsPerBlock();
        int width = (maxRegionX - minRegionX + 1) * pixelsPerRegion;
        int height = (maxRegionZ - minRegionZ + 1) * pixelsPerRegion;
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);

        Graphics2D g2 = image.createGraphics();
        try {
            g2.setColor(new Color(normalizedOptions.defaultArgb(), true));
            g2.fillRect(0, 0, width, height);

            List<RenderedRegion> renderedRegions = IntStream.range(0, normalizedPaths.size())
                    .parallel()
                    .mapToObj(i -> {
                        try {
                            // Muy importante: al componer varias regiones no debemos recortar cada una por separado.
                            // Si lo hiciésemos, cada imagen regional tendría un tamaño distinto y al dibujarla en su
                            // "slot" de 512x512 se desplazaría, provocando cortes rectos o mezclas entre previews.
                            BufferedImage regionImage = renderRegion(normalizedPaths.get(i), normalizedOptions, false);
                            return new RenderedRegion(coords.get(i), regionImage);
                        } catch(IOException ex) {
                            throw new RegionRenderRuntimeException(ex);
                        }
                    })
                    .toList();

            for(RenderedRegion renderedRegion : renderedRegions) {
                RegionCoordinates region = renderedRegion.region();
                int drawX = (region.x() - minRegionX) * pixelsPerRegion;
                int drawY = (region.z() - minRegionZ) * pixelsPerRegion;
                g2.drawImage(renderedRegion.image(), drawX, drawY, null);
            }
        } catch(RegionRenderRuntimeException ex) {
            throw ex.ioException();
        } finally {
            g2.dispose();
        }

        if(markerPoint != null) {
            // El marcador se pinta sobre el mosaico completo, en coordenadas absolutas del mundo.
            // Asi no depende del recorte posterior ni de como se escale la preview en Swing.
            paintMarker(image, markerPoint, minRegionX, minRegionZ, normalizedOptions);
        }

        Rectangle cropArea = resolveCropArea(image, normalizedOptions);
        BufferedImage result = image.getSubimage(cropArea.x, cropArea.y, cropArea.width, cropArea.height);
        debug("renderWorld finish result=%dx%d", result.getWidth(), result.getHeight());
        return result;
    }

    // Variante interna para decidir si la region se devuelve recortada o con su tamaño completo de 512x512 bloques.
    private BufferedImage renderRegion(Path mcaPath, RenderOptions options, boolean cropResult) throws IOException {
        Path normalizedPath = validateMcaPath(mcaPath);
        RenderOptions normalizedOptions = options == null ? RenderOptions.defaults() : options.normalized();
        RegionCoordinates region = parseRegionCoordinates(normalizedPath);
        debug("renderRegion start path=%s region=%s crop=%s yRange=%d..%d pixelsPerBlock=%d ignoreTransparent=%s",
                normalizedPath,
                formatRegion(region),
                cropResult,
                normalizedOptions.minY(),
                normalizedOptions.maxY(),
                normalizedOptions.pixelsPerBlock(),
                normalizedOptions.ignoreTransparentBlocks());
        MCAFile mcaFile = MCAUtil.read(normalizedPath.toFile(), RAW_DATA_FLAG);
        BufferedImage image = createRegionImage(normalizedOptions);
        fillImageBackground(image, normalizedOptions.defaultArgb());
        paintRegion(mcaFile, image, region, normalizedOptions);
        BufferedImage result = cropResult ? cropToVisibleArea(image, normalizedOptions) : image;
        debug("renderRegion finish path=%s result=%dx%d", normalizedPath, result.getWidth(), result.getHeight());
        return result;
    }

    // Exporta una sola region a PNG con opciones por defecto.
    public void renderRegionToPng(Path mcaPath, Path outputFile) throws IOException {
        renderRegionToPng(mcaPath, outputFile, RenderOptions.defaults());
    }

    // Exporta una sola region a PNG.
    public void renderRegionToPng(Path mcaPath, Path outputFile, RenderOptions options) throws IOException {
        Objects.requireNonNull(outputFile, "outputFile no puede ser null");
        BufferedImage image = renderRegion(mcaPath, options);
        writePng(image, outputFile);
    }

    // Exporta un mosaico de varias regiones a PNG con opciones por defecto.
    public void renderWorldToPng(List<Path> regionPaths, Path outputFile) throws IOException {
        renderWorldToPng(regionPaths, outputFile, RenderOptions.defaults());
    }

    // Exporta un mosaico de varias regiones a PNG.
    public void renderWorldToPng(List<Path> regionPaths, Path outputFile, RenderOptions options) throws IOException {
        renderWorldToPng(regionPaths, outputFile, options, null);
    }

    /**
     * Exporta un mosaico y, opcionalmente, dibuja una X roja en un punto concreto del mundo.
     */
    public void renderWorldToPng(List<Path> regionPaths, Path outputFile, RenderOptions options, WorldPoint markerPoint) throws IOException {
        Objects.requireNonNull(outputFile, "outputFile no puede ser null");
        BufferedImage image = renderWorld(regionPaths, options, markerPoint);
        writePng(image, outputFile);
    }

    /**
     * Guarda una imagen en PNG asegurando antes que la carpeta de salida existe.
     */
    private void writePng(BufferedImage image, Path outputFile) throws IOException {
        Path parent = outputFile.toAbsolutePath().getParent();
        if(parent != null) {
            Files.createDirectories(parent);
        }
        ImageIO.write(image, "png", outputFile.toFile());
    }

    /**
     * Recorta automaticamente una imagen al rectangulo minimo que contiene pixeles
     * distintos del color de fondo.
     *
     * Esto es importante porque en muchos mundos la region completa tiene muchisimo
     * espacio vacio alrededor del contenido real, y si no recortamos da la sensacion
     * de que "solo se ve una isla pequeña en el centro".
     *
     * Si toda la imagen es fondo, se devuelve la imagen original.
     */
    private BufferedImage cropToVisibleArea(BufferedImage image, int backgroundArgb, int pixelsPerBlock) {
        Rectangle visibleArea = findVisibleBounds(image, backgroundArgb);
        if(visibleArea == null) {
            return image;
        }
        return image.getSubimage(visibleArea.x, visibleArea.y, visibleArea.width, visibleArea.height);
    }

    private BufferedImage cropToVisibleArea(BufferedImage image, RenderOptions options) {
        Rectangle cropArea = resolveCropArea(image, options);
        return image.getSubimage(cropArea.x, cropArea.y, cropArea.width, cropArea.height);
    }

    private Rectangle resolveCropArea(BufferedImage image, RenderOptions options) {
        if(options != null && options.preferSquareCrop()) {
            Rectangle squareArea = findLargestSquareWithoutEmptyChunks(image, options.defaultArgb(), options.pixelsPerBlock());
            if(squareArea != null) {
                return squareArea;
            }
        }
        Rectangle visibleArea = findVisibleBounds(image, options == null ? DEFAULT_ARGB : options.defaultArgb());
        if(visibleArea != null) {
            return visibleArea;
        }
        return new Rectangle(0, 0, image.getWidth(), image.getHeight());
    }

    /**
     * Busca el rectangulo minimo que contiene informacion real del mapa.
     *
     * Se separa de cropToVisibleArea para poder reutilizar la deteccion si en el futuro
     * necesitamos mas overlays o depuracion visual sobre los bordes del render.
     */
    private Rectangle findVisibleBounds(BufferedImage image, int backgroundArgb) {
        int minX = image.getWidth();
        int minY = image.getHeight();
        int maxX = -1;
        int maxY = -1;

        for(int y = 0; y < image.getHeight(); y++) {
            for(int x = 0; x < image.getWidth(); x++) {
                if(image.getRGB(x, y) == backgroundArgb) {
                    continue;
                }
                if(x < minX) minX = x;
                if(y < minY) minY = y;
                if(x > maxX) maxX = x;
                if(y > maxY) maxY = y;
            }
        }

        if(maxX < minX || maxY < minY) {
            return null;
        }

        return new Rectangle(minX, minY, (maxX - minX) + 1, (maxY - minY) + 1);
    }

    private Rectangle findLargestSquareWithoutEmptyChunks(BufferedImage image, int backgroundArgb, int pixelsPerBlock) {
        int chunkPixelSide = CHUNK_BLOCK_SIDE * Math.max(1, pixelsPerBlock);
        if(image.getWidth() < chunkPixelSide || image.getHeight() < chunkPixelSide) {
            return null;
        }
        if(image.getWidth() % chunkPixelSide != 0 || image.getHeight() % chunkPixelSide != 0) {
            return null;
        }

        int chunkCols = image.getWidth() / chunkPixelSide;
        int chunkRows = image.getHeight() / chunkPixelSide;
        boolean[][] occupied = new boolean[chunkRows][chunkCols];

        for(int chunkY = 0; chunkY < chunkRows; chunkY++) {
            for(int chunkX = 0; chunkX < chunkCols; chunkX++) {
                occupied[chunkY][chunkX] = chunkHasVisiblePixels(image, chunkX, chunkY, chunkPixelSide, backgroundArgb);
            }
        }

        int[][] dp = new int[chunkRows][chunkCols];
        int bestSide = 0;
        int bestEndX = -1;
        int bestEndY = -1;

        for(int y = 0; y < chunkRows; y++) {
            for(int x = 0; x < chunkCols; x++) {
                if(!occupied[y][x]) {
                    dp[y][x] = 0;
                    continue;
                }

                if(x == 0 || y == 0) {
                    dp[y][x] = 1;
                } else {
                    dp[y][x] = 1 + Math.min(dp[y - 1][x], Math.min(dp[y][x - 1], dp[y - 1][x - 1]));
                }

                if(dp[y][x] > bestSide) {
                    bestSide = dp[y][x];
                    bestEndX = x;
                    bestEndY = y;
                }
            }
        }

        if(bestSide <= 0) {
            return null;
        }

        int startChunkX = bestEndX - bestSide + 1;
        int startChunkY = bestEndY - bestSide + 1;
        return new Rectangle(
                startChunkX * chunkPixelSide,
                startChunkY * chunkPixelSide,
                bestSide * chunkPixelSide,
                bestSide * chunkPixelSide
        );
    }

    private boolean chunkHasVisiblePixels(BufferedImage image, int chunkX, int chunkY, int chunkPixelSide, int backgroundArgb) {
        int startX = chunkX * chunkPixelSide;
        int startY = chunkY * chunkPixelSide;
        for(int y = startY; y < startY + chunkPixelSide; y++) {
            for(int x = startX; x < startX + chunkPixelSide; x++) {
                if(image.getRGB(x, y) != backgroundArgb) {
                    return true;
                }
            }
        }
        return false;
    }

    // Crea el lienzo base para una sola region.
    private BufferedImage createRegionImage(RenderOptions options) {
        int size = REGION_BLOCK_SIDE * options.pixelsPerBlock();
        return new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
    }

    private void fillImageBackground(BufferedImage image, int argb) {
        Graphics2D g2 = image.createGraphics();
        try {
            g2.setColor(new Color(argb, true));
            g2.fillRect(0, 0, image.getWidth(), image.getHeight());
        } finally {
            g2.dispose();
        }
    }

    /**
     * Recorre una region completa chunk por chunk.
     *
     * Importante:
     * - {@code localChunkX/localChunkZ} van de 0 a 31 dentro del archivo actual.
     * - A partir de esos indices locales mas las coordenadas regionales, luego calculamos
     *   las coordenadas globales reales del chunk y del bloque.
     */
    private void paintRegion(MCAFile mcaFile, BufferedImage image, RegionCoordinates region, RenderOptions options) {
        String[][] blockNames = new String[REGION_BLOCK_SIDE][REGION_BLOCK_SIDE];
        int[][] heights = new int[REGION_BLOCK_SIDE][REGION_BLOCK_SIDE];
        int tilesPerSide = (int) Math.ceil((double) REGION_CHUNK_SIDE / REGION_RENDER_TILE_CHUNKS);
        IntStream.range(0, tilesPerSide * tilesPerSide)
                .parallel()
                .forEach(tileIndex -> {
                    int tileChunkX = (tileIndex % tilesPerSide) * REGION_RENDER_TILE_CHUNKS;
                    int tileChunkZ = (tileIndex / tilesPerSide) * REGION_RENDER_TILE_CHUNKS;
                    sampleChunkTile(mcaFile, region, options, tileChunkX, tileChunkZ, blockNames, heights);
                });

        paintRegionFromSamples(image, blockNames, heights, options);
    }

    private void sampleChunkTile(
            MCAFile mcaFile,
            RegionCoordinates region,
            RenderOptions options,
            int startChunkX,
            int startChunkZ,
            String[][] blockNames,
            int[][] heights
    ) {
        int endChunkX = Math.min(REGION_CHUNK_SIDE, startChunkX + REGION_RENDER_TILE_CHUNKS);
        int endChunkZ = Math.min(REGION_CHUNK_SIDE, startChunkZ + REGION_RENDER_TILE_CHUNKS);
        for(int localChunkZ = startChunkZ; localChunkZ < endChunkZ; localChunkZ++) {
            for(int localChunkX = startChunkX; localChunkX < endChunkX; localChunkX++) {
                Chunk chunk = mcaFile.getChunk(localChunkX, localChunkZ);
                if(chunk == null) {
                    continue;
                }
                sampleChunk(chunk, localChunkX, localChunkZ, region, options, blockNames, heights);
            }
        }
    }

    /**
     * Pinta un chunk completo.
     *
     * Coordenadas usadas aqui:
     * - localChunkX/localChunkZ: posicion del chunk dentro del archivo region (0..31).
     * - worldChunkX/worldChunkZ: posicion global del chunk en el mundo.
     * - localX/localZ: posicion del bloque dentro del chunk (0..15).
     * - worldBlockX/worldBlockZ: posicion global del bloque en el mundo.
     *
     * Ese paso de local a global es fundamental porque el sombreado y algunos calculos
     * posteriores usan coordenadas globales reales.
     */
    private void sampleChunk(
            Chunk chunk,
            int localChunkX,
            int localChunkZ,
            RegionCoordinates region,
            RenderOptions options,
            String[][] blockNames,
            int[][] heights
    ) {
        int worldChunkX = region.x() * REGION_CHUNK_SIDE + localChunkX;
        int worldChunkZ = region.z() * REGION_CHUNK_SIDE + localChunkZ;
        int regionBlockZ = localChunkZ * CHUNK_BLOCK_SIDE;
        int regionBlockX = localChunkX * CHUNK_BLOCK_SIDE;

        for(int localZ = 0; localZ < CHUNK_BLOCK_SIDE; localZ++) {
            for(int localX = 0; localX < CHUNK_BLOCK_SIDE; localX++) {
                int worldBlockX = worldChunkX * CHUNK_BLOCK_SIDE + localX;
                int worldBlockZ = worldChunkZ * CHUNK_BLOCK_SIDE + localZ;
                TopBlockSample sample = findTopBlock(chunk, localX, localZ, worldBlockX, worldBlockZ, options);
                blockNames[regionBlockZ + localZ][regionBlockX + localX] = sample.blockName();
                heights[regionBlockZ + localZ][regionBlockX + localX] = sample.isEmpty() ? Integer.MIN_VALUE : sample.y();
            }
        }
    }

    private void paintRegionFromSamples(BufferedImage image, String[][] blockNames, int[][] heights, RenderOptions options) {
        for(int blockZ = 0; blockZ < REGION_BLOCK_SIDE; blockZ++) {
            for(int blockX = 0; blockX < REGION_BLOCK_SIDE; blockX++) {
                int argb = resolveBlockColor(blockNames, heights, blockX, blockZ, options);
                paintBlock(image, blockX, blockZ, argb, options.pixelsPerBlock());
            }
        }
    }

    /**
     * Variante simplificada de renderizado: solo pregunta si un chunk tiene alguna columna
     * con superficie visible.
     */
    private boolean chunkHasVisibleBlocks(
            Chunk chunk,
            int localChunkX,
            int localChunkZ,
            RegionCoordinates region,
            RenderOptions options,
            DebugTrace debugTrace
    ) {
        int worldChunkX = region.x() * REGION_CHUNK_SIDE + localChunkX;
        int worldChunkZ = region.z() * REGION_CHUNK_SIDE + localChunkZ;
        ChunkInspection chunkInspection = inspectChunk(chunk);
        debugTrace.onChunkStart(localChunkX, localChunkZ, worldChunkX, worldChunkZ, chunkInspection);

        for(int localZ = 0; localZ < CHUNK_BLOCK_SIDE; localZ++) {
            for(int localX = 0; localX < CHUNK_BLOCK_SIDE; localX++) {
                int worldBlockX = worldChunkX * CHUNK_BLOCK_SIDE + localX;
                int worldBlockZ = worldChunkZ * CHUNK_BLOCK_SIDE + localZ;
                ColumnDiagnostics diagnostics = new ColumnDiagnostics();
                TopBlockSample sample = findTopBlock(chunk, localX, localZ, worldBlockX, worldBlockZ, options, diagnostics);
                debugTrace.onColumnScanned(diagnostics);
                if(!sample.isEmpty()) {
                    debugTrace.onVisibleColumn(localChunkX, localChunkZ, localX, localZ, sample, diagnostics);
                    return true;
                }
            }
        }
        debugTrace.onChunkWithoutVisibleBlocks(localChunkX, localChunkZ, chunkInspection);
        return false;
    }

    /**
     * Busca el bloque visible superior de una columna concreta.
     *
     * Estrategia:
     * - si el chunk tiene tag legacy {@code Level}, se usa el camino antiguo,
     * - si no, se usa el camino moderno con {@code sections}.
     *
     * Recorre Y de arriba hacia abajo porque solo nos interesa la primera interseccion
     * visible desde una vista cenital.
     */
    private TopBlockSample findTopBlock(
            Chunk chunk,
            int localX,
            int localZ,
            int worldBlockX,
            int worldBlockZ,
            RenderOptions options
    ) {
        return findTopBlock(chunk, localX, localZ, worldBlockX, worldBlockZ, options, null);
    }

    private TopBlockSample findTopBlock(
            Chunk chunk,
            int localX,
            int localZ,
            int worldBlockX,
            int worldBlockZ,
            RenderOptions options,
            ColumnDiagnostics diagnostics
    ) {
        CompoundTag root = chunk.getHandle();
        if(root == null) {
            markReason(diagnostics, "root_null");
            return TopBlockSample.EMPTY;
        }

        CompoundTag legacyLevel = root.getCompoundTag("Level");
        if(legacyLevel != null) {
            ListTag<CompoundTag> levelSections = getLegacyLevelSections(legacyLevel);
            if(isModernLevelSections(levelSections)) {
                markScheme(diagnostics, "modern_level");
                int dataVersion = root.getInt("DataVersion");
                debug("modern_level detected dv=%d sections=%d rootKeys=%s levelKeys=%s",
                        dataVersion,
                        levelSections == null ? 0 : levelSections.size(),
                        root.keySet(),
                        legacyLevel.keySet());
                if(diagnostics != null) {
                    diagnostics.dataVersion = dataVersion;
                    diagnostics.sectionCount = levelSections == null ? 0 : levelSections.size();
                }
                return findTopBlockModernSections(levelSections, dataVersion, localX, localZ, worldBlockX, worldBlockZ, options, diagnostics);
            }

            markScheme(diagnostics, "legacy");
            return findTopBlockLegacy(legacyLevel, root.getInt("DataVersion"), localX, localZ, worldBlockX, worldBlockZ, options, diagnostics);
        }

        ListTag<?> sectionsTag = root.getListTag("sections");
        if(sectionsTag == null || sectionsTag.size() == 0) {
            markScheme(diagnostics, "modern");
            markReason(diagnostics, "modern_sections_missing");
            return TopBlockSample.EMPTY;
        }

        markScheme(diagnostics, "modern");
        if(diagnostics != null) {
            diagnostics.sectionCount = sectionsTag.size();
        }
        ListTag<CompoundTag> sections = sectionsTag.asCompoundTagList();
        int dataVersion = root.getInt("DataVersion");
        if(diagnostics != null) {
            diagnostics.dataVersion = dataVersion;
        }
        return findTopBlockModernSections(sections, dataVersion, localX, localZ, worldBlockX, worldBlockZ, options, diagnostics);
    }

    private TopBlockSample findTopBlockModernSections(
            ListTag<CompoundTag> sections,
            int dataVersion,
            int localX,
            int localZ,
            int worldBlockX,
            int worldBlockZ,
            RenderOptions options,
            ColumnDiagnostics diagnostics
    ) {
        if(sections == null || sections.size() == 0) {
            markReason(diagnostics, "modern_sections_missing");
            return TopBlockSample.EMPTY;
        }

        for(int y = options.maxY(); y >= options.minY(); y--) {
            CompoundTag section = findSectionForY(sections, y);
            if(section == null) {
                continue;
            }
            String blockName = getModernBlockNameAt(section, localX, y & 15, localZ, dataVersion);
            if(diagnostics != null && diagnostics.firstTransparentBlock == null && blockName != null) {
                if("modern_level".equals(diagnostics.scheme)) {
                    debug("modern_level sample worldBlock=(%d,%d) y=%d block=%s sectionKeys=%s",
                            worldBlockX,
                            worldBlockZ,
                            y,
                            blockName,
                            section.keySet());
                } else if(diagnostics.scheme != null && diagnostics.scheme.startsWith("modern(")) {
                    debug("modern sample worldBlock=(%d,%d) y=%d block=%s sectionKeys=%s",
                            worldBlockX,
                            worldBlockZ,
                            y,
                            blockName,
                            section.keySet());
                }
            }
            if(options.ignoreTransparentBlocks() && isTransparentBlock(blockName)) {
                if(diagnostics != null && blockName != null && diagnostics.firstTransparentBlock == null) {
                    diagnostics.firstTransparentBlock = blockName;
                    diagnostics.firstTransparentY = y;
                }
                continue;
            }
            return new TopBlockSample(blockName, y, worldBlockX, worldBlockZ);
        }

        if(diagnostics != null && diagnostics.firstTransparentBlock != null) {
            markReason(diagnostics, "only_transparent_blocks");
        } else {
            markReason(diagnostics, "no_visible_block_in_range");
        }
        return TopBlockSample.EMPTY;
    }

    private ListTag<CompoundTag> getLegacyLevelSections(CompoundTag legacyLevel) {
        if(legacyLevel == null) {
            return null;
        }
        ListTag<?> sectionsTag = legacyLevel.getListTag("Sections");
        if(sectionsTag == null || sectionsTag.size() == 0) {
            return null;
        }
        return sectionsTag.asCompoundTagList();
    }

    private boolean isModernLevelSections(ListTag<CompoundTag> sections) {
        if(sections == null || sections.size() == 0) {
            return false;
        }
        for(CompoundTag section : sections) {
            if(section == null) {
                continue;
            }
            if(section.getCompoundTag("block_states") != null) {
                return true;
            }
            if(section.getLongArrayTag("BlockStates") != null || section.getListTag("Palette") != null) {
                return true;
            }
            byte[] legacyBlocks = section.getByteArray("Blocks");
            if(legacyBlocks != null && legacyBlocks.length == 4096) {
                return false;
            }
        }
        return false;
    }

    /**
     * Camino legacy para versiones antiguas.
     *
     * Lee {@code Level -> Sections -> Blocks/Data/Add}. Aqui no existe palette moderna,
     * asi que hay que recomponer el ID numerico del bloque y su metadata.
     */
    private TopBlockSample findTopBlockLegacy(
            CompoundTag level,
            int dataVersion,
            int localX,
            int localZ,
            int worldBlockX,
            int worldBlockZ,
            RenderOptions options,
            ColumnDiagnostics diagnostics
    ) {
        ListTag<?> sectionsTag = level.getListTag("Sections");
        if(sectionsTag == null || sectionsTag.size() == 0) {
            markReason(diagnostics, "legacy_sections_missing");
            return TopBlockSample.EMPTY;
        }

        if(diagnostics != null) {
            diagnostics.sectionCount = sectionsTag.size();
        }
        ListTag<CompoundTag> sections = sectionsTag.asCompoundTagList();
        if(isModernLevelSections(sections)) {
            debug("legacy fallback switched to modern_level dv=%d sections=%d levelKeys=%s",
                    dataVersion,
                    sections.size(),
                    level.keySet());
            if(diagnostics != null) {
                diagnostics.dataVersion = dataVersion;
                diagnostics.scheme = "modern_level";
            }
            return findTopBlockModernSections(sections, dataVersion, localX, localZ, worldBlockX, worldBlockZ, options, diagnostics);
        }
        for(int y = options.maxY(); y >= options.minY(); y--) {
            CompoundTag section = findSectionForY(sections, y);
            if(section == null) {
                continue;
            }
            String blockName = getLegacyBlockNameAt(section, localX, y & 15, localZ);
            if(options.ignoreTransparentBlocks() && isTransparentBlock(blockName)) {
                if(diagnostics != null && blockName != null && diagnostics.firstTransparentBlock == null) {
                    diagnostics.firstTransparentBlock = blockName;
                    diagnostics.firstTransparentY = y;
                }
                continue;
            }
            return new TopBlockSample(blockName, y, worldBlockX, worldBlockZ);
        }

        if(diagnostics != null && diagnostics.firstTransparentBlock != null) {
            markReason(diagnostics, "only_transparent_blocks");
        } else {
            markReason(diagnostics, "no_visible_block_in_range");
        }
        return TopBlockSample.EMPTY;
    }

    /**
     * Devuelve la seccion vertical que corresponde a una Y absoluta concreta.
     *
     * Cada seccion mide 16 bloques de alto.
     * Por ejemplo:
     * - y 0..15   -> seccion 0
     * - y 16..31  -> seccion 1
     * - y -16..-1 -> seccion -1
     */
    private CompoundTag findSectionForY(ListTag<CompoundTag> sections, int y) {
        int targetSectionY = Math.floorDiv(y, 16);
        for(CompoundTag section : sections) {
            if(section != null && section.getByte("Y") == targetSectionY) {
                return section;
            }
        }
        return null;
    }

    /**
     * Lee el nombre del bloque en una seccion moderna.
     *
     * Formato moderno:
     * - {@code block_states.palette}: lista de estados posibles
     * - {@code block_states.data}: indices empaquetados en longs
     *
     * Si la palette solo tiene un elemento, toda la seccion es ese bloque y no hace
     * falta desempaquetar nada.
     */
    private String getModernBlockNameAt(CompoundTag section, int localX, int localY, int localZ, int dataVersion) {
        CompoundTag blockStates = section.getCompoundTag("block_states");
        if(blockStates != null) {
            return getModernBlockNameFromPackedStates(
                    blockStates.getListTag("palette"),
                    blockStates.getLongArrayTag("data"),
                    localX,
                    localY,
                    localZ,
                    dataVersion
            );
        }

        // 1.13-1.16 transitional chunks can still live under Level->Sections using
        // Palette/BlockStates in the old capitalized form.
        return getModernBlockNameFromPackedStates(
                section.getListTag("Palette"),
                section.getLongArrayTag("BlockStates"),
                localX,
                localY,
                localZ,
                dataVersion
        );
    }

    private String getModernBlockNameFromPackedStates(
            ListTag<?> paletteTag,
            LongArrayTag dataTag,
            int localX,
            int localY,
            int localZ,
            int dataVersion
    ) {
        if(paletteTag == null || paletteTag.size() == 0) {
            return null;
        }

        ListTag<CompoundTag> palette = paletteTag.asCompoundTagList();
        if(palette.size() == 1) {
            return palette.get(0).getString("Name");
        }

        if(dataTag == null) {
            return palette.get(0).getString("Name");
        }

        long[] data = dataTag.getValue();
        int bitsPerBlock = Math.max(4, ceilLog2(palette.size()));
        int paletteIndex = readPackedIndex(data, blockIndex(localX, localY, localZ), bitsPerBlock, dataVersion);
        if(paletteIndex < 0 || paletteIndex >= palette.size()) {
            return null;
        }
        return palette.get(paletteIndex).getString("Name");
    }

    /**
     * Lee el nombre del bloque en una seccion legacy.
     *
     * En este formato:
     * - {@code Blocks} contiene los 8 bits bajos del ID,
     * - {@code Add} aporta bits altos adicionales si existen,
     * - {@code Data} contiene metadata en nibbles (4 bits).
     */
    private String getLegacyBlockNameAt(CompoundTag section, int localX, int localY, int localZ) {
        byte[] blocks = section.getByteArray("Blocks");
        if(blocks == null || blocks.length != 4096) {
            return null;
        }

        byte[] data = section.getByteArray("Data");
        byte[] add = section.getByteArray("Add");
        int index = blockIndex(localX, localY, localZ);
        int blockId = blocks[index] & 0xFF;
        if(add != null && add.length > 0) {
            blockId |= nibbleAt(add, index) << 8;
        }
        int blockData = data != null && data.length > 0 ? nibbleAt(data, index) : 0;
        return legacyBlockName(blockId, blockData);
    }

    /**
     * Convierte coordenadas locales de bloque dentro de una seccion 16x16x16 en un indice lineal.
     *
     * Orden usado:
     * - Y ocupa el bloque de bits mas alto
     * - Z el intermedio
     * - X el mas bajo
     *
     * Esto coincide con la distribucion esperada por los datos de bloques legacy y por
     * el empaquetado moderno que estamos leyendo.
     */
    private int blockIndex(int localX, int localY, int localZ) {
        return (localY << 8) | (localZ << 4) | localX;
    }

    /**
     * Extrae un indice de palette desde un array de longs empaquetados.
     *
     * El formato moderno guarda bloques consecutivos usando un numero variable de bits
     * por entrada. Este metodo:
     * - calcula el bit inicial del bloque,
     * - localiza el long donde empieza,
     * - lee los bits necesarios,
     * - y si el valor cruza el limite de un long, concatena con el siguiente.
     */
    private int readPackedIndex(long[] data, int blockIndex, int bitsPerBlock, int dataVersion) {
        if(data == null || data.length == 0) {
            return 0;
        }

        long mask = (1L << bitsPerBlock) - 1L;

        // Minecraft cambió el layout interno de BlockStates en 1.16.2 aprox. (DataVersion 2527).
        // Antes los valores iban "compactados" de forma continua y podian partirse entre dos longs.
        // Ahora cada long reserva huecos fijos (padding) y un bloque nunca cruza de un long a otro.
        //
        // La version anterior de este renderer siempre usaba el camino compacto.
        // Eso hacia que los indices de palette de mundos modernos salieran corridos:
        // - cuadrados pequenos en vez del mapa completo,
        // - troncos/hojas mezclados,
        // - artefactos geometricos muy raros.
        if(dataVersion >= 2527) {
            int valuesPerLong = 64 / bitsPerBlock;
            if(valuesPerLong <= 0) {
                return 0;
            }

            int longIndex = blockIndex / valuesPerLong;
            if(longIndex >= data.length) {
                return 0;
            }

            int startOffset = (blockIndex % valuesPerLong) * bitsPerBlock;
            return (int) ((data[longIndex] >>> startOffset) & mask);
        }

        long bitIndex = (long) blockIndex * bitsPerBlock;
        int startLong = (int) (bitIndex >>> 6);
        int startOffset = (int) (bitIndex & 63L);

        if(startLong >= data.length) {
            return 0;
        }

        long value = data[startLong] >>> startOffset;
        int endOffset = startOffset + bitsPerBlock;
        if(endOffset > 64 && startLong + 1 < data.length) {
            value |= data[startLong + 1] << (64 - startOffset);
        }
        return (int) (value & mask);
    }

    // Entero auxiliar: ceil(log2(n)). Sirve para saber cuantos bits hacen falta para indexar la palette.
    private int ceilLog2(int value) {
        return value <= 1 ? 0 : 32 - Integer.numberOfLeadingZeros(value - 1);
    }

    /**
     * Lee un nibble (4 bits) desde un array empaquetado de bytes.
     *
     * Se usa para datos legacy como {@code Data} y {@code Add}.
     */
    private int nibbleAt(byte[] packed, int index) {
        int value = packed[index >> 1] & 0xFF;
        return (index & 1) == 0 ? (value & 0x0F) : ((value >>> 4) & 0x0F);
    }

    /**
     * Traduccion aproximada de IDs legacy a nombres de bloque modernos.
     *
     * No pretende ser una tabla historica completa; solo suficiente para una preview visual.
     * Si quieres mas fidelidad en 1.7/1.8/1.12, este es uno de los mejores puntos para ampliar.
     */
    private String legacyBlockName(int blockId, int blockData) {
        return switch (blockId) {
            case 0 -> "minecraft:air";
            case 1, 4, 48 -> "minecraft:stone";
            case 2 -> "minecraft:grass_block";
            case 3 -> "minecraft:dirt";
            case 7 -> "minecraft:bedrock";
            case 8, 9 -> "minecraft:water";
            case 10, 11 -> "minecraft:lava";
            case 12, 24 -> "minecraft:sand";
            case 13 -> "minecraft:gravel";
            case 17, 162 -> "minecraft:oak_log";
            case 18, 161 -> "minecraft:oak_leaves";
            case 79 -> "minecraft:ice";
            case 80 -> "minecraft:snow_block";
            case 82 -> "minecraft:clay";
            case 87 -> "minecraft:netherrack";
            case 88 -> "minecraft:soul_sand";
            case 89 -> "minecraft:glowstone";
            case 110 -> "minecraft:mycelium";
            case 112 -> "minecraft:nether_bricks";
            case 121 -> "minecraft:end_stone";
            case 129 -> "minecraft:emerald_ore";
            case 159, 172 -> "minecraft:terracotta";
            case 173 -> "minecraft:coal_block";
            case 35 -> legacyWoolName(blockData);
            default -> "minecraft:stone";
        };
    }

    // Subtabla especifica para lana legacy coloreada.
    private String legacyWoolName(int blockData) {
        return switch (blockData & 15) {
            case 0 -> "minecraft:white_wool";
            case 1 -> "minecraft:orange_wool";
            case 2 -> "minecraft:magenta_wool";
            case 3 -> "minecraft:light_blue_wool";
            case 4 -> "minecraft:yellow_wool";
            case 5 -> "minecraft:lime_wool";
            case 6 -> "minecraft:pink_wool";
            case 7 -> "minecraft:gray_wool";
            case 8 -> "minecraft:light_gray_wool";
            case 9 -> "minecraft:cyan_wool";
            case 10 -> "minecraft:purple_wool";
            case 11 -> "minecraft:blue_wool";
            case 12 -> "minecraft:brown_wool";
            case 13 -> "minecraft:green_wool";
            case 14 -> "minecraft:red_wool";
            case 15 -> "minecraft:black_wool";
            default -> "minecraft:white_wool";
        };
    }

    /**
     * Convierte una muestra de bloque superior en un color ARGB.
     *
     * Si no existe bloque visible, devuelve el color de fondo.
     * Si el sombreado por altura esta activo, se aplica una pequena variacion para
     * que la imagen no quede completamente plana.
     */
    private int resolveBlockColor(String[][] blockNames, int[][] heights, int blockX, int blockZ, RenderOptions options) {
        String blockName = blockNames[blockZ][blockX];
        Color base = resolveBaseColor(blockName);
        if(base == null) {
            return options.defaultArgb();
        }
        if(!options.shadeByHeight()) {
            return base.getRGB();
        }

        int center = heights[blockZ][blockX];
        if(center == Integer.MIN_VALUE) {
            return base.getRGB();
        }

        int west = sampleHeight(heights, blockX - 1, blockZ, center);
        int east = sampleHeight(heights, blockX + 1, blockZ, center);
        int north = sampleHeight(heights, blockX, blockZ - 1, center);
        int south = sampleHeight(heights, blockX, blockZ + 1, center);

        double dx = (east - west) * 0.5d;
        double dz = (south - north) * 0.5d;
        double exaggeration = blockName != null && blockName.contains("water") ? 0.18d : 0.38d;

        double nx = -dx * exaggeration;
        double ny = -dz * exaggeration;
        double nz = 1.0d;
        double normalLength = Math.sqrt((nx * nx) + (ny * ny) + (nz * nz));
        if(normalLength <= 0d) {
            return base.getRGB();
        }
        nx /= normalLength;
        ny /= normalLength;
        nz /= normalLength;

        double lightX = -0.58d;
        double lightY = -0.58d;
        double lightZ = 0.57d;
        double lightLength = Math.sqrt((lightX * lightX) + (lightY * lightY) + (lightZ * lightZ));
        lightX /= lightLength;
        lightY /= lightLength;
        lightZ /= lightLength;

        double dot = (nx * lightX) + (ny * lightY) + (nz * lightZ);
        double ambient = blockName != null && blockName.contains("water") ? 0.80d : 0.62d;
        double contrast = blockName != null && blockName.contains("water") ? 0.20d : 0.38d;
        double intensity = Math.max(0d, Math.min(1d, ambient + (Math.max(-0.9d, Math.min(0.9d, dot)) * contrast)));
        int shade = (int) Math.round((intensity - 0.72d) * 62d);
        return applyShade(base, shade).getRGB();
    }

    private int sampleHeight(int[][] heights, int x, int z, int fallback) {
        if(heights == null || z < 0 || z >= heights.length || x < 0 || x >= heights[z].length) {
            return fallback;
        }
        int value = heights[z][x];
        return value == Integer.MIN_VALUE ? fallback : value;
    }

    /**
     * Paleta base aproximada por familia de bloques.
     *
     * Aqui no se intenta ser perfecto; se intenta que la preview se entienda visualmente.
     * Si notas colores pobres o ambiguos, este metodo es el lugar natural para ajustarlos.
     */
    private Color resolveBaseColor(String blockName) {
        if(blockName == null || blockName.isBlank()) {
            return null;
        }

        String name = blockName.toLowerCase(Locale.ROOT);

        if(name.contains("water")) return new Color(58, 125, 219);
        if(name.contains("lava")) return new Color(255, 108, 43);
        if(name.contains("snow")) return new Color(242, 246, 250);
        if(name.contains("ice")) return new Color(150, 202, 255);
        if(name.contains("sand") || name.contains("sandstone")) return new Color(218, 205, 135);
        if(name.contains("clay") || name.contains("terracotta")) return new Color(169, 116, 92);
        if(name.contains("stone") || name.contains("cobblestone") || name.contains("deepslate") || name.contains("ore")) return new Color(131, 131, 131);
        if(name.contains("dirt") || name.contains("mud") || name.contains("gravel") || name.contains("path")) return new Color(132, 96, 67);
        if(name.contains("grass") || name.contains("moss")) return new Color(100, 164, 76);
        if(name.contains("leaves") || name.contains("azalea")) return new Color(62, 128, 57);
        if(name.contains("log") || name.contains("wood") || name.contains("planks")) return new Color(138, 110, 73);
        if(name.contains("mushroom")) return new Color(178, 50, 63);
        if(name.contains("netherrack") || name.contains("nether") || name.contains("crimson")) return new Color(116, 46, 49);
        if(name.contains("warped")) return new Color(43, 117, 114);
        if(name.contains("end_stone")) return new Color(216, 224, 162);
        if(name.contains("blackstone") || name.contains("basalt") || name.contains("obsidian")) return new Color(58, 56, 68);
        if(name.contains("glass")) return new Color(200, 220, 235);
        if(name.contains("wool") || name.contains("concrete") || name.contains("concrete_powder")) return colorForDyedBlock(name);
        return new Color(95, 95, 95);
    }

    // Paleta de bloques teñidos.
    private Color colorForDyedBlock(String name) {
        if(name.contains("white")) return new Color(240, 240, 240);
        if(name.contains("orange")) return new Color(235, 124, 52);
        if(name.contains("magenta")) return new Color(198, 79, 189);
        if(name.contains("light_blue")) return new Color(104, 161, 255);
        if(name.contains("yellow")) return new Color(249, 198, 39);
        if(name.contains("lime")) return new Color(127, 204, 25);
        if(name.contains("pink")) return new Color(242, 140, 172);
        if(name.contains("light_gray")) return new Color(157, 157, 151);
        if(name.contains("gray")) return new Color(74, 79, 82);
        if(name.contains("cyan")) return new Color(21, 137, 145);
        if(name.contains("purple")) return new Color(121, 42, 172);
        if(name.contains("blue")) return new Color(53, 57, 157);
        if(name.contains("brown")) return new Color(114, 71, 40);
        if(name.contains("green")) return new Color(84, 109, 27);
        if(name.contains("red")) return new Color(161, 39, 34);
        if(name.contains("black")) return new Color(20, 21, 25);
        return new Color(160, 160, 160);
    }

    /**
     * Decide si un bloque debe ignorarse como "no visible" desde arriba.
     *
     * Aqui conviene ser prudente:
     * - si marcas demasiado como transparente, apareceran huecos,
     * - si marcas demasiado poco, sobresaldran antorchas, flores, railes, etc.
     *
     * Importante: las hojas NO se consideran transparentes aqui.
     * Aunque visualmente puedan generar mucho verde, desde una vista cenital normalmente
     * deben tapar al tronco. Si las marcamos como transparentes el render baja hasta la
     * madera y aparecen muchos puntos marrones donde deberia verse la copa del arbol.
     */
    private boolean isTransparentBlock(String blockName) {
        if(blockName == null || blockName.isBlank()) {
            return true;
        }
        String name = blockName.toLowerCase(Locale.ROOT);
        return TRANSPARENT_BLOCKS.contains(name)
                || name.endsWith("_air")
                || name.contains("glass")
                || name.contains("pane")
                || name.contains("rail")
                || name.contains("torch")
                || name.contains("flower")
                || name.contains("tall_grass")
                || name.contains("fern")
                || name.contains("vine")
                || name.contains("carpet")
                || name.contains("button")
                || name.contains("pressure_plate")
                || name.contains("sign")
                || name.contains("sapling")
                || name.contains("crop")
                || name.contains("stem")
                || name.contains("tripwire")
                || name.contains("ladder")
                || name.contains("redstone_wire");
    }

    // Aplica una variacion simple de brillo manteniendo el alfa original.
    private Color applyShade(Color color, int delta) {
        int r = clampColor(color.getRed() + delta);
        int g = clampColor(color.getGreen() + delta);
        int b = clampColor(color.getBlue() + delta);
        return new Color(r, g, b, color.getAlpha());
    }

    // Limita un canal de color al rango valido 0..255.
    private int clampColor(int value) {
        return Math.max(0, Math.min(255, value));
    }

    /**
     * Rellena el area de un chunk completo con el color de fondo.
     *
     * Se usa cuando en esa posicion no existe chunk en el MCA.
     */
    private void fillChunkBackground(BufferedImage image, int localChunkX, int localChunkZ, int argb, int pixelsPerBlock) {
        int chunkPixelSide = CHUNK_BLOCK_SIDE * pixelsPerBlock;
        int baseX = localChunkX * chunkPixelSide;
        int baseY = localChunkZ * chunkPixelSide;

        for(int z = 0; z < chunkPixelSide; z++) {
            for(int x = 0; x < chunkPixelSide; x++) {
                image.setRGB(baseX + x, baseY + z, argb);
            }
        }
    }

    /**
     * Pinta un bloque en la imagen.
     *
     * Si {@code pixelsPerBlock > 1}, cada bloque se dibuja como un cuadrado mas grande
     * para obtener una preview ampliada.
     */
    private void paintBlock(BufferedImage image, int blockX, int blockZ, int argb, int pixelsPerBlock) {
        int pixelX = blockX * pixelsPerBlock;
        int pixelY = blockZ * pixelsPerBlock;
        for(int dz = 0; dz < pixelsPerBlock; dz++) {
            for(int dx = 0; dx < pixelsPerBlock; dx++) {
                image.setRGB(pixelX + dx, pixelY + dz, argb);
            }
        }
    }

    /**
     * Dibuja una X roja pequena sobre un punto del mundo.
     *
     * Coordenadas:
     * - markerPoint.x/z estan en bloques globales del mundo,
     * - minRegionX/minRegionZ indican donde empieza el mosaico generado,
     * - el resultado final se convierte a pixeles del lienzo.
     */
    private void paintMarker(
            BufferedImage image,
            WorldPoint markerPoint,
            int minRegionX,
            int minRegionZ,
            RenderOptions options
    ) {
        int worldOriginBlockX = minRegionX * REGION_BLOCK_SIDE;
        int worldOriginBlockZ = minRegionZ * REGION_BLOCK_SIDE;
        int markerPixelX = (markerPoint.x() - worldOriginBlockX) * options.pixelsPerBlock();
        int markerPixelY = (markerPoint.z() - worldOriginBlockZ) * options.pixelsPerBlock();

        if(markerPixelX < 0 || markerPixelY < 0 || markerPixelX >= image.getWidth() || markerPixelY >= image.getHeight()) {
            return;
        }

        // La marca del spawn se dibuja con una estetica mas "mapa del tesoro":
        // - base roja oscura para darle cuerpo,
        // - rojo principal por encima.
        //
        // Se evita cualquier color secundario en el centro para que toda la X sea roja.
        int arm = Math.max(7, options.pixelsPerBlock() * 6);
        int shadowThickness = Math.max(3, options.pixelsPerBlock() * 3);
        int mainThickness = Math.max(2, options.pixelsPerBlock() * 2);
        int shadow = new Color(92, 17, 17).getRGB();
        int red = new Color(196, 33, 33).getRGB();

        drawMarkerLine(image, markerPixelX, markerPixelY, arm, shadowThickness, shadow, true);
        drawMarkerLine(image, markerPixelX, markerPixelY, arm, shadowThickness, shadow, false);
        drawMarkerLine(image, markerPixelX, markerPixelY, arm, mainThickness, red, true);
        drawMarkerLine(image, markerPixelX, markerPixelY, arm, mainThickness, red, false);
    }

    // Dibuja una de las dos diagonales de la X.
    private void drawMarkerLine(BufferedImage image, int centerX, int centerY, int arm, int thickness, int argb, boolean descending) {
        for(int offset = -arm; offset <= arm; offset++) {
            int x = centerX + offset;
            int y = descending ? centerY + offset : centerY - offset;
            paintMarkerPixel(image, x, y, thickness, argb);
        }
    }

    // Pinta un punto grueso para que la X aguante mejor cuando la preview se reduzca.
    private void paintMarkerPixel(BufferedImage image, int centerX, int centerY, int thickness, int argb) {
        int radius = Math.max(0, thickness / 2);
        for(int dy = -radius; dy <= radius; dy++) {
            for(int dx = -radius; dx <= radius; dx++) {
                int x = centerX + dx;
                int y = centerY + dy;
                if(x < 0 || y < 0 || x >= image.getWidth() || y >= image.getHeight()) {
                    continue;
                }
                image.setRGB(x, y, argb);
            }
        }
    }

    /**
     * Valida una ruta de entrada.
     *
     * Requisitos:
     * - no null
     * - archivo real
     * - extension .mca
     */
    private Path validateMcaPath(Path mcaPath) {
        Objects.requireNonNull(mcaPath, "mcaPath no puede ser null");
        Path normalizedPath = mcaPath.toAbsolutePath().normalize();
        if(!Files.isRegularFile(normalizedPath)) {
            throw new IllegalArgumentException("No existe el archivo .mca: " + normalizedPath);
        }
        // Algunos mundos dejan ficheros region de 0 bytes como huecos o restos.
        // Querz no los puede deserializar como una region valida, asi que los filtramos
        // aqui con un mensaje explicito en lugar de dejar que mas tarde falle con errores
        // poco claros o incluso con mensaje null.
        try {
            if(Files.size(normalizedPath) <= 0L) {
                throw new IllegalArgumentException("El archivo .mca esta vacio: " + normalizedPath.getFileName());
            }
        } catch (IOException ex) {
            throw new IllegalArgumentException("No se ha podido comprobar el tamano del .mca: " + normalizedPath.getFileName(), ex);
        }
        if(!normalizedPath.getFileName().toString().toLowerCase(Locale.ROOT).endsWith(".mca")) {
            throw new IllegalArgumentException("El archivo no tiene extension .mca: " + normalizedPath);
        }
        return normalizedPath;
    }

    /**
     * Extrae las coordenadas regionales del nombre estandar de Minecraft:
     * {@code r.<regionX>.<regionZ>.mca}
     */
    private RegionCoordinates parseRegionCoordinates(Path mcaPath) {
        String[] parts = mcaPath.getFileName().toString().split("\\.");
        if(parts.length != 4 || !"r".equalsIgnoreCase(parts[0]) || !"mca".equalsIgnoreCase(parts[3])) {
            throw new IllegalArgumentException("Nombre de region invalido: " + mcaPath.getFileName());
        }
        return new RegionCoordinates(Integer.parseInt(parts[1]), Integer.parseInt(parts[2]));
    }

    /**
     * Opciones de renderizado.
     *
     * Se ha hecho inmutable a proposito:
     * - cada metodo withX devuelve una nueva instancia,
     * - asi evitamos estados mutables raros entre llamadas.
     */
    public static final class RenderOptions {
        private final int pixelsPerBlock;
        private final int minY;
        private final int maxY;
        private final boolean shadeByHeight;
        private final boolean ignoreTransparentBlocks;
        private final boolean preferSquareCrop;
        private final int defaultArgb;

        private RenderOptions(int pixelsPerBlock, int minY, int maxY, boolean shadeByHeight, boolean ignoreTransparentBlocks, boolean preferSquareCrop, int defaultArgb) {
            this.pixelsPerBlock = pixelsPerBlock;
            this.minY = minY;
            this.maxY = maxY;
            this.shadeByHeight = shadeByHeight;
            this.ignoreTransparentBlocks = ignoreTransparentBlocks;
            this.preferSquareCrop = preferSquareCrop;
            this.defaultArgb = defaultArgb;
        }

        // Valores por defecto pensados para mundos modernos, pero validos tambien para muchos casos legacy.
        public static RenderOptions defaults() {
            return new RenderOptions(1, -64, 319, true, true, true, DEFAULT_ARGB);
        }

        // Cambia la escala visual: 1 bloque = N pixeles.
        public RenderOptions withPixelsPerBlock(int pixelsPerBlock) {
            return new RenderOptions(pixelsPerBlock, minY, maxY, shadeByHeight, ignoreTransparentBlocks, preferSquareCrop, defaultArgb);
        }

        // Cambia el rango vertical explorado al buscar la superficie.
        public RenderOptions withYRange(int minY, int maxY) {
            return new RenderOptions(pixelsPerBlock, minY, maxY, shadeByHeight, ignoreTransparentBlocks, preferSquareCrop, defaultArgb);
        }

        // Activa o desactiva el sombreado por altura.
        public RenderOptions withShadeByHeight(boolean shadeByHeight) {
            return new RenderOptions(pixelsPerBlock, minY, maxY, shadeByHeight, ignoreTransparentBlocks, preferSquareCrop, defaultArgb);
        }

        // Permite decidir si se ignoran bloques de detalle/transparencia.
        public RenderOptions withIgnoreTransparentBlocks(boolean ignoreTransparentBlocks) {
            return new RenderOptions(pixelsPerBlock, minY, maxY, shadeByHeight, ignoreTransparentBlocks, preferSquareCrop, defaultArgb);
        }

        public RenderOptions withPreferSquareCrop(boolean preferSquareCrop) {
            return new RenderOptions(pixelsPerBlock, minY, maxY, shadeByHeight, ignoreTransparentBlocks, preferSquareCrop, defaultArgb);
        }

        // Permite cambiar el color de fondo.
        public RenderOptions withDefaultArgb(int defaultArgb) {
            return new RenderOptions(pixelsPerBlock, minY, maxY, shadeByHeight, ignoreTransparentBlocks, preferSquareCrop, defaultArgb);
        }

        // Asegura que las opciones tengan valores coherentes antes de usarse internamente.
        private RenderOptions normalized() {
            int normalizedPixels = Math.max(1, pixelsPerBlock);
            int normalizedMinY = minY;
            int normalizedMaxY = Math.max(normalizedMinY, maxY);
            return new RenderOptions(normalizedPixels, normalizedMinY, normalizedMaxY, shadeByHeight, ignoreTransparentBlocks, preferSquareCrop, defaultArgb);
        }

        // Getters simples para leer las opciones desde el renderer.
        public int pixelsPerBlock() { return pixelsPerBlock; }
        public int minY() { return minY; }
        public int maxY() { return maxY; }
        public boolean shadeByHeight() { return shadeByHeight; }
        public boolean ignoreTransparentBlocks() { return ignoreTransparentBlocks; }
        public boolean preferSquareCrop() { return preferSquareCrop; }
        public int defaultArgb() { return defaultArgb; }
    }

    // Coordenadas de una region dentro del mundo. Ejemplo: r.1.-2.mca -> x=1, z=-2.
    private record RegionCoordinates(int x, int z) {}

    // Punto absoluto del mundo en el plano X/Z.
    public record WorldPoint(int x, int z) {}

    /**
     * Resultado de la busqueda del bloque superior de una columna.
     *
     * Guardamos:
     * - el nombre del bloque,
     * - la altura Y,
     * - las coordenadas globales X/Z,
     *
     * porque mas tarde el renderer puede necesitar contexto adicional al colorear
     * o depurar una columna concreta del mundo.
     */
    private record TopBlockSample(String blockName, int y, int worldBlockX, int worldBlockZ) {
        private static final TopBlockSample EMPTY = new TopBlockSample(null, 0, 0, 0);

        // Conveniencia: una muestra vacia significa "no se encontro bloque visible".
        private boolean isEmpty() {
            return blockName == null || blockName.isBlank();
        }
    }

    private static void debug(String format, Object... args) {
        if(!DEBUG_LOGGING) {
            return;
        }
        System.out.println("[MCARender] " + String.format(Locale.ROOT, format, args));
    }

    private static String formatRegion(RegionCoordinates region) {
        return region == null ? "?" : "(" + region.x() + "," + region.z() + ")";
    }

    private static void markReason(ColumnDiagnostics diagnostics, String reason) {
        if(diagnostics == null || reason == null || reason.isBlank()) {
            return;
        }
        diagnostics.reason = reason;
    }

    private static void markScheme(ColumnDiagnostics diagnostics, String scheme) {
        if(diagnostics == null || scheme == null || scheme.isBlank()) {
            return;
        }
        diagnostics.scheme = scheme;
    }

    private ChunkInspection inspectChunk(Chunk chunk) {
        CompoundTag root = chunk == null ? null : chunk.getHandle();
        if(root == null) {
            return new ChunkInspection("unknown", -1, "root_null");
        }

        CompoundTag legacyLevel = root.getCompoundTag("Level");
        if(legacyLevel != null) {
            ListTag<CompoundTag> sections = getLegacyLevelSections(legacyLevel);
            int sectionCount = sections == null ? 0 : sections.size();
            if(isModernLevelSections(sections)) {
                int dataVersion = root.getInt("DataVersion");
                return new ChunkInspection("modern_level(dv=" + dataVersion + ")", sectionCount, sectionCount == 0 ? "modern_sections_missing" : "ok");
            }
            return new ChunkInspection("legacy", sectionCount, sectionCount == 0 ? "legacy_sections_missing" : "ok");
        }

        int dataVersion = root.getInt("DataVersion");
        ListTag<?> sections = root.getListTag("sections");
        int sectionCount = sections == null ? 0 : sections.size();
        return new ChunkInspection("modern(dv=" + dataVersion + ")", sectionCount, sectionCount == 0 ? "modern_sections_missing" : "ok");
    }

    private static final class ColumnDiagnostics {
        private String scheme;
        private String reason;
        private int sectionCount = -1;
        private int dataVersion = Integer.MIN_VALUE;
        private String firstTransparentBlock;
        private int firstTransparentY = Integer.MIN_VALUE;
    }

    private record ChunkInspection(String scheme, int sectionCount, String status) {}
    private record RenderedRegion(RegionCoordinates region, BufferedImage image) {}
    private static final class RegionRenderRuntimeException extends RuntimeException {
        private final IOException ioException;

        private RegionRenderRuntimeException(IOException ioException) {
            super(ioException);
            this.ioException = ioException;
        }

        private IOException ioException() {
            return ioException;
        }
    }

    private static final class DebugTrace {
        private final Path path;
        private final RegionCoordinates region;
        private final RenderOptions options;
        private int nullChunks;
        private int nonNullChunks;
        private int scannedColumns;
        private int chunksWithoutVisibleBlocks;
        private int chunkLinesLogged;
        private int sampleLinesLogged;
        private final Map<String, Integer> reasonCounts = new LinkedHashMap<>();
        private final Map<String, Integer> schemeCounts = new LinkedHashMap<>();

        private DebugTrace(Path path, RegionCoordinates region, RenderOptions options) {
            this.path = path;
            this.region = region;
            this.options = options;
        }

        private static DebugTrace forRegionScan(Path path, RegionCoordinates region, RenderOptions options) {
            return new DebugTrace(path, region, options);
        }

        private void logStart() {
            debug("scan start path=%s region=%s yRange=%d..%d ignoreTransparent=%s",
                    path,
                    formatRegion(region),
                    options.minY(),
                    options.maxY(),
                    options.ignoreTransparentBlocks());
        }

        private void onNullChunk(int localChunkX, int localChunkZ) {
            nullChunks++;
            recordReason("chunk_null");
            maybeLogChunk("chunk (%d,%d) world=(%d,%d) status=null",
                    localChunkX,
                    localChunkZ,
                    region.x() * REGION_CHUNK_SIDE + localChunkX,
                    region.z() * REGION_CHUNK_SIDE + localChunkZ);
        }

        private void onChunkStart(int localChunkX, int localChunkZ, int worldChunkX, int worldChunkZ, ChunkInspection inspection) {
            nonNullChunks++;
            recordScheme(inspection.scheme());
            if(!"ok".equals(inspection.status())) {
                recordReason(inspection.status());
            }
            maybeLogChunk("chunk (%d,%d) world=(%d,%d) scheme=%s sections=%d status=%s",
                    localChunkX,
                    localChunkZ,
                    worldChunkX,
                    worldChunkZ,
                    inspection.scheme(),
                    inspection.sectionCount(),
                    inspection.status());
        }

        private void onColumnScanned(ColumnDiagnostics diagnostics) {
            scannedColumns++;
            if(diagnostics == null) {
                return;
            }
            if(diagnostics.scheme != null) {
                recordScheme(diagnostics.scheme);
            }
            if(diagnostics.reason != null) {
                recordReason(diagnostics.reason);
                if(sampleLinesLogged < DEBUG_MAX_SAMPLE_LINES
                        && ("only_transparent_blocks".equals(diagnostics.reason) || "no_visible_block_in_range".equals(diagnostics.reason))
                        && diagnostics.firstTransparentBlock != null) {
                    sampleLinesLogged++;
                    debug("no visible column path=%s scheme=%s reason=%s transparentProbe=%s@%d",
                            path.getFileName(),
                            fallback(diagnostics.scheme, "?"),
                            diagnostics.reason,
                            diagnostics.firstTransparentBlock,
                            diagnostics.firstTransparentY);
                }
            }
        }

        private void onVisibleColumn(int localChunkX, int localChunkZ, int localX, int localZ, TopBlockSample sample, ColumnDiagnostics diagnostics) {
            if(sampleLinesLogged >= DEBUG_MAX_SAMPLE_LINES) {
                return;
            }
            sampleLinesLogged++;
            debug("visible column path=%s chunk=(%d,%d) localBlock=(%d,%d) worldBlock=(%d,%d) block=%s y=%d scheme=%s sections=%d transparentProbe=%s@%s",
                    path.getFileName(),
                    localChunkX,
                    localChunkZ,
                    localX,
                    localZ,
                    sample.worldBlockX(),
                    sample.worldBlockZ(),
                    sample.blockName(),
                    sample.y(),
                    diagnostics == null ? "?" : fallback(diagnostics.scheme, "?"),
                    diagnostics == null ? -1 : diagnostics.sectionCount,
                    diagnostics == null ? "-" : fallback(diagnostics.firstTransparentBlock, "-"),
                    diagnostics == null || diagnostics.firstTransparentY == Integer.MIN_VALUE ? "-" : Integer.toString(diagnostics.firstTransparentY));
        }

        private void onChunkWithoutVisibleBlocks(int localChunkX, int localChunkZ, ChunkInspection inspection) {
            chunksWithoutVisibleBlocks++;
            maybeLogChunk("chunk (%d,%d) no visible blocks scheme=%s sections=%d",
                    localChunkX,
                    localChunkZ,
                    inspection.scheme(),
                    inspection.sectionCount());
        }

        private void logFinish(boolean visibleFound) {
            debug("scan finish path=%s region=%s visibleFound=%s nonNullChunks=%d nullChunks=%d scannedColumns=%d chunksWithoutVisible=%d",
                    path,
                    formatRegion(region),
                    visibleFound,
                    nonNullChunks,
                    nullChunks,
                    scannedColumns,
                    chunksWithoutVisibleBlocks);
            logMap("scheme summary", schemeCounts, DEBUG_MAX_REASON_LINES);
            logMap("reason summary", reasonCounts, DEBUG_MAX_REASON_LINES);
        }

        private void maybeLogChunk(String format, Object... args) {
            if(chunkLinesLogged >= DEBUG_MAX_CHUNK_LINES) {
                return;
            }
            chunkLinesLogged++;
            debug(format, args);
        }

        private void recordReason(String reason) {
            if(reason == null || reason.isBlank()) {
                return;
            }
            reasonCounts.merge(reason, 1, Integer::sum);
        }

        private void recordScheme(String scheme) {
            if(scheme == null || scheme.isBlank()) {
                return;
            }
            schemeCounts.merge(scheme, 1, Integer::sum);
        }

        private void logMap(String label, Map<String, Integer> counts, int maxLines) {
            if(counts.isEmpty()) {
                return;
            }
            int printed = 0;
            for(Map.Entry<String, Integer> entry : counts.entrySet()) {
                if(printed >= maxLines) {
                    debug("%s ... %d more", label, counts.size() - printed);
                    break;
                }
                printed++;
                debug("%s %s=%d", label, entry.getKey(), entry.getValue());
            }
        }

        private String fallback(String value, String fallback) {
            return value == null || value.isBlank() ? fallback : value;
        }
    }
}
