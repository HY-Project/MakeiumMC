package com.hyproj.makeium.feature.datapack;

import java.util.List;

/**
 * An input on a block. The type decides the editor the slot opens (text field, number, selector builder, block or
 * item picker, registry list, rich text, condition builder) — the compiled value is always the slot's text.
 */
public record SlotDef(String name, Type type, String def, List<String> choices, String registry) {
    public enum Type {
        /** Free text, inserted as-is. */
        TEXT,
        NUMBER,
        /** Entity selector (@a[...]) with a builder. */
        SELECTOR,
        /** Coordinates "~ ~ ~" with a "my position" helper. */
        POS,
        /** Block state, e.g. minecraft:oak_stairs[facing=east]. */
        BLOCK,
        /** Item with optional components, e.g. minecraft:diamond_sword[enchantments={sharpness:5}]. */
        ITEM,
        /** One of {@link #choices}. */
        CHOICE,
        /** An id from a built-in registry ({@link #registry}, e.g. "mob_effect"). */
        REGISTRY,
        /** Text shown to players: plain text + colour → JSON/SNBT text component. */
        RICH_TEXT,
        /** An execute condition: "entity @s[tag=x]", "block ~ ~-1 ~ stone", "score ...", "predicate ...". */
        CONDITION,
        /** A full command (raw), validated with the server's command tree. */
        COMMAND,
        /** A function id (namespace:path). */
        FUNCTION,
        /** A scoreboard objective name. */
        OBJECTIVE,
        /** SNBT compound. */
        NBT
    }

    public static SlotDef of(String name, Type type, String def) {
        return new SlotDef(name, type, def, List.of(), "");
    }

    public static SlotDef choice(String name, String def, String... choices) {
        return new SlotDef(name, Type.CHOICE, def, List.of(choices), "");
    }

    public static SlotDef registry(String name, String registry, String def) {
        return new SlotDef(name, Type.REGISTRY, def, List.of(), registry);
    }
}
