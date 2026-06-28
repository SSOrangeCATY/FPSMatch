package com.tacz.guns.compat.kubejs.recipe;

import com.google.gson.JsonObject;
import com.tacz.guns.GunMod;
import com.tacz.guns.compat.kubejs.util.GunSmithTableResultInfo;
import dev.latvian.mods.kubejs.recipe.RecipeKey;
import dev.latvian.mods.kubejs.recipe.schema.KubeRecipeFactory;
import dev.latvian.mods.kubejs.recipe.schema.RecipeSchema;
import net.minecraft.resources.Identifier;

import java.util.List;

public interface TimelessGunSmithTableRecipeSchema {
    RecipeKey<GunSmithTableResultInfo> RESULT = GunSmithTableResultComponents.RESULT_INFO.outputKey("result");
    RecipeKey<List<JsonObject>> MATERIALS = GunSmithTableResultComponents.MATERIALS.inputKey("materials");

    RecipeSchema SCHEMA = new RecipeSchema(RESULT, MATERIALS)
            .factory(new KubeRecipeFactory(
                    Identifier.fromNamespaceAndPath(GunMod.MOD_ID, "gun_smith_table_crafting"),
                    TimelessRecipeJS.class,
                    TimelessRecipeJS::new
            ))
            .constructor(RESULT, MATERIALS);
}
