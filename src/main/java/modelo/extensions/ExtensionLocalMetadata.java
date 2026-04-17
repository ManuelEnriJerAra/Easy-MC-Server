package modelo.extensions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExtensionLocalMetadata {
    private String relativePath;
    private String fileName;
    private Long fileSizeBytes;
    private String sha256;
    private String minecraftVersionConstraint;
    private Long discoveredAtEpochMillis;
    private Long lastUpdatedAtEpochMillis;
    private Boolean enabled;

    public ExtensionLocalMetadata() {
        this.enabled = Boolean.TRUE;
    }
}
