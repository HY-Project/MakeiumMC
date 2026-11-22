package com.hyproj.makeium.client.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;

/**
 * Searchable icon grid for picking an item or a block (search matches the id and the localized name). Blocks
 * without an item form (fire, water, ...) show a barrier icon.
 */
public class MkPicker extends MkWidgetPopup {
    public record Entry(Identifier id, ItemStack icon, Component name) {}

    private static final int CELL = 20;
    private final List<Entry> all;
    private List<Entry> shown;
    private final Consumer<Identifier> pick;
    private final MkField search;
    private int scroll;
    private final int cols, rows;
    private final Component title;

    public MkPicker(MkScreen screen, Component title, List<Entry> entries, Consumer<Identifier> pick) {
        super((screen.width - 300) / 2, Math.max(30, (screen.height - 260) / 2), 300, 260);
        this.title = title;
        this.all = entries;
        this.shown = entries;
        this.pick = pick;
        this.cols = (w - 16) / CELL;
        this.rows = (h - 64) / CELL;
        search = child(new MkField(0, 0, w - 16, 18, Component.translatable("makeium.ui.search")));
        search.onChange(s -> refilter());
        focus(search);
    }

    public static List<Entry> items() {
        List<Entry> out = new ArrayList<>();
        for (var item : BuiltInRegistries.ITEM) {
            if (item == Items.AIR) continue;
            ItemStack st = new ItemStack(item);
            out.add(new Entry(BuiltInRegistries.ITEM.getKey(item), st, st.getHoverName()));
        }
        return out;
    }

    public static List<Entry> blocks() {
        List<Entry> out = new ArrayList<>();
        for (Block b : BuiltInRegistries.BLOCK) {
            Identifier id = BuiltInRegistries.BLOCK.getKey(b);
            if (id.getPath().equals("air")) continue;
            ItemStack icon = b.asItem() == Items.AIR ? new ItemStack(Items.BARRIER) : new ItemStack(b.asItem());
            out.add(new Entry(id, icon, b.getName()));
        }
        return out;
    }

    @Override
    protected void layout() {
        search.moveBox(x + 8, y + 22);
    }

    private void refilter() {
        String f = search.getValue().toLowerCase(Locale.ROOT).trim();
        shown = f.isEmpty() ? all : all.stream().filter(e -> e.id.toString().contains(f) || e.name.getString().toLowerCase(Locale.ROOT).contains(f)).toList();
        scroll = 0;
    }

    @Override
    protected void renderFrame(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        MkTheme.modal(g, x, y, w, h);
        MkTheme.boldText(g, title, x + 8, y + 8, MkTheme.TEXT);
    }

    @Override
    protected void renderAfter(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int gx = x + 8, gy = y + 46;
        Entry hover = null;
        for (int i = 0; i < cols * rows; i++) {
            int idx = scroll * cols + i;
            if (idx >= shown.size()) break;
            Entry e = shown.get(idx);
            int cx = gx + (i % cols) * CELL, cy = gy + (i / cols) * CELL;
            boolean hot = mouseX >= cx && mouseX < cx + CELL && mouseY >= cy && mouseY < cy + CELL;
            g.fill(cx, cy, cx + CELL - 2, cy + CELL - 2, hot ? MkTheme.SURFACE_HOVER : MkTheme.SURFACE);
            g.item(e.icon, cx + 1, cy + 1);
            if (hot) hover = e;
        }
        MkTheme.text(g, Component.translatable("makeium.ui.count", shown.size()), x + 8, y + h - 14, MkTheme.FAINT);
        if (hover != null) g.setTooltipForNextFrame(MkTheme.font(), Component.literal(hover.name.getString() + "\n").append(Component.literal(hover.id.toString()).withColor(MkTheme.MUTED)), mouseX, mouseY);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean doubleClick) {
        int gx = x + 8, gy = y + 46;
        if (e.x() >= gx && e.y() >= gy && e.x() < gx + cols * CELL && e.y() < gy + rows * CELL) {
            int idx = scroll * cols + (int) ((e.y() - gy) / CELL) * cols + (int) ((e.x() - gx) / CELL);
            if (idx < shown.size()) {
                close();
                pick.accept(shown.get(idx).id);
            }
            return true;
        }
        return super.mouseClicked(e, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double sx, double sy) {
        int maxScroll = Math.max(0, (shown.size() + cols - 1) / cols - rows);
        scroll = Math.max(0, Math.min(maxScroll, scroll - (int) Math.signum(sy)));
        return true;
    }

    @Override
    protected boolean onEnter() {
        if (!shown.isEmpty()) {
            close();
            pick.accept(shown.get(0).id);
        }
        return true;
    }
}
