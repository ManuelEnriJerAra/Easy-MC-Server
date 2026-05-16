package controlador.automation;

public record AutomationRuleValidation(boolean valid, String message) {
    public static AutomationRuleValidation ok() {
        return new AutomationRuleValidation(true, null);
    }

    public static AutomationRuleValidation error(String message) {
        return new AutomationRuleValidation(false, message);
    }
}
