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
    private String installedVersion;
    private String knownRemoteVersion;
    private String knownRemoteVersionId;
    private Long discoveredAtEpochMillis;
    private Long lastUpdatedAtEpochMillis;
    private Long lastCheckedForUpdatesAtEpochMillis;
    private Boolean enabled;
    private ExtensionUpdateState updateState;
    private String updateMessage;

    public ExtensionLocalMetadata() {
        this.enabled = Boolean.TRUE;
        this.updateState = ExtensionUpdateState.UNKNOWN;
    }
}
