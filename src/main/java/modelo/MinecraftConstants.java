/*
 * Fichero: MinecraftConstants.java
 *
 * Autor: Manuel Enrique Jeronimo Aragon
 *
 * Descripcion:
 * Esta clase centraliza las constantes de Minecraft usadas en el proyecto para facilitar su reutilizacion
 * y evitar valores literales repetidos en el codigo.
 * */

package modelo;

import java.util.List;

public final class MinecraftConstants {
    private MinecraftConstants() {
        throw new UnsupportedOperationException("Esta clase no se puede instanciar");
    }

    // ===== GAMEMODES =====
    public static final String GAMEMODE_SURVIVAL = "survival";
    public static final String GAMEMODE_CREATIVE = "creative";
    public static final String GAMEMODE_ADVENTURE = "adventure";
    public static final String GAMEMODE_SPECTATOR = "spectator";

    public static final List<String> GAMEMODES = List.of(
            GAMEMODE_SURVIVAL,
            GAMEMODE_CREATIVE,
            GAMEMODE_ADVENTURE,
            GAMEMODE_SPECTATOR
    );

    // ===== DIFFICULTIES =====
    public static final String DIFFICULTY_PEACEFUL = "peaceful";
    public static final String DIFFICULTY_EASY = "easy";
    public static final String DIFFICULTY_NORMAL = "normal";
    public static final String DIFFICULTY_HARD = "hard";

    public static final List<String> DIFFICULTIES = List.of(
            DIFFICULTY_PEACEFUL,
            DIFFICULTY_EASY,
            DIFFICULTY_NORMAL,
            DIFFICULTY_HARD
    );

    // ===== WORLD TYPES =====
    public static final String WORLD_TYPE_NORMAL = "normal";
    public static final String WORLD_TYPE_FLAT = "flat";
    public static final String WORLD_TYPE_LARGE_BIOMES = "large_biomes";
    public static final String WORLD_TYPE_AMPLIFIED = "amplified";
    public static final String WORLD_TYPE_SINGLE_BIOME_SURFACE = "single_biome_surface";

    public static final String WORLD_TYPE_NORMAL_NAMESPACED = "minecraft:" + WORLD_TYPE_NORMAL;
    public static final String WORLD_TYPE_FLAT_NAMESPACED = "minecraft:" + WORLD_TYPE_FLAT;
    public static final String WORLD_TYPE_LARGE_BIOMES_NAMESPACED = "minecraft:" + WORLD_TYPE_LARGE_BIOMES;
    public static final String WORLD_TYPE_AMPLIFIED_NAMESPACED = "minecraft:" + WORLD_TYPE_AMPLIFIED;
    public static final String WORLD_TYPE_SINGLE_BIOME_SURFACE_NAMESPACED = "minecraft:" + WORLD_TYPE_SINGLE_BIOME_SURFACE;

    public static final List<String> WORLD_TYPES = List.of(
            WORLD_TYPE_NORMAL,
            WORLD_TYPE_FLAT,
            WORLD_TYPE_LARGE_BIOMES,
            WORLD_TYPE_AMPLIFIED,
            WORLD_TYPE_SINGLE_BIOME_SURFACE
    );

    public static final List<String> WORLD_TYPES_NAMESPACED = List.of(
            WORLD_TYPE_NORMAL_NAMESPACED,
            WORLD_TYPE_FLAT_NAMESPACED,
            WORLD_TYPE_LARGE_BIOMES_NAMESPACED,
            WORLD_TYPE_AMPLIFIED_NAMESPACED,
            WORLD_TYPE_SINGLE_BIOME_SURFACE_NAMESPACED
    );

    // ===== DEFAULTS =====
    public static final String DEFAULT_WORLD_NAME = "world";
    public static final String DEFAULT_GAMEMODE = GAMEMODE_SURVIVAL;
    public static final String DEFAULT_DIFFICULTY = DIFFICULTY_NORMAL;
    public static final String DEFAULT_WORLD_TYPE = WORLD_TYPE_NORMAL;
    public static final String DEFAULT_WORLD_TYPE_NAMESPACED = WORLD_TYPE_NORMAL_NAMESPACED;
}
