package com.hyproj.makeium.net;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

import com.hyproj.makeium.Makeium;
import com.hyproj.makeium.platform.Platform;
import com.hyproj.makeium.server.Perms;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtAccounter;
import net.minecraft.nbt.NbtIo;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

/**
 * Message layer over {@link MkPayload}: {@code op} + CompoundTag, gzip-compressed and split into chunks that fit a
 * single custom payload (32 KiB client to server, 1 MiB server to client). Handlers run on the receiving main thread.
 */
public final class Net {
    /** Client-to-server payloads are capped at 32767 bytes by vanilla; leave room for the header. */
    private static final int C2S_CHUNK = 30_000;
    private static final int S2C_CHUNK = 900_000;
    /** Largest reassembled message accepted (compressed bytes). */
    private static final int MAX_MESSAGE = 16 * 1024 * 1024;

    public interface ServerHandler {
        void handle(ServerPlayer player, CompoundTag data);
    }

    private record Server(ServerHandler handler, boolean editorOnly) {}

    private static final Map<String, Server> SERVER = new HashMap<>();
    private static final Map<String, Consumer<CompoundTag>> CLIENT = new HashMap<>();
    private static final AtomicInteger NEXT_TRANSFER = new AtomicInteger(1);

    /** Partial messages: key = sender (player UUID or "server") + transfer id. */
    private static final Map<String, Partial> PARTIAL = new ConcurrentHashMap<>();

    private static final class Partial {
        final byte[][] parts;
        int received;
        int size;

        Partial(int count) {
            parts = new byte[count][];
        }
    }

    private Net() {}

    /** A server handler that only map editors (op level 2 / cheats on) may trigger. */
    public static void onServer(String op, ServerHandler handler) {
        SERVER.put(op, new Server(handler, true));
    }

    /** A server handler anyone may trigger (e.g. the hello ping). */
    public static void onServerPublic(String op, ServerHandler handler) {
        SERVER.put(op, new Server(handler, false));
    }

    public static void onClient(String op, Consumer<CompoundTag> handler) {
        CLIENT.put(op, handler);
    }

    // ---------------------------------------------------------------- sending

    public static void sendToServer(String op, CompoundTag data) {
        byte[] bytes = compress(data);
        int transfer = NEXT_TRANSFER.getAndIncrement();
        int count = Math.max(1, (bytes.length + C2S_CHUNK - 1) / C2S_CHUNK);
        for (int i = 0; i < count; i++) {
            byte[] part = Arrays.copyOfRange(bytes, i * C2S_CHUNK, Math.min(bytes.length, (i + 1) * C2S_CHUNK));
            Platform.get().sendToServer(new MkPayload.C2S(op, transfer, i, count, part));
        }
    }

    public static void sendToServer(String op) {
        sendToServer(op, new CompoundTag());
    }

    public static void sendToPlayer(ServerPlayer player, String op, CompoundTag data) {
        if (!Platform.get().hasMakeium(player)) return;
        byte[] bytes = compress(data);
        int transfer = NEXT_TRANSFER.getAndIncrement();
        int count = Math.max(1, (bytes.length + S2C_CHUNK - 1) / S2C_CHUNK);
        for (int i = 0; i < count; i++) {
            byte[] part = Arrays.copyOfRange(bytes, i * S2C_CHUNK, Math.min(bytes.length, (i + 1) * S2C_CHUNK));
            Platform.get().sendToPlayer(player, new MkPayload.S2C(op, transfer, i, count, part));
        }
    }

    // ---------------------------------------------------------------- receiving (the loader calls these on the main thread)

    public static void receiveOnServer(ServerPlayer player, MkPayload.C2S p) {
        CompoundTag data = collect(player.getUUID().toString(), p.transfer(), p.index(), p.count(), p.data());
        if (data == null) return;
        Server s = SERVER.get(p.op());
        if (s == null) {
            Makeium.LOGGER.warn("Unknown Makeium message '{}' from {}", p.op(), player.getScoreboardName());
            return;
        }
        if (s.editorOnly() && !Perms.canEdit(player)) {
            player.sendSystemMessage(Component.translatable("makeium.error.no_permission"));
            return;
        }
        try {
            s.handler().handle(player, data);
        } catch (RuntimeException e) {
            Makeium.LOGGER.error("Makeium message '{}' from {} failed", p.op(), player.getScoreboardName(), e);
            player.sendSystemMessage(Component.translatable("makeium.error.internal", String.valueOf(e.getMessage())));
        }
    }

    public static void receiveOnClient(MkPayload.S2C p) {
        CompoundTag data = collect("server", p.transfer(), p.index(), p.count(), p.data());
        if (data == null) return;
        Consumer<CompoundTag> h = CLIENT.get(p.op());
        if (h == null) {
            Makeium.LOGGER.warn("Unknown Makeium message '{}' from the server", p.op());
            return;
        }
        try {
            h.accept(data);
        } catch (RuntimeException e) {
            Makeium.LOGGER.error("Makeium message '{}' from the server failed", p.op(), e);
        }
    }

    public static void forget(UUID player) {
        String prefix = player.toString() + "#";
        PARTIAL.keySet().removeIf(k -> k.startsWith(prefix));
    }

    private static CompoundTag collect(String sender, int transfer, int index, int count, byte[] data) {
        if (count <= 0 || count > MAX_MESSAGE / 1024 || index < 0 || index >= count) return null;
        byte[] whole;
        if (count == 1) {
            whole = data;
        } else {
            String key = sender + "#" + transfer;
            Partial partial = PARTIAL.computeIfAbsent(key, k -> new Partial(count));
            if (partial.parts.length != count || partial.parts[index] != null) {
                PARTIAL.remove(key);
                return null;
            }
            partial.parts[index] = data;
            partial.received++;
            partial.size += data.length;
            if (partial.size > MAX_MESSAGE) {
                PARTIAL.remove(key);
                Makeium.LOGGER.warn("Dropped an oversized Makeium message from {}", sender);
                return null;
            }
            if (partial.received < count) return null;
            PARTIAL.remove(key);
            ByteArrayOutputStream out = new ByteArrayOutputStream(partial.size);
            for (byte[] b : partial.parts) out.writeBytes(b);
            whole = out.toByteArray();
        }
        try {
            return NbtIo.readCompressed(new ByteArrayInputStream(whole), NbtAccounter.create(64L * 1024 * 1024));
        } catch (IOException | RuntimeException e) {
            Makeium.LOGGER.warn("Could not read a Makeium message from {}: {}", sender, e.toString());
            return null;
        }
    }

    private static byte[] compress(CompoundTag tag) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            NbtIo.writeCompressed(tag, out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Could not encode a Makeium message", e);
        }
    }
}
