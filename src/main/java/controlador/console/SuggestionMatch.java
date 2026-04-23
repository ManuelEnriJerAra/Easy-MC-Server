package controlador.console;

import java.util.Objects;

/**
 * Describe cómo encaja una sugerencia concreta con la entrada actual del usuario.
 */
public record SuggestionMatch(
        MatchKind kind,
        String queryText,
        String matchedText,
        String completionText,
        int score,
        boolean partialAcceptanceAllowed
) {

    public SuggestionMatch {
        kind = Objects.requireNonNullElse(kind, MatchKind.NONE);
        queryText = normalize(queryText);
        matchedText = normalize(matchedText);
        completionText = normalize(completionText);
    }

    public static SuggestionMatch none() {
        return new SuggestionMatch(MatchKind.NONE, "", "", "", 0, false);
    }

    public static SuggestionMatch exact(String queryText, String matchedText, int score) {
        return new SuggestionMatch(MatchKind.EXACT, queryText, matchedText, "", score, false);
    }

    public static SuggestionMatch prefix(String queryText, String matchedText, String completionText, int score) {
        return new SuggestionMatch(MatchKind.PREFIX, queryText, matchedText, completionText, score, true);
    }

    public static SuggestionMatch partial(String queryText, String matchedText, String completionText, int score) {
        return new SuggestionMatch(MatchKind.PARTIAL_COMMON_PREFIX, queryText, matchedText, completionText, score, true);
    }

    public boolean isMatch() {
        return kind != MatchKind.NONE;
    }

    public enum MatchKind {
        NONE,
        EXACT,
        PREFIX,
        PARTIAL_COMMON_PREFIX,
        CONTAINS,
        FUZZY
    }

    private static String normalize(String value) {
        return value == null ? "" : value;
    }
}
