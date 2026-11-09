package com.photronix.towerdefense.test;

import com.photronix.towerdefense.CoreBlockEntity;
import com.photronix.towerdefense.ModRegistry;
import com.photronix.towerdefense.TowerDefenseMod;
import net.minecraft.core.BlockPos;
import net.minecraft.gametest.framework.GameTest;
import net.minecraft.gametest.framework.GameTestHelper;
import net.minecraft.world.Difficulty;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.gametest.GameTestHolder;
import net.minecraftforge.gametest.PrefixGameTestTemplate;

/** Headless smoke test: run with ./gradlew runGameTestServer. Not part of the shipped jar. */
@GameTestHolder(TowerDefenseMod.MOD_ID)
@PrefixGameTestTemplate(false)
public class TowerDefenseTests {

    @GameTest(template = "empty", timeoutTicks = 20 * 60 * 3)
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
        helper.setBlock(new BlockPos(0, 2, 0), ModRegistry.ARROW_TOWER.get());
        helper.setBlock(new BlockPos(2, 2, 0), ModRegistry.ARROW_TOWER.get());
        helper.setBlock(new BlockPos(0, 2, 2), ModRegistry.CANNON_TOWER.get());
        helper.setBlock(new BlockPos(2, 2, 2), ModRegistry.FROST_TOWER.get());
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
