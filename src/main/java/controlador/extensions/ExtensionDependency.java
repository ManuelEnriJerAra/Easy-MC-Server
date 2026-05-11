package controlador.extensions;

public record ExtensionDependency(
        String providerId,
        String projectId,
        String versionId,
        String displayName,
        String dependencyType,
        boolean required
) {
}
