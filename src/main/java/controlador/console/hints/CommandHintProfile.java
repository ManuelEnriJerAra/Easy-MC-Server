package controlador.console.hints;

import controlador.console.ArgumentHint;
import controlador.console.ParsedCommandLine;
import controlador.console.SuggestionCategory;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * Perfil reutilizable de hints para una familia/rama de comandos.
 */
public record CommandHintProfile(
        String id,
        String description,
        AdminCommandFamily family,
        Set<String> commands,
        Set<String> subcommands,
        List<ArgumentHint> argumentHints,
        Set<SuggestionCategory> fallbackCategories,
        CommandHintResolver resolver,
        String source,
        Map<String, String> metadata
) {

    public CommandHintProfile {
        id = normalize(id);
        description = normalize(description);
        family = Objects.requireNonNullElse(family, AdminCommandFamily.GENERAL);
        commands = immutableOrderedSet(commands);
        subcommands = immutableOrderedSet(subcommands);
        argumentHints = List.copyOf(argumentHints == null ? List.of() : argumentHints);
        fallbackCategories = immutableOrderedSet(fallbackCategories);
        resolver = Objects.requireNonNullElseGet(resolver, CommandHintProfiles::defaultResolver);
        source = normalize(source);
        metadata = immutableOrderedMap(metadata);
    }

    public boolean supportsCommand(String commandName) {
        if (commandName == null || commandName.isBlank()) {
            return false;
        }
        String normalized = normalize(commandName).toLowerCase();
        return commands.stream().anyMatch(command -> command.equalsIgnoreCase(normalized));
    }

    public Set<SuggestionCategory> expectedCategories(ParsedCommandLine parsedCommandLine) {
        return resolver.resolve(parsedCommandLine, this);
    }

    public ArgumentHint argumentHintAt(int index) {
        if (index < 0 || argumentHints.isEmpty()) {
            return null;
        }
        if (index < argumentHints.size()) {
            return argumentHints.get(index);
        }
        ArgumentHint last = argumentHints.get(argumentHints.size() - 1);
        return last.repeatable() ? last : null;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.trim();
    }

    private static <T> Set<T> immutableOrderedSet(Set<T> source) {
        if (source == null || source.isEmpty()) {
            return Set.of();
        }
        return Set.copyOf(new LinkedHashSet<>(source));
    }

    private static Map<String, String> immutableOrderedMap(Map<String, String> source) {
        if (source == null || source.isEmpty()) {
            return Map.of();
        }
        return Map.copyOf(new LinkedHashMap<>(source));
    }
}
