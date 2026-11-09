package com.photronix.towerdefense;

import com.mojang.brigadier.CommandDispatcher;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingExperienceDropEvent;
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
        giveKit(player);
        player.sendSystemMessage(Component.literal(
                "§6§lTower Defense§r\n"
                + "§71. Place the §fDefense Core§7 on open, flat ground and right-click it to start.\n"
                + "§72. Monsters spawn in a ring around it and walk to the Core. Don't let them touch it!\n"
                + "§73. Craft towers from coins: §fArrow Tower§7 = 3 coins, §fFrost Tower§7 = 5, §fCannon Tower§7 = 7.\n"
                + "§74. Place towers along the path. Killing monsters drops more coins.\n"
                + "§7Lost your kit? Type §f/tdkit§7. The world difficulty must not be Peaceful."));
    }

    @SubscribeEvent
    public void onCommands(RegisterCommandsEvent event) {
        CommandDispatcher<CommandSourceStack> dispatcher = event.getDispatcher();
        dispatcher.register(Commands.literal("tdkit").executes(ctx -> {
            giveKit(ctx.getSource().getPlayerOrException());
            return 1;
        }));
    }

    private static void giveKit(ServerPlayer player) {
        ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(ModRegistry.DEFENSE_CORE_ITEM.get()));
        ItemHandlerHelper.giveItemToPlayer(player, new ItemStack(ModRegistry.COIN.get(), KIT_COINS));
    }
}
