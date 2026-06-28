package com.tacz.guns.client.model;

import com.mojang.blaze3d.GpuFormat;
import com.mojang.blaze3d.PrimitiveTopology;
import com.mojang.blaze3d.pipeline.ColorTargetState;
import com.mojang.blaze3d.pipeline.DepthStencilState;
import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.platform.CompareOp;
import com.tacz.guns.GunMod;
import net.minecraft.client.renderer.BindGroupLayouts;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.renderer.rendertype.RenderSetup;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.resources.Identifier;
import net.neoforged.neoforge.client.event.RegisterRenderPipelinesEvent;
import net.neoforged.neoforge.client.stencil.StencilOperation;
import net.neoforged.neoforge.client.stencil.StencilPerFaceTest;
import net.neoforged.neoforge.client.stencil.StencilTest;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * RenderTypes that bind TACZ scope stencil state to the retained draw stage.
 */
public final class ScopeStencilRenderTypes {
    private static final int STENCIL_MASK = 0xFF;
    private static final int MAX_OCULAR_REF = Byte.MAX_VALUE + 1;
    private static final ColorTargetState NO_COLOR_WRITE =
            new ColorTargetState(Optional.empty(), GpuFormat.RGBA8_UNORM, ColorTargetState.WRITE_NONE);
    private static final DepthStencilState NO_DEPTH_WRITE =
            new DepthStencilState(CompareOp.GREATER_THAN_OR_EQUAL, false);
    private static final DepthStencilState ALWAYS_NO_DEPTH_WRITE =
            new DepthStencilState(CompareOp.ALWAYS_PASS, false);
    private static final DepthStencilState STENCIL_MUTATION_DEPTH =
            new DepthStencilState(CompareOp.ALWAYS_PASS, false);

    private static final RenderPipeline CLEAR_STENCIL_PIPELINE = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
            .withLocation(id("pipeline/scope_stencil/clear"))
            .withPrimitiveTopology(PrimitiveTopology.QUADS)
            .withColorTargetState(NO_COLOR_WRITE)
            .withDepthStencilState(ALWAYS_NO_DEPTH_WRITE)
            .withStencilTest(stencil(StencilOperation.ZERO, CompareOp.ALWAYS_PASS, 0, STENCIL_MASK))
            .withCull(false)
            .build();
    private static final RenderPipeline ENTITY_EQUAL_0_PIPELINE = entityPipeline(
            "entity_equal_0",
            stencil(StencilOperation.KEEP, CompareOp.EQUAL, 0, 0)
    );
    private static final RenderPipeline ENTITY_GREATER_127_PIPELINE = entityPipeline(
            "entity_greater_127",
            stencil(StencilOperation.KEEP, CompareOp.GREATER_THAN, 127, 0)
    );
    private static final RenderPipeline[] OCULAR_WRITE_PIPELINES = new RenderPipeline[MAX_OCULAR_REF + 1];
    private static final RenderPipeline[] APERTURE_INVERT_PIPELINES = new RenderPipeline[MAX_OCULAR_REF + 1];
    private static final RenderPipeline[] ENTITY_EQUAL_REF_PIPELINES = new RenderPipeline[MAX_OCULAR_REF + 1];
    private static final RenderPipeline[] ENTITY_EQUAL_REF_NO_DEPTH_PIPELINES = new RenderPipeline[MAX_OCULAR_REF + 1];
    private static final RenderPipeline[] ENTITY_EQUAL_INVERTED_REF_PIPELINES = new RenderPipeline[MAX_OCULAR_REF + 1];
    private static final RenderPipeline[] ENTITY_EQUAL_INVERTED_REF_NO_DEPTH_PIPELINES = new RenderPipeline[MAX_OCULAR_REF + 1];
    private static final RenderType CLEAR_STENCIL = RenderType.create(
            "tacz_scope_stencil_clear",
            RenderSetup.builder(CLEAR_STENCIL_PIPELINE).createRenderSetup()
    );
    private static final Map<Identifier, RenderType> ENTITY_EQUAL_0_TYPES = new ConcurrentHashMap<>();
    private static final Map<Identifier, RenderType> ENTITY_GREATER_127_TYPES = new ConcurrentHashMap<>();
    private static final Map<TexturedRefKey, RenderType> OCULAR_WRITE_TYPES = new ConcurrentHashMap<>();
    private static final Map<TexturedRefKey, RenderType> ENTITY_EQUAL_REF_TYPES = new ConcurrentHashMap<>();
    private static final Map<TexturedRefKey, RenderType> ENTITY_EQUAL_REF_NO_DEPTH_TYPES = new ConcurrentHashMap<>();
    private static final Map<TexturedRefKey, RenderType> ENTITY_EQUAL_INVERTED_REF_TYPES = new ConcurrentHashMap<>();
    private static final Map<TexturedRefKey, RenderType> ENTITY_EQUAL_INVERTED_REF_NO_DEPTH_TYPES = new ConcurrentHashMap<>();
    private static final RenderType[] APERTURE_INVERT_TYPES = new RenderType[MAX_OCULAR_REF + 1];

    static {
        for (int ref = 1; ref <= MAX_OCULAR_REF; ref++) {
            OCULAR_WRITE_PIPELINES[ref] = entityPipeline(
                    "ocular_write_" + ref,
                    stencil(StencilOperation.REPLACE, CompareOp.GREATER_THAN, ref, STENCIL_MASK),
                    NO_COLOR_WRITE,
                    STENCIL_MUTATION_DEPTH
            );
            ENTITY_EQUAL_REF_PIPELINES[ref] = entityPipeline(
                    "entity_equal_ref_" + ref,
                    stencil(StencilOperation.KEEP, CompareOp.EQUAL, ref, 0)
            );
            ENTITY_EQUAL_REF_NO_DEPTH_PIPELINES[ref] = entityPipeline(
                    "entity_equal_ref_no_depth_" + ref,
                    stencil(StencilOperation.KEEP, CompareOp.EQUAL, ref, 0),
                    null,
                    ALWAYS_NO_DEPTH_WRITE
            );
            int invertedRef = ~ref & STENCIL_MASK;
            ENTITY_EQUAL_INVERTED_REF_PIPELINES[ref] = entityPipeline(
                    "entity_equal_inverted_ref_" + ref,
                    stencil(StencilOperation.KEEP, CompareOp.EQUAL, invertedRef, 0)
            );
            ENTITY_EQUAL_INVERTED_REF_NO_DEPTH_PIPELINES[ref] = entityPipeline(
                    "entity_equal_inverted_ref_no_depth_" + ref,
                    stencil(StencilOperation.KEEP, CompareOp.EQUAL, invertedRef, 0),
                    null,
                    ALWAYS_NO_DEPTH_WRITE
            );
            APERTURE_INVERT_PIPELINES[ref] = RenderPipeline.builder(RenderPipelines.DEBUG_FILLED_SNIPPET)
                    .withLocation(id("pipeline/scope_stencil/aperture_invert_" + ref))
                    .withPrimitiveTopology(PrimitiveTopology.TRIANGLE_FAN)
                    .withColorTargetState(NO_COLOR_WRITE)
                    .withDepthStencilState(STENCIL_MUTATION_DEPTH)
                    .withStencilTest(stencil(StencilOperation.INVERT, CompareOp.EQUAL, ref, STENCIL_MASK))
                    .withCull(false)
                    .build();
            APERTURE_INVERT_TYPES[ref] = RenderType.create(
                    "tacz_scope_aperture_invert_" + ref,
                    RenderSetup.builder(APERTURE_INVERT_PIPELINES[ref]).createRenderSetup()
            );
        }
    }

    private ScopeStencilRenderTypes() {
    }

    public static void registerPipelines(RegisterRenderPipelinesEvent event) {
        event.registerPipeline(CLEAR_STENCIL_PIPELINE);
        event.registerPipeline(ENTITY_EQUAL_0_PIPELINE);
        event.registerPipeline(ENTITY_GREATER_127_PIPELINE);
        for (int ref = 1; ref <= MAX_OCULAR_REF; ref++) {
            event.registerPipeline(OCULAR_WRITE_PIPELINES[ref]);
            event.registerPipeline(ENTITY_EQUAL_REF_PIPELINES[ref]);
            event.registerPipeline(ENTITY_EQUAL_REF_NO_DEPTH_PIPELINES[ref]);
            event.registerPipeline(ENTITY_EQUAL_INVERTED_REF_PIPELINES[ref]);
            event.registerPipeline(ENTITY_EQUAL_INVERTED_REF_NO_DEPTH_PIPELINES[ref]);
            event.registerPipeline(APERTURE_INVERT_PIPELINES[ref]);
        }
    }

    static RenderType clearStencil() {
        return CLEAR_STENCIL;
    }

    static RenderType ocularWrite(Identifier texture, int ref) {
        int safeRef = validateOcularRef(ref);
        return OCULAR_WRITE_TYPES.computeIfAbsent(new TexturedRefKey(texture, safeRef), key ->
                texturedEntity("tacz_scope_ocular_write", OCULAR_WRITE_PIPELINES[safeRef], key.texture(), false));
    }

    static RenderType entityEqual0(Identifier texture) {
        return ENTITY_EQUAL_0_TYPES.computeIfAbsent(texture, key ->
                texturedEntity("tacz_scope_entity_equal_0", ENTITY_EQUAL_0_PIPELINE, key, true));
    }

    static RenderType entityEqualRef(Identifier texture, int ref) {
        int safeRef = validateOcularRef(ref);
        return ENTITY_EQUAL_REF_TYPES.computeIfAbsent(new TexturedRefKey(texture, safeRef), key ->
                texturedEntity("tacz_scope_entity_equal_ref", ENTITY_EQUAL_REF_PIPELINES[safeRef], key.texture(), true));
    }

    static RenderType entityEqualRefNoDepth(Identifier texture, int ref) {
        int safeRef = validateOcularRef(ref);
        return ENTITY_EQUAL_REF_NO_DEPTH_TYPES.computeIfAbsent(new TexturedRefKey(texture, safeRef), key ->
                texturedEntity("tacz_scope_entity_equal_ref_no_depth",
                        ENTITY_EQUAL_REF_NO_DEPTH_PIPELINES[safeRef], key.texture(), true));
    }

    static RenderType entityEqualInvertedRef(Identifier texture, int ref) {
        int safeRef = validateOcularRef(ref);
        return ENTITY_EQUAL_INVERTED_REF_TYPES.computeIfAbsent(new TexturedRefKey(texture, safeRef), key ->
                texturedEntity("tacz_scope_entity_equal_inverted_ref",
                        ENTITY_EQUAL_INVERTED_REF_PIPELINES[safeRef], key.texture(), true));
    }

    static RenderType entityEqualInvertedRefNoDepth(Identifier texture, int ref) {
        int safeRef = validateOcularRef(ref);
        return ENTITY_EQUAL_INVERTED_REF_NO_DEPTH_TYPES.computeIfAbsent(new TexturedRefKey(texture, safeRef), key ->
                texturedEntity("tacz_scope_entity_equal_inverted_ref_no_depth",
                        ENTITY_EQUAL_INVERTED_REF_NO_DEPTH_PIPELINES[safeRef], key.texture(), true));
    }

    static RenderType entityGreater127(Identifier texture) {
        return ENTITY_GREATER_127_TYPES.computeIfAbsent(texture, key ->
                texturedEntity("tacz_scope_entity_greater_127", ENTITY_GREATER_127_PIPELINE, key, true));
    }

    static RenderType apertureInvert(int ref) {
        return APERTURE_INVERT_TYPES[validateOcularRef(ref)];
    }

    private static RenderType texturedEntity(String name, RenderPipeline pipeline, Identifier texture, boolean affectsOutline) {
        RenderSetup setup = RenderSetup.builder(pipeline)
                .withTexture("Sampler0", texture)
                .useLightmap()
                .useOverlay()
                .affectsCrumbling()
                .setOutline(affectsOutline ? RenderSetup.OutlineProperty.AFFECTS_OUTLINE : RenderSetup.OutlineProperty.NONE)
                .createRenderSetup();
        return RenderType.create(name, setup);
    }

    private static RenderPipeline entityPipeline(String name, StencilTest stencilTest) {
        return entityPipeline(name, stencilTest, null, null);
    }

    private static RenderPipeline entityPipeline(String name, StencilTest stencilTest,
                                                 ColorTargetState colorTargetState,
                                                 DepthStencilState depthStencilState) {
        RenderPipeline.Builder builder = RenderPipeline.builder(RenderPipelines.ENTITY_SNIPPET)
                .withLocation(id("pipeline/scope_stencil/" + name))
                .withShaderDefine("ALPHA_CUTOUT", 0.1F)
                .withShaderDefine("PER_FACE_LIGHTING")
                .withBindGroupLayout(BindGroupLayouts.SAMPLER1)
                .withCull(false)
                .withStencilTest(stencilTest);
        if (colorTargetState != null) {
            builder.withColorTargetState(colorTargetState);
        }
        if (depthStencilState != null) {
            builder.withDepthStencilState(depthStencilState);
        }
        return builder.build();
    }

    private static StencilTest stencil(StencilOperation pass, CompareOp compare, int ref, int writeMask) {
        StencilPerFaceTest face = new StencilPerFaceTest(StencilOperation.KEEP, StencilOperation.KEEP, pass, compare);
        return new StencilTest(face, STENCIL_MASK, writeMask, ref);
    }

    private static int validateOcularRef(int ref) {
        if (ref <= 0 || ref > MAX_OCULAR_REF) {
            throw new IllegalArgumentException("Index of oculus is out of range for 127");
        }
        return ref;
    }

    private static Identifier id(String path) {
        return Identifier.fromNamespaceAndPath(GunMod.MOD_ID, path);
    }

    private record TexturedRefKey(Identifier texture, int ref) {
    }
}
