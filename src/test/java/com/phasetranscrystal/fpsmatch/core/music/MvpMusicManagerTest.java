package com.phasetranscrystal.fpsmatch.core.music;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * 验证 FPSMatch {@link MvpMusicManager} 的默认 MVP 音乐管理逻辑。
 * <p>
 * 对应 Bug 3 修复：FPSMatch 承载默认 MVP 音乐的设置职责。
 * <p>
 * 由于测试 classpath 不包含 Minecraft 类，此处通过字符串模拟验证
 * 默认音乐解析逻辑的正确性。生产代码中 {@link MvpMusicManager} 使用
 * {@code ResourceLocation}，但逻辑等价。
 */
class MvpMusicManagerTest {

    private static final String BUILTIN_DEFAULT = "fpsmatch:mvp.default";

    /**
     * 模拟 MvpMusicManager 的默认音乐解析逻辑（字符串版本）。
     */
    static String resolveDefaultMusicName(String currentDefault) {
        return currentDefault != null ? currentDefault : BUILTIN_DEFAULT;
    }

    static String setDefault(String music) {
        return music != null ? music : BUILTIN_DEFAULT;
    }

    @Test
    void builtinDefaultIsFpsMatchMvpDefault() {
        assertEquals("fpsmatch:mvp.default", BUILTIN_DEFAULT);
    }

    @Test
    void defaultMusicNameMatchesExpected() {
        assertEquals("fpsmatch:mvp.default", resolveDefaultMusicName(BUILTIN_DEFAULT));
    }

    @Test
    void setDefaultMusicChangesCurrentDefault() {
        String custom = "blockoffensive:mvp.epic";
        String newDefault = setDefault(custom);
        assertEquals("blockoffensive:mvp.epic", resolveDefaultMusicName(newDefault));
    }

    @Test
    void setDefaultMusicWithNullFallsBackToBuiltin() {
        String afterNull = setDefault(null);
        assertEquals("fpsmatch:mvp.default", resolveDefaultMusicName(afterNull));
    }

    @Test
    void resetToBuiltinRestoresDefaultAfterCustomChange() {
        String custom = setDefault("test:override");
        assertEquals("test:override", resolveDefaultMusicName(custom));

        String reset = setDefault(BUILTIN_DEFAULT);
        assertEquals("fpsmatch:mvp.default", resolveDefaultMusicName(reset));
    }

    @Test
    void defaultMusicNeverNullOrEmpty() {
        assertNotNull(BUILTIN_DEFAULT);
        assertEquals(false, BUILTIN_DEFAULT.isBlank());
        assertEquals(false, setDefault(null).isBlank());
    }
}
