package com.phasetranscrystal.fpsmatch.common.client.screen;

import com.google.gson.Gson;
import com.google.gson.JsonElement;
import com.phasetranscrystal.fpsmatch.core.shop.FPSMShop;
import com.phasetranscrystal.fpsmatch.core.shop.INamedType;
import com.phasetranscrystal.fpsmatch.core.shop.slot.ShopSlot;
import com.phasetranscrystal.fpsmatch.util.FPSMCodec;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.AbstractContainerMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemStackHandler;
import net.minecraftforge.items.SlotItemHandler;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.NotNull;

import java.util.*;

public class EditorShopContainer extends AbstractContainerMenu {
    private static final int SLOT_SIZE = 18;
    private static final int d = 10;
    private static final int ID_MAX_LENGTH = 128;

    private int rows = 0;
    private int cols = 0;

    private final String gameType;
    private final String mapName;
    private final String teamName;
    private final ItemStackHandler itemStackHandler;
    private int totalIndex;
    private final Map<String, TypeInfo> types = new LinkedHashMap<>();
    private final List<ShopSlot> allShopSlots;

    public record TypeInfo(String name, int slotCount, int startIndex) {
    }

    // Server-side constructor
    public EditorShopContainer(int containerId, Inventory playerInventory, FPSMShop<?> shop, String gameType, String mapName, String teamName) {
        super(VanillaGuiRegister.EDITOR_SHOP_CONTAINER.get(), containerId);
        this.gameType = gameType;
        this.mapName = mapName;
        this.teamName = teamName;

        List<?> enums = shop.getEnums();
        int total = 0;
        for (Object type : enums) {
            if (!(type instanceof INamedType named)) continue;
            rows++;
            int slotCount = named.slotCount();
            this.types.put(named.name(), new TypeInfo(named.name(), slotCount, total));
            total += slotCount;
            if (cols < slotCount) cols = slotCount;
        }
        this.totalIndex = total;
        this.itemStackHandler = new ItemStackHandler(total);
        this.allShopSlots = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            this.allShopSlots.add(null);
        }

        int slotSpan = SLOT_SIZE + 4 * d;
        int start = 5;
        for (int row = 0; row < rows; row++) {
            List<String> typeNames = new ArrayList<>(types.keySet());
            String type = typeNames.get(row);
            List<ShopSlot> shopSlots = shop.getDefaultShopSlotListByType(type);
            int slotCount = types.get(type).slotCount();
            for (int col = 0; col < slotCount; col++) {
                int slotIndex = types.get(type).startIndex() + col;
                ShopSlot shopSlot = (shopSlots != null && col < shopSlots.size()) ? shopSlots.get(col) : null;
                ItemStack slotItem = shopSlot == null ? ItemStack.EMPTY : shopSlot.process();
                if (shopSlot != null) {
                    this.allShopSlots.set(slotIndex, shopSlot);
                }
                SlotItemHandler customSlot = new SlotItemHandler(
                        itemStackHandler,
                        slotIndex,
                        start + col * slotSpan,
                        start + row * (SLOT_SIZE + d)
                );
                this.addSlot(customSlot);
                if (!slotItem.isEmpty()) {
                    itemStackHandler.setStackInSlot(slotIndex, slotItem);
                }
            }
        }
    }

    // Client-side constructor
    public EditorShopContainer(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        super(VanillaGuiRegister.EDITOR_SHOP_CONTAINER.get(), containerId);
        this.gameType = buf.readUtf(ID_MAX_LENGTH);
        this.mapName = buf.readUtf(ID_MAX_LENGTH);
        this.teamName = buf.readUtf(ID_MAX_LENGTH);

        int typeCount = buf.readInt();
        int total = 0;
        for (int t = 0; t < typeCount; t++) {
            String typeName = buf.readUtf(ID_MAX_LENGTH);
            int slotCount = buf.readInt();
            rows++;
            this.types.put(typeName, new TypeInfo(typeName, slotCount, total));
            total += slotCount;
            if (cols < slotCount) cols = slotCount;
        }
        this.totalIndex = total;
        this.itemStackHandler = new ItemStackHandler(total);
        this.allShopSlots = new ArrayList<>(total);
        for (int i = 0; i < total; i++) {
            this.allShopSlots.add(null);
        }

        int slotSpan = SLOT_SIZE + 4 * d;
        int start = 5;
        for (int row = 0; row < rows; row++) {
            List<String> typeNames = new ArrayList<>(types.keySet());
            String type = typeNames.get(row);
            int slotCount = types.get(type).slotCount();
            for (int col = 0; col < slotCount; col++) {
                int slotIndex = types.get(type).startIndex() + col;
                ItemStack slotItem = buf.readItem();
                String json = buf.readUtf();
                ShopSlot shopSlot = null;
                if (!json.isEmpty()) {
                    try {
                        shopSlot = FPSMCodec.decodeFromJson(ShopSlot.CODEC, new Gson().fromJson(json, JsonElement.class));
                    } catch (Exception ignored) {
                    }
                }
                if (shopSlot != null) {
                    this.allShopSlots.set(slotIndex, shopSlot);
                }
                SlotItemHandler customSlot = new SlotItemHandler(
                        itemStackHandler,
                        slotIndex,
                        start + col * slotSpan,
                        start + row * (SLOT_SIZE + d)
                );
                this.addSlot(customSlot);
                if (!slotItem.isEmpty()) {
                    itemStackHandler.setStackInSlot(slotIndex, slotItem);
                }
            }
        }
    }

    public Map<String, TypeInfo> getTypes() {
        return types;
    }

    public int getTotalIndex() {
        return totalIndex;
    }

    public ItemStackHandler getItemStackHandler() {
        return itemStackHandler;
    }

    public int getCols() {
        return cols;
    }

    public int getRows() {
        return rows;
    }

    public String getGameType() {
        return gameType;
    }

    public String getMapName() {
        return mapName;
    }

    public String getTeamName() {
        return teamName;
    }

    @Override
    public boolean stillValid(@NotNull Player player) {
        return true;
    }

    @Override
    public @NotNull ItemStack quickMoveStack(@NotNull Player player, int slotIndex) {
        return ItemStack.EMPTY;
    }

    private int getSlotIndex(String type, int col) {
        if (type == null || types.isEmpty() || col < 0) {
            return -1;
        }
        TypeInfo info = types.get(type);
        if (info == null || col >= info.slotCount()) {
            return -1;
        }
        return info.startIndex() + col;
    }

    private ShopSlot getShopSlot(int index) {
        if (index < 0 || index >= allShopSlots.size()) {
            return null;
        }
        return allShopSlots.get(index);
    }

    private SlotRef getShopSlotRef(int index) {
        if (types.isEmpty() || index < 0 || index >= totalIndex) {
            return null;
        }
        int currentIndex = 0;
        for (Map.Entry<String, TypeInfo> entry : types.entrySet()) {
            TypeInfo info = entry.getValue();
            int slotCount = info.slotCount();
            if (index >= currentIndex && index < currentIndex + slotCount) {
                return new SlotRef(entry.getKey(), index - currentIndex);
            }
            currentIndex += slotCount;
        }
        return null;
    }

    @Override
    public void clicked(int slotIndex, int button, @NotNull ClickType clickType, @NotNull Player player) {
        boolean isCustomContainer = slotIndex >= 0 && slotIndex < this.totalIndex;
        if (isCustomContainer) {
            ShopSlot targetShopSlot = getShopSlot(slotIndex);
            SlotRef slotRef = getShopSlotRef(slotIndex);
            if (targetShopSlot != null && slotRef != null) {
                this.openSecondMenu(player, targetShopSlot, slotRef);
            }
            return;
        }
        super.clicked(slotIndex, button, clickType, player);
    }

    @Override
    public void removed(@NotNull Player pPlayer) {
        super.removed(pPlayer);
    }

    public List<ShopSlot> getAllSlots() {
        return Collections.unmodifiableList(allShopSlots);
    }

    private void openSecondMenu(Player player, ShopSlot shopSlot, SlotRef slotRef) {
        if (player == null || shopSlot == null || slotRef == null) {
            return;
        }
        if (player instanceof ServerPlayer serverPlayer) {
            NetworkHooks.openScreen(serverPlayer,
                    new SimpleMenuProvider(
                            (windowId, inv, p) -> new EditShopSlotMenu(windowId, inv, shopSlot, gameType, mapName, teamName, slotRef.type(), slotRef.slotNum()),
                            Component.translatable("gui.fpsm.edit_shop_slot.title")
                    ),
                    buf -> {
                        String json = "";
                        try {
                            json = new Gson().toJson(FPSMCodec.encodeToJson(ShopSlot.CODEC, shopSlot));
                        } catch (Exception e) {
                            json = "";
                        }
                        buf.writeUtf(json);
                        buf.writeUtf(gameType);
                        buf.writeUtf(mapName);
                        buf.writeUtf(teamName);
                        buf.writeUtf(slotRef.type());
                        buf.writeInt(slotRef.slotNum());
                    }
            );
        }
    }

    private record SlotRef(String type, int slotNum) {
    }
}