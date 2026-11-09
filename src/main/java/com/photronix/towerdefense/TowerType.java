package com.photronix.towerdefense;

import net.minecraft.world.level.material.MapColor;

import java.util.Locale;

/**
 * Every tower in the game. Cost is paid in coins at a crafting table (shapeless recipe in
 * data/towerdefense/recipes); the recipe is unlocked in the recipe book when the player
 * reaches {@code unlockWave}. Keep recipe JSON in sync if you change a cost.
 */
public enum TowerType {
    ARROW("Arrow Tower", 3, 1, TowerStats.ARROW, MapColor.COLOR_GREEN, "fast, single target"),
    FROST("Frost Tower", 5, 2, TowerStats.FROST, MapColor.ICE, "slows enemies in an area"),
    FLAME("Flame Tower", 4, 3, TowerStats.FLAME, MapColor.COLOR_RED, "very fast, short range"),
    CANNON("Cannon Tower", 7, 4, TowerStats.CANNON, MapColor.COLOR_ORANGE, "slow, big area damage"),
    STORM("Storm Tower", 9, 6, TowerStats.STORM, MapColor.COLOR_YELLOW, "chain lightning hits 5 enemies"),
    SNIPER("Sniper Tower", 8, 8, TowerStats.SNIPER, MapColor.COLOR_PURPLE, "huge range, heavy damage"),
    WITHER("Wither Tower", 9, 10, TowerStats.WITHER, MapColor.COLOR_BLACK, "deadly wither over time");

    public final String displayName;
    public final int cost;
    public final int unlockWave;
    public final TowerStats stats;
    public final MapColor mapColor;
    public final String blurb;

    TowerType(String displayName, int cost, int unlockWave, TowerStats stats, MapColor mapColor, String blurb) {
        this.displayName = displayName;
        this.cost = cost;
        this.unlockWave = unlockWave;
        this.stats = stats;
        this.mapColor = mapColor;
        this.blurb = blurb;
    }

    /** Registry / file name, e.g. "arrow_tower". */
    public String id() {
        return name().toLowerCase(Locale.ROOT) + "_tower";
    }
}
