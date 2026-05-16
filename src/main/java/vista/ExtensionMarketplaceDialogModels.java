package vista;

import controlador.extensions.ExtensionCatalogEntry;
import controlador.extensions.ExtensionCatalogQuery;
import controlador.extensions.ExtensionCompatibilityStatus;
import controlador.extensions.ExtensionDependency;
import controlador.extensions.ExtensionDownloadPlan;
import modelo.extensions.ServerPlatform;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Locale;

enum ViewState {
    IDLE,
    LOADING,
    READY,
    EMPTY,
    ERROR,
    BLOCKED
}

record SearchViewState(ViewState state, String message, long requestId) {
}

record MarketplaceSearchSpec(ExtensionCatalogQuery query,
                             ProviderFilterOption provider,
                             SideFilterOption sideFilter,
                             boolean compatibilityOnly,
                             SearchSortOption sortOption,
                             int resultLimit,
                             int displayLimit) {
    boolean hasSearchText() {
        return query != null && query.queryText() != null && !query.queryText().isBlank();
    }
}

record DetailViewState(ViewState state,
                       String message,
                       ExtensionCatalogEntry entry,
                       long requestId) {
}

record PreviewViewState(ViewState state,
                        String message,
                        String versionId,
                        long requestId) {
}

record QueueViewState(ViewState state, String message) {
}

record IconViewState(ViewState state, String message) {
}

record LinkRange(int start, int end, String url) {
}

enum VersionMatchType {
    EXACT,
    FAMILY,
    NONE
}

record MarketplaceCompatibilityAssessment(
        ExtensionCompatibilityStatus status,
        String summary,
        List<String> reasons
) {
}

record ProviderFilterOption(String providerId, String displayName) {
    static ProviderFilterOption provider(String providerId, String displayName) {
        return new ProviderFilterOption(providerId, displayName);
    }

    @Override
    public String toString() {
        return displayName;
    }
}

record PlatformFilterOption(String label, ServerPlatform platform, boolean preferred) {
    @Override
    public String toString() {
        return label;
    }
}

enum SideFilterKind {
    ANY,
    CLIENT,
    BOTH,
    SERVER
}

record SideFilterOption(String label, SideFilterKind kind) {
    static SideFilterOption any() {
        return new SideFilterOption("Cualquier lado", SideFilterKind.ANY);
    }

    @Override
    public String toString() {
        return label;
    }
}

record SearchSortOption(String label, String providerSort, boolean localNameSort) {
    @Override
    public String toString() {
        return label;
    }
}

record ResultLimitOption(int limit) {
    @Override
    public String toString() {
        return Integer.toString(limit);
    }
}

record VersionOption(String versionId, String displayName, String meta, boolean compatible) {
    @Override
    public String toString() {
        return displayName;
    }
}

enum QueueState {
    PENDING,
    DOWNLOADING,
    COMPLETED,
    FAILED
}

enum DependencyPromptChoice {
    ADD_ALL,
    ADD_REQUIRED_ONLY,
    CANCEL
}

final class DownloadQueueItem {
    final String providerId;
    final String projectId;
    final String versionId;
    ExtensionDownloadPlan downloadPlan;
    final String iconUrl;
    final String displayName;
    final String versionLabel;
    final String dependencyOfKey;
    List<String> requiredDependencyKeys;
    QueueState state;
    String message;

    DownloadQueueItem(String providerId,
                      String projectId,
                      String versionId,
                      ExtensionDownloadPlan downloadPlan,
                      String iconUrl,
                      String displayName,
                      String versionLabel,
                      QueueState state,
                      String message,
                      String dependencyOfKey,
                      List<String> requiredDependencyKeys) {
        this.providerId = providerId;
        this.projectId = projectId;
        this.versionId = versionId;
        this.downloadPlan = downloadPlan;
        this.iconUrl = iconUrl;
        this.displayName = displayName;
        this.versionLabel = versionLabel;
        this.dependencyOfKey = dependencyOfKey;
        this.requiredDependencyKeys = requiredDependencyKeys == null ? List.of() : List.copyOf(requiredDependencyKeys);
        this.state = state;
        this.message = message;
    }

    String queueKey() {
        return queueEntryKey(providerId, projectId);
    }

    boolean matchesProject(String providerId, String projectId) {
        return this.providerId != null
                && this.projectId != null
                && providerId != null
                && projectId != null
                && this.providerId.equalsIgnoreCase(providerId)
                && this.projectId.equalsIgnoreCase(projectId);
    }

    boolean matchesExactVersion(String providerId, String projectId, String versionId) {
        return matchesProject(providerId, projectId)
                && this.versionId != null
                && versionId != null
                && this.versionId.equalsIgnoreCase(versionId);
    }

    private static String queueEntryKey(String providerId, String projectId) {
        if (providerId == null || providerId.isBlank() || projectId == null || projectId.isBlank()) {
            return null;
        }
        return providerId.trim().toLowerCase(Locale.ROOT) + "::" + projectId.trim().toLowerCase(Locale.ROOT);
    }
}

record QueueAdmission(boolean allowed, DownloadQueueItem existingItem, String message) {
}

record ActionAvailability(boolean canInstallNow, boolean canQueue, String detailMessage) {
}

record DependencyNotice(
        ExtensionDependency dependency,
        String parentDisplayName,
        String parentKey,
        boolean optionalBranch
) {
}

record ResolvedDependency(
        ExtensionDependency dependency,
        ExtensionDownloadPlan plan,
        String parentDisplayName,
        String parentKey,
        boolean requiredByParent,
        boolean optionalBranch,
        List<String> requiredDependencyKeys
) {
    ResolvedDependency {
        requiredDependencyKeys = requiredDependencyKeys == null ? List.of() : List.copyOf(requiredDependencyKeys);
    }
}

record DependencyResolutionResult(
        List<ResolvedDependency> resolvedDependencies,
        List<DependencyNotice> unresolvedRequired,
        List<DependencyNotice> unresolvedOptional,
        List<DependencyNotice> alreadySatisfied,
        List<String> rootRequiredDependencyKeys
) {
    DependencyResolutionResult {
        resolvedDependencies = resolvedDependencies == null ? List.of() : List.copyOf(resolvedDependencies);
        unresolvedRequired = unresolvedRequired == null ? List.of() : List.copyOf(unresolvedRequired);
        unresolvedOptional = unresolvedOptional == null ? List.of() : List.copyOf(unresolvedOptional);
        alreadySatisfied = alreadySatisfied == null ? List.of() : List.copyOf(alreadySatisfied);
        rootRequiredDependencyKeys = rootRequiredDependencyKeys == null ? List.of() : List.copyOf(rootRequiredDependencyKeys);
    }

    static DependencyResolutionResult empty() {
        return new DependencyResolutionResult(List.of(), List.of(), List.of(), List.of(), List.of());
    }

    List<ResolvedDependency> resolvedRequired() {
        return resolvedDependencies.stream()
                .filter(ResolvedDependency::requiredByParent)
                .filter(dependency -> !dependency.optionalBranch())
                .toList();
    }

    List<ResolvedDependency> resolvedOptional() {
        return resolvedDependencies.stream()
                .filter(ResolvedDependency::optionalBranch)
                .toList();
    }

    boolean hasPromptableDependencies() {
        return hasRequiredPrompts() || hasOptionalPrompts();
    }

    boolean hasRequiredPrompts() {
        return resolvedDependencies.stream().anyMatch(dependency -> dependency.requiredByParent() && !dependency.optionalBranch())
                || !unresolvedRequired.isEmpty();
    }

    boolean hasOptionalPrompts() {
        return resolvedDependencies.stream().anyMatch(ResolvedDependency::optionalBranch)
                || !unresolvedOptional.isEmpty();
    }
}

final class DependencyResolutionBuilder {
    final List<ResolvedDependency> resolvedDependencies = new ArrayList<>();
    final Map<String, ResolvedDependency> resolvedByKey = new LinkedHashMap<>();
    final List<DependencyNotice> unresolvedRequired = new ArrayList<>();
    final List<DependencyNotice> unresolvedOptional = new ArrayList<>();
    final List<DependencyNotice> alreadySatisfied = new ArrayList<>();

    boolean hasResolved(String key) {
        return key != null && resolvedByKey.containsKey(key);
    }

    ResolvedDependency resolved(String key) {
        return key == null ? null : resolvedByKey.get(key);
    }

    void addResolved(String key, ResolvedDependency dependency) {
        if (dependency == null) {
            return;
        }
        if (key == null) {
            resolvedDependencies.add(dependency);
            return;
        }
        ResolvedDependency existing = resolvedByKey.get(key);
        if (existing == null) {
            resolvedByKey.put(key, dependency);
            resolvedDependencies.add(dependency);
            return;
        }
        List<String> requiredKeys = new ArrayList<>(existing.requiredDependencyKeys());
        for (String requiredKey : dependency.requiredDependencyKeys()) {
            if (requiredKey != null && !requiredKey.isBlank() && !requiredKeys.contains(requiredKey)) {
                requiredKeys.add(requiredKey);
            }
        }
        ResolvedDependency merged = new ResolvedDependency(
                existing.dependency(),
                existing.plan(),
                existing.parentDisplayName(),
                existing.parentKey(),
                existing.requiredByParent() || dependency.requiredByParent(),
                existing.optionalBranch() && dependency.optionalBranch(),
                requiredKeys
        );
        resolvedByKey.put(key, merged);
        int index = resolvedDependencies.indexOf(existing);
        if (index >= 0) {
            resolvedDependencies.set(index, merged);
        }
    }
    DependencyResolutionResult build(List<String> rootRequiredDependencyKeys) {
        return new DependencyResolutionResult(
                resolvedDependencies,
                unresolvedRequired,
                unresolvedOptional,
                alreadySatisfied,
                rootRequiredDependencyKeys
        );
    }
}

enum QueueProgressPhase {
    STARTING,
    COMPLETED,
    FAILED
}

record QueueProgress(QueueProgressPhase phase, DownloadQueueItem item, ExtensionDownloadPlan plan, String message) {
    static QueueProgress starting(DownloadQueueItem item) {
        return new QueueProgress(QueueProgressPhase.STARTING, item, item == null ? null : item.downloadPlan, null);
    }

    static QueueProgress completed(DownloadQueueItem item, ExtensionDownloadPlan plan) {
        return new QueueProgress(QueueProgressPhase.COMPLETED, item, plan, null);
    }

    static QueueProgress failed(DownloadQueueItem item, ExtensionDownloadPlan plan, String message) {
        return new QueueProgress(QueueProgressPhase.FAILED, item, plan, message);
    }
}
