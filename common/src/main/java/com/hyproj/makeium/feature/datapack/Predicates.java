package com.hyproj.makeium.feature.datapack;

import com.google.gson.JsonObject;

/**
 * 26.3 predicate JSON shapes (loot conditions are {@code {"type": ...}}; entity predicates are maps of namespaced
 * sub-predicates such as {@code "minecraft:nbt"}).
 */
public final class Predicates {
    private Predicates() {}

    /** An entity condition matching entities that carry {@code tag}: for advancement trigger "entity" fields. */
    public static JsonObject taggedEntity(String tag) {
        JsonObject predicate = new JsonObject();
        predicate.addProperty("minecraft:nbt", "{Tags:[\"" + tag + "\"]}");
        JsonObject cond = new JsonObject();
        cond.addProperty("type", "minecraft:entity_properties");
        cond.addProperty("entity", "this");
        cond.add("predicate", predicate);
        return cond;
    }

    /** {@code {"entity": <tagged entity>}} — the conditions of a player_interacted_with_entity trigger. */
    public static JsonObject interactedWithTagged(String tag) {
        JsonObject c = new JsonObject();
        c.add("entity", taggedEntity(tag));
        return c;
    }
}
