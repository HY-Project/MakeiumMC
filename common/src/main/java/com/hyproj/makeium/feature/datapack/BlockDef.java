package com.hyproj.makeium.feature.datapack;

import java.util.List;

/**
 * One kind of block in the palette.
 *
 * <ul>
 *   <li>{@link Shape#HAT} — an event; starts a script (compiled to a function plus the hook that calls it).</li>
 *   <li>{@link Shape#STATEMENT} — one command, from {@link #template} with {@code {slot}} placeholders.</li>
 *   <li>{@link Shape#C} — wraps a body: the template's {@code {body}} becomes {@code function <sub-function>}.</li>
 *   <li>{@link Shape#C2} — if/else: two bodies, compiled specially.</li>
 * </ul>
 * The label comes from the language file ({@code makeium.block.<id>}) and places slots with {@code {slot}} tokens.
 */
public record BlockDef(String id, Category category, Shape shape, String template, List<SlotDef> slots) {
    public enum Shape { HAT, STATEMENT, C, C2 }

    public enum Category {
        EVENTS(0xFFE8B931),
        CONTROL(0xFFE0902F),
        PLAYER(0xFF4A90D9),
        ENTITY(0xFF5C6BC0),
        WORLD(0xFF3C8527),
        ITEMS(0xFF8D6E63),
        SCORE(0xFFD9443A),
        TEXT(0xFFB36AD9),
        EFFECTS(0xFF26A69A),
        BOSSBAR(0xFFEC407A),
        DATA(0xFF78909C),
        FUNCTIONS(0xFFD96A8F),
        COMMANDS(0xFF607D8B);

        public final int color;

        Category(int color) {
            this.color = color;
        }
    }

    public SlotDef slot(String name) {
        for (SlotDef s : slots) if (s.name().equals(name)) return s;
        return null;
    }
}
