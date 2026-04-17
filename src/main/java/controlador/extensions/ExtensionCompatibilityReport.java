package controlador.extensions;

import java.util.List;

public record ExtensionCompatibilityReport(
        ExtensionCompatibilityStatus status,
        String summary,
        List<String> reasons
) {
    public ExtensionCompatibilityReport {
        reasons = reasons == null ? List.of() : List.copyOf(reasons);
    }

    public boolean compatible() {
        return status == ExtensionCompatibilityStatus.COMPATIBLE;
    }

    public boolean warning() {
        return status == ExtensionCompatibilityStatus.WARNING;
    }

    public boolean incompatible() {
        return status == ExtensionCompatibilityStatus.INCOMPATIBLE;
    }
}
