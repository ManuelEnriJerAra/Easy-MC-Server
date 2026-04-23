package controlador.console.hints;

import controlador.console.ParsedCommandLine;
import controlador.console.SuggestionCategory;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Registro extensible de perfiles de hints de comandos.
 */
public final class CommandHintRegistry {
    private final Map<String, CommandHintProfile> profilesByCommand;

    public CommandHintRegistry(List<CommandHintProfile> profiles) {
        LinkedHashMap<String, CommandHintProfile> byCommand = new LinkedHashMap<>();
        for (CommandHintProfile profile : profiles == null ? List.<CommandHintProfile>of() : profiles) {
            if (profile == null) {
                continue;
            }
            for (String command : profile.commands()) {
                if (command == null || command.isBlank()) {
                    continue;
                }
                byCommand.put(command.trim().toLowerCase(Locale.ROOT), profile);
            }
        }
        this.profilesByCommand = Map.copyOf(byCommand);
    }

    public static CommandHintRegistry defaultRegistry() {
        return new CommandHintRegistry(CommandHintProfiles.defaults());
    }

    public Optional<CommandHintProfile> profileFor(String commandName) {
        if (commandName == null || commandName.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(profilesByCommand.get(commandName.trim().toLowerCase(Locale.ROOT)));
    }

    public Set<SuggestionCategory> expectedCategories(ParsedCommandLine parsedCommandLine) {
        if (parsedCommandLine == null || !parsedCommandLine.hasCommand()) {
            return Set.of(SuggestionCategory.COMMAND);
        }
        return profileFor(parsedCommandLine.commandName())
                .map(profile -> profile.expectedCategories(parsedCommandLine))
                .orElse(Set.of(SuggestionCategory.COMMAND, SuggestionCategory.SUBCOMMAND, SuggestionCategory.FREE_VALUE));
    }

    public Set<AdminCommandFamily> availableFamilies() {
        LinkedHashSet<AdminCommandFamily> families = new LinkedHashSet<>();
        profilesByCommand.values().forEach(profile -> families.add(profile.family()));
        return Set.copyOf(families);
    }
}
