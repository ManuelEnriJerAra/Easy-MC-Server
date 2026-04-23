package controlador.console;

/**
 * Token extraído de una línea de comandos, con posición y contexto de parseo.
 */
public record CommandToken(
        String text,
        int startOffset,
        int endOffset,
        boolean quoted,
        boolean startsWithSlash
) {

    public CommandToken {
        text = text == null ? "" : text;
        startOffset = Math.max(0, startOffset);
        endOffset = Math.max(startOffset, endOffset);
    }

    public boolean containsCaret(int caretOffset) {
        return caretOffset >= startOffset && caretOffset <= endOffset;
    }

    public String normalizedText() {
        if (startsWithSlash && !text.isEmpty()) {
            return text.substring(1);
        }
        return text;
    }
}
