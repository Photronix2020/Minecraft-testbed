package com.photronix.towerdefense;

import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.crafting.Recipe;

import java.util.ArrayList;
import java.util.List;

/**
 * Tower recipes are unlocked through Minecraft's own recipe book: locked towers don't show up there,
 * and each unlock pops the usual "Recipe unlocked" toast. Reaching a wave unlocks its towers.
 */
public final class TowerUnlocks {
    private TowerUnlocks() {}

    private static ResourceLocation recipeId(String path) {
        return new ResourceLocation(TowerDefenseMod.MOD_ID, path);
    }

    /** Unlocks every tower whose unlock wave is <= {@code upToWave}. Returns the towers that were newly unlocked. */
    public static List<TowerType> award(ServerPlayer player, int upToWave, boolean announce) {
        List<TowerType> newlyUnlocked = new ArrayList<>();
        List<Recipe<?>> recipes = new ArrayList<>();
        for (TowerType type : TowerType.values()) {
            ResourceLocation id = recipeId(type.id());
            if (type.unlockWave <= upToWave && !player.getRecipeBook().contains(id)) {
                player.server.getRecipeManager().byKey(id).ifPresent(r -> {
                    recipes.add(r);
                    newlyUnlocked.add(type);
                });
            }
        }
        if (!recipes.isEmpty()) {
            player.awardRecipes(recipes);
            if (announce) {
                for (TowerType type : newlyUnlocked) {
                    player.sendSystemMessage(Component.literal("§b§lNew tower unlocked: §f" + type.displayName
                            + " §7(" + type.cost + " coins, " + type.blurb + "). Open the recipe book at a crafting table."));
                }
            }
        }
        return newlyUnlocked;
    }

    /** Removes every non-Core-Siege recipe from the player's recipe book (e.g. ones unlocked before this mod's fix). */
    public static void cleanRecipeBook(ServerPlayer player) {
        List<Recipe<?>> stray = new ArrayList<>();
        for (Recipe<?> recipe : player.server.getRecipeManager().getRecipes()) {
            if (!recipe.getId().getNamespace().equals(TowerDefenseMod.MOD_ID) && player.getRecipeBook().contains(recipe)) {
                stray.add(recipe);
            }
        }
        if (!stray.isEmpty()) {
            player.resetRecipes(stray);
        }
    }

    /** First-join unlocks: the starting towers and the Defense Core recipe. */
    public static void awardStarter(ServerPlayer player) {
        award(player, 1, false);
        for (String extra : new String[]{"defense_core", "siege_blaster"}) {
            ResourceLocation id = recipeId(extra);
            if (!player.getRecipeBook().contains(id)) {
                player.server.getRecipeManager().byKey(id).ifPresent(r -> player.awardRecipes(List.of(r)));
            }
        }
    }

    /** Chat summary of every tower: cost, what it does, and whether it is unlocked. */
    public static void sendRecipeList(ServerPlayer player) {
        player.sendSystemMessage(Component.literal("§6§lCore Siege towers§r §7(craft with coins; shapeless recipe)"));
        for (TowerType type : TowerType.values()) {
            boolean unlocked = player.getRecipeBook().contains(recipeId(type.id()));
            player.sendSystemMessage(Component.literal(unlocked
                    ? "§a✔ §f" + type.displayName + " §7- " + type.cost + " coins - " + type.blurb
                    : "§8✖ " + type.displayName + " - unlocks at wave " + type.unlockWave));
        }
    }
}
