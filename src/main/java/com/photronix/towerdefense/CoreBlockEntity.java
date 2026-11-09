package com.photronix.towerdefense;

import net.minecraft.core.BlockPos;
import net.minecraft.core.GlobalPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.level.Level;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerBossEvent;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.RandomSource;
import net.minecraft.world.BossEvent;
import net.minecraft.world.Difficulty;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.items.ItemHandlerHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Runs one game of tower defense around the Defense Core.
 *
 * Flow: IDLE -> COUNTDOWN (prep time) -> WAVE (mobs spawn on a ring and walk to the core)
 * -> COUNTDOWN ... -> WON after the last wave, or LOST when the core runs out of health.
 */
public class CoreBlockEntity extends BlockEntity {
    public static final String ENEMY_TAG = "td_enemy";

    // ---- Tuning ----
    public static final int MAX_WAVES = 12;
    public static final int CORE_MAX_HP = 20;
    private static final int FIRST_PREP_TICKS = 20 * 30;
    private static final int PREP_TICKS = 20 * 20;
    private static final int WAVE_TIMEOUT_TICKS = 20 * 300; // stragglers after this long count as leaks
    private static final int SPAWN_INTERVAL_TICKS = 15;
    private static final int SPAWN_RADIUS = 22;
    private static final int ARENA_RADIUS = 48;
    private static final int STARTER_COINS = 15;
    private static final double LEAK_DISTANCE = 3.2;

    /** Natural monster spawns are blocked this far (blocks, horizontal) around any Core. */
    public static final int PEACE_RADIUS = ARENA_RADIUS + 32;

    /** Every loaded Core, so spawn checks are cheap. Cleared when the server stops. */
    private static final Set<GlobalPos> LOADED_CORES = ConcurrentHashMap.newKeySet();

    public static void forgetAllCores() {
        LOADED_CORES.clear();
    }

    /** True if (x, z) in that dimension is inside a Core's no-natural-monsters zone. */
    public static boolean isNearCore(ResourceKey<Level> dimension, double x, double z) {
        for (GlobalPos core : LOADED_CORES) {
            if (core.dimension().equals(dimension)) {
                double dx = x - core.pos().getX();
                double dz = z - core.pos().getZ();
                if (dx * dx + dz * dz <= (double) PEACE_RADIUS * PEACE_RADIUS) {
                    return true;
                }
            }
        }
        return false;
    }

    /** Removes monsters near a Core that aren't wave enemies (leftover natural spawns, e.g. slimes in Superflat). */
    public static void clearStrayMonsters(ServerLevel level, BlockPos center) {
        for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(center).inflate(PEACE_RADIUS, 64, PEACE_RADIUS),
                m -> m.getType().getCategory() == MobCategory.MONSTER && !m.getTags().contains(ENEMY_TAG)
                        && !m.hasCustomName() && !m.isPersistenceRequired())) {
            mob.discard();
        }
    }

    private enum Phase { IDLE, COUNTDOWN, WAVE, WON, LOST }

    private Phase phase = Phase.IDLE;
    private int wave = 0;
    private int hp = CORE_MAX_HP;
    private int toSpawn = 0;
    private int waveTotal = 0;
    private int spawnTimer = 0;
    private int countdown = 0;
    private int waveTicks = 0;

    private boolean nightLocked = false;
    private long savedDayTime = 0;

    private int tickCount = 0; // not saved
    private final ServerBossEvent bar = new ServerBossEvent(Component.literal("Core Siege"),
            BossEvent.BossBarColor.YELLOW, BossEvent.BossBarOverlay.PROGRESS);

    public CoreBlockEntity(BlockPos pos, BlockState state) {
        super(ModRegistry.CORE_BE.get(), pos, state);
    }

    /** Compact state string, used by the headless smoke test. */
    public String debugState() {
        return phase + " wave=" + wave + " hp=" + hp + " toSpawn=" + toSpawn;
    }

    // ------------------------------------------------------------------ interaction

    public void onUse(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        switch (phase) {
            case IDLE, WON, LOST -> {
                if (level.getDifficulty() == Difficulty.PEACEFUL) {
                    player.displayClientMessage(Component.literal(
                            "§cMonsters can't spawn on Peaceful. Set the difficulty to Easy or higher (/difficulty easy)."), false);
                    return;
                }
                startGame(level);
            }
            case COUNTDOWN -> {
                if (player.isShiftKeyDown()) {
                    skipCountdown(level);
                } else {
                    player.displayClientMessage(Component.literal("§eWave " + (wave + 1) + " in "
                            + (countdown / 20 + 1) + "s. Sneak + right-click the Core to start it now."), true);
                }
            }
            case WAVE -> player.displayClientMessage(Component.literal("§cWave " + wave + "/" + MAX_WAVES
                    + " - Core health " + hp + "/" + CORE_MAX_HP), true);
        }
    }

    /** Starts a new game (also used by the headless smoke test). */
    public void startGame(ServerLevel level) {
        wave = 0;
        hp = CORE_MAX_HP;
        toSpawn = 0;
        phase = Phase.COUNTDOWN;
        countdown = FIRST_PREP_TICKS;
        discardEnemies(level);
        clearStrayMonsters(level, worldPosition);
        beginNight(level);
        giveCoins(level, STARTER_COINS);
        for (ServerPlayer p : nearbyPlayers(level)) {
            TowerUnlocks.award(p, 1, false);
        }
        broadcast(level, "§6§lCore Siege begins!§r §7Defend the Core for " + MAX_WAVES
                + " waves. Craft towers from coins and place them along the enemy path. First wave in "
                + FIRST_PREP_TICKS / 20 + "s.");
        level.playSound(null, worldPosition, SoundEvents.BEACON_ACTIVATE, SoundSource.BLOCKS, 1F, 1F);
        refreshBar(level);
        setChanged();
    }

    // ------------------------------------------------------------------ ticking

    /** Skips the prep countdown and starts the next wave immediately. */
    public void skipCountdown(ServerLevel level) {
        if (phase == Phase.COUNTDOWN) {
            broadcast(level, "§eStarting wave " + (wave + 1) + " early!");
            startWave(level);
        }
    }

    public void serverTick(ServerLevel level) {
        if (phase == Phase.IDLE) {
            if (!bar.getPlayers().isEmpty()) {
                bar.removeAllPlayers();
            }
            if (nightLocked) {
                endNight(level);
            }
            return;
        }
        tickCount++;
        if (tickCount % 20 == 0) {
            refreshBar(level);
            keepNight(level);
        }

        switch (phase) {
            case COUNTDOWN -> {
                if (--countdown <= 0) {
                    startWave(level);
                } else if (countdown == 20 * 10 || countdown == 20 * 5 || countdown == 20 * 3) {
                    broadcast(level, "§eWave " + (wave + 1) + " in " + countdown / 20 + "s...");
                }
            }
            case WAVE -> tickWave(level);
            case WON, LOST -> {
                if (--countdown <= 0) {
                    phase = Phase.IDLE;
                    bar.removeAllPlayers();
                    endNight(level);
                    setChanged();
                }
            }
            default -> { }
        }
    }

    private void tickWave(ServerLevel level) {
        waveTicks++;

        if (toSpawn > 0 && --spawnTimer <= 0) {
            spawnOne(level);
            toSpawn--;
            spawnTimer = SPAWN_INTERVAL_TICKS;
            setChanged();
        }

        if (tickCount % 5 == 0) {
            List<Mob> enemies = enemies(level);
            for (Mob mob : enemies) {
                if (hasReachedCore(mob)) {
                    leak(level, mob);
                    if (phase != Phase.WAVE) {
                        return;
                    }
                }
            }
            if (tickCount % 20 == 0) {
                for (Mob mob : enemies) {
                    if (mob.isAlive() && mob.getTarget() == null && mob.getNavigation().isDone()) {
                        walkToCore(mob);
                    }
                }
            }
            if (toSpawn == 0 && enemies(level).isEmpty()) {
                waveCleared(level);
                return;
            }
        }

        if (waveTicks > WAVE_TIMEOUT_TICKS && toSpawn == 0) {
            broadcast(level, "§cThe remaining enemies slip through!");
            for (Mob mob : enemies(level)) {
                leak(level, mob);
                if (phase != Phase.WAVE) {
                    return;
                }
            }
        }
    }

    private void startWave(ServerLevel level) {
        wave++;
        toSpawn = 4 + wave * 2 + bossCount(wave);
        waveTotal = toSpawn;
        spawnTimer = 0;
        waveTicks = 0;
        phase = Phase.WAVE;
        broadcast(level, bossCount(wave) > 0
                ? "§4§lWave " + wave + "/" + MAX_WAVES + " - BOSS WAVE!"
                : "§c§lWave " + wave + "/" + MAX_WAVES + " incoming!");
        level.playSound(null, worldPosition, SoundEvents.ENDER_DRAGON_GROWL, SoundSource.HOSTILE, 1F, 1F);
        refreshBar(level);
        setChanged();
    }

    private void waveCleared(ServerLevel level) {
        int bonus = 5 + wave * 2;
        giveCoins(level, bonus);
        for (ServerPlayer p : nearbyPlayers(level)) {
            TowerUnlocks.award(p, wave + 1, true); // unlock next wave's towers during prep time
        }
        if (wave >= MAX_WAVES) {
            giveCoins(level, 50);
            phase = Phase.WON;
            countdown = 20 * 10;
            broadcast(level, "§a§lVICTORY!§r §7You defended the Core through all " + MAX_WAVES
                    + " waves! (+" + (bonus + 50) + " coins). Right-click the Core to play again.");
            level.playSound(null, worldPosition, SoundEvents.PLAYER_LEVELUP, SoundSource.BLOCKS, 1F, 1F);
        } else {
            phase = Phase.COUNTDOWN;
            countdown = PREP_TICKS;
            broadcast(level, "§aWave " + wave + " cleared! +" + bonus + " coins. §7Next wave in "
                    + PREP_TICKS / 20 + "s (sneak + right-click the Core to start now).");
            level.playSound(null, worldPosition, SoundEvents.UI_TOAST_CHALLENGE_COMPLETE, SoundSource.BLOCKS, 1F, 1F);
        }
        refreshBar(level);
        setChanged();
    }

    private void lose(ServerLevel level) {
        phase = Phase.LOST;
        countdown = 20 * 10;
        discardEnemies(level);
        broadcast(level, "§4§lDEFEAT!§r §7The Core fell on wave " + wave + ". Right-click it to try again.");
        level.playSound(null, worldPosition, SoundEvents.WITHER_DEATH, SoundSource.HOSTILE, 1F, 1F);
        refreshBar(level);
        setChanged();
    }

    // ------------------------------------------------------------------ night mode

    /** Remembers the current time and switches to midnight for the duration of the game. */
    private void beginNight(ServerLevel level) {
        if (!TdConfig.nightMode()) {
            return;
        }
        if (!nightLocked) {
            savedDayTime = level.getDayTime();
            nightLocked = true;
        }
        keepNight(level);
    }

    /** Called once a second during a game: hold the clock at midnight and refresh Night Vision. */
    private void keepNight(ServerLevel level) {
        if (!nightLocked) {
            return;
        }
        level.setDayTime(savedDayTime / 24000L * 24000L + 18000L);
        for (ServerPlayer p : nearbyPlayers(level)) {
            p.addEffect(new MobEffectInstance(MobEffects.NIGHT_VISION, 20 * 30, 0, false, false, false));
        }
    }

    /** Puts the clock back and removes Night Vision. */
    private void endNight(ServerLevel level) {
        if (!nightLocked) {
            return;
        }
        nightLocked = false;
        level.setDayTime(savedDayTime);
        for (ServerPlayer p : nearbyPlayers(level)) {
            p.removeEffect(MobEffects.NIGHT_VISION);
        }
        setChanged();
    }

    // ------------------------------------------------------------------ enemies

    private static int bossCount(int wave) {
        return wave == 6 ? 1 : wave == MAX_WAVES ? 2 : 0;
    }

    private EntityType<?> pickType(RandomSource random) {
        List<EntityType<?>> pool = new ArrayList<>();
        pool.add(EntityType.ZOMBIE);
        if (wave >= 3) pool.add(EntityType.HUSK);
        if (wave >= 4) pool.add(EntityType.SPIDER);
        if (wave >= 7) pool.add(EntityType.VINDICATOR);
        return pool.get(random.nextInt(pool.size()));
    }

    private void spawnOne(ServerLevel level) {
        RandomSource random = level.random;
        EntityType<?> type = toSpawn <= bossCount(wave) ? EntityType.RAVAGER : pickType(random);
        // try the normal ring first; on rough terrain fall back to closer rings so waves never silently vanish
        int[] radii = {SPAWN_RADIUS, SPAWN_RADIUS, 14, 8};
        for (int attempt = 0; attempt < 24; attempt++) {
            int radius = radii[attempt / 6];
            double angle = random.nextDouble() * Math.PI * 2;
            int x = worldPosition.getX() + (int) Math.round(Math.cos(angle) * radius);
            int z = worldPosition.getZ() + (int) Math.round(Math.sin(angle) * radius);
            if (!level.hasChunkAt(new BlockPos(x, worldPosition.getY(), z))) {
                continue;
            }
            BlockPos pos = findGround(level, x, z);
            if (pos == null) {
                continue;
            }
            Entity entity = type.spawn(level, pos, MobSpawnType.EVENT);
            if (entity instanceof Mob mob) {
                prepare(mob);
                return;
            }
        }
    }

    /**
     * Finds standing room (solid floor, two free blocks above, no fluid) in this column close to the
     * core's own height, so spawns happen at the level the player built on - not on a cave roof or hilltop.
     */
    private BlockPos findGround(ServerLevel level, int x, int z) {
        int top = worldPosition.getY() + 6;
        int bottom = worldPosition.getY() - 8;
        for (int y = top; y >= bottom; y--) {
            BlockPos feet = new BlockPos(x, y, z);
            BlockPos floor = feet.below();
            if (level.getBlockState(floor).getCollisionShape(level, floor).isEmpty()
                    || !level.getFluidState(floor).isEmpty()) {
                continue;
            }
            if (level.getBlockState(feet).getCollisionShape(level, feet).isEmpty()
                    && level.getBlockState(feet.above()).getCollisionShape(level, feet.above()).isEmpty()
                    && level.getFluidState(feet).isEmpty()) {
                return feet;
            }
        }
        return null;
    }

    private void prepare(Mob mob) {
        mob.addTag(ENEMY_TAG);
        mob.setPersistenceRequired();

        AttributeInstance health = mob.getAttribute(Attributes.MAX_HEALTH);
        if (health != null) {
            health.setBaseValue(health.getBaseValue() * (1.0 + 0.08 * (wave - 1)));
            mob.setHealth(mob.getMaxHealth());
        }
        AttributeInstance follow = mob.getAttribute(Attributes.FOLLOW_RANGE);
        if (follow != null) {
            follow.setBaseValue(64);
        }
        mob.addEffect(new MobEffectInstance(MobEffects.FIRE_RESISTANCE, 20 * 60 * 30, 0, false, false));
        mob.addEffect(new MobEffectInstance(MobEffects.GLOWING, 20 * 60 * 30, 0, false, false));
        walkToCore(mob);
    }

    private void walkToCore(Mob mob) {
        mob.getNavigation().moveTo(worldPosition.getX() + 0.5, worldPosition.getY(), worldPosition.getZ() + 0.5, 1.0);
    }

    private boolean hasReachedCore(Mob mob) {
        double dx = mob.getX() - (worldPosition.getX() + 0.5);
        double dz = mob.getZ() - (worldPosition.getZ() + 0.5);
        return dx * dx + dz * dz < LEAK_DISTANCE * LEAK_DISTANCE && Math.abs(mob.getY() - worldPosition.getY()) < 4;
    }

    private void leak(ServerLevel level, Mob mob) {
        int damage = mob.getType() == EntityType.RAVAGER ? 5 : mob.getType() == EntityType.VINDICATOR ? 2 : 1;
        hp = Math.max(0, hp - damage);
        level.sendParticles(ParticleTypes.EXPLOSION, mob.getX(), mob.getY() + 1, mob.getZ(), 1, 0, 0, 0, 0);
        level.playSound(null, worldPosition, SoundEvents.GENERIC_HURT, SoundSource.BLOCKS, 1F, 0.6F);
        mob.discard();
        broadcast(level, "§c" + "An enemy hit the Core! Health " + hp + "/" + CORE_MAX_HP);
        setChanged();
        if (hp <= 0) {
            lose(level);
        }
    }

    private List<Mob> enemies(ServerLevel level) {
        return level.getEntitiesOfClass(Mob.class, new AABB(worldPosition).inflate(ARENA_RADIUS * 2),
                m -> m.isAlive() && m.getTags().contains(ENEMY_TAG));
    }

    private void discardEnemies(ServerLevel level) {
        for (Mob mob : level.getEntitiesOfClass(Mob.class, new AABB(worldPosition).inflate(ARENA_RADIUS * 2),
                m -> m.getTags().contains(ENEMY_TAG))) {
            mob.discard();
        }
    }

    /** Called when the core block is broken. */
    public void cleanup(ServerLevel level) {
        discardEnemies(level);
        endNight(level);
        bar.removeAllPlayers();
    }

    // ------------------------------------------------------------------ players / messages

    private List<ServerPlayer> nearbyPlayers(ServerLevel level) {
        List<ServerPlayer> result = new ArrayList<>();
        double maxSq = (ARENA_RADIUS + 16.0) * (ARENA_RADIUS + 16.0);
        for (ServerPlayer p : level.players()) {
            if (p.distanceToSqr(worldPosition.getX() + 0.5, worldPosition.getY(), worldPosition.getZ() + 0.5) <= maxSq) {
                result.add(p);
            }
        }
        return result;
    }

    private void broadcast(ServerLevel level, String message) {
        for (ServerPlayer p : nearbyPlayers(level)) {
            p.sendSystemMessage(Component.literal(message));
        }
    }

    private void giveCoins(ServerLevel level, int amount) {
        for (ServerPlayer p : nearbyPlayers(level)) {
            ItemHandlerHelper.giveItemToPlayer(p, new ItemStack(ModRegistry.COIN.get(), amount));
        }
    }

    private void refreshBar(ServerLevel level) {
        List<ServerPlayer> near = nearbyPlayers(level);
        for (ServerPlayer p : new ArrayList<>(bar.getPlayers())) {
            if (!near.contains(p)) {
                bar.removePlayer(p);
            }
        }
        for (ServerPlayer p : near) {
            bar.addPlayer(p);
        }

        String health = "Core " + hp + "/" + CORE_MAX_HP;
        switch (phase) {
            case COUNTDOWN -> {
                bar.setColor(BossEvent.BossBarColor.GREEN);
                bar.setName(Component.literal("Wave " + (wave + 1) + "/" + MAX_WAVES + " starts in "
                        + (countdown / 20 + 1) + "s  |  " + health));
                bar.setProgress(Math.max(0F, Math.min(1F, countdown / (float) (wave == 0 ? FIRST_PREP_TICKS : PREP_TICKS))));
            }
            case WAVE -> {
                int remaining = toSpawn + enemies(level).size();
                bar.setColor(BossEvent.BossBarColor.RED);
                bar.setName(Component.literal("Wave " + wave + "/" + MAX_WAVES + "  |  Enemies left: "
                        + remaining + "  |  " + health));
                bar.setProgress(waveTotal <= 0 ? 0F : Math.max(0F, Math.min(1F, remaining / (float) waveTotal)));
            }
            case WON -> {
                bar.setColor(BossEvent.BossBarColor.PURPLE);
                bar.setName(Component.literal("VICTORY!"));
                bar.setProgress(1F);
            }
            case LOST -> {
                bar.setColor(BossEvent.BossBarColor.RED);
                bar.setName(Component.literal("DEFEAT - the Core has fallen"));
                bar.setProgress(0F);
            }
            default -> { }
        }
    }

    // ------------------------------------------------------------------ persistence

    @Override
    protected void saveAdditional(CompoundTag tag) {
        super.saveAdditional(tag);
        tag.putString("Phase", phase.name());
        tag.putInt("Wave", wave);
        tag.putInt("Hp", hp);
        tag.putInt("ToSpawn", toSpawn);
        tag.putInt("WaveTotal", waveTotal);
        tag.putInt("Countdown", countdown);
        tag.putInt("WaveTicks", waveTicks);
        tag.putBoolean("NightLocked", nightLocked);
        tag.putLong("SavedDayTime", savedDayTime);
    }

    @Override
    public void load(CompoundTag tag) {
        super.load(tag);
        try {
            phase = Phase.valueOf(tag.getString("Phase"));
        } catch (IllegalArgumentException e) {
            phase = Phase.IDLE;
        }
        wave = tag.getInt("Wave");
        hp = tag.contains("Hp") ? tag.getInt("Hp") : CORE_MAX_HP;
        toSpawn = tag.getInt("ToSpawn");
        waveTotal = tag.getInt("WaveTotal");
        countdown = tag.getInt("Countdown");
        waveTicks = tag.getInt("WaveTicks");
        nightLocked = tag.getBoolean("NightLocked");
        savedDayTime = tag.getLong("SavedDayTime");
    }

    @Override
    public void onLoad() {
        super.onLoad();
        if (level instanceof ServerLevel serverLevel) {
            LOADED_CORES.add(GlobalPos.of(serverLevel.dimension(), worldPosition));
        }
    }

    @Override
    public void setRemoved() {
        if (level != null) {
            LOADED_CORES.remove(GlobalPos.of(level.dimension(), worldPosition));
        }
        bar.removeAllPlayers();
        super.setRemoved();
    }
}
