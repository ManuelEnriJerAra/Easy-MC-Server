package controlador.extensions;

import modelo.Server;
import modelo.extensions.ServerExtension;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class ExtensionCatalogService {
    private static final Logger LOGGER = Logger.getLogger(ExtensionCatalogService.class.getName());
    private final ExtensionCatalogRegistry registry;

    public ExtensionCatalogService() {
        this(ExtensionCatalogRegistry.defaultRegistry());
    }

    public ExtensionCatalogService(ExtensionCatalogRegistry registry) {
        this.registry = registry == null ? ExtensionCatalogRegistry.defaultRegistry() : registry;
    }

    public List<ExtensionCatalogProviderDescriptor> getAvailableProviders() {
        return registry.describeProviders();
    }

    public ExtensionCatalogQuery buildQueryForServer(Server server, String queryText, int limit) {
        return ServerExtensionQueryFactory.forServer(server, queryText, limit);
    }

    public List<ExtensionCatalogEntry> search(ExtensionCatalogQuery query) throws IOException {
        ExtensionCatalogQuery normalized = normalizeQuery(query);
        List<ExtensionCatalogEntry> aggregated = new ArrayList<>();
        IOException firstFailure = null;
        for (ExtensionCatalogProvider provider : registry.providers()) {
            if (!provider.supportsSearch()) {
                continue;
            }
            try {
                aggregated.addAll(provider.search(normalized));
            } catch (IOException ex) {
                if (firstFailure == null) {
                    firstFailure = ex;
                }
                LOGGER.log(Level.WARNING, "Fallo al buscar en el proveedor " + provider.getProviderId(), ex);
            } catch (RuntimeException ex) {
                if (firstFailure == null) {
                    firstFailure = new IOException("El proveedor " + provider.getProviderId() + " ha devuelto una respuesta invalida.", ex);
                }
                LOGGER.log(Level.WARNING, "Fallo inesperado al buscar en el proveedor " + provider.getProviderId(), ex);
            }
        }
        if (aggregated.isEmpty() && firstFailure != null) {
            throw new IOException("No se ha podido consultar ningun proveedor del marketplace.", firstFailure);
        }
        return aggregated.stream()
                .sorted(Comparator
                        .comparing(ExtensionCatalogEntry::providerId, String.CASE_INSENSITIVE_ORDER)
                        .thenComparing(ExtensionCatalogEntry::displayName, String.CASE_INSENSITIVE_ORDER))
                .limit(normalized.limit())
                .toList();
    }

    public Optional<ExtensionCatalogDetails> getDetails(String providerId,
                                                        String projectId,
                                                        ExtensionCatalogQuery query) throws IOException {
        return resolveProvider(providerId)
                .flatMap(provider -> fetchDetails(provider, projectId, normalizeQuery(query)));
    }

    public Optional<ExtensionDownloadPlan> resolveDownload(String providerId,
                                                           String projectId,
                                                           String versionId,
                                                           Server server) throws IOException {
        return resolveProvider(providerId)
                .flatMap(provider -> fetchDownload(provider, projectId, versionId, server));
    }

    public List<ExtensionUpdateCandidate> findUpdates(Server server) throws IOException {
        List<ServerExtension> installedExtensions = server == null || server.getExtensions() == null
                ? List.of()
                : List.copyOf(server.getExtensions());

        List<ExtensionUpdateCandidate> candidates = new ArrayList<>();
        IOException firstFailure = null;
        for (ExtensionCatalogProvider provider : registry.providers()) {
            try {
                candidates.addAll(provider.findUpdates(server, installedExtensions));
            } catch (IOException ex) {
                if (firstFailure == null) {
                    firstFailure = ex;
                }
                LOGGER.log(Level.WARNING, "Fallo al buscar actualizaciones en el proveedor " + provider.getProviderId(), ex);
            } catch (RuntimeException ex) {
                if (firstFailure == null) {
                    firstFailure = new IOException("El proveedor " + provider.getProviderId() + " ha fallado al revisar actualizaciones.", ex);
                }
                LOGGER.log(Level.WARNING, "Fallo inesperado al buscar actualizaciones en el proveedor " + provider.getProviderId(), ex);
            }
        }
        if (candidates.isEmpty() && firstFailure != null && !installedExtensions.isEmpty()) {
            throw new IOException("No se ha podido comprobar el estado remoto de las extensiones instaladas.", firstFailure);
        }
        return candidates;
    }

    private Optional<ExtensionCatalogProvider> resolveProvider(String providerId) {
        return registry.findById(providerId);
    }

    private Optional<ExtensionCatalogDetails> fetchDetails(ExtensionCatalogProvider provider,
                                                           String projectId,
                                                           ExtensionCatalogQuery query) {
        try {
            return provider.getDetails(projectId, query);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Fallo al cargar detalles desde " + provider.getProviderId(), ex);
            return Optional.empty();
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Fallo inesperado al cargar detalles desde " + provider.getProviderId(), ex);
            return Optional.empty();
        }
    }

    private Optional<ExtensionDownloadPlan> fetchDownload(ExtensionCatalogProvider provider,
                                                          String projectId,
                                                          String versionId,
                                                          Server server) {
        try {
            return provider.resolveDownload(projectId, versionId, server);
        } catch (IOException ex) {
            LOGGER.log(Level.WARNING, "Fallo al resolver descarga desde " + provider.getProviderId(), ex);
            return Optional.empty();
        } catch (RuntimeException ex) {
            LOGGER.log(Level.WARNING, "Fallo inesperado al resolver descarga desde " + provider.getProviderId(), ex);
            return Optional.empty();
        }
    }

    private ExtensionCatalogQuery normalizeQuery(ExtensionCatalogQuery query) {
        return query == null
                ? new ExtensionCatalogQuery(null, null, null, null, 20)
                : query;
    }
}
