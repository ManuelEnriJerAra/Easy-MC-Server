package vista;

import controlador.extensions.ExtensionDependency;
import controlador.extensions.ExtensionDownloadPlan;
import controlador.extensions.ExtensionInstallResolution;
import controlador.extensions.ExtensionInstallResolutionState;
import controlador.GestorServidores;
import modelo.Server;
import modelo.extensions.ExtensionLocalMetadata;
import modelo.extensions.ExtensionRemoteDependency;
import modelo.extensions.ExtensionSourceType;
import modelo.extensions.ExtensionSource;
import modelo.extensions.ServerExtension;
import modelo.extensions.ServerExtensionType;
import modelo.extensions.ServerPlatform;
import org.junit.jupiter.api.Test;

import javax.swing.DefaultListModel;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import static org.assertj.core.api.Assertions.assertThat;

class ExtensionMarketplaceDependencyTest {
    @Test
    void installedDependencyMatchesByProviderProjectLocalIdAndName() {
        ExtensionDependency dependency = new ExtensionDependency(
                "hangar",
                "ViaVersion",
                null,
                "ViaVersion",
                "required",
                true
        );
        ServerExtension installed = new ServerExtension();
        installed.setId("viaversion");
        installed.setDisplayName("Via Version");
        ExtensionSource source = new ExtensionSource();
        source.setProvider("hangar");
        source.setProjectId("ViaVersion");
        installed.setSource(source);

        assertThat(ExtensionMarketplaceDialog.dependencyMatchesInstalledExtension(dependency, installed)).isTrue();

        ExtensionDependency byLocalId = new ExtensionDependency(null, "viaversion", null, null, "required", true);
        assertThat(ExtensionMarketplaceDialog.dependencyMatchesInstalledExtension(byLocalId, installed)).isTrue();

        ExtensionDependency byDisplayName = new ExtensionDependency(null, null, null, "Via Version", "required", true);
        assertThat(ExtensionMarketplaceDialog.dependencyMatchesInstalledExtension(byDisplayName, installed)).isTrue();
    }

    @Test
    void installedDependencyMatchesMetadataDependencyIdFallback() {
        ExtensionDependency dependency = new ExtensionDependency(null, "ProtocolLib", null, "ProtocolLib", "required", true);
        ServerExtension installed = new ServerExtension();
        installed.setDisplayName("Protocol Lib");
        ExtensionLocalMetadata metadata = new ExtensionLocalMetadata();
        metadata.setLocalDependencyDescriptions(List.of("ProtocolLib"));
        ExtensionRemoteDependency remoteDependency = new ExtensionRemoteDependency();
        remoteDependency.setProviderId("hangar");
        remoteDependency.setProjectId("ProtocolLib");
        remoteDependency.setDisplayName("ProtocolLib");
        metadata.setDependencies(List.of(remoteDependency));
        installed.setLocalMetadata(metadata);

        assertThat(ExtensionMarketplaceDialog.dependencyMatchesInstalledExtension(dependency, installed)).isTrue();
    }

    @Test
    void queuedDependencyMatchesProviderProjectAndNormalizedDisplayName() {
        ExtensionDependency dependency = new ExtensionDependency("modrinth", "luckperms", null, "Luck Perms", "required", true);

        assertThat(ExtensionMarketplaceDialog.dependencyMatchesCandidate(
                dependency,
                "modrinth",
                "luckperms",
                "LuckPerms",
                null
        )).isTrue();
        assertThat(ExtensionMarketplaceDialog.dependencyMatchesCandidate(
                new ExtensionDependency(null, null, null, "Luck Perms", "required", true),
                null,
                null,
                "LuckPerms",
                null
        )).isTrue();
    }

    @Test
    void dependencyCycleKeyMatchesSlugAndDisplayNameVariants() {
        String rootKey = ExtensionMarketplaceDialog.dependencyResolutionCycleKey("hangar", "first-plugin");
        String optionalBackReferenceKey = ExtensionMarketplaceDialog.dependencyResolutionCycleKey("Hangar", "First Plugin");

        assertThat(optionalBackReferenceKey).isEqualTo(rootKey);
    }

    @Test
    void optionalDependencyCycleDoesNotResolveBackReference() throws Exception {
        ExtensionDownloadPlan pluginA = plan(
                "plugin-a",
                "Plugin A",
                List.of(new ExtensionDependency("hangar", "plugin-b", null, "Plugin B", "optional", false))
        );
        ExtensionDownloadPlan pluginB = plan(
                "plugin-b",
                "Plugin B",
                List.of(new ExtensionDependency("hangar", "plugin-a", null, "Plugin A", "optional", false))
        );
        FakeGestorServidores gestor = new FakeGestorServidores(Map.of("plugin-b", pluginB));
        ExtensionMarketplaceDialog dialog = dialogForDependencyResolution(gestor);

        Object result = resolveDependencies(dialog, pluginA);
        List<?> resolved = invokeList(result, "resolvedDependencies");

        assertThat(gestor.requestedProjects).containsExactly("plugin-b");
        assertThat(resolved).hasSize(1);
    }

    @Test
    void versionOnlyBackReferenceIsTreatedAsCycleAlias() throws Exception {
        ExtensionDownloadPlan pluginA = plan(
                "plugin-a",
                "Plugin A",
                List.of(new ExtensionDependency("hangar", "plugin-b", null, "Plugin B", "optional", false))
        );
        ExtensionDownloadPlan pluginB = plan(
                "plugin-b",
                "Plugin B",
                List.of(new ExtensionDependency("hangar", null, "version-plugin-a", null, "optional", false))
        );
        FakeGestorServidores gestor = new FakeGestorServidores(Map.of("plugin-b", pluginB));
        ExtensionMarketplaceDialog dialog = dialogForDependencyResolution(gestor);

        Object result = resolveDependencies(dialog, pluginA);
        List<?> resolved = invokeList(result, "resolvedDependencies");

        assertThat(gestor.requestedProjects).containsExactly("plugin-b");
        assertThat(resolved).hasSize(1);
    }

    @Test
    void duplicateDependencyIsResolvedOnceAndPromotedToRequired() throws Exception {
        ExtensionDownloadPlan pluginA = plan(
                "plugin-a",
                "Plugin A",
                List.of(
                        new ExtensionDependency("hangar", "plugin-b", null, "Plugin B", "optional", false),
                        new ExtensionDependency("hangar", "plugin-b", null, "Plugin B", "required", true)
                )
        );
        ExtensionDownloadPlan pluginB = plan("plugin-b", "Plugin B", List.of());
        FakeGestorServidores gestor = new FakeGestorServidores(Map.of("plugin-b", pluginB));
        ExtensionMarketplaceDialog dialog = dialogForDependencyResolution(gestor);

        Object result = resolveDependencies(dialog, pluginA);
        List<?> resolved = invokeList(result, "resolvedDependencies");

        assertThat(gestor.requestedProjects).containsExactly("plugin-b");
        assertThat(resolved).hasSize(1);
        assertThat(invokeBoolean(resolved.getFirst(), "requiredByParent")).isTrue();
        assertThat(invokeBoolean(resolved.getFirst(), "optionalBranch")).isFalse();
    }

    private static ExtensionMarketplaceDialog dialogForDependencyResolution(FakeGestorServidores gestor) throws Exception {
        ExtensionMarketplaceDialog dialog = allocateDialog();
        Server server = new Server();
        server.setPlatform(ServerPlatform.PAPER);
        setField(dialog, "gestorServidores", gestor);
        setField(dialog, "server", server);
        setField(dialog, "queueModel", new DefaultListModel<>());
        setField(dialog, "downloadPlanCache", new ConcurrentHashMap<String, Optional<ExtensionDownloadPlan>>());
        setField(dialog, "installResolutionCache", new ConcurrentHashMap<String, ExtensionInstallResolution>());
        return dialog;
    }

    private static Object resolveDependencies(ExtensionMarketplaceDialog dialog, ExtensionDownloadPlan plan) throws Exception {
        Method method = ExtensionMarketplaceDialog.class.getDeclaredMethod("resolveDependenciesForPlan", ExtensionDownloadPlan.class);
        method.setAccessible(true);
        return method.invoke(dialog, plan);
    }

    @SuppressWarnings("unchecked")
    private static List<?> invokeList(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return (List<?>) method.invoke(target);
    }

    private static boolean invokeBoolean(Object target, String methodName) throws Exception {
        Method method = target.getClass().getDeclaredMethod(methodName);
        method.setAccessible(true);
        return (boolean) method.invoke(target);
    }

    private static ExtensionMarketplaceDialog allocateDialog() throws Exception {
        Field field = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        sun.misc.Unsafe unsafe = (sun.misc.Unsafe) field.get(null);
        return (ExtensionMarketplaceDialog) unsafe.allocateInstance(ExtensionMarketplaceDialog.class);
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = ExtensionMarketplaceDialog.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static ExtensionDownloadPlan plan(String projectId, String displayName, List<ExtensionDependency> dependencies) {
        return new ExtensionDownloadPlan(
                "hangar",
                projectId,
                "version-" + projectId,
                displayName,
                "author",
                "description",
                "1.0.0",
                null,
                projectId + ".jar",
                "https://example.com/" + projectId + ".jar",
                null,
                null,
                null,
                null,
                0L,
                "unsupported",
                "required",
                Set.of(),
                ExtensionSourceType.HANGAR,
                ServerExtensionType.PLUGIN,
                ServerPlatform.PAPER,
                "1.21.1",
                true,
                "ready",
                dependencies
        );
    }

    private static final class FakeGestorServidores extends GestorServidores {
        private final Map<String, ExtensionDownloadPlan> plans;
        private final List<String> requestedProjects = new ArrayList<>();

        private FakeGestorServidores(Map<String, ExtensionDownloadPlan> plans) {
            this.plans = plans;
        }

        @Override
        public ExtensionDownloadPlan prepararDescargaExtensionExterna(String providerId,
                                                                       String projectId,
                                                                       String versionId,
                                                                       Server server) {
            requestedProjects.add(projectId);
            return plans.get(projectId);
        }

        @Override
        public ExtensionInstallResolution evaluarInstalacionExterna(Server server, ExtensionDownloadPlan downloadPlan) {
            return new ExtensionInstallResolution(
                    ExtensionInstallResolutionState.AVAILABLE,
                    null,
                    downloadPlan == null ? null : downloadPlan.fileName(),
                    downloadPlan == null ? null : downloadPlan.versionNumber(),
                    null,
                    "available"
            );
        }
    }
}
