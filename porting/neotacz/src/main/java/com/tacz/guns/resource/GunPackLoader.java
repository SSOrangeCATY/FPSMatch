package com.tacz.guns.resource;

import com.google.gson.JsonIOException;
import com.google.gson.JsonSyntaxException;
import com.tacz.guns.GunMod;
import com.tacz.guns.api.resource.ResourceManager;
import com.tacz.guns.config.PreLoadConfig;
import com.tacz.guns.util.GetJarResources;
import net.minecraft.SharedConstants;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.server.packs.AbstractPackResources;
import net.minecraft.server.packs.FilePackResources;
import net.minecraft.server.packs.PackLocationInfo;
import net.minecraft.server.packs.PackResources;
import net.minecraft.server.packs.PackSelectionConfig;
import net.minecraft.server.packs.PackType;
import net.minecraft.server.packs.PathPackResources;
import net.minecraft.server.packs.metadata.MetadataSectionType;
import net.minecraft.server.packs.metadata.pack.PackMetadataSection;
import net.minecraft.server.packs.repository.Pack;
import net.minecraft.server.packs.repository.PackSource;
import net.minecraft.server.packs.repository.RepositorySource;
import net.minecraft.server.packs.resources.IoSupplier;
import net.neoforged.fml.jarcontents.JarResource;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.ModList;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforgespi.language.IModInfo;
import org.apache.logging.log4j.Marker;
import org.apache.logging.log4j.MarkerManager;
import org.apache.maven.artifact.versioning.ArtifactVersion;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public enum GunPackLoader implements RepositorySource {
    INSTANCE;
    private static final Marker MARKER = MarkerManager.getMarker("GunPackFinder");
    public PackType packType;
    private boolean firstLoad = true;


    @Override
    public void loadPacks(Consumer<Pack> pOnLoad) {
        Pack extensionsPack = discoverExtensions();
        if (extensionsPack != null) {
            pOnLoad.accept(extensionsPack);
        }
    }

    public Pack discoverExtensions() {
        Path resourcePacksPath = FMLPaths.GAMEDIR.get().resolve("tacz");
        File folder = resourcePacksPath.toFile();
        if (!folder.isDirectory()) {
            try {
                Files.createDirectories(folder.toPath());
            } catch (Exception e) {
                GunMod.LOGGER.warn(MARKER, "Failed to init tacz resource directory...", e);
                return null;
            }
        }

        // 确保配置文件加载，这个阶段将比标准的forge配置文件加载早
        PreLoadConfig.load(resourcePacksPath);

        // 仅在第一次加载时复制默认资源包
        if (firstLoad) {
            if (!PreLoadConfig.override.get()) {
                for (ResourceManager.ExtraEntry entry : ResourceManager.EXTRA_ENTRIES) {
                    GetJarResources.copyModDirectory(entry.modMainClass(), entry.srcPath(), resourcePacksPath, entry.extraDirName());
                }
            }
            firstLoad = false;
        }

        GunMod.LOGGER.info(MARKER, "Start scanning for gun packs in {}", resourcePacksPath);
        List<GunPack> gunPacks = scanExtensions(resourcePacksPath);
        GunMod.LOGGER.info(MARKER, "Found {} possible gunpack(s) and added them to resource set.", gunPacks.size());

        PackLocationInfo locationInfo = new PackLocationInfo(
                "tacz_resources",
                Component.literal("TACZ Resources"),
                PackSource.BUILT_IN,
                Optional.empty()
        );
        PackMetadataSection metadata = new PackMetadataSection(
                Component.translatable("tacz.resources.modresources"),
                SharedConstants.getCurrentVersion().packVersion(packType).minorRange()
        );
        return Pack.readMetaAndCreate(
                locationInfo,
                new TaczResourcesSupplier(gunPacks, metadata),
                packType,
                new PackSelectionConfig(true, Pack.Position.BOTTOM, false)
        );
    }

    public static @Nullable IoSupplier<InputStream> getModIcon(String modId) {
        Optional<? extends ModContainer> m = ModList.get().getModContainerById(modId);
        if (m.isPresent()) {
            IModInfo mod = m.get().getModInfo();
            String logoFile = mod.getLogoFile().orElse("logo.png");
            JarResource logoResource = mod.getOwningFile().getFile().getContents().get(logoFile);
            if (logoResource != null) {
                return logoResource.retain()::open;
            }
        }

        return null;
    }

    // 检查路径中的config.json
    // 应该不会在用这个了，先保留
//    private static RepositoryConfig checkConfig(Path resourcePacksPath) {
//        Path configPath = resourcePacksPath.resolve("config.json");
//        if (Files.exists(configPath)) {
//            try (InputStream stream = Files.newInputStream(configPath)) {
//                return GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), RepositoryConfig.class);
//            } catch (IOException | JsonSyntaxException | JsonIOException e) {
//                GunMod.LOGGER.warn(MARKER, "Failed to read config json: {}", configPath);
//            }
//        }
//        // 不存在或者出问题了，新建一个
//        RepositoryConfig config = new RepositoryConfig(true);
//        // 使用Gson写文件
//        try (BufferedWriter writer = Files.newBufferedWriter(configPath, StandardCharsets.UTF_8)) {
//            GSON.toJson(config, writer);
//        } catch (IOException e) {
//            GunMod.LOGGER.warn(MARKER, "Failed to init config json: {}", configPath);
//        }
//        return config;
//    }

    private static GunPack fromDirPath(Path path) throws IOException {
        Path packInfoFilePath = path.resolve("gunpack.meta.json");
        try (InputStream stream = Files.newInputStream(packInfoFilePath)) {
            PackMeta info = CommonAssetsManager.GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), PackMeta.class);

            if (info == null) {
                GunMod.LOGGER.warn(MARKER, "Failed to read info json: {}", packInfoFilePath.getFileName());
                return null;
            }

            if (info.getDependencies() !=null && !modVersionAllMatch(info)) {
                GunMod.LOGGER.warn(MARKER, "Mod version mismatch: {}", packInfoFilePath.getFileName());
                return null;
            }

            return new GunPack(path, info.getName());
        } catch (IOException | JsonSyntaxException | JsonIOException | InvalidVersionSpecificationException exception) {
            GunMod.LOGGER.warn(MARKER, "Failed to read info json: {}", packInfoFilePath.getFileName());
            GunMod.LOGGER.warn(exception.getMessage());
        }
        return null;
    }

    private static GunPack fromZipPath(Path path)  {
        try(ZipFile zipFile = new ZipFile(path.toFile())){
            ZipEntry extDescriptorEntry = zipFile.getEntry("gunpack.meta.json");
            if (extDescriptorEntry == null) {
                GunMod.LOGGER.error(MARKER,"Failed to load extension from ZIP {}. Error: {}", path.getFileName(), "No gunpack.meta.json found");
                return null;
            }

            try (InputStream stream = zipFile.getInputStream(extDescriptorEntry)) {
                PackMeta info = CommonAssetsManager.GSON.fromJson(new InputStreamReader(stream, StandardCharsets.UTF_8), PackMeta.class);

                if (info == null) {
                    GunMod.LOGGER.warn(MARKER, "Failed to read info json: {}", path.getFileName());
                    return null;
                }

                if (info.getDependencies() !=null && !modVersionAllMatch(info)) {
                    GunMod.LOGGER.warn(MARKER, "Mod version mismatch: {}", path.getFileName());
                    return null;
                }

                return new GunPack(path, info.getName());
            } catch (IOException | JsonSyntaxException | JsonIOException | InvalidVersionSpecificationException e) {
                GunMod.LOGGER.error(MARKER,"Failed to load extension from ZIP {}. Error: {}", path.getFileName(), e);
                return null;
            }
        } catch (IOException e) {
            GunMod.LOGGER.error(MARKER,"Failed to load extension from ZIP {}. Error: {}", path.getFileName(), e);
            return null;
        }
    }

    private static List<GunPack> scanExtensions(Path extensionsPath) {
        List<GunPack> gunPacks = new ArrayList<>();

        try (DirectoryStream<Path> stream = Files.newDirectoryStream(extensionsPath)){
            for (Path entry : stream) {
                GunPack gunPack = null;
                if (Files.isDirectory(entry)) {
                    gunPack = fromDirPath(entry);
                } else if (entry.toString().endsWith(".zip")) {
                    gunPack = fromZipPath(entry);
                }
                if (gunPack != null) {
                    GunMod.LOGGER.info(MARKER, "- {}, Main namespace: {}", gunPack.path.getFileName(), gunPack.name);
                    gunPacks.add(gunPack);
                }
            }
        } catch (IOException e) {
            GunMod.LOGGER.error(MARKER, "Failed to scan extensions from {}. Error: {}", extensionsPath, e);
        }

        return gunPacks;
    }

    private static boolean modVersionAllMatch(PackMeta info) throws InvalidVersionSpecificationException {
        HashMap<String, String> dependencies = info.getDependencies();
        for (String modId : dependencies.keySet()) {
            if (!modVersionMatch(modId, dependencies.get(modId))) {
                return false;
            }
        }
        return true;
    }

    private static boolean modVersionMatch(String modId, String version) throws InvalidVersionSpecificationException {
        VersionRange versionRange = VersionRange.createFromVersionSpec(version);
        return ModList.get().getModContainerById(modId).map(mod -> {
            ArtifactVersion modVersion = mod.getModInfo().getVersion();
            return versionRange.containsVersion(modVersion);
        }).orElse(false);
    }


    public record GunPack(Path path, String name) {
    }

    private record TaczResourcesSupplier(List<GunPack> gunPacks, PackMetadataSection metadata) implements Pack.ResourcesSupplier {
        @Override
        public PackResources openPrimary(PackLocationInfo locationInfo) {
            return new TaczPackResources(locationInfo, gunPacks, metadata);
        }

        @Override
        public PackResources openFull(PackLocationInfo locationInfo, Pack.Metadata metadata) {
            return openPrimary(locationInfo);
        }
    }

    private static final class TaczPackResources extends AbstractPackResources {
        private final List<PackResources> packs;
        private final PackMetadataSection metadata;
        private final @Nullable IoSupplier<InputStream> icon;

        private TaczPackResources(PackLocationInfo locationInfo, List<GunPack> gunPacks, PackMetadataSection metadata) {
            super(locationInfo);
            this.metadata = metadata;
            this.icon = getModIcon(GunMod.MOD_ID);
            this.packs = gunPacks.stream()
                    .map(gunPack -> openPackResources(locationInfo, gunPack))
                    .toList();
        }

        private static PackResources openPackResources(PackLocationInfo locationInfo, GunPack gunPack) {
            Path path = gunPack.path();
            if (Files.isDirectory(path)) {
                return new PathPackResources(locationInfo, path);
            }
            return new FilePackResources(locationInfo, new FilePackResources.SharedZipFileAccess(path.toFile()), "");
        }

        @Override
        public @Nullable IoSupplier<InputStream> getRootResource(String... paths) {
            if (paths.length == 1 && paths[0].equals("pack.png") && icon != null) {
                return icon;
            }
            for (PackResources pack : packs) {
                IoSupplier<InputStream> resource = pack.getRootResource(paths);
                if (resource != null) {
                    return resource;
                }
            }
            return null;
        }

        @Override
        public @Nullable IoSupplier<InputStream> getResource(PackType type, Identifier location) {
            for (PackResources pack : packs) {
                IoSupplier<InputStream> resource = pack.getResource(type, location);
                if (resource != null) {
                    return resource;
                }
            }
            return null;
        }

        @Override
        public void listResources(PackType type, String namespace, String path, PackResources.ResourceOutput output) {
            HashMap<Identifier, IoSupplier<InputStream>> resources = new HashMap<>();
            for (PackResources pack : packs) {
                pack.listResources(type, namespace, path, resources::putIfAbsent);
            }
            resources.forEach(output);
        }

        @Override
        public Set<String> getNamespaces(PackType type) {
            Set<String> namespaces = new HashSet<>();
            for (PackResources pack : packs) {
                namespaces.addAll(pack.getNamespaces(type));
            }
            return namespaces;
        }

        @SuppressWarnings("unchecked")
        @Override
        public <T> @Nullable T getMetadataSection(MetadataSectionType<T> metadataType) {
            if (PackMetadataSection.CLIENT_TYPE.equals(metadataType)
                    || PackMetadataSection.SERVER_TYPE.equals(metadataType)
                    || PackMetadataSection.FALLBACK_TYPE.equals(metadataType)) {
                return (T) metadata;
            }
            return null;
        }

        @Override
        public void close() {
            packs.forEach(PackResources::close);
        }
    }
}
