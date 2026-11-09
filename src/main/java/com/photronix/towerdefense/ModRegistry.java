package com.photronix.towerdefense;

import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.minecraft.world.level.material.MapColor;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public final class ModRegistry {
    private ModRegistry() {}

    private static final String ID = TowerDefenseMod.MOD_ID;

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);

    // ---- Blocks ----
    public static final RegistryObject<Block> DEFENSE_CORE = BLOCKS.register("defense_core",
            () -> new CoreBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE).strength(3.0F, 1200.0F)
                    .sound(SoundType.AMETHYST).lightLevel(state -> 12)));

    public static final RegistryObject<Block> ARROW_TOWER = BLOCKS.register("arrow_tower",
            () -> new TowerBlock(towerProps(MapColor.COLOR_GREEN), TowerStats.ARROW, "Arrow Tower"));
    public static final RegistryObject<Block> CANNON_TOWER = BLOCKS.register("cannon_tower",
            () -> new TowerBlock(towerProps(MapColor.COLOR_ORANGE), TowerStats.CANNON, "Cannon Tower"));
    public static final RegistryObject<Block> FROST_TOWER = BLOCKS.register("frost_tower",
            () -> new TowerBlock(towerProps(MapColor.ICE), TowerStats.FROST, "Frost Tower"));

    private static BlockBehaviour.Properties towerProps(MapColor color) {
        return BlockBehaviour.Properties.of().mapColor(color).strength(3.0F, 6.0F).sound(SoundType.STONE);
    }

    // ---- Items ----
    public static final RegistryObject<Item> COIN = ITEMS.register("coin",
            () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> DEFENSE_CORE_ITEM = blockItem("defense_core", DEFENSE_CORE);
    public static final RegistryObject<Item> ARROW_TOWER_ITEM = blockItem("arrow_tower", ARROW_TOWER);
    public static final RegistryObject<Item> CANNON_TOWER_ITEM = blockItem("cannon_tower", CANNON_TOWER);
    public static final RegistryObject<Item> FROST_TOWER_ITEM = blockItem("frost_tower", FROST_TOWER);

    private static RegistryObject<Item> blockItem(String name, RegistryObject<Block> block) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    // ---- Block entities ----
    public static final RegistryObject<BlockEntityType<CoreBlockEntity>> CORE_BE = BLOCK_ENTITIES.register("defense_core",
            () -> BlockEntityType.Builder.of(CoreBlockEntity::new, DEFENSE_CORE.get()).build(null));
    public static final RegistryObject<BlockEntityType<TowerBlockEntity>> TOWER_BE = BLOCK_ENTITIES.register("tower",
            () -> BlockEntityType.Builder.of(TowerBlockEntity::new,
                    ARROW_TOWER.get(), CANNON_TOWER.get(), FROST_TOWER.get()).build(null));

    // ---- Creative tab ----
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.towerdefense"))
                    .icon(() -> new ItemStack(ARROW_TOWER_ITEM.get()))
                    .displayItems((params, output) -> {
                        output.accept(DEFENSE_CORE_ITEM.get());
                        output.accept(ARROW_TOWER_ITEM.get());
                        output.accept(CANNON_TOWER_ITEM.get());
                        output.accept(FROST_TOWER_ITEM.get());
                        output.accept(COIN.get());
                    })
                    .build());

    public static void register(IEventBus bus) {
        BLOCKS.register(bus);
        ITEMS.register(bus);
        BLOCK_ENTITIES.register(bus);
        TABS.register(bus);
    }
}
