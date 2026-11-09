package com.photronix.towerdefense;

import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.items.ItemHandlerHelper;
import net.minecraftforge.registries.ForgeRegistries;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extra starter-kit items from the config (e.g. a gun from another mod). Each entry looks like
 * {@code modid:item{nbt} xCOUNT}: the NBT in braces and the " xCOUNT" suffix are both optional.
 * Entries naming an item that isn't installed are skipped, so one config works with or without the other mod.
 */
public final class KitItems {
    private KitItems() {}

    private static final Logger LOGGER = LoggerFactory.getLogger("towerdefense");

    /** Parses one config entry; returns {@link ItemStack#EMPTY} (and logs) if it can't be used. */
    public static ItemStack parse(String entry) {
        String text = entry.trim();
        int count = 1;
        int x = text.lastIndexOf(" x");
        if (x > 0 && x > text.lastIndexOf('}')) {
            try {
                count = Math.max(1, Math.min(64, Integer.parseInt(text.substring(x + 2).trim())));
                text = text.substring(0, x).trim();
            } catch (NumberFormatException ignored) {
                // not a count suffix
            }
        }
        String idPart = text;
        String nbtPart = null;
        int brace = text.indexOf('{');
        if (brace >= 0) {
            idPart = text.substring(0, brace).trim();
            nbtPart = text.substring(brace);
        }
        ResourceLocation id = ResourceLocation.tryParse(idPart);
        Item item = id == null ? null : ForgeRegistries.ITEMS.getValue(id);
        if (id == null || item == null || !ForgeRegistries.ITEMS.containsKey(id)) {
            LOGGER.info("Core Siege kit: skipping '{}' (item not installed)", entry);
            return ItemStack.EMPTY;
        }
        ItemStack stack = new ItemStack(item, count);
        if (nbtPart != null) {
            try {
                CompoundTag tag = TagParser.parseTag(nbtPart);
                stack.setTag(tag);
            } catch (CommandSyntaxException e) {
                LOGGER.warn("Core Siege kit: bad NBT in '{}': {}", entry, e.getMessage());
                return ItemStack.EMPTY;
            }
        }
        return stack;
    }

    /** Gives the player every usable item from the per-wave resupply config list (e.g. ammo). */
    public static void giveResupply(ServerPlayer player) {
        for (String entry : TdConfig.resupplyItems()) {
            ItemStack stack = parse(entry);
            if (!stack.isEmpty()) {
                ItemHandlerHelper.giveItemToPlayer(player, stack);
            }
        }
    }

    /** Gives the player every usable configured extra item. */
    public static void giveExtras(ServerPlayer player) {
        for (String entry : TdConfig.extraKitItems()) {
            ItemStack stack = parse(entry);
            if (!stack.isEmpty()) {
                ItemHandlerHelper.giveItemToPlayer(player, stack);
            }
        }
    }
}
