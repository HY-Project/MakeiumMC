package com.hyproj.makeium.client.resourcepack;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.hyproj.makeium.Makeium;
import com.hyproj.makeium.feature.datapack.BlockCompiler;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.world.level.storage.LevelResource;
import org.jspecify.annotations.Nullable;

/**
 * A resource pack being made in Makeium: a folder in {@code resourcepacks/} that is enabled on the client and reloaded
 * after every save, so changes show up in the world at once. Its own content lives in the namespace {@link #ns}; overrides
 * of vanilla textures go under {@code assets/minecraft/}.
 */
public final class RpProject {
    /** Resource pack format for 26.3 (version.json pack_version.resource_major/minor). */
    public static final int RESOURCE_MAJOR = 97, RESOURCE_MINOR = 1;

    private static @Nullable RpProject current;

    public final String name;
    public final String ns;
    public final Path dir;

    private RpProject(String name) {
        this.name = name;
        this.ns = BlockCompiler.sanitize(name);
        this.dir = root().resolve(name);
    }

    public static Path root() {
        return Minecraft.getInstance().getResourcePackDirectory();
    }

    public static @Nullable RpProject current() {
        return current;
    }

    /** Folder packs in resourcepacks/ (zips are not editable). */
    public static List<String> list() {
        List<String> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(root())) {
            s.filter(p -> Files.isDirectory(p) && Files.exists(p.resolve("pack.mcmeta"))).map(p -> p.getFileName().toString()).sorted().forEach(out::add);
        } catch (IOException ignored) {
            // none
        }
        return out;
    }

    public static RpProject open(String name) throws IOException {
        RpProject p = new RpProject(name);
        if (!Files.exists(p.dir.resolve("pack.mcmeta"))) {
            Files.createDirectories(p.dir);
            p.writeMeta("Made with Makeium");
        }
        current = p;
        return p;
    }

    public String description() {
        try {
            JsonObject o = JsonParser.parseString(Files.readString(dir.resolve("pack.mcmeta"))).getAsJsonObject();
            var d = o.getAsJsonObject("pack").get("description");
            return d.isJsonPrimitive() ? d.getAsString() : d.toString();
        } catch (Exception e) {
            return "";
        }
    }

    public void writeMeta(String description) throws IOException {
        JsonObject pack = new JsonObject();
        pack.addProperty("description", description);
        com.google.gson.JsonArray min = new com.google.gson.JsonArray();
        min.add(RESOURCE_MAJOR);
        min.add(RESOURCE_MINOR);
        pack.add("min_format", min);
        pack.add("max_format", min.deepCopy());
        JsonObject root = new JsonObject();
        root.add("pack", pack);
        Files.writeString(dir.resolve("pack.mcmeta"), new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create().toJson(root) + "\n");
    }

    // ---------------------------------------------------------------- files

    /** Resolves a pack-relative path, refusing anything outside the pack. */
    public Path file(String relative) {
        Path f = dir.resolve(relative.replace('\\', '/')).normalize();
        if (!f.startsWith(dir.normalize())) throw new IllegalArgumentException("Outside the pack: " + relative);
        return f;
    }

    public void writeText(String relative, String content) throws IOException {
        Path f = file(relative);
        Files.createDirectories(f.getParent());
        Files.writeString(f, content, StandardCharsets.UTF_8);
    }

    public String readText(String relative) {
        try {
            return Files.readString(file(relative), StandardCharsets.UTF_8);
        } catch (IOException e) {
            return null;
        }
    }

    public void writeImage(String relative, NativeImage image) throws IOException {
        Path f = file(relative);
        Files.createDirectories(f.getParent());
        image.writeToFile(f.toFile());
    }

    public void copyIn(Path source, String relative) throws IOException {
        Path f = file(relative);
        Files.createDirectories(f.getParent());
        Files.copy(source, f, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    public boolean exists(String relative) {
        return Files.exists(file(relative));
    }

    /** All files (relative, forward slashes). */
    public List<String> files() {
        List<String> out = new ArrayList<>();
        try (Stream<Path> s = Files.walk(dir)) {
            s.filter(Files::isRegularFile).map(p -> dir.relativize(p).toString().replace('\\', '/')).sorted().forEach(out::add);
        } catch (IOException ignored) {
            // empty
        }
        return out;
    }

    public void delete(String relative) throws IOException {
        Files.deleteIfExists(file(relative));
    }

    // ---------------------------------------------------------------- applying

    public String packId() {
        return "file/" + name;
    }

    /** Selects the pack (once) and reloads resources so edits appear in the world. */
    public void apply() {
        Minecraft mc = Minecraft.getInstance();
        PackRepository repo = mc.getResourcePackRepository();
        repo.reload();
        if (!repo.getSelectedIds().contains(packId())) {
            repo.addPack(packId());
            mc.options.updateResourcePacks(repo);
        } else {
            mc.reloadResourcePacks();
        }
    }

    public Path exportZip(Path target) throws IOException {
        Files.createDirectories(target.getParent());
        try (OutputStream os = Files.newOutputStream(target); ZipOutputStream zos = new ZipOutputStream(os)) {
            for (String rel : files()) {
                zos.putNextEntry(new ZipEntry(rel));
                Files.copy(dir.resolve(rel), zos);
                zos.closeEntry();
            }
        }
        return target;
    }

    /** Singleplayer: writes the pack as the world's own resource pack (players get it when they open the map). */
    public Path exportToWorld() throws IOException {
        var server = Minecraft.getInstance().getSingleplayerServer();
        if (server == null) throw new IOException("Not in a singleplayer world");
        return exportZip(server.getWorldPath(LevelResource.MAP_RESOURCE_FILE));
    }

    public Path exportToDownloads() throws IOException {
        Path out = Minecraft.getInstance().gameDirectory.toPath().resolve("makeium_exports").resolve(name.toLowerCase(Locale.ROOT) + ".zip");
        return exportZip(out);
    }

    public static void logFail(String what, Exception e) {
        Makeium.LOGGER.warn("Resource pack {} failed: {}", what, e.toString());
    }
}
