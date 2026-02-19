package Vista;

public class MotdRenderUtil {
    private MotdRenderUtil() {}

    public static String toHtml(String motdRaw) {
        if (motdRaw == null) motdRaw = "";
        motdRaw = motdRaw.replace('&', '\u00A7');

        StringBuilder out = new StringBuilder();
        out.append("<html><div style='white-space:pre;line-height:1.2;'>");

        class RenderState {
            String color = null;
            boolean bold = false;
            boolean italic = false;
            boolean under = false;
            boolean strike = false;
            final StringBuilder buf = new StringBuilder();

            void flush() {
                if (buf.isEmpty()) return;
                out.append("<span style='");
                if (color != null) out.append("color:").append(color).append(";");
                if (bold) out.append("font-weight:bold;");
                if (italic) out.append("font-style:italic;");
                if (under || strike) {
                    out.append("text-decoration:");
                    if (under) out.append(" underline");
                    if (strike) out.append(" line-through");
                    out.append(";");
                }
                out.append("'>");
                out.append(escapeHtml(buf.toString()));
                out.append("</span>");
                buf.setLength(0);
            }
        }

        RenderState st = new RenderState();

        for (int i = 0; i < motdRaw.length(); i++) {
            char c = motdRaw.charAt(i);
            if (c == '\u00A7' && i + 1 < motdRaw.length()) {
                char code = Character.toLowerCase(motdRaw.charAt(i + 1));
                st.flush();
                switch (code) {
                    case '0' -> st.color = "#000000";
                    case '1' -> st.color = "#0000AA";
                    case '2' -> st.color = "#00AA00";
                    case '3' -> st.color = "#00AAAA";
                    case '4' -> st.color = "#AA0000";
                    case '5' -> st.color = "#AA00AA";
                    case '6' -> st.color = "#FFAA00";
                    case '7' -> st.color = "#AAAAAA";
                    case '8' -> st.color = "#555555";
                    case '9' -> st.color = "#5555FF";
                    case 'a' -> st.color = "#55FF55";
                    case 'b' -> st.color = "#55FFFF";
                    case 'c' -> st.color = "#FF5555";
                    case 'd' -> st.color = "#FF55FF";
                    case 'e' -> st.color = "#FFFF55";
                    case 'f' -> st.color = "#FFFFFF";
                    case 'l' -> st.bold = true;
                    case 'o' -> st.italic = true;
                    case 'n' -> st.under = true;
                    case 'm' -> st.strike = true;
                    case 'r' -> {
                        st.color = null;
                        st.bold = false;
                        st.italic = false;
                        st.under = false;
                        st.strike = false;
                    }
                    default -> {
                        // 'k' ofuscado: no intentamos emularlo aquí
                    }
                }
                i++;
                continue;
            }
            if (c == '\n') {
                st.flush();
                out.append("<br/>");
                continue;
            }
            st.buf.append(c);
        }
        st.flush();
        out.append("</div></html>");
        return out.toString();
    }

    public static String stripCodes(String s) {
        if (s == null) return null;
        return s.replaceAll("(?i)(§.|&.)", "");
    }

    private static String escapeHtml(String s) {
        return s.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }
}
