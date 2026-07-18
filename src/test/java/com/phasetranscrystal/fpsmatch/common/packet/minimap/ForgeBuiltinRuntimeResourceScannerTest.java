package com.phasetranscrystal.fpsmatch.common.packet.minimap;

import com.phasetranscrystal.fpsmatch.common.minimap.server.sync.BuiltinRuntimeResourceLoader;
import com.phasetranscrystal.fpsmatch.common.minimap.server.sync.BuiltinRuntimeCatalog;
import com.phasetranscrystal.fpsmatch.common.minimap.server.sync.BuiltinRuntimeMapRegistry;
import com.phasetranscrystal.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.phasetranscrystal.fpsmatch.core.minimap.format.BuiltinRuntimeBindingCodec;
import com.phasetranscrystal.fpsmatch.core.minimap.format.ContainerValidationException;
import com.phasetranscrystal.fpsmatch.core.minimap.format.MinimapContainerFixtures;
import com.phasetranscrystal.fpsmatch.core.minimap.format.RuntimeMapReader;
import com.phasetranscrystal.fpsmatch.core.minimap.model.BuiltinRuntimeBinding;
import com.phasetranscrystal.fpsmatch.core.minimap.model.NamespacedId;
import com.phasetranscrystal.fpsmatch.core.minimap.storage.MinimapStorageFixtures;
import com.phasetranscrystal.fpsmatch.core.minimap.wire.WireIdentity;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.ByteArrayInputStream;
import java.lang.reflect.Proxy;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ForgeBuiltinRuntimeResourceScannerTest {
    @TempDir
    java.nio.file.Path temp;

    @Test
    void pairsNestedCompanionsFromTheSamePackInStableOrder() throws Exception {
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(0L);
        byte[] binding = BuiltinRuntimeBindingCodec.write(binding(pair.runtime()));
        Map<ResourceLocation, ForgeBuiltinRuntimeResourceScanner.ListedResource>
                resources = new LinkedHashMap<>();
        resources.put(
                new ResourceLocation(
                        "zeta", "fpsmatch/minimaps/maps/dust2.fpsmapc"
                ),
                listed("pack-a", pair.runtime())
        );
        resources.put(
                new ResourceLocation(
                        "zeta", "fpsmatch/minimaps/maps/dust2.json"
                ),
                listed("pack-a", binding)
        );

        List<BuiltinRuntimeResourceLoader.ResourcePair> scanned =
                ForgeBuiltinRuntimeResourceScanner.scan(resources);

        assertEquals(1, scanned.size());
        assertEquals(
                NamespacedId.parse("zeta:maps/dust2"),
                scanned.get(0).resourceId()
        );
        assertArrayEquals(binding, scanned.get(0).bindingBytes());
        assertArrayEquals(pair.runtime(),
                scanned.get(0).runtimeSource().open().readAllBytes());
    }

    @Test
    void scansTheFinalResourcesSelectedByTheServerResourceManager()
            throws Exception {
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(0L);
        byte[] binding = BuiltinRuntimeBindingCodec.write(binding(pair.runtime()));
        Map<ResourceLocation, Resource> resources = Map.of(
                new ResourceLocation(
                        "fpsmatch", "fpsmatch/minimaps/dust2.json"
                ), resource("pack-a", binding),
                new ResourceLocation(
                        "fpsmatch", "fpsmatch/minimaps/dust2.fpsmapc"
                ), resource("pack-a", pair.runtime())
        );

        List<BuiltinRuntimeResourceLoader.ResourcePair> scanned =
                ForgeBuiltinRuntimeResourceScanner.scan(
                        resourceManager(resources)
                );

        assertEquals(1, scanned.size());
        assertArrayEquals(binding, scanned.get(0).bindingBytes());
        assertArrayEquals(pair.runtime(),
                scanned.get(0).runtimeSource().open().readAllBytes());
    }

    @Test
    void reloadsCatalogFromServerResourcesAndKeepsLastGoodSnapshotOnFailure()
            throws Exception {
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(0L);
        BuiltinRuntimeBinding declaration = binding(pair.runtime());
        byte[] binding = BuiltinRuntimeBindingCodec.write(declaration);
        ResourceLocation json = new ResourceLocation(
                "fpsmatch", "fpsmatch/minimaps/dust2.json"
        );
        ResourceLocation runtime = new ResourceLocation(
                "fpsmatch", "fpsmatch/minimaps/dust2.fpsmapc"
        );
        BuiltinRuntimeCatalog catalog = new BuiltinRuntimeCatalog(
                new BuiltinRuntimeResourceLoader()
        );
        WireIdentity.MapTarget target = new WireIdentity.MapTarget(
                declaration.binding(), declaration.dimension()
        );

        ForgeMinimapServerRuntimeRegistration.reloadBuiltinCatalog(
                catalog,
                temp.resolve("cache"),
                resourceManager(Map.of(
                        json, resource("pack-a", binding),
                        runtime, resource("pack-a", pair.runtime())
                ))
        );
        BuiltinRuntimeMapRegistry lastGood = catalog.snapshot();
        org.junit.jupiter.api.Assertions.assertTrue(
                lastGood.find(target).isPresent()
        );

        assertThrows(ContainerValidationException.class, () ->
                ForgeMinimapServerRuntimeRegistration.reloadBuiltinCatalog(
                        catalog,
                        temp.resolve("cache"),
                        resourceManager(Map.of(
                                json, resource("pack-a", binding)
                        ))
                ));
        org.junit.jupiter.api.Assertions.assertSame(lastGood, catalog.snapshot());
    }

    @Test
    void rejectsOrphansCrossPackPairsInvalidPathsAndOversizedBindings()
            throws Exception {
        MinimapStorageFixtures.Pair pair = MinimapStorageFixtures.validPair(0L);
        byte[] binding = BuiltinRuntimeBindingCodec.write(binding(pair.runtime()));
        ResourceLocation json = new ResourceLocation(
                "fpsmatch", "fpsmatch/minimaps/dust2.json"
        );
        ResourceLocation runtime = new ResourceLocation(
                "fpsmatch", "fpsmatch/minimaps/dust2.fpsmapc"
        );

        assertThrows(ContainerValidationException.class, () ->
                ForgeBuiltinRuntimeResourceScanner.scan(Map.of(
                        json, listed("pack-a", binding)
                )));
        assertThrows(ContainerValidationException.class, () ->
                ForgeBuiltinRuntimeResourceScanner.scan(Map.of(
                        json, listed("pack-a", binding),
                        runtime, listed("pack-b", pair.runtime())
                )));
        assertThrows(ContainerValidationException.class, () ->
                ForgeBuiltinRuntimeResourceScanner.scan(Map.of(
                        new ResourceLocation(
                                "fpsmatch", "fpsmatch/minimaps/.json"
                        ), listed("pack-a", binding),
                        new ResourceLocation(
                                "fpsmatch", "fpsmatch/minimaps/.fpsmapc"
                        ), listed("pack-a", pair.runtime())
                )));
        assertThrows(ContainerValidationException.class, () ->
                ForgeBuiltinRuntimeResourceScanner.scan(Map.of(
                        json, listed("pack-a", new byte[
                                MinimapHardLimits.MAX_BUILTIN_BINDING_BYTES + 1
                        ]),
                        runtime, listed("pack-a", pair.runtime())
                )));
    }

    private static ForgeBuiltinRuntimeResourceScanner.ListedResource listed(
            String pack,
            byte[] bytes
    ) {
        return new ForgeBuiltinRuntimeResourceScanner.ListedResource(
                pack, () -> new ByteArrayInputStream(bytes)
        );
    }

    private static Resource resource(String packId, byte[] bytes) {
        PackResources pack = (PackResources) Proxy.newProxyInstance(
                PackResources.class.getClassLoader(),
                new Class<?>[]{PackResources.class},
                (proxy, method, arguments) -> {
                    if (method.getName().equals("packId")) {
                        return packId;
                    }
                    if (method.getReturnType() == boolean.class) {
                        return false;
                    }
                    return null;
                }
        );
        return new Resource(pack, () -> new ByteArrayInputStream(bytes));
    }

    private static ResourceManager resourceManager(
            Map<ResourceLocation, Resource> resources
    ) {
        return new ResourceManager() {
            @Override
            public Set<String> getNamespaces() {
                return Set.of("fpsmatch");
            }

            @Override
            public Optional<Resource> getResource(ResourceLocation location) {
                return Optional.ofNullable(resources.get(location));
            }

            @Override
            public List<Resource> getResourceStack(ResourceLocation location) {
                return getResource(location).stream().toList();
            }

            @Override
            public Map<ResourceLocation, Resource> listResources(
                    String directory,
                    Predicate<ResourceLocation> filter
            ) {
                assertEquals(ForgeBuiltinRuntimeResourceScanner.DIRECTORY, directory);
                return resources.entrySet().stream()
                        .filter(entry -> filter.test(entry.getKey()))
                        .collect(java.util.stream.Collectors.toMap(
                                Map.Entry::getKey,
                                Map.Entry::getValue
                        ));
            }

            @Override
            public Map<ResourceLocation, List<Resource>> listResourceStacks(
                    String directory,
                    Predicate<ResourceLocation> filter
            ) {
                throw new UnsupportedOperationException();
            }

            @Override
            public Stream<PackResources> listPacks() {
                return Stream.empty();
            }
        };
    }

    private static BuiltinRuntimeBinding binding(byte[] runtimeBytes) throws Exception {
        try (var runtime = RuntimeMapReader.read(runtimeBytes)) {
            return new BuiltinRuntimeBinding(
                    runtime.manifest().binding(),
                    MinimapContainerFixtures.sourceDefinition().manifest().dimension(),
                    runtime.manifest().documentId(),
                    runtime.runtimeHash()
            );
        }
    }
}
