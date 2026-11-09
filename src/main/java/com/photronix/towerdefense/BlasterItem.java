package com.photronix.towerdefense;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

/**
 * The Siege Rifle (item id "siege_blaster"): a bolt-action hitscan rifle.
 * Right-click fires one powerful, long-range shot; hold Sneak to scope in (zoom) for a perfectly accurate shot,
 * otherwise there is a little hip-fire spread. The bullet pierces up to 3 monsters in a line (less damage each),
 * and a hit on the head does double damage. Unlimited ammo; the slow bolt-action cooldown is the trade-off.
 */
public class BlasterItem extends Item {
    public static final double RANGE = 100.0;
    public static final float DAMAGE = 14.0F;
    public static final float HEADSHOT_MULTIPLIER = 2.0F;
    public static final int MAX_PIERCE = 3;
    public static final float PIERCE_FALLOFF = 0.7F;
    public static final int COOLDOWN_TICKS = 22;
    private static final double HIP_FIRE_SPREAD = 0.025;

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
            Vec3 look = player.getLookAngle();
            if (!player.isShiftKeyDown()) { // hip fire: a little spread; scoped (sneaking) shots are exact
                RandomSourceHelper.jitter(look, HIP_FIRE_SPREAD, serverLevel.random);
                look = RandomSourceHelper.last;
            }
            fire(serverLevel, player, player.getEyePosition(), look);
        } else {
            player.setXRot(player.getXRot() - 3.0F); // recoil kick on the local camera
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
     * Fires one shot along {@code look}. Returns every monster that was hit, nearest first. Walls stop the bullet.
     * Public so the headless test can shoot without a real player.
     */
    public static List<LivingEntity> fire(ServerLevel level, LivingEntity shooter, Vec3 eye, Vec3 look) {
        Vec3 end = eye.add(look.scale(RANGE));
        BlockHitResult wall = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, shooter));
        Vec3 limit = wall.getType() == HitResult.Type.MISS ? end : wall.getLocation();

        // every monster the bullet line passes through, nearest first
        record Contact(LivingEntity entity, Vec3 point, double dist) {}
        List<Contact> contacts = new ArrayList<>();
        for (Entity e : level.getEntities(shooter, new AABB(eye, limit).inflate(2.0), BlasterItem::isTarget)) {
            if (e instanceof LivingEntity living) {
                Optional<Vec3> point = living.getBoundingBox().inflate(0.15).clip(eye, limit);
                point.ifPresent(p -> contacts.add(new Contact(living, p, p.distanceToSqr(eye))));
            }
        }
        contacts.sort(Comparator.comparingDouble(Contact::dist));

        List<LivingEntity> hits = new ArrayList<>();
        Vec3 impact = limit;
        float damage = DAMAGE;
        boolean headshot = false;
        for (Contact c : contacts) {
            if (hits.size() >= MAX_PIERCE) {
                break;
            }
            boolean head = c.point().y >= c.entity().getEyeY() - 0.3;
            float dealt = head ? damage * HEADSHOT_MULTIPLIER : damage;
            c.entity().invulnerableTime = 0; // each shot always lands
            c.entity().hurt(shooter instanceof Player p ? level.damageSources().playerAttack(p)
                    : level.damageSources().mobAttack(shooter), dealt);
            level.sendParticles(head ? ParticleTypes.CRIT : ParticleTypes.ELECTRIC_SPARK,
                    c.point().x, c.point().y, c.point().z, head ? 12 : 5, 0.15, 0.15, 0.15, 0.1);
            headshot |= head;
            hits.add(c.entity());
            damage *= PIERCE_FALLOFF;
            if (hits.size() >= MAX_PIERCE) {
                impact = c.point();
            }
        }
        if (headshot && shooter instanceof ServerPlayer sp) {
            sp.displayClientMessage(Component.literal("§6§lHeadshot!"), true);
        }

        // visible tracer starting just in front of the muzzle
        Vec3 muzzle = eye.add(look.scale(0.9)).add(0, -0.15, 0);
        DustParticleOptions dust = new DustParticleOptions(new Vector3f(1.0F, 0.85F, 0.35F), 0.8F);
        Vec3 delta = impact.subtract(muzzle);
        int steps = Math.max(2, (int) (Math.min(delta.length(), 60) * 1.5));
        for (int i = 0; i <= steps; i++) {
            Vec3 p = muzzle.add(delta.scale(i / (double) steps));
            level.sendParticles(dust, p.x, p.y, p.z, 1, 0, 0, 0, 0);
        }
        level.sendParticles(ParticleTypes.SMOKE, muzzle.x, muzzle.y, muzzle.z, 4, 0.05, 0.05, 0.05, 0.01);
        level.playSound(null, shooter.blockPosition(), SoundEvents.FIREWORK_ROCKET_LARGE_BLAST, SoundSource.PLAYERS, 1.0F, 1.3F);
        level.playSound(null, shooter.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 0.8F, 0.7F);
        return hits;
    }

    /** Tiny helper so the spread maths lives in one place. */
    private static final class RandomSourceHelper {
        static Vec3 last = Vec3.ZERO;

        static void jitter(Vec3 look, double spread, net.minecraft.util.RandomSource random) {
            last = look.add((random.nextDouble() - 0.5) * 2 * spread,
                    (random.nextDouble() - 0.5) * 2 * spread,
                    (random.nextDouble() - 0.5) * 2 * spread).normalize();
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        tooltip.add(Component.literal("§7Bolt-action rifle. Right-click to fire."));
        tooltip.add(Component.literal("§7Hold §fSneak§7 to scope in for an exact shot."));
        tooltip.add(Component.literal("§7Damage " + (int) DAMAGE + ", headshots x" + (int) HEADSHOT_MULTIPLIER
                + ", pierces " + MAX_PIERCE + " monsters, range " + (int) RANGE + "."));
        tooltip.add(Component.literal("§7Unlimited ammo. Only hurts monsters."));
    }
}
