package controlador.world;

import controlador.MCARenderer;
import modelo.Server;

import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

public final class PreviewRenderPreferences {
    public static final String PROPERTY_PREFIX = "preview.menu.";
    private static final String KEY_PRESET = PROPERTY_PREFIX + "preset";
    private static final String KEY_RENDER_REALTIME = PROPERTY_PREFIX + "renderRealtime";
    private static final String KEY_SHOW_SPAWN = PROPERTY_PREFIX + "showSpawn";
    private static final String KEY_SHOW_PLAYERS = PROPERTY_PREFIX + "showPlayers";
    private static final String KEY_SHOW_CHUNK_GRID = PROPERTY_PREFIX + "showChunkGrid";
    private static final String KEY_USE_WHOLE_MAP = PROPERTY_PREFIX + "useWholeMap";
    private static final String KEY_RENDER_LIMIT_PIXELS = PROPERTY_PREFIX + "renderLimitPixels";
    private static final String KEY_RENDER_CENTER_ID = PROPERTY_PREFIX + "renderCenterId";
    private static final String LEGACY_KEY_NEIGHBOR_HEIGHT_HINTS = PROPERTY_PREFIX + "toggle.neighborHeightHints";
    private static final String LEGACY_KEY_IGNORE_TRANSPARENT_BLOCKS = PROPERTY_PREFIX + "toggle.ignoreTransparentBlocks";

    private final PreviewRenderPreset preset;
    private final EnumMap<RenderToggle, Boolean> renderToggles;
    private final boolean renderRealtime;
    private final boolean showSpawn;
    private final boolean showPlayers;
    private final boolean showChunkGrid;
    private final boolean useWholeMap;
    private final int renderLimitPixels;
    private final String renderCenterId;

    private PreviewRenderPreferences(
            PreviewRenderPreset preset,
            Map<RenderToggle, Boolean> renderToggles,
            boolean renderRealtime,
            boolean showSpawn,
            boolean showPlayers,
            boolean showChunkGrid,
            boolean useWholeMap,
            int renderLimitPixels,
            String renderCenterId
    ) {
        this.preset = preset == null ? PreviewRenderPreset.BALANCED : preset;
        this.renderToggles = new EnumMap<>(RenderToggle.class);
        for (RenderToggle toggle : RenderToggle.values()) {
            this.renderToggles.put(toggle, Boolean.TRUE.equals(renderToggles.get(toggle)));
        }
        this.renderRealtime = renderRealtime;
        this.showSpawn = showSpawn;
        this.showPlayers = showPlayers;
        this.showChunkGrid = showChunkGrid;
        this.useWholeMap = useWholeMap;
        this.renderLimitPixels = normalizeRenderLimitPixels(renderLimitPixels);
        this.renderCenterId = normalizeRenderCenterId(renderCenterId);
    }

    public static PreviewRenderPreferences defaults() {
        return presetDefaults(PreviewRenderPreset.BALANCED);
    }

    public static PreviewRenderPreferences presetDefaults(PreviewRenderPreset preset) {
        EnumMap<RenderToggle, Boolean> toggles = new EnumMap<>(RenderToggle.class);
        PreviewRenderPreset effectivePreset = preset == null || preset == PreviewRenderPreset.CUSTOM
                ? PreviewRenderPreset.BALANCED
                : preset;
        for (RenderToggle toggle : RenderToggle.values()) {
            toggles.put(toggle, toggle.enabledInPreset(effectivePreset));
        }
        return new PreviewRenderPreferences(
                effectivePreset,
                toggles,
                false,
                false,
                false,
                false,
                false,
                256,
                "spawn"
        );
    }

    public static PreviewRenderPreferences fromLegacyServer(Server server) {
        PreviewRenderPreset preset = PreviewRenderPreset.fromId(server == null ? null : server.getPreviewRenderProfileId());
        PreviewRenderPreferences base = preset == PreviewRenderPreset.CUSTOM
                ? defaults().withPreset(PreviewRenderPreset.CUSTOM)
                : presetDefaults(preset);
        if (server == null) {
            return base;
        }
        return new PreviewRenderPreferences(
                base.preset,
                base.renderToggles,
                Boolean.TRUE.equals(server.getPreviewRenderRealtime()),
                Boolean.TRUE.equals(server.getPreviewShowSpawn()),
                Boolean.TRUE.equals(server.getPreviewShowPlayers()),
                Boolean.TRUE.equals(server.getPreviewShowChunkGrid()),
                Boolean.TRUE.equals(server.getPreviewUseWholeMap()),
                server.getPreviewRenderLimitPixels() == null ? 256 : server.getPreviewRenderLimitPixels(),
                server.getPreviewRenderCenterId()
        );
    }

    public static PreviewRenderPreferences fromProperties(Properties properties, PreviewRenderPreferences fallback) {
        PreviewRenderPreferences base = fallback == null ? defaults() : fallback;
        if (properties == null || !hasPersistedPreviewProperties(properties)) {
            return base;
        }

        PreviewRenderPreset preset = PreviewRenderPreset.fromId(properties.getProperty(KEY_PRESET));
        PreviewRenderPreferences presetBase = preset == PreviewRenderPreset.CUSTOM ? base.withPreset(PreviewRenderPreset.CUSTOM) : base.withPreset(preset);
        EnumMap<RenderToggle, Boolean> toggles = new EnumMap<>(RenderToggle.class);
        for (RenderToggle toggle : RenderToggle.values()) {
            String property = properties.getProperty(toggle.propertyKey());
            toggles.put(toggle, property == null ? presetBase.isEnabled(toggle) : Boolean.parseBoolean(property));
        }

        return new PreviewRenderPreferences(
                preset,
                toggles,
                readBoolean(properties, KEY_RENDER_REALTIME, presetBase.renderRealtime),
                readBoolean(properties, KEY_SHOW_SPAWN, presetBase.showSpawn),
                readBoolean(properties, KEY_SHOW_PLAYERS, presetBase.showPlayers),
                readBoolean(properties, KEY_SHOW_CHUNK_GRID, presetBase.showChunkGrid),
                readBoolean(properties, KEY_USE_WHOLE_MAP, presetBase.useWholeMap),
                readInt(properties, KEY_RENDER_LIMIT_PIXELS, presetBase.renderLimitPixels),
                properties.getProperty(KEY_RENDER_CENTER_ID, presetBase.renderCenterId)
        );
    }

    public static boolean hasPersistedPreviewProperties(Properties properties) {
        if (properties == null || properties.isEmpty()) {
            return false;
        }
        for (String key : properties.stringPropertyNames()) {
            if (key != null && key.startsWith(PROPERTY_PREFIX)) {
                return true;
            }
        }
        return false;
    }

    public void writeTo(Properties properties) {
        if (properties == null) {
            return;
        }
        properties.remove(LEGACY_KEY_NEIGHBOR_HEIGHT_HINTS);
        properties.remove(LEGACY_KEY_IGNORE_TRANSPARENT_BLOCKS);
        properties.setProperty(KEY_PRESET, preset.id());
        properties.setProperty(KEY_RENDER_REALTIME, Boolean.toString(renderRealtime));
        properties.setProperty(KEY_SHOW_SPAWN, Boolean.toString(showSpawn));
        properties.setProperty(KEY_SHOW_PLAYERS, Boolean.toString(showPlayers));
        properties.setProperty(KEY_SHOW_CHUNK_GRID, Boolean.toString(showChunkGrid));
        properties.setProperty(KEY_USE_WHOLE_MAP, Boolean.toString(useWholeMap));
        properties.setProperty(KEY_RENDER_LIMIT_PIXELS, Integer.toString(renderLimitPixels));
        properties.setProperty(KEY_RENDER_CENTER_ID, renderCenterId);
        for (RenderToggle toggle : RenderToggle.values()) {
            properties.setProperty(toggle.propertyKey(), Boolean.toString(isEnabled(toggle)));
        }
    }

    public PreviewRenderPreferences withPreset(PreviewRenderPreset preset) {
        if (preset == null || preset == PreviewRenderPreset.CUSTOM) {
            return new PreviewRenderPreferences(
                    PreviewRenderPreset.CUSTOM,
                    renderToggles,
                    renderRealtime,
                    showSpawn,
                    showPlayers,
                    showChunkGrid,
                    useWholeMap,
                    renderLimitPixels,
                    renderCenterId
            );
        }
        EnumMap<RenderToggle, Boolean> toggles = new EnumMap<>(RenderToggle.class);
        for (RenderToggle toggle : RenderToggle.values()) {
            toggles.put(toggle, toggle.enabledInPreset(preset));
        }
        return new PreviewRenderPreferences(
                preset,
                toggles,
                renderRealtime,
                showSpawn,
                showPlayers,
                showChunkGrid,
                useWholeMap,
                renderLimitPixels,
                renderCenterId
        );
    }

    public PreviewRenderPreferences withRenderToggle(RenderToggle toggle, boolean enabled) {
        EnumMap<RenderToggle, Boolean> toggles = new EnumMap<>(renderToggles);
        toggles.put(toggle, enabled);
        return new PreviewRenderPreferences(
                PreviewRenderPreset.CUSTOM,
                toggles,
                renderRealtime,
                showSpawn,
                showPlayers,
                showChunkGrid,
                useWholeMap,
                renderLimitPixels,
                renderCenterId
        );
    }

    public PreviewRenderPreferences withRenderRealtime(boolean value) {
        return new PreviewRenderPreferences(preset, renderToggles, value, showSpawn, showPlayers, showChunkGrid, useWholeMap, renderLimitPixels, renderCenterId);
    }

    public PreviewRenderPreferences withShowSpawn(boolean value) {
        return new PreviewRenderPreferences(preset, renderToggles, renderRealtime, value, showPlayers, showChunkGrid, useWholeMap, renderLimitPixels, renderCenterId);
    }

    public PreviewRenderPreferences withShowPlayers(boolean value) {
        return new PreviewRenderPreferences(preset, renderToggles, renderRealtime, showSpawn, value, showChunkGrid, useWholeMap, renderLimitPixels, renderCenterId);
    }

    public PreviewRenderPreferences withShowChunkGrid(boolean value) {
        return new PreviewRenderPreferences(preset, renderToggles, renderRealtime, showSpawn, showPlayers, value, useWholeMap, renderLimitPixels, renderCenterId);
    }

    public PreviewRenderPreferences withUseWholeMap(boolean value) {
        return new PreviewRenderPreferences(preset, renderToggles, renderRealtime, showSpawn, showPlayers, showChunkGrid, value, renderLimitPixels, renderCenterId);
    }

    public PreviewRenderPreferences withRenderLimitPixels(int value) {
        return new PreviewRenderPreferences(preset, renderToggles, renderRealtime, showSpawn, showPlayers, showChunkGrid, useWholeMap, value, renderCenterId);
    }

    public PreviewRenderPreferences withRenderCenterId(String value) {
        return new PreviewRenderPreferences(preset, renderToggles, renderRealtime, showSpawn, showPlayers, showChunkGrid, useWholeMap, renderLimitPixels, value);
    }

    public MCARenderer.RenderOptions toRenderOptions() {
        return MCARenderer.RenderOptions.defaults()
                .withShadeByHeight(isEnabled(RenderToggle.SHADE_BY_HEIGHT))
                .withWaterSubsurfaceShading(isEnabled(RenderToggle.WATER_SUBSURFACE_SHADING))
                .withBiomeColoring(isEnabled(RenderToggle.BIOME_COLORING))
                .withAdvancedMaterialShading(isEnabled(RenderToggle.ADVANCED_MATERIAL_SHADING))
                .withAdvancedWaterColoring(isEnabled(RenderToggle.ADVANCED_WATER_COLORING))
                .withAdvancedBiomeColoring(isEnabled(RenderToggle.ADVANCED_BIOME_COLORING));
    }

    public boolean isEnabled(RenderToggle toggle) {
        return Boolean.TRUE.equals(renderToggles.get(toggle));
    }

    public PreviewRenderPreset preset() {
        return preset;
    }

    public boolean renderRealtime() {
        return renderRealtime;
    }

    public boolean showSpawn() {
        return showSpawn;
    }

    public boolean showPlayers() {
        return showPlayers;
    }

    public boolean showChunkGrid() {
        return showChunkGrid;
    }

    public boolean useWholeMap() {
        return useWholeMap;
    }

    public int renderLimitPixels() {
        return renderLimitPixels;
    }

    public String renderCenterId() {
        return renderCenterId;
    }

    private static boolean readBoolean(Properties properties, String key, boolean fallback) {
        String value = properties.getProperty(key);
        return value == null ? fallback : Boolean.parseBoolean(value);
    }

    private static int readInt(Properties properties, String key, int fallback) {
        String value = properties.getProperty(key);
        if (value == null || value.isBlank()) {
            return fallback;
        }
        try {
            return Integer.parseInt(value.trim());
        } catch (NumberFormatException ex) {
            return fallback;
        }
    }

    private static int normalizeRenderLimitPixels(int value) {
        if (value < 0) {
            return 256;
        }
        return switch (value) {
            case 0, 64, 128, 256, 512, 1024, 2048, 4096, 8192, 16384 -> value;
            default -> 256;
        };
    }

    private static String normalizeRenderCenterId(String value) {
        return value == null || value.isBlank() ? "spawn" : value;
    }

    public enum PreviewRenderPreset {
        CUSTOM("custom", "Personalizado"),
        QUALITY("quality", "Calidad"),
        BALANCED("balanced", "Equilibrado"),
        PERFORMANCE("fast", "Rendimiento"),
        ULTRA_PERFORMANCE("performance", "Ultra Rendimiento");

        private final String id;
        private final String label;

        PreviewRenderPreset(String id, String label) {
            this.id = id;
            this.label = label;
        }

        public String id() {
            return id;
        }

        public static PreviewRenderPreset fromId(String value) {
            if (value != null) {
                for (PreviewRenderPreset preset : values()) {
                    if (Objects.equals(preset.id, value.toLowerCase(Locale.ROOT))) {
                        return preset;
                    }
                }
            }
            return BALANCED;
        }

        @Override
        public String toString() {
            return label;
        }
    }

    public enum RenderToggle {
        SHADE_BY_HEIGHT(
                "shadeByHeight",
                "Shaders basicos por altura",
                "<html><b>Que hace:</b> calcula la pendiente local con las alturas oeste/este/norte/sur y ajusta el brillo final por pixel.<br>"
                        + "<b>Rendimiento:</b> aumenta trabajo de CPU en el pintado; no usa GPU. Es bastante mas barato que el sombreado avanzado.<br>"
                        + "<b>Visual:</b> mejora la lectura del relieve y evita mapas planos.<br>"
                        + "<b>Efectos secundarios:</b> puede exagerar ruido fino o escalones en terreno abrupto.<br>"
                        + "<b>Conviene:</b> activarlo salvo que busques el maximo rendimiento absoluto.</html>"
        ),
        WATER_SUBSURFACE_SHADING(
                "waterSubsurfaceShading",
                "Transparencia basica del agua",
                "<html><b>Que hace:</b> cuando la superficie es agua, busca el primer bloque solido bajo ella y mezcla color de fondo y profundidad.<br>"
                        + "<b>Rendimiento:</b> añade lecturas verticales extra en columnas con agua; coste de CPU moderado en oceanos y rios.<br>"
                        + "<b>Visual:</b> distingue agua somera/profunda y mejora costas y cauces.<br>"
                        + "<b>Efectos secundarios:</b> desactivarlo vuelve el agua plana y pierde transparencia aparente.<br>"
                        + "<b>Conviene:</b> activarlo en calidad y equilibrado; desactivarlo si el mundo tiene mucha agua y prima velocidad.</html>"
        ),
        BIOME_COLORING(
                "biomeColoring",
                "Tintado por bioma",
                "<html><b>Que hace:</b> consulta el bioma por columna y tine hierba, follaje, agua, hielo y nieve segun ese bioma.<br>"
                        + "<b>Rendimiento:</b> suma busquedas de bioma y mezclas de color cacheadas; coste de CPU moderado.<br>"
                        + "<b>Visual:</b> separa mejor pantanos, taigas, junglas, rios y otras zonas.<br>"
                        + "<b>Efectos secundarios:</b> al desactivarlo vegetacion y agua se uniformizan y el mapa pierde contexto ambiental.<br>"
                        + "<b>Conviene:</b> activarlo salvo cuando el objetivo sea reducir tiempo de render al minimo.</html>"
        ),
        ADVANCED_MATERIAL_SHADING(
                "advancedMaterialShading",
                "Sombreado avanzado por material",
                "<html><b>Que hace:</b> ajusta el sombreado basico segun familia de bloque para que agua, nieve, arena, piedra, madera y vegetacion reaccionen distinto a la pendiente.<br>"
                        + "<b>Rendimiento:</b> anade branching y calculo extra por pixel; coste de CPU medio.<br>"
                        + "<b>Visual:</b> el relieve se lee mejor y algunos materiales quedan menos planos o menos agresivos.<br>"
                        + "<b>Efectos secundarios:</b> puede cambiar el contraste material a material y producir diferencias mas marcadas en zonas mixtas.<br>"
                        + "<b>Conviene:</b> activarlo cuando buscas la mejor lectura visual y puedes asumir algo mas de coste.</html>"
        ),
        ADVANCED_WATER_COLORING(
                "advancedWaterColoring",
                "Agua avanzada",
                "<html><b>Que hace:</b> usa una mezcla mas rica de profundidad, tipo de fondo y factor costero para colorear la superficie del agua.<br>"
                        + "<b>Rendimiento:</b> mas mezcla de color y mas ramificaciones que la transparencia basica; coste de CPU medio.<br>"
                        + "<b>Visual:</b> mejora orillas, fondos claros, agua organica y diferencia entre somero y profundo.<br>"
                        + "<b>Efectos secundarios:</b> si el agua domina el mapa, su coste se nota; desactivarlo simplifica mucho el resultado.<br>"
                        + "<b>Conviene:</b> activarlo en calidad; normalmente no compensa en modos orientados a velocidad.</html>"
        ),
        ADVANCED_BIOME_COLORING(
                "advancedBiomeColoring",
                "Biomas avanzados",
                "<html><b>Que hace:</b> aplica reglas adicionales de tintado, por ejemplo para follaje de cherry grove y variantes mas finas de vegetacion/agua.<br>"
                        + "<b>Rendimiento:</b> aumenta el trabajo de CPU sobre la ruta de colorimetria; suele ser el refinamiento de bioma mas caro.<br>"
                        + "<b>Visual:</b> mejora matices finos entre biomas y especies concretas.<br>"
                        + "<b>Efectos secundarios:</b> la ganancia visual es sutil en muchos mapas comparada con su coste.<br>"
                        + "<b>Conviene:</b> reservarlo para calidad o inspeccion visual detallada.</html>"
        );

        private final String propertySuffix;
        private final String label;
        private final String helpText;

        RenderToggle(String propertySuffix, String label, String helpText) {
            this.propertySuffix = propertySuffix;
            this.label = label;
            this.helpText = helpText;
        }

        public String propertyKey() {
            return PROPERTY_PREFIX + "toggle." + propertySuffix;
        }

        public String label() {
            return label;
        }

        public String helpText() {
            return helpText;
        }

        public boolean enabledInPreset(PreviewRenderPreset preset) {
            return switch (preset) {
                case QUALITY -> switch (this) {
                    case SHADE_BY_HEIGHT, WATER_SUBSURFACE_SHADING, BIOME_COLORING, ADVANCED_MATERIAL_SHADING,
                            ADVANCED_WATER_COLORING, ADVANCED_BIOME_COLORING -> true;
                };
                case PERFORMANCE -> switch (this) {
                    case SHADE_BY_HEIGHT -> true;
                    case WATER_SUBSURFACE_SHADING, BIOME_COLORING, ADVANCED_MATERIAL_SHADING,
                            ADVANCED_WATER_COLORING, ADVANCED_BIOME_COLORING -> false;
                };
                case ULTRA_PERFORMANCE -> switch (this) {
                    case SHADE_BY_HEIGHT, WATER_SUBSURFACE_SHADING, BIOME_COLORING, ADVANCED_MATERIAL_SHADING,
                            ADVANCED_WATER_COLORING, ADVANCED_BIOME_COLORING -> false;
                };
                case BALANCED, CUSTOM -> switch (this) {
                    case SHADE_BY_HEIGHT, WATER_SUBSURFACE_SHADING, BIOME_COLORING -> true;
                    case ADVANCED_MATERIAL_SHADING, ADVANCED_WATER_COLORING, ADVANCED_BIOME_COLORING -> false;
                };
            };
        }
    }
}
