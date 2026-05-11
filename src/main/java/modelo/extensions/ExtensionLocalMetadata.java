package modelo.extensions;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Getter;
import lombok.Setter;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

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
    private Long lastMetadataSyncAtEpochMillis;
    private Boolean enabled;
    private ExtensionUpdateState updateState;
    private String updateMessage;
    private Long downloadCount;
    private String clientSide;
    private String serverSide;
    private List<String> authors;
    private List<String> categories;
    private List<String> supportedLoaders;
    private List<String> supportedMinecraftVersions;
    private String localIconUrl;
    private String localIconPath;
    private String websiteUrl;
    private String issuesUrl;
    private String licenseName;
    private List<String> embeddedMetadataFiles;
    private Map<String, String> manifestAttributes;
    private List<ExtensionRemoteDependency> dependencies;
    private List<String> localDependencyDescriptions;

    public ExtensionLocalMetadata() {
        this.enabled = Boolean.TRUE;
        this.updateState = ExtensionUpdateState.UNKNOWN;
        this.authors = new ArrayList<>();
        this.categories = new ArrayList<>();
        this.supportedLoaders = new ArrayList<>();
        this.supportedMinecraftVersions = new ArrayList<>();
        this.embeddedMetadataFiles = new ArrayList<>();
        this.manifestAttributes = new LinkedHashMap<>();
        this.dependencies = new ArrayList<>();
        this.localDependencyDescriptions = new ArrayList<>();
    }
}
