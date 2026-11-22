package com.hyproj.makeium.server;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.google.gson.JsonElement;
import com.google.gson.JsonParser;
import com.hyproj.makeium.Makeium;
import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.JsonOps;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.functions.CommandFunction;
import net.minecraft.resources.Identifier;
import net.minecraft.resources.RegistryDataLoader;
import net.minecraft.resources.RegistryOps;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.packs.repository.PackRepository;
import net.minecraft.tags.TagFile;
import net.minecraft.world.level.storage.LevelResource;

/**
 * Reads, validates and writes datapacks under {@code <world>/datapacks/<name>/}. Every path a client sends is sanitised:
 * pack names are {@code [a-z0-9_.-]}, file paths are relative, forward-slash, without {@code ..}, and must stay inside the
 * pack directory. Function files are compiled with the server's own dispatcher and JSON files are decoded with the real
 * registry codecs, so the editor reports exactly what {@code /reload} would reject.
 */
public final class DatapackStore {
    public static final Pattern PACK_NAME = Pattern.compile("[a-z0-9_.\\-]{1,64}");
    private static final Pattern FILE_PATH = Pattern.compile("[a-z0-9_.\\-/]{1,240}");
    /** Datapack format for 26.3 (version.json pack_version.data_major/minor). */
    public static final int DATA_MAJOR = 121;
    public static final int DATA_MINOR = 0;

    private DatapackStore() {}

    public static Path root(MinecraftServer server) {
        return server.getWorldPath(LevelResource.DATAPACK_DIR);
    }

    public static Path packDir(MinecraftServer server, String name) {
        if (!PACK_NAME.matcher(name).matches()) throw new IllegalArgumentException("Invalid pack name: " + name);
        Path root = root(server).toAbsolutePath().normalize();
        Path dir = root.resolve(name).normalize();
        if (!dir.startsWith(root) || dir.equals(root)) throw new IllegalArgumentException("Invalid pack name: " + name);
        return dir;
    }

    /** Resolves a pack-relative file path, refusing anything that would leave the pack. */
    public static Path file(Path packDir, String relative) {
        String p = relative.replace('\\', '/');
        if (p.startsWith("/") || p.contains("..") || p.contains("//") || !FILE_PATH.matcher(p).matches()) {
            throw new IllegalArgumentException("Invalid file path: " + relative);
        }
        Path f = packDir.resolve(p).normalize();
        if (!f.startsWith(packDir)) throw new IllegalArgumentException("Invalid file path: " + relative);
        return f;
    }

    /** Folder names directly under the world's datapacks directory. */
    public static List<String> list(MinecraftServer server) {
        List<String> out = new ArrayList<>();
        try (Stream<Path> s = Files.list(root(server))) {
            s.filter(Files::isDirectory).map(p -> p.getFileName().toString())
                    .filter(n -> PACK_NAME.matcher(n).matches()).sorted().forEach(out::add);
        } catch (IOException ignored) {
            // no datapacks directory yet
        }
        return out;
    }

    /** All text files of a pack (path relative to the pack root, forward slashes). */
    public static Map<String, String> read(MinecraftServer server, String name) throws IOException {
        Path dir = packDir(server, name);
        Map<String, String> out = new LinkedHashMap<>();
        if (!Files.isDirectory(dir)) return out;
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path f : (Iterable<Path>) s.filter(Files::isRegularFile)::iterator) {
                String rel = dir.relativize(f).toString().replace('\\', '/');
                String lower = rel.toLowerCase(Locale.ROOT);
                if (!(lower.endsWith(".json") || lower.endsWith(".mcfunction") || lower.endsWith(".mcmeta") || lower.endsWith(".txt"))) continue;
                if (Files.size(f) > 4 * 1024 * 1024) continue;
                out.put(rel, Files.readString(f, StandardCharsets.UTF_8));
            }
        }
        return out;
    }

    /**
     * Replaces the pack's generated content: writes {@code files}, deletes {@code deleted}, and adds pack.mcmeta when
     * the pack has none. Returns the validation problems (empty = clean). Files are written even with problems so the
     * map maker never loses work; /reload then skips just the broken ones, as vanilla does.
     */
    public static List<String> write(MinecraftServer server, String name, String description, Map<String, String> files, Collection<String> deleted) throws IOException {
        Path dir = packDir(server, name);
        Files.createDirectories(dir);
        for (String rel : deleted) {
            Path f = file(dir, rel);
            Files.deleteIfExists(f);
        }
        // A broken registry JSON (recipe, advancement, dialog, ...) makes the whole /reload fail, so those are not
        // written; broken functions are written (vanilla skips just that function and the map maker keeps the text).
        List<String> problems = new ArrayList<>();
        for (Map.Entry<String, String> e : files.entrySet()) {
            List<String> own = validate(server, Map.of(e.getKey(), e.getValue()));
            problems.addAll(own);
            if (!own.isEmpty() && e.getKey().endsWith(".json")) {
                problems.add(e.getKey() + ": not saved (it would stop /reload)");
                continue;
            }
            Path f = file(dir, e.getKey());
            Files.createDirectories(f.getParent());
            Files.writeString(f, e.getValue(), StandardCharsets.UTF_8);
        }
        Path meta = dir.resolve("pack.mcmeta");
        if (!files.containsKey("pack.mcmeta") && !Files.exists(meta)) {
            Files.writeString(meta, packMeta(description), StandardCharsets.UTF_8);
        }
        return problems;
    }

    public static String packMeta(String description) {
        String desc = description == null || description.isBlank() ? "Made with Makeium" : description;
        return "{\n  \"pack\": {\n    \"description\": " + new com.google.gson.JsonPrimitive(desc)
                + ",\n    \"min_format\": [" + DATA_MAJOR + ", " + DATA_MINOR + "],\n    \"max_format\": [" + DATA_MAJOR + ", " + DATA_MINOR + "]\n  }\n}\n";
    }

    /** Validates function and JSON files the way /reload would read them. */
    public static List<String> validate(MinecraftServer server, Map<String, String> files) {
        List<String> problems = new ArrayList<>();
        Map<String, Codec<?>> codecs = registryCodecs();
        RegistryOps<JsonElement> ops = RegistryOps.create(JsonOps.INSTANCE, server.registryAccess());
        CommandSourceStack compile = Commands.createCompilationContext(server.getFunctionCompilationPermissions());
        for (Map.Entry<String, String> e : files.entrySet()) {
            String path = e.getKey();
            String[] parts = path.split("/");
            // data/<namespace>/<registry path...>/<name>.<ext>
            if (parts.length < 4 || !parts[0].equals("data")) continue;
            String ns = parts[1];
            String rest = String.join("/", Arrays.copyOfRange(parts, 2, parts.length));
            try {
                if (rest.startsWith("function/") && rest.endsWith(".mcfunction")) {
                    String fn = rest.substring("function/".length(), rest.length() - ".mcfunction".length());
                    Identifier id = Identifier.fromNamespaceAndPath(ns, fn);
                    CommandFunction.fromLines(id, server.getCommands().getDispatcher(), compile, e.getValue().lines().toList());
                } else if (rest.endsWith(".json")) {
                    JsonElement json = JsonParser.parseString(e.getValue());
                    Codec<?> codec = rest.startsWith("tags/") ? TagFile.CODEC : codecFor(codecs, rest);
                    if (codec != null) {
                        DataResult<?> r = codec.parse(ops, json);
                        r.error().ifPresent(err -> problems.add(path + ": " + err.message()));
                    }
                }
            } catch (IllegalArgumentException | com.google.gson.JsonParseException ex) {
                problems.add(path + ": " + ex.getMessage());
            }
        }
        return problems;
    }

    private static Codec<?> codecFor(Map<String, Codec<?>> codecs, String rest) {
        // Longest registry folder that prefixes the path (worldgen/... registries have two segments).
        String best = null;
        for (String folder : codecs.keySet()) {
            if (rest.startsWith(folder + "/") && (best == null || folder.length() > best.length())) best = folder;
        }
        return best == null ? null : codecs.get(best);
    }

    /** Registry folder (e.g. "recipe", "worldgen/biome", "dialog") → element codec, for every data-driven registry. */
    public static Map<String, Codec<?>> registryCodecs() {
        Map<String, Codec<?>> out = new LinkedHashMap<>();
        for (List<RegistryDataLoader.RegistryData<?>> list : List.of(RegistryDataLoader.WORLD_REGISTRIES, RegistryDataLoader.DIMENSION_REGISTRIES, RegistryDataLoader.RELOADABLE_REGISTRIES)) {
            for (RegistryDataLoader.RegistryData<?> d : list) {
                out.put(d.key().identifier().getPath(), d.elementCodec());
            }
        }
        return out;
    }

    /**
     * Whether any of these files belongs to a world registry (dialog, enchantment, damage_type, worldgen, variants...):
     * those are read when the world loads, so /reload does not apply them.
     */
    public static boolean needsRejoin(Map<String, String> files) {
        java.util.Set<String> world = new java.util.HashSet<>();
        for (List<RegistryDataLoader.RegistryData<?>> list : List.of(RegistryDataLoader.WORLD_REGISTRIES, RegistryDataLoader.DIMENSION_REGISTRIES)) {
            for (RegistryDataLoader.RegistryData<?> d : list) world.add(d.key().identifier().getPath());
        }
        for (String path : files.keySet()) {
            String[] parts = path.split("/");
            if (parts.length < 4 || !parts[0].equals("data") || !path.endsWith(".json")) continue;
            String rest = String.join("/", Arrays.copyOfRange(parts, 2, parts.length));
            for (String folder : world) if (rest.startsWith(folder + "/")) return true;
        }
        return false;
    }

    /** Re-discovers packs (so a new folder is picked up and enabled) and reloads, like {@code /reload}. */
    public static void reload(MinecraftServer server, Runnable done) {
        PackRepository repo = server.getPackRepository();
        repo.reload();
        List<String> selected = new ArrayList<>(repo.getSelectedIds());
        Collection<String> disabled = server.getWorldData().getDataConfiguration().dataPacks().getDisabled();
        for (String id : repo.getAvailableIds()) {
            if (!disabled.contains(id) && !selected.contains(id)) selected.add(id);
        }
        server.reloadResources(selected).whenComplete((v, t) -> server.execute(() -> {
            if (t != null) Makeium.LOGGER.warn("Datapack reload failed", t);
            done.run();
        }));
    }
}
