package com.phasetranscrystal.fpsmatch.core.team.capability;

import com.phasetranscrystal.fpsmatch.core.team.BaseTeam;

/**
 * 队伍能力接口，定义队伍可扩展的能力规范
 */
public interface TeamCapability {

    /**
     * 能力初始化方法，在能力被添加到队伍时调用
     */
    default void init() {}

    /**
     * 能力重置方法，在队伍需要重置状态时调用
     */
    default void reset() {}

    /**
     * 能力销毁方法，在能力被从队伍移除时调用
     */
    default void destroy() {}

    default String getName(){
        return this.getClass().getSimpleName();
    }

    /**
     * 能力工厂接口，负责创建TeamCapability实例
     * @param <T> 能力类型
     */
    interface Factory<T extends TeamCapability> {
        T create(BaseTeam team);
    }
}