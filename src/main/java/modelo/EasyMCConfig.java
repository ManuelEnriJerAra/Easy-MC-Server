package modelo;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class EasyMCConfig {
    private String temaClassName;

    public EasyMCConfig() {
    }

    public EasyMCConfig(String temaClassName) {
        this.temaClassName = temaClassName;
    }
}
