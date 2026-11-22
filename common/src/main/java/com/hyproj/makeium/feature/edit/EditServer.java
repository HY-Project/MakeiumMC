package com.hyproj.makeium.feature.edit;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.hyproj.makeium.net.Net;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.arguments.blocks.BlockStateParser;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Vec3i;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.Mirror;
import net.minecraft.world.level.block.Rotation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructurePlaceSettings;
import net.minecraft.world.level.levelgen.structure.templatesystem.StructureTemplate;

/**
 * Region edit (a light WorldEdit): fill / replace / clear / copy / paste / rotate / mirror / move / undo over the
 * selection the client sends. Every change first snapshots the affected box as a StructureTemplate (blocks + block
 * entities), so undo restores exactly. Per player: one clipboard and an undo history of {@link #HISTORY} steps.
 */
public final class EditServer {
    public static final int MAX_VOLUME = 1_000_000;
    private static final int HISTORY = 10;
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE;

    private record Snapshot(ServerLevel level, BlockPos origin, StructureTemplate template) {}

    private static final class Clip {
        StructureTemplate template;
        Rotation rotation = Rotation.NONE;
        Mirror mirror = Mirror.NONE;
    }

    private static final Map<UUID, Deque<Snapshot>> UNDO = new HashMap<>();
    private static final Map<UUID, Clip> CLIPBOARD = new HashMap<>();

    private EditServer() {}

    public static void init() {
        Net.onServer("edit/op", EditServer::op);
    }

    public static void tick(MinecraftServer server) {}

    private static void reply(ServerPlayer p, boolean ok, String key, String arg) {
        CompoundTag t = new CompoundTag();
        t.putBoolean("ok", ok);
        t.putString("key", key);
        t.putString("arg", arg);
        Net.sendToPlayer(p, "edit/result", t);
        CompoundTag toast = new CompoundTag();
        toast.putString("kind", ok ? "ok" : "error");
        toast.putString("key", key);
        toast.putString("arg", arg);
        Net.sendToPlayer(p, "toast", toast);
    }

    private static void op(ServerPlayer p, CompoundTag d) {
        ServerLevel level = p.level();
        String op = d.getStringOr("op", "");
        int[] mn = d.getIntArray("min").orElse(new int[0]);
        int[] mx = d.getIntArray("max").orElse(new int[0]);
        boolean hasBox = mn.length == 3 && mx.length == 3;
        BlockPos min = hasBox ? new BlockPos(Math.min(mn[0], mx[0]), Math.min(mn[1], mx[1]), Math.min(mn[2], mx[2])) : null;
        BlockPos max = hasBox ? new BlockPos(Math.max(mn[0], mx[0]), Math.max(mn[1], mx[1]), Math.max(mn[2], mx[2])) : null;
        long volume = hasBox ? (long) (max.getX() - min.getX() + 1) * (max.getY() - min.getY() + 1) * (max.getZ() - min.getZ() + 1) : 0;
        if (hasBox && volume > MAX_VOLUME) {
            reply(p, false, "makeium.edit.too_big", Long.toString(volume));
            return;
        }
        try {
            switch (op) {
                case "fill" -> {
                    BlockState state = parse(level, d.getStringOr("block", "minecraft:stone"));
                    snapshot(p, level, min, max);
                    int n = forEach(level, min, max, (pos, old) -> state);
                    reply(p, true, "makeium.edit.done", Integer.toString(n));
                }
                case "clear" -> {
                    snapshot(p, level, min, max);
                    int n = forEach(level, min, max, (pos, old) -> Blocks.AIR.defaultBlockState());
                    reply(p, true, "makeium.edit.done", Integer.toString(n));
                }
                case "replace" -> {
                    BlockState from = parse(level, d.getStringOr("from", "minecraft:stone"));
                    BlockState to = parse(level, d.getStringOr("to", "minecraft:air"));
                    boolean anyState = !d.getStringOr("from", "").contains("[");
                    snapshot(p, level, min, max);
                    int n = forEach(level, min, max, (pos, old) -> (anyState ? old.is(from.getBlock()) : old.equals(from)) ? to : null);
                    reply(p, true, "makeium.edit.done", Integer.toString(n));
                }
                case "walls" -> {
                    BlockState state = parse(level, d.getStringOr("block", "minecraft:stone"));
                    snapshot(p, level, min, max);
                    int n = forEach(level, min, max, (pos, old) -> pos.getX() == min.getX() || pos.getX() == max.getX() || pos.getZ() == min.getZ() || pos.getZ() == max.getZ() ? state : null);
                    reply(p, true, "makeium.edit.done", Integer.toString(n));
                }
                case "copy" -> {
                    Clip c = new Clip();
                    c.template = capture(level, min, max);
                    CLIPBOARD.put(p.getUUID(), c);
                    reply(p, true, "makeium.edit.copied", Long.toString(volume));
                }
                case "rotate" -> {
                    Clip c = CLIPBOARD.get(p.getUUID());
                    if (c == null) {
                        reply(p, false, "makeium.edit.no_clipboard", "");
                        return;
                    }
                    c.rotation = c.rotation.getRotated(Rotation.CLOCKWISE_90);
                    reply(p, true, "makeium.edit.rotated", c.rotation.name());
                }
                case "mirror" -> {
                    Clip c = CLIPBOARD.get(p.getUUID());
                    if (c == null) {
                        reply(p, false, "makeium.edit.no_clipboard", "");
                        return;
                    }
                    c.mirror = c.mirror == Mirror.NONE ? Mirror.FRONT_BACK : c.mirror == Mirror.FRONT_BACK ? Mirror.LEFT_RIGHT : Mirror.NONE;
                    reply(p, true, "makeium.edit.mirrored", c.mirror.name());
                }
                case "paste" -> {
                    Clip c = CLIPBOARD.get(p.getUUID());
                    if (c == null) {
                        reply(p, false, "makeium.edit.no_clipboard", "");
                        return;
                    }
                    int[] at = d.getIntArray("at").orElse(new int[]{p.getBlockX(), p.getBlockY(), p.getBlockZ()});
                    BlockPos origin = new BlockPos(at[0], at[1], at[2]);
                    paste(p, level, c, origin);
                    reply(p, true, "makeium.edit.pasted", origin.toShortString());
                }
                case "move" -> {
                    int[] by = d.getIntArray("by").orElse(new int[]{0, 1, 0});
                    StructureTemplate t = capture(level, min, max);
                    BlockPos dest = min.offset(by[0], by[1], by[2]);
                    BlockPos destMax = max.offset(by[0], by[1], by[2]);
                    // one undo step covering both the source and the destination
                    BlockPos uMin = new BlockPos(Math.min(min.getX(), dest.getX()), Math.min(min.getY(), dest.getY()), Math.min(min.getZ(), dest.getZ()));
                    BlockPos uMax = new BlockPos(Math.max(max.getX(), destMax.getX()), Math.max(max.getY(), destMax.getY()), Math.max(max.getZ(), destMax.getZ()));
                    snapshot(p, level, uMin, uMax);
                    forEach(level, min, max, (pos, old) -> Blocks.AIR.defaultBlockState());
                    t.placeInWorld(level, dest, dest, new StructurePlaceSettings().setIgnoreEntities(true), RandomSource.create(), FLAGS);
                    reply(p, true, "makeium.edit.moved", dest.toShortString());
                }
                case "undo" -> {
                    Deque<Snapshot> h = UNDO.get(p.getUUID());
                    if (h == null || h.isEmpty()) {
                        reply(p, false, "makeium.edit.nothing_to_undo", "");
                        return;
                    }
                    Snapshot s = h.pop();
                    s.template.placeInWorld(s.level, s.origin, s.origin, new StructurePlaceSettings().setIgnoreEntities(true), RandomSource.create(), FLAGS);
                    reply(p, true, "makeium.edit.undone", Integer.toString(h.size()));
                }
                default -> reply(p, false, "makeium.edit.unknown", op);
            }
        } catch (CommandSyntaxException e) {
            reply(p, false, "makeium.edit.bad_block", e.getMessage());
        }
    }

    private interface Change {
        /** New state for this position, or null to leave it. */
        BlockState apply(BlockPos pos, BlockState old);
    }

    private static int forEach(ServerLevel level, BlockPos min, BlockPos max, Change change) {
        int n = 0;
        for (BlockPos pos : BlockPos.betweenClosed(min, max)) {
            BlockState old = level.getBlockState(pos);
            BlockState next = change.apply(pos, old);
            if (next != null && next != old) {
                level.setBlock(pos, next, FLAGS);
                n++;
            }
        }
        return n;
    }

    private static StructureTemplate capture(ServerLevel level, BlockPos min, BlockPos max) {
        StructureTemplate t = new StructureTemplate();
        t.fillFromWorld(level, min, new Vec3i(max.getX() - min.getX() + 1, max.getY() - min.getY() + 1, max.getZ() - min.getZ() + 1), false, List.of());
        return t;
    }

    private static void snapshot(ServerPlayer p, ServerLevel level, BlockPos min, BlockPos max) {
        Deque<Snapshot> h = UNDO.computeIfAbsent(p.getUUID(), k -> new ArrayDeque<>());
        h.push(new Snapshot(level, min, capture(level, min, max)));
        while (h.size() > HISTORY) h.removeLast();
    }

    private static void paste(ServerPlayer p, ServerLevel level, Clip c, BlockPos origin) {
        Vec3i size = c.template.getSize(c.rotation);
        BlockPos zero = c.template.getZeroPositionWithTransform(origin, c.mirror, c.rotation);
        // snapshot where it lands (the box from the transformed zero corner)
        BlockPos a = StructureTemplate.transform(BlockPos.ZERO, c.mirror, c.rotation, BlockPos.ZERO).offset(zero);
        BlockPos b = StructureTemplate.transform(new BlockPos(c.template.getSize().getX() - 1, c.template.getSize().getY() - 1, c.template.getSize().getZ() - 1), c.mirror, c.rotation, BlockPos.ZERO).offset(zero);
        BlockPos sMin = new BlockPos(Math.min(a.getX(), b.getX()), Math.min(a.getY(), b.getY()), Math.min(a.getZ(), b.getZ()));
        BlockPos sMax = sMin.offset(size.getX() - 1, size.getY() - 1, size.getZ() - 1);
        snapshot(p, level, sMin, sMax);
        StructurePlaceSettings settings = new StructurePlaceSettings().setRotation(c.rotation).setMirror(c.mirror).setIgnoreEntities(true);
        c.template.placeInWorld(level, zero, zero, settings, RandomSource.create(), FLAGS);
    }

    static BlockState parse(ServerLevel level, String s) throws CommandSyntaxException {
        return BlockStateParser.parseForBlock(level.registryAccess().lookupOrThrow(Registries.BLOCK), new StringReader(s), false).blockState();
    }

    public static void forget(UUID id) {
        UNDO.remove(id);
        CLIPBOARD.remove(id);
    }
}
