package com.hyproj.makeium.client.ui;

import java.util.Locale;
import java.util.function.IntConsumer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

/**
 * A colour swatch with its #RRGGBB(AA) code; clicking opens {@link Picker}: saturation/value square, hue bar,
 * optional alpha bar, HEX field and the 16 Minecraft text colours.
 */
public class MkColorButton extends AbstractButton {
    private final MkScreen screen;
    private final boolean alpha;
    private int argb;
    private final IntConsumer listener;

    public MkColorButton(MkScreen screen, int x, int y, int w, int h, int argb, boolean alpha, IntConsumer listener) {
        super(x, y, w, h, Component.empty());
        this.screen = screen;
        this.alpha = alpha;
        this.argb = alpha ? argb : (argb | 0xFF000000);
        this.listener = listener;
    }

    public int value() {
        return argb;
    }

    public void setValue(int v) {
        argb = alpha ? v : (v | 0xFF000000);
    }

    @Override
    public void onPress(InputWithModifiers input) {
        screen.openPopup(new Picker(getX(), getY() + getHeight() + 1, argb, alpha, v -> {
            argb = v;
            if (listener != null) listener.accept(v);
        }));
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        g.fill(x, y, x + w, y + h, MkTheme.INPUT);
        MkTheme.border(g, x, y, w, h, isHoveredOrFocused() ? MkTheme.LINE_STRONG : MkTheme.LINE);
        int s = h - 6;
        MkTheme.checker(g, x + 3, y + 3, s, s, 3);
        g.fill(x + 3, y + 3, x + 3 + s, y + 3 + s, argb);
        MkTheme.monoText(g, hex(argb, alpha), x + s + 8, y + (h - 8) / 2, MkTheme.TEXT);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    public static String hex(int argb, boolean alpha) {
        if (alpha) return String.format(Locale.ROOT, "#%06X%02X", argb & 0xFFFFFF, (argb >>> 24) & 0xFF);
        return String.format(Locale.ROOT, "#%06X", argb & 0xFFFFFF);
    }

    /** Parses #RGB, #RRGGBB or #RRGGBBAA; null when malformed. */
    public static Integer parse(String s, boolean alpha) {
        s = s.trim();
        if (s.startsWith("#")) s = s.substring(1);
        try {
            if (s.length() == 3) {
                int r = Integer.parseInt(s.substring(0, 1), 16), gg = Integer.parseInt(s.substring(1, 2), 16), b = Integer.parseInt(s.substring(2, 3), 16);
                return 0xFF000000 | (r * 17 << 16) | (gg * 17 << 8) | (b * 17);
            }
            if (s.length() == 6) return 0xFF000000 | Integer.parseInt(s, 16);
            if (s.length() == 8 && alpha) {
                int rgb = Integer.parseInt(s.substring(0, 6), 16);
                int aa = Integer.parseInt(s.substring(6), 16);
                return (aa << 24) | rgb;
            }
        } catch (NumberFormatException ignored) {
            // fall through
        }
        return null;
    }

    /** The 16 legacy chat colours (§0-§f), which are also the "named" text colours. */
    public static final int[] MC_COLORS = {
            0x000000, 0x0000AA, 0x00AA00, 0x00AAAA, 0xAA0000, 0xAA00AA, 0xFFAA00, 0xAAAAAA,
            0x555555, 0x5555FF, 0x55FF55, 0x55FFFF, 0xFF5555, 0xFF55FF, 0xFFFF55, 0xFFFFFF
    };

    public static class Picker extends MkWidgetPopup {
        private static final int SQ = 100;
        private final boolean alpha;
        private final IntConsumer listener;
        private float hue, sat, val, a;
        private int drag; // 0 none, 1 square, 2 hue, 3 alpha
        private final MkField hexField;

        public Picker(int x, int y, int argb, boolean alpha, IntConsumer listener) {
            super(x, y, SQ + 46 + (alpha ? 14 : 0), SQ + 74);
            this.alpha = alpha;
            this.listener = listener;
            float[] hsv = rgbToHsv(argb);
            hue = hsv[0];
            sat = hsv[1];
            val = hsv[2];
            a = ((argb >>> 24) & 0xFF) / 255f;
            if (!alpha) a = 1;
            hexField = child(MkField.mono(0, 0, 80, 16, Component.empty()));
            hexField.value(hex(argb, alpha));
            hexField.onChange(s -> {
                Integer v = parse(s, alpha);
                hexField.setInvalid(v == null);
                if (v != null) {
                    float[] h2 = rgbToHsv(v);
                    hue = h2[0];
                    sat = h2[1];
                    val = h2[2];
                    if (alpha) a = ((v >>> 24) & 0xFF) / 255f;
                    listener.accept(current());
                }
            });
        }

        @Override
        protected void layout() {
            hexField.moveBox(x + 6, y + SQ + 12);
        }

        private int current() {
            int rgb = Mth.hsvToRgb(hue, sat, val) & 0xFFFFFF;
            return (Math.round(a * 255) << 24) | rgb;
        }

        @Override
        protected void renderFrame(GuiGraphicsExtractor g, int mouseX, int mouseY, float pt) {
            MkTheme.modal(g, x, y, w, h);
            int sx = x + 6, sy = y + 6;
            // saturation (x) / value (y) square, drawn as columns of vertical gradients
            for (int i = 0; i < SQ; i += 2) {
                int top = 0xFF000000 | Mth.hsvToRgb(hue, i / (float) SQ, 1f);
                g.fillGradient(sx + i, sy, sx + i + 2, sy + SQ, top, 0xFF000000);
            }
            int cx = sx + Math.round(sat * SQ), cy = sy + Math.round((1 - val) * SQ);
            MkTheme.border(g, cx - 3, cy - 3, 7, 7, 0xFFFFFFFF);
            // hue bar
            int hx = sx + SQ + 6;
            for (int i = 0; i < SQ; i++) g.fill(hx, sy + i, hx + 10, sy + i + 1, 0xFF000000 | Mth.hsvToRgb(i / (float) SQ, 1f, 1f));
            int hy = sy + Math.round(hue * SQ);
            MkTheme.border(g, hx - 1, hy - 1, 12, 3, 0xFFFFFFFF);
            if (alpha) {
                int ax = hx + 14;
                MkTheme.checker(g, ax, sy, 10, SQ, 5);
                int rgb = current() & 0xFFFFFF;
                g.fillGradient(ax, sy, ax + 10, sy + SQ, 0xFF000000 | rgb, rgb);
                int ay = sy + Math.round((1 - a) * SQ);
                MkTheme.border(g, ax - 1, ay - 1, 12, 3, 0xFFFFFFFF);
            }
            // preview
            int px = x + 92, py = y + SQ + 12;
            MkTheme.checker(g, px, py, w - 98, 16, 4);
            g.fill(px, py, x + w - 6, py + 16, current());
            // palette
            int pw = (w - 12) / 8;
            for (int i = 0; i < 16; i++) {
                int bx = x + 6 + (i % 8) * pw, by = y + SQ + 34 + (i / 8) * 14;
                g.fill(bx, by, bx + pw - 2, by + 12, 0xFF000000 | MC_COLORS[i]);
            }
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
            int sx = x + 6, sy = y + 6, hx = sx + SQ + 6;
            double mx = e.x(), my = e.y();
            if (my >= sy && my < sy + SQ) {
                if (mx >= sx && mx < sx + SQ) drag = 1;
                else if (mx >= hx && mx < hx + 10) drag = 2;
                else if (alpha && mx >= hx + 14 && mx < hx + 24) drag = 3;
                if (drag != 0) {
                    dragTo(mx, my);
                    return true;
                }
            }
            int pw = (w - 12) / 8;
            if (my >= y + SQ + 34 && my < y + SQ + 62 && mx >= x + 6 && mx < x + 6 + pw * 8) {
                int i = (int) ((mx - x - 6) / pw) + ((int) ((my - y - SQ - 34) / 14)) * 8;
                if (i >= 0 && i < 16) {
                    float[] hsv = rgbToHsv(MC_COLORS[i]);
                    hue = hsv[0];
                    sat = hsv[1];
                    val = hsv[2];
                    changed();
                }
                return true;
            }
            return super.mouseClicked(e, doubleClick);
        }

        @Override
        public boolean mouseDragged(MouseButtonEvent e, double dx, double dy) {
            if (drag != 0) {
                dragTo(e.x(), e.y());
                return true;
            }
            return super.mouseDragged(e, dx, dy);
        }

        @Override
        public boolean mouseReleased(MouseButtonEvent e) {
            drag = 0;
            return super.mouseReleased(e);
        }

        private void dragTo(double mx, double my) {
            int sx = x + 6, sy = y + 6;
            float ty = Mth.clamp((float) (my - sy) / SQ, 0, 1);
            switch (drag) {
                case 1 -> {
                    sat = Mth.clamp((float) (mx - sx) / SQ, 0, 1);
                    val = 1 - ty;
                }
                case 2 -> hue = Math.min(ty, 0.999f);
                case 3 -> a = 1 - ty;
                default -> {
                    return;
                }
            }
            changed();
        }

        private void changed() {
            int v = current();
            hexField.setValue(hex(v, alpha));
            hexField.setInvalid(false);
            listener.accept(v);
        }

        private static float[] rgbToHsv(int rgb) {
            float r = ((rgb >> 16) & 0xFF) / 255f, g = ((rgb >> 8) & 0xFF) / 255f, b = (rgb & 0xFF) / 255f;
            float max = Math.max(r, Math.max(g, b)), min = Math.min(r, Math.min(g, b)), d = max - min;
            float h;
            if (d == 0) h = 0;
            else if (max == r) h = ((g - b) / d) % 6;
            else if (max == g) h = (b - r) / d + 2;
            else h = (r - g) / d + 4;
            h /= 6;
            if (h < 0) h += 1;
            return new float[]{h, max == 0 ? 0 : d / max, max};
        }
    }
}
