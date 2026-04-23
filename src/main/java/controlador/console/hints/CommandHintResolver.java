package controlador.console.hints;

import controlador.console.ParsedCommandLine;
import controlador.console.SuggestionCategory;

import java.util.Set;

@FunctionalInterface
public interface CommandHintResolver {

    Set<SuggestionCategory> resolve(ParsedCommandLine parsedCommandLine, CommandHintProfile profile);
}
