package com.photronix.towerdefense;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

/** Remembers whether this world already got its battleground, so it is only auto-built once. */
public class ArenaState extends SavedData {
    private boolean built;

    public static ArenaState get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(ArenaState::load, ArenaState::new, "towerdefense_arena");
    }

    public boolean isBuilt() {
        return built;
    }

    public void markBuilt() {
        built = true;
        setDirty();
    }

    private static ArenaState load(CompoundTag tag) {
        ArenaState state = new ArenaState();
        state.built = tag.getBoolean("Built");
        return state;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        tag.putBoolean("Built", built);
        return tag;
    }
}
