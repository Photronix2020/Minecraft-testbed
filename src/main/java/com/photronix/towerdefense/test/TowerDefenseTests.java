package com.photronix.towerdefense.test;

import com.photronix.towerdefense.CoreBlockEntity;
import com.photronix.towerdefense.ModRegistry;
import com.photronix.towerdefense.TowerDefenseMod;
import com.photronix.towerdefense.TowerType;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import java.util.ArrayList;
import java.util.List;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Headless smoke test: run with ./gradlew runGameTestServer. Not part of the shipped jar. */
@GameTestHolder(TowerDefenseMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class TowerDefenseTests {

    @GameTest(template = "empty", timeoutTicks = 100, batch = "peace")
    public static void peaceAroundCore(GameTestHelper helper) {
        helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
        BlockPos corePos = new BlockPos(1, 2, 1);
        BlockPos abs = helper.absolutePos(corePos);

        // a leftover slime (spawned like a command would) must be cleaned up when the core is placed
        Mob slime = (Mob) EntityType.SLIME.spawn(helper.getLevel(), abs.offset(8, 0, 0), MobSpawnType.COMMAND);
        helper.assertTrue(slime != null, "test slime should spawn before the core exists");

        helper.setBlock(corePos, ModRegistry.DEFENSE_CORE.get());
        helper.runAfterDelay(5, () -> {
            helper.assertTrue(slime.isRemoved(), "stray slime near a new Core should be cleared");
            // natural spawns near the core are blocked
            Mob natural = (Mob) EntityType.ZOMBIE.spawn(helper.getLevel(), abs.offset(10, 0, 0), MobSpawnType.NATURAL);
            helper.assertTrue(natural == null || natural.isRemoved() || !natural.isAddedToWorld(), "natural zombie near the Core must be blocked");
            // wave-style spawns are not blocked
            Mob wave = (Mob) EntityType.ZOMBIE.spawn(helper.getLevel(), abs.offset(10, 0, 0), MobSpawnType.EVENT);
            helper.assertTrue(wave != null && wave.isAddedToWorld(), "EVENT spawns (waves) must still work");
            wave.discard();
            System.out.println("[TDTEST] PEACE PASS");
            helper.succeed();
        });
    }

    /** The Siege Blaster hits a monster in the crosshairs, ignores a cow in front of it, and is stopped by walls. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "blaster")
    public static void blasterShoots(GameTestHelper helper) {
        helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
        BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
        for (int dx = -2; dx <= 30; dx++) {
            for (int dz = -4; dz <= 4; dz++) {
                helper.getLevel().setBlock(origin.offset(dx, -1, dz), Blocks.STONE.defaultBlockState(), 2);
                for (int dy = 0; dy <= 4; dy++) {
                    helper.getLevel().setBlock(origin.offset(dx, dy, dz), Blocks.AIR.defaultBlockState(), 2);
                }
            }
        }
        net.minecraft.world.entity.LivingEntity shooter = (net.minecraft.world.entity.LivingEntity)
                EntityType.VILLAGER.spawn(helper.getLevel(), origin, MobSpawnType.COMMAND);
        ((Mob) shooter).setNoAi(true);
        net.minecraft.world.entity.LivingEntity cow = (net.minecraft.world.entity.LivingEntity)
                EntityType.COW.spawn(helper.getLevel(), origin.offset(5, 0, 0), MobSpawnType.COMMAND);
        ((Mob) cow).setNoAi(true);
        Mob zombie = (Mob) EntityType.ZOMBIE.spawn(helper.getLevel(), origin.offset(10, 0, 0), MobSpawnType.COMMAND);
        zombie.setNoAi(true);
        zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(100);
        zombie.setHealth(100);

        net.minecraft.world.phys.Vec3 eye = net.minecraft.world.phys.Vec3.atCenterOf(origin).add(0, 0.6, 0);
        net.minecraft.world.phys.Vec3 look = new net.minecraft.world.phys.Vec3(1, 0, 0);
        var hit = com.photronix.towerdefense.BlasterItem.fire(helper.getLevel(), shooter, eye, look);
        helper.assertTrue(hit == zombie, "beam should hit the zombie, not " + hit);
        helper.assertTrue(zombie.getHealth() <= 100 - com.photronix.towerdefense.BlasterItem.DAMAGE * 0.8F, "zombie should lose health: " + zombie.getHealth());
        helper.assertTrue(cow.getHealth() == cow.getMaxHealth(), "cow in the line of fire must not be hurt");

        // a wall between shooter and zombie blocks the shot
        for (int dy = 0; dy <= 3; dy++) {
            helper.getLevel().setBlock(origin.offset(8, dy, 0), Blocks.STONE.defaultBlockState(), 2);
            helper.getLevel().setBlock(origin.offset(8, dy, 1), Blocks.STONE.defaultBlockState(), 2);
            helper.getLevel().setBlock(origin.offset(8, dy, -1), Blocks.STONE.defaultBlockState(), 2);
        }
        float before = zombie.getHealth();
        var blocked = com.photronix.towerdefense.BlasterItem.fire(helper.getLevel(), shooter, eye, look);
        helper.assertTrue(blocked == null && zombie.getHealth() == before, "wall should stop the beam");
        System.out.println("[TDTEST] BLASTER PASS");
        helper.succeed();
    }

    /** Every tower recipe (and the Core recipe) must be loaded, so the recipe-book unlocks can find them. */
    @GameTest(template = "empty", timeoutTicks = 20, batch = "recipes")
    public static void recipesAreLoaded(GameTestHelper helper) {
        var manager = helper.getLevel().getServer().getRecipeManager();
        System.out.println("[TDTEST] total recipes loaded: " + manager.getRecipes().size());
        for (TowerType type : TowerType.values()) {
            var id = new net.minecraft.resources.ResourceLocation(TowerDefenseMod.MOD_ID, type.id());
            var recipe = manager.byKey(id);
            helper.assertTrue(recipe.isPresent(), "missing recipe " + id);
            int ingredients = recipe.get().getIngredients().size();
            System.out.println("[TDTEST] recipe " + id + " ingredients=" + ingredients + " (cost " + type.cost + ")");
            helper.assertTrue(ingredients == type.cost, id + " recipe has " + ingredients + " coins but TowerType says " + type.cost);
        }
        helper.assertTrue(manager.byKey(new net.minecraft.resources.ResourceLocation(TowerDefenseMod.MOD_ID, "defense_core")).isPresent(),
                "missing defense_core recipe");
        helper.succeed();
    }

    /** Every tower type must be able to damage an enemy placed 3 blocks away. */
    @GameTest(template = "empty", timeoutTicks = 200, batch = "towers")
    public static void everyTowerFires(GameTestHelper helper) {
        helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
        BlockPos origin = helper.absolutePos(new BlockPos(1, 2, 1));
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int dx = -3; dx <= 60; dx++) {
            for (int dz = -8; dz <= 8; dz++) {
                helper.getLevel().setBlock(origin.offset(dx, -1, dz), stone, 2);
                for (int dy = 0; dy <= 4; dy++) {
                    helper.getLevel().setBlock(origin.offset(dx, dy, dz), air, 2);
                }
            }
        }
        List<Mob> targets = new ArrayList<>();
        int i = 0;
        for (TowerType type : TowerType.values()) {
            BlockPos towerPos = origin.offset(i * 8, 0, 0);
            helper.getLevel().setBlock(towerPos, ModRegistry.TOWER_BLOCKS.get(type).get().defaultBlockState(), 3);
            Mob zombie = (Mob) EntityType.ZOMBIE.spawn(helper.getLevel(), towerPos.offset(3, 0, 0), MobSpawnType.COMMAND);
            zombie.addTag(CoreBlockEntity.ENEMY_TAG);
            zombie.setNoAi(true);
            zombie.getAttribute(Attributes.MAX_HEALTH).setBaseValue(1000);
            zombie.setHealth(1000);
            targets.add(zombie);
            i++;
        }
        helper.runAfterDelay(80, () -> {
            int idx = 0;
            for (TowerType type : TowerType.values()) {
                Mob z = targets.get(idx++);
                System.out.println("[TDTEST] " + type + " target health " + z.getHealth());
                helper.assertTrue(z.getHealth() < 1000, type + " did not damage its target (health " + z.getHealth() + ")");
            }
            helper.succeed();
        });
    }

    /** Builds the full battleground map, checks its parts, then plays wave 1 inside it. */
    @GameTest(template = "empty", timeoutTicks = 20 * 60 * 3, batch = "arenamap")
    public static void battlegroundPlaysWave(GameTestHelper helper) {
        helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
        BlockPos center = helper.absolutePos(new BlockPos(1, 2, 1));
        long start = System.currentTimeMillis();
        com.photronix.towerdefense.ArenaBuilder.build(helper.getLevel(), center, false);
        System.out.println("[TDTEST] arena built in " + (System.currentTimeMillis() - start) + " ms");

        helper.assertTrue(helper.getLevel().getBlockState(center).is(ModRegistry.DEFENSE_CORE.get()), "Core must be in the middle");
        helper.assertTrue(helper.getLevel().getBlockState(center.offset(0, -1, 10)).is(Blocks.DIRT_PATH), "road to the gate");
        int r = com.photronix.towerdefense.ArenaBuilder.RADIUS;
        helper.assertTrue(helper.getLevel().getBlockState(center.offset(25, 3, 25)).is(Blocks.STONE_BRICKS)
                || helper.getLevel().getBlockState(center.offset(25, 3, 25)).is(Blocks.MOSSY_STONE_BRICKS), "border wall");
        helper.assertTrue(helper.getLevel().getBlockState(center.offset(9, 3, 2)).is(Blocks.LANTERN), "lamp post along the road");
        helper.assertTrue(helper.getLevel().getBlockState(center.offset(4, 3, 4)).is(Blocks.LANTERN), "lantern on the spawn pad pillar");
        int buildingBlocks = 0;
        for (int dx = 5; dx <= 11; dx++) {
            for (int dz = 5; dz <= 11; dz++) {
                for (int dy = 0; dy <= 6; dy++) {
                    BlockState st = helper.getLevel().getBlockState(center.offset(dx, dy, dz));
                    if (st.is(Blocks.OAK_PLANKS) || st.is(Blocks.SPRUCE_PLANKS) || st.is(Blocks.OAK_LOG)) buildingBlocks++;
                }
            }
        }
        System.out.println("[TDTEST] house blocks found: " + buildingBlocks);
        helper.assertTrue(buildingBlocks > 40, "a house should stand in the south-east quadrant");

        for (int[] o : new int[][]{{2, 0}, {-2, 0}, {0, 2}, {0, -2}}) {
            helper.getLevel().setBlock(center.offset(o[0], 0, o[1]),
                    ModRegistry.TOWER_BLOCKS.get(TowerType.ARROW).get().defaultBlockState(), 3);
        }
        CoreBlockEntity core = (CoreBlockEntity) helper.getLevel().getBlockEntity(center);
        helper.getLevel().setDayTime(1000); // morning before the game
        core.startGame(helper.getLevel());
        core.skipCountdown(helper.getLevel());
        long nightTime = helper.getLevel().getDayTime() % 24000;
        System.out.println("[TDTEST] time of day after start: " + nightTime);
        helper.assertTrue(nightTime == 18000, "game should switch to midnight, was " + nightTime);

        int[] maxEnemies = {0};
        helper.onEachTick(() -> {
            int enemies = helper.getLevel().getEntitiesOfClass(Mob.class,
                    new net.minecraft.world.phys.AABB(center).inflate(60),
                    m -> m.getTags().contains(CoreBlockEntity.ENEMY_TAG)).size();
            maxEnemies[0] = Math.max(maxEnemies[0], enemies);
        });
        helper.succeedWhen(() -> {
            helper.assertTrue(maxEnemies[0] >= 1, "enemies must spawn inside the arena: " + core.debugState());
            helper.assertTrue(core.debugState().startsWith("COUNTDOWN wave=1"), "wave 1 not cleared yet: " + core.debugState());
            System.out.println("[TDTEST] ARENA PASS maxEnemies=" + maxEnemies[0] + " " + core.debugState());
        });
    }

    @GameTest(template = "empty", timeoutTicks = 20 * 60 * 3, batch = "arena")
    public static void fullFirstWave(GameTestHelper helper) {
        helper.getLevel().getServer().setDifficulty(Difficulty.NORMAL, true);
        helper.getLevel().getServer().getWorldData().setDifficulty(Difficulty.NORMAL);

        BlockPos corePos = new BlockPos(1, 2, 1);
        BlockPos abs = helper.absolutePos(corePos);

        // carve a flat, open arena (stone floor, air above) like a player placing the core on open ground
        BlockState stone = Blocks.STONE.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int dx = -34; dx <= 34; dx++) {
            for (int dz = -34; dz <= 34; dz++) {
                helper.getLevel().setBlock(abs.offset(dx, -1, dz), stone, 2);
                for (int dy = 0; dy <= 10; dy++) {
                    BlockPos p = abs.offset(dx, dy, dz);
                    if (!helper.getLevel().getBlockState(p).isAir()) {
                        helper.getLevel().setBlock(p, air, 2);
                    }
                }
            }
        }

        helper.setBlock(corePos, ModRegistry.DEFENSE_CORE.get());
        // a few towers right next to the core
        helper.setBlock(new BlockPos(0, 2, 0), ModRegistry.TOWER_BLOCKS.get(TowerType.ARROW).get());
        helper.setBlock(new BlockPos(2, 2, 0), ModRegistry.TOWER_BLOCKS.get(TowerType.ARROW).get());
        helper.setBlock(new BlockPos(0, 2, 2), ModRegistry.TOWER_BLOCKS.get(TowerType.CANNON).get());
        helper.setBlock(new BlockPos(2, 2, 2), ModRegistry.TOWER_BLOCKS.get(TowerType.FROST).get());
        CoreBlockEntity core = (CoreBlockEntity) helper.getBlockEntity(corePos);
        core.startGame(helper.getLevel());
        core.skipCountdown(helper.getLevel());
        System.out.println("[TDTEST] started: " + core.debugState());

        int[] maxEnemies = {0};
        helper.onEachTick(() -> {
            int enemies = helper.getLevel().getEntitiesOfClass(Mob.class,
                    new net.minecraft.world.phys.AABB(helper.absolutePos(corePos)).inflate(60),
                    m -> m.getTags().contains(CoreBlockEntity.ENEMY_TAG)).size();
            maxEnemies[0] = Math.max(maxEnemies[0], enemies);
            if (helper.getTick() % 200 == 0) {
                for (Mob m : helper.getLevel().getEntitiesOfClass(Mob.class,
                        new net.minecraft.world.phys.AABB(helper.absolutePos(corePos)).inflate(60),
                        m -> m.getTags().contains(CoreBlockEntity.ENEMY_TAG))) {
                    System.out.println("[TDTEST]   enemy " + m.getType() + " pos=" + m.blockPosition() + " core=" + helper.absolutePos(corePos)
                            + " navDone=" + m.getNavigation().isDone() + " target=" + m.getTarget() + " hp=" + m.getHealth()
                            + " onGround=" + m.onGround() + " path=" + m.getNavigation().getPath());
                }
            }
            if (helper.getTick() % 40 == 0) {
                System.out.println("[TDTEST] tick " + helper.getTick() + " enemies=" + enemies + " " + core.debugState());
            }
        });

        // success: enemies really appeared, and wave 1 was then cleared (back in COUNTDOWN for wave 2), core alive
        helper.succeedWhen(() -> {
            if (maxEnemies[0] < 1) {
                helper.fail("no enemies have spawned yet: " + core.debugState());
            }
            if (!core.debugState().startsWith("COUNTDOWN wave=1")) {
                helper.fail("waiting for wave 1 to be cleared: " + core.debugState());
            }
            System.out.println("[TDTEST] PASS maxEnemies=" + maxEnemies[0] + " " + core.debugState() + " at tick " + helper.getTick());
        });
    }
}
