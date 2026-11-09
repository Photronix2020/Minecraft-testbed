package com.photronix.towerdefense;

import net.minecraftforge.common.ForgeConfigSpec;

/** Options in config/towerdefense-common.toml. */
public final class TdConfig {
    private TdConfig() {}

    public static final ForgeConfigSpec SPEC;
    private static final ForgeConfigSpec.BooleanValue AUTO_ARENA;
    private static final ForgeConfigSpec.BooleanValue NIGHT_MODE;
    private static final ForgeConfigSpec.BooleanValue CLEAN_RECIPE_BOOK;
    private static final ForgeConfigSpec.BooleanValue GIVE_RIFLE;
    private static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> EXTRA_KIT_ITEMS;
    private static final ForgeConfigSpec.ConfigValue<java.util.List<? extends String>> RESUPPLY_ITEMS;

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
        GIVE_RIFLE = b.comment("Include the Siege Rifle in the starter kit. Turn off if another gun mod supplies the weapons.")
                .define("giveSiegeRifleInKit", true);
        EXTRA_KIT_ITEMS = b.comment("Extra items for the starter kit (new players and /tdkit), e.g. a gun from another mod.",
                        "Format: modid:item{nbt} xCOUNT  (NBT and count are optional). Entries for mods that aren't installed are skipped.",
                        "Tip: hold the item in-game and run /data get entity @s SelectedItem to see its id and NBT.",
                        "Example: ['tacz:modern_kinetic_gun{GunId:\"modpack:gun_id\",GunFireMode:\"AUTO\",GunCurrentAmmoCount:30,HasBulletInBarrel:1b}']")
                .defineListAllowEmpty("extraKitItems", java.util.List.of(), o -> o instanceof String);
        RESUPPLY_ITEMS = b.comment("Items every nearby player receives at the start of a game and each time a wave is cleared (e.g. ammo for a gun mod).",
                        "Same format as extraKitItems. Entries for mods that aren't installed are skipped.",
                        "Example: ['tacz:ammo{AmmoId:\"pack:ammo_id\"} x64']")
                .defineListAllowEmpty("resupplyEachWave", java.util.List.of(), o -> o instanceof String);
        SPEC = b.build();
    }

    public static boolean autoArena() {
        try {
            return AUTO_ARENA.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    public static java.util.List<? extends String> resupplyItems() {
        try {
            return RESUPPLY_ITEMS.get();
        } catch (IllegalStateException notLoaded) {
            return java.util.List.of();
        }
    }

    public static boolean giveRifle() {
        try {
            return GIVE_RIFLE.get();
        } catch (IllegalStateException notLoaded) {
            return true;
        }
    }

    public static java.util.List<? extends String> extraKitItems() {
        try {
            return EXTRA_KIT_ITEMS.get();
        } catch (IllegalStateException notLoaded) {
            return java.util.List.of();
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
