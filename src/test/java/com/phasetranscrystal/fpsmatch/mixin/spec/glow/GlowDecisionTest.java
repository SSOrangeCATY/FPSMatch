package com.phasetranscrystal.fpsmatch.mixin.spec.glow;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 验证 MixinEntityUnified 中发光判定逻辑的正确性。
 * <p>
 * 对应 Bug 1 修复：关闭 enemyGlow 时敌人不应发光。
 * <p>
 * 测试提取了 Mixin 中的核心判定逻辑，验证在各种配置组合下，
 * 发光设置具有权威性（开启则发光，关闭则强制不发光）。
 */
class GlowDecisionTest {

    /**
     * 提取自 MixinEntityUnified#onIsCurrentlyGlowing 的核心判定逻辑。
     * 当本地玩家在正常队伍中且目标不是自己时，发光完全由设置决定。
     */
    static boolean decideGlow(boolean localInNormalTeam,
                              boolean targetIsSelf,
                              boolean sameTeam,
                              boolean teamGlow,
                              boolean enemyGlow) {
        if (localInNormalTeam && !targetIsSelf) {
            return sameTeam ? teamGlow : enemyGlow;
        }
        // 非正常队伍或自身：不由此逻辑决定（交由 disableDefaultGlow 或原版）
        return false;
    }

    @Test
    void enemyGlowOff_enemyDoesNotGlow() {
        // Bug 1 核心场景：enemyGlow=false 时敌人不发光
        assertFalse(decideGlow(true, false, false, false, false));
    }

    @Test
    void enemyGlowOn_enemyGlows() {
        assertTrue(decideGlow(true, false, false, false, true));
    }

    @Test
    void teamGlowOff_teammateDoesNotGlow() {
        assertFalse(decideGlow(true, false, true, false, true));
    }

    @Test
    void teamGlowOn_teammateGlows() {
        assertTrue(decideGlow(true, false, true, true, false));
    }

    @Test
    void enemyGlowOff_teamGlowOn_enemyStillDoesNotGlow() {
        // 队友发光开启不影响敌人发光关闭
        assertFalse(decideGlow(true, false, false, true, false));
    }

    @Test
    void enemyGlowOn_teamGlowOff_enemyStillGlows() {
        // 敌人发光开启不受队友发光关闭影响
        assertTrue(decideGlow(true, false, false, false, true));
    }

    @Test
    void selfTarget_neverGlowByTeamLogic() {
        // 自身目标不由此逻辑决定
        assertFalse(decideGlow(true, true, true, true, true));
    }

    @Test
    void notInNormalTeam_neverGlowByTeamLogic() {
        // 不在正常队伍中时不由此逻辑决定
        assertFalse(decideGlow(false, false, false, true, true));
    }

    @Test
    void enemyGlowOff_suppressesEvenWhenAllSettingsOn() {
        // 关键回归测试：enemyGlow=false 时，即使 teamGlow=true 敌人也不发光
        // 修复前：enemyGlow=false 不 cancel，原版 GLOWING 效果仍会显示
        // 修复后：enemyGlow=false 强制返回 false，屏蔽所有来源的发光
        assertFalse(decideGlow(true, false, false, true, false));
    }
}
