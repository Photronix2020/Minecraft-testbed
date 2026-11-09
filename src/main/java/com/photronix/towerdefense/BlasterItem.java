package com.photronix.towerdefense;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.List;

/** The player's own weapon: right-click fires an instant beam at the first monster in the crosshairs. Unlimited ammo. */
public class BlasterItem extends Item {
    public static final double RANGE = 40.0;
    public static final float DAMAGE = 5.0F;
    public static final int COOLDOWN_TICKS = 8;

    public BlasterItem(Properties properties) {
        super(properties);
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.getCooldowns().isOnCooldown(this)) {
            return InteractionResultHolder.fail(stack);
        }
        if (level instanceof ServerLevel serverLevel) {
            fire(serverLevel, player, player.getEyePosition(), player.getLookAngle());
        }
        player.getCooldowns().addCooldown(this, COOLDOWN_TICKS);
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    /** Only monsters (and wave enemies) can be shot, never pets, villagers or other players. */
    private static boolean isTarget(Entity e) {
        return e.isAlive() && !e.isSpectator() && e instanceof Mob mob
                && (mob instanceof Enemy || mob.getTags().contains(CoreBlockEntity.ENEMY_TAG));
    }

    /**
     * Fires one shot. Returns the entity that was hit, or null. Walls stop the beam.
     * Public so the headless test can shoot without a real player.
     */
    @Nullable
    public static LivingEntity fire(ServerLevel level, LivingEntity shooter, Vec3 eye, Vec3 look) {
        Vec3 end = eye.add(look.scale(RANGE));
        BlockHitResult wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        Vec3 limit = wall.getType() == HitResult.Type.MISS ? end : wall.getLocation();

        AABB box = new AABB(eye, limit).inflate(1.0);
        EntityHitResult hit = ProjectileUtil.getEntityHitResult(shooter, eye, limit, box,
                BlasterItem::isTarget, RANGE * RANGE);

        Vec3 impact = limit;
        LivingEntity target = null;
        if (hit != null && hit.getEntity() instanceof LivingEntity living) {
            target = living;
            impact = hit.getLocation();
            living.invulnerableTime = 0; // allow rapid fire
            living.hurt(shooter instanceof Player p ? level.damageSources().playerAttack(p)
                    : level.damageSources().mobAttack(shooter), DAMAGE);
        }

        // visible beam starting just in front of the muzzle
        Vec3 muzzle = eye.add(look.scale(0.8)).add(0, -0.15, 0);
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(0.4F, 0.9F, 1.0F), 0.9F);
        Vec3 delta = impact.subtract(muzzle);
        int steps = Math.max(2, (int) (delta.length() * 2));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = muzzle.add(delta.scale(i / (double) steps));
            level.sendParticles(dust, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.ELECTRIC_SPARK, impact.x, impact.y, impact.z, target != null ? 6 : 2, 0.1, 0.1, 0.1, 0.05);
        level.playSound(null, shooter.blockPosition(), SoundEvents.FIREWORK_ROCKET_BLAST, SoundSource.PLAYERS, 0.7F, 1.6F);
        return target;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7Right-click to fire. Unlimited ammo."));
        tooltip.add(Component.literal("§7Damage " + (int) DAMAGE + ", range " + (int) RANGE + ", only hurts monsters."));
    }
}
