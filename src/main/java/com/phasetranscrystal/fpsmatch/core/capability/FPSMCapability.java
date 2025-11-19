package com.phasetranscrystal.fpsmatch.core.capability;

import com.google.common.collect.Lists;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.serialization.Codec;
import com.phasetranscrystal.fpsmatch.core.event.RegisterFPSMCommandEvent;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.FriendlyByteBuf;

/**
 * 基础能力接口，定义所有能力的通用规范
 * @param <H> 能力的持有者类型（如BaseTeam、BaseMap）
 * @apiNote 更多功能拓展请见：
 * @see Synchronizable 允许网络同步功能
 * @see Savable 允许持久化数据
 */
public abstract class FPSMCapability<H> {

    /**
     * 初始化能力（持有者添加能力时调用）
     */
    public void init() {}

    /**
     * 重置能力状态
     */
    public void reset() {}

    /**
     * 销毁能力（持有者移除能力时调用）
     */
    public void destroy() {}

    /**
     * 获取能力的持有者
     */
    public abstract H getHolder();

    /**
     * 不允许在CapabilityMap中移除此能力
     */
    public boolean isImmutable(){
        return false;
    };

    /**
     * 获取能力名称（默认使用类名）
     */
    public String getName() {
        return this.getClass().getSimpleName();
    }

    /**
     * 能力工厂接口，负责创建能力实例
     * @param <H> 持有者类型
     * @param <T> 能力类型
     */
    public interface Factory<H, T extends FPSMCapability<H>> {
        /**
         * 创建能力实例
         * @param holder 能力的持有者
         */
        T create(H holder);

        /**
         * 获取能力对应的命令（可选）
         */
        default Command command() {
            return null;
        }

        /**
         * 能力相关的命令接口
         * @see RegisterFPSMCommandEvent 命令注册相关
         */
        interface Command {
            String getName();
            LiteralArgumentBuilder<CommandSourceStack> builder(LiteralArgumentBuilder<CommandSourceStack> builder, CommandBuildContext context);
        }
    }

    public interface Savable<T> {
        Codec<T> codec();   // 获取数据的编解码器
        T write(T value);
        T read();
    }

    /**
     * 支持网络同步接口
     * @apiNote 你不应该在初始化能力时对Holder进行任何操作
     * @see FPSMCapability 使用init()方法对holder进行操作;
     * */
    public interface Synchronizable {
        void readFromBuf(FriendlyByteBuf buf);
        void writeToBuf(FriendlyByteBuf buf);
    }
}