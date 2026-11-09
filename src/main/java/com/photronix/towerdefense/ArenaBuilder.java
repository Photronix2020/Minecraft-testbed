package com.photronix.towerdefense;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LadderBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.border.WorldBorder;

import java.util.Random;

/**
 * Builds the Core Siege battleground around a point: flat grass arena, stone wall border with gatehouses,
 * a spawn pad with the Defense Core in the middle, dirt roads, and a handful of buildings.
 *
 * Layout: enemies spawn on a ring 22 blocks from the Core, so that band is kept free of buildings.
 * Inner buildings sit around radius 11, outer buildings around radius 29, the wall is at radius ~36.
 */
public final class ArenaBuilder {
    private ArenaBuilder() {}

    public static final int RADIUS = 36;
    private static final int CLEAR_HEIGHT = 18;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private static void set(ServerLevel level, int x, int y, int z, BlockState state) {
        level.setBlock(new BlockPos(x, y, z), state, FLAGS);
    }

    /** @param center the block the player stands on top of the arena at; the Core is placed here. */
    public static void build(ServerLevel level, BlockPos center, boolean setWorldBorder) {
        int cx = center.getX();
        int cz = center.getZ();
        int floorY = center.getY() - 1;
        BlockState grass = Blocks.GRASS_BLOCK.defaultBlockState();
        BlockState dirt = Blocks.DIRT.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();

        // 1. flat ground, cleared space above
        int clearR = RADIUS + 2;
        for (int dx = -clearR; dx <= clearR; dx++) {
            for (int dz = -clearR; dz <= clearR; dz++) {
                if (dx * dx + dz * dz > clearR * clearR) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                set(level, x, floorY, z, grass);
                set(level, x, floorY - 1, z, dirt);
                set(level, x, floorY - 2, z, dirt);
                fillDownToSolid(level, x, floorY - 3, z);
                for (int dy = 0; dy < CLEAR_HEIGHT; dy++) {
                    BlockPos p = new BlockPos(x, floorY + 1 + dy, z);
                    if (!level.getBlockState(p).isAir()) {
                        level.setBlock(p, air, FLAGS);
                    }
                }
            }
        }

        // 2. dirt roads along the four axes
        for (int r = 6; r <= RADIUS - 2; r++) {
            for (int w = -1; w <= 1; w++) {
                set(level, cx + r, floorY, cz + w, Blocks.DIRT_PATH.defaultBlockState());
                set(level, cx - r, floorY, cz + w, Blocks.DIRT_PATH.defaultBlockState());
                set(level, cx + w, floorY, cz + r, Blocks.DIRT_PATH.defaultBlockState());
                set(level, cx + w, floorY, cz - r, Blocks.DIRT_PATH.defaultBlockState());
            }
        }

        // 2b. lamp posts along every road (arena games run at night) and on a ring around the middle
        for (int r : new int[]{9, 15, 21, 27}) {
            for (int side : new int[]{-2, 2}) {
                lampPost(level, cx + r, floorY, cz + side);
                lampPost(level, cx - r, floorY, cz + side);
                lampPost(level, cx + side, floorY, cz + r);
                lampPost(level, cx + side, floorY, cz - r);
            }
        }
        for (int k = 0; k < 12; k++) {
            double angle = Math.toRadians(15 + 30 * k);
            int dx = (int) Math.round(Math.cos(angle) * 17);
            int dz = (int) Math.round(Math.sin(angle) * 17);
            if (Math.abs(dx) > 3 && Math.abs(dz) > 3) {
                lampPost(level, cx + dx, floorY, cz + dz);
            }
        }

        // 3. spawn pad with pillars
        for (int dx = -6; dx <= 6; dx++) {
            for (int dz = -6; dz <= 6; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d <= 5.5) {
                    BlockState pad = d <= 1.6 ? Blocks.POLISHED_ANDESITE.defaultBlockState()
                            : (d > 4.5 ? Blocks.CHISELED_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
                    set(level, cx + dx, floorY, cz + dz, pad);
                }
            }
        }
        for (int sx : new int[]{-4, 4}) {
            for (int sz : new int[]{-4, 4}) {
                for (int dy = 1; dy <= 3; dy++) {
                    set(level, cx + sx, floorY + dy, cz + sz, Blocks.STONE_BRICKS.defaultBlockState());
                }
                set(level, cx + sx, floorY + 4, cz + sz, Blocks.LANTERN.defaultBlockState());
            }
        }

        // 4. border wall with four gatehouses (iron-bar gates)
        int wallHeight = 6;
        for (int dx = -RADIUS - 2; dx <= RADIUS + 2; dx++) {
            for (int dz = -RADIUS - 2; dz <= RADIUS + 2; dz++) {
                double d = Math.sqrt(dx * dx + dz * dz);
                if (d < RADIUS - 1.5 || d > RADIUS + 0.5) {
                    continue;
                }
                int x = cx + dx;
                int z = cz + dz;
                set(level, x, floorY, z, Blocks.STONE_BRICKS.defaultBlockState());
                for (int h = 1; h <= wallHeight; h++) {
                    set(level, x, floorY + h, z, (h == 3 && (dx + dz) % 5 == 0)
                            ? Blocks.MOSSY_STONE_BRICKS.defaultBlockState() : Blocks.STONE_BRICKS.defaultBlockState());
                }
                if (((dx + dz) & 1) == 0) { // battlements
                    set(level, x, floorY + wallHeight + 1, z, Blocks.STONE_BRICKS.defaultBlockState());
                }
                if ((Math.abs(dx) + Math.abs(dz)) % 9 == 0 && ((dx + dz) & 1) == 0) {
                    set(level, x, floorY + wallHeight + 2, z, Blocks.LANTERN.defaultBlockState());
                }
            }
        }
        for (int[] dir : new int[][]{{1, 0}, {-1, 0}, {0, 1}, {0, -1}}) {
            buildGate(level, cx, cz, floorY, dir[0], dir[1]);
        }

        // 5. buildings: inner ring (houses and ruins), outer ring (watchtowers and houses)
        buildHouse(level, cx, cz, floorY, 8, 8);     // south-east quadrant etc, door faces the Core
        buildRuin(level, cx, cz, floorY, -8, 8, 1);
        buildHouse(level, cx, cz, floorY, -8, -8);
        buildRuin(level, cx, cz, floorY, 8, -8, 2);
        buildTower(level, cx, cz, floorY, 10, 27);
        buildHouse(level, cx, cz, floorY, -27, 10);
        buildTower(level, cx, cz, floorY, -10, -27);
        buildHouse(level, cx, cz, floorY, 27, -10);

        // 6. the Core, spawn point and (optionally) a real world border
        BlockPos corePos = new BlockPos(cx, floorY + 1, cz);
        level.setBlock(corePos, ModRegistry.DEFENSE_CORE.get().defaultBlockState(), Block.UPDATE_ALL);
        level.setDefaultSpawnPos(corePos.offset(0, 0, 3), 0F);
        if (setWorldBorder) {
            WorldBorder border = level.getWorldBorder();
            border.setCenter(cx + 0.5, cz + 0.5);
            border.setSize(2 * RADIUS + 14);
        }
    }

    /** A fence post with a lantern on top (4 blocks tall). */
    private static void lampPost(ServerLevel level, int x, int floorY, int z) {
        for (int h = 1; h <= 3; h++) {
            set(level, x, floorY + h, z, Blocks.OAK_FENCE.defaultBlockState());
        }
        set(level, x, floorY + 4, z, Blocks.LANTERN.defaultBlockState());
    }

    /**
     * Fills from {@code startY} downwards with stone until real ground is reached, so the arena (and the wall
     * on its edge) never floats over a valley, a lake or a cave. Gives up after 48 blocks.
     */
    private static void fillDownToSolid(ServerLevel level, int x, int startY, int z) {
        int minY = Math.max(level.getMinBuildHeight() + 1, startY - 48);
        for (int y = startY; y >= minY; y--) {
            BlockPos p = new BlockPos(x, y, z);
            BlockState state = level.getBlockState(p);
            boolean ground = state.blocksMotion() && state.getFluidState().isEmpty()
                    && !state.is(BlockTags.LEAVES) && !state.is(BlockTags.LOGS);
            if (ground) {
                return;
            }
            level.setBlock(p, Blocks.STONE.defaultBlockState(), FLAGS);
        }
    }

    // ---------------------------------------------------------------- gate

    private static void buildGate(ServerLevel level, int cx, int cz, int floorY, int ax, int az) {
        // (ax, az) is a unit axis direction; the gate is a 3-wide opening in the wall with iron bars
        for (int along = RADIUS - 3; along <= RADIUS + 1; along++) {
            for (int w = -2; w <= 2; w++) {
                int x = cx + ax * along + (az != 0 ? w : 0);
                int z = cz + az * along + (ax != 0 ? w : 0);
                boolean pillar = Math.abs(w) == 2;
                for (int h = 0; h <= 8; h++) {
                    BlockState state;
                    if (pillar) {
                        state = h == 0 ? Blocks.STONE_BRICKS.defaultBlockState() : (h <= 8 ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState());
                    } else if (h == 0) {
                        state = Blocks.DIRT_PATH.defaultBlockState();
                    } else if (h <= 3) {
                        state = along >= RADIUS - 1 && along <= RADIUS ? Blocks.IRON_BARS.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    } else if (h <= 4) {
                        state = Blocks.STONE_BRICKS.defaultBlockState(); // lintel
                    } else {
                        state = h <= 8 ? Blocks.STONE_BRICKS.defaultBlockState() : Blocks.AIR.defaultBlockState();
                    }
                    level.setBlock(new BlockPos(x, floorY + h, z), state, Block.UPDATE_ALL);
                }
                if (pillar) {
                    set(level, x, floorY + 9, z, Blocks.LANTERN.defaultBlockState());
                }
            }
        }
    }

    // ---------------------------------------------------------------- buildings

    /** Maps a footprint-local (x, z) to the world, with the door side (local -z) turned to face the arena centre. */
    private record Frame(ServerLevel level, int cx, int cz, int px, int pz, int floorY, int rot) {
        static Frame at(ServerLevel level, int cx, int cz, int floorY, int offX, int offZ) {
            int rot;
            if (Math.abs(offX) > Math.abs(offZ)) {
                rot = offX < 0 ? 1 : 3; // door towards east (+x) when the building is west of the core
            } else {
                rot = offZ > 0 ? 0 : 2;
            }
            return new Frame(level, cx, cz, cx + offX, cz + offZ, floorY, rot);
        }

        int[] rotate(int x, int z) {
            return switch (rot) {
                case 1 -> new int[]{-z, x};
                case 2 -> new int[]{-x, -z};
                case 3 -> new int[]{z, -x};
                default -> new int[]{x, z};
            };
        }

        void put(int lx, int ly, int lz, BlockState state) {
            int[] r = rotate(lx, lz);
            level.setBlock(new BlockPos(px + r[0], floorY + ly, pz + r[1]), state, FLAGS);
        }

        Direction facing(int lx, int lz) {
            int[] r = rotate(lx, lz);
            return Direction.getNearest(r[0], 0, r[1]);
        }
    }

    private static void buildHouse(ServerLevel level, int cx, int cz, int floorY, int offX, int offZ) {
        Frame f = Frame.at(level, cx, cz, floorY, offX, offZ);
        int hw = 3;
        BlockState planks = Blocks.OAK_PLANKS.defaultBlockState();
        BlockState log = Blocks.OAK_LOG.defaultBlockState();
        BlockState roof = Blocks.SPRUCE_PLANKS.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = -hw; x <= hw; x++) {
            for (int z = -hw; z <= hw; z++) {
                f.put(x, 0, z, planks); // floor
                boolean edge = Math.abs(x) == hw || Math.abs(z) == hw;
                boolean corner = Math.abs(x) == hw && Math.abs(z) == hw;
                for (int h = 1; h <= 3; h++) {
                    BlockState s = air;
                    if (corner) {
                        s = log;
                    } else if (edge) {
                        boolean window = h == 2 && ((Math.abs(x) == hw && Math.abs(z) == 1) || (z == hw && Math.abs(x) == 1));
                        s = window ? Blocks.GLASS.defaultBlockState() : planks;
                    }
                    f.put(x, h, z, s);
                }
                f.put(x, 4, z, roof);
                if (Math.abs(x) <= 2 && Math.abs(z) <= 2) f.put(x, 5, z, roof);
                if (Math.abs(x) <= 1 && Math.abs(z) <= 1) f.put(x, 6, z, Blocks.SPRUCE_SLAB.defaultBlockState());
            }
        }
        f.put(0, 1, -hw, air); // doorway
        f.put(0, 2, -hw, air);
        f.put(hw - 1, 1, hw - 1, Blocks.CRAFTING_TABLE.defaultBlockState());
        f.put(-hw + 1, 1, hw - 1, Blocks.CHEST.defaultBlockState());
        f.put(-hw + 1, 3, -hw + 1, Blocks.LANTERN.defaultBlockState());
    }

    private static void buildTower(ServerLevel level, int cx, int cz, int floorY, int offX, int offZ) {
        Frame f = Frame.at(level, cx, cz, floorY, offX, offZ);
        int hw = 2;
        BlockState cobble = Blocks.COBBLESTONE.defaultBlockState();
        BlockState brick = Blocks.STONE_BRICKS.defaultBlockState();
        BlockState air = Blocks.AIR.defaultBlockState();
        for (int x = -hw; x <= hw; x++) {
            for (int z = -hw; z <= hw; z++) {
                boolean edge = Math.abs(x) == hw || Math.abs(z) == hw;
                boolean corner = Math.abs(x) == hw && Math.abs(z) == hw;
                f.put(x, 0, z, brick);
                for (int h = 1; h <= 8; h++) {
                    f.put(x, h, z, edge ? (corner ? brick : cobble) : air);
                }
                boolean ladderColumn = x == 0 && z == hw - 1;
                f.put(x, 9, z, ladderColumn ? air : brick); // platform, with a hatch above the ladder
                if (edge) {
                    f.put(x, 10, z, ((x + z) & 1) == 0 ? brick : air); // battlements
                }
            }
        }
        f.put(0, 1, -hw, air); // doorway
        f.put(0, 2, -hw, air);
        BlockState ladder = Blocks.LADDER.defaultBlockState().setValue(LadderBlock.FACING, f.facing(0, -1));
        for (int h = 1; h <= 9; h++) {
            f.put(0, h, hw - 1, ladder);
        }
        f.put(0, 10, 0, Blocks.LANTERN.defaultBlockState());
    }

    private static void buildRuin(ServerLevel level, int cx, int cz, int floorY, int offX, int offZ, int seed) {
        Frame f = Frame.at(level, cx, cz, floorY, offX, offZ);
        Random random = new Random(seed * 31L + offX * 7L + offZ);
        int hw = 3;
        for (int x = -hw; x <= hw; x++) {
            for (int z = -hw; z <= hw; z++) {
                boolean edge = Math.abs(x) == hw || Math.abs(z) == hw;
                f.put(x, 0, z, random.nextInt(3) == 0 ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.COBBLESTONE.defaultBlockState());
                if (edge) {
                    int height = random.nextInt(4); // broken walls, 0..3 high
                    for (int h = 1; h <= height; h++) {
                        f.put(x, h, z, random.nextBoolean() ? Blocks.MOSSY_COBBLESTONE.defaultBlockState() : Blocks.CRACKED_STONE_BRICKS.defaultBlockState());
                    }
                }
            }
        }
    }
}
