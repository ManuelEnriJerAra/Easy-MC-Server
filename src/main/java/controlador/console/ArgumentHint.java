package controlador.console;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Describe qué tipo de argumento espera una rama de la gramática.
 */
public record ArgumentHint(
        String name,
        String description,
        Set<SuggestionCategory> acceptedCategories,
        ValueMode valueMode,
        boolean required,
        boolean repeatable,
        boolean quotedTextAllowed,
        List<String> examples
) {

    public ArgumentHint {
        name = normalize(name);
        description = normalize(description);
        acceptedCategories = immutableOrderedSet(acceptedCategories);
        valueMode = Objects.requireNonNullElse(valueMode, ValueMode.STRICT);
        examples = List.copyOf(examples == null ? List.of() : examples);
    }

    public static ArgumentHint literal(String name, String description) {
        return new ArgumentHint(
                name,
                description,
                Set.of(SuggestionCategory.TEXT),
                ValueMode.STRICT,
                true,
                false,
                false,
                List.of()
        );
    }

    public static ArgumentHint freeValue(String name, String description, Set<SuggestionCategory> acceptedCategories) {
        return new ArgumentHint(
                name,
                description,
                acceptedCategories,
                ValueMode.ALLOW_FREE_TEXT,
                true,
                false,
                true,
                List.of()
        );
    }

    public boolean accepts(SuggestionCategory category) {
        return category != null && acceptedCategories.contains(category);
    }

    public enum ValueMode {
        STRICT,
        ALLOW_FREE_TEXT,
        ALLOW_MIXED
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
}
