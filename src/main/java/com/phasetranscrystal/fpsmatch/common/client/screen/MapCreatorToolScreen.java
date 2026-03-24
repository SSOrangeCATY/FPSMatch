package com.phasetranscrystal.fpsmatch.common.client.screen;

import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.item.MapCreatorTool;
import com.phasetranscrystal.fpsmatch.common.item.tool.ToolInteractionAction;
import com.phasetranscrystal.fpsmatch.common.packet.MapCreatorToolActionC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.OpenMapCreatorToolScreenS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.ToolInteractionC2SPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class MapCreatorToolScreen extends Screen {
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 188;
    private static final int SCREEN_OVERLAY = 0x5A000000;
    private static final int PANEL_BACKGROUND = 0xD0191D22;
    private static final int PANEL_BORDER = 0xFF7DA3B8;

    private List<String> availableTypes;
    private String selectedType;

    private EditBox mapNameField;
    private EditBox pos1XField;
    private EditBox pos1YField;
    private EditBox pos1ZField;
    private EditBox pos2XField;
    private EditBox pos2YField;
    private EditBox pos2ZField;
    private Button typeButton;

    public MapCreatorToolScreen(OpenMapCreatorToolScreenS2CPacket data) {
        super(Component.translatable("gui.fpsm.map_creator.title"));
        this.availableTypes = new ArrayList<>(data.availableTypes());
        this.selectedType = normalizeSelectedType(data.selectedType());
    }

    @Override
    protected void init() {
        int left = 18;
        int top = Math.max(18, (this.height - PANEL_HEIGHT) / 2);

        this.typeButton = this.addRenderableWidget(new Button.Builder(Component.empty(), button -> cycleType())
                .pos(left + 110, top + 20)
                .size(172, 20)
                .build());

        this.mapNameField = addTextField(left + 110, top + 52, 172, 18, 64, s -> true);
        this.pos1XField = addIntField(left + 110, top + 90);
        this.pos1YField = addIntField(left + 166, top + 90);
        this.pos1ZField = addIntField(left + 222, top + 90);
        this.pos2XField = addIntField(left + 110, top + 120);
        this.pos2YField = addIntField(left + 166, top + 120);
        this.pos2ZField = addIntField(left + 222, top + 120);

        this.addRenderableWidget(new Button.Builder(Component.translatable("gui.fpsm.map_creator.create"), button -> createMap())
                .pos(left + 18, top + 154)
                .size(122, 20)
                .build());
        this.addRenderableWidget(new Button.Builder(Component.translatable("gui.fpsm.close"), button -> onClose())
                .pos(left + 160, top + 154)
                .size(122, 20)
                .build());

        updateTypeButton();
        loadFromHeldTool();
    }

    public void applyData(OpenMapCreatorToolScreenS2CPacket data) {
        this.availableTypes = new ArrayList<>(data.availableTypes());
        this.selectedType = normalizeSelectedType(data.selectedType());
        if (this.typeButton != null) {
            updateTypeButton();
        }
        if (this.mapNameField != null) {
            this.mapNameField.setValue(data.draftMapName());
            setBlockPosFields(data.pos1(), true);
            setBlockPosFields(data.pos2(), false);
        }
    }

    @Override
    public void tick() {
        super.tick();
        if (this.mapNameField != null) {
            this.mapNameField.tick();
        }
        for (EditBox field : getPosFields()) {
            field.tick();
        }
    }

    @Override
    public void render(@NotNull GuiGraphics guiGraphics, int mouseX, int mouseY, float partialTick) {
        int left = 18;
        int top = Math.max(18, (this.height - PANEL_HEIGHT) / 2);
        guiGraphics.fill(0, 0, this.width, this.height, SCREEN_OVERLAY);
        guiGraphics.fill(left, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, PANEL_BACKGROUND);
        guiGraphics.fill(left, top, left + PANEL_WIDTH, top + 1, PANEL_BORDER);
        guiGraphics.fill(left, top + PANEL_HEIGHT - 1, left + PANEL_WIDTH, top + PANEL_HEIGHT, PANEL_BORDER);
        guiGraphics.fill(left, top, left + 1, top + PANEL_HEIGHT, PANEL_BORDER);
        guiGraphics.fill(left + PANEL_WIDTH - 1, top, left + PANEL_WIDTH, top + PANEL_HEIGHT, PANEL_BORDER);

        guiGraphics.drawString(this.font, this.title, left + 10, top + 8, 0xFFFFFF, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.fpsm.map_creator.type"), left + 10, top + 26, 0xD0E3EA, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.fpsm.map_creator.name"), left + 10, top + 58, 0xD0E3EA, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.fpsm.map_creator.pos1"), left + 10, top + 96, 0xD0E3EA, false);
        guiGraphics.drawString(this.font, Component.translatable("gui.fpsm.map_creator.pos2"), left + 10, top + 126, 0xD0E3EA, false);
        guiGraphics.drawString(this.font, Component.literal("X"), left + 112, top + 80, 0x8FA7B3, false);
        guiGraphics.drawString(this.font, Component.literal("Y"), left + 168, top + 80, 0x8FA7B3, false);
        guiGraphics.drawString(this.font, Component.literal("Z"), left + 224, top + 80, 0x8FA7B3, false);

        super.render(guiGraphics, mouseX, mouseY, partialTick);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (this.mapNameField.keyPressed(keyCode, scanCode, modifiers)) return true;
        for (EditBox field : getPosFields()) {
            if (field.keyPressed(keyCode, scanCode, modifiers)) {
                return true;
            }
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean charTyped(char codePoint, int modifiers) {
        if (this.mapNameField.charTyped(codePoint, modifiers)) return true;
        for (EditBox field : getPosFields()) {
            if (field.charTyped(codePoint, modifiers)) {
                return true;
            }
        }
        return super.charTyped(codePoint, modifiers);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isInsidePanel(mouseX, mouseY)) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        BlockPos clickedPos = pickBlockPos(mouseX, mouseY);
        if (clickedPos == null) {
            return super.mouseClicked(mouseX, mouseY, button);
        }

        boolean isPos1 = button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
        FPSMatch.sendToServer(new ToolInteractionC2SPacket(
                isPos1 ? ToolInteractionAction.LEFT_CLICK_BLOCK : ToolInteractionAction.RIGHT_CLICK_BLOCK,
                clickedPos
        ));
        setBlockPosFields(clickedPos, isPos1);
        return true;
    }

    @Override
    public void onClose() {
        FPSMatch.sendToServer(new MapCreatorToolActionC2SPacket(
                MapCreatorToolActionC2SPacket.Action.SAVE_DRAFT,
                this.selectedType,
                this.mapNameField.getValue(),
                parseBlockPos(true),
                parseBlockPos(false)
        ));
        super.onClose();
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private EditBox addTextField(int x, int y, int width, int height, int maxLength, java.util.function.Predicate<String> filter) {
        EditBox editBox = new EditBox(this.font, x, y, width, height, Component.empty());
        editBox.setMaxLength(maxLength);
        editBox.setFilter(filter);
        this.addRenderableWidget(editBox);
        return editBox;
    }

    private EditBox addIntField(int x, int y) {
        return addTextField(x, y, 48, 18, 10, value -> value.matches("-?\\d*"));
    }

    private List<EditBox> getPosFields() {
        return List.of(pos1XField, pos1YField, pos1ZField, pos2XField, pos2YField, pos2ZField);
    }

    private void cycleType() {
        if (this.availableTypes.isEmpty()) {
            this.selectedType = "";
        } else {
            int currentIndex = this.availableTypes.indexOf(this.selectedType);
            int nextIndex = currentIndex < 0 ? 0 : (currentIndex + 1) % this.availableTypes.size();
            this.selectedType = this.availableTypes.get(nextIndex);
        }
        updateTypeButton();
    }

    private void updateTypeButton() {
        this.typeButton.setMessage(Component.literal(this.selectedType.isBlank() ? "-" : this.selectedType));
    }

    private String normalizeSelectedType(String type) {
        if (type != null && !type.isBlank() && this.availableTypes.contains(type)) {
            return type;
        }
        return this.availableTypes.isEmpty() ? "" : this.availableTypes.get(0);
    }

    private void loadFromHeldTool() {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return;
        }

        ItemStack stack = minecraft.player.getMainHandItem();
        if (!(stack.getItem() instanceof MapCreatorTool)) {
            return;
        }

        this.selectedType = normalizeSelectedType(MapCreatorTool.getSelectedType(stack));
        this.mapNameField.setValue(MapCreatorTool.getDraftMapName(stack));
        setBlockPosFields(MapCreatorTool.getBlockPos(stack, MapCreatorTool.BLOCK_POS_TAG_1), true);
        setBlockPosFields(MapCreatorTool.getBlockPos(stack, MapCreatorTool.BLOCK_POS_TAG_2), false);
        updateTypeButton();
    }

    private void setBlockPosFields(@Nullable BlockPos pos, boolean first) {
        EditBox xField = first ? pos1XField : pos2XField;
        EditBox yField = first ? pos1YField : pos2YField;
        EditBox zField = first ? pos1ZField : pos2ZField;
        xField.setValue(pos == null ? "" : Integer.toString(pos.getX()));
        yField.setValue(pos == null ? "" : Integer.toString(pos.getY()));
        zField.setValue(pos == null ? "" : Integer.toString(pos.getZ()));
    }

    private @Nullable BlockPos parseBlockPos(boolean first) {
        EditBox xField = first ? pos1XField : pos2XField;
        EditBox yField = first ? pos1YField : pos2YField;
        EditBox zField = first ? pos1ZField : pos2ZField;
        if (xField.getValue().isBlank() || yField.getValue().isBlank() || zField.getValue().isBlank()) {
            return null;
        }
        try {
            return new BlockPos(
                    Integer.parseInt(xField.getValue()),
                    Integer.parseInt(yField.getValue()),
                    Integer.parseInt(zField.getValue())
            );
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private void createMap() {
        FPSMatch.sendToServer(new MapCreatorToolActionC2SPacket(
                MapCreatorToolActionC2SPacket.Action.CREATE,
                this.selectedType,
                this.mapNameField.getValue(),
                parseBlockPos(true),
                parseBlockPos(false)
        ));
    }

    private boolean isInsidePanel(double mouseX, double mouseY) {
        int left = 18;
        int top = Math.max(18, (this.height - PANEL_HEIGHT) / 2);
        return mouseX >= left && mouseX < left + PANEL_WIDTH && mouseY >= top && mouseY < top + PANEL_HEIGHT;
    }

    private @Nullable BlockPos pickBlockPos(double mouseX, double mouseY) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || minecraft.player == null) {
            return null;
        }

        Camera camera = minecraft.gameRenderer.getMainCamera();
        Vec3 eyePosition = camera.getPosition();
        Vec3 direction = getRayDirection(camera, mouseX, mouseY);
        double reach = minecraft.player.getBlockReach();
        BlockHitResult hitResult = minecraft.level.clip(new ClipContext(
                eyePosition,
                eyePosition.add(direction.scale(reach)),
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                minecraft.player
        ));
        if (hitResult.getType() != HitResult.Type.BLOCK) {
            return null;
        }
        return hitResult.getBlockPos();
    }

    private Vec3 getRayDirection(Camera camera, double mouseX, double mouseY) {
        double normalizedX = mouseX / (double) this.width * 2.0D - 1.0D;
        double normalizedY = 1.0D - mouseY / (double) this.height * 2.0D;
        double aspect = (double) this.width / (double) this.height;
        double tanHalfFov = Math.tan(Math.toRadians(Minecraft.getInstance().options.fov().get()) / 2.0D);
        double horizontalScale = normalizedX * aspect * tanHalfFov;
        double verticalScale = normalizedY * tanHalfFov;

        Vec3 look = toVec3(camera.getLookVector());
        Vec3 up = toVec3(camera.getUpVector());
        Vec3 left = toVec3(camera.getLeftVector());
        return look.add(left.scale(-horizontalScale)).add(up.scale(verticalScale)).normalize();
    }

    private static Vec3 toVec3(Vector3f vector) {
        return new Vec3(vector.x(), vector.y(), vector.z());
    }
}
