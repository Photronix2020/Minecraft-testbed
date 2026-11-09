package com.photronix.towerdefense;

/**
 * Tuning values for one tower type. Edit these to rebalance the game.
 *
 * @param range         targeting radius in blocks
 * @param cooldownTicks ticks between shots (20 ticks = 1 second)
 * @param damage        damage per shot
 * @param splashRadius  if > 0, other enemies within this radius of the target are hit too
 * @param slowTicks     if > 0, hit enemies are slowed for this long
 * @param slowAmplifier slowness level (0 = Slowness I)
 * @param rgb           beam colour as 0xRRGGBB
 */
public record TowerStats(double range, int cooldownTicks, float damage, float splashRadius,
                         int slowTicks, int slowAmplifier, int rgb) {

    public static final TowerStats ARROW = new TowerStats(14, 10, 3.0F, 0, 0, 0, 0x55DD55);
    public static final TowerStats CANNON = new TowerStats(12, 45, 9.0F, 3.0F, 0, 0, 0xFF7722);
    public static final TowerStats FROST = new TowerStats(10, 15, 1.5F, 2.5F, 50, 2, 0x66CCFF);
}
