package com.tacz.guns.compat.kubejs;

import com.tacz.guns.GunMod;
import com.tacz.guns.api.GunProperties;
import com.tacz.guns.compat.kubejs.custom.CustomGunItemBuilder;
import com.tacz.guns.compat.kubejs.events.GunKubeJSEvents;
import com.tacz.guns.compat.kubejs.events.TimelessClientEvents;
import com.tacz.guns.compat.kubejs.events.TimelessCommonEvents;
import com.tacz.guns.compat.kubejs.events.TimelessServerEvents;
import com.tacz.guns.compat.kubejs.recipe.GunSmithTableResultComponents;
import com.tacz.guns.compat.kubejs.recipe.TimelessGunSmithTableRecipeSchema;
import com.tacz.guns.compat.kubejs.util.GunSmithTableResultInfo;
import com.tacz.guns.compat.kubejs.util.TimelessItemWrapper;
import dev.latvian.mods.kubejs.event.EventGroupRegistry;
import dev.latvian.mods.kubejs.plugin.KubeJSPlugin;
import dev.latvian.mods.kubejs.recipe.component.RecipeComponentTypeRegistry;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchemaRegistry;
import dev.latvian.mods.kubejs.registry.BuilderTypeRegistry;
import dev.latvian.mods.kubejs.script.BindingRegistry;
import dev.latvian.mods.kubejs.script.TypeWrapperRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.Identifier;
import net.neoforged.fml.loading.FMLEnvironment;

public class TimelessKubeJSPlugin implements KubeJSPlugin {
    @Override
    public void registerBuilderTypes(BuilderTypeRegistry registry) {
        registry.of(Registries.ITEM, callback -> callback.add(
                Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "tacz_gun"),
                CustomGunItemBuilder.class,
                CustomGunItemBuilder::new
        ));
    }

    @Override
    public void registerEvents(EventGroupRegistry registry) {
        //提早加载防止出现问题
        TimelessCommonEvents.INSTANCE.init();
        TimelessServerEvents.INSTANCE.init();
        if (FMLEnvironment.getDist().isClient()) {
            TimelessClientEvents.INSTANCE.init();
        }
        registry.register(GunKubeJSEvents.GROUP);
    }

    @Override
    public void registerBindings(BindingRegistry registry) {
        registry.add("TimelessItem", TimelessItemWrapper.class);
        registry.add("GunProperties", GunProperties.class);
        registry.add("GunSmithTableResultInfo", GunSmithTableResultInfo.class);
    }

    @Override
    public void registerTypeWrappers(TypeWrapperRegistry registry) {
        registry.register(
                GunSmithTableResultInfo.class,
                (TypeWrapperRegistry.ContextFromFunction<GunSmithTableResultInfo>) GunSmithTableResultInfo::of
        );
    }

    @Override
    public void registerRecipeSchemas(RecipeSchemaRegistry registry) {
        registry.namespace(GunMod.MOD_ID).register("gun_smith_table_crafting", TimelessGunSmithTableRecipeSchema.SCHEMA);
    }

    @Override
    public void registerRecipeComponents(RecipeComponentTypeRegistry registry) {
        registry.unit(GunSmithTableResultComponents.RESULT_INFO);
        registry.unit(GunSmithTableResultComponents.MATERIALS);
    }
}
