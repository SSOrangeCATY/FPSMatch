package com.phasetranscrystal.fpsmatch.core.map;

import com.phasetranscrystal.fpsmatch.core.BaseMap;
import com.phasetranscrystal.fpsmatch.core.BaseTeam;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import org.jetbrains.annotations.NotNull;

import java.util.List;
import java.util.UUID;

public interface GiveStartKitsMap<T extends BaseMap> extends IMap<T> {
    List<ItemStack> getKits(BaseTeam team);
    void setKits(BaseTeam team, ItemStack itemStack);
    void setAllTeamKits(ItemStack itemStack);
    default void givePlayerKits(ServerPlayer player){
        BaseMap map = this.getMap();
        BaseTeam team = map.getMapTeams().getTeamByPlayer(player);
        if(team != null){
            List<ItemStack> items = this.getKits(team);
            player.getInventory().clearContent();
            items.forEach(player.getInventory()::add);
        }
    }
    default void giveTeamKits(@NotNull BaseTeam team){
        BaseMap map = this.getMap();
        for(UUID uuid : team.getPlayers()){
            Player player = map.getServerLevel().getPlayerByUUID(uuid);
            if (player != null){
                List<ItemStack> items = this.getKits(team);
                player.getInventory().clearContent();
                items.forEach(player.getInventory()::add);
            }
        };
    }

    default void giveAllPlayersKits(){
        BaseMap map = this.getMap();
        for(UUID uuid : this.getMap().getMapTeams().getJoinedPlayers()){
            Player player = map.getServerLevel().getPlayerByUUID(uuid);
            if (player != null){
                BaseTeam team = map.getMapTeams().getTeamByPlayer(player);
                if(team != null){
                    List<ItemStack> items = this.getKits(team);
                    player.getInventory().clearContent();
                    items.forEach(player.getInventory()::add);
                }
            }
        };
    }
}
