package com.photronix.towerdefense;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.item.Items;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
import net.minecraftforge.event.entity.living.MobSpawnEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.items.ItemHandlerHelper;

public class GameEvents {
    private static final String KIT_FLAG = "towerdefense_kit";
    private static final int KIT_COINS = 20;

    /** Wave enemies drop coins instead of their normal loot. */
    @SubscribeEvent
    public void onDrops(LivingDropsEvent event) {
        LivingEntity entity = event.getEntity();
        if (!entity.getTags().contains(CoreBlockEntity.ENEMY_TAG)) {
            return;
        }
        event.getDrops().clear();
        int coins = coinsFor(entity);
        if (coins > 0) {
            event.getDrops().add(new ItemEntity(entity.level(), entity.getX(), entity.getY(), entity.getZ(),
                    new ItemStack(ModRegistry.COIN.get(), coins)));
        }
    }

    private static int coinsFor(LivingEntity entity) {
        EntityType<?> type = entity.getType();
        int roll = entity.getRandom().nextInt(2); // 0 or 1 bonus coin
        if (type == EntityType.RAVAGER) return 15;
        if (type == EntityType.VINDICATOR) return 3 + roll;
        if (type == EntityType.SPIDER || type == EntityType.HUSK) return 2 + roll;
        return 1 + roll;
    }

    /** No natural monsters (zombies, slimes, creepers...) near a Core - wave enemies use MobSpawnType.EVENT and are unaffected. */
    @SubscribeEvent
    public void onFinalizeSpawn(MobSpawnEvent.FinalizeSpawn event) {
        Mob mob = event.getEntity();
        if (mob.getType().getCategory() != MobCategory.MONSTER || mob.getTags().contains(CoreBlockEntity.ENEMY_TAG)) {
            return;
        }
        MobSpawnType type = event.getSpawnType();
        if (type != MobSpawnType.NATURAL && type != MobSpawnType.CHUNK_GENERATION
                && type != MobSpawnType.PATROL && type != MobSpawnType.REINFORCEMENT) {
            return;
        }
        if (CoreBlockEntity.isNearCore(event.getLevel().getLevel().dimension(), event.getX(), event.getZ())) {
            event.setSpawnCancelled(true);
        }
    }

    @SubscribeEvent
    public void onServerStopped(ServerStoppedEvent event) {
        CoreBlockEntity.forgetAllCores();
    }

    @SubscribeEvent
    public void onXp(LivingExperienceDropEvent event) {
        if (event.getEntity().getTags().contains(CoreBlockEntity.ENEMY_TAG)) {
            event.setDroppedExperience(0);
        }
    }

    /** Give every new player a starter kit and a short how-to the first time they join. */
    @SubscribeEvent
    public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        CompoundTag persisted = data.getCompound(Player.PERSISTED_NBT_TAG);
        if (persisted.getBoolean(KIT_FLAG)) {
            return;
        }
        persisted.putBoolean(KIT_FLAG, true);
        data.put(Player.PERSISTED_NBT_TAG, persisted);
        boolean arena = maybeBuildStartingArena(player);
        giveKit(player, !arena);
        TowerUnlocks.awardStarter(player);
        player.sendSystemMessage(Component.literal(
                "§6§lCore Siege§r\n"
                + (arena ? "§71. Your battleground is ready! Right-click the §fDefense Core§7 in the middle to start.\n"
                        : "§71. Place the §fDefense Core§7 on open, flat ground and right-click it to start (or type §f/tdarena§7 to build a battleground).\n")
                + "§72. Monsters spawn in a ring around it and walk to the Core. Don't let them touch it!\n"
                + "§73. Place your §fCrafting Table§7 and craft towers from coins (open the recipe book!). New towers unlock as you clear waves.\n"
                + "§74. Place towers along the path. Killing monsters drops more coins.\n"
                + "§7Games run at night with Night Vision, and natural monsters can't spawn near the Core.\n"
                + "§7Commands: §f/tdarena§7 (build the battleground map), §f/tdkit§7 (new kit), §f/tdrecipes§7 (tower list). The world difficulty must not be Peaceful."));
    }

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("tdarena")
                .requires(src -> src.hasPermission(2) || src.getServer().isSingleplayer())
                .executes(ctx -> {
                    ServerPlayer player = ctx.getSource().getPlayerOrException();
                    ServerLevel level = player.serverLevel();
                    BlockPos center = player.blockPosition();
                    ArenaBuilder.build(level, center, true);
                    ArenaState.get(level).markBuilt();
                    player.teleportTo(level, center.getX() + 0.5, center.getY(), center.getZ() + 3.5, player.getYRot(), 0);
                    ctx.getSource().sendSuccess(() -> Component.literal("§6Battleground built! §7The Defense Core is in the middle. Right-click it to start. "
                            + "(§f/tdarena noborder§7 removes the world border.)"), false);
                    return 1;
                })
                .then(Commands.literal("noborder").executes(ctx -> {
                    ServerLevel level = ctx.getSource().getLevel();
                    level.getWorldBorder().setSize(5.9999968E7);
                    ctx.getSource().sendSuccess(() -> Component.literal("§7World border removed."), false);
                    return 1;
                })));
        dispatcher.register(Commands.literal("tdrecipes").executes(ctx -> {
            TowerUnlocks.sendRecipeList(ctx.getSource().getPlayerOrException());
            return 1;
        }));
        dispatcher.register(Commands.literal("tdkit").executes(ctx -> {
            giveKit(ctx.getSource().getPlayerOrException(), true);
            return 1;
        }));
    }

    /**
     * In a brand-new world (first minute, overworld, never built before) the battleground is built around
     * the player automatically. Existing worlds are never touched. Returns true if an arena is being built.
     */
    private static boolean maybeBuildStartingArena(ServerPlayer player) {
        ServerLevel level = player.serverLevel();
        if (!TdConfig.autoArena() || level.dimension() != Level.OVERWORLD || level.getGameTime() > 20 * 60) {
            return false;
        }
        ArenaState state = ArenaState.get(level);
        if (state.isBuilt()) {
            return false;
        }
        state.markBuilt();
        BlockPos center = player.blockPosition();
        level.getServer().execute(() -> {
            ArenaBuilder.build(level, center, true);
            player.teleportTo(level, center.getX() + 0.5, center.getY(), center.getZ() + 3.5, player.getYRot(), 0);
        });
        return true;
    }

    private static void giveKit(ServerPlayer player, boolean includeCore) {
        if (includeCore) {
            ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(ModRegistry.DEFENSE_CORE_ITEM.get()));
        }
        ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(ModRegistry.COIN.get(), KIT_COINS));
        ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(Items.CRAFTING_TABLE));
    }
}
