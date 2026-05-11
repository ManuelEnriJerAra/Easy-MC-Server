package vista;

import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JPanel;
import javax.swing.JSeparator;
import javax.swing.JTextPane;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.text.BadLocationException;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;
import javax.swing.text.StyledDocument;

final class ExtensionDescriptionRenderer {
    private static final Pattern URL_PATTERN = Pattern.compile("https?://[^\\s)]+");
    private static final Pattern MARKDOWN_LINK_PATTERN = Pattern.compile("\\[([^\\]]+)]\\((https?://[^)\\s]+)(?:\\s+\"[^\"]*\")?\\)");
    private static final Pattern BB_TABLE_PATTERN = Pattern.compile("(?is)\\[table(?:=[^\\]]*)?]\\s*(.*?)\\s*\\[/table]");
    private static final Pattern HTML_TABLE_PATTERN = Pattern.compile("(?is)<\\s*table\\b[^>]*>\\s*(.*?)\\s*</\\s*table\\s*>");
    private static final Pattern BB_ROW_PATTERN = Pattern.compile("(?is)\\[tr(?:=[^\\]]*)?]\\s*(.*?)\\s*\\[/tr]");
    private static final Pattern HTML_ROW_PATTERN = Pattern.compile("(?is)<\\s*tr\\b[^>]*>\\s*(.*?)\\s*</\\s*tr\\s*>");
    private static final Pattern BB_CELL_PATTERN = Pattern.compile("(?is)\\[(td|th)(?:=[^\\]]*)?]\\s*(.*?)\\s*\\[/\\1]");
    private static final Pattern HTML_CELL_PATTERN = Pattern.compile("(?is)<\\s*(td|th)\\b[^>]*>\\s*(.*?)\\s*</\\s*\\1\\s*>");

    private ExtensionDescriptionRenderer() {
    }

    static List<Block> parse(String rawText) {
        String text = removeUnsafeRichContent(decodeBasicHtmlEntities(rawText));
        if (text.isBlank()) {
            return List.of(new TextBlock("Sin descripcion disponible."));
        }

        List<Block> blocks = new ArrayList<>();
        int cursor = 0;
        while (cursor < text.length()) {
            TableMatch next = nextTable(text, cursor);
            if (next == null) {
                appendTextBlocks(blocks, text.substring(cursor));
                break;
            }
            appendTextBlocks(blocks, text.substring(cursor, next.start()));
            if (!next.table().rows().isEmpty()) {
                blocks.add(next.table());
            }
            cursor = next.end();
        }
        if (blocks.isEmpty()) {
            blocks.add(new TextBlock("Sin descripcion disponible."));
        }
        return blocks;
    }

    static void renderInto(JPanel target, String rawText, Consumer<String> linkHandler) {
        target.removeAll();
        target.setLayout(new BoxLayout(target, BoxLayout.Y_AXIS));
        target.setOpaque(false);
        ExtensionDetailsLayout.configureFullWidth(target);

        List<Block> blocks = parse(rawText);
        for (int i = 0; i < blocks.size(); i++) {
            JComponent component = renderBlock(blocks.get(i), linkHandler);
            if (component == null) {
                continue;
            }
            ExtensionDetailsLayout.configureFullWidth(component);
            target.add(component);
            if (i < blocks.size() - 1) {
                target.add(Box.createVerticalStrut(8));
            }
        }
        target.revalidate();
        target.repaint();
    }

    static String cleanPlainText(String rawText) {
        List<Block> blocks = parse(rawText);
        List<String> parts = new ArrayList<>();
        for (Block block : blocks) {
            switch (block) {
                case HeadingBlock heading -> parts.add(heading.text());
                case TextBlock text -> parts.add(text.text());
                case ListBlock list -> parts.add(String.join(" ", list.items()));
                case TableBlock table -> {
                    for (TableRow row : table.rows()) {
                        List<String> cells = new ArrayList<>();
                        for (TableCell cell : row.cells()) {
                            cells.add(cell.text());
                        }
                        parts.add(String.join(" ", cells));
                    }
                }
                case SeparatorBlock ignored -> {
                }
            }
        }
        return finishText(String.join("\n", parts));
    }

    private static JComponent renderBlock(Block block, Consumer<String> linkHandler) {
        return switch (block) {
            case HeadingBlock heading -> createTextPane(heading.text(), true, Math.max(14f, 18f - heading.level()), linkHandler);
            case TextBlock text -> createTextPane(text.text(), false, 13.5f, linkHandler);
            case ListBlock list -> createTextPane("- " + String.join("\n- ", list.items()), false, 13.5f, linkHandler);
            case TableBlock table -> createTable(table, linkHandler);
            case SeparatorBlock ignored -> {
                JSeparator separator = new JSeparator(SwingConstants.HORIZONTAL);
                separator.setForeground(AppTheme.withAlpha(AppTheme.getBorderColor(), 160));
                separator.setMaximumSize(new Dimension(Integer.MAX_VALUE, 1));
                yield separator;
            }
        };
    }

    private static JTextPane createTextPane(String text, boolean heading, float size, Consumer<String> linkHandler) {
        JTextPane pane = new WrappingTextPane();
        ExtensionDetailsLayout.configureDescriptionArea(pane);
        pane.setFont(pane.getFont().deriveFont(heading ? Font.BOLD : Font.PLAIN, size));
        pane.setForeground(heading ? AppTheme.withAlpha(AppTheme.getForeground(), 235) : AppTheme.getForeground());
        pane.setMargin(new Insets(0, 0, 0, 10));
        pane.addComponentListener(new ComponentAdapter() {
            @Override
            public void componentResized(ComponentEvent e) {
                pane.revalidate();
                pane.repaint();
            }
        });

        List<LinkRange> links = new ArrayList<>();
        try {
            appendInlineText(pane.getStyledDocument(), text, baseAttributes(pane), links);
        } catch (BadLocationException ex) {
            pane.setText(softWrapLongTokens(stripInlineMarkup(text)));
        }
        installLinkHandler(pane, links, linkHandler);
        return pane;
    }

    private static JPanel createTable(TableBlock table, Consumer<String> linkHandler) {
        JPanel panel = new JPanel(new GridBagLayout());
        panel.setOpaque(false);
        panel.setBorder(BorderFactory.createEmptyBorder(2, 0, 2, 10));
        int columns = table.rows().stream().mapToInt(row -> row.cells().size()).max().orElse(0);
        if (columns <= 0) {
            return panel;
        }

        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.insets = new Insets(0, 0, 0, 0);
        gbc.weighty = 0d;
        for (int rowIndex = 0; rowIndex < table.rows().size(); rowIndex++) {
            TableRow row = table.rows().get(rowIndex);
            for (int column = 0; column < columns; column++) {
                TableCell cell = column < row.cells().size()
                        ? row.cells().get(column)
                        : new TableCell("", false);
                gbc.gridx = column;
                gbc.gridy = rowIndex;
                gbc.weightx = 1d;
                panel.add(createTableCell(cell, rowIndex == 0 && row.hasHeader(), linkHandler), gbc);
            }
        }
        return panel;
    }

    private static JComponent createTableCell(TableCell cell, boolean headerRow, Consumer<String> linkHandler) {
        JPanel wrapper = new JPanel(new GridBagLayout());
        boolean header = cell.header() || headerRow;
        wrapper.setOpaque(true);
        wrapper.setBackground(header
                ? AppTheme.withAlpha(AppTheme.getForeground(), 20)
                : AppTheme.withAlpha(AppTheme.getPanelBackground(), 210));
        wrapper.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(1, 1, 0, 0, AppTheme.withAlpha(AppTheme.getBorderColor(), 170)),
                BorderFactory.createEmptyBorder(7, 8, 7, 8)
        ));

        JTextPane textPane = createTextPane(cell.text(), header, 12.75f, linkHandler);
        textPane.setMargin(new Insets(0, 0, 0, 0));
        textPane.setForeground(header ? AppTheme.withAlpha(AppTheme.getForeground(), 235) : AppTheme.getForeground());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.HORIZONTAL;
        gbc.weightx = 1d;
        gbc.gridx = 0;
        gbc.gridy = 0;
        wrapper.add(textPane, gbc);
        return wrapper;
    }

    private static void appendTextBlocks(List<Block> blocks, String rawText) {
        String text = normalizeStructuralMarkup(rawText);
        if (text.isBlank()) {
            return;
        }
        String[] paragraphs = text.split("\\n\\s*\\n+");
        for (String paragraph : paragraphs) {
            appendParagraphBlock(blocks, paragraph);
        }
    }

    private static void appendParagraphBlock(List<Block> blocks, String paragraph) {
        String text = finishText(paragraph);
        if (text.isBlank()) {
            return;
        }

        String[] lines = text.split("\\R");
        for (int i = 0; i < lines.length; i++) {
            String line = lines[i].trim();
            if (line.isBlank()) {
                continue;
            }

            MarkdownTable markdownTable = tryReadMarkdownTable(lines, i);
            if (markdownTable != null) {
                blocks.add(markdownTable.table());
                i = markdownTable.endLine();
                continue;
            }

            Matcher heading = Pattern.compile("^\\s{0,3}(#{1,6})\\s+(.+)$").matcher(line);
            Matcher bbHeading = Pattern.compile("(?is)^\\[h([1-6])]\\s*(.*?)\\s*\\[/h\\1]$").matcher(line);
            if (heading.matches()) {
                blocks.add(new HeadingBlock(heading.group(1).length(), finishText(heading.group(2))));
            } else if (bbHeading.matches()) {
                blocks.add(new HeadingBlock(Integer.parseInt(bbHeading.group(1)), finishText(bbHeading.group(2))));
            } else if (line.matches("^[-*_]{3,}$")) {
                blocks.add(new SeparatorBlock());
            } else if (isListLine(line)) {
                List<String> items = new ArrayList<>();
                while (i < lines.length && isListLine(lines[i].trim())) {
                    items.add(finishText(lines[i].trim().replaceFirst("^([-*+]|\\d+[.)])\\s+", "")));
                    i++;
                }
                i--;
                blocks.add(new ListBlock(items));
            } else {
                List<String> body = new ArrayList<>();
                while (i < lines.length) {
                    String bodyLine = lines[i].trim();
                    if (bodyLine.isBlank() || isListLine(bodyLine) || bodyLine.matches("^\\s{0,3}#{1,6}\\s+.+$")) {
                        i--;
                        break;
                    }
                    body.add(bodyLine.replaceFirst("^>\\s?", ""));
                    i++;
                }
                blocks.add(new TextBlock(finishText(String.join("\n", body))));
            }
        }
    }

    private static boolean isListLine(String line) {
        return line != null && line.matches("^([-*+]|\\d+[.)])\\s+.+$");
    }

    private static MarkdownTable tryReadMarkdownTable(String[] lines, int start) {
        if (start + 1 >= lines.length || !looksLikePipeRow(lines[start]) || !isMarkdownSeparator(lines[start + 1])) {
            return null;
        }
        List<TableRow> rows = new ArrayList<>();
        rows.add(new TableRow(parsePipeCells(lines[start], true)));
        int cursor = start + 2;
        while (cursor < lines.length && looksLikePipeRow(lines[cursor])) {
            rows.add(new TableRow(parsePipeCells(lines[cursor], false)));
            cursor++;
        }
        return new MarkdownTable(new TableBlock(rows), cursor - 1);
    }

    private static boolean looksLikePipeRow(String line) {
        return line != null && line.contains("|") && line.replace("|", "").trim().length() > 0;
    }

    private static boolean isMarkdownSeparator(String line) {
        return line != null && line.trim().matches("^\\|?\\s*:?-{3,}:?\\s*(\\|\\s*:?-{3,}:?\\s*)+\\|?\\s*$");
    }

    private static List<TableCell> parsePipeCells(String line, boolean header) {
        String value = line.trim();
        if (value.startsWith("|")) {
            value = value.substring(1);
        }
        if (value.endsWith("|")) {
            value = value.substring(0, value.length() - 1);
        }
        List<TableCell> cells = new ArrayList<>();
        for (String cell : value.split("\\|", -1)) {
            cells.add(new TableCell(finishText(cell), header));
        }
        return cells;
    }

    private static TableMatch nextTable(String text, int start) {
        Matcher bb = BB_TABLE_PATTERN.matcher(text);
        Matcher html = HTML_TABLE_PATTERN.matcher(text);
        boolean hasBb = bb.find(start);
        boolean hasHtml = html.find(start);
        if (!hasBb && !hasHtml) {
            return null;
        }
        if (hasBb && (!hasHtml || bb.start() <= html.start())) {
            return new TableMatch(bb.start(), bb.end(), parseTable(bb.group(1), BB_ROW_PATTERN, BB_CELL_PATTERN));
        }
        return new TableMatch(html.start(), html.end(), parseTable(html.group(1), HTML_ROW_PATTERN, HTML_CELL_PATTERN));
    }

    private static TableBlock parseTable(String tableText, Pattern rowPattern, Pattern cellPattern) {
        List<TableRow> rows = new ArrayList<>();
        Matcher rowMatcher = rowPattern.matcher(defaultString(tableText));
        while (rowMatcher.find()) {
            Matcher cellMatcher = cellPattern.matcher(rowMatcher.group(1));
            List<TableCell> cells = new ArrayList<>();
            while (cellMatcher.find()) {
                boolean header = "th".equalsIgnoreCase(cellMatcher.group(1));
                cells.add(new TableCell(cleanCellText(cellMatcher.group(2)), header));
            }
            if (!cells.isEmpty()) {
                rows.add(new TableRow(cells));
            }
        }
        return new TableBlock(rows);
    }

    private static String cleanCellText(String text) {
        return finishText(normalizeStructuralMarkup(text).replace('\n', ' '));
    }

    private static String normalizeStructuralMarkup(String text) {
        String normalized = convertHtmlHeadings(convertLinks(defaultString(text)))
                .replaceAll("(?i)<\\s*br\\s*/?\\s*>", "\n")
                .replaceAll("(?i)</\\s*(p|div|section|article|blockquote)\\s*>", "\n\n")
                .replaceAll("(?i)<\\s*li[^>]*>", "\n- ")
                .replaceAll("(?i)</\\s*li\\s*>", "\n")
                .replaceAll("(?i)</\\s*(ul|ol)\\s*>", "\n")
                .replaceAll("(?i)\\[\\*]", "\n- ")
                .replaceAll("(?i)\\[/?list(?:=[^\\]]*)?]", "\n")
                .replaceAll("(?i)\\[/?quote(?:=[^\\]]*)?]", "\n")
                .replaceAll("(?i)\\[(?:divider|separator|hr)]", "\n---\n")
                .replaceAll("(?is)<\\s*img\\b[^>]*>", "")
                .replaceAll("!\\[[^\\]]*]\\([^)]*\\)", "");
        return stripInlineMarkup(normalized);
    }

    private static String convertHtmlHeadings(String text) {
        Matcher matcher = Pattern.compile("(?is)<\\s*h([1-6])\\b[^>]*>(.*?)</\\s*h\\1\\s*>").matcher(defaultString(text));
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            int level = Integer.parseInt(matcher.group(1));
            String replacement = "\n" + "#".repeat(level) + " " + matcher.group(2) + "\n\n";
            matcher.appendReplacement(output, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private static String convertLinks(String text) {
        return defaultString(text)
                .replaceAll("(?is)<\\s*a\\s+[^>]*href\\s*=\\s*['\"]([^'\"]+)['\"][^>]*>(.*?)</\\s*a\\s*>", "[$2]($1)")
                .replaceAll("(?is)\\[url=([^\\]]+)](.*?)\\[/url]", "[$2]($1)")
                .replaceAll("(?is)\\[url](https?://[^\\[]+)\\[/url]", "$1");
    }

    private static String stripInlineMarkup(String text) {
        return defaultString(text)
                .replaceAll("(?is)<\\s*/?\\s*(b|strong|i|em|u|s|strike|code|pre|span|font)\\b[^>]*>", "")
                .replaceAll("(?is)<[^>]+>", "")
                .replaceAll("(?is)\\[/?(?:b|i|u|s|strike|center|left|right|code|pre|spoiler)]", "")
                .replaceAll("(?is)\\[/?(?:size|color|font|align|anchor|heading|sub|sup)=[^\\]]*]", "")
                .replaceAll("(?is)\\[/?(?:size|color|font|align|anchor|heading|sub|sup)]", "")
                .replaceAll("(?is)\\[/url]", "")
                .replaceAll("(?s)\\[[a-z][a-z0-9_-]*(?:=[^\\]]*)?]", "")
                .replaceAll("(?s)\\[/[a-z][a-z0-9_-]*]", "");
    }

    private static String finishText(String text) {
        return decodeBasicHtmlEntities(defaultString(text))
                .replace('\u00a0', ' ')
                .replaceAll("[ \\t\\x0B\\f\\r]+", " ")
                .replaceAll(" *\\n *", "\n")
                .replaceAll("\\n{3,}", "\n\n")
                .trim();
    }

    private static String removeUnsafeRichContent(String text) {
        return defaultString(text).replaceAll("(?is)<\\s*(script|style)[^>]*>.*?</\\s*\\1\\s*>", "");
    }

    private static String decodeBasicHtmlEntities(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        String decoded = text.replace("&nbsp;", " ")
                .replace("&amp;", "&")
                .replace("&lt;", "<")
                .replace("&gt;", ">")
                .replace("&quot;", "\"")
                .replace("&#39;", "'")
                .replace("&apos;", "'")
                .replace("&#91;", "[")
                .replace("&#x5B;", "[")
                .replace("&#x5b;", "[")
                .replace("&lbrack;", "[")
                .replace("&#93;", "]")
                .replace("&#x5D;", "]")
                .replace("&#x5d;", "]")
                .replace("&rbrack;", "]");
        Matcher matcher = Pattern.compile("&#(\\d+);").matcher(decoded);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            int codePoint = Integer.parseInt(matcher.group(1));
            matcher.appendReplacement(output, Matcher.quoteReplacement(new String(Character.toChars(codePoint))));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    private static void appendInlineText(StyledDocument document,
                                         String text,
                                         SimpleAttributeSet baseAttributes,
                                         List<LinkRange> links) throws BadLocationException {
        Matcher linkMatcher = MARKDOWN_LINK_PATTERN.matcher(text);
        int cursor = 0;
        while (linkMatcher.find()) {
            appendBareUrls(document, text.substring(cursor, linkMatcher.start()), baseAttributes, links);
            String label = linkMatcher.group(1) == null || linkMatcher.group(1).isBlank()
                    ? linkMatcher.group(2)
                    : linkMatcher.group(1);
            appendLink(document, label, linkMatcher.group(2), baseAttributes, links);
            cursor = linkMatcher.end();
        }
        appendBareUrls(document, text.substring(cursor), baseAttributes, links);
    }

    private static void appendBareUrls(StyledDocument document,
                                       String text,
                                       SimpleAttributeSet baseAttributes,
                                       List<LinkRange> links) throws BadLocationException {
        Matcher urlMatcher = URL_PATTERN.matcher(text);
        int cursor = 0;
        while (urlMatcher.find()) {
            appendInlineWithoutLinks(document, text.substring(cursor, urlMatcher.start()), baseAttributes);
            appendLink(document, urlMatcher.group(), urlMatcher.group(), baseAttributes, links);
            cursor = urlMatcher.end();
        }
        appendInlineWithoutLinks(document, text.substring(cursor), baseAttributes);
    }

    private static void appendInlineWithoutLinks(StyledDocument document,
                                                 String text,
                                                 SimpleAttributeSet baseAttributes) throws BadLocationException {
        if (text == null || text.isEmpty()) {
            return;
        }
        Pattern emphasisPattern = Pattern.compile("(\\*\\*|__)(.+?)\\1|(?<!\\*)\\*([^*\\n]+)\\*(?!\\*)|(?<!_)_([^_\\n]+)_(?!_)|~~(.+?)~~|`([^`]+)`");
        Matcher matcher = emphasisPattern.matcher(text);
        int cursor = 0;
        while (matcher.find()) {
            insertText(document, text.substring(cursor, matcher.start()), baseAttributes);
            SimpleAttributeSet styled = new SimpleAttributeSet(baseAttributes);
            String content;
            if (matcher.group(6) != null) {
                content = matcher.group(6);
                StyleConstants.setFontFamily(styled, Font.MONOSPACED);
                StyleConstants.setBackground(styled, AppTheme.withAlpha(AppTheme.getForeground(), 20));
            } else if (matcher.group(5) != null) {
                content = matcher.group(5);
                StyleConstants.setStrikeThrough(styled, true);
            } else if (matcher.group(3) != null || matcher.group(4) != null) {
                content = matcher.group(3) != null ? matcher.group(3) : matcher.group(4);
                StyleConstants.setItalic(styled, true);
            } else {
                content = matcher.group(2);
                StyleConstants.setBold(styled, true);
            }
            insertText(document, content, styled);
            cursor = matcher.end();
        }
        insertText(document, text.substring(cursor), baseAttributes);
    }

    private static void appendLink(StyledDocument document,
                                   String label,
                                   String url,
                                   SimpleAttributeSet baseAttributes,
                                   List<LinkRange> links) throws BadLocationException {
        String visible = label == null || label.isBlank() ? url : label;
        int start = document.getLength();
        SimpleAttributeSet attributes = new SimpleAttributeSet(baseAttributes);
        StyleConstants.setForeground(attributes, AppTheme.getLinkForeground());
        StyleConstants.setUnderline(attributes, true);
        insertText(document, visible, attributes);
        links.add(new LinkRange(start, document.getLength(), url));
    }

    private static void insertText(StyledDocument document,
                                   String text,
                                   SimpleAttributeSet attributes) throws BadLocationException {
        if (text == null || text.isEmpty()) {
            return;
        }
        document.insertString(document.getLength(), softWrapLongTokens(decodeBasicHtmlEntities(text)), attributes);
    }

    private static SimpleAttributeSet baseAttributes(JTextPane pane) {
        SimpleAttributeSet attributes = new SimpleAttributeSet();
        Font font = pane.getFont();
        StyleConstants.setFontFamily(attributes, font.getFamily());
        StyleConstants.setFontSize(attributes, Math.max(1, font.getSize()));
        StyleConstants.setForeground(attributes, pane.getForeground());
        return attributes;
    }

    private static void installLinkHandler(JTextPane pane, List<LinkRange> links, Consumer<String> linkHandler) {
        if (links.isEmpty() || linkHandler == null) {
            pane.setCursor(Cursor.getDefaultCursor());
            return;
        }
        pane.addMouseMotionListener(new MouseAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                pane.setCursor(urlAt(pane, links, e) == null
                        ? Cursor.getDefaultCursor()
                        : Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
            }
        });
        pane.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (!SwingUtilities.isLeftMouseButton(e)) {
                    return;
                }
                String url = urlAt(pane, links, e);
                if (url != null) {
                    linkHandler.accept(url);
                }
            }
        });
    }

    private static String urlAt(JTextPane pane, List<LinkRange> links, MouseEvent event) {
        int offset = pane.viewToModel2D(event.getPoint());
        if (offset < 0) {
            return null;
        }
        for (LinkRange link : links) {
            if (offset >= link.start() && offset <= link.end()) {
                return link.url();
            }
        }
        return null;
    }

    private static String softWrapLongTokens(String text) {
        if (text == null || text.isBlank()) {
            return "";
        }
        return text.replace("/", "/\u200B")
                .replace(".", ".\u200B")
                .replace("-", "-\u200B")
                .replace("_", "_\u200B")
                .replace("?", "?\u200B")
                .replace("&", "&\u200B")
                .replace("=", "=\u200B");
    }

    private static String defaultString(String text) {
        return text == null ? "" : text;
    }

    sealed interface Block permits TextBlock, HeadingBlock, ListBlock, TableBlock, SeparatorBlock {
    }

    record TextBlock(String text) implements Block {
    }

    record HeadingBlock(int level, String text) implements Block {
    }

    record ListBlock(List<String> items) implements Block {
    }

    record TableBlock(List<TableRow> rows) implements Block {
    }

    record TableRow(List<TableCell> cells) {
        boolean hasHeader() {
            return cells.stream().anyMatch(TableCell::header);
        }
    }

    record TableCell(String text, boolean header) {
    }

    record SeparatorBlock() implements Block {
    }

    private record TableMatch(int start, int end, TableBlock table) {
    }

    private record MarkdownTable(TableBlock table, int endLine) {
    }

    private record LinkRange(int start, int end, String url) {
    }

    private static final class WrappingTextPane extends JTextPane {
        @Override
        public boolean getScrollableTracksViewportWidth() {
            return true;
        }

        @Override
        public Dimension getMaximumSize() {
            Dimension preferred = getPreferredSize();
            return new Dimension(Integer.MAX_VALUE, preferred.height);
        }
    }
}
