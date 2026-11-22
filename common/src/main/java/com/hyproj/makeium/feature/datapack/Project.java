package com.hyproj.makeium.feature.datapack;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

/**
 * A datapack being built in Makeium: pack folder name, namespace, description, the block-coding scripts, extra files
 * written as-is (JSON registry files, hand-written functions) and the list of files the last save generated (so a
 * removed script also removes its old function). Saved inside the pack as {@code makeium/project.json}.
 */
public final class Project {
    public static final String FILE = "makeium/project.json";
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    public String pack = "my_map";
    public String namespace = "my_map";
    public String description = "Made with Makeium";
    public final List<Script> scripts = new ArrayList<>();
    /** pack-relative path → content (data/<ns>/recipe/x.json, data/<ns>/function/y.mcfunction, ...). */
    public final Map<String, String> files = new TreeMap<>();
    public final List<String> generated = new ArrayList<>();

    public String toJson() {
        JsonObject o = new JsonObject();
        o.addProperty("format", 1);
        o.addProperty("pack", pack);
        o.addProperty("namespace", namespace);
        o.addProperty("description", description);
        JsonArray s = new JsonArray();
        for (Script sc : scripts) s.add(sc.toJson());
        o.add("scripts", s);
        JsonArray gen = new JsonArray();
        for (String g : generated) gen.add(g);
        o.add("generated", gen);
        return GSON.toJson(o);
    }

    public static Project fromJson(String json) {
        Project p = new Project();
        JsonObject o = JsonParser.parseString(json).getAsJsonObject();
        if (o.has("pack")) p.pack = o.get("pack").getAsString();
        if (o.has("namespace")) p.namespace = o.get("namespace").getAsString();
        if (o.has("description")) p.description = o.get("description").getAsString();
        if (o.has("scripts")) for (JsonElement e : o.getAsJsonArray("scripts")) p.scripts.add(Script.fromJson(e.getAsJsonObject()));
        if (o.has("generated")) for (JsonElement e : o.getAsJsonArray("generated")) p.generated.add(e.getAsString());
        return p;
    }

    /**
     * Rebuilds a project from a pack's files: Makeium's own project file when present, otherwise every function
     * becomes a "function" script of raw command blocks and every other file is kept as an extra file.
     */
    public static Project fromPackFiles(String pack, Map<String, String> packFiles) {
        Project p;
        String json = packFiles.get(FILE);
        if (json != null) {
            p = fromJson(json);
            p.pack = pack;
            for (Map.Entry<String, String> e : packFiles.entrySet()) {
                String k = e.getKey();
                if (k.equals(FILE) || k.equals("pack.mcmeta") || p.generated.contains(k)) continue;
                p.files.put(k, e.getValue());
            }
            return p;
        }
        p = new Project();
        p.pack = pack;
        String ns = null;
        double y = 20;
        for (Map.Entry<String, String> e : packFiles.entrySet()) {
            String k = e.getKey();
            if (k.equals("pack.mcmeta")) continue;
            String[] parts = k.split("/");
            if (parts.length >= 4 && parts[0].equals("data") && parts[2].equals("function") && k.endsWith(".mcfunction") && !parts[1].equals("minecraft")) {
                if (ns == null) ns = parts[1];
                String fn = k.substring(("data/" + parts[1] + "/function/").length(), k.length() - ".mcfunction".length());
                Script s = new Script(20, y);
                Node hat = Node.create(BlockDefs.get("ev_function"));
                hat.values.put("name", parts[1].equals(ns) ? fn : parts[1] + ":" + fn);
                s.nodes.add(hat);
                for (String line : e.getValue().split("\n")) {
                    String l = line.strip();
                    if (l.isEmpty()) continue;
                    Node n;
                    if (l.startsWith("#")) {
                        n = Node.create(BlockDefs.get("comment"));
                        n.values.put("text", l.substring(1).strip());
                    } else {
                        n = Node.create(BlockDefs.get("raw"));
                        n.values.put("command", l);
                    }
                    s.nodes.add(n);
                }
                p.scripts.add(s);
                y += 40 + 20 * s.nodes.size();
            } else {
                p.files.put(k, e.getValue());
            }
        }
        if (ns != null) p.namespace = ns;
        return p;
    }

    public Map<String, String> allFilesForSave(Map<String, String> compiled) {
        Map<String, String> out = new LinkedHashMap<>(files);
        out.putAll(compiled);
        return out;
    }
}
