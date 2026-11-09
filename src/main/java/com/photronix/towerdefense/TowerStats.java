package com.photronix.towerdefense;

import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;

/**
 * Tuning values for one tower type. Edit these to rebalance the game.
 *
 * @param range         targeting radius in blocks
 * @param cooldownTicks ticks between shots (20 ticks = 1 second)
 * @param damage        damage per shot
 * @param splashRadius  if > 0, other enemies within this radius of the target are hit too
 * @param slowTicks     if > 0, hit enemies are slowed for this long
 * @param slowAmplifier slowness level (0 = Slowness I)
 * @param chainTargets  if > 0, lightning jumps to this many extra enemies near the target
 * @param witherTicks   if > 0, hit enemies get the Wither effect for this long
 * @param witherAmp     Wither level (0 = Wither I)
 * @param rgb           beam colour as 0xRRGGBB
 * @param sound         shot sound
 */
public record TowerStats(double range, int cooldownTicks, float damage, float splashRadius,
                         int slowTicks, int slowAmplifier, int chainTargets,
                         int witherTicks, int witherAmp, int rgb, SoundEvent sound) {

    public static final TowerStats ARROW = new TowerStats(14, 10, 3.0F, 0, 0, 0, 0, 0, 0, 0x55DD55, SoundEvents.ARROW_SHOOT);
    public static final TowerStats FLAME = new TowerStats(7, 6, 2.0F, 0, 0, 0, 0, 0, 0, 0xFF5522, SoundEvents.BLAZE_SHOOT);
    public static final TowerStats FROST = new TowerStats(10, 15, 1.5F, 2.5F, 50, 2, 0, 0, 0, 0x66CCFF, SoundEvents.GLASS_BREAK);
    public static final TowerStats CANNON = new TowerStats(12, 45, 9.0F, 3.0F, 0, 0, 0, 0, 0, 0xFF9933, SoundEvents.GENERIC_EXPLODE);
    public static final TowerStats STORM = new TowerStats(11, 50, 6.0F, 0, 0, 0, 4, 0, 0, 0xFFEE55, SoundEvents.LIGHTNING_BOLT_IMPACT);
    public static final TowerStats SNIPER = new TowerStats(28, 50, 16.0F, 0, 0, 0, 0, 0, 0, 0xCC88FF, SoundEvents.CROSSBOW_SHOOT);
    public static final TowerStats WITHER = new TowerStats(12, 40, 2.0F, 0, 0, 0, 0, 100, 3, 0x7733AA, SoundEvents.WITHER_SHOOT);
}
