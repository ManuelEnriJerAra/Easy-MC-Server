package modelo.extensions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExtensionSource {
    private ExtensionSourceType type;
    private String provider;
    private String projectId;
    private String versionId;
    private String url;
    private String author;
    private String iconUrl;

    public ExtensionSource() {
        this.type = ExtensionSourceType.UNKNOWN;
    }

    public String getIconUrl() {
        return iconUrl;
    }
}
