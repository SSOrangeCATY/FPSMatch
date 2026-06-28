package com.tacz.guns.client.renderer.block;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.tacz.guns.api.DefaultAssets;
import com.tacz.guns.api.TimelessAPI;
import com.tacz.guns.api.item.IBlock;
import com.tacz.guns.block.AbstractGunSmithTableBlock;
import com.tacz.guns.block.entity.GunSmithTableBlockEntity;
import com.tacz.guns.client.model.bedrock.BedrockModel;
import com.tacz.guns.client.renderer.BedrockSubmitUtils;
import com.tacz.guns.client.resource.index.ClientBlockIndex;
import com.tacz.guns.config.client.RenderConfig;
import com.tacz.guns.init.ModItems;
import com.tacz.guns.item.DefaultTableItem;
import java.util.Optional;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.blockentity.BlockEntityRendererProvider;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.feature.ModelFeatureRenderer;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.Direction;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public class GunSmithTableRenderer implements BlockEntityRenderer<GunSmithTableBlockEntity, GunSmithTableRenderer.GunSmithTableRenderState> {
    public GunSmithTableRenderer(BlockEntityRendererProvider.Context context) {
    }

    public Optional<ClientBlockIndex> getIndex(GunSmithTableBlockEntity blockEntity) {
        Identifier id = blockEntity.getId();
        if (id == null || id.equals(DefaultAssets.EMPTY_BLOCK_ID)) {
            return Optional.empty();
        }
        return TimelessAPI.getClientBlockIndex(id);
    }

    public static Optional<ClientBlockIndex> getIndex(ItemStack stack) {
        if (stack.getItem() instanceof IBlock iBlock) {
            Identifier id = iBlock.getBlockId(stack);
            Optional<ClientBlockIndex> index = getClientBlockIndex(id);
            if (index.isPresent()) {
                return index;
            }
            Identifier fallbackId = getRegisteredWorkbenchFallbackId(stack);
            if ((id == null || id.equals(DefaultAssets.EMPTY_BLOCK_ID)) && fallbackId != null) {
                return getClientBlockIndex(fallbackId);
            }
            return Optional.empty();
        }
        return Optional.empty();
    }

    private static Optional<ClientBlockIndex> getClientBlockIndex(@Nullable Identifier id) {
        if (id == null || id.equals(DefaultAssets.EMPTY_BLOCK_ID)) {
            return Optional.empty();
        }
        return TimelessAPI.getClientBlockIndex(id);
    }

    @Nullable
    private static Identifier getRegisteredWorkbenchFallbackId(ItemStack stack) {
        if (stack.getItem() == ModItems.GUN_SMITH_TABLE.get()) {
            return DefaultTableItem.ID;
        }
        if (stack.getItem() == ModItems.WORKBENCH_111.get()) {
            return ModItems.WORKBENCH_A_ID;
        }
        if (stack.getItem() == ModItems.WORKBENCH_211.get()) {
            return ModItems.WORKBENCH_B_ID;
        }
        if (stack.getItem() == ModItems.WORKBENCH_121.get()) {
            return ModItems.WORKBENCH_C_ID;
        }
        return null;
    }

    @Override
    public GunSmithTableRenderState createRenderState() {
        return new GunSmithTableRenderState();
    }

    @Override
    public void extractRenderState(GunSmithTableBlockEntity blockEntity, GunSmithTableRenderState state, float partialTicks,
                                   Vec3 cameraPosition, ModelFeatureRenderer.@org.jspecify.annotations.Nullable CrumblingOverlay breakProgress) {
        BlockEntityRenderer.super.extractRenderState(blockEntity, state, partialTicks, cameraPosition, breakProgress);
        state.render = false;
        state.model = null;
        state.texture = null;
        getIndex(blockEntity).ifPresent(index -> {
            BedrockModel model = index.getModel();
            Identifier texture = index.getTexture();
            BlockState blockState = blockEntity.getBlockState();
            if (model != null && blockState.getBlock() instanceof AbstractGunSmithTableBlock block && block.isRoot(blockState)) {
                Direction facing = blockState.getValue(AbstractGunSmithTableBlock.FACING);
                state.model = model;
                state.texture = texture;
                state.rotation = block.parseRotation(facing);
                state.render = true;
            }
        });
    }

    @Override
    public void submit(GunSmithTableRenderState state, PoseStack poseStack, SubmitNodeCollector submitNodeCollector, CameraRenderState camera) {
        if (!state.render || state.model == null || state.texture == null) {
            return;
        }
        poseStack.pushPose();
        poseStack.translate(0.5, 1.5, 0.5);
        poseStack.mulPose(Axis.ZN.rotationDegrees(180));
        poseStack.mulPose(Axis.YN.rotationDegrees(state.rotation));
        RenderType renderType = RenderConfig.BLOCK_ENTITY_TRANSLUCENT.get()
                ? RenderTypes.entityTranslucent(state.texture)
                : RenderTypes.entityCutout(state.texture);
        BedrockSubmitUtils.submitModel(
                submitNodeCollector,
                poseStack,
                renderType,
                state.model,
                ItemDisplayContext.NONE,
                state.lightCoords,
                OverlayTexture.NO_OVERLAY
        );
        poseStack.popPose();
    }

    @Override
    public boolean shouldRenderOffScreen() {
        return true;
    }

    public static class GunSmithTableRenderState extends BlockEntityRenderState {
        @Nullable BedrockModel model;
        @Nullable Identifier texture;
        float rotation;
        boolean render;
    }
}
