package com.ptcrys.fpsmatch.common.packet.minimap;

import com.ptcrys.fpsmatch.common.minimap.server.sync.BuiltinRuntimeResourceLoader;
import com.ptcrys.fpsmatch.core.minimap.contract.MinimapHardLimits;
import com.ptcrys.fpsmatch.core.minimap.format.ContainerValidationException;
import com.ptcrys.fpsmatch.core.minimap.model.NamespacedId;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.Resource;
import net.minecraft.server.packs.resources.ResourceManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

final class ForgeBuiltinRuntimeResourceScanner {
    static final String DIRECTORY = "fpsmatch/minimaps";
    private static final String PREFIX = DIRECTORY + "/";
    private static final String BINDING_SUFFIX = ".json";
    private static final String RUNTIME_SUFFIX = ".fpsmapc";

    private ForgeBuiltinRuntimeResourceScanner() {
    }

    static List<BuiltinRuntimeResourceLoader.ResourcePair> scan(
            ResourceManager resourceManager
    ) {
        Objects.requireNonNull(resourceManager, "resourceManager");
        Map<ResourceLocation, ListedResource> selected = new LinkedHashMap<>();
        resourceManager.listResources(
                DIRECTORY,
                location -> location.getPath().endsWith(BINDING_SUFFIX)
                        || location.getPath().endsWith(RUNTIME_SUFFIX)
        ).forEach((location, resource) -> selected.put(
                location,
                listed(resource)
        ));
        return scan(selected);
    }

    static List<BuiltinRuntimeResourceLoader.ResourcePair> scan(
            Map<ResourceLocation, ListedResource> resources
    ) {
        Objects.requireNonNull(resources, "resources");
        if (resources.size() > 2 * MinimapHardLimits.MAX_ZIP_ENTRIES) {
            throw new ContainerValidationException(
                    "Too many builtin minimap resources"
            );
        }
        Map<NamespacedId, PairBuilder> pairs = new LinkedHashMap<>();
        resources.entrySet().stream()
                .sorted(Map.Entry.comparingByKey())
                .forEach(entry -> add(pairs, entry.getKey(), entry.getValue()));
        ArrayList<BuiltinRuntimeResourceLoader.ResourcePair> result =
                new ArrayList<>(pairs.size());
        pairs.entrySet().stream()
                .sorted(Map.Entry.comparingByKey(
                        Comparator.comparing(NamespacedId::toString)
                ))
                .forEach(entry -> result.add(entry.getValue().build(entry.getKey())));
        return List.copyOf(result);
    }

    private static void add(
            Map<NamespacedId, PairBuilder> pairs,
            ResourceLocation location,
            ListedResource resource
    ) {
        Objects.requireNonNull(location, "location");
        Objects.requireNonNull(resource, "resource");
        String path = location.getPath();
        if (!path.startsWith(PREFIX)) {
            throw new ContainerValidationException(
                    "Builtin minimap resource is outside its directory"
            );
        }
        boolean binding = path.endsWith(BINDING_SUFFIX);
        boolean runtime = path.endsWith(RUNTIME_SUFFIX);
        if (binding == runtime) {
            throw new ContainerValidationException(
                    "Builtin minimap resource has an invalid suffix"
            );
        }
        String suffix = binding ? BINDING_SUFFIX : RUNTIME_SUFFIX;
        String localId = path.substring(PREFIX.length(), path.length() - suffix.length());
        NamespacedId resourceId;
        try {
            resourceId = new NamespacedId(location.getNamespace(), localId);
        } catch (IllegalArgumentException invalid) {
            throw new ContainerValidationException(
                    "Builtin minimap resource ID is invalid", invalid
            );
        }
        PairBuilder pair = pairs.computeIfAbsent(resourceId, ignored -> new PairBuilder());
        pair.add(binding, resource);
    }

    private static byte[] readBinding(InputSource source) {
        try (InputStream input = source.open();
             ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            byte[] buffer = new byte[8 * 1024];
            int total = 0;
            while (true) {
                int count = input.read(buffer);
                if (count < 0) {
                    return output.toByteArray();
                }
                if (count == 0) {
                    throw new IOException("Builtin binding stream made no progress");
                }
                if (count > MinimapHardLimits.MAX_BUILTIN_BINDING_BYTES - total) {
                    throw new ContainerValidationException(
                            "Builtin runtime binding exceeds its byte limit"
                    );
                }
                output.write(buffer, 0, count);
                total += count;
            }
        } catch (ContainerValidationException exception) {
            throw exception;
        } catch (IOException | RuntimeException exception) {
            throw new ContainerValidationException(
                    "Unable to read builtin runtime binding", exception
            );
        }
    }

    private static ListedResource listed(Resource resource) {
        return new ListedResource(resource.sourcePackId(), resource::open);
    }

    record ListedResource(String packId, InputSource source) {
        ListedResource {
            if (packId == null || packId.isEmpty()) {
                throw new IllegalArgumentException("packId cannot be empty");
            }
            Objects.requireNonNull(source, "source");
        }
    }

    @FunctionalInterface
    interface InputSource {
        InputStream open() throws IOException;
    }

    private static final class PairBuilder {
        private ListedResource binding;
        private ListedResource runtime;

        private void add(boolean isBinding, ListedResource resource) {
            if (isBinding) {
                if (binding != null) {
                    throw new ContainerValidationException(
                            "Duplicate builtin minimap binding resource"
                    );
                }
                binding = resource;
            } else {
                if (runtime != null) {
                    throw new ContainerValidationException(
                            "Duplicate builtin minimap runtime resource"
                    );
                }
                runtime = resource;
            }
        }

        private BuiltinRuntimeResourceLoader.ResourcePair build(
                NamespacedId resourceId
        ) {
            if (binding == null || runtime == null) {
                throw new ContainerValidationException(
                        "Builtin minimap binding and runtime must be paired"
                );
            }
            if (!binding.packId().equals(runtime.packId())) {
                throw new ContainerValidationException(
                        "Builtin minimap companions must come from the same pack"
                );
            }
            return new BuiltinRuntimeResourceLoader.ResourcePair(
                    resourceId,
                    readBinding(binding.source()),
                    runtime.source()::open
            );
        }
    }
}
