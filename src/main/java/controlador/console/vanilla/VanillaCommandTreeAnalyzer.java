package controlador.console.vanilla;

import controlador.console.CommandToken;
import controlador.console.ParsedCommandLine;
import controlador.console.Suggestion;
import controlador.console.SuggestionCategory;
import controlador.console.SuggestionMatch;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Analiza el arbol real de commands.json vanilla para inferir el contexto esperado.
 */
public final class VanillaCommandTreeAnalyzer {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private final Map<String, JsonNode> commandTreesByVersion = new ConcurrentHashMap<>();
    private final Path reportsDirectory = Path.of(
            System.getProperty("user.home"),
            ".easy-mc-server",
            "cache",
            "console",
            "vanilla",
            "reports"
    );

    public Analysis analyze(String version, ParsedCommandLine parsedLine, String serverType) {
        if (parsedLine == null || version == null || version.isBlank()) {
            return Analysis.empty();
        }

        Optional<JsonNode> rootOptional = loadTree(version);
        if (rootOptional.isEmpty()) {
            return Analysis.empty();
        }

        JsonNode root = rootOptional.get();
        List<TraversalState> states = List.of(new TraversalState(root, 0));
        List<String> completedTokens = completedTokens(parsedLine);

        for (String token : completedTokens) {
            states = descend(states, token);
            if (states.isEmpty()) {
                return Analysis.empty();
            }
        }

        String activeToken = parsedLine.activeToken() == null ? "" : parsedLine.activeToken().trim().toLowerCase(Locale.ROOT);
        List<TraversalState> partialStates = states.stream()
                .filter(state -> state.remainingSegments() > 0)
                .toList();
        if (!partialStates.isEmpty()) {
            LinkedHashSet<SuggestionCategory> categories = new LinkedHashSet<>();
            for (TraversalState state : partialStates) {
                categories.addAll(mapArgumentNode(state.node()));
            }
            return categories.isEmpty()
                    ? Analysis.empty()
                    : new Analysis(Set.copyOf(categories), List.of(), true);
        }
        boolean rootLevel = completedTokens.isEmpty();
        List<JsonNode> nextNodes = childrenOf(states);
        if (nextNodes.isEmpty()) {
            return Analysis.empty();
        }

        LinkedHashSet<SuggestionCategory> categories = new LinkedHashSet<>();
        List<Suggestion> literalSuggestions = new ArrayList<>();
        for (JsonNode node : nextNodes) {
            String nodeType = node.path("type").asText("");
            String token = node.path("_token").asText("");
            if ("literal".equalsIgnoreCase(nodeType)) {
                if (activeToken.isBlank() || token.toLowerCase(Locale.ROOT).startsWith(activeToken)) {
                    categories.add(rootLevel ? SuggestionCategory.COMMAND : SuggestionCategory.SUBCOMMAND);
                    literalSuggestions.add(new Suggestion(
                            "vanilla-grammar:" + version + ":" + token,
                            token,
                            token,
                            "",
                            rootLevel ? SuggestionCategory.COMMAND : SuggestionCategory.SUBCOMMAND,
                            version,
                            "VANILLA",
                            "vanilla-grammar",
                            rootLevel ? 240 : 230,
                            SuggestionMatch.none(),
                            true,
                            !node.path("executable").asBoolean(false) && !node.path("children").isObject(),
                            Map.of("grammarSource", "commands.json")
                    ));
                }
                continue;
            }
            if ("argument".equalsIgnoreCase(nodeType)) {
                categories.addAll(mapArgumentNode(node));
            }
        }

        if (categories.isEmpty() && literalSuggestions.isEmpty()) {
            return Analysis.empty();
        }
        return new Analysis(Set.copyOf(categories), List.copyOf(literalSuggestions), true);
    }

    public boolean isKnownRootCommand(String version, String commandName) {
        String normalizedVersion = normalize(version);
        String normalizedCommand = normalize(commandName);
        if (normalizedVersion.isBlank() || normalizedCommand.isBlank()) {
            return false;
        }
        Optional<JsonNode> rootOptional = loadTree(normalizedVersion);
        if (rootOptional.isEmpty()) {
            return false;
        }
        JsonNode children = rootOptional.get().path("children");
        JsonNode commandNode = children.get(normalizedCommand);
        return commandNode != null
                && commandNode.isObject()
                && "literal".equalsIgnoreCase(commandNode.path("type").asText(""));
    }

    private Optional<JsonNode> loadTree(String version) {
        JsonNode cached = commandTreesByVersion.get(version);
        if (cached != null) {
            return Optional.of(cached);
        }
        Path commandsPath = reportsDirectory.resolve(version).resolve("work").resolve("generated").resolve("reports").resolve("commands.json");
        if (!Files.isRegularFile(commandsPath)) {
            return Optional.empty();
        }
        try (InputStream in = Files.newInputStream(commandsPath)) {
            JsonNode root = objectMapper.readTree(in);
            annotateTokens(root);
            commandTreesByVersion.put(version, root);
            return Optional.of(root);
        } catch (Exception ex) {
            return Optional.empty();
        }
    }

    private void annotateTokens(JsonNode node) {
        if (node == null || !node.isObject()) {
            return;
        }
        JsonNode children = node.path("children");
        if (!children.isObject()) {
            return;
        }
        for (Map.Entry<String, JsonNode> entry : children.properties()) {
            JsonNode child = entry.getValue();
            if (child != null && child.isObject()) {
                ((tools.jackson.databind.node.ObjectNode) child).put("_token", entry.getKey());
                annotateTokens(child);
            }
        }
    }

    private List<String> completedTokens(ParsedCommandLine parsedLine) {
        if (parsedLine.activeTokenIndex() < 0) {
            return parsedLine.tokens().stream()
                    .map(CommandToken::normalizedText)
                    .map(this::normalize)
                    .filter(token -> !token.isBlank())
                    .toList();
        }
        List<String> completed = new ArrayList<>();
        for (int index = 0; index < parsedLine.activeTokenIndex(); index++) {
            completed.add(normalize(parsedLine.tokens().get(index).normalizedText()));
        }
        return completed;
    }

    private List<TraversalState> descend(List<TraversalState> states, String token) {
        List<TraversalState> next = new ArrayList<>();
        for (TraversalState state : states) {
            if (state.remainingSegments() > 0) {
                next.add(new TraversalState(state.node(), state.remainingSegments() - 1));
                continue;
            }
            JsonNode children = state.node().path("children");
            if (!children.isObject()) {
                continue;
            }

            JsonNode literal = children.get(token);
            if (literal != null && literal.isObject() && "literal".equalsIgnoreCase(literal.path("type").asText(""))) {
                next.add(new TraversalState(literal, 0));
                continue;
            }

            for (Map.Entry<String, JsonNode> entry : children.properties()) {
                JsonNode child = entry.getValue();
                if (child == null || !child.isObject()) {
                    continue;
                }
                if ("argument".equalsIgnoreCase(child.path("type").asText(""))) {
                    int width = argumentWidth(child);
                    next.add(new TraversalState(child, Math.max(0, width - 1)));
                }
            }
        }
        return next;
    }

    private List<JsonNode> childrenOf(List<TraversalState> candidates) {
        List<JsonNode> children = new ArrayList<>();
        for (TraversalState candidate : candidates) {
            JsonNode childNode = candidate.node().path("children");
            if (!childNode.isObject()) {
                continue;
            }
            for (Map.Entry<String, JsonNode> entry : childNode.properties()) {
                JsonNode child = entry.getValue();
                if (child != null && child.isObject()) {
                    children.add(child);
                }
            }
        }
        return children;
    }

    private int argumentWidth(JsonNode node) {
        String parser = node.path("parser").asText("").toLowerCase(Locale.ROOT);
        return switch (parser) {
            case "minecraft:vec3", "minecraft:block_pos", "minecraft:column_pos", "minecraft:rotation" -> 3;
            case "minecraft:vec2" -> 2;
            default -> 1;
        };
    }

    private Set<SuggestionCategory> mapArgumentNode(JsonNode node) {
        String parser = node.path("parser").asText("").toLowerCase(Locale.ROOT);
        JsonNode properties = node.path("properties");

        if ("minecraft:entity".equals(parser)) {
            String entityType = properties.path("type").asText("");
            return "players".equalsIgnoreCase(entityType)
                    ? Set.of(SuggestionCategory.PLAYER)
                    : Set.of(SuggestionCategory.ENTITY, SuggestionCategory.PLAYER);
        }
        if ("minecraft:game_profile".equals(parser)) {
            return Set.of(SuggestionCategory.PLAYER);
        }
        if ("minecraft:item_stack".equals(parser) || "minecraft:item_predicate".equals(parser)) {
            return Set.of(SuggestionCategory.ITEM, SuggestionCategory.TAG);
        }
        if ("minecraft:block_state".equals(parser) || "minecraft:block_predicate".equals(parser)) {
            return Set.of(SuggestionCategory.BLOCK, SuggestionCategory.TAG);
        }
        if ("minecraft:resource".equals(parser) || "minecraft:resource_or_tag".equals(parser)) {
            return mapRegistry(properties.path("registry").asText(""));
        }
        if ("minecraft:vec3".equals(parser)
                || "minecraft:vec2".equals(parser)
                || "minecraft:block_pos".equals(parser)
                || "minecraft:column_pos".equals(parser)
                || "minecraft:rotation".equals(parser)) {
            return Set.of(SuggestionCategory.COORDINATE);
        }
        if ("minecraft:message".equals(parser)
                || "minecraft:nbt_compound_tag".equals(parser)
                || "minecraft:nbt_tag".equals(parser)) {
            return Set.of(SuggestionCategory.FREE_VALUE);
        }
        if (parser.startsWith("brigadier:")) {
            if (parser.endsWith("integer") || parser.endsWith("long") || parser.endsWith("float") || parser.endsWith("double")) {
                return Set.of(SuggestionCategory.NUMBER);
            }
            if (parser.endsWith("bool")) {
                return Set.of(SuggestionCategory.FREE_VALUE);
            }
            if (parser.endsWith("string")) {
                String type = properties.path("type").asText("");
                return "greedy".equalsIgnoreCase(type)
                        ? Set.of(SuggestionCategory.FREE_VALUE)
                        : Set.of(SuggestionCategory.FREE_VALUE, SuggestionCategory.SUBCOMMAND);
            }
        }
        return Set.of(SuggestionCategory.FREE_VALUE);
    }

    private Set<SuggestionCategory> mapRegistry(String registryName) {
        String normalized = normalize(registryName);
        return switch (normalized) {
            case "minecraft:item" -> Set.of(SuggestionCategory.ITEM, SuggestionCategory.TAG);
            case "minecraft:block" -> Set.of(SuggestionCategory.BLOCK, SuggestionCategory.TAG);
            case "minecraft:entity_type" -> Set.of(SuggestionCategory.ENTITY, SuggestionCategory.TAG);
            case "minecraft:mob_effect" -> Set.of(SuggestionCategory.EFFECT);
            case "minecraft:enchantment" -> Set.of(SuggestionCategory.ENCHANTMENT);
            case "minecraft:dimension_type", "minecraft:dimension" -> Set.of(SuggestionCategory.DIMENSION);
            default -> Set.of(SuggestionCategory.FREE_VALUE);
        };
    }

    private String normalize(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }

    public record Analysis(
            Set<SuggestionCategory> expectedCategories,
            List<Suggestion> literalSuggestions,
            boolean authoritative
    ) {
        public static Analysis empty() {
            return new Analysis(Set.of(), List.of(), false);
        }
    }

    private record TraversalState(JsonNode node, int remainingSegments) {
    }
}
