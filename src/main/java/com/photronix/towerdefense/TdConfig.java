package com.photronix.towerdefense;

import net.minecraftforge.common.ForgeConfigSpec;

/** Options in config/towerdefense-common.toml. */
public final class TdConfig {
    private TdConfig() {}

    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue AUTO_ARENA;
    private static final ForgeConfigSpec.BooleanValue NIGHT_MODE;
    private static final ForgeConfigSpec.BooleanValue CLEAN_RECIPE_BOOK;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        AUTO_ARENA = b.comment("Build the Core Siege battleground automatically where a player first joins a brand-new world.",
                        "Existing worlds are never touched; use /tdarena there.")
                .define("autoBuildArenaInNewWorlds", true);
        NIGHT_MODE = b.comment("Make it night and give nearby players Night Vision while a game is running.",
                        "Time is restored when the game ends.")
                .define("nightDuringGames", true);
        CLEAN_RECIPE_BOOK = b.comment("Remove vanilla recipes from a player's recipe book at login so only Core Siege recipes show.",
                        "(Vanilla recipes can still be crafted by hand. The mod also stops vanilla from auto-unlocking recipes.)")
                .define("cleanRecipeBookAtLogin", true);
        SPEC = b.build();
    }

    public static boolean autoArena() {
        try {
            return AUTO_ARENA.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    public static boolean cleanRecipeBook() {
        try {
            return CLEAN_RECIPE_BOOK.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    public static boolean nightMode() {
        try {
            return NIGHT_MODE.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }
}
