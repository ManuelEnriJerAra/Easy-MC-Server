package controlador.extensions;

import modelo.extensions.ExtensionLocalMetadata;
import modelo.extensions.ExtensionRemoteDependency;
import modelo.extensions.ExtensionSource;
import modelo.extensions.ServerExtension;

import java.util.Locale;

public final class ExtensionDependencyMatcher {
    private ExtensionDependencyMatcher() {
    }

    public static boolean matchesInstalledExtension(ExtensionDependency dependency, ServerExtension extension) {
        if (dependency == null || extension == null) {
            return false;
        }
        ExtensionSource source = extension.getSource();
        if (source != null && matchesCandidate(
                dependency,
                source.getProvider(),
                source.getProjectId(),
                extension.getDisplayName(),
                extension.getId())) {
            return true;
        }
        if (matchesCandidate(
                dependency,
                null,
                null,
                extension.getDisplayName(),
                extension.getId())) {
            return true;
        }
        return matchesLocalMetadata(dependency, extension.getLocalMetadata());
    }

    public static boolean matchesInstalledExtension(ExtensionRemoteDependency dependency, ServerExtension extension) {
        if (dependency == null || extension == null) {
            return false;
        }
        ExtensionSource source = extension.getSource();
        if (source != null && matchesCandidate(
                dependency,
                source.getProvider(),
                source.getProjectId(),
                extension.getDisplayName(),
                extension.getId())) {
            return true;
        }
        if (matchesCandidate(
                dependency,
                null,
                null,
                extension.getDisplayName(),
                extension.getId())) {
            return true;
        }
        return matchesLocalMetadata(dependency, extension.getLocalMetadata());
    }

    public static boolean matchesCandidate(ExtensionDependency dependency,
                                           String providerId,
                                           String projectId,
                                           String displayName,
                                           String localId) {
        if (dependency == null) {
            return false;
        }
        return matchesCandidate(
                dependency.providerId(),
                dependency.projectId(),
                dependency.displayName(),
                providerId,
                projectId,
                displayName,
                localId
        );
    }

    public static boolean matchesCandidate(ExtensionRemoteDependency dependency,
                                           String providerId,
                                           String projectId,
                                           String displayName,
                                           String localId) {
        if (dependency == null) {
            return false;
        }
        return matchesCandidate(
                dependency.getProviderId(),
                dependency.getProjectId(),
                dependency.getDisplayName(),
                providerId,
                projectId,
                displayName,
                localId
        );
    }

    public static String normalizedDependencyKey(ExtensionDependency dependency) {
        if (dependency == null) {
            return null;
        }
        return normalizedDependencyKey(dependency.providerId(), dependency.projectId(), dependency.displayName());
    }

    public static String normalizedDependencyKey(ExtensionRemoteDependency dependency) {
        if (dependency == null) {
            return null;
        }
        return normalizedDependencyKey(dependency.getProviderId(), dependency.getProjectId(), dependency.getDisplayName());
    }

    public static String normalizeIdentifier(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = stripJarExtension(value)
                .trim()
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "");
        return normalized.isBlank() ? null : normalized;
    }

    private static boolean matchesLocalMetadata(ExtensionDependency dependency, ExtensionLocalMetadata metadata) {
        if (metadata == null) {
            return false;
        }
        if (matchesCandidate(dependency, null, null, stripJarExtension(metadata.getFileName()), metadata.getFileName())) {
            return true;
        }
        if (metadata.getLocalDependencyDescriptions() != null) {
            for (String localId : metadata.getLocalDependencyDescriptions()) {
                if (matchesCandidate(dependency, null, null, localId, localId)) {
                    return true;
                }
            }
        }
        if (metadata.getDependencies() != null) {
            for (ExtensionRemoteDependency localDependency : metadata.getDependencies()) {
                if (localDependency != null && matchesCandidate(
                        dependency,
                        localDependency.getProviderId(),
                        localDependency.getProjectId(),
                        localDependency.getDisplayName(),
                        localDependency.getProjectId())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matchesLocalMetadata(ExtensionRemoteDependency dependency, ExtensionLocalMetadata metadata) {
        if (metadata == null) {
            return false;
        }
        if (matchesCandidate(dependency, null, null, stripJarExtension(metadata.getFileName()), metadata.getFileName())) {
            return true;
        }
        if (metadata.getLocalDependencyDescriptions() != null) {
            for (String localId : metadata.getLocalDependencyDescriptions()) {
                if (matchesCandidate(dependency, null, null, localId, localId)) {
                    return true;
                }
            }
        }
        if (metadata.getDependencies() != null) {
            for (ExtensionRemoteDependency localDependency : metadata.getDependencies()) {
                if (localDependency != null && matchesCandidate(
                        dependency,
                        localDependency.getProviderId(),
                        localDependency.getProjectId(),
                        localDependency.getDisplayName(),
                        localDependency.getProjectId())) {
                    return true;
                }
            }
        }
        return false;
    }

    private static boolean matchesCandidate(String dependencyProvider,
                                            String dependencyProject,
                                            String dependencyDisplayName,
                                            String providerId,
                                            String projectId,
                                            String displayName,
                                            String localId) {
        String normalizedDependencyProvider = normalizeIdentifier(dependencyProvider);
        String normalizedDependencyProject = normalizeIdentifier(dependencyProject);
        String normalizedCandidateProvider = normalizeIdentifier(providerId);
        String normalizedCandidateProject = normalizeIdentifier(projectId);
        if (normalizedDependencyProvider != null
                && normalizedDependencyProject != null
                && normalizedDependencyProvider.equals(normalizedCandidateProvider)
                && normalizedDependencyProject.equals(normalizedCandidateProject)) {
            return true;
        }

        String normalizedDependencyName = normalizeIdentifier(dependencyDisplayName);
        return identifierMatches(normalizedDependencyProject, localId)
                || identifierMatches(normalizedDependencyProject, displayName)
                || identifierMatches(normalizedDependencyProject, projectId)
                || identifierMatches(normalizedDependencyName, localId)
                || identifierMatches(normalizedDependencyName, displayName)
                || identifierMatches(normalizedDependencyName, projectId);
    }

    private static boolean identifierMatches(String dependencyIdentifier, String candidateValue) {
        if (dependencyIdentifier == null || candidateValue == null || candidateValue.isBlank()) {
            return false;
        }
        String candidate = normalizeIdentifier(candidateValue);
        if (dependencyIdentifier.equals(candidate)) {
            return true;
        }
        String leading = normalizeIdentifier(leadingIdentifier(candidateValue));
        return dependencyIdentifier.length() >= 3 && dependencyIdentifier.equals(leading);
    }

    private static String normalizedDependencyKey(String providerId, String projectId, String displayName) {
        String provider = normalizeIdentifier(providerId);
        String project = normalizeIdentifier(firstNonBlank(projectId, displayName));
        if (provider != null && project != null) {
            return provider + "::" + project;
        }
        return project == null ? null : "local::" + project;
    }

    private static String stripJarExtension(String value) {
        if (value == null) {
            return null;
        }
        return value.replaceAll("(?i)\\.jar$", "");
    }

    private static String leadingIdentifier(String value) {
        if (value == null) {
            return null;
        }
        String trimmed = value.trim();
        int end = trimmed.length();
        for (int i = 0; i < trimmed.length(); i++) {
            char ch = trimmed.charAt(i);
            if (Character.isWhitespace(ch) || ch == '(' || ch == '[' || ch == '<' || ch == '>' || ch == '=' || ch == ':') {
                end = i;
                break;
            }
        }
        return trimmed.substring(0, end);
    }

    private static String firstNonBlank(String first, String second) {
        return first == null || first.isBlank() ? second : first;
    }
}
