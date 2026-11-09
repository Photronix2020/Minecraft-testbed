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

import java.util.EnumMap;
import java.util.Map;

public final class ModRegistry {
    private ModRegistry() {}

    private static final String ID = TowerDefenseMod.MOD_ID;

    public static final DeferredRegister<Block> BLOCKS = DeferredRegister.create(ForgeRegistries.BLOCKS, ID);
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ID);
    public static final DeferredRegister<BlockEntityType<?>> BLOCK_ENTITIES =
            DeferredRegister.create(ForgeRegistries.BLOCK_ENTITY_TYPES, ID);
    public static final DeferredRegister<CreativeModeTab> TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ID);

    // ---- Core ----
    public static final RegistryObject<Block> DEFENSE_CORE = BLOCKS.register("defense_core",
            () -> new CoreBlock(BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PURPLE).strength(3.0F, 1200.0F)
                    .sound(SoundType.AMETHYST).lightLevel(state -> 12)));
    public static final RegistryObject<Item> DEFENSE_CORE_ITEM = blockItem("defense_core", DEFENSE_CORE);

    // ---- Towers (one block + item per TowerType) ----
    public static final Map<TowerType, RegistryObject<Block>> TOWER_BLOCKS = new EnumMap<>(TowerType.class);
    public static final Map<TowerType, RegistryObject<Item>> TOWER_ITEMS = new EnumMap<>(TowerType.class);

    static {
        for (TowerType type : TowerType.values()) {
            RegistryObject<Block> block = BLOCKS.register(type.id(), () -> new TowerBlock(
                    BlockBehaviour.Properties.of().mapColor(type.mapColor).strength(3.0F, 6.0F).sound(SoundType.STONE), type));
            TOWER_BLOCKS.put(type, block);
            TOWER_ITEMS.put(type, blockItem(type.id(), block));
        }
    }

    // ---- Items ----
    public static final RegistryObject<Item> COIN = ITEMS.register("coin", () -> new Item(new Item.Properties()));
    public static final RegistryObject<Item> SIEGE_BLASTER = ITEMS.register("siege_blaster",
            () -> new BlasterItem(new Item.Properties().stacksTo(1)));

    private static RegistryObject<Item> blockItem(String name, RegistryObject<Block> block) {
        return ITEMS.register(name, () -> new BlockItem(block.get(), new Item.Properties()));
    }

    // ---- Block entities ----
    public static final RegistryObject<BlockEntityType<CoreBlockEntity>> CORE_BE = BLOCK_ENTITIES.register("defense_core",
            () -> BlockEntityType.Builder.of(CoreBlockEntity::new, DEFENSE_CORE.get()).build(null));
    public static final RegistryObject<BlockEntityType<TowerBlockEntity>> TOWER_BE = BLOCK_ENTITIES.register("tower",
            () -> BlockEntityType.Builder.of(TowerBlockEntity::new,
                    TOWER_BLOCKS.values().stream().map(RegistryObject::get).toArray(Block[]::new)).build(null));

    // ---- Creative tab ----
    public static final RegistryObject<CreativeModeTab> TAB = TABS.register("main",
            () -> CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup.towerdefense"))
                    .icon(() -> new ItemStack(TOWER_ITEMS.get(TowerType.ARROW).get()))
                    .displayItems((params, output) -> {
                        output.accept(DEFENSE_CORE_ITEM.get());
                        TOWER_ITEMS.values().forEach(item -> output.accept(item.get()));
                        output.accept(SIEGE_BLASTER.get());
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
