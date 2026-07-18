package com.phasetranscrystal.fpsmatch.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.Reader;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Properties;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class Ldlib2DependencyContractTest {
    private static final String RELEASE_TAG =
            "v2.2.27-forge.1.20.1-r8";
    private static final String RELEASE_COMMIT =
            "4bced05a11b4e10bf03cf70daacf830057498158";
    private static final String API_JAR =
            "ldlib2-forge-1.20.1-2.2.27+forge.1.20.1.jar";
    private static final String API_SHA256 =
            "5cb50fc24ac486850472018a5a8cfe9a2211dc9407a8f25f149dafc381becf2e";
    private static final String RUNTIME_JAR =
            "ldlib2-forge-1.20.1-2.2.27+forge.1.20.1-all.jar";
    private static final String RUNTIME_SHA256 =
            "dc5465c5b01d54a477cacdfec2daebd80ce91cb9bb91aa64baf10b485b4a2e9c";

    @Test
    void immutableReleaseAssetsArePinnedByNameUrlAndDigest() throws IOException {
        Properties properties = properties();

        assertEquals(RELEASE_TAG, properties.getProperty("ldlib2_release_tag"));
        assertEquals(RELEASE_COMMIT,
                properties.getProperty("ldlib2_release_commit"));
        assertEquals(API_JAR, properties.getProperty("ldlib2_api_jar"));
        assertEquals(API_SHA256,
                properties.getProperty("ldlib2_api_sha256"));
        assertEquals(RUNTIME_JAR,
                properties.getProperty("ldlib2_runtime_jar"));
        assertEquals(RUNTIME_SHA256,
                properties.getProperty("ldlib2_runtime_sha256"));
        assertEquals(assetUrl(API_JAR),
                properties.getProperty("ldlib2_api_url"));
        assertEquals(assetUrl(RUNTIME_JAR),
                properties.getProperty("ldlib2_runtime_url"));
    }

    @Test
    void buildUsesApiForCompilationAndAllJarExactlyOnceAtRuntime()
            throws IOException {
        String build = Files.readString(Path.of("build.gradle"));

        assertTrue(build.contains("prepareLdlib2Artifacts"));
        assertTrue(build.contains("verifyPinnedArtifact"));
        assertTrue(build.contains("verifyLdlib2Classpath"));
        assertTrue(build.contains("ldlib2Probe"));
        assertTrue(build.contains("src/ldlib2Probe/java"));
        assertTrue(build.contains("'prepareClientRun'"));
        assertTrue(build.contains("'prepareServerRun'"));
        assertTrue(build.contains("'prepareGameTestServerRun'"));
        assertTrue(build.contains("dependsOn tasks.named('verifyLdlib2Classpath')"));
        assertTrue(build.contains("tasks.named('check').configure"));
        assertTrue(build.contains("name = 'PinnedLdlib2Release'"));
        assertTrue(build.contains("modCompileOnly(ldlib2Coordinates)"));
        assertTrue(build.contains("modRuntimeOnly(ldlib2Coordinates)"));
        assertTrue(build.contains("classifier = 'all'"));
        assertTrue(build.contains("verifyLdlib2ReleaseArtifacts"));
        assertFalse(build.contains("testCompileOnly files(ldlib2ApiArtifact)"));
        assertFalse(build.contains("testRuntimeOnly files(ldlib2RuntimeArtifact)"));
        assertFalse(build.contains("sourceSets.main.compileClasspath += ldlib2ApiFiles"));
        assertFalse(build.contains("sourceSets.main.runtimeClasspath += ldlib2RuntimeFiles"));
        assertEquals(1, occurrences(
                build, "modCompileOnly(ldlib2Coordinates)"
        ));
        assertEquals(1, occurrences(
                build, "modRuntimeOnly(ldlib2Coordinates)"
        ));
    }

    @Test
    void runtimeMetadataRequiresPinnedLdlib2AndKotlinForForge()
            throws IOException {
        String metadata = Files.readString(
                Path.of("src/main/templates/META-INF/mods.toml")
        );

        assertDependency(metadata, "ldlib2", "[2.2.27+forge.1.20.1]");
        assertDependency(metadata, "kotlinforforge", "[4.11.0,)");
    }

    @Test
    void pureAndServerMinimapPackagesDoNotImportUiLibraries()
            throws IOException {
        for (Path root : List.of(
                Path.of("src/main/java/com/phasetranscrystal/fpsmatch/core/minimap"),
                Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/minimap/server"),
                Path.of("src/main/java/com/phasetranscrystal/fpsmatch/common/packet/minimap")
        )) {
            try (Stream<Path> files = Files.walk(root)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java"))
                        .toList()) {
                    String source = Files.readString(file);
                    assertFalse(source.contains("com.lowdragmc.ldlib2"), file.toString());
                    assertFalse(source.contains("net.minecraft.client"), file.toString());
                    assertFalse(source.contains("net.minecraftforge.client"), file.toString());
                }
            }
        }
    }

    private static Properties properties() throws IOException {
        Properties properties = new Properties();
        try (Reader reader = Files.newBufferedReader(Path.of("gradle.properties"))) {
            properties.load(reader);
        }
        return properties;
    }

    private static String assetUrl(String name) {
        return "https://github.com/weiliangyan/LDLib2-1.20.1-Forge/releases/download/"
                + RELEASE_TAG + "/" + name.replace("+", "%2B");
    }

    private static void assertDependency(
            String metadata,
            String modId,
            String versionRange
    ) {
        String blockStart = "[[dependencies.\"${mod_id}\"]]";
        int cursor = 0;
        while ((cursor = metadata.indexOf(blockStart, cursor)) >= 0) {
            int next = metadata.indexOf(blockStart, cursor + blockStart.length());
            String block = metadata.substring(
                    cursor,
                    next < 0 ? metadata.length() : next
            );
            if (block.contains("modId=\"" + modId + "\"")
                    && block.contains("mandatory=true")
                    && block.contains("versionRange=\"" + versionRange + "\"")
                    && block.contains("side=\"BOTH\"")) {
                return;
            }
            cursor += blockStart.length();
        }
        throw new AssertionError("Missing dependency metadata for " + modId);
    }

    private static int occurrences(String text, String needle) {
        int count = 0;
        int cursor = 0;
        while ((cursor = text.indexOf(needle, cursor)) >= 0) {
            count++;
            cursor += needle.length();
        }
        return count;
    }

    @Test
    void headlessClientAcceptanceTaskCoversClientAndEditorWithoutGraphicsHardware()
            throws IOException {
        String build = Files.readString(Path.of("build.gradle"));

        assertTrue(build.contains("tasks.register('headlessClientAcceptance', Test)"));
        assertTrue(build.contains("systemProperty 'java.awt.headless', 'true'"));
        assertTrue(build.contains("com.phasetranscrystal.fpsmatch.common.client.minimap.*"));
        assertTrue(build.contains("com.phasetranscrystal.fpsmatch.core.minimap.editor.*"));
        assertTrue(build.contains("EditorWorldBakeScenarioTest"));
        assertTrue(build.contains("PngImportWorkflowTest"));
    }

    @Test
    void gameTestServerUsesAnIsolatedGeneratedRunDirectory()
            throws IOException {
        String build = Files.readString(Path.of("build.gradle"));
        int runStart = build.indexOf("gameTestServer {");
        int runEnd = build.indexOf("\n        }", runStart);

        assertTrue(runStart >= 0);
        assertTrue(runEnd > runStart);
        String gameTestServer = build.substring(runStart, runEnd);
        assertTrue(gameTestServer.contains(
                "gameDirectory = project.file('build/run-gametest')"
        ));
    }

    @Test
    void headlessPerformanceAcceptanceUsesDedicatedFixturesAndReport()
            throws IOException {
        String build = Files.readString(Path.of("build.gradle"));

        assertTrue(build.contains("performanceAcceptance"));
        assertTrue(build.contains("tasks.register('headlessPerformanceAcceptance', JavaExec)"));
        assertTrue(build.contains("MinimapHeadlessPerformanceAcceptance"));
        assertTrue(build.contains("minimapPerformanceProfile"));
        assertTrue(build.contains("minimap-performance/headless.json"));
        assertTrue(build.contains("-XX:+UseG1GC"));
        assertTrue(build.contains("-Xms4G"));
        assertTrue(build.contains("-Xmx4G"));
        assertTrue(build.contains("-Xms512m"));
        assertTrue(build.contains("-Xmx1G"));
        assertTrue(build.contains("minimapPerformanceProfile == 'calibration'"));
    }
}
