package vista;

import javax.swing.*;
import javax.swing.text.*;
import java.awt.*;
import java.util.Random;

public class MotdEditorDialog {
    private static final char SECTION = '\u00A7';

    public static String show(Component parent, String initial) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, "Editar MOTD", Dialog.ModalityType.APPLICATION_MODAL);

        JTextPane pane = new JTextPane();
        pane.setEditorKit(new ObfuscatedEditorKit());
        pane.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
        pane.setFont(UIManager.getFont("TextField.font"));
        if (pane.getFont() == null) pane.setFont(new Font("Dialog", Font.PLAIN, 14));

        // Limitar a 2 líneas (0 o 1 salto de línea)
        ((AbstractDocument) pane.getDocument()).setDocumentFilter(new TwoLineFilter());

        applyInitial(pane.getStyledDocument(), initial);

        JScrollPane scroll = new JScrollPane(pane);
        scroll.setBorder(BorderFactory.createTitledBorder("MOTD (máx. 2 líneas)"));

        JToolBar bar = new JToolBar();
        bar.setFloatable(false);
        bar.setBorder(BorderFactory.createEmptyBorder(4, 4, 4, 4));
        Font baseBtnFont = UIManager.getFont("Button.font");
        if(baseBtnFont == null) baseBtnFont = new Font("Dialog", Font.PLAIN, 12);

        // Colores (cuadrados)
        addColorButton(bar, "§0", new Color(0x000000), pane);
        addColorButton(bar, "§1", new Color(0x0000AA), pane);
        addColorButton(bar, "§2", new Color(0x00AA00), pane);
        addColorButton(bar, "§3", new Color(0x00AAAA), pane);
        addColorButton(bar, "§4", new Color(0xAA0000), pane);
        addColorButton(bar, "§5", new Color(0xAA00AA), pane);
        addColorButton(bar, "§6", new Color(0xFFAA00), pane);
        addColorButton(bar, "§7", new Color(0xAAAAAA), pane);
        addColorButton(bar, "§8", new Color(0x555555), pane);
        addColorButton(bar, "§9", new Color(0x5555FF), pane); // azul
        addColorButton(bar, "§a", new Color(0x55FF55), pane);
        addColorButton(bar, "§b", new Color(0x55FFFF), pane);
        addColorButton(bar, "§c", new Color(0xFF5555), pane);
        addColorButton(bar, "§d", new Color(0xFF55FF), pane);
        addColorButton(bar, "§e", new Color(0xFFFF55), pane);
        addColorButton(bar, "§f", new Color(0xFFFFFF), pane);

        bar.addSeparator();

        bar.add(makeToggle("B", "Negrita", pane, baseBtnFont.deriveFont(Font.BOLD), a -> StyleConstants.setBold(a, !StyleConstants.isBold(a))));
        bar.add(makeToggle("I", "Cursiva", pane, baseBtnFont.deriveFont(Font.ITALIC), a -> StyleConstants.setItalic(a, !StyleConstants.isItalic(a))));
        bar.add(makeToggle("U", "Subrayado", pane, underlineFont(baseBtnFont), a -> StyleConstants.setUnderline(a, !StyleConstants.isUnderline(a))));
        bar.add(makeToggle("S", "Tachado", pane, strikeFont(baseBtnFont), a -> StyleConstants.setStrikeThrough(a, !StyleConstants.isStrikeThrough(a))));
        bar.add(makeToggle("K", "Ofuscado", pane, new Font("Monospaced", Font.PLAIN, baseBtnFont.getSize()), a -> a.addAttribute(ObfuscatedEditorKit.OBFUSCATED, !isObfuscated(a))));

        bar.addSeparator();

        JButton reset = new JButton("Reset");
        reset.setToolTipText("Reset (§r)");
        reset.setFocusable(false);
        reset.addActionListener(e -> applyResetToSelection(pane));
        bar.add(reset);

        JButton cancel = new JButton("Cancelar");
        JButton ok = new JButton("Guardar");
        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT));
        buttons.add(cancel);
        buttons.add(ok);

        final String[] result = new String[1];
        final Timer[] obfTimerRef = new Timer[1];
        cancel.addActionListener(e -> {
            result[0] = null;
            if(obfTimerRef[0] != null) obfTimerRef[0].stop();
            dialog.dispose();
        });
        ok.addActionListener(e -> {
            result[0] = toMotdWithCodes(pane.getStyledDocument());
            if(obfTimerRef[0] != null) obfTimerRef[0].stop();
            dialog.dispose();
        });

        JPanel content = new JPanel(new BorderLayout(10, 10));
        content.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        content.add(bar, BorderLayout.NORTH);
        content.add(scroll, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.setSize(720, 260);
        dialog.setLocationRelativeTo(parent);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        // Animar ofuscado (repintado periódico)
        Timer obfTimer = new Timer(140, e -> pane.repaint());
        obfTimerRef[0] = obfTimer;
        obfTimerRef[0].start();
        dialog.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosed(java.awt.event.WindowEvent e) {
                if(obfTimerRef[0] != null) obfTimerRef[0].stop();
            }

            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                if(obfTimerRef[0] != null) obfTimerRef[0].stop();
            }
        });

        SwingUtilities.invokeLater(() -> {
            pane.requestFocusInWindow();
            pane.setCaretPosition(pane.getDocument().getLength());
        });

        dialog.setVisible(true);
        return result[0];
    }

    private static void addColorButton(JToolBar bar, String code, Color color, JTextPane pane) {
        JButton b = new JButton();
        b.setFocusable(false);
        b.setPreferredSize(new Dimension(18, 18));
        b.setMinimumSize(new Dimension(18, 18));
        b.setMaximumSize(new Dimension(18, 18));
        b.setBackground(color);
        b.setOpaque(true);
        b.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY, 1, true));
        b.setToolTipText(code);
        b.addActionListener(e -> applyColorToSelection(pane, color));
        bar.add(b);
    }

    private interface AttrMutator {
        void mutate(MutableAttributeSet attrs);
    }

    private static JButton makeToggle(String text, String tooltip, JTextPane pane, Font font, AttrMutator mutator) {
        JButton b = new JButton(text);
        b.setFocusable(false);
        b.setToolTipText(tooltip);
        b.setMargin(new Insets(2, 6, 2, 6));
        if(font != null) b.setFont(font);
        b.addActionListener(e -> toggleAttr(pane, mutator));
        return b;
    }

    private static Font underlineFont(Font base){
        if(base == null) return null;
        java.util.Map<java.awt.font.TextAttribute, Object> attrs = new java.util.HashMap<>(base.getAttributes());
        attrs.put(java.awt.font.TextAttribute.UNDERLINE, java.awt.font.TextAttribute.UNDERLINE_ON);
        return base.deriveFont(attrs);
    }

    private static Font strikeFont(Font base){
        if(base == null) return null;
        java.util.Map<java.awt.font.TextAttribute, Object> attrs = new java.util.HashMap<>(base.getAttributes());
        attrs.put(java.awt.font.TextAttribute.STRIKETHROUGH, java.awt.font.TextAttribute.STRIKETHROUGH_ON);
        return base.deriveFont(attrs);
    }

    private static void toggleAttr(JTextPane pane, AttrMutator mutator) {
        StyledDocument doc = pane.getStyledDocument();
        int start = pane.getSelectionStart();
        int end = pane.getSelectionEnd();
        if (end < start) { int t = start; start = end; end = t; }
        if (start == end) {
            MutableAttributeSet current = new SimpleAttributeSet(pane.getCharacterAttributes());
            mutator.mutate(current);
            pane.setCharacterAttributes(current, true);
            return;
        }
        for (int pos = start; pos < end; pos++) {
            Element el = doc.getCharacterElement(pos);
            MutableAttributeSet a = new SimpleAttributeSet(el.getAttributes());
            mutator.mutate(a);
            doc.setCharacterAttributes(pos, 1, a, true);
        }
    }

    private static void applyColorToSelection(JTextPane pane, Color color) {
        StyledDocument doc = pane.getStyledDocument();
        int start = pane.getSelectionStart();
        int end = pane.getSelectionEnd();
        if (end < start) { int t = start; start = end; end = t; }
        if (start == end) {
            MutableAttributeSet current = new SimpleAttributeSet(pane.getCharacterAttributes());
            StyleConstants.setForeground(current, color);
            pane.setCharacterAttributes(current, true);
            return;
        }
        for (int pos = start; pos < end; pos++) {
            Element el = doc.getCharacterElement(pos);
            MutableAttributeSet a = new SimpleAttributeSet(el.getAttributes());
            StyleConstants.setForeground(a, color);
            doc.setCharacterAttributes(pos, 1, a, true);
        }
    }

    private static void applyResetToSelection(JTextPane pane) {
        StyledDocument doc = pane.getStyledDocument();
        int start = pane.getSelectionStart();
        int end = pane.getSelectionEnd();
        if (end < start) { int t = start; start = end; end = t; }

        MutableAttributeSet reset = new SimpleAttributeSet();
        StyleConstants.setBold(reset, false);
        StyleConstants.setItalic(reset, false);
        StyleConstants.setUnderline(reset, false);
        StyleConstants.setStrikeThrough(reset, false);
        reset.addAttribute(ObfuscatedEditorKit.OBFUSCATED, false);
        StyleConstants.setForeground(reset, UIManager.getColor("Label.foreground"));

        if (start == end) {
            pane.setCharacterAttributes(reset, true);
            return;
        }
        doc.setCharacterAttributes(start, end - start, reset, true);
    }

    private static boolean isObfuscated(AttributeSet a) {
        Object v = a.getAttribute(ObfuscatedEditorKit.OBFUSCATED);
        return v instanceof Boolean b && b;
    }

    private static void applyInitial(StyledDocument doc, String initial) {
        try {
            doc.remove(0, doc.getLength());
        } catch (BadLocationException ignored) {
        }

        String raw = initial == null ? "" : initial.replace('&', SECTION);

        MutableAttributeSet current = new SimpleAttributeSet();
        StyleConstants.setForeground(current, UIManager.getColor("Label.foreground"));

        StringBuilder text = new StringBuilder();

        Runnable flush = () -> {
            if (text.isEmpty()) return;
            try {
                doc.insertString(doc.getLength(), text.toString(), current);
            } catch (BadLocationException ignored) {
            }
            text.setLength(0);
        };

        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (c == SECTION && i + 1 < raw.length()) {
                char code = Character.toLowerCase(raw.charAt(i + 1));
                flush.run();
                applyCodeToAttrs(current, code);
                i++;
                continue;
            }
            // máximo 2 líneas: solo permite el primer '\n'
            if (c == '\n') {
                if (doc.getLength() == 0 || doc.toString().indexOf('\n') < 0) {
                    text.append('\n');
                }
                continue;
            }
            text.append(c);
        }
        flush.run();
    }

    private static void applyCodeToAttrs(MutableAttributeSet a, char code) {
        switch (code) {
            case '0' -> StyleConstants.setForeground(a, new Color(0x000000));
            case '1' -> StyleConstants.setForeground(a, new Color(0x0000AA));
            case '2' -> StyleConstants.setForeground(a, new Color(0x00AA00));
            case '3' -> StyleConstants.setForeground(a, new Color(0x00AAAA));
            case '4' -> StyleConstants.setForeground(a, new Color(0xAA0000));
            case '5' -> StyleConstants.setForeground(a, new Color(0xAA00AA));
            case '6' -> StyleConstants.setForeground(a, new Color(0xFFAA00));
            case '7' -> StyleConstants.setForeground(a, new Color(0xAAAAAA));
            case '8' -> StyleConstants.setForeground(a, new Color(0x555555));
            case '9' -> StyleConstants.setForeground(a, new Color(0x5555FF));
            case 'a' -> StyleConstants.setForeground(a, new Color(0x55FF55));
            case 'b' -> StyleConstants.setForeground(a, new Color(0x55FFFF));
            case 'c' -> StyleConstants.setForeground(a, new Color(0xFF5555));
            case 'd' -> StyleConstants.setForeground(a, new Color(0xFF55FF));
            case 'e' -> StyleConstants.setForeground(a, new Color(0xFFFF55));
            case 'f' -> StyleConstants.setForeground(a, new Color(0xFFFFFF));
            case 'l' -> StyleConstants.setBold(a, true);
            case 'o' -> StyleConstants.setItalic(a, true);
            case 'n' -> StyleConstants.setUnderline(a, true);
            case 'm' -> StyleConstants.setStrikeThrough(a, true);
            case 'k' -> a.addAttribute(ObfuscatedEditorKit.OBFUSCATED, true);
            case 'r' -> {
                StyleConstants.setBold(a, false);
                StyleConstants.setItalic(a, false);
                StyleConstants.setUnderline(a, false);
                StyleConstants.setStrikeThrough(a, false);
                a.addAttribute(ObfuscatedEditorKit.OBFUSCATED, false);
                StyleConstants.setForeground(a, UIManager.getColor("Label.foreground"));
            }
            default -> {
            }
        }
    }

    private static String toMotdWithCodes(StyledDocument doc) {
        try {
            String text = doc.getText(0, doc.getLength());
            StringBuilder out = new StringBuilder();

            Color lastColor = null;
            boolean lastBold = false, lastItalic = false, lastUnder = false, lastStrike = false, lastObf = false;

            for (int i = 0; i < text.length(); i++) {
                char ch = text.charAt(i);
                if (ch == '\n') {
                    out.append('\n');
                    continue;
                }

                AttributeSet a = doc.getCharacterElement(i).getAttributes();
                Color color = StyleConstants.getForeground(a);
                boolean bold = StyleConstants.isBold(a);
                boolean italic = StyleConstants.isItalic(a);
                boolean under = StyleConstants.isUnderline(a);
                boolean strike = StyleConstants.isStrikeThrough(a);
                boolean obf = isObfuscated(a);

                if (!colorsEqual(color, lastColor)) {
                    String code = colorToCode(color);
                    if (code != null) out.append(SECTION).append(code);
                    lastColor = color;
                }
                if (bold != lastBold) { if (bold) out.append(SECTION).append('l'); lastBold = bold; }
                if (italic != lastItalic) { if (italic) out.append(SECTION).append('o'); lastItalic = italic; }
                if (under != lastUnder) { if (under) out.append(SECTION).append('n'); lastUnder = under; }
                if (strike != lastStrike) { if (strike) out.append(SECTION).append('m'); lastStrike = strike; }
                if (obf != lastObf) { if (obf) out.append(SECTION).append('k'); lastObf = obf; }

                out.append(ch);
            }

            return out.toString();
        } catch (BadLocationException e) {
            return "";
        }
    }

    private static boolean colorsEqual(Color a, Color b) {
        if (a == b) return true;
        if (a == null || b == null) return false;
        return a.getRGB() == b.getRGB();
    }

    private static String colorToCode(Color c) {
        if (c == null) return null;
        int rgb = c.getRGB() & 0xFFFFFF;
        return switch (rgb) {
            case 0x000000 -> "0";
            case 0x0000AA -> "1";
            case 0x00AA00 -> "2";
            case 0x00AAAA -> "3";
            case 0xAA0000 -> "4";
            case 0xAA00AA -> "5";
            case 0xFFAA00 -> "6";
            case 0xAAAAAA -> "7";
            case 0x555555 -> "8";
            case 0x5555FF -> "9";
            case 0x55FF55 -> "a";
            case 0x55FFFF -> "b";
            case 0xFF5555 -> "c";
            case 0xFF55FF -> "d";
            case 0xFFFF55 -> "e";
            case 0xFFFFFF -> "f";
            default -> null;
        };
    }

    private static class TwoLineFilter extends DocumentFilter {
        @Override
        public void insertString(FilterBypass fb, int offset, String string, AttributeSet attr) throws BadLocationException {
            if (string == null) return;
            string = string.replace("\r", "");
            String filtered = filterNewlines(fb.getDocument().getText(0, fb.getDocument().getLength()), offset, 0, string);
            if (filtered.isEmpty()) return;
            super.insertString(fb, offset, filtered, attr);
        }

        @Override
        public void replace(FilterBypass fb, int offset, int length, String text, AttributeSet attrs) throws BadLocationException {
            if (text == null) text = "";
            text = text.replace("\r", "");
            String current = fb.getDocument().getText(0, fb.getDocument().getLength());
            String filtered = filterNewlines(current, offset, length, text);
            super.replace(fb, offset, length, filtered, attrs);
        }

        private String filterNewlines(String current, int offset, int length, String incoming) {
            String next = current.substring(0, offset) + incoming + current.substring(offset + length);
            int newlineCount = 0;
            for (int i = 0; i < next.length(); i++) if (next.charAt(i) == '\n') newlineCount++;
            if (newlineCount <= 1) return incoming;
            // eliminamos saltos extra del texto entrante
            StringBuilder out = new StringBuilder();
            int allowed = current.indexOf('\n') >= 0 ? 0 : 1;
            for (int i = 0; i < incoming.length(); i++) {
                char c = incoming.charAt(i);
                if (c == '\n') {
                    if (allowed <= 0) continue;
                    allowed--;
                }
                out.append(c);
            }
            return out.toString();
        }
    }

    // === Ofuscación visual (sin cambiar el texto real) ===
    static class ObfuscatedEditorKit extends StyledEditorKit {
        static final String OBFUSCATED = "obfuscated";
        private final ViewFactory defaultFactory = new StyledEditorKit().getViewFactory();

        @Override
        public ViewFactory getViewFactory() {
            return elem -> {
                View v = defaultFactory.create(elem);
                if (v instanceof LabelView lv) {
                    return new ObfuscatedLabelView(elem);
                }
                return v;
            };
        }

        private static class ObfuscatedLabelView extends LabelView {
            private static final char[] POOL = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
            private final Random rnd = new Random();

            ObfuscatedLabelView(Element elem) {
                super(elem);
            }

            @Override
            public void paint(Graphics g, Shape a) {
                AttributeSet attrs = getAttributes();
                Object obf = attrs.getAttribute(OBFUSCATED);
                boolean isObf = obf instanceof Boolean b && b;
                if (!isObf) {
                    super.paint(g, a);
                    return;
                }

                try {
                    int start = getStartOffset();
                    int end = getEndOffset();
                    Document doc = getDocument();
                    String s = doc.getText(start, end - start);

                    StringBuilder fake = new StringBuilder(s.length());
                    for (int i = 0; i < s.length(); i++) {
                        char c = s.charAt(i);
                        if (c == '\n' || Character.isWhitespace(c)) fake.append(c);
                        else fake.append(POOL[rnd.nextInt(POOL.length)]);
                    }

                    Graphics2D g2 = (Graphics2D) g.create();
                    Rectangle r = (a instanceof Rectangle rect) ? rect : a.getBounds();
                    g2.setClip(r);
                    g2.setColor(StyleConstants.getForeground(attrs));
                    g2.setFont(g2.getFont());

                    FontMetrics fm = g2.getFontMetrics();
                    int x = r.x;
                    int y = r.y + fm.getAscent();
                    g2.drawString(fake.toString(), x, y);
                    g2.dispose();
                } catch (BadLocationException e) {
                    super.paint(g, a);
                }
            }
        }
    }
}
