package com.hyproj.makeium.feature.display;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.Identifier;
import org.jspecify.annotations.Nullable;

/**
 * Text of a text display as a list of styled segments (what the editor shows) ↔ a vanilla Component (what the entity
 * stores). Each segment carries its own colour, decorations and font, so one sign can mix fonts and colours.
 */
public final class RichText {
    public static final class Segment {
        public String text = "";
        /** RGB, or -1 for the default (white). */
        public int color = -1;
        public boolean bold, italic, underlined, strikethrough, obfuscated;
        /** Font id, or null for minecraft:default. */
        public @Nullable Identifier font;

        public Segment() {}

        public Segment(String text) {
            this.text = text;
        }

        public Segment copy() {
            Segment s = new Segment(text);
            s.color = color;
            s.bold = bold;
            s.italic = italic;
            s.underlined = underlined;
            s.strikethrough = strikethrough;
            s.obfuscated = obfuscated;
            s.font = font;
            return s;
        }

        public Style style() {
            Style st = Style.EMPTY;
            if (color >= 0) st = st.withColor(TextColor.fromRgb(color));
            if (bold) st = st.withBold(true);
            if (italic) st = st.withItalic(true);
            if (underlined) st = st.withUnderlined(true);
            if (strikethrough) st = st.withStrikethrough(true);
            if (obfuscated) st = st.withObfuscated(true);
            if (font != null && !font.equals(FontDescription.DEFAULT.id())) st = st.withFont(new FontDescription.Resource(font));
            return st;
        }

        boolean sameStyle(Segment o) {
            return color == o.color && bold == o.bold && italic == o.italic && underlined == o.underlined
                    && strikethrough == o.strikethrough && obfuscated == o.obfuscated && java.util.Objects.equals(font, o.font);
        }
    }

    private RichText() {}

    public static Component toComponent(List<Segment> segments) {
        if (segments.size() == 1) {
            Segment s = segments.get(0);
            return Component.literal(s.text).withStyle(s.style());
        }
        MutableComponent root = Component.literal("");
        for (Segment s : segments) {
            if (s.text.isEmpty()) continue;
            root.append(Component.literal(s.text).withStyle(s.style()));
        }
        return root;
    }

    /** Flattens any component into styled runs (adjacent runs with the same style are merged). */
    public static List<Segment> fromComponent(Component c) {
        List<Segment> out = new ArrayList<>();
        c.visit((style, string) -> {
            Segment s = new Segment(string);
            TextColor color = style.getColor();
            s.color = color == null ? -1 : color.getValue();
            s.bold = style.isBold();
            s.italic = style.isItalic();
            s.underlined = style.isUnderlined();
            s.strikethrough = style.isStrikethrough();
            s.obfuscated = style.isObfuscated();
            FontDescription f = style.getFont();
            s.font = f instanceof FontDescription.Resource r && !r.equals(FontDescription.DEFAULT) ? r.id() : null;
            if (!out.isEmpty() && out.get(out.size() - 1).sameStyle(s)) {
                out.get(out.size() - 1).text += string;
            } else {
                out.add(s);
            }
            return Optional.empty();
        }, Style.EMPTY);
        if (out.isEmpty()) out.add(new Segment(""));
        return out;
    }
}
