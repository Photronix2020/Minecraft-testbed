package com.photronix.towerdefense;

import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityTicker;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import org.jetbrains.annotations.Nullable;

/** A tower that automatically shoots nearby enemies. Behaviour lives in {@link TowerBlockEntity}. */
public class TowerBlock extends Block implements EntityBlock {
    public final TowerStats stats;
    private final String displayName;

    public TowerBlock(Properties properties, TowerStats stats, String displayName) {
        super(properties);
        this.stats = stats;
        this.displayName = displayName;
    }

    @Nullable
    @Override
    public BlockEntity newBlockEntity(BlockPos pos, BlockState state) {
        return new TowerBlockEntity(pos, state);
    }

    @Nullable
    @Override
    @SuppressWarnings("unchecked")
    public <T extends BlockEntity> BlockEntityTicker<T> getTicker(Level level, BlockState state, BlockEntityType<T> type) {
        if (level.isClientSide || type != ModRegistry.TOWER_BE.get()) {
            return null;
        }
        return (lvl, pos, st, be) -> ((TowerBlockEntity) be).serverTick((ServerLevel) lvl);
    }

    @Override
    public InteractionResult use(BlockState state, Level level, BlockPos pos, Player player,
                                 InteractionHand hand, BlockHitResult hit) {
        if (!level.isClientSide) {
            String extra = stats.splashRadius() > 0 ? ", splash " + stats.splashRadius() : "";
            if (stats.slowTicks() > 0) {
                extra += ", slows";
            }
            player.displayClientMessage(Component.literal(displayName + ": range " + (int) stats.range()
                    + ", damage " + stats.damage() + ", " + String.format("%.1f", 20.0 / stats.cooldownTicks())
                    + " shots/sec" + extra), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
