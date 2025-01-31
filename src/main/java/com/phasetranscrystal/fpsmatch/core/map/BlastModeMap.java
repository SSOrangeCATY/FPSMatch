package com.phasetranscrystal.fpsmatch.core.map;

import com.phasetranscrystal.fpsmatch.core.data.AreaData;
import net.minecraft.world.entity.player.Player;

import java.util.List;

public interface BlastModeMap<T extends BaseMap> extends IMap<T>{
    //TODO WIP
    void addBombArea(AreaData area);
    List<AreaData> getBombAreaData(); // Arraylist
    void setBlastTeam(String team);
    void setBlasting(int blasting);
    void setExploded(boolean exploded);
    int isBlasting();
    boolean isExploded();
    boolean checkCanPlacingBombs(String team);
    boolean checkPlayerIsInBombArea(Player player);
}
