package com.hyproj.makeium.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Function;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.CharacterEvent;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;

/**
 * Select box: shows the current option and a caret; clicking opens a scrollable option list (with type-to-filter when
 * the list is long) as a popup on the owning {@link MkScreen}.
 */
public class MkDropdown<T> extends AbstractButton {
    private final MkScreen screen;
    private List<T> options;
    private final Function<T, Component> label;
    private final Consumer<T> listener;
    private T value;

    public MkDropdown(MkScreen screen, int x, int y, int w, int h, List<T> options, T value, Function<T, Component> label, Consumer<T> listener) {
        super(x, y, w, h, Component.empty());
        this.screen = screen;
        this.options = new ArrayList<>(options);
        this.value = value;
        this.label = label;
        this.listener = listener;
    }

    public T value() {
        return value;
    }

    public void setValue(T v) {
        value = v;
    }

    public void setOptions(List<T> options) {
        this.options = new ArrayList<>(options);
    }

    @Override
    public void onPress(InputWithModifiers input) {
        screen.openPopup(new ListPopup<>(getX(), getY() + getHeight() + 1, Math.max(getWidth(), 120), options, value, label, v -> {
            value = v;
            if (listener != null) listener.accept(v);
        }));
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hot = isHoveredOrFocused();
        g.fill(x, y, x + w, y + h, MkTheme.INPUT);
        MkTheme.border(g, x, y, w, h, hot ? MkTheme.LINE_STRONG : MkTheme.LINE);
        Component c = value == null ? Component.literal("—") : label.apply(value);
        MkTheme.text(g, MkTheme.clip(MkTheme.font(), c, w - 22), x + 6, y + (h - 8) / 2, MkTheme.TEXT);
        // caret ▾
        int cx = x + w - 11, cy = y + h / 2 - 1;
        g.fill(cx, cy, cx + 5, cy + 1, MkTheme.MUTED);
        g.fill(cx + 1, cy + 1, cx + 4, cy + 2, MkTheme.MUTED);
        g.fill(cx + 2, cy + 2, cx + 3, cy + 3, MkTheme.MUTED);
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        defaultButtonNarrationText(output);
    }

    /** The option list popup; also used on its own (context menus, pickers). */
    public static class ListPopup<T> extends MkPopup {
        private static final int ROW = 14;
        private final List<T> all;
        private List<T> shown;
        private final T current;
        private final Function<T, Component> label;
        private final Consumer<T> pick;
        private String filter = "";
        private int scroll;

        public ListPopup(int x, int y, int w, List<T> options, T current, Function<T, Component> label, Consumer<T> pick) {
            super(x, y, w, Math.min(options.size(), 14) * ROW + 4 + (options.size() > 14 ? ROW : 0));
            this.all = options;
            this.shown = options;
            this.current = current;
            this.label = label;
            this.pick = pick;
            int i = options.indexOf(current);
            if (i > 10) scroll = i - 5;
        }

        private boolean filterable() {
            return all.size() > 14;
        }

        private int listTop() {
            return y + 2 + (filterable() ? ROW : 0);
        }

        private int visibleRows() {
            return Math.min(14, all.size());
        }

        @Override
        public void render(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
            MkTheme.modal(g, x, y, w, h);
            if (filterable()) {
                g.fill(x + 1, y + 1, x + w - 1, y + ROW + 1, MkTheme.INPUT);
                MkTheme.text(g, filter.isEmpty() ? MkTheme.tr("makeium.ui.type_to_filter").copy().withColor(MkTheme.FAINT) : Component.literal(filter + "_"), x + 6, y + 4, MkTheme.TEXT);
            }
            int top = listTop();
            g.enableScissor(x, top, x + w, top + visibleRows() * ROW);
            for (int i = 0; i < visibleRows() && i + scroll < shown.size(); i++) {
                T o = shown.get(i + scroll);
                int ry = top + i * ROW;
                boolean hover = mouseX >= x && mouseX < x + w && mouseY >= ry && mouseY < ry + ROW;
                if (hover) g.fill(x + 1, ry, x + w - 1, ry + ROW, MkTheme.SURFACE_HOVER);
                if (o.equals(current)) g.fill(x + 1, ry, x + 3, ry + ROW, MkTheme.GREEN);
                MkTheme.text(g, MkTheme.clip(MkTheme.font(), label.apply(o), w - 14), x + 8, ry + 3, o.equals(current) ? MkTheme.GREEN_TEXT : MkTheme.TEXT);
            }
            g.disableScissor();
        }

        @Override
        public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
            int top = listTop();
            int i = (int) ((e.y() - top) / ROW);
            if (e.y() >= top && i >= 0 && i < visibleRows() && i + scroll < shown.size()) {
                pick.accept(shown.get(i + scroll));
                close();
            }
            return true;
        }

        @Override
        public boolean mouseScrolled(double mx, double my, double sx, double sy) {
            scroll = Math.max(0, Math.min(Math.max(0, shown.size() - visibleRows()), scroll - (int) Math.signum(sy) * 2));
            return true;
        }

        @Override
        public boolean charTyped(CharacterEvent e) {
            if (!filterable()) return false;
            filter += e.codepointAsString();
            refilter();
            return true;
        }

        @Override
        public boolean keyPressed(KeyEvent e) {
            if (filterable() && e.key() == com.mojang.blaze3d.platform.InputConstants.KEY_BACKSPACE && !filter.isEmpty()) {
                filter = filter.substring(0, filter.length() - 1);
                refilter();
                return true;
            }
            if (e.isConfirmation() && !shown.isEmpty()) {
                pick.accept(shown.get(Math.min(scroll, shown.size() - 1)));
                close();
                return true;
            }
            return super.keyPressed(e);
        }

        private void refilter() {
            String f = filter.toLowerCase(Locale.ROOT);
            shown = f.isEmpty() ? all : all.stream().filter(o -> label.apply(o).getString().toLowerCase(Locale.ROOT).contains(f) || o.toString().toLowerCase(Locale.ROOT).contains(f)).toList();
            scroll = 0;
        }
    }
}
