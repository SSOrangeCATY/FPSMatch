package com.phasetranscrystal.fpsmatch.common.client.screen;

import com.google.gson.Gson;
import com.phasetranscrystal.fpsmatch.common.capability.team.ShopCapability;
import com.phasetranscrystal.fpsmatch.common.mapselect.MapRoomQueryService;
import com.phasetranscrystal.fpsmatch.core.map.BaseMap;
import com.phasetranscrystal.fpsmatch.core.shop.FPSMShop;
import com.phasetranscrystal.fpsmatch.core.shop.INamedType;
import com.phasetranscrystal.fpsmatch.core.shop.slot.ShopSlot;
import com.phasetranscrystal.fpsmatch.core.team.ServerTeam;
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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
    private final int totalIndex;
    private final Map<String, INamedType> types = new LinkedHashMap<>();
    private final FPSMShop<?> shop;

    public EditorShopContainer(int containerId, Inventory playerInventory, FriendlyByteBuf buf) {
        this(containerId, playerInventory, buf.readUtf(ID_MAX_LENGTH), buf.readUtf(ID_MAX_LENGTH), buf.readUtf(ID_MAX_LENGTH));
    }

    public EditorShopContainer(int containerId, Inventory playerInventory, String gameType, String mapName, String teamName) {
        super(VanillaGuiRegister.EDITOR_SHOP_CONTAINER.get(), containerId);
        this.gameType = gameType;
        this.mapName = mapName;
        this.teamName = teamName;
        this.shop = this.resolveShop().orElse(null);

        int total = 0;
        if (this.shop != null) {
            List<?> enums = shop.getEnums();
            for (Object type : enums) {
                if (!(type instanceof INamedType named)) {
                    continue;
                }
                rows++;
                this.types.put(named.name(), named);

                int slotCount = named.slotCount();
                total += slotCount;
                if (cols < slotCount) {
                    cols = slotCount;
                }
            }
        }

        this.totalIndex = total;
        this.itemStackHandler = new ItemStackHandler(this.totalIndex);

        int slotSpan = SLOT_SIZE + 4 * d;
        int start = 5;

        for (int row = 0; row < rows; row++) {
            if (shop == null || types.isEmpty()) {
                break;
            }

            List<String> typeNames = new ArrayList<>(types.keySet());
            if (row >= typeNames.size()) {
                break;
            }
            String type = typeNames.get(row);
            INamedType namedType = types.get(type);
            if (namedType == null) {
                continue;
            }

            List<ShopSlot> shopSlots = this.shop.getDefaultShopSlotListByType(type);
            for (int col = 0; col < namedType.slotCount(); col++) {
                int slotIndex = getSlotIndex(type, col);

                if (slotIndex < 0 || slotIndex >= itemStackHandler.getSlots()) {
                    continue;
                }

                ShopSlot shopSlot = (shopSlots != null && col < shopSlots.size()) ? shopSlots.get(col) : null;
                ItemStack slotItem = shopSlot == null ? ItemStack.EMPTY : shopSlot.process();

                SlotItemHandler customSlot = new SlotItemHandler(
                        itemStackHandler,
                        slotIndex,
                        start + col * slotSpan,
                        start + row * (SLOT_SIZE + d)
                );
                this.addSlot(customSlot);

                if (!slotItem.isEmpty()) {
                    itemStackHandler.setStackInSlot(slotIndex, slotItem);
                } else {
                    itemStackHandler.setStackInSlot(slotIndex, ItemStack.EMPTY);
                }
            }
        }
    }

    public Map<String, INamedType> getTypes() {
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

    private Optional<FPSMShop<?>> resolveShop() {
        return MapRoomQueryService.findMap(gameType, mapName)
                .flatMap(this::resolveTeam)
                .flatMap(ShopCapability::getShop);
    }

    private Optional<ServerTeam> resolveTeam(BaseMap map) {
        return map.getMapTeams().getTeamsWithSpectator().stream()
                .filter(team -> team.getName().equals(teamName))
                .findFirst();
    }

    private int getSlotIndex(String type, int col) {
        if (type == null || types.isEmpty() || col < 0) {
            return -1;
        }

        int index = 0;

        for (Map.Entry<String, INamedType> entry : this.types.entrySet()) {
            INamedType namedType = entry.getValue();
            if (namedType == null) {
                continue;
            }
            if (entry.getKey().equals(type)) {
                if (col >= namedType.slotCount()) {
                    return -1;
                }
                index += col;
                break;
            } else {
                index += namedType.slotCount();
            }
        }

        if (index >= this.totalIndex) {
            return -1;
        }
        return index;
    }

    private ShopSlot getShopSlot(int index) {
        if (shop == null || types.isEmpty() || index < 0 || index >= totalIndex) {
            return null;
        }

        int currentIndex = 0;
        for (Map.Entry<String, INamedType> entry : types.entrySet()) {
            String type = entry.getKey();
            INamedType namedType = entry.getValue();
            if (namedType == null) {
                continue;
            }
            int slotCount = namedType.slotCount();

            if (index >= currentIndex && index < currentIndex + slotCount) {
                int col = index - currentIndex;
                List<ShopSlot> shopSlots = shop.getDefaultShopSlotListByType(type);
                if (shopSlots != null && col < shopSlots.size()) {
                    return shopSlots.get(col);
                } else {
                    return null;
                }
            }
            currentIndex += slotCount;
        }
        return null;
    }

    private SlotRef getShopSlotRef(int index) {
        if (types.isEmpty() || index < 0 || index >= totalIndex) {
            return null;
        }

        int currentIndex = 0;
        for (Map.Entry<String, INamedType> entry : types.entrySet()) {
            INamedType namedType = entry.getValue();
            if (namedType == null) {
                continue;
            }
            int slotCount = namedType.slotCount();
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
        Optional<FPSMShop<?>> opt = this.resolveShop();
        if (opt.isEmpty()) {
            return new ArrayList<>();
        }
        FPSMShop<?> shop = opt.get();

        List<ShopSlot> allShopSlots = new ArrayList<>();
        shop.getDefaultShopDataMap().entrySet().stream()
                .sorted(Comparator.comparingInt(entry -> entry.getKey().ordinal()))
                .forEach(entry -> allShopSlots.addAll(entry.getValue()));

        return allShopSlots;
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
