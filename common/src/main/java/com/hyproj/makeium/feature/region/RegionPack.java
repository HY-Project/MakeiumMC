package com.hyproj.makeium.feature.region;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.hyproj.makeium.feature.datapack.BlockCompiler;

/**
 * Trigger regions → the "makeium_regions" datapack. Each tick, players inside a region's box get an "in" tag; the
 * change against last tick's "was" tag fires enter/leave, and "inside" runs every tick. Each event calls the region's
 * own command (if any) and the function tag {@code #makeium:region/<name>/<event>} — the block-coding "region"
 * event adds functions to that tag, so regions and scripts stay decoupled.
 */
public final class RegionPack {
    public static final String PACK = "makeium_regions";
    public static final String NS = "makeium_regions";

    public record Region(String name, String dimension, int[] min, int[] max, String selector, String onEnter, String onLeave, String whileInside) {}

    private RegionPack() {}

    public static Map<String, String> compile(List<Region> regions) {
        Map<String, String> out = new LinkedHashMap<>();
        List<String> tick = new ArrayList<>();
        tick.add("# Makeium trigger regions (generated)");
        for (Region r : regions) {
            String n = BlockCompiler.sanitizePath(r.name()).replace('/', '_');
            String in = "mkr_" + n + "_in", was = "mkr_" + n + "_was";
            int dx = r.max()[0] - r.min()[0], dy = r.max()[1] - r.min()[1], dz = r.max()[2] - r.min()[2];
            String sel = r.selector() == null || r.selector().isBlank() ? "@a" : r.selector().strip();
            String box = "x=" + r.min()[0] + ",y=" + r.min()[1] + ",z=" + r.min()[2] + ",dx=" + dx + ",dy=" + dy + ",dz=" + dz;
            String inSel = sel.endsWith("]") ? sel.substring(0, sel.length() - 1) + "," + box + "]" : sel + "[" + box + "]";
            String dim = r.dimension() == null || r.dimension().isBlank() ? "minecraft:overworld" : r.dimension();
            tick.add("execute in " + dim + " as " + inSel + " run tag @s add " + in);
            tick.add("execute as @a[tag=" + in + ",tag=!" + was + "] at @s run function " + NS + ":" + n + "/enter");
            tick.add("execute as @a[tag=!" + in + ",tag=" + was + "] at @s run function " + NS + ":" + n + "/leave");
            tick.add("execute as @a[tag=" + in + "] at @s run function " + NS + ":" + n + "/inside");
            tick.add("tag @a[tag=" + in + "] add " + was);
            tick.add("tag @a[tag=!" + in + "] remove " + was);
            tick.add("tag @a remove " + in);
            event(out, n, "enter", r.onEnter());
            event(out, n, "leave", r.onLeave());
            event(out, n, "inside", r.whileInside());
        }
        out.put("data/" + NS + "/function/tick.mcfunction", String.join("\n", tick) + "\n");
        out.put("data/minecraft/tags/function/tick.json", "{\"values\":[\"" + NS + ":tick\"]}");
        return out;
    }

    private static void event(Map<String, String> out, String n, String event, String command) {
        StringBuilder sb = new StringBuilder();
        if (command != null && !command.isBlank()) {
            for (String line : command.split("\n")) {
                String l = line.strip();
                if (l.startsWith("/")) l = l.substring(1);
                if (!l.isEmpty()) sb.append(l).append('\n');
            }
        }
        sb.append("function #makeium:region/").append(n).append('/').append(event).append('\n');
        out.put("data/" + NS + "/function/" + n + "/" + event + ".mcfunction", sb.toString());
        // the tag must exist (possibly empty) for the call above to load; other packs add to it
        out.put("data/makeium/tags/function/region/" + n + "/" + event + ".json", "{\"replace\":false,\"values\":[]}");
    }
}
