package controlador.extensions;

public enum ExtensionInstallResolutionState {
    AVAILABLE,
    INSTALLED_EXACT,
    UPDATE_AVAILABLE,
    FILE_NAME_CONFLICT,
    INSTALLED_WITH_INCOMPLETE_METADATA
}
