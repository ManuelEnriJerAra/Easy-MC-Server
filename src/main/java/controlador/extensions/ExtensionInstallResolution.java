package controlador.extensions;

import modelo.extensions.ServerExtension;

public record ExtensionInstallResolution(
        ExtensionInstallResolutionState state,
        ServerExtension installedExtension,
        String targetFileName,
        String requestedVersion,
        String installedVersion,
        String message
) {
    public ExtensionInstallResolution {
        state = state == null ? ExtensionInstallResolutionState.AVAILABLE : state;
    }

    public boolean alreadyInstalled() {
        return switch (state) {
            case INSTALLED_EXACT,
                 UPDATE_AVAILABLE,
                 INSTALLED_WITH_INCOMPLETE_METADATA -> true;
            default -> false;
        };
    }

    public boolean exactVersionInstalled() {
        return state == ExtensionInstallResolutionState.INSTALLED_EXACT;
    }

    public boolean updateAvailable() {
        return state == ExtensionInstallResolutionState.UPDATE_AVAILABLE;
    }

    public boolean fileNameConflict() {
        return state == ExtensionInstallResolutionState.FILE_NAME_CONFLICT;
    }

    public boolean incompleteMetadataMatch() {
        return state == ExtensionInstallResolutionState.INSTALLED_WITH_INCOMPLETE_METADATA;
    }

    public boolean blocksInstall() {
        return state != ExtensionInstallResolutionState.AVAILABLE;
    }
}
