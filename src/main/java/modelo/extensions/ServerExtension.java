package modelo.extensions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.UUID;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ServerExtension {
    private String id;
    private String displayName;
    private String version;
    private String description;
    private String fileName;
    private ServerExtensionType extensionType;
    private ServerPlatform platform;
    private ExtensionSource source;
    private ExtensionInstallState installState;
    private ExtensionLocalMetadata localMetadata;

    public ServerExtension() {
        this.id = UUID.randomUUID().toString();
        this.extensionType = ServerExtensionType.UNKNOWN;
        this.platform = ServerPlatform.UNKNOWN;
        this.source = new ExtensionSource();
        this.installState = ExtensionInstallState.UNKNOWN;
        this.localMetadata = new ExtensionLocalMetadata();
    }

    public String getDisplayName() {
        return displayName;
    }

    public ExtensionSource getSource() {
        return source;
    }

    public ExtensionLocalMetadata getLocalMetadata() {
        return localMetadata;
    }
}
