package vista;

import org.junit.jupiter.api.Test;

import javax.swing.JPanel;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ExtensionDescriptionRendererTest {
    @Test
    void parsesBbCodeTableWithHeadersAndRows() {
        List<ExtensionDescriptionRenderer.Block> blocks = ExtensionDescriptionRenderer.parse("""
                Commands

                [table]
                [tr][th]Command[/th][th]Permission[/th][th]Description[/th][/tr]
                [tr][td]/antif5 or /antif5 toggle[/td][td]antif5.command.self[/td][td]Toggle AntiF5 for yourself[/td][/tr]
                [/table]
                """);

        assertThat(blocks).hasSize(2);
        ExtensionDescriptionRenderer.TableBlock table = (ExtensionDescriptionRenderer.TableBlock) blocks.get(1);
        assertThat(table.rows()).hasSize(2);
        assertThat(table.rows().getFirst().cells())
                .extracting(ExtensionDescriptionRenderer.TableCell::text)
                .containsExactly("Command", "Permission", "Description");
        assertThat(table.rows().getFirst().cells()).allMatch(ExtensionDescriptionRenderer.TableCell::header);
        assertThat(table.rows().get(1).cells())
                .extracting(ExtensionDescriptionRenderer.TableCell::text)
                .containsExactly("/antif5 or /antif5 toggle", "antif5.command.self", "Toggle AntiF5 for yourself");
    }

    @Test
    void parsesHtmlTable() {
        List<ExtensionDescriptionRenderer.Block> blocks = ExtensionDescriptionRenderer.parse("""
                <p>Before</p>
                <table>
                  <tr><th>Name</th><th>URL</th></tr>
                  <tr><td>Docs</td><td><a href="https://example.com/docs">Read</a></td></tr>
                </table>
                """);

        ExtensionDescriptionRenderer.TableBlock table = (ExtensionDescriptionRenderer.TableBlock) blocks.get(1);
        assertThat(table.rows()).hasSize(2);
        assertThat(table.rows().get(1).cells())
                .extracting(ExtensionDescriptionRenderer.TableCell::text)
                .containsExactly("Docs", "[Read](https://example.com/docs)");
    }

    @Test
    void preservesMixedTextTableTextTableOrder() {
        List<ExtensionDescriptionRenderer.Block> blocks = ExtensionDescriptionRenderer.parse("""
                # Top
                Intro text.
                [table][tr][td]A[/td][/tr][/table]
                Middle text.
                <table><tr><td>B</td></tr></table>
                End text.
                """);

        assertThat(blocks)
                .extracting(block -> block.getClass().getSimpleName())
                .containsExactly("HeadingBlock", "TextBlock", "TableBlock", "TextBlock", "TableBlock", "TextBlock");
        assertThat(((ExtensionDescriptionRenderer.TextBlock) blocks.get(3)).text()).isEqualTo("Middle text.");
        assertThat(((ExtensionDescriptionRenderer.TextBlock) blocks.get(5)).text()).isEqualTo("End text.");
    }

    @Test
    void parsesEscapedBbCodeTableEntities() {
        List<ExtensionDescriptionRenderer.Block> blocks = ExtensionDescriptionRenderer.parse("""
                &#91;table&#93;&#91;tr&#93;&#91;th&#93;Command&#91;/th&#93;&#91;/tr&#93;&#91;tr&#93;&#91;td&#93;/antif5&#91;/td&#93;&#91;/tr&#93;&#91;/table&#93;
                """);

        ExtensionDescriptionRenderer.TableBlock table = (ExtensionDescriptionRenderer.TableBlock) blocks.getFirst();
        assertThat(table.rows()).hasSize(2);
        assertThat(table.rows().get(1).cells().getFirst().text()).isEqualTo("/antif5");
    }

    @Test
    void plainTextFallsBackToReadableTextBlock() {
        List<ExtensionDescriptionRenderer.Block> blocks = ExtensionDescriptionRenderer.parse("A simple description.");

        assertThat(blocks).containsExactly(new ExtensionDescriptionRenderer.TextBlock("A simple description."));
    }

    @Test
    void cleansMarkdownHtmlAndBbCodeLinks() {
        String plain = ExtensionDescriptionRenderer.cleanPlainText("""
                [Website](https://example.com)
                <a href="https://example.com/docs">Docs</a>
                [url=https://example.com/support]Support[/url]
                """);

        assertThat(plain).contains("[Website](https://example.com)");
        assertThat(plain).contains("[Docs](https://example.com/docs)");
        assertThat(plain).contains("[Support](https://example.com/support)");
        assertThat(plain).doesNotContain("<a").doesNotContain("[url=");
    }

    @Test
    void parsesMinimalMarkdownPipeTable() {
        List<ExtensionDescriptionRenderer.Block> blocks = ExtensionDescriptionRenderer.parse("""
                | Command | Permission |
                | --- | --- |
                | /antif5 | antif5.command.self |
                """);

        ExtensionDescriptionRenderer.TableBlock table = (ExtensionDescriptionRenderer.TableBlock) blocks.getFirst();
        assertThat(table.rows()).hasSize(2);
        assertThat(table.rows().getFirst().cells()).allMatch(ExtensionDescriptionRenderer.TableCell::header);
        assertThat(table.rows().get(1).cells())
                .extracting(ExtensionDescriptionRenderer.TableCell::text)
                .containsExactly("/antif5", "antif5.command.self");
    }

    @Test
    void rendersTableAsSwingPanelInsteadOfText() {
        JPanel target = new JPanel();

        ExtensionDescriptionRenderer.renderInto(
                target,
                "[table][tr][th]Command[/th][/tr][tr][td]/antif5[/td][/tr][/table]",
                ignored -> {
                }
        );

        assertThat(target.getComponentCount()).isEqualTo(1);
        assertThat(target.getComponent(0)).isInstanceOf(JPanel.class);
        assertThat(((JPanel) target.getComponent(0)).getComponentCount()).isEqualTo(2);
    }
}
