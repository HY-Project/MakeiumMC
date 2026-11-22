package com.hyproj.makeium.client.display;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import com.hyproj.makeium.client.tool.ToolController;
import com.hyproj.makeium.client.ui.MkButton;
import com.hyproj.makeium.client.ui.MkForm;
import com.hyproj.makeium.client.ui.MkList;
import com.hyproj.makeium.client.ui.MkScreen;
import com.hyproj.makeium.client.ui.MkTheme;
import com.hyproj.makeium.client.ui.MkToast;
import com.hyproj.makeium.feature.display.DisplayData;
import com.hyproj.makeium.feature.display.WallSnap;
import com.hyproj.makeium.net.Net;
import com.hyproj.makeium.server.EntityNbt;
import com.hyproj.makeium.server.Tools;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.Display;

/** Display Factory overview: get the tools, set how they place, and browse/edit the displays around you. */
public class DisplayFactoryScreen extends MkScreen {
    private MkForm form;
    private MkList nearby;

    public DisplayFactoryScreen(Screen parent) {
        super(Component.translatable("makeium.feature.display"), parent);
    }

    @Override
    protected void init() {
        super.init();
        add(new MkButton(width - PAD - 60, 4, 60, 20, Component.translatable("makeium.ui.close"), MkButton.Kind.GHOST, this::onClose));
        int colW = colW();
        topActionsWidth = 70;
        form = new MkForm(this, w -> addScrolled(w), PAD, TOP + 10 - scroll, colW);
        form.caption(Component.translatable("makeium.display.tools"));
        form.buttons(MkButton.Kind.PRIMARY,
                new Component[]{Component.translatable("makeium.display.kind.text"), Component.translatable("makeium.display.kind.block"), Component.translatable("makeium.display.kind.item")},
                new Runnable[]{() -> give(Tools.TEXT_DISPLAY), () -> give(Tools.BLOCK_DISPLAY), () -> give(Tools.ITEM_DISPLAY)});
        form.button(Component.translatable("makeium.tool.display_select"), MkButton.Kind.DEFAULT, () -> give(Tools.DISPLAY_SELECT));

        form.caption(Component.translatable("makeium.display.placement"));
        form.dropdown(Component.translatable("makeium.display.snap_mode"), List.of(WallSnap.Snap.values()), DisplayBrush.snap,
                s -> Component.translatable("makeium.display.snap." + s.name().toLowerCase(Locale.ROOT)), s -> DisplayBrush.snap = s);
        form.toggle(Component.translatable("makeium.display.align_wall"), DisplayBrush.alignToWall, v -> DisplayBrush.alignToWall = v);
        form.toggle(Component.translatable("makeium.display.open_after"), DisplayBrush.openEditorAfterPlace, v -> DisplayBrush.openEditorAfterPlace = v);
        form.caption(Component.translatable("makeium.display.templates"));
        form.buttons(MkButton.Kind.DEFAULT,
                new Component[]{Component.translatable("makeium.display.reset_text"), Component.translatable("makeium.display.reset_block"), Component.translatable("makeium.display.reset_item")},
                new Runnable[]{() -> reset(WallSnap.Kind.TEXT), () -> reset(WallSnap.Kind.BLOCK), () -> reset(WallSnap.Kind.ITEM)});

        int rx = PAD * 2 + colW;
        int rw = width - rx - PAD;
        nearby = add(new MkList(rx, TOP + 28, rw, height - TOP - 28 - 40));
        add(new MkButton(rx, height - 32, 110, 20, Component.translatable("makeium.ui.refresh"), MkButton.Kind.DEFAULT, this::refresh));
        refresh();
    }

    private int colW() {
        return Math.min(280, (width - 3 * PAD) / 2);
    }

    @Override
    protected net.minecraft.client.gui.navigation.ScreenRectangle scrollArea() {
        return new net.minecraft.client.gui.navigation.ScreenRectangle(0, TOP, PAD + colW() + PAD / 2, height - TOP);
    }

    @Override
    protected int extraContentBottom() {
        return form == null ? 0 : form.y + 14 + MkTheme.paragraphHeight(Component.translatable("makeium.display.controls.text"), colW()) + 10;
    }

    private void give(Tools t) {
        CompoundTag d = new CompoundTag();
        d.putString("command", "makeium tool " + t.id());
        Net.sendToServer("cmd/run", d);
    }

    private void reset(WallSnap.Kind k) {
        DisplayBrush.setTemplate(DisplayData.defaults(k, minecraft.level == null ? null : minecraft.level.registryAccess()));
        MkToast.ok(Component.translatable("makeium.display.template_reset"));
    }

    private void refresh() {
        List<MkList.Item> rows = new ArrayList<>();
        if (minecraft.level == null || minecraft.player == null) {
            nearby.setItems(rows);
            return;
        }
        List<Display> list = minecraft.level.getEntitiesOfClass(Display.class, minecraft.player.getBoundingBox().inflate(48));
        list.sort(Comparator.comparingDouble(d -> d.distanceToSqr(minecraft.player)));
        for (Display d : list) {
            WallSnap.Kind kind = ToolController.kindOf(d);
            Component title = switch (kind) {
                case TEXT -> {
                    var s = ((Display.TextDisplay) d).textRenderState();
                    String str = s == null ? "" : s.text().getString().replace('\n', ' ');
                    yield Component.literal(str.isBlank() ? "(text)" : str);
                }
                case BLOCK -> {
                    var s = ((Display.BlockDisplay) d).blockRenderState();
                    yield s == null ? Component.literal("block") : s.blockState().getBlock().getName();
                }
                case ITEM -> {
                    var s = ((Display.ItemDisplay) d).itemRenderState();
                    yield s == null ? Component.literal("item") : s.itemStack().getHoverName();
                }
            };
            String where = String.format(Locale.ROOT, "%.1f, %.1f, %.1f", d.getX(), d.getY(), d.getZ());
            boolean mine = d.entityTags().contains("makeium");
            rows.add(new MkList.Row(title, Component.literal(where + (mine ? "  · Makeium" : "")),
                    Component.literal(String.format(Locale.ROOT, "%.0fm", Math.sqrt(d.distanceToSqr(minecraft.player)))),
                    () -> minecraft.gui.setScreen(new DisplayEditorScreen(this, d.getUUID().toString(), new DisplayData(kind, EntityNbt.save(d)))))
                    .accent(kind == WallSnap.Kind.TEXT ? MkTheme.GREEN : kind == WallSnap.Kind.BLOCK ? MkTheme.BLUE : MkTheme.YELLOW));
        }
        if (rows.isEmpty()) rows.add(new MkList.Note(Component.translatable("makeium.display.none_nearby"), MkTheme.FAINT));
        nearby.setItems(rows);
    }

    @Override
    protected void drawBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        int colW = colW();
        clipToScrollArea(g, () -> {
            form.draw(g);
            int hy = form.y + 10;
            MkTheme.caption(g, Component.translatable("makeium.display.controls"), PAD, hy);
            MkTheme.paragraph(g, Component.translatable("makeium.display.controls.text"), PAD, hy + 14, colW, MkTheme.MUTED);
        });
        int rx = PAD * 2 + colW;
        MkTheme.caption(g, Component.translatable("makeium.display.nearby"), rx, TOP + 14);
    }
}
