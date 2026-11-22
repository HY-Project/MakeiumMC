package com.hyproj.makeium.feature.checkpoint;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import com.hyproj.makeium.Makeium;
import com.hyproj.makeium.net.Net;
import com.hyproj.makeium.server.DatapackStore;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.AABB;

/**
 * Test-play checkpoints: snapshot a box of the map (blocks, block entities and optionally entities) before playing,
 * then put it back in one click. Stored as structure NBT under {@code <world>/makeium/checkpoints/<name>.nbt} with the
 * dimension and origin, so they survive restarts (and are left out of map packages).
 */
public final class CheckpointServer {
    public static final int MAX_VOLUME = 4_000_000;

    private CheckpointServer() {}

    public static void init() {
        Net.onServer("cp/list", (p, d) -> sendList(p));
        Net.onServer("cp/save", CheckpointServer::save);
        Net.onServer("cp/restore", CheckpointServer::restore);
        Net.onServer("cp/delete", (p, d) -> {
            try {
                Files.deleteIfExists(file(p.level().getServer(), d.getStringOr("name", "")));
            } catch (IOException | IllegalArgumentException e) {
                Makeium.LOGGER.warn("Could not delete checkpoint: {}", e.toString());
            }
            sendList(p);
        });
    }

    public static void tick(MinecraftServer server) {}

    static Path dir(MinecraftServer server) {
        return server.getWorldPath(LevelResource.ROOT).resolve("makeium").resolve("checkpoints");
    }

    static Path file(MinecraftServer server, String name) {
        if (!DatapackStore.PACK_NAME.matcher(name).matches()) throw new IllegalArgumentException("Invalid checkpoint name: " + name);
        return dir(server).resolve(name + ".nbt");
    }

    private static void sendList(ServerPlayer p) {
        CompoundTag out = new CompoundTag();
        ListTag list = new ListTag();
        try (Stream<Path> s = Files.list(dir(p.level().getServer()))) {
            for (Path f : (Iterable<Path>) s.filter(x -> x.toString().endsWith(".nbt")).sorted()::iterator) {
                CompoundTag e = new CompoundTag();
                String name = f.getFileName().toString();
                e.putString("name", name.substring(0, name.length() - 4));
                e.putLong("time", Files.getLastModifiedTime(f).toMillis());
                e.putLong("size", Files.size(f));
                try {
                    CompoundTag meta = NbtIo.readCompressed(f, NbtAccounter.unlimitedHeap()).getCompoundOrEmpty("makeium");
                    e.put("meta", meta);
                } catch (IOException ignored) {
                    // listed without details
                }
                list.add(e);
            }
        } catch (IOException ignored) {
            // no checkpoints yet
        }
        out.put("checkpoints", list);
        Net.sendToPlayer(p, "cp/list", out);
    }

    private static void toast(ServerPlayer p, boolean ok, String key, String arg) {
        CompoundTag t = new CompoundTag();
        t.putString("kind", ok ? "ok" : "error");
        t.putString("key", key);
        t.putString("arg", arg);
        Net.sendToPlayer(p, "toast", t);
    }

    private static void save(ServerPlayer p, CompoundTag d) {
        MinecraftServer server = p.level().getServer();
        String name = d.getStringOr("name", "");
        int[] mn = d.getIntArray("min").orElse(new int[0]);
        int[] mx = d.getIntArray("max").orElse(new int[0]);
        if (mn.length != 3 || mx.length != 3) {
            toast(p, false, "makeium.cp.no_selection", "");
            return;
        }
        BlockPos min = new BlockPos(Math.min(mn[0], mx[0]), Math.min(mn[1], mx[1]), Math.min(mn[2], mx[2]));
        BlockPos max = new BlockPos(Math.max(mn[0], mx[0]), Math.max(mn[1], mx[1]), Math.max(mn[2], mx[2]));
        Vec3i size = new Vec3i(max.getX() - min.getX() + 1, max.getY() - min.getY() + 1, max.getZ() - min.getZ() + 1);
        long volume = (long) size.getX() * size.getY() * size.getZ();
        if (volume > MAX_VOLUME) {
            toast(p, false, "makeium.edit.too_big", Long.toString(volume));
            return;
        }
        boolean entities = d.getBooleanOr("entities", true);
        StructureTemplate t = new StructureTemplate();
        t.fillFromWorld(p.level(), min, size, entities, List.of());
        CompoundTag tag = t.save(new CompoundTag());
        CompoundTag meta = new CompoundTag();
        meta.putString("dimension", p.level().dimension().identifier().toString());
        meta.putIntArray("min", new int[]{min.getX(), min.getY(), min.getZ()});
        meta.putIntArray("max", new int[]{max.getX(), max.getY(), max.getZ()});
        meta.putBoolean("entities", entities);
        tag.put("makeium", meta);
        try {
            Path f = file(server, name);
            Files.createDirectories(f.getParent());
            NbtIo.writeCompressed(tag, f);
            toast(p, true, "makeium.cp.saved", name);
        } catch (IOException | IllegalArgumentException e) {
            toast(p, false, "makeium.cp.failed", e.getMessage());
        }
        sendList(p);
    }

    private static void restore(ServerPlayer p, CompoundTag d) {
        MinecraftServer server = p.level().getServer();
        String name = d.getStringOr("name", "");
        try {
            CompoundTag tag = NbtIo.readCompressed(file(server, name), NbtAccounter.unlimitedHeap());
            CompoundTag meta = tag.getCompoundOrEmpty("makeium");
            ResourceKey<Level> dim = ResourceKey.create(Registries.DIMENSION, Identifier.parse(meta.getStringOr("dimension", "minecraft:overworld")));
            ServerLevel level = server.getLevel(dim);
            if (level == null) throw new IOException("Unknown dimension " + dim.identifier());
            int[] mn = meta.getIntArray("min").orElse(new int[3]);
            int[] mx = meta.getIntArray("max").orElse(new int[3]);
            BlockPos min = new BlockPos(mn[0], mn[1], mn[2]);
            StructureTemplate t = new StructureTemplate();
            t.load(level.registryAccess().lookupOrThrow(Registries.BLOCK), tag);
            boolean entities = meta.getBooleanOr("entities", true);
            if (entities) {
                // remove the box's current non-player entities; the snapshot brings back its own
                AABB box = new AABB(mn[0], mn[1], mn[2], mx[0] + 1, mx[1] + 1, mx[2] + 1);
                for (Entity e : level.getEntities((Entity) null, box, e -> !(e instanceof Player))) e.discard();
            }
            t.placeInWorld(level, min, min, new StructurePlaceSettings().setIgnoreEntities(!entities), RandomSource.create(), Block.UPDATE_CLIENTS);
            toast(p, true, "makeium.cp.restored", name);
        } catch (IOException | IllegalArgumentException e) {
            toast(p, false, "makeium.cp.failed", e.getMessage());
        }
    }
}
