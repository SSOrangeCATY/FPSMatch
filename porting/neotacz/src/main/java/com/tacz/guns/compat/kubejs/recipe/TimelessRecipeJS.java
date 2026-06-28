package com.tacz.guns.compat.kubejs.recipe;

import com.tacz.guns.compat.kubejs.util.GunSmithTableResultInfo;
import dev.latvian.mods.kubejs.recipe.KubeRecipe;

public class TimelessRecipeJS extends KubeRecipe {
    public GunSmithTableResultInfo getResultInfo() {
        GunSmithTableResultInfo resultInfo = getValue(TimelessGunSmithTableRecipeSchema.RESULT);
        if (resultInfo == null) {
            resultInfo = GunSmithTableResultInfo.create();
            setResultInfo(resultInfo);
        }
        return resultInfo;
    }

    public void setResultInfo(GunSmithTableResultInfo info) {
        setValue(TimelessGunSmithTableRecipeSchema.RESULT, info);
    }

    /**
     * 设置后对配方结果的影响改至{@link com.tacz.guns.compat.kubejs.util.GunSmithTableResultInfo}
     */
    public TimelessRecipeJS outputGroupName(String group) {
        getResultInfo().setGroupName(group);
        return this;
    }

    /**
     * 设置后对配方结果的影响改至{@link com.tacz.guns.compat.kubejs.util.GunSmithTableResultInfo}
     */
    public TimelessRecipeJS outputGroup(GunSmithTableResultInfo.OutputGroupName group) {
        return outputGroupName(group.getName());
    }
}
