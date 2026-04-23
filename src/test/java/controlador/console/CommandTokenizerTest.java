package controlador.console;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class CommandTokenizerTest {

    @Test
    void parsesQuotedArgumentsAndActiveTokenInsideQuotes() {
        CommandTokenizer tokenizer = new CommandTokenizer();

        ParsedCommandLine parsed = tokenizer.parse("tell \"Steve Jobs\" he", "tell \"Steve Jobs\" he".length());

        assertThat(parsed.tokens()).hasSize(3);
        assertThat(parsed.tokens().get(1).text()).isEqualTo("Steve Jobs");
        assertThat(parsed.tokens().get(1).quoted()).isTrue();
        assertThat(parsed.activeToken()).isEqualTo("he");
        assertThat(parsed.commandName()).isEqualTo("tell");
    }

    @Test
    void detectsUnclosedQuoteAndKeepsSingleToken() {
        CommandTokenizer tokenizer = new CommandTokenizer();

        ParsedCommandLine parsed = tokenizer.parse("say \"hello world", "say \"hello world".length());

        assertThat(parsed.unclosedQuote()).isTrue();
        assertThat(parsed.tokens()).hasSize(2);
        assertThat(parsed.tokens().get(1).text()).isEqualTo("hello world");
    }

    @Test
    void trailingWhitespaceCreatesNextArgumentSlot() {
        CommandTokenizer tokenizer = new CommandTokenizer();

        ParsedCommandLine parsed = tokenizer.parse("tp Steve ", "tp Steve ".length());

        assertThat(parsed.trailingWhitespace()).isTrue();
        assertThat(parsed.activeTokenIndex()).isEqualTo(-1);
        assertThat(parsed.argumentIndex()).isEqualTo(1);
    }
}
