package com.photronix.towerdefense;

import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.joml.Vector3f;

import java.util.List;

public class TowerBlockEntity extends BlockEntity {
    private int cooldown = 0;

    public TowerBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.TOWER_BE.get(), pos, state);
    }

    private TowerStats stats() {
        return ((TowerBlock) getBlockState().getBlock()).stats;
    }

    public void serverTick(ServerLevel level) {
        if (--cooldown > 0) {
            return;
        }
        TowerStats stats = stats();
        Vec3 origin = Vec3.atCenterOf(worldPosition).add(0, 0.6, 0);

        Mob best = null;
        double bestDist = stats.range() * stats.range();
        List<Mob> candidates = level.getEntitiesOfClass(Mob.class,
                new AABB(worldPosition).inflate(stats.range()),
                m -> m.isAlive() && m.getTags().contains(CoreBlockEntity.ENEMY_TAG));
        for (Mob m : candidates) {
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
        Vec3 target = best.position().add(0, best.getBbHeight() * 0.6, 0);
        drawBeam(level, origin, target, stats.rgb());
        hit(level, best, stats);

        if (stats.splashRadius() > 0) {
            level.sendParticles(stats.slowTicks() > 0 ? ParticleTypes.SNOWFLAKE : ParticleTypes.EXPLOSION,
                    target.x, target.y, target.z, stats.slowTicks() > 0 ? 20 : 1, 0.8, 0.5, 0.8, 0.02);
            for (Mob other : level.getEntitiesOfClass(Mob.class, best.getBoundingBox().inflate(stats.splashRadius()),
                    m -> m != null && m.isAlive() && m.getTags().contains(CoreBlockEntity.ENEMY_TAG))) {
                if (other != best) {
                    hit(level, other, stats);
                }
            }
        }

        SoundEvent sound = stats.splashRadius() > 0 && stats.slowTicks() == 0
                ? SoundEvents.GENERIC_EXPLODE
                : stats.slowTicks() > 0 ? SoundEvents.GLASS_BREAK : SoundEvents.ARROW_SHOOT;
        level.playSound(null, worldPosition, sound, SoundSource.BLOCKS, 0.5F, 1.2F);
    }

    private static void hit(ServerLevel level, Mob mob, TowerStats stats) {
        mob.invulnerableTime = 0; // let several towers damage the same enemy in the same moment
        mob.hurt(level.damageSources().magic(), stats.damage());
        if (stats.slowTicks() > 0) {
            mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, stats.slowTicks(), stats.slowAmplifier()));
        }
    }

    private static void drawBeam(ServerLevel level, Vec3 from, Vec3 to, int rgb) {
        Vector3f color = new Vector3f(((rgb >> 16) & 0xFF) / 255F, ((rgb >> 8) & 0xFF) / 255F, (rgb & 0xFF) / 255F);
        DustParticleOptions dust = new DustParticleOptions(color, 1.2F);
        Vec3 delta = to.subtract(from);
        int steps = Math.max(2, (int) (delta.length() * 2));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = from.add(delta.scale(i / (double) steps));
            level.sendParticles(dust, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
    }
}
