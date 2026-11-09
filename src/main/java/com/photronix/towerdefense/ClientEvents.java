package com.photronix.towerdefense;

import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ComputeFovModifierEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** Client-only: zoom in while sneaking with the Siege Rifle in hand (the scope). */
@Mod.EventBusSubscriber(modid = TowerDefenseMod.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE, value = Dist.CLIENT)
public final class ClientEvents {
    private ClientEvents() {}

    @SubscribeEvent
    public static void onFov(ComputeFovModifierEvent event) {
        Player player = event.getPlayer();
        if (player.isShiftKeyDown() && player.getMainHandItem().is(ModRegistry.SIEGE_BLASTER.get())) {
            event.setNewFovModifier(event.getNewFovModifier() * 0.3F);
        }
    }
}
