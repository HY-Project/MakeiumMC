package com.hyproj.makeium.server;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.hyproj.makeium.Makeium;
import com.hyproj.makeium.feature.checkpoint.CheckpointServer;
import com.hyproj.makeium.feature.edit.EditServer;
import com.hyproj.makeium.feature.pack.PackageServer;
import com.hyproj.makeium.net.Net;
import com.hyproj.makeium.platform.Platform;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.phys.Vec3;

/** Server side of every Makeium message. Registered once from {@link Makeium#init()}. */
public final class ServerFeatures {
    private ServerFeatures() {}

    public static void init() {
        // ---- hello: the client asks whether this server runs Makeium and whether it may edit ----
        Net.onServerPublic("hello", (p, d) -> sendHello(p));

        // ---- run a command as the player (dashboards, quick actions); same permission as typing it ----
        Net.onServer("cmd/run", (p, d) -> {
            String cmd = d.getStringOr("command", "");
            if (cmd.startsWith("/")) cmd = cmd.substring(1);
            if (cmd.isBlank()) return;
            p.level().getServer().getCommands().performPrefixedCommand(p.createCommandSourceStack(), cmd);
        });

        // ---- displays ----
        Net.onServer("display/place", ServerFeatures::placeDisplay);
        Net.onServer("display/update", ServerFeatures::updateDisplay);
        Net.onServer("display/remove", (p, d) -> {
            Entity e = display(p, d);
            if (e != null) e.discard();
        });
        Net.onServer("display/get", (p, d) -> {
            Entity e = display(p, d);
            if (e == null) return;
            CompoundTag out = new CompoundTag();
            out.putString("uuid", e.getUUID().toString());
            out.put("nbt", EntityNbt.save(e));
            out.putString("type", typeName(e));
            Net.sendToPlayer(p, "display/data", out);
        });

        // ---- datapacks ----
        Net.onServer("dp/list", (p, d) -> {
            MinecraftServer server = p.level().getServer();
            CompoundTag out = new CompoundTag();
            ListTag names = new ListTag();
            for (String n : DatapackStore.list(server)) names.add(StringTag.valueOf(n));
            out.put("packs", names);
            ListTag registries = new ListTag();
            for (String r : DatapackStore.registryCodecs().keySet()) registries.add(StringTag.valueOf(r));
            out.put("registries", registries);
            Net.sendToPlayer(p, "dp/list", out);
        });
        Net.onServer("dp/load", (p, d) -> {
            String name = d.getStringOr("name", "");
            CompoundTag out = new CompoundTag();
            out.putString("name", name);
            try {
                out.put("files", toTag(DatapackStore.read(p.level().getServer(), name)));
            } catch (IOException | IllegalArgumentException e) {
                out.putString("error", e.getMessage());
            }
            Net.sendToPlayer(p, "dp/load", out);
        });
        Net.onServer("dp/save", ServerFeatures::saveDatapack);
        Net.onServer("dp/validate", (p, d) -> {
            List<String> problems = DatapackStore.validate(p.level().getServer(), fromTag(d.getCompoundOrEmpty("files")));
            CompoundTag out = new CompoundTag();
            out.putString("token", d.getStringOr("token", ""));
            out.put("problems", strings(problems));
            Net.sendToPlayer(p, "dp/validate", out);
        });

        EditServer.init();
        CheckpointServer.init();
        PackageServer.init();
        ScoreboardServer.init();
    }

    public static void sendHello(ServerPlayer p) {
        CompoundTag out = new CompoundTag();
        out.putString("version", Platform.get().modVersion(Makeium.MOD_ID));
        out.putBoolean("canEdit", Perms.canEdit(p));
        Net.sendToPlayer(p, "hello", out);
    }

    public static void tick(MinecraftServer server) {
        EditServer.tick(server);
        CheckpointServer.tick(server);
    }

    // ---------------------------------------------------------------- displays

    private static void placeDisplay(ServerPlayer p, CompoundTag d) {
        ServerLevel level = p.level();
        CompoundTag nbt = d.getCompoundOrEmpty("nbt").copy();
        String type = d.getStringOr("type", "text");
        String id = switch (type) {
            case "block" -> "minecraft:block_display";
            case "item" -> "minecraft:item_display";
            default -> "minecraft:text_display";
        };
        nbt.putString("id", id);
        ListTag tags = nbt.getListOrEmpty("Tags").copy();
        if (!tags.contains(StringTag.valueOf("makeium"))) tags.add(StringTag.valueOf("makeium"));
        nbt.put("Tags", tags);
        Vec3 pos = new Vec3(d.getDoubleOr("x", p.getX()), d.getDoubleOr("y", p.getY()), d.getDoubleOr("z", p.getZ()));
        if (pos.distanceToSqr(p.position()) > 256 * 256) return;
        Entity e = EntityNbt.summon(level, nbt, pos, d.getFloatOr("yaw", 0), d.getFloatOr("pitch", 0));
        if (e == null) {
            p.sendSystemMessage(Component.translatable("makeium.display.place_failed"));
            return;
        }
        CompoundTag out = new CompoundTag();
        out.putString("uuid", e.getUUID().toString());
        out.putBoolean("openEditor", d.getBooleanOr("openEditor", false));
        out.put("nbt", EntityNbt.save(e));
        out.putString("type", type);
        Net.sendToPlayer(p, "display/placed", out);
    }

    private static void updateDisplay(ServerPlayer p, CompoundTag d) {
        Entity e = display(p, d);
        if (e == null) return;
        CompoundTag patch = d.getCompoundOrEmpty("nbt");
        if (!patch.isEmpty()) EntityNbt.replaceKeys(e, patch);
        if (d.contains("x")) {
            Vec3 pos = new Vec3(d.getDoubleOr("x", e.getX()), d.getDoubleOr("y", e.getY()), d.getDoubleOr("z", e.getZ()));
            if (pos.distanceToSqr(p.position()) <= 256 * 256) {
                e.snapTo(pos.x, pos.y, pos.z, d.getFloatOr("yaw", e.getYRot()), d.getFloatOr("pitch", e.getXRot()));
            }
        } else if (d.contains("yaw")) {
            e.setYRot(d.getFloatOr("yaw", e.getYRot()));
            e.setXRot(d.getFloatOr("pitch", e.getXRot()));
        }
    }

    private static Entity display(ServerPlayer p, CompoundTag d) {
        try {
            Entity e = p.level().getEntity(UUID.fromString(d.getStringOr("uuid", "")));
            return e instanceof Display ? e : null;
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static String typeName(Entity e) {
        if (e instanceof Display.BlockDisplay) return "block";
        if (e instanceof Display.ItemDisplay) return "item";
        return "text";
    }

    // ---------------------------------------------------------------- datapacks

    private static void saveDatapack(ServerPlayer p, CompoundTag d) {
        MinecraftServer server = p.level().getServer();
        String name = d.getStringOr("name", "");
        CompoundTag out = new CompoundTag();
        out.putString("name", name);
        try {
            List<String> deleted = new ArrayList<>();
            for (Tag t : d.getListOrEmpty("deleted")) deleted.add(t.asString().orElse(""));
            List<String> problems = DatapackStore.write(server, name, d.getStringOr("description", ""), fromTag(d.getCompoundOrEmpty("files")), deleted);
            out.put("problems", strings(problems));
            out.putBoolean("rejoin", DatapackStore.needsRejoin(fromTag(d.getCompoundOrEmpty("files"))));
            boolean reload = d.getBooleanOr("reload", true);
            if (reload) {
                DatapackStore.reload(server, () -> {
                    out.putBoolean("reloaded", true);
                    Net.sendToPlayer(p, "dp/saved", out);
                });
                return;
            }
        } catch (IOException | IllegalArgumentException e) {
            out.putString("error", e.getMessage());
            Makeium.LOGGER.warn("Saving datapack '{}' for {} failed: {}", name, p.getScoreboardName(), e.toString());
        }
        Net.sendToPlayer(p, "dp/saved", out);
    }

    public static CompoundTag toTag(Map<String, String> files) {
        CompoundTag t = new CompoundTag();
        files.forEach(t::putString);
        return t;
    }

    public static Map<String, String> fromTag(CompoundTag t) {
        Map<String, String> out = new LinkedHashMap<>();
        for (String k : t.keySet()) out.put(k, t.getStringOr(k, ""));
        return out;
    }

    public static ListTag strings(List<String> list) {
        ListTag l = new ListTag();
        for (String s : list) l.add(StringTag.valueOf(s));
        return l;
    }
}
