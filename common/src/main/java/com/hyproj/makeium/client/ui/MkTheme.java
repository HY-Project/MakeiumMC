package com.hyproj.makeium.client.ui;

import com.hyproj.makeium.Makeium;
import com.hyproj.makeium.mixin.client.FontAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FontDescription;
import net.minecraft.network.chat.Style;
import net.minecraft.util.FormattedCharSequence;

/**
 * The Makeium look: a clean dark web-app UI in minecraft.net's colours — charcoal surfaces, one green accent,
 * square corners with a 2px darker "bevel" under primary buttons. All text is Noto Sans KR (Regular / Bold) through
 * fonts whose default face is remapped, so vanilla widgets (EditBox) measure and draw with the same glyphs.
 */
public final class MkTheme {
    // ---- tokens (ARGB) ----
    public static final int BG = 0xFF121212;
    public static final int PANEL = 0xFF1E1E1F;
    public static final int SURFACE = 0xFF2B2B2D;
    public static final int SURFACE_HOVER = 0xFF353538;
    public static final int INPUT = 0xFF161617;
    public static final int LINE = 0xFF3A3A3D;
    public static final int LINE_STRONG = 0xFF55555A;
    public static final int GREEN = 0xFF3C8527;
    public static final int GREEN_HOVER = 0xFF52A535;
    public static final int GREEN_DARK = 0xFF2A5E1C;
    public static final int GREEN_TEXT = 0xFF6CC349;
    public static final int TEXT = 0xFFF0F0F0;
    public static final int MUTED = 0xFFA0A0A0;
    public static final int FAINT = 0xFF6E6E73;
    public static final int RED = 0xFFD9443A;
    public static final int RED_DARK = 0xFF8E2A24;
    public static final int YELLOW = 0xFFE8B931;
    public static final int BLUE = 0xFF4A90D9;
    public static final int OVERLAY = 0xC8000000;
    public static final int SHADOW = 0x66000000;

    public static final FontDescription UI = new FontDescription.Resource(Makeium.id("ui"));
    public static final FontDescription UI_BOLD = new FontDescription.Resource(Makeium.id("ui_bold"));
    public static final FontDescription MONO = new FontDescription.Resource(Makeium.id("mono"));

    private static Font font, bold, mono;

    private MkTheme() {}

    /** Noto Sans KR Regular as the default face (unstyled text and every vanilla widget given this font). */
    public static Font font() {
        if (font == null) font = remapped(UI);
        return font;
    }

    /** Noto Sans KR Bold as the default face: headings, tabs, buttons. */
    public static Font bold() {
        if (bold == null) bold = remapped(UI_BOLD);
        return bold;
    }

    /** Courier Prime (Latin) + Noto Sans KR: code, commands, JSON. */
    public static Font mono() {
        if (mono == null) mono = remapped(MONO);
        return mono;
    }

    private static Font remapped(FontDescription face) {
        Font.Provider base = ((FontAccessor) Minecraft.getInstance().font).makeium$provider();
        return new Font(new Font.Provider() {
            @Override
            public net.minecraft.client.gui.GlyphSource glyphs(FontDescription d) {
                return base.glyphs(FontDescription.DEFAULT.equals(d) ? face : d);
            }

            @Override
            public net.minecraft.client.gui.font.glyphs.EffectGlyph effect() {
                return base.effect();
            }
        });
    }

    public static int argb(float alpha, int rgb) {
        return (Math.round(alpha * 255) << 24) | (rgb & 0xFFFFFF);
    }

    public static int withAlpha(int color, float alpha) {
        int a = Math.round(((color >>> 24) & 0xFF) * alpha);
        return (a << 24) | (color & 0xFFFFFF);
    }

    public static int mix(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF, aa = (a >>> 24);
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF, ba = (b >>> 24);
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }

    // ---- shapes ----

    public static void border(GuiGraphicsExtractor g, int x, int y, int w, int h, int color) {
        g.outline(x, y, w, h, color);
    }

    /** A card: fill + 1px line. */
    public static void card(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x, y, x + w, y + h, PANEL);
        border(g, x, y, w, h, LINE);
    }

    /** Modal panel with a soft shadow. */
    public static void modal(GuiGraphicsExtractor g, int x, int y, int w, int h) {
        g.fill(x + 3, y + 4, x + w + 3, y + h + 4, SHADOW);
        g.fill(x, y, x + w, y + h, PANEL);
        border(g, x, y, w, h, LINE_STRONG);
    }

    public static void hline(GuiGraphicsExtractor g, int x0, int x1, int y, int color) {
        g.fill(x0, y, x1, y + 1, color);
    }

    public static void vline(GuiGraphicsExtractor g, int x, int y0, int y1, int color) {
        g.fill(x, y0, x + 1, y1, color);
    }

    /** Checkerboard, for transparent colours and textures. */
    public static void checker(GuiGraphicsExtractor g, int x, int y, int w, int h, int cell) {
        for (int yy = 0; yy < h; yy += cell) {
            for (int xx = 0; xx < w; xx += cell) {
                boolean dark = ((xx / cell) + (yy / cell)) % 2 == 0;
                g.fill(x + xx, y + yy, x + Math.min(w, xx + cell), y + Math.min(h, yy + cell), dark ? 0xFF3A3A3A : 0xFF5A5A5A);
            }
        }
    }

    // ---- text ----

    public static void text(GuiGraphicsExtractor g, String s, int x, int y, int color) {
        g.text(font(), s, x, y, color, false);
    }

    public static void text(GuiGraphicsExtractor g, Component c, int x, int y, int color) {
        g.text(font(), c, x, y, color, false);
    }

    public static void text(GuiGraphicsExtractor g, Font f, Component c, int x, int y, int color) {
        g.text(f, c, x, y, color, false);
    }

    public static void boldText(GuiGraphicsExtractor g, Component c, int x, int y, int color) {
        g.text(bold(), c, x, y, color, false);
    }

    public static void textRight(GuiGraphicsExtractor g, Component c, int right, int y, int color) {
        g.text(font(), c, right - font().width(c), y, color, false);
    }

    public static void textCentered(GuiGraphicsExtractor g, Font f, Component c, int cx, int y, int color) {
        g.text(f, c, cx - f.width(c) / 2, y, color, false);
    }

    public static void monoText(GuiGraphicsExtractor g, String s, int x, int y, int color) {
        g.text(mono(), s, x, y, color, false);
    }

    /** Clips {@code c} to {@code maxWidth} with an ellipsis. */
    public static Component clip(Font f, Component c, int maxWidth) {
        if (f.width(c) <= maxWidth) return c;
        String s = c.getString();
        Style style = c.getStyle();
        while (!s.isEmpty() && f.width(Component.literal(s + "…").withStyle(style)) > maxWidth) s = s.substring(0, s.length() - 1);
        return Component.literal(s + "…").withStyle(style);
    }

    public static String clip(Font f, String s, int maxWidth) {
        if (f.width(s) <= maxWidth) return s;
        while (!s.isEmpty() && f.width(s + "…") > maxWidth) s = s.substring(0, s.length() - 1);
        return s + "…";
    }

    /** Word-wrapped paragraph; returns the height used. */
    public static int paragraph(GuiGraphicsExtractor g, Component c, int x, int y, int width, int color) {
        int yy = y;
        for (FormattedCharSequence line : font().split(c, width)) {
            g.text(font(), line, x, yy, color, false);
            yy += 11;
        }
        return yy - y;
    }

    public static int paragraphHeight(Component c, int width) {
        return font().split(c, width).size() * 11;
    }

    /** The small uppercase section caption used above groups of fields. */
    public static void caption(GuiGraphicsExtractor g, Component c, int x, int y) {
        g.text(bold(), c, x, y, MUTED, false);
    }

    public static Component tr(String key, Object... args) {
        return Component.translatable(key, args);
    }
}
