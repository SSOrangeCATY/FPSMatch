package com.tacz.guns.client.renderer.item;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Transformation;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.MapCodec;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.item.ItemModel;
import net.minecraft.client.renderer.item.ItemModelResolver;
import net.minecraft.client.renderer.item.ItemStackRenderState;
import net.minecraft.client.renderer.item.ModelRenderProperties;
import net.minecraft.client.renderer.special.SpecialModelRenderer;
import net.minecraft.client.resources.model.ModelBaker;
import net.minecraft.client.resources.model.ResolvableModel;
import net.minecraft.client.resources.model.ResolvedModel;
import net.minecraft.client.resources.model.sprite.TextureSlots;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.ItemOwner;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import org.joml.Matrix4fc;
import org.joml.Vector3f;
import org.joml.Vector3fc;
import org.jspecify.annotations.Nullable;

import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Supplier;

public class TaczCustomItemModel implements ItemModel {
    private static final Vector3fc[] ITEM_EXTENTS = {
            new Vector3f(0.0F, 0.0F, 0.0F),
            new Vector3f(1.0F, 1.0F, 1.0F)
    };
    private static final Supplier<Vector3fc[]> ITEM_EXTENTS_SUPPLIER = () -> ITEM_EXTENTS;
    private static final SpecialModelRenderer<RenderContext> SPECIAL_RENDERER = new SpecialModelRenderer<>() {
        @Override
        public void submit(@Nullable RenderContext argument, PoseStack poseStack, SubmitNodeCollector submitNodeCollector,
                           int lightCoords, int overlayCoords, boolean hasFoil, int outlineColor) {
            if (argument == null) {
                return;
            }
            switch (argument.renderer()) {
                case GUN -> TaczItemRenderers.gun().submitByItem(argument.stack(), argument.displayContext(), poseStack, submitNodeCollector, lightCoords, overlayCoords);
                case AMMO -> TaczItemRenderers.ammo().submitByItem(argument.stack(), argument.displayContext(), poseStack, submitNodeCollector, lightCoords, overlayCoords);
                case ATTACHMENT -> TaczItemRenderers.attachment().submitByItem(argument.stack(), argument.displayContext(), poseStack, submitNodeCollector, lightCoords, overlayCoords);
                case GUN_SMITH_TABLE -> TaczItemRenderers.gunSmithTable().submitByItem(argument.stack(), argument.displayContext(), poseStack, submitNodeCollector, lightCoords, overlayCoords);
            }
        }

        @Override
        public void getExtents(Consumer<Vector3fc> output) {
            for (Vector3fc extent : ITEM_EXTENTS) {
                output.accept(extent);
            }
        }

        @Override
        public @Nullable RenderContext extractArgument(ItemStack stack) {
            return null;
        }
    };

    private final RendererKind renderer;
    private final ModelRenderProperties properties;
    private final Matrix4fc transformation;

    private TaczCustomItemModel(RendererKind renderer, ModelRenderProperties properties, Matrix4fc transformation) {
        this.renderer = renderer;
        this.properties = properties;
        this.transformation = transformation;
    }

    @Override
    public void update(ItemStackRenderState output, ItemStack item, ItemModelResolver resolver,
                       ItemDisplayContext displayContext, @Nullable ClientLevel level, @Nullable ItemOwner owner, int seed) {
        output.appendModelIdentityElement(this);
        output.appendModelIdentityElement(this.renderer);
        output.appendModelIdentityElement(displayContext);
        output.appendModelIdentityElement(item.getItem());
        output.appendModelIdentityElement(ItemStack.hashItemAndComponents(item));
        output.setOversizedInGui(false);
        ItemStackRenderState.LayerRenderState layer = output.newLayer();
        if (item.hasFoil()) {
            ItemStackRenderState.FoilType foilType = ItemStackRenderState.FoilType.STANDARD;
            layer.setFoilType(foilType);
            output.setAnimated();
            output.appendModelIdentityElement(foilType);
        }

        RenderContext argument = new RenderContext(item.copy(), displayContext, this.renderer);
        layer.setExtents(ITEM_EXTENTS_SUPPLIER);
        layer.setLocalTransform(this.transformation);
        layer.setupSpecialModel(SPECIAL_RENDERER, argument);
        this.properties.applyToLayer(layer, displayContext);
    }

    private record RenderContext(ItemStack stack, ItemDisplayContext displayContext, RendererKind renderer) {
    }

    public enum RendererKind {
        GUN("gun"),
        AMMO("ammo"),
        ATTACHMENT("attachment"),
        GUN_SMITH_TABLE("gun_smith_table");

        private static final Codec<RendererKind> CODEC = Codec.STRING.comapFlatMap(RendererKind::read, RendererKind::serializedName);
        private final String serializedName;

        RendererKind(String serializedName) {
            this.serializedName = serializedName;
        }

        private String serializedName() {
            return this.serializedName;
        }

        private static DataResult<RendererKind> read(String name) {
            for (RendererKind renderer : values()) {
                if (renderer.serializedName.equals(name)) {
                    return DataResult.success(renderer);
                }
            }
            return DataResult.error(() -> "Unknown TACZ item renderer: " + name);
        }
    }

    public record Unbaked(Identifier base, Optional<Transformation> transformation, RendererKind renderer) implements ItemModel.Unbaked {
        public static final MapCodec<Unbaked> MAP_CODEC = RecordCodecBuilder.mapCodec(instance -> instance.group(
                Identifier.CODEC.fieldOf("base").forGetter(Unbaked::base),
                Transformation.EXTENDED_CODEC.optionalFieldOf("transformation").forGetter(Unbaked::transformation),
                RendererKind.CODEC.fieldOf("renderer").forGetter(Unbaked::renderer)
        ).apply(instance, Unbaked::new));

        @Override
        public void resolveDependencies(ResolvableModel.Resolver resolver) {
            resolver.markDependency(this.base);
        }

        @Override
        public ItemModel bake(ItemModel.BakingContext context, Matrix4fc transformation) {
            Matrix4fc modelTransform = Transformation.compose(transformation, this.transformation);
            ModelRenderProperties properties = this.getProperties(context);
            return new TaczCustomItemModel(this.renderer, properties, modelTransform);
        }

        private ModelRenderProperties getProperties(ItemModel.BakingContext context) {
            ModelBaker baker = context.blockModelBaker();
            ResolvedModel model = baker.getModel(this.base);
            TextureSlots textureSlots = model.getTopTextureSlots();
            return ModelRenderProperties.fromResolvedModel(baker, model, textureSlots);
        }

        @Override
        public MapCodec<Unbaked> type() {
            return MAP_CODEC;
        }
    }
}
