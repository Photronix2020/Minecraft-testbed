package com.photronix.towerdefense;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(TowerDefenseMod.MOD_ID)
public class TowerDefenseMod {
    public static final String MOD_ID = "towerdefense";

    public TowerDefenseMod() {
        IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
        ModRegistry.register(modBus);
        MinecraftForge.EVENT_BUS.register(new GameEvents());
    }
}
