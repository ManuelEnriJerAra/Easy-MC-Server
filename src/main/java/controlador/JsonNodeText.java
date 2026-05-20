package controlador;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.JsonNodeType;

public final class JsonNodeText {
    private JsonNodeText() {
    }

    public static String text(JsonNode node) {
        return text(node, null);
    }

    public static String text(JsonNode node, String fallback) {
        if (node == null) {
            return fallback;
        }
        return switch (node.getNodeType()) {
            case STRING -> node.stringValue(null);
            case NUMBER, BOOLEAN -> node.toString();
            case NULL, MISSING -> fallback;
            default -> fallback;
        };
    }

    public static boolean isScalarText(JsonNode node) {
        if (node == null) {
            return false;
        }
        return switch (node.getNodeType()) {
            case STRING, NUMBER, BOOLEAN -> true;
            default -> false;
        };
    }
}
