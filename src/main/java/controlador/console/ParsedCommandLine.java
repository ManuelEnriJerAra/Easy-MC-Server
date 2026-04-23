package controlador.console;

import java.util.List;

/**
 * Resultado del parseo de una línea de comandos con conocimiento del cursor.
 */
public record ParsedCommandLine(
        String rawText,
        int caretOffset,
        List<CommandToken> tokens,
        int activeTokenIndex,
        String activeToken,
        String activeTokenRaw,
        int activeTokenStart,
        int activeTokenEnd,
        boolean trailingWhitespace,
        boolean unclosedQuote,
        boolean slashPrefixed,
        String commandName,
        int argumentIndex
) {

    public ParsedCommandLine {
        rawText = rawText == null ? "" : rawText;
        tokens = List.copyOf(tokens == null ? List.of() : tokens);
        activeToken = activeToken == null ? "" : activeToken;
        activeTokenRaw = activeTokenRaw == null ? "" : activeTokenRaw;
        activeTokenStart = Math.max(0, activeTokenStart);
        activeTokenEnd = Math.max(activeTokenStart, activeTokenEnd);
        commandName = commandName == null ? "" : commandName;
    }

    public boolean hasCommand() {
        return !commandName.isBlank();
    }

    public int tokenCountBeforeActive() {
        if (activeTokenIndex < 0) {
            return tokens.size();
        }
        return activeTokenIndex;
    }
}
