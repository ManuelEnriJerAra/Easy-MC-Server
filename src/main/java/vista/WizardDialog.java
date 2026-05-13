package vista;

import javax.swing.JComponent;
import java.awt.Dimension;
import java.awt.Window;
import java.util.List;
import java.util.function.BooleanSupplier;
import java.util.function.IntConsumer;
import java.util.function.IntFunction;

/**
 * Compatibility facade kept for builds that still have classes compiled against
 * the old wizard name. New process flows should use {@link ProcessWizardDialog}.
 */
@Deprecated(forRemoval = false)
public final class WizardDialog {
    private WizardDialog() {
    }

    public static boolean show(Window owner, String title, List<Step> steps, Options options) {
        List<ProcessWizardDialog.Step> processSteps = steps == null
                ? List.of()
                : steps.stream()
                .map(Step::toProcessStep)
                .toList();
        return ProcessWizardDialog.show(
                owner,
                title,
                processSteps,
                options == null ? ProcessWizardDialog.Options.defaults() : options.toProcessOptions()
        );
    }

    public record Step(
            String id,
            String title,
            JComponent content,
            BooleanSupplier canContinue,
            IntFunction<String> validate
    ) {
        public static Step of(String id, String title, JComponent content) {
            return new Step(id, title, content, () -> true, ignored -> null);
        }

        private ProcessWizardDialog.Step toProcessStep() {
            return new ProcessWizardDialog.Step(id, title, content, canContinue, validate);
        }
    }

    public record Options(
            Dimension minimumSize,
            String finishTooltip,
            BooleanSupplier confirmCancel,
            IntConsumer stepChanged
    ) {
        public static Options defaults() {
            return new Options(new Dimension(760, 520), "Finalizar", () -> true, ignored -> { });
        }

        private ProcessWizardDialog.Options toProcessOptions() {
            return new ProcessWizardDialog.Options(minimumSize, finishTooltip, confirmCancel, stepChanged);
        }
    }
}
