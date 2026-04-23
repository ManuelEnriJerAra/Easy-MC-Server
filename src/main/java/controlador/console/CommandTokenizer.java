package controlador.console;

import java.util.ArrayList;
import java.util.List;

/**
 * Tokeniza una línea de comandos respetando espacios, comillas y posición del cursor.
 */
public final class CommandTokenizer {

    public ParsedCommandLine parse(String text, int caretOffset) {
        String safeText = text == null ? "" : text;
        int safeCaret = Math.max(0, Math.min(caretOffset, safeText.length()));

        List<CommandToken> tokens = new ArrayList<>();
        StringBuilder current = new StringBuilder();
        int tokenStart = -1;
        boolean inQuotes = false;
        boolean tokenQuoted = false;

        for (int i = 0; i < safeText.length(); i++) {
            char ch = safeText.charAt(i);
            if (ch == '"') {
                if (tokenStart < 0) {
                    tokenStart = i;
                    tokenQuoted = true;
                }
                inQuotes = !inQuotes;
                continue;
            }

            if (Character.isWhitespace(ch) && !inQuotes) {
                if (tokenStart >= 0) {
                    tokens.add(createToken(current.toString(), tokenStart, i, tokenQuoted));
                    current.setLength(0);
                    tokenStart = -1;
                    tokenQuoted = false;
                }
                continue;
            }

            if (tokenStart < 0) {
                tokenStart = i;
            }
            current.append(ch);
        }

        if (tokenStart >= 0) {
            tokens.add(createToken(current.toString(), tokenStart, safeText.length(), tokenQuoted));
        }

        boolean trailingWhitespace = !safeText.isEmpty() && Character.isWhitespace(safeText.charAt(safeText.length() - 1));
        int activeTokenIndex = resolveActiveTokenIndex(tokens, safeCaret, trailingWhitespace, safeText.length());
        CommandToken activeToken = activeTokenIndex >= 0 ? tokens.get(activeTokenIndex) : null;

        boolean slashPrefixed = !tokens.isEmpty() && tokens.get(0).startsWithSlash();
        String commandName = tokens.isEmpty() ? "" : tokens.get(0).normalizedText();
        int argumentIndex = computeArgumentIndex(activeTokenIndex, tokens, trailingWhitespace);

        String activeNormalized = activeToken == null ? "" : activeToken.normalizedText();
        String activeRaw = activeToken == null ? "" : activeToken.text();
        int activeStart = activeToken == null ? safeCaret : activeToken.startOffset();
        int activeEnd = activeToken == null ? safeCaret : activeToken.endOffset();

        return new ParsedCommandLine(
                safeText,
                safeCaret,
                tokens,
                activeTokenIndex,
                activeNormalized,
                activeRaw,
                activeStart,
                activeEnd,
                trailingWhitespace,
                inQuotes,
                slashPrefixed,
                commandName,
                argumentIndex
        );
    }

    private CommandToken createToken(String text, int start, int end, boolean quoted) {
        return new CommandToken(text, start, end, quoted, text.startsWith("/"));
    }

    private int resolveActiveTokenIndex(List<CommandToken> tokens, int caretOffset, boolean trailingWhitespace, int textLength) {
        if (tokens.isEmpty()) {
            return -1;
        }
        if (trailingWhitespace && caretOffset >= textLength) {
            return -1;
        }
        for (int i = 0; i < tokens.size(); i++) {
            if (tokens.get(i).containsCaret(caretOffset)) {
                return i;
            }
        }
        return -1;
    }

    private int computeArgumentIndex(int activeTokenIndex, List<CommandToken> tokens, boolean trailingWhitespace) {
        if (tokens.isEmpty()) {
            return 0;
        }
        if (activeTokenIndex < 0) {
            return trailingWhitespace ? Math.max(0, tokens.size() - 1) : 0;
        }
        return Math.max(0, activeTokenIndex - 1);
    }
}
