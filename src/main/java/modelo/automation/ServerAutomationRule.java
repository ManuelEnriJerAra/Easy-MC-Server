package modelo.automation;

import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
public class ServerAutomationRule {
    private String id;
    private String displayName;
    private Boolean enabled;
    private AutomationTriggerType triggerType;
    private String timeOfDay;
    private Integer intervalAmount;
    private AutomationIntervalUnit intervalUnit;
    private AutomationActionType actionType;
    private String command;

    public ServerAutomationRule() {
        this.id = UUID.randomUUID().toString();
        this.enabled = true;
        this.intervalUnit = AutomationIntervalUnit.MINUTES;
    }
}
