package com.phasetranscrystal.fpsmatch.common.client.screen;

import com.lowdragmc.lowdraglib.gui.texture.ColorRectTexture;
import com.lowdragmc.lowdraglib.gui.widget.LabelWidget;
import com.lowdragmc.lowdraglib.gui.widget.TextFieldWidget;
import com.lowdragmc.lowdraglib.gui.widget.WidgetGroup;
import com.phasetranscrystal.fpsmatch.FPSMatch;
import com.phasetranscrystal.fpsmatch.common.item.MapCreatorTool;
import com.phasetranscrystal.fpsmatch.common.item.tool.ToolInteractionAction;
import com.phasetranscrystal.fpsmatch.common.packet.MapCreatorToolActionC2SPacket;
import com.phasetranscrystal.fpsmatch.common.packet.OpenMapCreatorToolScreenS2CPacket;
import com.phasetranscrystal.fpsmatch.common.packet.ToolInteractionC2SPacket;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
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

public class MapCreatorToolScreen extends FPSMWidgetScreen {
    private static final int PANEL_WIDTH = 300;
    private static final int PANEL_HEIGHT = 188;

    private List<String> availableTypes;
    private String selectedType;

    private TextFieldWidget mapNameField;
    private TextFieldWidget pos1XField;
    private TextFieldWidget pos1YField;
    private TextFieldWidget pos1ZField;
    private TextFieldWidget pos2XField;
    private TextFieldWidget pos2YField;
    private TextFieldWidget pos2ZField;
    private LabelWidget typeLabel;

    private int panelLeft;
    private int panelTop;

    public MapCreatorToolScreen(OpenMapCreatorToolScreenS2CPacket data) {
        super(Component.translatable("gui.fpsm.map_creator.title"));
        this.availableTypes = new ArrayList<>(data.availableTypes());
        this.selectedType = normalizeSelectedType(data.selectedType());
    }

    public void applyData(OpenMapCreatorToolScreenS2CPacket data) {
        this.availableTypes = new ArrayList<>(data.availableTypes());
        this.selectedType = normalizeSelectedType(data.selectedType());
        if (this.mapNameField != null) {
            this.mapNameField.setCurrentString(data.draftMapName());
            setBlockPosFields(data.pos1(), true);
            setBlockPosFields(data.pos2(), false);
        }
        updateTypeLabel();
    }

    @Override
    protected void buildUI() {
        panelLeft = 18;
        panelTop = Math.max(18, (height - PANEL_HEIGHT) / 2);

        // 半透明背景
        root.addWidget(new WidgetGroup(0, 0, width, height)
                .setBackground(new ColorRectTexture(0x5A000000)));

        // 面板
        root.addWidget(new WidgetGroup(panelLeft, panelTop, PANEL_WIDTH, PANEL_HEIGHT)
                .setBackground(new ColorRectTexture(0xD0191D22)));
        root.addWidget(new WidgetGroup(panelLeft, panelTop, PANEL_WIDTH, 1).setBackground(new ColorRectTexture(0xFF7DA3B8)));
        root.addWidget(new WidgetGroup(panelLeft, panelTop + PANEL_HEIGHT - 1, PANEL_WIDTH, 1).setBackground(new ColorRectTexture(0xFF7DA3B8)));
        root.addWidget(new WidgetGroup(panelLeft, panelTop, 1, PANEL_HEIGHT).setBackground(new ColorRectTexture(0xFF7DA3B8)));
        root.addWidget(new WidgetGroup(panelLeft + PANEL_WIDTH - 1, panelTop, 1, PANEL_HEIGHT).setBackground(new ColorRectTexture(0xFF7DA3B8)));

        // 标题
        root.addWidget(new LabelWidget(panelLeft + 10, panelTop + 8, title.getString()).setTextColor(0xFFFFFFFF));
        root.addWidget(new LabelWidget(panelLeft + 10, panelTop + 26,
                Component.translatable("gui.fpsm.map_creator.type").getString()).setTextColor(0xFFD0E3EA));
        root.addWidget(new LabelWidget(panelLeft + 10, panelTop + 58,
                Component.translatable("gui.fpsm.map_creator.name").getString()).setTextColor(0xFFD0E3EA));
        root.addWidget(new LabelWidget(panelLeft + 10, panelTop + 96,
                Component.translatable("gui.fpsm.map_creator.pos1").getString()).setTextColor(0xFFD0E3EA));
        root.addWidget(new LabelWidget(panelLeft + 10, panelTop + 126,
                Component.translatable("gui.fpsm.map_creator.pos2").getString()).setTextColor(0xFFD0E3EA));
        root.addWidget(new LabelWidget(panelLeft + 112, panelTop + 80, "X").setTextColor(0xFF8FA7B3));
        root.addWidget(new LabelWidget(panelLeft + 168, panelTop + 80, "Y").setTextColor(0xFF8FA7B3));
        root.addWidget(new LabelWidget(panelLeft + 224, panelTop + 80, "Z").setTextColor(0xFF8FA7B3));

        // 类型按钮
        typeLabel = new LabelWidget(panelLeft + 110, panelTop + 20,
                (selectedType.isBlank() ? "-" : selectedType)).setTextColor(0xFFFFFFFF);
        root.addWidget(typeLabel);

        root.addWidget(FPSMWidgets.button(panelLeft + 110, panelTop + 20, 172, 20,
                Component.literal(selectedType.isBlank() ? "-" : selectedType), this::cycleType));

        // 地图名字段
        mapNameField = createTextField(panelLeft + 110, panelTop + 52, 172, 18, s -> s);
        root.addWidget(mapNameField);

        // 坐标字段
        pos1XField = createIntField(panelLeft + 110, panelTop + 90);
        pos1YField = createIntField(panelLeft + 166, panelTop + 90);
        pos1ZField = createIntField(panelLeft + 222, panelTop + 90);
        pos2XField = createIntField(panelLeft + 110, panelTop + 120);
        pos2YField = createIntField(panelLeft + 166, panelTop + 120);
        pos2ZField = createIntField(panelLeft + 222, panelTop + 120);
        root.addWidget(pos1XField); root.addWidget(pos1YField); root.addWidget(pos1ZField);
        root.addWidget(pos2XField); root.addWidget(pos2YField); root.addWidget(pos2ZField);

        // 按钮
        root.addWidget(FPSMWidgets.button(panelLeft + 18, panelTop + 154, 122, 20,
                Component.translatable("gui.fpsm.map_creator.create"), this::createMap));
        root.addWidget(FPSMWidgets.button(panelLeft + 160, panelTop + 154, 122, 20,
                Component.translatable("gui.fpsm.close"), this::onClose));

        loadFromHeldTool();
    }

    private TextFieldWidget createTextField(int x, int y, int w, int h, java.util.function.Function<String, String> validator) {
        TextFieldWidget field = new TextFieldWidget(x, y, w, h, () -> "", s -> {});
        field.setValidator(validator);
        field.setMaxStringLength(64);
        return field;
    }

    private TextFieldWidget createIntField(int x, int y) {
        return createTextField(x, y, 48, 18, s -> s.matches("-?\\d*") ? s : s.replaceAll("[^\\d-]", ""));
    }

    private List<TextFieldWidget> getPosFields() {
        return List.of(pos1XField, pos1YField, pos1ZField, pos2XField, pos2YField, pos2ZField);
    }

    private void cycleType() {
        if (availableTypes.isEmpty()) {
            selectedType = "";
        } else {
            int idx = availableTypes.indexOf(selectedType);
            selectedType = availableTypes.get(idx < 0 ? 0 : (idx + 1) % availableTypes.size());
        }
        updateTypeLabel();
    }

    private void updateTypeLabel() {
        if (typeLabel != null) {
            typeLabel.setText(selectedType.isBlank() ? "-" : selectedType);
        }
    }

    private String normalizeSelectedType(String type) {
        if (type != null && !type.isBlank() && availableTypes.contains(type)) return type;
        return availableTypes.isEmpty() ? "" : availableTypes.get(0);
    }

    private void loadFromHeldTool() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        ItemStack stack = mc.player.getMainHandItem();
        if (!(stack.getItem() instanceof MapCreatorTool)) return;
        selectedType = normalizeSelectedType(MapCreatorTool.getSelectedType(stack));
        mapNameField.setCurrentString(MapCreatorTool.getDraftMapName(stack));
        setBlockPosFields(MapCreatorTool.getBlockPos(stack, MapCreatorTool.BLOCK_POS_TAG_1), true);
        setBlockPosFields(MapCreatorTool.getBlockPos(stack, MapCreatorTool.BLOCK_POS_TAG_2), false);
        updateTypeLabel();
    }

    private void setBlockPosFields(@Nullable BlockPos pos, boolean first) {
        TextFieldWidget xf = first ? pos1XField : pos2XField;
        TextFieldWidget yf = first ? pos1YField : pos2YField;
        TextFieldWidget zf = first ? pos1ZField : pos2ZField;
        xf.setCurrentString(pos == null ? "" : Integer.toString(pos.getX()));
        yf.setCurrentString(pos == null ? "" : Integer.toString(pos.getY()));
        zf.setCurrentString(pos == null ? "" : Integer.toString(pos.getZ()));
    }

    private @Nullable BlockPos parseBlockPos(boolean first) {
        TextFieldWidget xf = first ? pos1XField : pos2XField;
        TextFieldWidget yf = first ? pos1YField : pos2YField;
        TextFieldWidget zf = first ? pos1ZField : pos2ZField;
        try {
            return new BlockPos(
                    Integer.parseInt(xf.getCurrentString()),
                    Integer.parseInt(yf.getCurrentString()),
                    Integer.parseInt(zf.getCurrentString()));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private void createMap() {
        FPSMatch.sendToServer(new MapCreatorToolActionC2SPacket(
                MapCreatorToolActionC2SPacket.Action.CREATE,
                selectedType, mapNameField.getCurrentString(), parseBlockPos(true), parseBlockPos(false)));
    }

    private boolean isInsidePanel(double mx, double my) {
        return mx >= panelLeft && mx < panelLeft + PANEL_WIDTH && my >= panelTop && my < panelTop + PANEL_HEIGHT;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (isInsidePanel(mouseX, mouseY)) return super.mouseClicked(mouseX, mouseY, button);
        if (button != GLFW.GLFW_MOUSE_BUTTON_LEFT && button != GLFW.GLFW_MOUSE_BUTTON_RIGHT)
            return super.mouseClicked(mouseX, mouseY, button);
        BlockPos pos = pickBlockPos(mouseX, mouseY);
        if (pos == null) return super.mouseClicked(mouseX, mouseY, button);
        boolean isPos1 = button == GLFW.GLFW_MOUSE_BUTTON_LEFT;
        FPSMatch.sendToServer(new ToolInteractionC2SPacket(
                isPos1 ? ToolInteractionAction.LEFT_CLICK_BLOCK : ToolInteractionAction.RIGHT_CLICK_BLOCK, pos));
        setBlockPosFields(pos, isPos1);
        return true;
    }

    @Override
    public void onClose() {
        FPSMatch.sendToServer(new MapCreatorToolActionC2SPacket(
                MapCreatorToolActionC2SPacket.Action.SAVE_DRAFT,
                selectedType, mapNameField.getCurrentString(), parseBlockPos(true), parseBlockPos(false)));
        super.onClose();
    }

    private @Nullable BlockPos pickBlockPos(double mouseX, double mouseY) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) return null;
        Camera cam = mc.gameRenderer.getMainCamera();
        Vec3 eye = cam.getPosition();
        Vec3 dir = getRayDirection(cam, mouseX, mouseY);
        BlockHitResult hit = mc.level.clip(new ClipContext(eye, eye.add(dir.scale(mc.player.getBlockReach())),
                ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, mc.player));
        return hit.getType() == HitResult.Type.BLOCK ? hit.getBlockPos() : null;
    }

    private Vec3 getRayDirection(Camera camera, double mouseX, double mouseY) {
        double nx = mouseX / (double) width * 2.0 - 1.0;
        double ny = 1.0 - mouseY / (double) height * 2.0;
        double aspect = (double) width / (double) height;
        double tanFov = Math.tan(Math.toRadians(Minecraft.getInstance().options.fov().get()) / 2.0);
        Vec3 look = toVec3(camera.getLookVector());
        Vec3 up = toVec3(camera.getUpVector());
        Vec3 left = toVec3(camera.getLeftVector());
        return look.add(left.scale(-nx * aspect * tanFov)).add(up.scale(ny * tanFov)).normalize();
    }

    private static Vec3 toVec3(Vector3f v) { return new Vec3(v.x(), v.y(), v.z()); }
}