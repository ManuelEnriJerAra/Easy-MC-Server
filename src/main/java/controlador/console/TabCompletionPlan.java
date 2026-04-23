package controlador.console;

/**
 * Acción concreta de autocompletado que podría aplicar la UI al pulsar Tab.
 */
public record TabCompletionPlan(
        Mode mode,
        String replacementText,
        int replaceStartOffset,
        int replaceEndOffset,
        String completionSuffix
) {

    public TabCompletionPlan {
        mode = mode == null ? Mode.NONE : mode;
        replacementText = replacementText == null ? "" : replacementText;
        replaceStartOffset = Math.max(0, replaceStartOffset);
        replaceEndOffset = Math.max(replaceStartOffset, replaceEndOffset);
        completionSuffix = completionSuffix == null ? "" : completionSuffix;
    }

    public static TabCompletionPlan none(int start, int end) {
        return new TabCompletionPlan(Mode.NONE, "", start, end, "");
    }

    public enum Mode {
        NONE,
        APPLY_SELECTION,
        APPLY_COMMON_PREFIX
    }
}
