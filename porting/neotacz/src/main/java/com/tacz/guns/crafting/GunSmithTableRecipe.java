package com.tacz.guns.crafting;

import com.tacz.guns.crafting.result.GunSmithTableResult;
import com.tacz.guns.init.ModRecipe;
import com.tacz.guns.resource.pojo.data.recipe.TableRecipe;
import net.minecraft.core.RegistryAccess;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.PlacementInfo;
import net.minecraft.world.item.crafting.Recipe;
import net.minecraft.world.item.crafting.RecipeBookCategories;
import net.minecraft.world.item.crafting.RecipeBookCategory;
import net.minecraft.world.item.crafting.RecipeInput;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.level.Level;

import java.util.List;

public class GunSmithTableRecipe implements Recipe<RecipeInput> {
    private final Identifier id;
    private final GunSmithTableResult result;
    private final List<GunSmithTableIngredient> inputs;

    public GunSmithTableRecipe(Identifier id, GunSmithTableResult result, List<GunSmithTableIngredient> inputs) {
        this.id = id;
        this.result = result;
        this.inputs = inputs;
    }

    public GunSmithTableRecipe(Identifier id, TableRecipe tableRecipe) {
        this(id, tableRecipe.getResult(), tableRecipe.getMaterials());
    }

    @Override
    @Deprecated
    public boolean matches(RecipeInput input, Level level) {
        return false;
    }

    @Override
    @Deprecated
    public ItemStack assemble(RecipeInput input) {
        return ItemStack.EMPTY;
    }

    public boolean canCraftInDimensions(int pWidth, int pHeight) {
        return true;
    }

    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return this.result.getResult().copy();
    }

    public Identifier getId() {
        return this.id;
    }

    @Override
    public boolean showNotification() {
        return false;
    }

    @Override
    public String group() {
        return "";
    }

    @Override
    @SuppressWarnings("unchecked")
    public RecipeSerializer<? extends Recipe<RecipeInput>> getSerializer() {
        return (RecipeSerializer<? extends Recipe<RecipeInput>>) (RecipeSerializer<?>) ModRecipe.GUN_SMITH_TABLE_RECIPE_SERIALIZER.get();
    }

    @Override
    @SuppressWarnings("unchecked")
    public RecipeType<? extends Recipe<RecipeInput>> getType() {
        return (RecipeType<? extends Recipe<RecipeInput>>) (RecipeType<?>) ModRecipe.GUN_SMITH_TABLE_CRAFTING.get();
    }

    @Override
    public PlacementInfo placementInfo() {
        return PlacementInfo.create(this.inputs.stream().map(GunSmithTableIngredient::getIngredient).toList());
    }

    @Override
    public RecipeBookCategory recipeBookCategory() {
        return RecipeBookCategories.CRAFTING_MISC;
    }

    public ItemStack getOutput() {
        return result.getResult();
    }

    public List<GunSmithTableIngredient> getInputs() {
        return inputs;
    }

    public GunSmithTableResult getResult() {
        return result;
    }

    public void init() {
        result.init();
    }

    public Identifier getTab() {
        return result.getGroup();
    }
}
