package modelo.extensions;

public enum ServerExtensionType {
    UNKNOWN,
    MOD,
    PLUGIN;

    public String getDisplayName() {
        return switch (this) {
            case MOD -> "Mod";
            case PLUGIN -> "Plugin";
            case UNKNOWN -> "Extensión";
        };
    }

    public String getPluralDisplayName() {
        return switch (this) {
            case MOD -> "Mods";
            case PLUGIN -> "Plugins";
            case UNKNOWN -> "Extensiones";
        };
    }

    public boolean isMod() {
        return this == MOD;
    }

    public boolean isPlugin() {
        return this == PLUGIN;
    }

    public boolean isKnownType() {
        return this != UNKNOWN;
    }
}
