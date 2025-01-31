package com.phasetranscrystal.fpsmatch.core.map;

import com.phasetranscrystal.fpsmatch.core.BaseTeam;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

public interface GiveStartKitsMap<T extends BaseMap> extends IMap<T> {
    ArrayList<ItemStack> getKits(BaseTeam team);
    void addKits(BaseTeam team, ItemStack itemStack);
    default void setTeamKits(BaseTeam team, ArrayList<ItemStack> itemStack){
        this.clearTeamKits(team);
        this.getKits(team).addAll(itemStack);
    }
    void setStartKits(Map<String,ArrayList<ItemStack>> kits);
    void setAllTeamKits(ItemStack itemStack);
    default void clearTeamKits(BaseTeam team){
        this.getKits(team).clear();
    }
    default boolean removeItem(BaseTeam team, ItemStack itemStack){
        AtomicBoolean flag = new AtomicBoolean(false);
        this.getKits(team).forEach((itemStack1 -> {
            if(itemStack1.is(itemStack.getItem())){
                itemStack1.shrink(itemStack.getCount());
                flag.set(true);
            }
        }));
        return flag.get();
    }


    default void clearAllTeamKits(){
        for(BaseTeam team : this.getMap().getMapTeams().getTeams()){
            this.clearTeamKits(team);
        }
    }

    default void givePlayerKits(ServerPlayer player){
        BaseMap map = this.getMap();
        BaseTeam team = map.getMapTeams().getTeamByPlayer(player);
        if(team != null){
            ArrayList<ItemStack> items = this.getKits(team);
            player.getInventory().clearContent();
            items.forEach((itemStack -> {
                player.getInventory().add(itemStack.copy());
                player.inventoryMenu.broadcastChanges();
                player.inventoryMenu.slotsChanged(player.getInventory());
            }));
        }else{
            System.out.println("givePlayerKits: player not in team ->" + player.getDisplayName().getString());
        }
    }
    default void giveTeamKits(@NotNull BaseTeam team){
        BaseMap map = this.getMap();
        for(UUID uuid : team.getPlayerList()){
            Player player = map.getServerLevel().getPlayerByUUID(uuid);
            if (player != null){
                ArrayList<ItemStack> items = this.getKits(team);
                player.getInventory().clearContent();
                items.forEach((itemStack -> {
                    player.getInventory().add(itemStack.copy());
                }));
                player.inventoryMenu.broadcastChanges();
                player.inventoryMenu.slotsChanged(player.getInventory());
            }
        }
    }

    default void giveAllPlayersKits(){
        BaseMap map = this.getMap();
        for(UUID uuid : this.getMap().getMapTeams().getJoinedPlayers()){
            Player player = map.getServerLevel().getPlayerByUUID(uuid);
            if (player != null){
                BaseTeam team = map.getMapTeams().getTeamByPlayer(player);
                if(team != null){
                    ArrayList<ItemStack> items = this.getKits(team);
                    player.getInventory().clearContent();
                    items.forEach((itemStack -> {
                        player.getInventory().add(itemStack.copy());
                    }));
                    player.inventoryMenu.broadcastChanges();
                    player.inventoryMenu.slotsChanged(player.getInventory());
                }else{
                    System.out.println("givePlayerKits: player not in team ->" + player.getDisplayName().getString());
                }
            }else{
                System.out.println("givePlayerKits: player not found ->" + uuid);
            }
        }
    }

    Map<String,List<ItemStack>> getStartKits();
}
