package com.hyproj.makeium.feature.link;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.google.gson.JsonObject;
import com.hyproj.makeium.feature.datapack.BlockCompiler;

/**
 * World links → the "makeium_links" datapack: right-clicking a block (an invisible interaction entity over it, tagged
 * {@code mk_link_<id>}) runs a function, through an advancement as vanilla allows.
 */
public final class LinkPack {
    public static final String PACK = "makeium_links";
    public static final String NS = "makeium_links";

    public record Link(String id, String function) {}

    private LinkPack() {}

    public static String tag(String id) {
        return "mk_link_" + BlockCompiler.sanitizePath(id).replace('/', '_');
    }

    public static Map<String, String> compile(List<Link> links) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Link l : links) {
            String id = BlockCompiler.sanitizePath(l.id()).replace('/', '_');
            JsonObject adv = new JsonObject();
            JsonObject crit = new JsonObject();
            JsonObject c = new JsonObject();
            c.addProperty("trigger", "minecraft:player_interacted_with_entity");
            c.add("conditions", com.hyproj.makeium.feature.datapack.Predicates.interactedWithTagged(tag(id)));
            crit.add("click", c);
            adv.add("criteria", crit);
            JsonObject rewards = new JsonObject();
            rewards.addProperty("function", NS + ":" + id);
            adv.add("rewards", rewards);
            out.put("data/" + NS + "/advancement/" + id + ".json", adv.toString());
            String fn = l.function().strip();
            out.put("data/" + NS + "/function/" + id + ".mcfunction",
                    "advancement revoke @s only " + NS + ":" + id + "\n" + (fn.isEmpty() ? "" : "function " + fn + "\n"));
        }
        return out;
    }

    /** The interaction entity that makes a block clickable (summoned at the block's corner). */
    public static String summon(String id, int x, int y, int z) {
        return "summon minecraft:interaction " + (x + 0.5) + " " + (y - 0.005) + " " + (z + 0.5)
                + " {width:1.01f,height:1.01f,response:1b,Tags:[\"" + tag(id) + "\"]}";
    }
}
