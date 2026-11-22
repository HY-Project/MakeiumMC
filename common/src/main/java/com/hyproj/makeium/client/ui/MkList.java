package com.hyproj.makeium.client.ui;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/** Scrolling list of rows with a thin scrollbar; rows decide their own height, drawing and clicks. */
public class MkList extends AbstractWidget {
    public interface Item {
        int height();

        void draw(GuiGraphicsExtractor g, int x, int y, int w, boolean hovered);

        default boolean clickable() {
            return false;
        }

        /** {@code button}: 0 left, 1 right. */
        default void click(double localX, double localY, int w, int button) {}
    }

    /** Section caption. */
    public record Header(Component text) implements Item {
        @Override
        public int height() {
            return 18;
        }

        @Override
        public void draw(GuiGraphicsExtractor g, int x, int y, int w, boolean hovered) {
            MkTheme.caption(g, text, x + 4, y + 7);
        }
    }

    /** A muted one-liner (empty list, loading, error). */
    public record Note(Component text, int color) implements Item {
        @Override
        public int height() {
            return 16;
        }

        @Override
        public void draw(GuiGraphicsExtractor g, int x, int y, int w, boolean hovered) {
            MkTheme.text(g, MkTheme.clip(MkTheme.font(), text, w - 8), x + 4, y + 4, color);
        }
    }

    /** A plain clickable row: title, optional subtitle and right-side detail. */
    public static class Row implements Item {
        final Component title, sub, right;
        final Runnable onClick;
        boolean selected;
        int accent = MkTheme.GREEN;

        public Row(Component title, Component sub, Component right, Runnable onClick) {
            this.title = title;
            this.sub = sub;
            this.right = right;
            this.onClick = onClick;
        }

        public Row selected(boolean s) {
            selected = s;
            return this;
        }

        public Row accent(int c) {
            accent = c;
            return this;
        }

        @Override
        public int height() {
            return sub == null ? 18 : 28;
        }

        @Override
        public void draw(GuiGraphicsExtractor g, int x, int y, int w, boolean hovered) {
            g.fill(x, y, x + w, y + height(), selected ? MkTheme.SURFACE_HOVER : hovered ? MkTheme.SURFACE : 0);
            if (selected) g.fill(x, y, x + 2, y + height(), accent);
            int rw = right == null ? 0 : MkTheme.font().width(right) + 8;
            MkTheme.text(g, MkTheme.clip(MkTheme.font(), title, w - 12 - rw), x + 8, y + 5, MkTheme.TEXT);
            if (sub != null) MkTheme.text(g, MkTheme.clip(MkTheme.font(), sub, w - 12), x + 8, y + 16, MkTheme.MUTED);
            if (right != null) MkTheme.textRight(g, right, x + w - 6, y + 5, MkTheme.MUTED);
        }

        @Override
        public boolean clickable() {
            return onClick != null;
        }

        @Override
        public void click(double lx, double ly, int w, int button) {
            if (button == 0 && onClick != null) onClick.run();
        }
    }

    private final List<Item> items = new ArrayList<>();
    private double scroll;
    private boolean background = true;

    public MkList(int x, int y, int w, int h) {
        super(x, y, w, h, Component.empty());
    }

    public MkList noBackground() {
        background = false;
        return this;
    }

    public void setItems(List<? extends Item> list) {
        items.clear();
        items.addAll(list);
        scroll = Math.min(scroll, maxScroll());
    }

    public List<Item> items() {
        return items;
    }

    public void scrollToTop() {
        scroll = 0;
    }

    private int contentHeight() {
        int h = 0;
        for (Item i : items) h += i.height() + 1;
        return h;
    }

    private double maxScroll() {
        return Math.max(0, contentHeight() - getHeight());
    }

    @Override
    protected void extractWidgetRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int x = getX(), y = getY(), w = getWidth() - 5, h = getHeight();
        if (background) g.fill(x, y, x + getWidth(), y + h, MkTheme.INPUT);
        g.enableScissor(x, y, x + getWidth(), y + h);
        int cy = y - (int) scroll;
        for (Item item : items) {
            int ih = item.height();
            if (cy + ih >= y && cy <= y + h) {
                boolean hover = item.clickable() && mouseX >= x && mouseX < x + w && mouseY >= Math.max(cy, y) && mouseY < Math.min(cy + ih, y + h);
                item.draw(g, x, cy, w, hover);
            }
            cy += ih + 1;
        }
        g.disableScissor();
        double max = maxScroll();
        if (max > 0) {
            g.fill(x + getWidth() - 3, y, x + getWidth(), y + h, MkTheme.PANEL);
            int thumb = Math.max(16, (int) (h * (h / (double) contentHeight())));
            int ty = y + (int) ((h - thumb) * (scroll / max));
            g.fill(x + getWidth() - 3, ty, x + getWidth(), ty + thumb, MkTheme.LINE_STRONG);
        }
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        if (!isMouseOver(mx, my)) return false;
        scroll = Math.max(0, Math.min(maxScroll(), scroll - sy * 20));
        return true;
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        if (!active || !visible || !isMouseOver(e.x(), e.y())) return false;
        int cy = getY() - (int) scroll;
        for (Item item : items) {
            int ih = item.height();
            if (e.y() >= cy && e.y() < cy + ih) {
                if (item.clickable() && e.x() < getX() + getWidth() - 5) {
                    playDownSound(Minecraft.getInstance().getSoundManager());
                    item.click(e.x() - getX(), e.y() - cy, getWidth() - 5, e.button() == com.mojang.blaze3d.platform.InputConstants.MOUSE_BUTTON_RIGHT ? 1 : 0);
                    return true;
                }
                return false;
            }
            cy += ih + 1;
        }
        return false;
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {}
}
