package controlador.console.content;

/**
 * Tipo de fuente potencial de contenido custom para autocompletado.
 */
public enum ContentSourceKind {
    MOD_JAR,
    PLUGIN_JAR,
    DATAPACK_DIRECTORY,
    DATAPACK_NAMESPACE,
    KUBEJS_DIRECTORY,
    CONFIG_DIRECTORY,
    RESOURCEPACK_HINT,
    HELPER_EXPORT,
    UNKNOWN
}
