package com.hyproj.makeium.client.ui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Util;

/** Bottom-right notifications (saved, errors) shown on Makeium screens for a few seconds. */
public final class MkToast {
    public enum Kind { INFO, SUCCESS, ERROR }

    private record Toast(Component text, Kind kind, long until) {}

    private static final List<Toast> TOASTS = new ArrayList<>();

    private MkToast() {}

    public static void show(Component text, Kind kind) {
        TOASTS.add(new Toast(text, kind, Util.getMillis() + (kind == Kind.ERROR ? 6000 : 3000)));
        while (TOASTS.size() > 4) TOASTS.remove(0);
    }

    public static void info(Component t) {
        show(t, Kind.INFO);
    }

    public static void ok(Component t) {
        show(t, Kind.SUCCESS);
    }

    public static void error(Component t) {
        show(t, Kind.ERROR);
    }

    static void render(GuiGraphicsExtractor g, int width, int height) {
        long now = Util.getMillis();
        TOASTS.removeIf(t -> t.until < now);
        int y = height - 10;
        for (int i = TOASTS.size() - 1; i >= 0; i--) {
            Toast t = TOASTS.get(i);
            int maxW = Math.min(320, width - 20);
            int textH = MkTheme.paragraphHeight(t.text, maxW - 20);
            int w = Math.min(maxW, MkTheme.font().width(t.text) + 22);
            int h = textH + 10;
            y -= h;
            int x = width - 10 - w;
            int accent = switch (t.kind) {
                case SUCCESS -> MkTheme.GREEN;
                case ERROR -> MkTheme.RED;
                default -> MkTheme.BLUE;
            };
            g.fill(x + 2, y + 3, x + w + 2, y + h + 3, MkTheme.SHADOW);
            g.fill(x, y, x + w, y + h, MkTheme.SURFACE);
            g.fill(x, y, x + 3, y + h, accent);
            MkTheme.paragraph(g, t.text, x + 11, y + 6, w - 18, MkTheme.TEXT);
            y -= 6;
        }
    }
}
