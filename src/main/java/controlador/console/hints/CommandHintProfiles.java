package controlador.console.hints;

import controlador.console.ArgumentHint;
import controlador.console.CommandToken;
import controlador.console.ParsedCommandLine;
import controlador.console.SuggestionCategory;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Perfiles de hints reutilizables para escenarios administrativos habituales.
 */
public final class CommandHintProfiles {
    private static final Set<SuggestionCategory> COMMAND_ONLY = Set.of(SuggestionCategory.COMMAND, SuggestionCategory.SUBCOMMAND);
    private static final Set<SuggestionCategory> PLAYER_ONLY = Set.of(SuggestionCategory.PLAYER);
    private static final Set<SuggestionCategory> PLAYER_OR_ENTITY = Set.of(SuggestionCategory.PLAYER, SuggestionCategory.ENTITY);
    private static final Set<SuggestionCategory> ITEM_OR_TAG = Set.of(SuggestionCategory.ITEM, SuggestionCategory.TAG);
    private static final Set<SuggestionCategory> BLOCK_OR_TAG = Set.of(SuggestionCategory.BLOCK, SuggestionCategory.TAG);
    private static final Set<SuggestionCategory> ENTITY_OR_TAG = Set.of(SuggestionCategory.ENTITY, SuggestionCategory.TAG);
    private static final Set<SuggestionCategory> WORLD_VALUE = Set.of(SuggestionCategory.DIMENSION, SuggestionCategory.COORDINATE, SuggestionCategory.FREE_VALUE);
    private static final Set<SuggestionCategory> RULE_VALUE = Set.of(SuggestionCategory.GAMERULE, SuggestionCategory.FREE_VALUE);
    private static final Set<SuggestionCategory> EFFECT_VALUE = Set.of(SuggestionCategory.EFFECT);
    private static final Set<SuggestionCategory> ENCHANT_VALUE = Set.of(SuggestionCategory.ENCHANTMENT);

    private CommandHintProfiles() {
    }

    public static CommandHintResolver defaultResolver() {
        return (parsed, profile) -> {
            if (parsed == null) {
                return profile.fallbackCategories();
            }
            ArgumentHint hint = profile.argumentHintAt(parsed.argumentIndex());
            if (hint == null || hint.acceptedCategories().isEmpty()) {
                return profile.fallbackCategories();
            }
            return hint.acceptedCategories();
        };
    }

    public static List<CommandHintProfile> defaults() {
        return List.of(
                playerManagementProfile(),
                inventoryProfile(),
                entityAndSummonProfile(),
                worldAndWeatherProfile(),
                permissionsAndModerationProfile(),
                configurationAndRulesProfile(),
                pluginAndModProfile()
        );
    }

    private static CommandHintProfile playerManagementProfile() {
        return new CommandHintProfile(
                "player-management",
                "Gestion de jugadores y mensajeria directa",
                AdminCommandFamily.PLAYER_MANAGEMENT,
                Set.of("tell", "msg", "w", "me", "list"),
                Set.of(),
                List.of(
                        ArgumentHint.freeValue("target", "Jugador objetivo", PLAYER_ONLY),
                        ArgumentHint.freeValue("message", "Mensaje", Set.of(SuggestionCategory.FREE_VALUE))
                ),
                PLAYER_ONLY,
                (parsed, profile) -> parsed == null || parsed.argumentIndex() <= 0
                        ? PLAYER_ONLY
                        : Set.of(SuggestionCategory.FREE_VALUE),
                "admin-family",
                java.util.Map.of("family", AdminCommandFamily.PLAYER_MANAGEMENT.name())
        );
    }

    private static CommandHintProfile inventoryProfile() {
        return new CommandHintProfile(
                "inventory-items",
                "Inventario, give, clear, item y enchant",
                AdminCommandFamily.INVENTORY_AND_ITEMS,
                Set.of("give", "clear", "item", "enchant"),
                Set.of("replace", "modify", "entity", "block"),
                List.of(
                        ArgumentHint.freeValue("target", "Jugador o entidad", PLAYER_OR_ENTITY),
                        ArgumentHint.freeValue("item", "Item o tag", ITEM_OR_TAG),
                        ArgumentHint.freeValue("count_or_value", "Cantidad o valor libre", Set.of(SuggestionCategory.NUMBER, SuggestionCategory.FREE_VALUE))
                ),
                ITEM_OR_TAG,
                (parsed, profile) -> {
                    if (parsed == null) {
                        return ITEM_OR_TAG;
                    }
                    String command = normalizeCommand(parsed.commandName());
                    return switch (command) {
                        case "give" -> parsed.argumentIndex() == 0 ? PLAYER_ONLY : ITEM_OR_TAG;
                        case "clear" -> parsed.argumentIndex() == 0 ? PLAYER_ONLY : ITEM_OR_TAG;
                        case "enchant" -> parsed.argumentIndex() == 0 ? PLAYER_ONLY : ENCHANT_VALUE;
                        case "item" -> resolveItemCommand(parsed);
                        default -> profile.fallbackCategories();
                    };
                },
                "admin-family",
                java.util.Map.of("family", AdminCommandFamily.INVENTORY_AND_ITEMS.name())
        );
    }

    private static CommandHintProfile entityAndSummonProfile() {
        return new CommandHintProfile(
                "entities-summon",
                "Entidades, summon, tp y tag",
                AdminCommandFamily.ENTITIES_AND_SUMMON,
                Set.of("summon", "kill", "tag", "tp", "teleport", "ride", "spectate"),
                Set.of("add", "remove", "list"),
                List.of(
                        ArgumentHint.freeValue("target_or_entity", "Entidad o jugador", PLAYER_OR_ENTITY),
                        ArgumentHint.freeValue("entity_or_destination", "Entidad, destino o coordenadas", Set.of(SuggestionCategory.ENTITY, SuggestionCategory.COORDINATE, SuggestionCategory.DIMENSION)),
                        ArgumentHint.freeValue("extra", "Tag o coordenadas", Set.of(SuggestionCategory.TAG, SuggestionCategory.COORDINATE, SuggestionCategory.FREE_VALUE))
                ),
                ENTITY_OR_TAG,
                (parsed, profile) -> {
                    if (parsed == null) {
                        return ENTITY_OR_TAG;
                    }
                    String command = normalizeCommand(parsed.commandName());
                    return switch (command) {
                        case "summon" -> parsed.argumentIndex() == 0 ? ENTITY_OR_TAG : Set.of(SuggestionCategory.COORDINATE, SuggestionCategory.TAG);
                        case "kill" -> PLAYER_OR_ENTITY;
                        case "tp", "teleport" -> parsed.argumentIndex() <= 1 ? PLAYER_OR_ENTITY : Set.of(SuggestionCategory.COORDINATE, SuggestionCategory.DIMENSION);
                        case "ride", "spectate" -> PLAYER_OR_ENTITY;
                        case "tag" -> parsed.argumentIndex() == 0
                                ? PLAYER_OR_ENTITY
                                : parsed.argumentIndex() == 1
                                ? COMMAND_ONLY
                                : Set.of(SuggestionCategory.TAG);
                        default -> profile.fallbackCategories();
                    };
                },
                "admin-family",
                java.util.Map.of("family", AdminCommandFamily.ENTITIES_AND_SUMMON.name())
        );
    }

    private static CommandHintProfile worldAndWeatherProfile() {
        return new CommandHintProfile(
                "world-weather",
                "Mundo, clima y bloques",
                AdminCommandFamily.WORLD_AND_WEATHER,
                Set.of("time", "weather", "setblock", "fill", "clone", "worldborder", "locate", "spreadplayers", "setworldspawn", "spawnpoint"),
                Set.of(),
                List.of(
                        ArgumentHint.freeValue("world_value", "Valor de mundo", WORLD_VALUE),
                        ArgumentHint.freeValue("secondary", "Valor secundario", Set.of(SuggestionCategory.COORDINATE, SuggestionCategory.BLOCK, SuggestionCategory.FREE_VALUE)),
                        ArgumentHint.freeValue("tertiary", "Valor terciario", Set.of(SuggestionCategory.COORDINATE, SuggestionCategory.FREE_VALUE))
                ),
                WORLD_VALUE,
                (parsed, profile) -> {
                    if (parsed == null) {
                        return WORLD_VALUE;
                    }
                    String command = normalizeCommand(parsed.commandName());
                    return switch (command) {
                        case "weather", "time", "worldborder" -> Set.of(SuggestionCategory.FREE_VALUE);
                        case "setblock" -> parsed.argumentIndex() < 3
                                ? Set.of(SuggestionCategory.COORDINATE)
                                : BLOCK_OR_TAG;
                        case "fill", "clone", "spreadplayers", "setworldspawn", "spawnpoint" ->
                                parsed.argumentIndex() < 3 ? Set.of(SuggestionCategory.COORDINATE) : Set.of(SuggestionCategory.FREE_VALUE, SuggestionCategory.BLOCK);
                        case "locate" -> Set.of(SuggestionCategory.FREE_VALUE, SuggestionCategory.DIMENSION);
                        default -> profile.fallbackCategories();
                    };
                },
                "admin-family",
                java.util.Map.of("family", AdminCommandFamily.WORLD_AND_WEATHER.name())
        );
    }

    private static CommandHintProfile permissionsAndModerationProfile() {
        return new CommandHintProfile(
                "permissions-moderation",
                "Permisos, moderation y listas administrativas",
                AdminCommandFamily.PERMISSIONS_AND_MODERATION,
                Set.of("op", "deop", "kick", "ban", "ban-ip", "pardon", "pardon-ip", "whitelist"),
                Set.of("on", "off", "list", "reload", "add", "remove"),
                List.of(
                        ArgumentHint.freeValue("target", "Jugador o ip", Set.of(SuggestionCategory.PLAYER, SuggestionCategory.FREE_VALUE)),
                        ArgumentHint.freeValue("detail", "Subcomando o razon", Set.of(SuggestionCategory.SUBCOMMAND, SuggestionCategory.FREE_VALUE))
                ),
                Set.of(SuggestionCategory.PLAYER, SuggestionCategory.SUBCOMMAND),
                (parsed, profile) -> {
                    if (parsed == null) {
                        return profile.fallbackCategories();
                    }
                    String command = normalizeCommand(parsed.commandName());
                    return switch (command) {
                        case "op", "deop", "kick", "ban", "pardon" -> PLAYER_ONLY;
                        case "ban-ip", "pardon-ip" -> Set.of(SuggestionCategory.FREE_VALUE);
                        case "whitelist" -> parsed.argumentIndex() == 0 ? COMMAND_ONLY : PLAYER_ONLY;
                        default -> profile.fallbackCategories();
                    };
                },
                "admin-family",
                java.util.Map.of("family", AdminCommandFamily.PERMISSIONS_AND_MODERATION.name())
        );
    }

    private static CommandHintProfile configurationAndRulesProfile() {
        return new CommandHintProfile(
                "configuration-rules",
                "Configuracion, reglas y estado del servidor",
                AdminCommandFamily.CONFIGURATION_AND_RULES,
                Set.of("gamemode", "gamerule", "difficulty", "effect", "scoreboard", "team", "bossbar", "function", "reload", "save-all", "stop"),
                Set.of("add", "remove", "list", "give", "clear", "set", "modify"),
                List.of(
                        ArgumentHint.freeValue("rule_or_value", "Regla o valor", RULE_VALUE),
                        ArgumentHint.freeValue("target_or_effect", "Jugador, efecto o valor", Set.of(SuggestionCategory.PLAYER, SuggestionCategory.EFFECT, SuggestionCategory.FREE_VALUE)),
                        ArgumentHint.freeValue("value", "Valor libre", Set.of(SuggestionCategory.FREE_VALUE))
                ),
                RULE_VALUE,
                (parsed, profile) -> {
                    if (parsed == null) {
                        return profile.fallbackCategories();
                    }
                    String command = normalizeCommand(parsed.commandName());
                    return switch (command) {
                        case "gamemode" -> parsed.argumentIndex() == 0
                                ? Set.of(SuggestionCategory.FREE_VALUE)
                                : PLAYER_ONLY;
                        case "gamerule" -> RULE_VALUE;
                        case "difficulty" -> Set.of(SuggestionCategory.FREE_VALUE);
                        case "effect" -> resolveEffectCommand(parsed);
                        case "scoreboard", "team", "bossbar" -> COMMAND_ONLY;
                        case "function", "reload", "save-all", "stop" -> Set.of(SuggestionCategory.FREE_VALUE);
                        default -> profile.fallbackCategories();
                    };
                },
                "admin-family",
                java.util.Map.of("family", AdminCommandFamily.CONFIGURATION_AND_RULES.name())
        );
    }

    private static CommandHintProfile pluginAndModProfile() {
        return new CommandHintProfile(
                "plugin-mod-commands",
                "Rama extensible para comandos de plugins y mods",
                AdminCommandFamily.MODDED_AND_PLUGIN_COMMANDS,
                Set.of("plugin", "mod"),
                Set.of("help", "reload", "config", "debug"),
                List.of(
                        ArgumentHint.freeValue("plugin_or_mod_subcommand", "Subcomando", COMMAND_ONLY),
                        ArgumentHint.freeValue("value", "Valor libre", Set.of(SuggestionCategory.FREE_VALUE, SuggestionCategory.PLAYER, SuggestionCategory.ITEM, SuggestionCategory.ENTITY))
                ),
                Set.of(SuggestionCategory.COMMAND, SuggestionCategory.SUBCOMMAND, SuggestionCategory.FREE_VALUE),
                (parsed, profile) -> parsed == null || parsed.argumentIndex() <= 0
                        ? COMMAND_ONLY
                        : Set.of(SuggestionCategory.FREE_VALUE, SuggestionCategory.PLAYER, SuggestionCategory.ITEM, SuggestionCategory.ENTITY),
                "admin-family",
                java.util.Map.of("family", AdminCommandFamily.MODDED_AND_PLUGIN_COMMANDS.name())
        );
    }

    private static Set<SuggestionCategory> resolveItemCommand(ParsedCommandLine parsed) {
        if (parsed.argumentIndex() == 0) {
            return Set.of(SuggestionCategory.SUBCOMMAND);
        }
        String subcommand = tokenAt(parsed, 1);
        if ("replace".equals(subcommand) || "modify".equals(subcommand)) {
            return parsed.argumentIndex() <= 2
                    ? PLAYER_OR_ENTITY
                    : ITEM_OR_TAG;
        }
        return ITEM_OR_TAG;
    }

    private static Set<SuggestionCategory> resolveEffectCommand(ParsedCommandLine parsed) {
        if (parsed.argumentIndex() == 0) {
            return COMMAND_ONLY;
        }
        String subcommand = tokenAt(parsed, 1);
        if ("give".equals(subcommand) || "clear".equals(subcommand)) {
            return parsed.argumentIndex() == 1 ? PLAYER_ONLY : EFFECT_VALUE;
        }
        return Set.of(SuggestionCategory.SUBCOMMAND, SuggestionCategory.PLAYER, SuggestionCategory.EFFECT);
    }

    private static String tokenAt(ParsedCommandLine parsed, int tokenIndex) {
        if (parsed == null || parsed.tokens().size() <= tokenIndex) {
            return "";
        }
        CommandToken token = parsed.tokens().get(tokenIndex);
        return token == null ? "" : token.normalizedText().toLowerCase(Locale.ROOT);
    }

    private static String normalizeCommand(String commandName) {
        return commandName == null ? "" : commandName.trim().toLowerCase(Locale.ROOT);
    }
}
