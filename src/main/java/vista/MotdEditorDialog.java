package vista;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Insets;
import java.awt.Window;
import java.util.concurrent.ThreadLocalRandom;

import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JPanel;
import javax.swing.JTextPane;
import javax.swing.JToolBar;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;
import javax.swing.Timer;
import javax.swing.UIManager;
import javax.swing.WindowConstants;
import javax.swing.text.Caret;
import javax.swing.text.AbstractDocument;
import javax.swing.text.AttributeSet;
import javax.swing.text.BadLocationException;
import javax.swing.text.DocumentFilter;
import javax.swing.text.Element;
import javax.swing.text.MutableAttributeSet;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

import com.formdev.flatlaf.extras.components.FlatButton;
import com.formdev.flatlaf.extras.components.FlatScrollPane;

public class MotdEditorDialog {
    private static final char SECTION = '\u00A7';
    private static final Color DEFAULT_TEXT_COLOR = Color.WHITE;
    private static final int EDITOR_FONT_SIZE = 18;
    private static final Insets EDITOR_PADDING = new Insets(8, 10, 8, 10);
    private static final char[] OBFUSCATED_POOL = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789".toCharArray();
    private static final String ATTR_OBFUSCATED = "obfuscated";
    private static final String ATTR_ORIGINAL_CHAR = "obfuscatedOriginalChar";

    public static String show(Component parent, String initial) {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        JDialog dialog = new JDialog(owner, "Editar MOTD", Dialog.ModalityType.APPLICATION_MODAL);

        JTextPane pane = new JTextPane();
        pane.setBorder(BorderFactory.createEmptyBorder(
                EDITOR_PADDING.top,
                EDITOR_PADDING.left,
                EDITOR_PADDING.bottom,
                EDITOR_PADDING.right
        ));
        pane.setFont(AppTheme.getConsoleLikeFont(EDITOR_FONT_SIZE));
        pane.setBackground(AppTheme.getConsoleBackground());
        pane.setForeground(DEFAULT_TEXT_COLOR);
        pane.setCaretColor(DEFAULT_TEXT_COLOR);
        pane.setOpaque(false);

        // Limitar a 2 líneas (0 o 1 salto de línea)
        ((AbstractDocument) pane.getDocument()).setDocumentFilter(new TwoLineFilter());

        applyInitial(pane, initial);
        setTwoLineEditorHeight(pane);

        RoundedBackgroundPanel editorWrap = new RoundedBackgroundPanel(AppTheme.getConsoleBackground(), AppTheme.getArc());
        editorWrap.setBorder(AppTheme.createRoundedBorder(
                new Insets(0, 0, 0, 0),
                AppTheme.getConsoleOutlineColor(),
                1f
        ));
        editorWrap.add(pane, BorderLayout.CENTER);

        FlatScrollPane scroll = new FlatScrollPane();
        scroll.setViewportView(editorWrap);
        scroll.setBorder(null);
        scroll.setHorizontalScrollBarPolicy(ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        scroll.setVerticalScrollBarPolicy(ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER);
        scroll.setWheelScrollingEnabled(false);
        scroll.setOpaque(false);
        scroll.getViewport().setOpaque(false);
        Dimension editorSize = new Dimension(100, pane.getPreferredSize().height + 2);
        editorWrap.setPreferredSize(editorSize);
        editorWrap.setMinimumSize(editorSize);
        editorWrap.setMaximumSize(new Dimension(Integer.MAX_VALUE, editorSize.height));
        scroll.setPreferredSize(editorSize);
        scroll.setMinimumSize(editorSize);
        scroll.setMaximumSize(new Dimension(Integer.MAX_VALUE, editorSize.height));

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
        JButton obfuscate = makeToggle(randomObfuscatedButtonText(), "Ofuscado", pane, AppTheme.getConsoleLikeFont(baseBtnFont.getSize2D()), null);
        Dimension obfuscateSize = obfuscate.getPreferredSize();
        obfuscate.setPreferredSize(obfuscateSize);
        obfuscate.setMinimumSize(obfuscateSize);
        obfuscate.setMaximumSize(obfuscateSize);
        installObfuscatedButtonAnimation(obfuscate);
        obfuscate.addActionListener(e -> toggleObfuscated(pane));
        bar.add(obfuscate);

        bar.addSeparator();

        JButton reset = new FlatButton();
        reset.setText("Reset");
        reset.setToolTipText("Reset (§r)");
        reset.setFocusable(false);
        reset.addActionListener(e -> applyResetToSelection(pane));
        bar.add(reset);

        JButton cancel = new FlatButton();
        cancel.setText("Cancelar");
        JButton ok = new FlatButton();
        ok.setText("Guardar");
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
        JPanel editorArea = new JPanel(new BorderLayout());
        editorArea.setOpaque(false);
        editorArea.add(scroll, BorderLayout.NORTH);
        content.add(editorArea, BorderLayout.CENTER);
        content.add(buttons, BorderLayout.SOUTH);

        dialog.setContentPane(content);
        dialog.setSize(720, 220);
        dialog.setLocationRelativeTo(parent);
        dialog.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);

        Timer obfTimer = new Timer(140, e -> animateObfuscatedText(pane));
        obfTimerRef[0] = obfTimer;
        obfTimer.start();
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
        JButton b = new FlatButton();
        b.setFocusable(false);
        b.setPreferredSize(new Dimension(18, 18));
        b.setMinimumSize(new Dimension(18, 18));
        b.setMaximumSize(new Dimension(18, 18));
        b.setBackground(color);
        b.setOpaque(true);
        b.setBorder(BorderFactory.createLineBorder(AppTheme.getMutedForeground().darker(), 1, true));
        b.setToolTipText(code);
        b.addActionListener(e -> applyColorToSelection(pane, color));
        bar.add(b);
    }

    private static void setTwoLineEditorHeight(JTextPane pane) {
        FontMetrics fm = pane.getFontMetrics(pane.getFont());
        int lineHeight = fm == null ? EDITOR_FONT_SIZE + 4 : fm.getHeight();
        int height = (lineHeight * 2) + EDITOR_PADDING.top + EDITOR_PADDING.bottom;
        Dimension size = new Dimension(100, height);
        pane.setPreferredSize(size);
        pane.setMinimumSize(size);
        pane.setMaximumSize(new Dimension(Integer.MAX_VALUE, height));
    }

    private interface AttrMutator {
        void mutate(MutableAttributeSet attrs);
    }

    private static JButton makeToggle(String text, String tooltip, JTextPane pane, Font font, AttrMutator mutator) {
        JButton b = new FlatButton();
        b.setText(text);
        b.setFocusable(false);
        b.setToolTipText(tooltip);
        b.setMargin(new Insets(2, 6, 2, 6));
        if(font != null) b.setFont(font);
        if(mutator != null){
            b.addActionListener(e -> toggleAttr(pane, mutator));
        }
        return b;
    }

    private static void installObfuscatedButtonAnimation(JButton button) {
        if (button == null) return;
        Timer timer = new Timer(140, e -> button.setText(randomObfuscatedButtonText()));
        timer.start();
        button.addHierarchyListener(e -> {
            if (button.isShowing()) {
                if (!timer.isRunning()) timer.start();
            } else {
                timer.stop();
            }
        });
    }

    private static String randomObfuscatedButtonText() {
        return String.valueOf(randomObfuscatedChar());
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
            applyEditorFont(pane, current);
            pane.setCharacterAttributes(current, true);
            return;
        }
        for (int pos = start; pos < end; pos++) {
            Element el = doc.getCharacterElement(pos);
            MutableAttributeSet a = new SimpleAttributeSet(el.getAttributes());
            mutator.mutate(a);
            applyEditorFont(pane, a);
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
            applyEditorFont(pane, current);
            pane.setCharacterAttributes(current, true);
            return;
        }
        for (int pos = start; pos < end; pos++) {
            Element el = doc.getCharacterElement(pos);
            MutableAttributeSet a = new SimpleAttributeSet(el.getAttributes());
            StyleConstants.setForeground(a, color);
            applyEditorFont(pane, a);
            doc.setCharacterAttributes(pos, 1, a, true);
        }
    }

    private static void toggleObfuscated(JTextPane pane) {
        StyledDocument doc = pane.getStyledDocument();
        int start = pane.getSelectionStart();
        int end = pane.getSelectionEnd();
        if (end < start) { int t = start; start = end; end = t; }

        if (start == end) {
            MutableAttributeSet current = new SimpleAttributeSet(pane.getCharacterAttributes());
            current.addAttribute(ATTR_OBFUSCATED, !isObfuscated(current));
            applyEditorFont(pane, current);
            pane.setCharacterAttributes(current, true);
            return;
        }

        boolean remove = isRangeObfuscated(doc, start, end);
        for (int pos = start; pos < end; pos++) {
            try {
                String currentText = doc.getText(pos, 1);
                char currentChar = currentText.isEmpty() ? 0 : currentText.charAt(0);
                Element el = doc.getCharacterElement(pos);
                MutableAttributeSet attrs = new SimpleAttributeSet(el.getAttributes());
                applyEditorFont(pane, attrs);

                if (remove) {
                    restoreOriginalChar(doc, pos, attrs, currentChar);
                } else {
                    obfuscateChar(doc, pos, attrs, currentChar);
                }
            } catch (BadLocationException ignored) {
            }
        }
        pane.select(start, end);
    }

    private static boolean isRangeObfuscated(StyledDocument doc, int start, int end) {
        if (start >= end) return false;
        for (int pos = start; pos < end; pos++) {
            if (!isObfuscated(doc.getCharacterElement(pos).getAttributes())) {
                return false;
            }
        }
        return true;
    }

    private static void obfuscateChar(StyledDocument doc, int pos, MutableAttributeSet attrs, char currentChar) throws BadLocationException {
        attrs.addAttribute(ATTR_OBFUSCATED, true);
        if (currentChar != '\n' && !Character.isWhitespace(currentChar)) {
            Object original = attrs.getAttribute(ATTR_ORIGINAL_CHAR);
            if (!(original instanceof Character)) {
                attrs.addAttribute(ATTR_ORIGINAL_CHAR, currentChar);
            }
            doc.remove(pos, 1);
            doc.insertString(pos, String.valueOf(randomObfuscatedChar()), attrs);
        } else {
            doc.setCharacterAttributes(pos, 1, attrs, true);
        }
    }

    private static void restoreOriginalChar(StyledDocument doc, int pos, MutableAttributeSet attrs, char currentChar) throws BadLocationException {
        Object original = attrs.getAttribute(ATTR_ORIGINAL_CHAR);
        attrs.removeAttribute(ATTR_OBFUSCATED);
        attrs.removeAttribute(ATTR_ORIGINAL_CHAR);
        if (original instanceof Character originalChar) {
            doc.remove(pos, 1);
            doc.insertString(pos, String.valueOf(originalChar), attrs);
        } else {
            doc.setCharacterAttributes(pos, 1, attrs, true);
        }
    }

    private static char randomObfuscatedChar() {
        return OBFUSCATED_POOL[ThreadLocalRandom.current().nextInt(OBFUSCATED_POOL.length)];
    }

    private static void animateObfuscatedText(JTextPane pane) {
        if (pane == null || !pane.isDisplayable()) return;
        StyledDocument doc = pane.getStyledDocument();
        if (doc == null || doc.getLength() == 0) return;

        Caret caret = pane.getCaret();
        int dot = caret == null ? pane.getCaretPosition() : caret.getDot();
        int mark = caret == null ? dot : caret.getMark();
        int selectionStart = pane.getSelectionStart();
        int selectionEnd = pane.getSelectionEnd();

        for (int pos = 0; pos < doc.getLength(); pos++) {
            try {
                Element el = doc.getCharacterElement(pos);
                MutableAttributeSet attrs = new SimpleAttributeSet(el.getAttributes());
                if (!isObfuscated(attrs) || !(attrs.getAttribute(ATTR_ORIGINAL_CHAR) instanceof Character)) {
                    continue;
                }

                String text = doc.getText(pos, 1);
                if (text.isEmpty()) continue;
                char visible = text.charAt(0);
                if (visible == '\n' || Character.isWhitespace(visible)) continue;

                char next = randomObfuscatedChar();
                if (next == visible && OBFUSCATED_POOL.length > 1) {
                    next = randomObfuscatedChar();
                }

                doc.remove(pos, 1);
                doc.insertString(pos, String.valueOf(next), attrs);
            } catch (BadLocationException ignored) {
            }
        }

        restoreCaretAndSelection(pane, dot, mark, selectionStart, selectionEnd);
    }

    private static void restoreCaretAndSelection(JTextPane pane, int dot, int mark, int selectionStart, int selectionEnd) {
        int length = pane.getDocument().getLength();
        int safeDot = Math.max(0, Math.min(dot, length));
        int safeMark = Math.max(0, Math.min(mark, length));
        if (selectionStart != selectionEnd) {
            pane.setCaretPosition(safeMark);
            pane.moveCaretPosition(safeDot);
        } else {
            pane.setCaretPosition(safeDot);
        }
    }

    private static void applyResetToSelection(JTextPane pane) {
        StyledDocument doc = pane.getStyledDocument();
        int start = pane.getSelectionStart();
        int end = pane.getSelectionEnd();
        if (end < start) { int t = start; start = end; end = t; }

        boolean resetAll = start == end;
        int resetStart = resetAll ? 0 : start;
        int resetEnd = resetAll ? doc.getLength() : end;
        restoreObfuscatedText(doc, resetStart, resetEnd);

        MutableAttributeSet reset = new SimpleAttributeSet();
        StyleConstants.setBold(reset, false);
        StyleConstants.setItalic(reset, false);
        StyleConstants.setUnderline(reset, false);
        StyleConstants.setStrikeThrough(reset, false);
        StyleConstants.setForeground(reset, DEFAULT_TEXT_COLOR);
        applyEditorFont(pane, reset);

        doc.setCharacterAttributes(resetStart, resetEnd - resetStart, reset, true);
        pane.setCharacterAttributes(reset, true);
        if (!resetAll) {
            pane.select(resetStart, resetEnd);
        }
    }

    private static boolean isObfuscated(AttributeSet a) {
        Object v = a.getAttribute(ATTR_OBFUSCATED);
        return v instanceof Boolean b && b;
    }

    private static void restoreObfuscatedText(StyledDocument doc, int start, int end) {
        int boundedStart = Math.max(0, Math.min(start, doc.getLength()));
        int boundedEnd = Math.max(boundedStart, Math.min(end, doc.getLength()));
        for (int pos = boundedStart; pos < boundedEnd; pos++) {
            try {
                Element el = doc.getCharacterElement(pos);
                MutableAttributeSet attrs = new SimpleAttributeSet(el.getAttributes());
                String text = doc.getText(pos, 1);
                char currentChar = text.isEmpty() ? 0 : text.charAt(0);
                if (isObfuscated(attrs)) {
                    restoreOriginalChar(doc, pos, attrs, currentChar);
                }
            } catch (BadLocationException ignored) {
            }
        }
    }

    private static void applyEditorFont(JTextPane pane, MutableAttributeSet attrs) {
        Font font = pane.getFont();
        if (font == null) return;
        StyleConstants.setFontFamily(attrs, font.getFamily());
        StyleConstants.setFontSize(attrs, font.getSize());
    }

    private static MutableAttributeSet createBaseAttrs(JTextPane pane) {
        MutableAttributeSet attrs = new SimpleAttributeSet();
        StyleConstants.setForeground(attrs, DEFAULT_TEXT_COLOR);
        applyEditorFont(pane, attrs);
        return attrs;
    }

    private static void applyInitial(JTextPane pane, String initial) {
        StyledDocument doc = pane.getStyledDocument();
        try {
            doc.remove(0, doc.getLength());
        } catch (BadLocationException ignored) {
        }

        String raw = initial == null ? "" : initial.replace('&', SECTION);

        MutableAttributeSet current = createBaseAttrs(pane);

        StringBuilder text = new StringBuilder();
        final boolean[] seenNewline = {false};

        Runnable flush = () -> {
            if (text.isEmpty()) return;
            try {
                insertStyledText(doc, text.toString(), current);
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
                if (!seenNewline[0]) {
                    text.append('\n');
                    seenNewline[0] = true;
                }
                continue;
            }
            text.append(c);
        }
        flush.run();
    }

    private static void insertStyledText(StyledDocument doc, String text, AttributeSet attrs) throws BadLocationException {
        if (text == null || text.isEmpty()) return;
        MutableAttributeSet baseAttrs = new SimpleAttributeSet(attrs);
        baseAttrs.removeAttribute(ATTR_ORIGINAL_CHAR);
        if (!isObfuscated(attrs)) {
            doc.insertString(doc.getLength(), text, baseAttrs);
            return;
        }

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            MutableAttributeSet charAttrs = new SimpleAttributeSet(baseAttrs);
            if (c != '\n' && !Character.isWhitespace(c)) {
                charAttrs.addAttribute(ATTR_ORIGINAL_CHAR, c);
                doc.insertString(doc.getLength(), String.valueOf(randomObfuscatedChar()), charAttrs);
            } else {
                doc.insertString(doc.getLength(), String.valueOf(c), charAttrs);
            }
        }
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
            case 'k' -> a.addAttribute(ATTR_OBFUSCATED, true);
            case 'r' -> {
                StyleConstants.setBold(a, false);
                StyleConstants.setItalic(a, false);
                StyleConstants.setUnderline(a, false);
                StyleConstants.setStrikeThrough(a, false);
                a.removeAttribute(ATTR_OBFUSCATED);
                a.removeAttribute(ATTR_ORIGINAL_CHAR);
                StyleConstants.setForeground(a, DEFAULT_TEXT_COLOR);
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

                boolean changed = !colorsEqual(color, lastColor)
                        || bold != lastBold
                        || italic != lastItalic
                        || under != lastUnder
                        || strike != lastStrike
                        || obf != lastObf;
                if (changed) {
                    boolean defaultStyle = isDefaultTextColor(color) && !bold && !italic && !under && !strike && !obf;
                    boolean hadStyle = lastColor != null || lastBold || lastItalic || lastUnder || lastStrike || lastObf;
                    if (defaultStyle) {
                        if (hadStyle) out.append(SECTION).append('r');
                    } else {
                        if (hadStyle) out.append(SECTION).append('r');
                        String code = isDefaultTextColor(color) ? null : colorToCode(color);
                        if (code != null) out.append(SECTION).append(code);
                        if (bold) out.append(SECTION).append('l');
                        if (italic) out.append(SECTION).append('o');
                        if (under) out.append(SECTION).append('n');
                        if (strike) out.append(SECTION).append('m');
                        if (obf) out.append(SECTION).append('k');
                    }
                    lastColor = defaultStyle || isDefaultTextColor(color) ? null : color;
                    lastBold = bold;
                    lastItalic = italic;
                    lastUnder = under;
                    lastStrike = strike;
                    lastObf = obf;
                }

                out.append(resolveStoredChar(ch, a, obf));
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

    private static boolean isDefaultTextColor(Color color) {
        return color == null || colorsEqual(color, DEFAULT_TEXT_COLOR);
    }

    private static char resolveStoredChar(char visibleChar, AttributeSet attrs, boolean obfuscated) {
        if (!obfuscated) return visibleChar;
        Object original = attrs.getAttribute(ATTR_ORIGINAL_CHAR);
        return original instanceof Character originalChar ? originalChar : visibleChar;
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

            int existingOutsideSelection = 0;
            String before = current.substring(0, offset);
            String after = current.substring(offset + length);
            for (int i = 0; i < before.length(); i++) if (before.charAt(i) == '\n') existingOutsideSelection++;
            for (int i = 0; i < after.length(); i++) if (after.charAt(i) == '\n') existingOutsideSelection++;

            StringBuilder out = new StringBuilder();
            int allowed = Math.max(0, 1 - existingOutsideSelection);
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
}
