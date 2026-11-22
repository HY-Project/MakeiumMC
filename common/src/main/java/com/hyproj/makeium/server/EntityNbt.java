package com.hyproj.makeium.server;

import java.util.UUID;

import com.hyproj.makeium.Makeium;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;
import net.minecraft.world.phys.Vec3;

/** Entity ↔ NBT the way {@code /summon} and {@code /data merge entity} do it (stable across versions). */
public final class EntityNbt {
    private EntityNbt() {}

    /** Spawns {@code tag} (must contain "id") at {@code pos} with the given rotation; null when it could not be created. */
    public static Entity summon(ServerLevel level, CompoundTag tag, Vec3 pos, float yaw, float pitch) {
        if (!Level.isInSpawnableBounds(BlockPos.containing(pos))) return null;
        Entity entity = EntityType.loadEntityRecursive(tag.copy(), level, new net.minecraft.world.entity.EntitySpawnRequest(EntitySpawnReason.COMMAND, false), e -> {
            e.snapTo(pos.x, pos.y, pos.z, yaw, pitch);
            return e;
        });
        if (entity == null) return null;
        if (!level.tryAddFreshEntityWithPassengers(entity)) return null;
        return entity;
    }

    public static CompoundTag save(Entity entity) {
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(entity.problemPath(), Makeium.LOGGER)) {
            TagValueOutput output = TagValueOutput.createWithContext(reporter, entity.registryAccess());
            entity.saveWithoutId(output);
            return output.buildResult();
        }
    }

    /**
     * Writes {@code patch} into the entity's data, keeping its UUID. Unlike /data merge, each top-level key of the
     * patch replaces the old value whole (a text component compound must not keep stale style keys).
     */
    public static void replaceKeys(Entity entity, CompoundTag patch) {
        if (entity instanceof Player) return;
        CompoundTag merged = save(entity);
        for (String key : patch.keySet()) {
            merged.put(key, patch.get(key).copy());
        }
        UUID uuid = entity.getUUID();
        try (ProblemReporter.ScopedCollector reporter = new ProblemReporter.ScopedCollector(entity.problemPath(), Makeium.LOGGER)) {
            entity.load(TagValueInput.create(reporter, entity.registryAccess(), merged));
            entity.setUUID(uuid);
            entity.postDataManipulated();
        }
    }
}
