package com.hyproj.makeium.client.feature;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import com.hyproj.makeium.client.Replies;
import com.hyproj.makeium.client.tool.Selection;
import com.hyproj.makeium.client.ui.MkButton;
import com.hyproj.makeium.client.ui.MkDialog;
import com.hyproj.makeium.client.ui.MkField;
import com.hyproj.makeium.client.ui.MkList;
import com.hyproj.makeium.client.ui.MkScreen;
import com.hyproj.makeium.client.ui.MkTheme;
import com.hyproj.makeium.client.ui.MkToast;
import com.hyproj.makeium.client.ui.MkToggle;
import com.hyproj.makeium.net.Net;
import com.hyproj.makeium.server.DatapackStore;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.network.chat.Component;

/** Test-play checkpoints: save the selected part of the map before playing, restore it afterwards. */
public class CheckpointScreen extends MkScreen {
    private static final List<CompoundTag> CHECKPOINTS = new ArrayList<>();
    private static boolean entities = true;
    private MkField name;
    private MkList list;

    public CheckpointScreen(Screen parent) {
        super(Component.translatable("makeium.feature.checkpoint"), parent);
    }

    @Override
    protected void init() {
        super.init();
        topActionsWidth = 70;
        Replies.on("cp/list", d -> {
            CHECKPOINTS.clear();
            for (Tag t : d.getListOrEmpty("checkpoints")) if (t instanceof CompoundTag c) CHECKPOINTS.add(c);
            refresh();
        });
        add(new MkButton(width - PAD - 60, 4, 60, 20, Component.translatable("makeium.ui.close"), MkButton.Kind.GHOST, this::onClose));
        int colW = Math.min(240, (width - 3 * PAD) / 2);
        int y = TOP + 42;
        name = add(new MkField(PAD, y, colW, 18, Component.translatable("makeium.cp.name")));
        name.value("before_test");
        add(new MkToggle(PAD, y + 24, colW, Component.translatable("makeium.cp.entities"), entities, v -> entities = v));
        add(new MkButton(PAD, y + 46, colW, 20, Component.translatable("makeium.cp.save"), MkButton.Kind.PRIMARY, this::save));
        add(new MkButton(PAD, y + 70, colW, 20, Component.translatable("makeium.tool.selection"), MkButton.Kind.DEFAULT, () -> PackState.runCommand("makeium tool selection")));
        int rx = PAD * 2 + colW;
        list = add(new MkList(rx, TOP + 26, width - rx - PAD, height - TOP - 26 - PAD));
        refresh();
        Net.sendToServer("cp/list");
    }

    private void refresh() {
        if (list == null) return;
        List<MkList.Item> rows = new ArrayList<>();
        SimpleDateFormat fmt = new SimpleDateFormat("MM-dd HH:mm");
        for (CompoundTag c : CHECKPOINTS) {
            String n = c.getStringOr("name", "");
            CompoundTag meta = c.getCompoundOrEmpty("meta");
            int[] mn = meta.getIntArray("min").orElse(new int[3]), mx = meta.getIntArray("max").orElse(new int[3]);
            String sub = fmt.format(new Date(c.getLongOr("time", 0))) + " · " + (mn.length == 3 ? mn[0] + "," + mn[1] + "," + mn[2] + " → " + mx[0] + "," + mx[1] + "," + mx[2] : "");
            rows.add(new MkList.Row(Component.literal(n), Component.literal(sub), Component.translatable("makeium.cp.restore_hint"), () ->
                    MkDialog.confirm(this, Component.translatable("makeium.cp.restore"), Component.translatable("makeium.cp.restore_text", n), () -> {
                        CompoundTag d = new CompoundTag();
                        d.putString("name", n);
                        Net.sendToServer("cp/restore", d);
                    })) {
                @Override
                public void click(double lx, double ly, int w, int button) {
                    if (button == 1) {
                        MkDialog.confirm(CheckpointScreen.this, Component.translatable("makeium.ui.delete"), Component.literal(n), () -> {
                            CompoundTag d = new CompoundTag();
                            d.putString("name", n);
                            Net.sendToServer("cp/delete", d);
                        });
                    } else {
                        super.click(lx, ly, w, button);
                    }
                }
            });
        }
        if (rows.isEmpty()) rows.add(new MkList.Note(Component.translatable("makeium.cp.none"), MkTheme.FAINT));
        list.setItems(rows);
    }

    private void save() {
        if (!Selection.complete()) {
            MkToast.error(Component.translatable("makeium.select.need"));
            return;
        }
        String n = name.getValue().strip().toLowerCase(java.util.Locale.ROOT).replaceAll("[^a-z0-9_.\\-]", "_");
        if (!DatapackStore.PACK_NAME.matcher(n).matches()) {
            MkToast.error(Component.translatable("makeium.cp.bad_name"));
            return;
        }
        CompoundTag d = Selection.toTag();
        d.putString("name", n);
        d.putBoolean("entities", entities);
        Net.sendToServer("cp/save", d);
    }

    @Override
    protected void drawBody(GuiGraphicsExtractor g, int mouseX, int mouseY, float a) {
        Component sel = Selection.complete()
                ? Component.translatable("makeium.edit.selection", Selection.min().toShortString(), Selection.max().toShortString(), Selection.volume())
                : Component.translatable("makeium.edit.no_selection");
        int colW = Math.min(240, (width - 3 * PAD) / 2);
        MkTheme.paragraph(g, sel, PAD, TOP + 10, colW, Selection.complete() ? MkTheme.GREEN_TEXT : MkTheme.YELLOW);
        MkTheme.paragraph(g, Component.translatable("makeium.cp.help"), PAD, TOP + 42 + 96, colW, MkTheme.FAINT);
        MkTheme.caption(g, Component.translatable("makeium.cp.list"), PAD * 2 + colW, TOP + 12);
    }
}
