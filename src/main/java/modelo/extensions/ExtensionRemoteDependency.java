package modelo.extensions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@JsonIgnoreProperties(ignoreUnknown = true)
public class ExtensionRemoteDependency {
    private String providerId;
    private String projectId;
    private String versionId;
    private String displayName;
    private String dependencyType;
    private Boolean required;
}
