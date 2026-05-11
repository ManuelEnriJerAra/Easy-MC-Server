package controlador.extensions;

public enum ExtensionInstallResolutionState {
    AVAILABLE,
    INCOMPATIBLE,
    INSTALLED_EXACT,
    UPDATE_AVAILABLE,
    FILE_NAME_CONFLICT,
    INSTALLED_WITH_INCOMPLETE_METADATA
}
