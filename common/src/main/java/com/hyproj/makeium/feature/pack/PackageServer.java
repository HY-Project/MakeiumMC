package com.hyproj.makeium.feature.pack;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.hyproj.makeium.Makeium;
import com.hyproj.makeium.net.Net;
import com.hyproj.makeium.platform.Platform;
import com.hyproj.makeium.server.DatapackStore;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.gamerules.GameRules;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Map packaging: saves the world, then zips its folder — minus player data, Makeium's working files and anything
 * {@link PackageRules} skips — into {@code <game dir>/makeium_exports/<world>-<time>.zip}, ready to upload. The client
 * sends the checklist first ("pkg/check") so the map maker sees game rules, spawn and packs before exporting.
 */
public final class PackageServer {
    private PackageServer() {}

    public static void init() {
        Net.onServer("pkg/check", (p, d) -> Net.sendToPlayer(p, "pkg/result", checklist(p)));
        Net.onServer("pkg/export", PackageServer::export);
    }

    public static void tick(MinecraftServer server) {}

    private static CompoundTag checklist(ServerPlayer p) {
        MinecraftServer server = p.level().getServer();
        CompoundTag out = new CompoundTag();
        out.putString("world", server.getWorldData().getLevelName());
        out.putString("difficulty", server.getWorldData().getDifficulty().getSerializedName());
        var spawn = p.level().getRespawnData().pos();
        out.putString("spawn", spawn.toShortString());
        ListTag packs = new ListTag();
        for (String s : server.getPackRepository().getSelectedIds()) packs.add(StringTag.valueOf(s));
        out.put("packs", packs);
        ListTag own = new ListTag();
        for (String s : DatapackStore.list(server)) own.add(StringTag.valueOf(s));
        out.put("worldPacks", own);
        boolean resources = Files.exists(server.getWorldPath(LevelResource.MAP_RESOURCE_FILE));
        out.putBoolean("resources", resources);
        GameRules rules = p.level().getGameRules();
        out.putString("rules", rules.toString().length() > 2000 ? "" : rules.toString());
        out.putBoolean("check", true);
        return out;
    }

    private static void export(ServerPlayer p, CompoundTag d) {
        MinecraftServer server = p.level().getServer();
        server.saveEverything(true, true, true);
        Path world = server.getWorldPath(LevelResource.ROOT).toAbsolutePath().normalize();
        String name = DatapackStore.PACK_NAME.matcher(d.getStringOr("name", "")).matches() ? d.getStringOr("name", "") : "map";
        Path outDir = Platform.get().gameDir().resolve("makeium_exports");
        String stamp = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmm"));
        Path zip = outDir.resolve(name + "-" + stamp + ".zip");
        String folder = name + "/";
        Thread t = new Thread(() -> {
            CompoundTag result = new CompoundTag();
            try {
                Files.createDirectories(outDir);
                long files = 0;
                try (OutputStream os = Files.newOutputStream(zip); ZipOutputStream zos = new ZipOutputStream(os); Stream<Path> walk = Files.walk(world)) {
                    for (Path f : (Iterable<Path>) walk.filter(Files::isRegularFile)::iterator) {
                        String rel = world.relativize(f).toString().replace('\\', '/');
                        if (PackageRules.skip(rel)) continue;
                        zos.putNextEntry(new ZipEntry(folder + rel));
                        try {
                            Files.copy(f, zos);
                        } catch (IOException e) {
                            // a file locked by the running server (rare): skip it, the world stays loadable
                            Makeium.LOGGER.warn("Skipped {} while packaging: {}", rel, e.toString());
                        }
                        zos.closeEntry();
                        files++;
                    }
                }
                result.putBoolean("ok", true);
                result.putString("path", zip.toString());
                result.putLong("files", files);
                result.putLong("size", Files.size(zip));
            } catch (IOException e) {
                result.putBoolean("ok", false);
                result.putString("error", e.getMessage());
                Makeium.LOGGER.error("Packaging {} failed", world, e);
            }
            server.execute(() -> Net.sendToPlayer(p, "pkg/result", result));
        }, "Makeium packager");
        t.setDaemon(true);
        t.start();
    }
}
