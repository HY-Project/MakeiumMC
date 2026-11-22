package com.hyproj.makeium.client.feature;

import java.util.ArrayList;
import java.util.List;

import com.hyproj.makeium.client.Replies;
import com.hyproj.makeium.client.tool.Selection;
import com.hyproj.makeium.client.ui.MkButton;
import com.hyproj.makeium.client.ui.MkDialog;
import com.hyproj.makeium.client.ui.MkForm;
import com.hyproj.makeium.client.ui.MkList;
import com.hyproj.makeium.client.ui.MkScreen;
import com.hyproj.makeium.client.ui.MkTheme;
import com.hyproj.makeium.client.ui.MkToast;
import com.hyproj.makeium.feature.region.RegionPack;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;

/**
 * Trigger regions: boxes that run commands (and block-coding "region" events) when players enter, leave or stay.
 * Create one from the current selection (region tool: left/right click two corners), edit its events, save & apply.
 */
public class RegionScreen extends MkScreen {
    private static CompoundTag state = new CompoundTag();
    private static int selected = -1;
    private MkForm form;
    private boolean requested;

    public RegionScreen(Screen parent) {
        super(Component.translatable("makeium.feature.regions"), parent);
    }

    private int listW() {
        return Math.min(200, width / 3);
    }

    @Override
    protected ScreenRectangle scrollArea() {
        int x = PAD + listW() + PAD;
        return new ScreenRectangle(x, TOP, width - x, height - TOP);
    }

    @Override
    protected void init() {
        super.init();
        topActionsWidth = 70 + 110;
        if (!requested) {
            requested = true;
            PackState.load(RegionPack.PACK, s -> {
                state = s;
                RegionCache.load(s.getListOrEmpty("regions"));
                rebuildWidgets();
            });
        }
        add(new MkButton(width - PAD - 60, 4, 60, 20, Component.translatable("makeium.ui.close"), MkButton.Kind.GHOST, this::onClose));
        add(new MkButton(width - PAD - 60 - 6 - 104, 4, 104, 20, Component.translatable("makeium.ui.save_apply"), MkButton.Kind.PRIMARY, this::save));

        int lw = listW();
        add(new MkButton(PAD, TOP + 8, lw, 20, Component.translatable("makeium.region.new"), MkButton.Kind.PRIMARY, this::newRegion));
        MkList list = add(new MkList(PAD, TOP + 32, lw, height - TOP - 32 - 52));
        List<MkList.Item> rows = new ArrayList<>();
        List<RegionCache.Region> regions = RegionCache.regions();
        for (int i = 0; i < regions.size(); i++) {
            final int idx = i;
            RegionCache.Region r = regions.get(i);
            rows.add(new MkList.Row(Component.literal(r.name()), Component.literal(new BlockPos(r.min()[0], r.min()[1], r.min()[2]).toShortString() + " → "
                    + new BlockPos(r.max()[0], r.max()[1], r.max()[2]).toShortString()), null, () -> {
                selected = idx;
                scroll = 0;
                rebuildWidgets();
            }).selected(i == selected).accent(0xFF000000 | r.color()));
        }
        if (rows.isEmpty()) rows.add(new MkList.Note(Component.translatable("makeium.region.none"), MkTheme.FAINT));
        list.setItems(rows);
        add(new MkButton(PAD, height - 46, lw, 18, Component.translatable("makeium.tool.region"), MkButton.Kind.DEFAULT, () -> PackState.runCommand("makeium tool region")));
        add(new com.hyproj.makeium.client.ui.MkToggle(PAD, height - 24, lw, Component.translatable("makeium.region.always_show"), RegionCache.alwaysShow, v -> RegionCache.alwaysShow = v));

        if (selected >= 0 && selected < regions.size()) buildEditor(regions.get(selected));
    }

    private void buildEditor(RegionCache.Region r) {
        int x = PAD + listW() + PAD;
        form = new MkForm(this, w -> addScrolled(w), x, TOP + 8 - scroll, Math.min(340, width - x - PAD - 4));
        form.caption(Component.translatable("makeium.region.region"));
        form.field(Component.translatable("makeium.region.name"), r.name(), s -> replace(new RegionCache.Region(s.isBlank() ? r.name() : s, r.dimension(), r.min(), r.max(), r.color(), r.onEnter(), r.onLeave(), r.whileInside(), r.selector())));
        form.field(Component.translatable("makeium.region.selector"), r.selector(), s -> replace(with(r, null, null, null, s)));
        form.color(Component.translatable("makeium.region.color"), 0xFF000000 | r.color(), false, c -> replace(new RegionCache.Region(r.name(), r.dimension(), r.min(), r.max(), c & 0xFFFFFF, r.onEnter(), r.onLeave(), r.whileInside(), r.selector())));
        form.button(Component.translatable("makeium.region.use_selection"), MkButton.Kind.DEFAULT, () -> {
            if (!Selection.complete()) {
                MkToast.error(Component.translatable("makeium.select.need"));
                return;
            }
            BlockPos a = Selection.min(), b = Selection.max();
            replace(new RegionCache.Region(r.name(), dim(), new int[]{a.getX(), a.getY(), a.getZ()}, new int[]{b.getX(), b.getY(), b.getZ()}, r.color(), r.onEnter(), r.onLeave(), r.whileInside(), r.selector()));
            rebuildWidgets();
        });
        form.caption(Component.translatable("makeium.region.events"));
        form.field(Component.translatable("makeium.region.on_enter"), r.onEnter(), s -> replace(with(r, s, null, null, null)));
        form.field(Component.translatable("makeium.region.on_leave"), r.onLeave(), s -> replace(with(r, null, s, null, null)));
        form.field(Component.translatable("makeium.region.while_inside"), r.whileInside(), s -> replace(with(r, null, null, s, null)));
        form.button(Component.translatable("makeium.region.teleport"), MkButton.Kind.DEFAULT, () -> PackState.runCommand(
                "tp @s " + (r.min()[0] + r.max()[0] + 1) / 2.0 + " " + r.min()[1] + " " + (r.min()[2] + r.max()[2] + 1) / 2.0));
        form.button(Component.translatable("makeium.ui.delete"), MkButton.Kind.DANGER, () -> MkDialog.confirm(this, Component.translatable("makeium.ui.delete"), Component.literal(r.name()), () -> {
            List<RegionCache.Region> l = new ArrayList<>(RegionCache.regions());
            l.remove(selected);
            RegionCache.set(l);
            selected = -1;
            rebuildWidgets();
        }));
    }

    /** The current region with some fields replaced (null = keep). */
    private RegionCache.Region with(RegionCache.Region r, String enter, String leave, String inside, String selector) {
        RegionCache.Region cur = RegionCache.regions().get(selected);
        return new RegionCache.Region(cur.name(), cur.dimension(), cur.min(), cur.max(), cur.color(),
                enter != null ? enter : cur.onEnter(), leave != null ? leave : cur.onLeave(), inside != null ? inside : cur.whileInside(),
                selector != null ? selector : cur.selector());
    }

    private void replace(RegionCache.Region r) {
        List<RegionCache.Region> l = new ArrayList<>(RegionCache.regions());
        if (selected >= 0 && selected < l.size()) l.set(selected, r);
        RegionCache.set(l);
    }

    private String dim() {
        return minecraft.level == null ? "minecraft:overworld" : minecraft.level.dimension().identifier().toString();
    }

    private void newRegion() {
        if (!Selection.complete()) {
            MkToast.error(Component.translatable("makeium.select.need"));
            PackState.runCommand("makeium tool region");
            return;
        }
        MkDialog.prompt(this, Component.translatable("makeium.region.new"), Component.translatable("makeium.region.name"), "region_" + (RegionCache.regions().size() + 1), name -> {
            BlockPos a = Selection.min(), b = Selection.max();
            List<RegionCache.Region> l = new ArrayList<>(RegionCache.regions());
            int[] palette = {0x52A535, 0x4A90D9, 0xE8B931, 0xD9443A, 0xB36AD9, 0x3FBF8F};
            l.add(new RegionCache.Region(name.isBlank() ? "region" : name, dim(), new int[]{a.getX(), a.getY(), a.getZ()}, new int[]{b.getX(), b.getY(), b.getZ()},
                    palette[l.size() % palette.length], "title @s actionbar {text:\"" + name + "\",color:\"green\"}", "", "", "@a"));
            RegionCache.set(l);
            selected = l.size() - 1;
            rebuildWidgets();
        });
    }

    private void save() {
        List<RegionPack.Region> regions = new ArrayList<>();
        for (RegionCache.Region r : RegionCache.regions()) regions.add(r.toPack());
        state.put("regions", RegionCache.toTag());
        PackState.save(RegionPack.PACK, "Makeium trigger regions", RegionPack.compile(regions), state);
        MkToast.ok(Component.translatable("makeium.region.saved", regions.size()));
    }

    @Override
    public void removed() {
        Replies.off("dp/load");
        super.removed();
    }

    @Override
    protected void drawBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        if (form != null) clipToScrollArea(g, () -> {
            form.draw(g);
            MkTheme.paragraph(g, Component.translatable("makeium.region.help"), form.x, form.y + 4, form.w, MkTheme.FAINT);
        });
        else {
            int x = PAD + listW() + PAD;
            MkTheme.paragraph(g, Component.translatable("makeium.region.intro"), x, TOP + 12, width - x - PAD, MkTheme.MUTED);
        }
    }
}
