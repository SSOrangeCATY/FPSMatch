package com.phasetranscrystal.fpsmatch.common.spectator.teammate;

/** 服务器与客户端共识：当前观战意图。 */
public enum SpectateMode {
    ATTACH, // 因FPSM死亡：需要附身队友
    FREE    // 自由旁观/手动旁观：不拦键也不强制队友视角
}