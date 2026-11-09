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
    public final TowerType type;
    public final TowerStats stats;

    public TowerBlock(Properties properties, TowerType type) {
        super(properties);
        this.type = type;
        this.stats = type.stats;
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
            player.displayClientMessage(Component.literal(type.displayName + " (" + type.blurb + "): range "
                    + (int) stats.range() + ", damage " + stats.damage() + ", "
                    + String.format("%.1f", 20.0 / stats.cooldownTicks()) + " shots/sec"), true);
        }
        return InteractionResult.sidedSuccess(level.isClientSide);
    }
}
