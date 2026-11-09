package com.photronix.towerdefense;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public class TowerBlockEntity extends BlockEntity {
    private static final double CHAIN_RADIUS = 7.0;
    private int cooldown = 0;

    public TowerBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.TOWER_BE.get(), pos, state);
    }

    private TowerStats stats() {
        return ((TowerBlock) getBlockState().getBlock()).stats;
    }

    private static boolean isEnemy(Mob m) {
        return m.isAlive() && m.getTags().contains(CoreBlockEntity.ENEMY_TAG);
    }

    public void serverTick(ServerLevel level) {
        if (--cooldown > 0) {
            return;
        }
        TowerStats stats = stats();
        Vec3 origin = Vec3.atCenterOf(worldPosition).add(0, 0.6, 0);

        Mob best = null;
        double bestDist = stats.range() * stats.range();
        for (Mob m : level.getEntitiesOfClass(Mob.class, new AABB(worldPosition).inflate(stats.range()),
                TowerBlockEntity::isEnemy)) {
            double d = m.distanceToSqr(origin);
            if (d <= bestDist) {
                bestDist = d;
                best = m;
            }
        }
        if (best == null) {
            cooldown = 5; // nothing in range, look again shortly
            return;
        }

        cooldown = stats.cooldownTicks();
        Vec3 target = aimPoint(best);
        drawBeam(level, origin, target, stats.rgb(), 1.2F);
        hit(level, best, stats);

        if (stats.splashRadius() > 0) {
            level.sendParticles(stats.slowTicks() > 0 ? ParticleTypes.SNOWFLAKE : ParticleTypes.EXPLOSION,
                    target.x, target.y, target.z, stats.slowTicks() > 0 ? 20 : 1, 0.8, 0.5, 0.8, 0.02);
            for (Mob other : level.getEntitiesOfClass(Mob.class, best.getBoundingBox().inflate(stats.splashRadius()),
                    TowerBlockEntity::isEnemy)) {
                if (other != best) {
                    hit(level, other, stats);
                }
            }
        }

        if (stats.chainTargets() > 0) {
            chainLightning(level, best, stats);
        }

        if (stats.witherTicks() > 0) {
            level.sendParticles(ParticleTypes.SMOKE, target.x, target.y, target.z, 8, 0.3, 0.4, 0.3, 0.02);
        }
        if (stats.range() <= 7) { // short-range flame tower: show fire at the target
            level.sendParticles(ParticleTypes.FLAME, target.x, target.y, target.z, 6, 0.25, 0.3, 0.25, 0.02);
        }

        level.playSound(null, worldPosition, stats.sound(), SoundSource.BLOCKS, 0.5F, 1.2F);
    }

    /** Lightning jumps from the target to the nearest other enemies, one after another. */
    private void chainLightning(ServerLevel level, Mob first, TowerStats stats) {
        List<Mob> hitSoFar = new ArrayList<>();
        hitSoFar.add(first);
        Mob current = first;
        for (int i = 0; i < stats.chainTargets(); i++) {
            final Mob from = current;
            Mob next = level.getEntitiesOfClass(Mob.class, from.getBoundingBox().inflate(CHAIN_RADIUS),
                            m -> isEnemy(m) && !hitSoFar.contains(m))
                    .stream().min(Comparator.comparingDouble(m -> m.distanceToSqr(from))).orElse(null);
            if (next == null) {
                break;
            }
            drawBeam(level, aimPoint(from), aimPoint(next), stats.rgb(), 1.0F);
            level.sendParticles(ParticleTypes.ELECTRIC_SPARK, next.getX(), next.getY() + 1, next.getZ(), 6, 0.3, 0.5, 0.3, 0.1);
            hit(level, next, stats);
            hitSoFar.add(next);
            current = next;
        }
    }

    private static Vec3 aimPoint(Mob mob) {
        return mob.position().add(0, mob.getBbHeight() * 0.6, 0);
    }

    private static void hit(ServerLevel level, Mob mob, TowerStats stats) {
        mob.invulnerableTime = 0; // let several towers damage the same enemy in the same moment
        mob.hurt(level.damageSources().magic(), stats.damage());
        if (stats.slowTicks() > 0) {
            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stats.slowTicks(), stats.slowAmplifier()));
        }
        if (stats.witherTicks() > 0) {
            mob.addEffect(new MobEffectInstance(MobEffects.WITHER, stats.witherTicks(), stats.witherAmp()));
        }
    }

    private static void drawBeam(ServerLevel level, Vec3 from, Vec3 to, int rgb, float size) {
        Vector3f color = new Vector3f(((rgb >> 16) & 0xFF) / 255F, ((rgb >> 8) & 0xFF) / 255F, (rgb & 0xFF) / 255F);
        ParticleOptions dust = new DustParticleOptions(color, size);
        Vec3 delta = to.subtract(from);
        int steps = Math.max(2, (int) (delta.length() * 2));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.add(delta.scale(i / (double) steps));
            level.sendParticles(dust, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }
}
